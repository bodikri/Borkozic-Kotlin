/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2013 Andrey Novikov <http://andreynovikov.info/>
 *
 * This file is part of Androzic application.
 *
 * Androzic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Androzic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Androzic. If not, see <http://www.gnu.org/licenses/>.
 */

package com.borkozic.location;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.graphics.Color;
import android.location.GpsStatus.NmeaListener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import android.util.Log;
import android.widget.Toast;

import com.borkozic.Borkozic;
import com.borkozic.R;
import com.borkozic.Splash;
import com.borkozic.data.Track;

public class LocationService extends BaseLocationService implements LocationListener, NmeaListener, OnSharedPreferenceChangeListener {
	private static final String TAG = "Location";
	private static final int NOTIFICATION_ID = 24161;
	private static final String NOTIFICATION_CHANNEL_ID = "com.borkozic.location";
	private static final String ChannelName = "Background Location Service";
	private static final boolean DEBUG_ERRORS = false;

	public static final String ENABLE_LOCATIONS = "enableLocations";
	public static final String DISABLE_LOCATIONS = "disableLocations";
	public static final String ENABLE_TRACK = "enableTrack";
	public static final String DISABLE_TRACK = "disableTrack";
	public static final String BROADCAST_TRACKING_STATUS = "com.borkozic.trackingStatusChanged";

	private boolean locationsEnabled = false;
	private boolean useNetwork = true;
	private int gpsLocationTimeout = 120000;

	private LocationManager locationManager = null;

	private int gpsStatus = GPS_OFF;
	private int gnssStatus = GPS_OFF;

