/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2012 Andrey Novikov <http://andreynovikov.info/>
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

package com.borkozic;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnKeyListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.borkozic.data.Route;
import com.borkozic.data.Track;
import com.borkozic.overlay.CurrentTrackOverlay;
import com.borkozic.overlay.RouteOverlay;
import com.borkozic.util.AutoloadedRouteFilenameFilter;
import com.borkozic.util.FileList;
import com.borkozic.util.GpxFiles;
import com.borkozic.util.KmlFiles;
import com.borkozic.util.OziExplorerFiles;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Splash extends Activity implements OnClickListener
{
	private static final int MSG_FINISH = 1;
	private static final int MSG_ERROR = 2;
	private static final int MSG_STATUS = 3;
	private static final int MSG_PROGRESS = 4;
	private static final int MSG_ASK = 5;
	private static final int MSG_SAY = 6;

	private static final int RES_YES = 1;
	private static final int RES_NO = 2;

	private static final int PROGRESS_STEP = 10000;

	private int result;
	private boolean wait;
	protected String savedMessage;
	private ProgressBar progress;
	private TextView message;
	private Button gotit;
	private Button yes;
	private Button no;
	private Button quit;
	protected Borkozic application;
	//Permission atributes
	private final static int ALL_PERMISSIONS_RESULT = 101;
	ArrayList<String> permissions = new ArrayList<>();
	ArrayList<String> permissionsToRequest;
	ArrayList<String> permissionsRejected = new ArrayList<>();

	private File getAppBaseDir() {
		File externalDir = getExternalFilesDir(null);
		if (externalDir != null) {
			return externalDir;
		}
		File internalDir = getFilesDir();
		if (internalDir != null) {
			Log.w("Splash", "Using internal files dir as fallback");
			return internalDir;
		}
		// Краен случай - не би трябвало да се случи
		throw new RuntimeException("Cannot get app base directory");
	}
	@SuppressLint("NewApi")
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		/*
		permissions.add(Manifest.permission.NAVIGATION);
		permissions.add(Manifest.permission.READ_MAP_DATA);
		permissions.add(Manifest.permission.READ_PREFERENCES);
		permissions.add(Manifest.permission.RECEIVE_LOCATION);
		permissions.add(Manifest.permission.RECEIVE_TRACK);
		permissions.add(Manifest.permission.WRITE_MAP_DATA);
		*/
		permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
		//permissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
		//permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE);
		permissions.add(android.Manifest.permission.INTERNET);
		permissions.add(android.Manifest.permission.ACCESS_COARSE_LOCATION);
		permissions.add(android.Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			permissions.add(android.Manifest.permission.POST_NOTIFICATIONS);
		}

		permissionsToRequest = findUnAskedPermissions(permissions);
		application = (Borkozic) getApplication();
		/**/
		PreferenceManager.setDefaultValues(this, R.xml.pref_behavior, true);
		PreferenceManager.setDefaultValues(this, R.xml.pref_folder, true);
		PreferenceManager.setDefaultValues(this, R.xml.pref_location, true);
		PreferenceManager.setDefaultValues(this, R.xml.pref_display, true);
		PreferenceManager.setDefaultValues(this, R.xml.pref_unit, true);
		PreferenceManager.setDefaultValues(this, R.xml.pref_tracking, true);
		PreferenceManager.setDefaultValues(this, R.xml.pref_waypoint, true);
		PreferenceManager.setDefaultValues(this, R.xml.pref_route, true);
		PreferenceManager.setDefaultValues(this, R.xml.pref_navigation, true);
		PreferenceManager.setDefaultValues(this, R.xml.pref_general, true);

		setContentView(R.layout.act_splash);
		/*
		if (application.isPaid)
		{
			findViewById(R.id.paid).setVisibility(View.VISIBLE);
		}
		*/
		progress = findViewById(R.id.progress);//PROGRESBAR
		message = findViewById(R.id.message);//TEXTvIEW

		message.setText(getString(R.string.msg_wait));
		progress.setMax(PROGRESS_STEP * 4);

		yes = findViewById(R.id.yes);//Button Yes
		yes.setOnClickListener(this);
		no = findViewById(R.id.no);//Button No
		no.setOnClickListener(this);
		gotit = findViewById(R.id.gotit);
		gotit.setOnClickListener(this);
		quit = findViewById(R.id.quit);
		quit.setOnClickListener(this);

		wait = true;

		showEula();

		if (!application.mapsInited)
		{
			new InitializationThread(progressHandler).start();
		}
		else
		{
			progressHandler.sendEmptyMessage(MSG_FINISH);
		}
	}
	//@RequiresApi(api = Build.VERSION_CODES.M)
	private void showEula()
	{
		final SpannableString message;
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		boolean hasBeenShown = prefs.getBoolean(getString(R.string.app_eulaaccepted), false);

		if (!hasBeenShown)
		{
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N)
			{// Do things the Android M way
				message = new SpannableString(Html.fromHtml(getString(R.string.app_eula).replace("/n", "<br/>"),Html.FROM_HTML_MODE_LEGACY));
			}
			else
			{// Do things the pre-Android M way
				message = new SpannableString(Html.fromHtml(getString(R.string.app_eula).replace("/n", "<br/>")));
			}

			Linkify.addLinks(message, Linkify.WEB_URLS);

			AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(getString(R.string.app_name)).setIcon(R.drawable.icon).setMessage(message)
					.setPositiveButton(android.R.string.ok, new Dialog.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialogInterface, int i)
						{
							prefs.edit().putBoolean(getString(R.string.app_eulaaccepted), true).commit();
							wait = false;
							dialogInterface.dismiss();
						}
					}).setOnKeyListener(new OnKeyListener() {
						@Override
						public boolean onKey(DialogInterface dialoginterface, int keyCode, KeyEvent event)
						{
							return !(keyCode == KeyEvent.KEYCODE_HOME);
						}
					}).setCancelable(false);

			AlertDialog d = builder.create();

			d.show();
			// Make the textview clickable. Must be called after show()
			((TextView) d.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
		}
		else
		{
			wait = false;
		}
		//Ето тук преди да е зазпочнало всичко е необходимо permissions за всичко

		if (permissionsToRequest.size() > 0 && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
			requestPermissions(permissionsToRequest.toArray(new String[permissionsToRequest.size()]), ALL_PERMISSIONS_RESULT);
		} else {
			//Toast.makeText(context,"Permissions already granted.", Toast.LENGTH_LONG).show();
		}
	}

	final Handler progressHandler = new Handler() {
		public void handleMessage(Message msg)
		{
			switch (msg.what)
			{
				case MSG_STATUS:
					message.setText(msg.getData().getString("message"));
					break;
				case MSG_PROGRESS:
					int total = msg.getData().getInt("total");
					progress.setProgress(total);
					break;
				case MSG_ASK:
					progress.setVisibility(View.GONE);
					savedMessage = message.getText().toString();
					message.setText(msg.getData().getString("message"));
					result = 0;
					yes.setVisibility(View.VISIBLE);
					no.setVisibility(View.VISIBLE);
					break;
				case MSG_SAY:
					progress.setVisibility(View.GONE);
					savedMessage = message.getText().toString();
					message.setText(msg.getData().getString("message"));
					result = 0;
					gotit.setVisibility(View.VISIBLE);
					break;
				case MSG_FINISH:
					startActivity(new Intent(Splash.this, MapActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK).putExtras(getIntent()));
					finish();
					break;
				case MSG_ERROR:
					progress.setVisibility(View.INVISIBLE);
					message.setText(msg.getData().getString("message"));
					quit.setVisibility(View.VISIBLE);
					break;
			}
		}
	};

	private class InitializationThread extends Thread
	{
		Handler mHandler;
		int total;

		InitializationThread(Handler h)
		{
			mHandler = h;
		}


		public void run()
		{
			while (wait)
			{
				try
				{
					sleep(100);
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
			}

			total = 0;

			Message msg = mHandler.obtainMessage(MSG_STATUS);
			Bundle b = new Bundle();
			b.putString("message", getString(R.string.msg_initializingdata));
			msg.setData(b);
			mHandler.sendMessage(msg);

			Resources resources = getResources();
			SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(Splash.this);


			Log.d("Splash", "Step 1: start location service");
			// start location service
			application.enableLocating(settings.getBoolean(getString(R.string.lc_locate), true));

// ⚠️ Игнорираме старата настройка и винаги използваме app-specific storage
			String rootPath = getAppBaseDir().getAbsolutePath() + File.separator + resources.getString(R.string.def_folder_prefix);

// Записваме новия път в настройките, за да се помни за в бъдеще
			Editor editor = settings.edit();
			editor.putString(getString(R.string.pref_folder_root), rootPath);
			editor.apply();

			Log.d("Splash", "Step 2: root path = " + rootPath);
			File root = new File(rootPath);
			if (!root.exists()) {
				boolean created = false;
				try {
					created = root.mkdirs();
				} catch (Exception e) {
					Log.e("Splash", "mkdirs exception", e);
				}
				if (!created) {
					Log.e("Splash", "Failed to create root directory: " + rootPath);
					// Допълнителна информация за parent директорията
					File parent = root.getParentFile();
					if (parent != null) {
						Log.e("Splash", "Parent exists: " + parent.exists() + " canWrite: " + parent.canWrite());
					} else {
						Log.e("Splash", "Parent is null");
					}
					msg = mHandler.obtainMessage(MSG_ERROR);
					Bundle errorBundle = new Bundle();         // ✅ ново име
					errorBundle.putString("message", "Cannot create root directory");
					msg.setData(errorBundle);
					mHandler.sendMessage(msg);
					return;
				}
				try {
					new File(root, ".nomedia").createNewFile();
				} catch (IOException e) {
					Log.w("Splash", "Failed to create .nomedia", e);
				}
			} else {
				Log.d("Splash", "Root directory already exists");
			}

			// check maps folder existence
			String mapPath = getAppBaseDir().getAbsolutePath() + File.separator + resources.getString(R.string.def_folder_map);
			File mapdir = new File(mapPath);
			editor.putString(getString(R.string.pref_folder_map), mapPath);

// ⚠️ Игнорираме старата настройка pref_folder_map_old
			String oldmap = settings.getString(getString(R.string.pref_folder_map_old), null);
			Log.d("Splash", "Step 3: oldmap (ignored) = " + oldmap);

// Уверяваме се, че mapdir съществува
			if (!mapdir.exists()) {
				boolean created = mapdir.mkdirs();
				if (!created) {
					Log.e("Splash", "Failed to create map directory: " + mapdir.getAbsolutePath());
				}
			}

			// check data folder existence
// data folder
			File datadir = new File(getAppBaseDir(), resources.getString(R.string.def_folder_data));
			Log.d("Splash", "data directory: " + datadir.getAbsolutePath());

			if (!datadir.exists()) {
				boolean created = datadir.mkdirs();
				if (!created) {
					Log.e("Splash", "Failed to create data directory: " + datadir.getAbsolutePath());
				}
			}

// Копиране на zoni (ако искаш да се изпълнява всеки път – както в оригинала)
			try {
				application.copyAssets("zoni", datadir);
				Log.d("Splash", "zoni copied successfully");
			} catch (Exception e) {
				Log.e("Splash", "Error copying zoni", e);
			}

			// check icons folder existence
			File iconsdir = new File(getAppBaseDir(), resources.getString(R.string.def_folder_icon));

			if (!iconsdir.exists()) {
				boolean created = iconsdir.mkdirs();
				if (created) {
					try {
						new File(iconsdir, ".nomedia").createNewFile();
					} catch (IOException e) {
						Log.e("Splash", "Failed to create .nomedia file", e);
					}
				} else {
					Log.e("Splash", "Failed to create icons directory");
				}
			}
			try {
				application.copyAssets("icons", iconsdir);
			} catch (Exception e) {
				Log.e("Splash", "Error copying icons", e);
			}

			//File sasdir = new File(settings.getString(getString(R.string.pref_folder_sas), Environment.getExternalStorageDirectory() + File.separator + resources.getString(R.string.def_folder_sas)));
			File sasdir = new File(settings.getString(getString(R.string.pref_folder_sas), getAppBaseDir().getAbsolutePath() + File.separator + resources.getString(R.string.def_folder_sas)));
			// check planes folder existence//resources.getString(R.string.def_folder_plane)
			//android.util.Log.i("Splash",sasdir.toString());
			//File planesdir = new File(settings.getString(getString(R.string.pref_folder_plane), Environment.getExternalStorageDirectory() + File.separator + resources.getString(R.string.def_folder_plane)));
			File planesdir = new File(settings.getString(getString(R.string.pref_folder_plane), getAppBaseDir().getAbsolutePath() + File.separator + resources.getString(R.string.def_folder_plane)));
			//android.util.Log.i("Splash",planesdir.toString());

			//File dirL = new File(Environment.getExternalStorageDirectory() + File.separator + "Borkozic/planes/L39");
			File dirL = new File(getAppBaseDir(), "planes/L39");
			//File dirPC = new File(Environment.getExternalStorageDirectory() + File.separator + "Borkozic/planes/PC9");
			File dirPC = new File(getAppBaseDir(), "planes/PC9" );
			File dirMiG = new File(getAppBaseDir(), "planes/MiG29");

			//android.util.Log.i("Splash",dirMiG.toString());
//resources.getString(R.string.def_folder_plane)
			// initialize paths
			application.setRootPath(root.getAbsolutePath());
			application.setMapPath(mapdir.getAbsolutePath());
			application.setDataPath(Borkozic.PATH_DATA, datadir.getAbsolutePath());
			//android.util.Log.i("Splash", application.dataPath);
			application.setDataPath(Borkozic.PATH_SAS, sasdir.getAbsolutePath());
			application.setDataPath(Borkozic.PATH_ICONS, iconsdir.getAbsolutePath());
			application.setDataPath(Borkozic.PATH_PLANES, planesdir.getAbsolutePath());
			// put Emer.txt, Norm.txt, 100001.txt and so on in folder if no any found

			//File l39dir = new File(planesdir + File.separator + "l39");
//todo - да попита иска ли потребителят да се изтрият файловете с процедурите от папка самолети и да се запишат на ново
			if (!dirL.exists())
			{
				try
				{	dirL.mkdirs();
					application.copyAssets("planes/L39", dirL);
				}
				catch (Exception e)
				{
					android.util.Log.e("Spalsh", "Error_Create folders", e);
				}
			} else
			{//Когато искам да запиша нови процедури в папките само тогава се отчеква защото вече съществуват
				try
				{
					application.copyAssets("planes/L39", dirL);
				}
				catch (Exception e)
				{
					android.util.Log.e("Spalsh", "Error_Create folders", e);
					//e.printStackTrace();
				}
			}
			if (!dirPC.exists())
			{
				try
				{	dirPC.mkdirs();
					application.copyAssets("planes/PC9", dirPC);
				}
				catch (Exception e)
				{e.printStackTrace();}
			}
			if (!dirMiG.exists())
			{
				try
				{	dirMiG.mkdirs();
					application.copyAssets("planes/MiG29", dirMiG);
				}
				catch (Exception e)
				{e.printStackTrace();}
			}
			// initialize data
			application.installData();

			// start tracking service
			application.enableTracking(settings.getBoolean(getString(R.string.lc_track), true));

			// read default waypoints
			File wptFile = new File(application.dataPath, "myWaypoints.wpt");
			if (wptFile.exists() && wptFile.canRead())
			{
				try
				{
					application.addWaypoints(OziExplorerFiles.loadWaypointsFromFile(wptFile, application.charset));
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
			}
			// read track tail
			if (settings.getBoolean(getString(R.string.pref_showcurrenttrack), true))
			{
				application.currentTrackOverlay = new CurrentTrackOverlay(Splash.this);
				if (settings.getBoolean(getString(R.string.pref_tracking_currentload), resources.getBoolean(R.bool.def_tracking_currentload)))
				{
					int length = Integer.parseInt(settings.getString(getString(R.string.pref_tracking_currentlength), getString(R.string.def_tracking_currentlength)));
					// TODO Move this to proper class
					File pathTo = new File(application.dataPath, "myTrack.db");
					try
					{
						SQLiteDatabase trackDB = SQLiteDatabase.openDatabase(pathTo.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
						Cursor cursor = trackDB.rawQuery("SELECT * FROM track ORDER BY _id DESC LIMIT " + length, null);
						if (cursor.getCount() > 0)
						{
							Track track = new Track();
							for (boolean hasItem = cursor.moveToLast(); hasItem; hasItem = cursor.moveToPrevious())
							{
								double latitude = cursor.getDouble(cursor.getColumnIndex("latitude"));
								double longitude = cursor.getDouble(cursor.getColumnIndex("longitude"));
								double altitude = cursor.getDouble(cursor.getColumnIndex("elevation"));
								double speed = cursor.getDouble(cursor.getColumnIndex("speed"));
								double bearing = cursor.getDouble(cursor.getColumnIndex("track"));
								double accuracy = cursor.getDouble(cursor.getColumnIndex("accuracy"));
								int code = cursor.getInt(cursor.getColumnIndex("code"));
								long time = cursor.getLong(cursor.getColumnIndex("datetime"));
								boolean continous = cursor.isFirst() || cursor.isLast() ? false : code == 0;
								track.addPoint(continous, latitude, longitude, altitude, speed, bearing, accuracy, time);
							}
							track.show = true;
							application.currentTrackOverlay.setTrack(track);
						}
						cursor.close();
						trackDB.close();
					}
					catch (Exception e)
					{
						Log.e("Splash", "Read track tail", e);
					}
				}
			}
			// load routes
			if (settings.getBoolean(getString(R.string.pref_route_preload), resources.getBoolean(R.bool.def_route_preload)))
			{
				boolean hide = settings.getBoolean(getString(R.string.pref_route_preload_hidden), resources.getBoolean(R.bool.def_route_preload_hidden));
				List<File> files = FileList.getFileListing(new File(application.dataPath), new AutoloadedRouteFilenameFilter());
				for (File file : files)
				{
					List<Route> routes = null;
					try
					{
						String lc = file.getName().toLowerCase();
						if (lc.endsWith(".rt2") || lc.endsWith(".rte"))
						{
							routes = OziExplorerFiles.loadRoutesFromFile(file, application.charset);
						}
						else if (lc.endsWith(".kml"))
						{
							routes = KmlFiles.loadRoutesFromFile(file);
						}
						else if (lc.endsWith(".gpx"))
						{
							routes = GpxFiles.loadRoutesFromFile(file);
						}
						application.addRoutes(routes);
						for (Route route : routes)
						{
							//route.show = !hide;
							RouteOverlay newRoute = new RouteOverlay(Splash.this, route);
							application.routeOverlays.add(newRoute);
						}
					}
					catch (Exception e)
					{
						e.printStackTrace();
					}
				}
			}

			total += PROGRESS_STEP;
			msg = mHandler.obtainMessage(MSG_PROGRESS);
			b = new Bundle();
			b.putInt("total", total);
			msg.setData(b);
			mHandler.sendMessage(msg);

			// put world map if no any found
			String[] mapfiles = mapdir.list();
			if (mapfiles != null && mapfiles.length == 0)
				application.copyAssets("maps", mapdir);

			msg = mHandler.obtainMessage(MSG_STATUS);
			b = new Bundle();
			b.putString("message", getString(R.string.msg_initializingmaps));
			msg.setData(b);
			mHandler.sendMessage(msg);

			// initialize maps
			application.initializeMaps();

			total += PROGRESS_STEP;
			msg = mHandler.obtainMessage(MSG_PROGRESS);
			b = new Bundle();
			b.putInt("total", total);
			msg.setData(b);
			mHandler.sendMessage(msg);

			msg = mHandler.obtainMessage(MSG_STATUS);
			b = new Bundle();
			b.putString("message", getString(R.string.msg_initializingplugins));
			msg.setData(b);
			mHandler.sendMessage(msg);

			// initialize plugins
			application.initializePlugins();

			total += PROGRESS_STEP;
			msg = mHandler.obtainMessage(MSG_PROGRESS);
			b = new Bundle();
			b.putInt("total", total);
			msg.setData(b);
			mHandler.sendMessage(msg);

			msg = mHandler.obtainMessage(MSG_STATUS);
			b = new Bundle();
			b.putString("message", getString(R.string.msg_initializingview));
			msg.setData(b);
			mHandler.sendMessage(msg);

			// initialize current map
			application.initializeMapCenter();

			total += PROGRESS_STEP;
			msg = mHandler.obtainMessage(MSG_PROGRESS);
			b = new Bundle();
			b.putInt("total", total);
			msg.setData(b);
			mHandler.sendMessage(msg);
			mHandler.sendEmptyMessage(MSG_FINISH);
		}
	}
	private ArrayList findUnAskedPermissions(ArrayList<String> wanted) {
		ArrayList result = new ArrayList();

		for (String perm : wanted) {
			if (!hasPermission(perm)) {
				result.add(perm);
			}
		}

		return result;
	}

	private boolean hasPermission(String permission) {
		if (canAskPermission()) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				return (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED);
			}
		}
		return true;
	}

	private boolean canAskPermission() {
		return (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1);
	}
	@TargetApi(Build.VERSION_CODES.M)
	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		switch (requestCode) {
			case ALL_PERMISSIONS_RESULT:
				//Log.d(TAG, "onRequestPermissionsResult");
				for (String perms : permissionsToRequest) {
					if (!hasPermission(perms)) {
						permissionsRejected.add(perms);
					}
				}

				if (permissionsRejected.size() > 0) {
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
						if (shouldShowRequestPermissionRationale(permissionsRejected.get(0))) {
							String msg = "These permissions are mandatory for the application. Please allow access.";
							showMessageOKCancel(msg,
									new DialogInterface.OnClickListener() {
										@Override
										public void onClick(DialogInterface dialog, int which) {
											if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
												requestPermissions(permissionsRejected.toArray(
														new String[permissionsRejected.size()]), ALL_PERMISSIONS_RESULT);
											}
										}
									});
							return;
						}
					}
				} else {
					//Toast.makeText(context, "Permissions garanted.", Toast.LENGTH_LONG).show();
				}
				break;
		}
	}

	private void showMessageOKCancel(String message, DialogInterface.OnClickListener okListener) {
		new AlertDialog.Builder(this)
				.setMessage(message)
				.setPositiveButton("OK", okListener)
				.setNegativeButton("Cancel", null)
				.create()
				.show();
	}







	@Override
	public void onClick(View v)
	{
		switch (v.getId())
		{
			case R.id.yes:
				result = RES_YES;
				break;
			case R.id.no:
				result = RES_NO;
				break;
			case R.id.quit:
				finish();
				break;
		}
		gotit.setVisibility(View.GONE);
		yes.setVisibility(View.GONE);
		no.setVisibility(View.GONE);
		progress.setVisibility(View.VISIBLE);
		message.setText(savedMessage);
		wait = false;
	}
}