	private final float[] speed = new float[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
	private final float[] speedav = new float[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
	private final float[] speedavex = new float[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

	private long lastLocationMillis = 0;
	private long tics = 0;
	private int pause = 1;

	private Location lastKnownLocation = null;
	private boolean isContinous = false;
	private boolean justStarted = true;
	private float smoothSpeed = 0.0f;
	private float avgSpeed = 0.0f;
	private float nmeaGeoidHeight = Float.NaN;
	private float HDOP = Float.NaN;
	private float VDOP = Float.NaN;

	private SQLiteDatabase trackDB = null;
	private boolean trackingEnabled = false;
	private String errorMsg = "";
	private long errorTime = 0;

	private Location lastWritenLocation = null;
	private Location lastLocation = null;
	private double distanceFromLastWriting = 0;
	private long timeFromLastWriting = 0;

	private long minTime = 2000; // 2 seconds (default)
	private final long maxTime = 300000; // 5 minutes
	private int minDistance = 3; // 3 meters (default)

	private final Binder binder = new LocalBinder();
	private final RemoteCallbackList<ILocationCallback> locationRemoteCallbacks = new RemoteCallbackList<>();
	private final Set<ILocationListener> locationCallbacks = new HashSet<>();
	private final RemoteCallbackList<ITrackingCallback> trackingRemoteCallbacks = new RemoteCallbackList<>();
	private final Set<ITrackingListener> trackingCallbacks = new HashSet<>();

	@Override
	public void onCreate() {
		super.onCreate();
		Log.e(TAG, "onCreate()");

		lastKnownLocation = new Location("unknown");

		SharedPreferences sharedPreferences = getSharedPreferences(getPackageName() + "_preferences", Context.MODE_PRIVATE);
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_loc_usenetwork));
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_loc_gpstimeout));
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_tracking_mintime));
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_tracking_mindistance));

		sharedPreferences.registerOnSharedPreferenceChangeListener(this);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			startMyOwnForeground();
		} else {
			startForeground(NOTIFICATION_ID, new Notification());
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent == null || intent.getAction() == null)
			return Service.START_NOT_STICKY;

		String action = intent.getAction();
		if (action.equals(ENABLE_LOCATIONS) && !locationsEnabled) {
			locationsEnabled = true;
			connect();
			sendBroadcast(new Intent(BROADCAST_LOCATING_STATUS));
			if (trackingEnabled) {
				sendBroadcast(new Intent(BROADCAST_TRACKING_STATUS));
			}
		} else if (action.equals(DISABLE_LOCATIONS) && locationsEnabled) {
			locationsEnabled = false;
			disconnect();
			updateProvider(LocationManager.GPS_PROVIDER, false);
			updateProvider(LocationManager.NETWORK_PROVIDER, false);
			sendBroadcast(new Intent(BROADCAST_LOCATING_STATUS));
			if (trackingEnabled) {
				closeDatabase();
				sendBroadcast(new Intent(BROADCAST_TRACKING_STATUS));
			}
		} else if (action.equals(ENABLE_TRACK) && !trackingEnabled) {
			errorMsg = "";
			errorTime = 0;
			trackingEnabled = true;
			isContinous = false;
			openDatabase();
			sendBroadcast(new Intent(BROADCAST_TRACKING_STATUS));
		} else if (action.equals(DISABLE_TRACK) && trackingEnabled) {
			trackingEnabled = false;
			closeDatabase();
			errorMsg = "";
			errorTime = 0;
			sendBroadcast(new Intent(BROADCAST_TRACKING_STATUS));
		}
		updateNotification();
		return Service.START_REDELIVER_INTENT;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		getSharedPreferences(getPackageName() + "_preferences", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(this);
		disconnect();
		closeDatabase();
		Log.i(TAG, "Service stopped");
	}

	private final ILocationRemoteService.Stub locationRemoteBinder = new ILocationRemoteService.Stub() {
		public void registerCallback(ILocationCallback cb) {
			Log.i(TAG, "Register location callback");
			if (cb != null)
				locationRemoteCallbacks.register(cb);
		}

		public void unregisterCallback(ILocationCallback cb) {
			if (cb != null)
				locationRemoteCallbacks.unregister(cb);
		}

		public boolean isLocating() {
			return locationsEnabled;
		}
	};

	private final ITrackingRemoteService.Stub trackingRemoteBinder = new ITrackingRemoteService.Stub() {
		public void registerCallback(ITrackingCallback cb) {
			Log.i(TAG, "Register track callback");
			if (cb != null)
				trackingRemoteCallbacks.register(cb);
		}

		public void unregisterCallback(ITrackingCallback cb) {
			if (cb != null)
				trackingRemoteCallbacks.unregister(cb);
		}
	};

	@Override
	public IBinder onBind(Intent intent) {
		if (BORKOZIC_LOCATION_SERVICE.equals(intent.getAction()) || ILocationRemoteService.class.getName().equals(intent.getAction())) {
			return locationRemoteBinder;
		}
		if ("com.borkozic.tracking".equals(intent.getAction()) || ITrackingRemoteService.class.getName().equals(intent.getAction())) {
			return trackingRemoteBinder;
		} else {
			return binder;
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (getString(R.string.pref_loc_usenetwork).equals(key)) {
			useNetwork = sharedPreferences.getBoolean(key, getResources().getBoolean(R.bool.def_loc_usenetwork));
		} else if (getString(R.string.pref_loc_gpstimeout).equals(key)) {
			gpsLocationTimeout = 1000 * sharedPreferences.getInt(key, getResources().getInteger(R.integer.def_loc_gpstimeout));
		} else if (getString(R.string.pref_tracking_mintime).equals(key)) {
			try {
				minTime = Integer.parseInt(sharedPreferences.getString(key, "500"));
			} catch (NumberFormatException ignored) {
			}
		} else if (getString(R.string.pref_tracking_mindistance).equals(key)) {
			try {
				minDistance = Integer.parseInt(sharedPreferences.getString(key, "5"));
			} catch (NumberFormatException ignored) {
			}
		} else if (getString(R.string.pref_folder_data).equals(key)) {
			closeDatabase();
			openDatabase();
		}
	}

	private void connect() {
		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		if (locationManager == null) return;

		lastLocationMillis = 0;
		pause = 1;
		isContinous = false;
		justStarted = true;
		smoothSpeed = 0.0f;
		avgSpeed = 0.0f;

		if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			return;
		}

		if (useNetwork) {
			try {
				locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
				Log.d(TAG, "Network provider set");
			} catch (IllegalArgumentException e) {
				Toast.makeText(this, getString(R.string.err_no_network_provider), Toast.LENGTH_LONG).show();
			}
		}
		try {
			locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
			Log.d(TAG, "Gps provider set");
		} catch (IllegalArgumentException e) {
			Log.d(TAG, "Cannot set gps provider, likely no gps on device");
		}

		updateNotification(); // само обновява нотификацията, без да стартира отново foreground
	}

	private void disconnect() {
		if (locationManager != null) {
			locationManager.removeUpdates(this);
			locationManager = null;
			stopForeground(true);
		}
	}

	private Notification getNotification() {
		int msgId = R.string.notif_loc_started;
		int ntfId = R.drawable.ic_stat_locating;
		if (trackingEnabled) {
			msgId = R.string.notif_trk_started;
			ntfId = R.drawable.ic_stat_tracking;
		}
		if (gpsStatus != GPS_OK) {
			msgId = R.string.notif_loc_waiting;
			ntfId = R.drawable.ic_stat_waiting;
		}
		if (gpsStatus == GPS_OFF) {
			ntfId = R.drawable.ic_stat_off;
		}
		if (gnssStatus != GPS_OK) {
			msgId = R.string.notif_loc_waiting;
			ntfId = R.drawable.ic_stat_waiting;
		}
		if (gnssStatus == GPS_OFF) {
			ntfId = R.drawable.ic_stat_off;
		}
		if (errorTime > 0) {
			msgId = R.string.notif_trk_failure;
			ntfId = R.drawable.ic_stat_failure;
		}

		NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
		builder.setWhen(errorTime);
		builder.setSmallIcon(ntfId);
		Intent intent = new Intent(Intent.ACTION_MAIN);
		intent.addCategory(Intent.CATEGORY_LAUNCHER);
		intent.setComponent(new ComponentName(getApplicationContext(), Splash.class));
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
		PendingIntent contentIntent = PendingIntent.getActivity(this, NOTIFICATION_ID, intent, PendingIntent.FLAG_IMMUTABLE);
		builder.setContentIntent(contentIntent);
		builder.setContentTitle(getText(R.string.notif_loc_short));
		if (errorTime > 0 && DEBUG_ERRORS) {
			builder.setContentText(errorMsg);
		} else {
			builder.setContentText(getText(msgId));
		}
		builder.setOngoing(true);
		return builder.build();
	}

	private void updateNotification() {
		if (locationManager != null) {
			NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
			notificationManager.notify(NOTIFICATION_ID, getNotification());
		}
	}

	@RequiresApi(api = Build.VERSION_CODES.O)
	private void startMyOwnForeground() {
		NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, ChannelName, NotificationManager.IMPORTANCE_NONE);
		chan.setLightColor(Color.BLUE);
		chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
		NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		if (manager != null) {
			manager.createNotificationChannel(chan);
		}

		NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
		notificationBuilder.setOngoing(true)
				.setSmallIcon(R.drawable.info)
				.setContentTitle("App is running in background")
				.setPriority(NotificationManager.IMPORTANCE_MIN)
				.setCategory(Notification.CATEGORY_SERVICE);

		Notification notification = notificationBuilder.build();
		Log.d(TAG, "startMyOwnForeground");

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
			startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
		} else {
			startForeground(NOTIFICATION_ID, notification);
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
				Log.w(TAG, "POST_NOTIFICATIONS permission not granted");
				// Нотификацията няма да се покаже, но услугата ще работи
			}
		}
	}

	private void openDatabase() {
		Borkozic application = Borkozic.getApplication();
		if (application.dataPath == null) {
			Log.e(TAG, "Data path is null");
			errorMsg = "Data path is null";
			errorTime = System.currentTimeMillis();
			updateNotification();
			return;
		}
		File dir = new File(application.dataPath);
		if (!dir.exists() && !dir.mkdirs()) {
			Log.e(TAG, "Failed to create data folder");
			errorMsg = "Failed to create data folder";
			errorTime = System.currentTimeMillis();
			updateNotification();
			return;
		}
		File path = new File(dir, "myTrack.db");
		Log.i(TAG, path.toString());
		try {
			trackDB = SQLiteDatabase.openDatabase(path.getAbsolutePath(), null,
					SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.CREATE_IF_NECESSARY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
			Cursor cursor = trackDB.rawQuery("SELECT DISTINCT tbl_name FROM sqlite_master WHERE tbl_name = 'track'", null);
			if (cursor.getCount() == 0) {
				trackDB.execSQL("CREATE TABLE track (_id INTEGER PRIMARY KEY, latitude REAL, longitude REAL, code INTEGER, elevation REAL, speed REAL, track REAL, accuracy REAL, datetime INTEGER)");
			}
			cursor.close();
		} catch (SQLiteException e) {
			trackDB = null;
			Log.e(TAG, "openDatabase", e);
			errorMsg = "Failed to open DB";
			errorTime = System.currentTimeMillis();
			updateNotification();
		}
	}

	private void closeDatabase() {
		if (trackDB != null) {
			trackDB.close();
			trackDB = null;
		}
	}

	public Track getTrack() {
		return getTrack(0);
	}

	public Track getTrack(long limit) {
		if (trackDB == null) openDatabase();
		Track track = new Track();
		if (trackDB == null) return track;

		String limitStr = limit > 0 ? " LIMIT " + limit : "";
		Cursor cursor = trackDB.rawQuery("SELECT * FROM track ORDER BY _id DESC" + limitStr, null);
		if (cursor == null) return track;

		int latIdx = cursor.getColumnIndex("latitude");
		int lonIdx = cursor.getColumnIndex("longitude");
		int eleIdx = cursor.getColumnIndex("elevation");
		int speedIdx = cursor.getColumnIndex("speed");
		int bearingIdx = cursor.getColumnIndex("track");
		int accIdx = cursor.getColumnIndex("accuracy");
		int codeIdx = cursor.getColumnIndex("code");
		int timeIdx = cursor.getColumnIndex("datetime");

		if (latIdx == -1 || lonIdx == -1 || eleIdx == -1 || speedIdx == -1 ||
				bearingIdx == -1 || accIdx == -1 || codeIdx == -1 || timeIdx == -1) {
			Log.e(TAG, "Database schema mismatch: missing columns");
			cursor.close();
			return track;
		}

		for (boolean hasItem = cursor.moveToLast(); hasItem; hasItem = cursor.moveToPrevious()) {
			double lat = cursor.getDouble(latIdx);
			double lon = cursor.getDouble(lonIdx);
			double ele = cursor.getDouble(eleIdx);
			double spd = cursor.getDouble(speedIdx);
			double brg = cursor.getDouble(bearingIdx);
			double acc = cursor.getDouble(accIdx);
			int code = cursor.getInt(codeIdx);
			long tm = cursor.getLong(timeIdx);
			track.addPoint(code == 0, lat, lon, ele, spd, brg, acc, tm);
		}
		cursor.close();
		return track;
	}

	public Track getTrack(long start, long end) {
		if (trackDB == null) openDatabase();
		Track track = new Track();
		if (trackDB == null) return track;

		Cursor cursor = trackDB.rawQuery("SELECT * FROM track WHERE datetime >= ? AND datetime <= ? ORDER BY _id DESC",
				new String[]{String.valueOf(start), String.valueOf(end)});
		if (cursor == null) return track;

		int latIdx = cursor.getColumnIndex("latitude");
		int lonIdx = cursor.getColumnIndex("longitude");
		int eleIdx = cursor.getColumnIndex("elevation");
		int speedIdx = cursor.getColumnIndex("speed");
		int bearingIdx = cursor.getColumnIndex("track");
		int accIdx = cursor.getColumnIndex("accuracy");
		int codeIdx = cursor.getColumnIndex("code");
		int timeIdx = cursor.getColumnIndex("datetime");

		if (latIdx == -1 || lonIdx == -1 || eleIdx == -1 || speedIdx == -1 ||
				bearingIdx == -1 || accIdx == -1 || codeIdx == -1 || timeIdx == -1) {
			Log.e(TAG, "Database schema mismatch: missing columns");
			cursor.close();
			return track;
		}

		for (boolean hasItem = cursor.moveToLast(); hasItem; hasItem = cursor.moveToPrevious()) {
			double lat = cursor.getDouble(latIdx);
			double lon = cursor.getDouble(lonIdx);
			double ele = cursor.getDouble(eleIdx);
			double spd = cursor.getDouble(speedIdx);
			double brg = cursor.getDouble(bearingIdx);
			double acc = cursor.getDouble(accIdx);
			int code = cursor.getInt(codeIdx);
			long tm = cursor.getLong(timeIdx);
			track.addPoint(code == 0, lat, lon, ele, spd, brg, acc, tm);
		}
		cursor.close();
		return track;
	}

	public long getTrackStartTime() {
		if (trackDB == null) openDatabase();
		if (trackDB == null) return Long.MIN_VALUE;
		Cursor cursor = trackDB.rawQuery("SELECT MIN(datetime) FROM track WHERE datetime > 0", null);
		long res = Long.MIN_VALUE;
		if (cursor.moveToFirst()) {
			res = cursor.getLong(0);
		}
		cursor.close();
		return res;
	}

	public long getTrackEndTime() {
		if (trackDB == null) openDatabase();
		if (trackDB == null) return Long.MAX_VALUE;
		Cursor cursor = trackDB.rawQuery("SELECT MAX(datetime) FROM track", null);
		long res = Long.MAX_VALUE;
		if (cursor.moveToFirst()) {
			res = cursor.getLong(0);
		}
		cursor.close();
		return res;
	}

	public void clearTrack() {
		if (trackDB == null) openDatabase();
		if (trackDB != null) {
			trackDB.execSQL("DELETE FROM track");
		}
	}

	public void addPoint(boolean continous, double latitude, double longitude, double elevation,
						 float speed, float bearing, float accuracy, long time) {
		if (trackDB == null) {
			openDatabase();
			if (trackDB == null) return;
		}

		ContentValues values = new ContentValues();
		values.put("latitude", latitude);
		values.put("longitude", longitude);
		values.put("code", continous ? 0 : 1);
		values.put("elevation", elevation);
		values.put("speed", speed);
		values.put("track", bearing);
		values.put("accuracy", accuracy);
		values.put("datetime", time);

		try {
			trackDB.insertOrThrow("track", null, values);
		} catch (SQLException e) {
			Log.e(TAG, "addPoint", e);
			errorMsg = e.getMessage();
			errorTime = System.currentTimeMillis();
			updateNotification();
			closeDatabase();
		}
	}

	private void writeLocation(final Location loc, final boolean continous) {
		Log.d(TAG, "Fix needs writing");
		lastWritenLocation = loc;
		distanceFromLastWriting = 0;
		addPoint(continous, loc.getLatitude(), loc.getLongitude(), loc.getAltitude(),
				loc.getSpeed(), loc.getBearing(), loc.getAccuracy(), loc.getTime());

		for (ITrackingListener callback : trackingCallbacks) {
			callback.onNewPoint(continous, loc.getLatitude(), loc.getLongitude(),
					loc.getAltitude(), loc.getSpeed(), loc.getBearing(), loc.getAccuracy(), loc.getTime());
		}

		int n = trackingRemoteCallbacks.beginBroadcast();
		for (int i = 0; i < n; i++) {
			ITrackingCallback callback = trackingRemoteCallbacks.getBroadcastItem(i);
			try {
				callback.onNewPoint(continous, loc.getLatitude(), loc.getLongitude(),
						loc.getAltitude(), loc.getSpeed(), loc.getBearing(), loc.getAccuracy(), loc.getTime());
			} catch (RemoteException e) {
				Log.e(TAG, "Point broadcast error", e);
			}
		}
		trackingRemoteCallbacks.finishBroadcast();
	}

	private void writeTrack(Location loc, boolean continous) {
		boolean needsWrite = false;
		if (lastLocation != null) {
			distanceFromLastWriting += loc.distanceTo(lastLocation);
		}
		if (lastWritenLocation != null) {
			timeFromLastWriting = loc.getTime() - lastWritenLocation.getTime();
		}

		if (lastLocation == null || lastWritenLocation == null || !continous ||
				timeFromLastWriting > maxTime ||
				(distanceFromLastWriting > minDistance && timeFromLastWriting > minTime)) {
			needsWrite = true;
		}

		lastLocation = loc;

		if (needsWrite) {
			writeLocation(loc, continous);
		}
	}

	private void tearTrack() {
		if (lastLocation != null && (lastWritenLocation == null || !lastLocation.toString().equals(lastWritenLocation.toString()))) {
			writeLocation(lastLocation, isContinous);
		}
		isContinous = false;
	}

	private void updateLocation() {
		final Location location = lastKnownLocation;
		final boolean continous = isContinous;
		final boolean geoid = !Float.isNaN(nmeaGeoidHeight);
		final float smoothspeed = smoothSpeed;
		final float avgspeed = avgSpeed;

		Handler handler = new Handler();

		if (trackingEnabled) {
			handler.post(() -> writeTrack(location, continous));
		}
		for (ILocationListener callback : locationCallbacks) {
			handler.post(() -> callback.onLocationChanged(location, continous, geoid, smoothspeed, avgspeed));
		}

		int n = locationRemoteCallbacks.beginBroadcast();
		for (int i = 0; i < n; i++) {
			ILocationCallback callback = locationRemoteCallbacks.getBroadcastItem(i);
			try {
				callback.onLocationChanged(location, continous, geoid, smoothspeed, avgspeed);
			} catch (RemoteException e) {
				Log.e(TAG, "Location broadcast error", e);
			}
		}
		locationRemoteCallbacks.finishBroadcast();
	}

	private void updateLocation(final ILocationListener callback) {
		if (!"unknown".equals(lastKnownLocation.getProvider())) {
			callback.onLocationChanged(lastKnownLocation, isContinous, !Float.isNaN(nmeaGeoidHeight), smoothSpeed, avgSpeed);
		}
	}

	private void updateProvider(final String provider, final boolean enabled) {
		if (LocationManager.GPS_PROVIDER.equals(provider)) {
			updateNotification();
		}
		Handler handler = new Handler();
		for (ILocationListener callback : locationCallbacks) {
			handler.post(() -> {
				if (enabled) {
					callback.onProviderEnabled(provider);
				} else {
					callback.onProviderDisabled(provider);
				}
			});
		}

		int n = locationRemoteCallbacks.beginBroadcast();
		for (int i = 0; i < n; i++) {
			ILocationCallback callback = locationRemoteCallbacks.getBroadcastItem(i);
			try {
				if (enabled) {
					callback.onProviderEnabled(provider);
				} else {
					callback.onProviderDisabled(provider);
				}
			} catch (RemoteException e) {
				Log.e(TAG, "Provider broadcast error", e);
			}
		}
		locationRemoteCallbacks.finishBroadcast();
		Log.d(TAG, "Provider status dispatched: " + (locationCallbacks.size() + n));
	}

	private void updateProvider(final ILocationListener callback) {
		if (gpsStatus == GPS_OFF) {
			callback.onProviderDisabled(LocationManager.GPS_PROVIDER);
		} else {
			callback.onProviderEnabled(LocationManager.GPS_PROVIDER);
		}
	}

	@Override
	public void onLocationChanged(final Location location) {
		tics++;

		boolean fromGps = false;
		boolean sendUpdate = false;
		long time = SystemClock.elapsedRealtime();

		if (LocationManager.NETWORK_PROVIDER.equals(location.getProvider())) {
			if (useNetwork && (gpsStatus == GPS_OFF || (gpsStatus == GPS_SEARCHING && time > lastLocationMillis + gpsLocationTimeout))) {
				Log.d(TAG, "New location");
				lastKnownLocation = location;
				lastLocationMillis = time;
				isContinous = false;
				sendUpdate = true;
			} else {
				return;
			}
		} else {
			fromGps = true;
			long prevLocationMillis = lastLocationMillis;
			float prevSpeed = lastKnownLocation.getSpeed();
			float prevTrack = lastKnownLocation.getBearing();
			lastKnownLocation = location;
			if (lastKnownLocation.getSpeed() == 0 && prevTrack != 0) {
				lastKnownLocation.setBearing(prevTrack);
			}
			lastLocationMillis = time;
			sendUpdate = true;
			if (!Float.isNaN(nmeaGeoidHeight)) {
				lastKnownLocation.setAltitude(lastKnownLocation.getAltitude() + nmeaGeoidHeight);
			}
			if (justStarted) {
				justStarted = prevSpeed == 0;
			} else if (lastKnownLocation.getSpeed() > 0) {
				double a = 2 * 9.8 * (lastLocationMillis - prevLocationMillis) / 1000;
				if (Math.abs(lastKnownLocation.getSpeed() - prevSpeed) > a) {
					lastKnownLocation.setSpeed(prevSpeed);
				}
			}

			float smoothspeed = 0;
			float curspeed = lastKnownLocation.getSpeed();
			for (int i = speed.length - 1; i > 1; i--) {
				smoothspeed += speed[i];
				speed[i] = speed[i - 1];
			}
			smoothspeed += speed[1];
			if (speed[1] < speed[0] && speed[0] > curspeed) {
				speed[0] = (speed[1] + curspeed) / 2;
			}
			smoothspeed += speed[0];
			speed[1] = speed[0];
			lastKnownLocation.setSpeed(speed[1]);
			speed[0] = curspeed;
			smoothspeed = (speed[0] == 0 && speed[1] == 0) ? 0 : smoothspeed / speed.length;

			float avspeed = 0;
			for (float v : speedav) {
				avspeed += v;
			}
			avspeed /= speedav.length;
			if (tics % pause == 0) {
				if (avspeed > 0) {
					float diff = curspeed / avspeed;
					if (0.95 < diff && diff < 1.05) {
						System.arraycopy(speedav, 0, speedav, 1, speedav.length - 1);
						speedav[0] = curspeed;
					}
				}
				float fluct = 0;
				for (int i = speedavex.length - 1; i > 0; i--) {
					fluct += speedavex[i] / curspeed;
					speedavex[i] = speedavex[i - 1];
				}
				fluct += speedavex[0] / curspeed;
				speedavex[0] = curspeed;
				fluct /= speedavex.length;
				if (0.95 < fluct && fluct < 1.05) {
					System.arraycopy(speedavex, 0, speedav, 0, speedav.length);
					if (pause < 5) pause++;
				}
			}

			smoothSpeed = smoothspeed;
			avgSpeed = avspeed;
		}

		if (sendUpdate) {
			updateLocation();
		}
		isContinous = fromGps;
	}

	@Override
	public void onNmeaReceived(long timestamp, String nmea) {
		if (nmea.indexOf('\n') == 0) return;
		if (nmea.indexOf('\n') > 0) {
			nmea = nmea.substring(0, nmea.indexOf('\n') - 1);
		}
		int len = nmea.length();
		if (len < 9) return;
		if (nmea.charAt(len - 3) == '*') {
			nmea = nmea.substring(0, len - 3);
		}
		String[] tokens = nmea.split(",");
		String sentenceId = tokens[0].length() > 5 ? tokens[0].substring(3, 6) : "";

		try {
			if ("GGA".equals(sentenceId) && tokens.length > 11) {
				String heightOfGeoid = tokens[11];
				if (!"".equals(heightOfGeoid)) {
					nmeaGeoidHeight = Float.parseFloat(heightOfGeoid);
				}
			} else if ("GSA".equals(sentenceId) && tokens.length > 17) {
				String hdop = tokens[16];
				String vdop = tokens[17];
				if (!"".equals(hdop)) {
					HDOP = Float.parseFloat(hdop);
				}
				if (!"".equals(vdop)) {
					VDOP = Float.parseFloat(vdop);
				}
			}
		} catch (NumberFormatException e) {
			Log.e(TAG, "NFE", e);
		} catch (ArrayIndexOutOfBoundsException e) {
			Log.e(TAG, "AIOOBE", e);
		}
	}

	@Override
	public void onProviderDisabled(String provider) {
		updateProvider(provider, false);
	}

	@Override
	public void onProviderEnabled(String provider) {
		updateProvider(provider, true);
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		if (LocationManager.GPS_PROVIDER.equals(provider)) {
			if (status == LocationProvider.TEMPORARILY_UNAVAILABLE || status == LocationProvider.OUT_OF_SERVICE) {
				tearTrack();
				updateNotification();
			}
		}
	}

	public class LocalBinder extends Binder implements ILocationService {
		@Override
		public void registerLocationCallback(ILocationListener callback) {
			updateProvider(callback);
			updateLocation(callback);
			locationCallbacks.add(callback);
		}

		@Override
		public void unregisterLocationCallback(ILocationListener callback) {
			locationCallbacks.remove(callback);
		}

		@Override
		public void registerTrackingCallback(ITrackingListener callback) {
			trackingCallbacks.add(callback);
		}

		@Override
		public void unregisterTrackingCallback(ITrackingListener callback) {
			trackingCallbacks.remove(callback);
		}

		@Override
		public boolean isLocating() {
			return locationsEnabled;
		}

		@Override
		public boolean isTracking() {
			return trackingEnabled;
		}

		@Override
		public float getHDOP() {
			return HDOP;
		}

		@Override
		public float getVDOP() {
			return VDOP;
		}

		@Override
		public Track getTrack() {
			return LocationService.this.getTrack();
		}

		@Override
		public Track getTrack(long start, long end) {
			return LocationService.this.getTrack(start, end);
		}

		@Override
		public void clearTrack() {
			LocationService.this.clearTrack();
		}

		@Override
		public long getTrackStartTime() {
			return LocationService.this.getTrackStartTime();
		}

		@Override
		public long getTrackEndTime() {
			return LocationService.this.getTrackEndTime();
		}
	}
}