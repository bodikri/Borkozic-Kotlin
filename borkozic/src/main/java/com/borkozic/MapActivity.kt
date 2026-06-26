/*
Това е централното активити когато е заредена картата. От него се управляват и активират всички дейности.
 */
package com.borkozic

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Message
import androidx.preference.PreferenceManager
import android.util.Log
import android.util.Pair
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.Window
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.widget.Button
import android.widget.ImageButton
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.borkozic.area.AreaDetails
import com.borkozic.area.AreaEdit
import com.borkozic.area.AreaList
import com.borkozic.area.AreaListActivity
import com.borkozic.data.Area
import com.borkozic.data.Route
import com.borkozic.data.Track
import com.borkozic.data.Track.TrackPoint
import com.borkozic.data.Waypoint
import com.borkozic.data.WaypointSet
import com.borkozic.location.BaseLocationService
import com.borkozic.location.ILocationListener
import com.borkozic.location.ILocationService
import com.borkozic.location.LocationService
import com.borkozic.map.MapInformation
import com.borkozic.navigation.BaseNavigationService
import com.borkozic.navigation.NavigationService
import com.borkozic.overlay.AccuracyOverlay
import com.borkozic.overlay.AreaOverlay
import com.borkozic.overlay.CurrentTrackOverlay
import com.borkozic.overlay.DistanceOverlay
import com.borkozic.overlay.MapObjectsOverlay
import com.borkozic.overlay.NavigationOverlay
import com.borkozic.overlay.RouteOverlay
import com.borkozic.overlay.ScaleOverlay
import com.borkozic.overlay.TrackOverlay
import com.borkozic.overlay.WaypointsOverlay
import com.borkozic.route.RouteDetails
import com.borkozic.route.RouteEdit
import com.borkozic.route.RoutePointListDialog
import com.borkozic.route.RouteList
import com.borkozic.route.RouteListActivity
import com.borkozic.route.RouteStart
import com.borkozic.track.TrackExportDialog
import com.borkozic.track.TrackListActivity
import com.borkozic.util.Astro.isDaytime
import com.borkozic.util.CoordinateParser.parse
import com.borkozic.util.Geo.belowAboveGlidePath
import com.borkozic.util.OziExplorerFiles.Companion.loadRoutesFromFile
import com.borkozic.util.StringFormatter
import com.borkozic.util.StringFormatter.coordinate
import com.borkozic.util.StringFormatter.coordinates
import com.borkozic.util.StringFormatter.distanceC
import com.borkozic.util.StringFormatter.distanceH
import com.borkozic.util.StringFormatter.timeHSec
import com.borkozic.waypoint.OnWaypointActionListener
import com.borkozic.waypoint.WaypointFileList
import com.borkozic.waypoint.WaypointInfo
import com.borkozic.waypoint.WaypointListActivity
import com.borkozic.waypoint.WaypointProject
import com.borkozic.waypoint.WaypointProperties
import net.londatiga.android.ActionItem
import net.londatiga.android.QuickAction3D
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.borkozic.ui.SidePanel
import com.borkozic.ui.SidePanelAction
import com.borkozic.ui.BorkozicTheme
import java.io.File
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Stack
import java.util.TimeZone
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.Array
import kotlin.Boolean
import kotlin.DoubleArray
import kotlin.Exception
import kotlin.Float
import kotlin.IllegalArgumentException
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.math.abs
import kotlin.math.floor
import kotlin.plus
import kotlin.synchronized
import kotlin.times

//import androidx.core.app.FragmentManager;
//import android.support.v7.app.AppCompatActivity;
class MapActivity : AppCompatActivity(), View.OnClickListener, OnSharedPreferenceChangeListener,
    OnWaypointActionListener, OnSeekBarChangeListener {
    // main preferences
    protected var precisionFormat: String = "%.0f"
    protected var speedFactor: Double = 0.0
    protected var speedAbbr: String? = null
    protected var elevationFactor: Double = 0.0
    protected var elevationAbbr: String? = null
    protected var zeroElevation: Double = 0.0
    private var lastElevation = 0.0
    protected var renderInterval: Int = 0
    protected var magInterval: Int = 0
    protected var autoDim: Boolean = false
    protected var dimInterval: Int = 0
    protected var dimValue: Int = 0
    protected var showDistance: Int = 0
    protected var showAccuracy: Boolean = false
    protected var followOnLocation: Boolean = false
    /** WaypointSet that collects all waypoints added to a route during editing for reuse across routes. */
    private var routeWaypointSet: WaypointSet? = null
    /** Global counter for unique waypoint names across all routes in this session. */
    private var routeWaypointNameCounter = 0
    protected var exitConfirmation: Int = 0
    private var secondBack = false
    private var backToast: Toast? = null

    private var coordinates: TextView? = null
    private var satInfo: TextView? = null
    private var accuracy: TextView? = null

    private var waypointName: TextView? = null
    private var waypointExtra: TextView? = null
    private var routeName: TextView? = null
    private var routeExtra: TextView? = null
    private var areaName: TextView? = null
    private var areaExtra: TextView? = null

    //private TextView distanceValue;
    //private TextView distanceUnit;
    //private TextView bearingValue;
    //private TextView bearingUnit;
    private var belowaboveValue: TextView? = null
    private var belowaboveUnit: TextView? = null
    private var belowaboveName: TextView? = null
    private var turnValue: TextView? = null

    private var speedValue: TextView? = null
    private var speedUnit: TextView? = null
    private var trackValue: TextView? = null
    private var trackUnit: TextView? = null
    private var elevationName: TextView? = null
    private var elevationValue: TextView? = null
    private var elevationUnit: TextView? = null
    private var xtkValue: TextView? = null
    private var xtkUnit: TextView? = null

    private var currentFile: TextView? = null
    private var mapZoom: TextView? = null

    protected var trackBar: SeekBar? = null
    protected var waitBar: TextView? = null
    @JvmField
    var map: MapView? = null
    protected var wptQuickActionAddToRoute: QuickAction3D? =
        null //активира действие при натискане за добавяне на точка към маршрут
    protected var wptQuickActionAddToArea: QuickAction3D? = null
    /** Quick action shown when tapping a waypoint outside editing mode (Edit/Navigate). */
    protected var wptQuickAction: QuickAction3D? = null
    /** Quick action shown when tapping a route waypoint during route edit (Edit / Add to end). */
    protected var wptQuickActionRouteEdit: QuickAction3D? = null
    protected var rteQuickAction: QuickAction3D? = null
    protected var mobQuickAction: QuickAction3D? = null
    private var dimView: ViewGroup? = null

    protected var application: Borkozic? = null

    protected var executorThread: ExecutorService = Executors.newSingleThreadExecutor()
    private var finishHandler: FinishHandler? = null

    private var waypointSelected = -1
    private var routeSelected = -1
    private var areaSelected = -1
    private var mapObjectSelected: Long = -1

    private var locationService: ILocationService? = null
    var navigationService: NavigationService? = null

    private var lastKnownLocation: Location? = null
    protected var lastRenderTime: Long = 0
    protected var lastDim: Long = 0
    protected var lastMagnetic: Long = 0
    private var lastGeoid = true

    private var animationSet = false
    private var isFullscreen = false
    private var keepScreenOn = false
    private var activeActions: MutableList<String?>? = null

    // ── Compose side panel state (състояние на страничния панел) ────────
    private var isPanelOpen by mutableStateOf(false)
    private var isFollowingState by mutableStateOf(false)
    private var isLocatingState by mutableStateOf(false)
    private var isTrackingState by mutableStateOf(false)
    var disable: LightingColorFilter = LightingColorFilter(-0x1, -0xaaaaab)

    protected var ready: Boolean = false
    private var restarting = false

    /* Called when the activity is first created. */
    @SuppressLint("ShowToast")
    override fun attachBaseContext(newBase: Context) {
        // Apply saved locale BEFORE resources are accessed — only safe place
        val locale = BaseApplication.savedLocale
        if (locale != null) {
            val config = android.content.res.Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            super.attachBaseContext(newBase.createConfigurationContext(config))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.e(TAG, "onCreate()")

        ready = false
        isFullscreen = false

        backToast = Toast.makeText(this, R.string.backQuit, Toast.LENGTH_SHORT)
        finishHandler = FinishHandler(this)

        application = getApplication() as Borkozic?

        // FIXME Should find a better place for this
        application!!.mapObjectsOverlay = MapObjectsOverlay(this)

        // check if called after crash
        if (!application!!.mapsInited) {
            restarting = true
            startActivity(
                Intent(
                    this,
                    Splash::class.java
                ).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtras(getIntent())
            )
            finish()
            return
        }

        application!!.setMapActivity(this)

        val settings = PreferenceManager.getDefaultSharedPreferences(this)!!
        setRequestedOrientation(
            settings.getString(getString(R.string.pref_orientation), "-1")!!.toInt()
        )
        //setRequestedOrientation(SCREEN_ORIENTATION_PORTRAIT);
        settings.registerOnSharedPreferenceChangeListener(this)
        val resources = getResources()
        if (settings.getBoolean(
                getString(R.string.pref_hideactionbar),
                resources.getBoolean(R.bool.def_hideactionbar)
            )
        ) {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
        }

        setContentView(R.layout.act_main)
        coordinates = findViewById<View?>(R.id.coordinates) as TextView?
        satInfo = findViewById<View?>(R.id.sats) as TextView?
        accuracy = findViewById<View?>(R.id.accuracy_satinfo) as TextView
        currentFile = findViewById<View?>(R.id.currentfile) as TextView?
        mapZoom = findViewById<View?>(R.id.currentzoom) as TextView?
        waypointName = findViewById<View?>(R.id.waypointname) as TextView?
        waypointExtra = findViewById<View?>(R.id.waypointextra) as TextView?
        routeName = findViewById<View?>(R.id.routename) as TextView?
        routeExtra = findViewById<View?>(R.id.routeextra) as TextView?
        areaName = findViewById<View?>(R.id.areaname) as TextView?
        areaExtra = findViewById<View?>(R.id.areaextra) as TextView?
        speedValue = findViewById<View?>(R.id.speed) as TextView?
        speedUnit = findViewById<View?>(R.id.speedunit) as TextView?
        trackValue = findViewById<View?>(R.id.track) as TextView?
        trackUnit = findViewById<View?>(R.id.trackunit) as TextView
        elevationValue = findViewById<View?>(R.id.elevation) as TextView?
        elevationName = findViewById<View?>(R.id.elevationname) as TextView?
        elevationUnit = findViewById<View?>(R.id.elevationunit) as TextView?
        //distanceValue = (TextView) findViewById(R.id.distance);
        //distanceUnit = (TextView) findViewById(R.id.distanceunit);
        belowaboveValue = findViewById<View?>(R.id.abovebelowGS) as TextView?
        belowaboveUnit = findViewById<View?>(R.id.abovebelowGSunit) as TextView?
        belowaboveName = findViewById<View?>(R.id.abovebelowGSname) as TextView?
        xtkValue = findViewById<View?>(R.id.xtk) as TextView?
        xtkUnit = findViewById<View?>(R.id.xtkunit) as TextView?
        //bearingValue = (TextView) findViewById(R.id.bearing);
        //bearingUnit = (TextView) findViewById(R.id.bearingunit);
        turnValue = findViewById<View?>(R.id.turn) as TextView?
        trackBar = findViewById<View?>(R.id.trackbar) as SeekBar?
        waitBar = findViewById<View?>(R.id.waitbar) as TextView
        map = findViewById<View?>(R.id.mapview) as MapView?

        // set button actions for edit panels (side panel buttons handled by Compose)
        findViewById<View?>(R.id.finishedit).setOnClickListener(this)
        findViewById<View?>(R.id.addpoint).setOnClickListener(this)
        findViewById<View?>(R.id.insertpoint).setOnClickListener(this)
        findViewById<View?>(R.id.removepoint).setOnClickListener(this)
        findViewById<View?>(R.id.orderpoints).setOnClickListener(this)
        findViewById<View?>(R.id.finishtrackedit).setOnClickListener(this)
        findViewById<View?>(R.id.cutafter).setOnClickListener(this)
        findViewById<View?>(R.id.cutbefore).setOnClickListener(this)

        // ── Side Panel (Compose) ─────────────────────────────────────────
        val panelOnLeft = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if (settings.getBoolean(getString(R.string.ui_drawer_open), false)) {
            isPanelOpen = true
        }
        val sidePanelView = findViewById<ComposeView>(R.id.side_panel)
        sidePanelView.setContent {
            SidePanel(
                isOpen = isPanelOpen,
                isOnLeft = panelOnLeft,
                onOpenChanged = { open ->
                    isPanelOpen = open
                    val editor = PreferenceManager.getDefaultSharedPreferences(this).edit()
                    editor.putBoolean(getString(R.string.ui_drawer_open), open)
                    editor.apply()
                },
                activeActions = activeActions?.filterNotNull().orEmpty(),
                isFollowing = isFollowingState,
                isLocating = isLocatingState,
                isTracking = isTrackingState,
                isFullscreen = isFullscreen,
                onAction = { action -> onSidePanelAction(action) },
            )
        }

        wptQuickActionAddToRoute = QuickAction3D(
            this,
            QuickAction3D.VERTICAL
        ) //ContextCompat.getDrawable(getActivity(), R.drawable.ic_action_add);
        wptQuickActionAddToRoute!!.addActionItem(
            ActionItem(
                qaAddWaypointToRoute,
                getString(R.string.menu_addtoroute),
                ResourcesCompat.getDrawable(getResources(), R.drawable.ic_action_add, null)
            )
        )
        wptQuickActionAddToRoute!!.setOnActionItemClickListener(waypointActionItemClickListener) //resources.getDrawable(R.drawable.ic_action_add)));

        wptQuickActionAddToArea = QuickAction3D(this, QuickAction3D.VERTICAL)
        wptQuickActionAddToArea!!.addActionItem(
            ActionItem(
                qaAddWaypointToArea,
                getString(R.string.menu_addtoarea),
                ResourcesCompat.getDrawable(getResources(), R.drawable.ic_action_add, null)
            )
        )
        wptQuickActionAddToArea!!.setOnActionItemClickListener(waypointActionItemClickListener) //resources.getDrawable(R.drawable.ic_action_add)));

        // Quick action for tapping a waypoint outside editing mode — Edit/Navigate
        wptQuickAction = QuickAction3D(this, QuickAction3D.VERTICAL)
        wptQuickAction!!.addActionItem(
            ActionItem(
                qaEditWaypoint,
                getString(R.string.menu_edit),
                ResourcesCompat.getDrawable(getResources(), R.drawable.ic_action_edit, null)
            )
        )
        wptQuickAction!!.addActionItem(
            ActionItem(
                qaNavigateToWaypoint,
                getString(R.string.menu_navigate),
                ResourcesCompat.getDrawable(getResources(), R.drawable.ic_action_directions, null)
            )
        )
        wptQuickAction!!.setOnActionItemClickListener(waypointActionItemClickListener)

        // Quick action for tapping a route waypoint during route editing — Edit / Add to end
        wptQuickActionRouteEdit = QuickAction3D(this, QuickAction3D.VERTICAL)
        wptQuickActionRouteEdit!!.addActionItem(
            ActionItem(
                qaEditRouteWaypoint,
                getString(R.string.menu_edit),
                ResourcesCompat.getDrawable(getResources(), R.drawable.ic_action_edit, null)
            )
        )
        wptQuickActionRouteEdit!!.addActionItem(
            ActionItem(
                qaAddRouteWaypointToEnd,
                getString(R.string.menu_addtoroute),
                ResourcesCompat.getDrawable(getResources(), R.drawable.ic_action_add, null)
            )
        )
        wptQuickActionRouteEdit!!.setOnActionItemClickListener(routeWaypointEditActionItemClickListener)

        rteQuickAction = QuickAction3D(this, QuickAction3D.VERTICAL)
        rteQuickAction!!.addActionItem(
            ActionItem(
                qaNavigateToWaypoint,
                getString(R.string.menu_thisnavpoint),
                ResourcesCompat.getDrawable(getResources(), R.drawable.ic_action_directions, null)
            )
        )
        rteQuickAction!!.setOnActionItemClickListener(routeActionItemClickListener) //ic_action_directions

        mobQuickAction = QuickAction3D(this, QuickAction3D.VERTICAL)
        mobQuickAction!!.addActionItem(
            ActionItem(
                qaNavigateToMapObject,
                getString(R.string.menu_navigate),
                ResourcesCompat.getDrawable(getResources(), R.drawable.ic_action_directions, null)
            )
        )
        mobQuickAction!!.setOnActionItemClickListener(mapObjectActionItemClickListener)

        trackBar!!.setOnSeekBarChangeListener(this)
        //Load plane logo and size from preferences
        map!!.planeLogo = settings.getString(getString(R.string.pref_plane_type), "L39")
        map!!.setMovingCursorSize(
            settings.getInt(
                "planelogosize",
                resources.getInteger(R.integer.def_planelogosize)
            )
        )
        map!!.initialize(application!!)

        dimView = RelativeLayout(this)
        //Зарежда навигация към точка ако има стартиран такъв (MapObject-Wpt)
        val navWpt: String = settings.getString(getString(R.string.nav_wpt), "")!!
        if ("" != navWpt && savedInstanceState == null) {
            val intent = Intent(getApplicationContext(), NavigationService::class.java).setAction(
                NavigationService.NAVIGATE_MAPOBJECT
            )
            intent.putExtra(NavigationService.EXTRA_NAME, navWpt)
            intent.putExtra(
                NavigationService.EXTRA_LATITUDE,
                settings.getFloat(getString(R.string.nav_wpt_lat), 0f).toDouble()
            )
            intent.putExtra(
                NavigationService.EXTRA_LONGITUDE,
                settings.getFloat(getString(R.string.nav_wpt_lon), 0f).toDouble()
            )
            intent.putExtra(
                NavigationService.EXTRA_PROXIMITY,
                settings.getInt(getString(R.string.nav_wpt_prx), 0)
            )
            startService(intent)
        }
        //По същият пример трябва да зарежда навигация към зона( към точка в центъра на зоната)
        /*
         String navАреа = settings.getString(getString(R.string.nav_ареа), "");
        if (!"".equals(navАреа) && savedInstanceState == null)
        {
            Intent intent = new Intent(getApplicationContext(), NavigationService.class).setAction(NavigationService.NAVIGATE_MAPOBJECT);
            intent.putExtra(NavigationService.EXTRA_NAME, navАреа);
            intent.putExtra(NavigationService.EXTRA_LATITUDE, (double) settings.getFloat(getString(R.string.nav_ареа_lat), 0));
            intent.putExtra(NavigationService.EXTRA_LONGITUDE, (double) settings.getFloat(getString(R.string.nav_ареа_lon), 0));
            intent.putExtra(NavigationService.EXTRA_PROXIMITY, settings.getInt(getString(R.string.nav_ареа_prx), 0));
            startService(intent);
        }
        */
        //Зарежда навигация по Маршрут ако има стартиран такъв
        val navRoute: String = settings.getString(getString(R.string.nav_route), "")!!
        if (("" != navRoute) && settings.getBoolean(
                getString(R.string.pref_navigation_loadlast),
                getResources().getBoolean(R.bool.def_navigation_loadlast)
            ) && savedInstanceState == null
        ) {
            val ndir = settings.getInt(getString(R.string.nav_route_dir), 0)
            val nwpt = settings.getInt(getString(R.string.nav_route_wpt), -1)
            try {
                var rt = -1
                var route: Route? = application!!.getRouteByFile(navRoute)
                if (route != null) {
                    route.show = true
                    rt = application!!.getRouteIndex(route)
                } else {
                    val rtf = File(navRoute)
                    // FIXME It's bad - it can be not a first route in a file
                    route = loadRoutesFromFile(rtf, application!!.charset!!).get(0)
                    rt = application!!.addRoute(route)
                }
                val newRoute = RouteOverlay(this, route)
                application!!.routeOverlays.add(newRoute)
                startForegroundService(
                    Intent(this, NavigationService::class.java).setAction(
                        NavigationService.NAVIGATE_ROUTE
                    ).putExtra(NavigationService.EXTRA_ROUTE_INDEX, rt)
                        .putExtra(NavigationService.EXTRA_ROUTE_DIRECTION, ndir)
                        .putExtra(NavigationService.EXTRA_ROUTE_START, nwpt)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start navigation", e)
            }
        }

        // set activity preferences
        onSharedPreferenceChanged(settings, getString(R.string.pref_exit))
        onSharedPreferenceChanged(settings, getString(R.string.pref_unitprecision))
        // set map preferences
        onSharedPreferenceChanged(settings, getString(R.string.pref_mapadjacent))
        onSharedPreferenceChanged(settings, getString(R.string.pref_mapcropborder))
        onSharedPreferenceChanged(settings, getString(R.string.pref_mapdrawborder))
        onSharedPreferenceChanged(settings, getString(R.string.pref_cursorcolor))
        onSharedPreferenceChanged(settings, getString(R.string.pref_grid_mapshow))
        onSharedPreferenceChanged(settings, getString(R.string.pref_grid_usershow))
        onSharedPreferenceChanged(settings, getString(R.string.pref_grid_preference))
        onSharedPreferenceChanged(settings, getString(R.string.pref_panelactions))
        onSharedPreferenceChanged(settings, getString(R.string.pref_maprotation))
        if (getIntent().getExtras() != null) onNewIntent(getIntent())

        ready = true
    }

    override fun onStart() {
        super.onStart()
        Log.e(TAG, "onStart()")
        (getWindow().getDecorView() as ViewGroup).addView(dimView)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.e(TAG, "onNewIntent()")
        if (intent.hasExtra("launch")) {
            val `object` = intent.getExtras()!!.getSerializable("launch")
            if (Class::class.java.isInstance(`object`)) {
                val launch = Intent(this, `object` as Class<*>)
                launch.putExtras(intent)
                launch.removeExtra("launch")
                startActivity(launch)
            }
        } else if (intent.hasExtra("lat") && intent.hasExtra("lon")) {
            val application = getApplication() as Borkozic
            application.ensureVisible(
                intent.getExtras()!!.getDouble("lat"),
                intent.getExtras()!!.getDouble("lon")
            )
        }
    }

    override fun onResume() {
        super.onResume()
        Log.e(TAG, "onResume()")

        val settings = PreferenceManager.getDefaultSharedPreferences(this)!!
        val resources = getResources()

        // update some preferences
        val planeType = settings.getString(getString(R.string.pref_plane_type), "L39")
        try {
            val def_plane = when (planeType) {
                "MiG29" -> "MiG29"
                else -> "L39"
            }
            getSupportActionBar()!!.setTitle(resources.getString(R.string.app_name) + "-" + def_plane)
        } catch (e: Exception) {
            Toast.makeText(
                this@MapActivity,
                "Неточен път към папката за процедури!",
                Toast.LENGTH_LONG
            ).show()
        }
        val speedIdx = settings.getString(getString(R.string.pref_unitspeed), "0")!!.toInt()
        speedFactor =
            resources.getStringArray(R.array.speed_factors)[speedIdx].toDouble() //множител който преобразува от м/с в км/ч или мили(както е избрано в  настройките)
        speedAbbr = resources.getStringArray(R.array.speed_abbrs)[speedIdx]
        speedUnit!!.setText(speedAbbr)
        val distanceIdx = settings.getString(getString(R.string.pref_unitdistance), "0")!!.toInt()
        val elevationIdx = settings.getString(getString(R.string.pref_unitelevation), "0")!!.toInt()
        elevationFactor =
            resources.getStringArray(R.array.elevation_factors)[elevationIdx].toDouble() //множител който преобразува височината от метри в фити
        elevationAbbr = resources.getStringArray(R.array.elevation_abbrs)[elevationIdx]
        elevationUnit!!.setText(elevationAbbr)
        StringFormatter.distanceFactor =
            resources.getStringArray(R.array.distance_factors)[distanceIdx].toDouble()
        StringFormatter.distanceAbbr = resources.getStringArray(R.array.distance_abbrs)[distanceIdx]
        StringFormatter.distanceShortFactor =
            resources.getStringArray(R.array.distance_factors_short)[distanceIdx].toDouble()
        StringFormatter.distanceShortAbbr =
            resources.getStringArray(R.array.distance_abbrs_short)[distanceIdx]
        StringFormatter.elevationFactor =
            resources.getStringArray(R.array.elevation_factors)[elevationIdx].toDouble()

        application!!.angleType =
            settings.getString(getString(R.string.pref_unitangle), "0")!!.toInt()
        trackUnit!!.setText((if (application!!.angleType == 0) "deg" else getString(R.string.degmag)))
        //bearingUnit.setText((application.angleType == 0 ? "deg" : getString(R.string.degmag)));
        application!!.coordinateFormat =
            settings.getString(getString(R.string.pref_unitcoordinate), "0")!!.toInt()
        application!!.sunriseType =
            settings.getString(getString(R.string.pref_unitsunrise), "0")!!.toInt()

        renderInterval = settings.getInt(
            getString(R.string.pref_maprenderinterval),
            resources.getInteger(R.integer.def_maprenderinterval)
        ) * 100
        followOnLocation = settings.getBoolean(
            getString(R.string.pref_mapfollowonloc),
            resources.getBoolean(R.bool.def_mapfollowonloc)
        )
        magInterval = resources.getInteger(R.integer.def_maginterval) * 1000
        showDistance = settings.getString(
            getString(R.string.pref_showdistance_int),
            getString(R.string.def_showdistance)
        )!!.toInt()
        showAccuracy = settings.getBoolean(getString(R.string.pref_showaccuracy), true)
        autoDim = settings.getBoolean(
            getString(R.string.pref_mapdim),
            resources.getBoolean(R.bool.def_mapdim)
        )
        dimInterval = settings.getInt(
            getString(R.string.pref_mapdiminterval),
            resources.getInteger(R.integer.def_mapdiminterval)
        ) * 1000
        dimValue = settings.getInt(
            getString(R.string.pref_mapdimvalue),
            resources.getInteger(R.integer.def_mapdimvalue)
        )

        map!!.setHideOnDrag(
            settings.getBoolean(
                getString(R.string.pref_maphideondrag),
                resources.getBoolean(R.bool.def_maphideondrag)
            )
        )
        map!!.setStrictUnfollow(
            !settings.getBoolean(
                getString(R.string.pref_unfollowontap),
                resources.getBoolean(R.bool.def_unfollowontap)
            )
        )
        map!!.setLookAhead(
            settings.getInt(
                getString(R.string.pref_lookahead),
                resources.getInteger(R.integer.def_lookahead)
            )
        )
        map!!.setTrackUp(
            settings.getString(
                getString(R.string.pref_maprotation),
                resources.getString(R.string.def_maprotation)
            )!!
        )
        map!!.setBestMapEnabled(
            settings.getBoolean(
                getString(R.string.pref_mapbest),
                resources.getBoolean(R.bool.def_mapbest)
            )
        )
        map!!.setBestMapInterval(
            settings.getInt(
                getString(R.string.pref_mapbestinterval),
                resources.getInteger(R.integer.def_mapbestinterval)
            ) * 1000
        )
        map!!.setCursorVector(
            settings.getString(
                getString(R.string.pref_cursorvector),
                getString(R.string.def_cursorvector)
            )!!.toInt(),
            settings.getInt(
                getString(R.string.pref_cursorvectormlpr),
                resources.getInteger(R.integer.def_cursorvectormlpr)
            )
        )
        map!!.setProximity(
            settings.getString(
                getString(R.string.pref_navigation_proximity),
                getString(R.string.def_navigation_proximity)
            )!!.toInt()
        ) // Задава близост при достигане на която навигацията автоматично превключва на следваща точка

        // prepare views
        customizeLayout(settings)
        findViewById<View?>(R.id.editroute).setVisibility(if (application!!.editingRoute != null || application!!.editingArea != null) View.VISIBLE else View.GONE) //появяа се само при режим на редакция на маршрут/зона

        if (application!!.editingTrack != null) {
            startEditTrack(application!!.editingTrack)
        }
        updateGPSStatus()
        updateNavigationStatus()
        // prepare overlays
        updateOverlays(settings, false)

        onSharedPreferenceChanged(settings, getString(R.string.pref_wakelock))
        map!!.setKeepScreenOn(keepScreenOn)

        // TODO move into application
        if (lastKnownLocation != null) {
            if (lastKnownLocation!!.getProvider() == LocationManager.GPS_PROVIDER) {
                updateMovingInfo(lastKnownLocation!!, true)
                updateNavigationInfo()
                dimScreen(lastKnownLocation!!)
            } else if (lastKnownLocation!!.getProvider() == LocationManager.NETWORK_PROVIDER) {
                dimScreen(lastKnownLocation!!)
            }
        }

        bindService(Intent(this, LocationService::class.java), locationConnection, BIND_AUTO_CREATE)
        bindService(
            Intent(this, NavigationService::class.java),
            navigationConnection,
            BIND_AUTO_CREATE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                broadcastReceiver,
                IntentFilter(BaseNavigationService.BROADCAST_NAVIGATION_STATUS),
                RECEIVER_NOT_EXPORTED
            )
            registerReceiver(
                broadcastReceiver,
                IntentFilter(BaseNavigationService.BROADCAST_NAVIGATION_STATE),
                RECEIVER_NOT_EXPORTED
            )
            registerReceiver(
                broadcastReceiver,
                IntentFilter(BaseLocationService.BROADCAST_LOCATING_STATUS),
                RECEIVER_NOT_EXPORTED
            )
            registerReceiver(
                broadcastReceiver,
                IntentFilter(LocationService.BROADCAST_TRACKING_STATUS),
                RECEIVER_NOT_EXPORTED
            )
            registerReceiver(
                broadcastReceiver,
                IntentFilter(Intent.ACTION_SCREEN_OFF),
                RECEIVER_NOT_EXPORTED
            )
            registerReceiver(
                broadcastReceiver,
                IntentFilter(Intent.ACTION_SCREEN_ON),
                RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(
                broadcastReceiver,
                IntentFilter(BaseNavigationService.BROADCAST_NAVIGATION_STATUS)
            )
            registerReceiver(
                broadcastReceiver,
                IntentFilter(BaseNavigationService.BROADCAST_NAVIGATION_STATE)
            )
            registerReceiver(
                broadcastReceiver,
                IntentFilter(BaseLocationService.BROADCAST_LOCATING_STATUS)
            )
            registerReceiver(
                broadcastReceiver,
                IntentFilter(LocationService.BROADCAST_TRACKING_STATUS)
            )
            registerReceiver(broadcastReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
            registerReceiver(broadcastReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
        }
        if (application!!.hasEnsureVisible()) {
            setFollowing(false)
            followOnLocation = false
            val loc: DoubleArray = application!!.getEnsureVisible()
            application!!.setMapCenter(loc[0], loc[1], true, false)
            application!!.clearEnsureVisible()
        } else {
            application!!.updateLocationMaps(true, map!!.isBestMapEnabled())
        }
        updateMapViewArea()
        map!!.resume()
        map!!.updateMapInfo()
        map!!.update()
        map!!.requestFocus()
        Log.e(TAG, "After map.requestFocus()")
    }

    override fun onPause() {
        super.onPause()
        Log.e(TAG, "onPause()")

        unregisterReceiver(broadcastReceiver)
        map!!.pause()

        // save active route or Wpt
        val editor = PreferenceManager.getDefaultSharedPreferences(this)!!.edit()
        editor.putString(getString(R.string.nav_route), "")
        editor.putString(getString(R.string.nav_wpt), "")
        if (navigationService != null) {
            if (navigationService!!.isNavigatingViaRoute()) {
                val route = navigationService!!.navRoute
                if (route!!.filepath != null) {
                    editor.putString(getString(R.string.nav_route), route.filepath)
                    editor.putInt(
                        getString(R.string.nav_route_idx),
                        application!!.getRouteIndex(navigationService!!.navRoute)
                    )
                    editor.putInt(
                        getString(R.string.nav_route_dir),
                        navigationService!!.navDirection
                    )
                    editor.putInt(
                        getString(R.string.nav_route_wpt),
                        navigationService!!.navCurrentRoutePoint
                    )
                }
            } else if (navigationService!!.isNavigating()) {
                val wpt = navigationService!!.navWaypoint
                editor.putString(getString(R.string.nav_wpt), wpt!!.name)
                editor.putInt(getString(R.string.nav_wpt_prx), wpt.proximity)
                editor.putFloat(getString(R.string.nav_wpt_lat), wpt.latitude.toFloat())
                editor.putFloat(getString(R.string.nav_wpt_lon), wpt.longitude.toFloat())
            }
        }
        editor.apply()

        if (navigationService != null) {
            unbindService(navigationConnection)
            navigationService = null
        }
        if (locationService != null) {
            locationService!!.unregisterLocationCallback(locationListener)
            locationService = null
        }
        unbindService(locationConnection)
    }

    override fun onStop() {
        super.onStop()
        Log.e(TAG, "onStop()")
        (getWindow().getDecorView() as ViewGroup).removeView(dimView)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.e(TAG, "onDestroy()")
        ready = false

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.unregisterOnSharedPreferenceChangeListener(this)

        if (isFinishing() && !restarting) {
            // clear all overlays from map
            updateOverlays(null, true)
            application!!.waypointsOverlay = null
            application!!.navigationOverlay = null
            application!!.distanceOverlay = null

            application!!.clear()
        }

        restarting = false

        application = null

        map = null

        coordinates = null
        satInfo = null
        currentFile = null
        mapZoom = null
        waypointName = null
        waypointExtra = null
        routeName = null
        routeExtra = null
        speedValue = null
        speedUnit = null
        trackValue = null
        elevationValue = null
        elevationUnit = null
        elevationName = null
        //distanceValue = null;
        //distanceUnit = null;
        xtkValue = null
        xtkUnit = null
        belowaboveName = null
        belowaboveUnit = null
        belowaboveValue = null
        //bearingValue = null;
        turnValue = null
        trackBar = null
    }

    //От тук започва същинската част на кода какво да прави машинката при нормална работа
    //Прави връзка с датчиците за местоположение и обновява информацията при стартирана навигация
    private val navigationConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName?, service: IBinder) {
            navigationService = (service as NavigationService.LocalBinder).getService()
            runOnUiThread(object : Runnable {
                override fun run() {
                    if (!ready) return
                    updateNavigationStatus()
                    updateNavigationInfo()
                }
            })
            Log.d(TAG, "Navigation service connected")
        }

        override fun onServiceDisconnected(className: ComponentName?) {
            navigationService = null
            Log.d(TAG, "Navigation service disconnected")
        }
    }

    //Какво да прави при обновяване на информацията за местоположението
    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val action = intent.getAction()
            Log.e(TAG, "Broadcast: " + action)
            if (action == BaseNavigationService.BROADCAST_NAVIGATION_STATE) {
                val state = intent.getExtras()!!.getInt("state")
                runOnUiThread(object : Runnable {
                    override fun run() {
                        if (!ready) return
                        if (state == BaseNavigationService.STATE_REACHED) {
                            Toast.makeText(
                                getApplicationContext(),
                                R.string.arrived,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        updateNavigationStatus()
                    }
                })
            } else if (action == BaseNavigationService.BROADCAST_NAVIGATION_STATUS) {
                runOnUiThread(object : Runnable {
                    override fun run() {
                        if (!ready) return
                        updateNavigationInfo()
                    }
                })
            } else if (action == LocationService.BROADCAST_TRACKING_STATUS) {
                isTrackingState = locationService != null && locationService!!.isTracking()
            } else if (action == BaseLocationService.BROADCAST_LOCATING_STATUS) {
                isLocatingState = locationService != null && locationService!!.isLocating()
                if (locationService != null && !locationService!!.isLocating()) map!!.clearLocation()
            } else if (action == Intent.ACTION_SCREEN_OFF) {
                map!!.pause()
            } else if (action == Intent.ACTION_SCREEN_ON) {
                map!!.resume()
            }
        }
    }

    private val locationConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName?, binder: IBinder?) {
            locationService = binder as ILocationService?
            locationService!!.registerLocationCallback(locationListener)
            Log.d(TAG, "Location service connected")
        }

        override fun onServiceDisconnected(className: ComponentName?) {
            locationService = null
            Log.d(TAG, "Location service disconnected")
        }
    }

    private val locationListener: ILocationListener = object : ILocationListener {
        override fun onGpsStatusChanged(provider: String, status: Int, fsats: Int, tsats: Int) {
            if (LocationManager.GPS_PROVIDER == provider)  //при обновяване на информация получена от GPS
            {
                runOnUiThread(object : Runnable {
                    override fun run() {
                        if (!ready) return
                        when (status) {
                            BaseLocationService.GPS_OK -> {
                                if (!map!!.isFixed) {
                                    satInfo!!.setTextColor(
                                        ContextCompat.getColor(
                                            getApplicationContext(),
                                            R.color.gpsworking
                                        )
                                    )
                                    map!!.setMoving(true)
                                    map!!.isFixed = true
                                    updateGPSStatus()
                                }
                                satInfo!!.setText(fsats.toString() + "/" + tsats.toString())
                            }

                            BaseLocationService.GPS_OFF -> {
                                satInfo!!.setText(R.string.sat_stop)
                                satInfo!!.setTextColor(
                                    ContextCompat.getColor(
                                        getApplicationContext(),
                                        R.color.gpsdisabled
                                    )
                                )
                                map!!.setMoving(false)
                                map!!.isFixed = false
                                updateGPSStatus()
                            }

                            BaseLocationService.GPS_SEARCHING -> {
                                if (map!!.isFixed) {
                                    satInfo!!.setTextColor(
                                        ContextCompat.getColor(
                                            getApplicationContext(),
                                            R.color.gpsenabled
                                        )
                                    )
                                    map!!.isFixed = false
                                }
                                satInfo!!.setText(fsats.toString() + "/" + tsats.toString())
                            }
                        }
                    }
                })
            }
        }

        override fun onLocationChanged(
            location: Location,
            continous: Boolean,
            geoid: Boolean,
            smoothspeed: Float,
            avgspeed: Float
        ) {
            if (!ready) return

            //Log.d(TAG, "Location arrived");
            val lastLocationMillis = location.getTime()

            var magnetic = false
            if (application!!.angleType == 1 && lastLocationMillis - lastMagnetic >= magInterval) {
                magnetic = true
                lastMagnetic = lastLocationMillis
            }

            // update map
            if (lastLocationMillis - lastRenderTime >= renderInterval) {
                lastRenderTime = lastLocationMillis

                application!!.setLocation(location, magnetic)
                map!!.setLocation(location)
                val enableFollowing = followOnLocation && lastKnownLocation == null

                lastKnownLocation = location

                if (application!!.accuracyOverlay != null && location.hasAccuracy()) {
                    application!!.accuracyOverlay!!.setAccuracy(location.getAccuracy())
                }

                runOnUiThread(object : Runnable {
                    override fun run() {
                        if (LocationManager.GPS_PROVIDER != location.getProvider() && map!!.isMoving()) {
                            map!!.setMoving(false)
                            updateGPSStatus()
                        }
                        // Mock provider hack
                        if (!map!!.isFixed && continous && LocationManager.GPS_PROVIDER == location.getProvider()) {
                            satInfo!!.setText(R.string.sat_start)
                            satInfo!!.setTextColor(
                                ContextCompat.getColor(
                                    getApplicationContext(),
                                    R.color.gpsworking
                                )
                            ) //gpsworking
                            map!!.setMoving(true)
                            map!!.isFixed = true
                            updateGPSStatus()
                        }
                        accuracy!!.setText(
                            if (location.hasAccuracy()) ("Accuracy: " + distanceH(
                                location.getAccuracy().toDouble(),
                                "%.1f",
                                1000
                            )) else "N/A"
                        )
                        updateMovingInfo(
                            location,
                            geoid
                        ) //обновява информацията за скоростта, височината и курса

                        if (enableFollowing) setFollowing(true)
                        else map!!.update()

                        // auto dim
                        if (autoDim && dimInterval > 0 && lastLocationMillis - lastDim >= dimInterval) {
                            dimScreen(location)
                            lastDim = lastLocationMillis
                        }
                    }
                })
            }
        }

        override fun onProviderChanged(provider: String) {
        }

        override fun onProviderDisabled(provider: String) {
            if (LocationManager.GPS_PROVIDER == provider) {
                runOnUiThread(object : Runnable {
                    override fun run() {
                        if (!ready) return
                        satInfo!!.setText(R.string.sat_stop)
                        satInfo!!.setTextColor(
                            ContextCompat.getColor(
                                getApplicationContext(),
                                R.color.gpsdisabled
                            )
                        ) //gpsdisabled
                        map!!.setMoving(false)
                        map!!.isFixed = false
                        updateGPSStatus()
                    }
                })
            }
        }

        override fun onProviderEnabled(provider: String) {
            if (LocationManager.GPS_PROVIDER == provider) {
                runOnUiThread(object : Runnable {
                    override fun run() {
                        if (!ready) return
                        if (!map!!.isFixed) {
                            satInfo!!.setText(R.string.sat_start)
                            //colorGlidePath = ContextCompat.getColor(getApplicationContext(), R.color.aboveGlidePath)
                            //getResources().getColor(R.color.gpsenabled)
                            satInfo!!.setTextColor(
                                ContextCompat.getColor(
                                    getApplicationContext(),
                                    R.color.gpsenabled
                                )
                            )
                        }
                    }
                })
            }
        }
    }

    private fun updateMapViewArea() {
        val vto = map!!.getViewTreeObserver()
        vto.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val area = Rect()
                map!!.getLocalVisibleRect(area)
                var v = findViewById<View?>(R.id.topbar)
                if (v != null) area.top = v.getBottom()
                v = findViewById<View?>(R.id.bottombar)
                if (v != null) area.bottom = v.getTop()
                v = findViewById<View?>(R.id.rightbar)
                if (v != null) area.right = v.getLeft()
                if (!area.isEmpty()) map!!.updateViewArea(area)
                if (vto.isAlive()) if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    vto.removeOnGlobalLayoutListener(this)
                } else {
                    vto.removeGlobalOnLayoutListener(this)
                }
                //listener.onGlobalLayout();
                /*{
                    vto.removeGlobalOnLayoutListener(this);
                }
                else
                {
                    final ViewTreeObserver vto1 = map.getViewTreeObserver();
                    vto1.removeGlobalOnLayoutListener(this);
                }*/
            }
        })
    }

    fun updateMap() {
        if (map != null) map!!.postInvalidate()
    }

    /**
     * Handles all side panel button actions (обработва всички действия от страничния панел).
     */
    private fun onSidePanelAction(action: SidePanelAction) {
        when (action) {
            SidePanelAction.EP -> {
                val IntentBtnEmer = Intent(this, BtnsProceduresSet::class.java)
                IntentBtnEmer.putExtra(BTN_TITLE, getString(R.string.buttonEP))
                startActivity(IntentBtnEmer)
            }
            SidePanelAction.NP -> {
                val IntentBtnNorm = Intent(this, BtnsProceduresSet::class.java)
                IntentBtnNorm.putExtra(BTN_TITLE, getString(R.string.buttonNP))
                startActivity(IntentBtnNorm)
            }
            SidePanelAction.ZOOM_IN -> {
                if (application!!.getNextZoom() != 0.0) {
                    waitBar!!.visibility = View.VISIBLE
                    waitBar!!.setText(R.string.msg_wait)
                    executorThread.execute {
                        synchronized(map!!) {
                            if (application!!.zoomIn()) {
                                map!!.updateMapInfo()
                                map!!.update()
                            }
                        }
                        finishHandler!!.sendEmptyMessage(0)
                    }
                }
            }
            SidePanelAction.ZOOM_OUT -> {
                if (application!!.getPrevZoom() != 0.0) {
                    waitBar!!.visibility = View.VISIBLE
                    waitBar!!.setText(R.string.msg_wait)
                    executorThread.execute {
                        synchronized(map!!) {
                            if (application!!.zoomOut()) {
                                map!!.updateMapInfo()
                                map!!.update()
                            }
                        }
                        finishHandler!!.sendEmptyMessage(0)
                    }
                }
            }
            SidePanelAction.NEXT_MAP -> {
                waitBar!!.visibility = View.VISIBLE
                waitBar!!.setText(R.string.msg_wait)
                executorThread.execute {
                    synchronized(map!!) {
                        if (application!!.prevMap()) {
                            map!!.suspendBestMap()
                            map!!.updateMapInfo()
                            map!!.update()
                        }
                    }
                    finishHandler!!.sendEmptyMessage(0)
                }
            }
            SidePanelAction.PREV_MAP -> {
                waitBar!!.visibility = View.VISIBLE
                waitBar!!.setText(R.string.msg_wait)
                executorThread.execute {
                    synchronized(map!!) {
                        if (application!!.nextMap()) {
                            map!!.suspendBestMap()
                            map!!.updateMapInfo()
                            map!!.update()
                        }
                    }
                    finishHandler!!.sendEmptyMessage(0)
                }
            }
            SidePanelAction.MAPS_AT_CURSOR -> startActivityForResult(
                Intent(this, MapList::class.java).putExtra("pos", true),
                RESULT_LOAD_MAP_ATPOSITION
            )
            SidePanelAction.WAYPOINTS -> startActivityForResult(
                Intent(this, WaypointListActivity::class.java),
                RESULT_MANAGE_WAYPOINTS
            )
            SidePanelAction.INFO -> startActivity(Intent(this, Information::class.java))
            SidePanelAction.FOLLOW -> {
                setFollowing(!map!!.isFollowing())
                isFollowingState = map!!.isFollowing()
            }
            SidePanelAction.LOCATE -> {
                val isLocating = locationService != null && locationService!!.isLocating()
                application!!.enableLocating(!isLocating)
                val editor = PreferenceManager.getDefaultSharedPreferences(this).edit()
                editor.putBoolean(getString(R.string.lc_locate), !isLocating)
                editor.apply()
                isLocatingState = !isLocating
            }
            SidePanelAction.TRACKING -> {
                val isTracking = locationService != null && locationService!!.isTracking()
                application!!.enableTracking(!isTracking)
                val editor = PreferenceManager.getDefaultSharedPreferences(this).edit()
                editor.putBoolean(getString(R.string.lc_track), !isTracking)
                editor.apply()
                isTrackingState = !isTracking
            }
            SidePanelAction.EXPAND -> {
                if (isFullscreen) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                } else {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_FULLSCREEN,
                        WindowManager.LayoutParams.FLAG_FULLSCREEN
                    )
                }
                isFullscreen = !isFullscreen
            }
            SidePanelAction.ZERO_LEVEL -> {
                zeroElevation = lastElevation
                application!!.setZeroLevelDouble(lastElevation)
            }
            SidePanelAction.CLEAR -> {
                zeroElevation = 0.0
                application!!.setZeroLevelDouble(0.0)
            }
        }
    }

    internal fun updateCoordinates(latlon: DoubleArray?) {
        // TODO strange situation, needs investigation
        if (application != null) {
            val pos = coordinates(application!!.coordinateFormat, " ", latlon!![0], latlon[1])
            this.runOnUiThread(object : Runnable {
                override fun run() {
                    coordinates!!.setText(pos)
                }
            })
        }
    }

    internal fun updateFileInfo() {
        val title: String? = application!!.getMapTitle()
        this.runOnUiThread(object : Runnable {
            override fun run() {
                if (title != null) {
                    currentFile!!.setText(title)
                } else {
                    currentFile!!.setText("-no map-")
                }

                updateZoomInfo()
            }
        })
    }

    protected fun updateZoomInfo() {
        val zoom: Double = application!!.getZoom() * 100

        if (zoom == 0.0) {
            mapZoom!!.setText("---%")
        } else {
            val rz = floor(zoom).toInt()
            val zoomStr = if (zoom - rz != 0.0) String.format("%.1f", zoom) else rz.toString()
            mapZoom!!.setText(zoomStr + "%")
        }

        // Zoom enable state now handled by Compose SidePanel
        // (zoomin/zoomout buttons no longer exist in XML layout)
    }

    protected fun updateGPSStatus() {
        val v =
            if (map!!.isMoving() && application!!.editingRoute == null && application!!.editingTrack == null) View.VISIBLE else View.GONE
        val view = findViewById<View>(R.id.movinginfo)
        if (view.getVisibility() != v) {
            view.setVisibility(v)
            updateMapViewArea()
        }
    }

    protected fun updateNavigationStatus() //Changed  for Flaying usage-3D
    {
        val isNavigating = navigationService != null && navigationService!!.isNavigating()
        val isNavigatingViaRoute = isNavigating && navigationService!!.isNavigatingViaRoute()

        // waypoint panel
        findViewById<View?>(R.id.waypointinfo).setVisibility(if (isNavigating) View.VISIBLE else View.GONE)
        // route panel
        findViewById<View?>(R.id.routeinfo).setVisibility(if (isNavigatingViaRoute) View.VISIBLE else View.GONE)

        // distance
        //distanceValue.setVisibility(isNavigating ? View.VISIBLE : View.GONE);
        //findViewById(R.id.distancelt).setVisibility(isNavigating ? View.VISIBLE : View.GONE);

        // bearing
        //bearingValue.setVisibility(isNavigating ? View.VISIBLE : View.GONE);
        //findViewById(R.id.bearinglt).setVisibility(isNavigating ? View.VISIBLE : View.GONE);
        // turn
        turnValue!!.setVisibility(if (isNavigating) View.VISIBLE else View.GONE)
        findViewById<View?>(R.id.turnlt).setVisibility(if (isNavigating) View.VISIBLE else View.GONE)
        // xtk
        xtkValue!!.setVisibility(if (isNavigatingViaRoute) View.VISIBLE else View.GONE)
        findViewById<View?>(R.id.xtklt).setVisibility(if (isNavigatingViaRoute) View.VISIBLE else View.GONE)
        // abovebelowGS
        try {
            belowaboveValue!!.setVisibility(if (isNavigatingViaRoute) View.VISIBLE else View.GONE)
            findViewById<View?>(R.id.abovebelowGSlt).setVisibility(if (isNavigatingViaRoute) View.VISIBLE else View.GONE)
        } catch (e: Exception) {
        }


        // Force movinginfo visible during navigation (regardless of movement)
        val movingInfo = findViewById<View>(R.id.movinginfo)
        if (isNavigating) {
            if (movingInfo.getVisibility() != View.VISIBLE) {
                movingInfo.setVisibility(View.VISIBLE)
            }
        }

        // we hide elevation in Navigating mode and show Above/Below glide path
        if (isNavigatingViaRoute) {
            routeName!!.setText("› " + navigationService!!.navRoute!!.name)
        }
        if (isNavigating) {
            waypointName!!.setText("» " + navigationService!!.navWaypoint!!.name)
            if (application!!.navigationOverlay == null) {
                application!!.navigationOverlay = NavigationOverlay(this)
            }
            // Always refresh overlay on navigation state change (target may have changed)
            application!!.navigationOverlay!!.onMapChanged()
        } else if (application!!.navigationOverlay != null) {
            application!!.navigationOverlay!!.onBeforeDestroy()
            application!!.navigationOverlay = null
        }

        updateMapViewArea()
        map!!.update()

        // Restore normal movinginfo visibility when navigation stops
        if (!isNavigating) {
            updateGPSStatus()
        }
    }

    protected fun updateNavigationInfo() {
        if (navigationService == null || !navigationService!!.isNavigating()) return

        val distance = navigationService!!.navDistance
        val bearing = navigationService!!.navBearing
        var turn = navigationService!!.navTurn
        val vmg = navigationService!!.avvmg * speedFactor
        val ete = navigationService!!.navETE

        val dist: Array<String> = distanceC(distance, precisionFormat)
        var extra = dist[0] + " " + dist[1] + " | " + String.format(
            precisionFormat,
            vmg
        ) + " " + speedAbbr + " | " + timeHSec(ete)

        var trnsym = ""
        if (turn > 0) {
            trnsym = "R"
        } else if (turn < 0) {
            trnsym = "L"
            turn = -turn
        }

        //	bearing = application.fixDeclination(bearing);	//distanceValue.setText(dist[0]);	//distanceUnit.setText(dist[1]);	//bearingValue.setText(String.valueOf(Math.round(bearing)));
        turnValue!!.setText(Math.round(turn.toFloat()).toString() + trnsym)
        waypointExtra!!.setText(extra)

        if (navigationService!!.isNavigatingViaRoute()) {
            val hasNext = navigationService!!.hasNextRouteWaypoint()
            if (distance < navigationService!!.navProximity * 3 && !animationSet) {
                val animation = AnimationSet(true)
                animation.addAnimation(AlphaAnimation(1.0f, 0.3f))
                animation.addAnimation(AlphaAnimation(0.3f, 1.0f))
                animation.setDuration(500)
                animation.setRepeatCount(10)
                findViewById<View?>(R.id.waypointinfo).startAnimation(animation)
                if (!hasNext) {
                    findViewById<View?>(R.id.routeinfo).startAnimation(animation)
                }
                animationSet = true
            } else if (animationSet) {
                findViewById<View?>(R.id.waypointinfo).setAnimation(null)
                if (!hasNext) {
                    findViewById<View?>(R.id.routeinfo).setAnimation(null)
                }
                animationSet = false
            }

            if (navigationService!!.navXTK == Double.NEGATIVE_INFINITY) {
                xtkValue!!.setText("--")
                xtkUnit!!.setText("--")
            } else {
                val xtksym =
                    if (navigationService!!.navXTK == 0.0) "" else if (navigationService!!.navXTK > 0) "R" else "L"
                val xtks: Array<String> = distanceC(abs(navigationService!!.navXTK))
                xtkValue!!.setText(xtks[0] + xtksym)
                xtkUnit!!.setText(xtks[1])
            }

            val navDistance = navigationService!!.navRouteDistanceLeft()
            var eta = navigationService!!.navRouteETE(navDistance)
            if (eta < Int.Companion.MAX_VALUE) eta += navigationService!!.navETE
            extra = distanceH(navDistance + distance, 1000) + " | " + timeHSec(eta)
            routeExtra!!.setText(extra)
        }
    }

    //
    protected fun updateMovingInfo(location: Location, geoid: Boolean) {
        var eOrb = 0.0 //eOrb - elevationOrGladePath
        val needtobe = 0.0 //eOrb - elevationOrGladePath
        val s = location.getSpeed() * speedFactor
        val track: Double = application!!.fixDeclination(location.getBearing().toDouble())
        speedValue!!.setText(String.format(precisionFormat, s))
        trackValue!!.setText(Math.round(track).toString())
        val isNavigating = navigationService != null && navigationService!!.isNavigating()
        val isNavigatingViaRoute = isNavigating && navigationService!!.isNavigatingViaRoute()
        val colorGlidePath: Int
        if (isNavigatingViaRoute) { // Calculate AboveBelow Glide Path

            eOrb = belowAboveGlidePath(
                location.getLatitude(),
                location.getLongitude(),
                navigationService!!.navWaypoint!!.latitude,
                navigationService!!.navWaypoint!!.longitude,
                location.getAltitude(),
                navigationService!!.navWaypoint!!.altitude,
                navigationService!!.SlopeAngle
            )
            eOrb =
                (eOrb - zeroElevation) * elevationFactor //понеже работим със относителна височина - трябва превишението по схемата да е спрямо летището
            belowaboveValue!!.setText(Math.round(eOrb).toString())

            if (eOrb < -5)  // Below Glide Path
            {
                colorGlidePath = ContextCompat.getColor(
                    getApplicationContext(),
                    R.color.belowGlidePath
                ) //5m belowGlidePath - colored in red
                belowaboveName!!.setText("BELOW")
                belowaboveName!!.setTextColor(colorGlidePath)
                belowaboveValue!!.setTextColor(colorGlidePath)
                belowaboveUnit!!.setTextColor(colorGlidePath)
            } else if (eOrb > 20) { // Above Glide Path
                colorGlidePath = ContextCompat.getColor(
                    getApplicationContext(),
                    R.color.aboveGlidePath
                ) //5m belowGlidePath - colored in blue
                belowaboveName!!.setText("above")
                belowaboveName!!.setTextColor(colorGlidePath)
                belowaboveValue!!.setTextColor(colorGlidePath)
                belowaboveUnit!!.setTextColor(colorGlidePath)
            } else { // On Glide Path
                colorGlidePath = ContextCompat.getColor(
                    getApplicationContext(),
                    R.color.onGlidePath
                ) //5m belowGlidePath - colored in white
                belowaboveName!!.setText("OnGlidePath")
                belowaboveName!!.setTextColor(colorGlidePath)
                belowaboveValue!!.setTextColor(colorGlidePath)
                belowaboveUnit!!.setTextColor(colorGlidePath)
            }

            //Log.i(TAG, "UpdateMouvingInfo: AboveBelowGlidePath= " + eOrb);
        } else {
            //colorGlidePath = ContextCompat.getColor(getApplicationContext(), R.color.aboveGlidePath);//5m belowGlidePath - colored in white
        }
        lastElevation = location.getAltitude()
        val elev = (lastElevation - zeroElevation) * elevationFactor
        elevationValue!!.setText(Math.round(elev).toString())
        // TODO set separate color
        if (geoid != lastGeoid) {
            val color = if (geoid) -0x1 else ContextCompat.getColor(
                getApplicationContext(),
                R.color.gpsenabled
            )
            elevationValue!!.setTextColor(color)
            elevationUnit!!.setTextColor(color)
            (findViewById<View?>(R.id.elevationname) as TextView).setTextColor(color)
            lastGeoid = geoid
        }
    }

    private fun customizeLayout(settings: SharedPreferences) {
        val slVisible = settings.getBoolean(getString(R.string.pref_showsatinfo), true)
        val mlVisible = settings.getBoolean(getString(R.string.pref_showmapinfo), true)

        findViewById<View?>(R.id.satinfo).setVisibility(if (slVisible) View.VISIBLE else View.GONE)
        findViewById<View?>(R.id.mapinfo).setVisibility(if (mlVisible) View.VISIBLE else View.GONE)

        updateMapViewArea()
    }

    private fun updateOverlays(settings: SharedPreferences?, justRemove: Boolean) {
        var ctEnabled = false
        var wptEnabled = false
        var navEnabled = false
        var distEnabled = false
        var accEnabled = false
        var moEnabled = false
        var scaleEnabled = false

        if (!justRemove) {
            if (settings == null) return
            ctEnabled = settings.getBoolean(getString(R.string.pref_showcurrenttrack), true)
            wptEnabled = settings.getBoolean(getString(R.string.pref_showwaypoints), true)
            distEnabled = showDistance > 0
            accEnabled = showAccuracy
            navEnabled = navigationService != null && navigationService!!.isNavigating()
            moEnabled = true
            scaleEnabled = true
        }
        if (ctEnabled && application!!.currentTrackOverlay == null) {
            application!!.currentTrackOverlay = CurrentTrackOverlay(this)
        } else if (!ctEnabled && application!!.currentTrackOverlay != null) {
            application!!.currentTrackOverlay!!.onBeforeDestroy()
            application!!.currentTrackOverlay = null
        }
        if (application!!.waypointsOverlay == null) {
            application!!.waypointsOverlay = WaypointsOverlay(this)
            application!!.waypointsOverlay!!.setWaypoints(application!!.waypoints)
        }
        application!!.waypointsOverlay!!.setEnabled(wptEnabled)
        if (navEnabled && application!!.navigationOverlay == null) {
            application!!.navigationOverlay = NavigationOverlay(this)
        } else if (!navEnabled && application!!.navigationOverlay != null) {
            application!!.navigationOverlay!!.onBeforeDestroy()
            application!!.navigationOverlay = null
        }
        if (distEnabled && application!!.distanceOverlay == null) {
            application!!.distanceOverlay = DistanceOverlay(this)
        } else if (!distEnabled && application!!.distanceOverlay != null) {
            application!!.distanceOverlay!!.onBeforeDestroy()
            application!!.distanceOverlay = null
        }
        if (!moEnabled && application!!.mapObjectsOverlay != null) {
            application!!.mapObjectsOverlay!!.onBeforeDestroy()
            application!!.mapObjectsOverlay = null
        }
        if (scaleEnabled && application!!.scaleOverlay == null) {
            application!!.scaleOverlay = ScaleOverlay(this)
        } else if (!scaleEnabled && application!!.scaleOverlay != null) {
            application!!.scaleOverlay!!.onBeforeDestroy()
            application!!.scaleOverlay = null
        }
        if (accEnabled && application!!.accuracyOverlay == null) {
            application!!.accuracyOverlay = AccuracyOverlay(this)
            application!!.accuracyOverlay!!.setAccuracy(
                application!!.getLocationAsLocation().getAccuracy()
            )
        } else if (!accEnabled && application!!.accuracyOverlay != null) {
            application!!.accuracyOverlay!!.onBeforeDestroy()
            application!!.accuracyOverlay = null
        }

        if (justRemove) {
            for (to in application!!.fileTrackOverlays) {
                to.onBeforeDestroy()
            }
            application!!.fileTrackOverlays.clear()
            for (ro in application!!.routeOverlays) {
                ro.onBeforeDestroy()
            }
            application!!.routeOverlays.clear()
            for (ao in application!!.areaOverlays) {
                ao.onBeforeDestroy()
            }
            application!!.areaOverlays.clear()
            if (application!!.waypointsOverlay != null) {
                application!!.waypointsOverlay!!.onBeforeDestroy()
            }
        } else {
            for (to in application!!.fileTrackOverlays) {
                to.onPreferencesChanged(settings!!)
            }
            for (ro in application!!.routeOverlays) {
                ro.onPreferencesChanged(settings!!)
            }
            for (ao in application!!.areaOverlays) {
                ao.onPreferencesChanged(settings!!)
            }
            if (application!!.waypointsOverlay != null) {
                application!!.waypointsOverlay!!.onPreferencesChanged(settings!!)
            }
            if (application!!.navigationOverlay != null) {
                application!!.navigationOverlay!!.onPreferencesChanged(settings!!)
            }
            if (application!!.mapObjectsOverlay != null) {
                application!!.mapObjectsOverlay!!.onPreferencesChanged(settings!!)
            }
            if (application!!.distanceOverlay != null) {
                application!!.distanceOverlay!!.onPreferencesChanged(settings!!)
            }
            if (application!!.accuracyOverlay != null) {
                application!!.accuracyOverlay!!.onPreferencesChanged(settings!!)
            }
            if (application!!.scaleOverlay != null) {
                application!!.scaleOverlay!!.onPreferencesChanged(settings!!)
            }
            if (application!!.currentTrackOverlay != null) {
                application!!.currentTrackOverlay!!.onPreferencesChanged(settings!!)
            }
        }
    }

    private fun startEditTrack(track: Track?) {
        setFollowing(false)
        application!!.editingTrack = track
        application!!.editingTrack!!.editing = true
        val n: Int = application!!.editingTrack!!.points.size - 1
        val p =
            if (application!!.editingTrack!!.editingPos >= 0) application!!.editingTrack!!.editingPos else n
        application!!.editingTrack!!.editingPos = p
        trackBar!!.setMax(n)
        trackBar!!.setProgress(0)
        trackBar!!.setProgress(p)
        trackBar!!.setKeyProgressIncrement(1)
        onProgressChanged(trackBar!!, p, false)
        findViewById<View?>(R.id.edittrack).setVisibility(View.VISIBLE)
        findViewById<View?>(R.id.trackdetails).setVisibility(View.VISIBLE)
        updateGPSStatus()
        if (showDistance > 0) application!!.distanceOverlay!!.setEnabled(false)
        map!!.setFocusable(false)
        map!!.setFocusableInTouchMode(false)
        trackBar!!.requestFocus()
        updateMapViewArea()
    }

    private fun startEditRoute(route: Route?) {
        setFollowing(false)
        application!!.editingRoute = route
        application!!.editingRoute!!.editing = true

        // ── Ensure RouteWaypoints waypoint set exists for sharing points across routes ──
        ensureRouteWaypointSet()

        // Center map on first waypoint if route has points
        val firstWp = application!!.editingRoute!!.waypoints.firstOrNull()
        if (firstWp != null) {
            application!!.setMapCenter(firstWp.latitude, firstWp.longitude, true, false)
        }

        var newroute = true
        val iter: MutableIterator<RouteOverlay> = application!!.routeOverlays.iterator()
        while (iter.hasNext()) {
            val ro = iter.next()
            if (ro.getRoute().editing) {
                ro.onRoutePropertiesChanged()
                newroute = false
            }
        }
        if (newroute) {
            val newRoute = RouteOverlay(this, application!!.editingRoute!!)
            application!!.routeOverlays.add(newRoute)
        }
        findViewById<View?>(R.id.editroute).setVisibility(View.VISIBLE) //използвам същият панел с бутони за едитване на маршрут
        //Log.d(TAG, "startEditRoute");
        updateGPSStatus()
        application!!.routeEditingWaypoints = Stack<Waypoint?>()
        application!!.routeEditingCursor = null
        if (showDistance > 0) application!!.distanceOverlay!!.setEnabled(false)
        updateMapViewArea()
    }

    private fun startEditArea(area: Area?) {
        setFollowing(false)
        application!!.editingArea = area
        application!!.editingArea!!.editing = true

        // Center map on first waypoint or center point
        if (area!!.isCircleArea() && area.AreaCenter != null) {
            application!!.setMapCenter(area.AreaCenter!!.latitude, area.AreaCenter!!.longitude, true, false)
        } else if (area!!.isCircleArea() && area.AreaCenter == null) {
            // Circle with no center: use current GPS location
            val loc = application!!.getLocation()
            application!!.setMapCenter(loc[0], loc[1], true, false)
        } else {
            val firstWp = application!!.editingArea!!.waypoints.firstOrNull()
            if (firstWp != null) {
                application!!.setMapCenter(firstWp.latitude, firstWp.longitude, true, false)
            }
        }

        var newarea = true
        val iter: MutableIterator<AreaOverlay> = application!!.areaOverlays.iterator()
        while (iter.hasNext()) {
            val аo = iter.next()
            if (аo.area.editing) {
                аo.onAreaPropertiesChanged()
                newarea = false
            }
        }
        if (newarea)  //не са въвеждани точки в зоната
        {
            val newArea = AreaOverlay(this, application!!.editingArea!!)
            application!!.areaOverlays.add(newArea)
        }
        findViewById<View?>(R.id.editroute).setVisibility(View.VISIBLE) //използвам същият панел с бутони за едитване на маршрут

        // For circle area edit: hide insert/remove/order buttons (only Add + Done)
        if (area.isCircleArea()) {
            findViewById<View?>(R.id.insertpoint).setVisibility(View.GONE)
            findViewById<View?>(R.id.removepoint).setVisibility(View.GONE)
            findViewById<View?>(R.id.orderpoints).setVisibility(View.GONE)
        } else {
            findViewById<View?>(R.id.insertpoint).setVisibility(View.VISIBLE)
            findViewById<View?>(R.id.removepoint).setVisibility(View.VISIBLE)
            findViewById<View?>(R.id.orderpoints).setVisibility(View.VISIBLE)
        }

        //Log.d(TAG, "startEditArea");
        updateGPSStatus()
        application!!.areaEditingWaypoints = Stack<Waypoint?>()
        if (showDistance > 0) application!!.distanceOverlay!!.setEnabled(false)
        updateMapViewArea()
    }


    fun setFollowing(follow: Boolean) {
        if (application!!.editingRoute == null && application!!.editingTrack == null) {
            if (showDistance > 0 && application!!.distanceOverlay != null) {
                if (showDistance == 2 && !follow) {
                    application!!.distanceOverlay!!.setAncor(application!!.getLocation())
                    application!!.distanceOverlay!!.setEnabled(true)
                } else {
                    application!!.distanceOverlay!!.setEnabled(false)
                }
            }
            map!!.setFollowing(follow)
        }
    }

    fun zoomMap(factor: Float) {
        waitBar!!.setVisibility(View.VISIBLE)
        waitBar!!.setText(R.string.msg_wait)
        executorThread.execute(object : Runnable {
            override fun run() {
                synchronized(map!!) {
                    if (application!!.zoomBy(factor)) {
                        map!!.updateMapInfo()
                        map!!.update()
                    }
                }
                finishHandler!!.sendEmptyMessage(0)
            }
        })
    }

    protected fun dimScreen(location: Location) {
        var color = Color.TRANSPARENT
        val now = GregorianCalendar.getInstance(TimeZone.getDefault())
        if (autoDim && !isDaytime(application!!.getZenith(), location, now)) color =
            dimValue shl 57 // value * 2 and shifted to transparency octet

        dimView!!.setBackgroundColor(color)
    }

    fun waypointTapped(waypoint: Waypoint, x: Int, y: Int): Boolean {
        try {
            // Ensure waypoint exists in global list (old routes may have
            // waypoints that only exist inside the route, not in global list).
            var idx = application!!.getWaypointIndex(waypoint)
            if (idx < 0) {
                application!!.addWaypoint(waypoint)
                idx = application!!.getWaypointIndex(waypoint)
                if (idx < 0) return false
            }
            if (application!!.editingRoute != null) {
                routeSelected = -1
                waypointSelected = idx
                // Show Add to Route quick action (with Confirm/Cancel buttons)
                wptQuickActionAddToRoute!!.show(map, x, y)
                return true
            } else if (application!!.editingArea != null) {
                if (application!!.editingArea!!.isCircleArea()) {
                    // Circle area edit: tapping a waypoint sets it as circle center
                    application!!.editingArea!!.AreaCenter = Waypoint(application!!.editingArea!!.name, "", waypoint.latitude, waypoint.longitude, 0.0)
                    // Recalculate area size
                    application!!.editingArea!!.areaSize = application!!.editingArea!!.calculateArea()
                    // Refresh overlay
                    val iter = application!!.areaOverlays.iterator()
                    while (iter.hasNext()) {
                        val ao = iter.next()
                        if (ao.area.editing) ao.onAreaPropertiesChanged()
                    }
                    map!!.postInvalidate()
                    return true
                }
                areaSelected = -1
                waypointSelected = idx
                wptQuickActionAddToArea!!.show(map, x, y)
                return true
            } else {
                // Outside editing mode — show Edit/Navigate quick action
                waypointSelected = idx
                wptQuickAction!!.show(map, x, y)
                Log.d(TAG, "waypointTapped: show wptQuickAction for waypoint=${waypoint.name}")
                return true
            }
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Performs action on a tapped route waypoint.
     * 
     * @param route
     * route index
     * @param index
     * waypoint index inside route
     * @param x
     * view X coordinate
     * @param y
     * view Y coordinate
     * @return true if any action was performed
     */
    fun routeWaypointTapped(route: Int, index: Int, x: Int, y: Int): Boolean {
        if (application!!.editingRoute != null && application!!.editingRoute == application!!.getRoute(
                route
            )
        ) {
            // Tapped a waypoint inside the route being edited → set as cursor
            application!!.routeEditingCursor = index
            Log.d(TAG, "routeWaypointTapped: set editing cursor to index=$index")
            return true
        } else if (application!!.editingRoute != null || application!!.editingArea != null) {
            // Route or Area is being edited — offer to add this waypoint to it
            val rte = application!!.getRoute(route) ?: return false
            val wpt = rte.waypoints[index]
            Log.d(TAG, "routeWaypointTapped: redirect to waypointTapped for editingRoute=${application!!.editingRoute?.name} editingArea=${application!!.editingArea?.name} wpt=${wpt.name}")
            return waypointTapped(wpt, x, y)
        } else if (navigationService != null && navigationService!!.navRoute == application!!.getRoute(
                route
            )
        ) {
            routeSelected = route
            waypointSelected = index
            rteQuickAction!!.show(map, x, y)
            Log.e(TAG, "rteQuickAction")
            return true
        } else {
            startActivityForResult(Intent(this, RouteDetails::class.java).putExtra("index", route), RESULT_ROUTE_DETAILS)
            return true
        }
    }

    /*
     * Performs action on a tapped area waypoint.
     *
     * @param area
     *            area index
     * @param index
     *            waypoint index inside area
     * @param x
     *            view X coordinate
     * @param y
     *            view Y coordinate
     * @return true if any action was performed
     */
    fun areaWaypointTapped(area: Int, index: Int, x: Int, y: Int): Boolean {
        if (application!!.editingArea != null && application!!.editingArea == application!!.getArea(
                area
            )
        ) {
            // Circle area in edit mode: tapping own center does nothing (or could open properties)
            if (application!!.editingArea!!.isCircleArea()) {
                return true // consume tap, no action
            }
            startActivityForResult(
                Intent(this, WaypointProperties::class.java).putExtra(
                    "INDEX",
                    index
                ).putExtra("AREA", area + 1), RESULT_EDIT_AREA
            )
            return true
        } else if (application!!.editingRoute != null || application!!.editingArea != null) {
            // Route or Area is being edited — offer to add this area waypoint to it
            val are = application!!.getArea(area) ?: return false
            val wpt = are.waypoints[index]
            Log.d(TAG, "areaWaypointTapped: redirect to waypointTapped for editingRoute=${application!!.editingRoute?.name} editingArea=${application!!.editingArea?.name} wpt=${wpt.name}")
            return waypointTapped(wpt, x, y)
        } else if (navigationService != null && navigationService!!.navArea == application!!.getArea(
                area
            )
        ) {
            areaSelected = area
            waypointSelected = index
            rteQuickAction!!.show(map, x, y)
            return true
        } else {
            startActivity(Intent(this, AreaDetails::class.java).putExtra("INDEX", area))
            return true
        }
    }

    fun mapObjectTapped(id: Long, x: Int, y: Int): Boolean {
        mapObjectSelected = id
        mobQuickAction!!.show(map, x, y)
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = getMenuInflater()
        inflater.inflate(R.menu.options_menu, menu)

        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        if (application!!.editingRoute != null || application!!.editingTrack != null) return false
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val wpt: Boolean = application!!.hasWaypoints()
        val rts: Boolean = application!!.hasRoutes()
        val ars: Boolean = application!!.hasAreas()
        val nvw = navigationService != null && navigationService!!.isNavigating()
        val nvr = navigationService != null && navigationService!!.isNavigatingViaRoute()
        val nva = navigationService != null && navigationService!!.isNavigatingViaArea()
        val cbm = ((clipboard.hasPrimaryClip()) && (clipboard.getPrimaryClipDescription()!!
            .hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)))
        //следва код който дефинира поведението на трите точки(меню)
        menu.findItem(R.id.menuManageWaypoints).setEnabled(wpt)
        menu.findItem(R.id.menuExportCurrentTrack)
            .setEnabled(application!!.currentTrackOverlay != null)
        menu.findItem(R.id.menuClearCurrentTrack)
            .setEnabled(application!!.currentTrackOverlay != null)
        // menuAreas is now a direct item — always enabled (user can always enter to load areas)
        menu.findItem(R.id.menuAreas).isEnabled = true
        menu.findItem(R.id.menuManageRoutes)
            .setVisible(!nvr)
        menu.findItem(R.id.menuStartNavigation)
            .setVisible(!nvr)
        menu.findItem(R.id.menuStartNavigation)
            .setEnabled(rts)
        menu.findItem(R.id.menuNavigationDetails)
            .setVisible(nvr)
        menu.findItem(R.id.menuNextNavPoint)
            .setVisible(nvr)
        menu.findItem(R.id.menuPrevNavPoint)
            .setVisible(nvr)
        menu.findItem(R.id.menuNextNavPoint)
            .setEnabled(navigationService != null && navigationService!!.hasNextRouteWaypoint())
        menu.findItem(R.id.menuPrevNavPoint)
            .setEnabled(navigationService != null && navigationService!!.hasPrevRouteWaypoint())
        menu.findItem(R.id.menuStopNavigation)
            .setEnabled(nvw)
        menu.findItem(R.id.menuSetAnchor).setVisible(showDistance > 0 && !map!!.isFollowing())
        menu.findItem(R.id.menuPasteLocation).setEnabled(cbm)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean { //при натискане на някой от бутоните на меню (трите точки) лентата
        when (item.getItemId()) {
            R.id.menuSearch -> {
                onSearchRequested()
                return true
            }

            R.id.menuAddWaypoint -> {
                val loc: DoubleArray = application!!.getMapCenter()
                val waypoint = Waypoint(
                    "",
                    "",
                    loc[0],
                    loc[1]
                ) //TODO ask user for wpt altitude, or find altitude on surface
                waypoint.date = Calendar.getInstance().getTime()
                val wpt: Int = application!!.addWaypoint(waypoint)
                waypoint.name = "WPT" + wpt
                application!!.saveDefaultWaypoints()
                map!!.update()
                return true
            }

            R.id.menuNewWaypoint -> {
                startActivityForResult(
                    Intent(
                        this,
                        WaypointProperties::class.java
                    ).putExtra("INDEX", -1), RESULT_SAVE_WAYPOINT
                )
                return true
            }

            R.id.menuProjectWaypoint -> {
                startActivityForResult(
                    Intent(this, WaypointProject::class.java),
                    RESULT_SAVE_WAYPOINT
                )
                return true
            }

            R.id.menuManageWaypoints -> {
                startActivityForResult(
                    Intent(this, WaypointListActivity::class.java),
                    RESULT_MANAGE_WAYPOINTS
                )
                return true
            }

            R.id.menuLoadWaypoints -> {
                startActivityForResult(
                    Intent(this, WaypointFileList::class.java),
                    RESULT_LOAD_WAYPOINTS
                )
                return true
            }

            R.id.menuManageTracks -> {
                startActivityForResult(
                    Intent(this, TrackListActivity::class.java),
                    RESULT_MANAGE_TRACKS
                )
                return true
            }

            R.id.menuExportCurrentTrack -> {
                val fm = getSupportFragmentManager()
                val trackExportDialog = TrackExportDialog.newInstance(locationService!!)
                trackExportDialog.show(fm, "track_export")
                return true
            }

            R.id.menuExpandCurrentTrack -> {
                AlertDialog.Builder(this).setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.warning).setMessage(R.string.msg_expandcurrenttrack)
                    .setPositiveButton(R.string.yes, object : DialogInterface.OnClickListener {
                        override fun onClick(dialog: DialogInterface?, which: Int) {
                            if (application!!.currentTrackOverlay != null) {
                                val track = locationService!!.getTrack()
                                track.show = true
                                application!!.currentTrackOverlay!!.setTrack(track)
                            }
                        }
                    }).setNegativeButton(R.string.no, null).show()
                return true
            }

            R.id.menuClearCurrentTrack -> {
                AlertDialog.Builder(this).setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.warning).setMessage(R.string.msg_clearcurrenttrack)
                    .setPositiveButton(R.string.yes, object : DialogInterface.OnClickListener {
                        override fun onClick(dialog: DialogInterface?, which: Int) {
                            if (application!!.currentTrackOverlay != null) application!!.currentTrackOverlay!!.clear()
                            locationService!!.clearTrack()
                        }
                    }).setNegativeButton(R.string.no, null).show()
                return true
            }

            R.id.menuManageRoutes -> {
                //Log.e(TAG, "on_menuManageRoutes");
                startActivityForResult(
                    Intent(this, RouteListActivity::class.java).putExtra(
                        "MODE",
                        RouteList.MODE_MANAGE
                    ), RESULT_MANAGE_ROUTES
                )
                return true
            }

            R.id.menuAreas -> {
                startActivityForResult(
                    Intent(this, AreaListActivity::class.java).putExtra(
                        "MODE",
                        AreaList.MODE_MANAGE
                    ), RESULT_MANAGE_AREAS
                )
                return true
            }

            R.id.menuStartNavigation -> {
                if (application!!.routes.size > 1) {
                    startActivity(
                        Intent(this, RouteListActivity::class.java).putExtra(
                            "MODE",
                            RouteList.MODE_START
                        )
                    )
                } else {
                    startActivity(Intent(this, RouteStart::class.java).putExtra("INDEX", 0))
                }
                return true
            }

            R.id.menuNavigationDetails -> {
                startActivity(
                    Intent(this, RouteDetails::class.java).putExtra(
                        "index",
                        application!!.getRouteIndex(navigationService!!.navRoute)
                    ).putExtra("nav", true)
                )
                return true
            }

            R.id.menuNextNavPoint -> {
                navigationService!!.nextRouteWaypoint()
                return true
            }

            R.id.menuPrevNavPoint -> {
                navigationService!!.prevRouteWaypoint()
                return true
            }

            R.id.menuStopNavigation -> {
                navigationService!!.stopNavigation()
                return true
            }

            R.id.menuHSI -> {
                startActivity(Intent(this, HSIActivity::class.java))
                return true
            }

            R.id.menuInformation -> {
                startActivity(Intent(this, Information::class.java))
                return true
            }

            R.id.menuMapInfo -> {
                startActivity(Intent(this, MapInformation::class.java))
                return true
            }

            R.id.menuCursorMaps -> {
                startActivityForResult(
                    Intent(this, MapList::class.java).putExtra("pos", true),
                    RESULT_LOAD_MAP_ATPOSITION
                )
                return true
            }

            R.id.menuAllMaps -> {
                startActivityForResult(Intent(this, MapList::class.java), RESULT_LOAD_MAP)
                return true
            }

            R.id.menuShare -> {
                val i = Intent(Intent.ACTION_SEND)
                i.setType("text/plain")
                i.putExtra(Intent.EXTRA_SUBJECT, R.string.currentloc)
                val sloc: DoubleArray = application!!.getMapCenter()
                val spos = coordinates(application!!.coordinateFormat, " ", sloc[0], sloc[1])
                i.putExtra(Intent.EXTRA_TEXT, spos)
                startActivity(Intent.createChooser(i, getString(R.string.menu_share)))
                return true
            }

            R.id.menuViewElsewhere -> {
                val sloc: DoubleArray = application!!.getMapCenter()
                val geoUri = "geo:" + sloc[0].toString() + "," + sloc[1].toString()
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(geoUri))
                startActivity(intent)
                return true
            }

            R.id.menuCopyLocation -> {
                // Gets a handle to the clipboard service.
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val cloc: DoubleArray = application!!.getMapCenter()
                val cpos = coordinates(application!!.coordinateFormat, " ", cloc[0], cloc[1])
                // Creates a new text clip to put on the clipboard
                val clip = ClipData.newPlainText("simple text", cpos)
                clipboard.setPrimaryClip(clip)
                // mPasteItem.setEnabled(true);
                return true
            }

            R.id.menuPasteLocation -> {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                /* If the clipboard doesn't contain data, disable the paste menu item.
                // If it does contain data, decide if you can handle the data.
                if (!(clipboard.hasPrimaryClip())) {
                   //Клипборда е празен
                } else if (!(clipboard.getPrimaryClipDescription().hasMimeType(MIMETYPE_TEXT_PLAIN))) {
                    // This disables the paste menu item, since the clipboard has data but it is not plain text
                   // mPasteItem.setEnabled(false);
                    Toast.makeText(getApplicationContext(), R.string.wrongClipboard, Toast.LENGTH_LONG).show();
                } else
                    { // This enables the paste menu item, since the clipboard contains plain text.*/
                val itemClip = clipboard.getPrimaryClip()!!.getItemAt(0)
                val q = itemClip.getText().toString()
                try {
                    val c: DoubleArray? = parse(q)
                    if (!c!![0].isNaN() && !c[1].isNaN()) {
                        val mapChanged: Boolean =
                            application!!.setMapCenter(c[0], c[1], true, false)
                        if (mapChanged) map!!.updateMapInfo()
                        map!!.update()
                        map!!.setFollowing(false)
                    }
                } catch (e: IllegalArgumentException) {
                }

                return true
            }

            R.id.menuSetAnchor -> {
                if (showDistance > 0) {
                    application!!.distanceOverlay!!.setAncor(application!!.getMapCenter())
                    application!!.distanceOverlay!!.setEnabled(true)
                }
                return true
            }

            R.id.menuSituationList -> {
                startActivity(Intent(this, com.borkozic.location.share.SituationListActivity::class.java))
                return true
            }

            R.id.menuPreferences -> {
                startActivity(Intent(this, Preferences::class.java))
                return true
            }
        }
        return false
    }

    private val waypointActionItemClickListener: QuickAction3D.OnActionItemClickListener =
        object : QuickAction3D.OnActionItemClickListener {
            override fun onItemClick(source: QuickAction3D?, pos: Int, actionId: Int) {
                if (waypointSelected < 0) return  // safety: not found in global list
                val wpt: Waypoint = application!!.getWaypoint(waypointSelected)!!

                when (actionId) {
                    qaEditWaypoint -> {
                        startActivityForResult(
                            Intent(this@MapActivity, WaypointProperties::class.java)
                                .putExtra("INDEX", waypointSelected)
                                .putExtra("ROUTE", 0),
                            RESULT_SAVE_WAYPOINT
                        )
                    }

                    qaNavigateToWaypoint -> {
                        // Navigate directly to this waypoint (no route needed)
                        navigationService?.navigateTo(wpt)
                    }

                    qaAddWaypointToRoute -> {
                        // Check if there's a cursor set (selected via orderpoints dialog)
                        // cursor = null means "last point" (default)
                        val cursor = application!!.routeEditingCursor
                        if (cursor != null && cursor < application!!.editingRoute!!.length() - 1) {
                            // Insert AFTER the cursor position, then advance cursor
                            val newWpt = application!!.editingRoute!!.addWaypointAt(
                                cursor + 1,
                                wpt.name,
                                wpt.latitude,
                                wpt.longitude,
                                wpt.altitude
                            )
                            application!!.routeEditingWaypoints!!.push(newWpt)
                            application!!.routeEditingCursor = cursor + 1
                        } else {
                            // No cursor or cursor at end → append to end (default behavior)
                            val addedWpt = application!!.editingRoute!!.addWaypoint(
                                wpt.name,
                                wpt.latitude,
                                wpt.longitude,
                                wpt.altitude
                            )
                            application!!.routeEditingWaypoints!!.push(addedWpt)
                        }
                        addToRouteWaypointSet(wpt)
                        map!!.invalidate()
                    }

                    qaAddWaypointToArea -> {
                        application!!.areaEditingWaypoints!!.push(
                            application!!.editingArea!!.addWaypoint(
                                wpt.name,
                                wpt.latitude,
                                wpt.longitude,
                                wpt.altitude
                            )
                        )
                        map!!.invalidate()
                    }
                }
                waypointSelected = -1
            }
        }

    private val routeActionItemClickListener: QuickAction3D.OnActionItemClickListener =
        object : QuickAction3D.OnActionItemClickListener {
            override fun onItemClick(source: QuickAction3D?, pos: Int, actionId: Int) {
                when (actionId) {
                    qaNavigateToWaypoint -> navigationService!!.setRouteWaypoint(waypointSelected)
                }
                waypointSelected = -1
            }
        }

    /** Handles the route waypoint edit popup shown during route editing (Edit / Add to end). */
    private val routeWaypointEditActionItemClickListener: QuickAction3D.OnActionItemClickListener =
        object : QuickAction3D.OnActionItemClickListener {
            override fun onItemClick(source: QuickAction3D?, pos: Int, actionId: Int) {
                when (actionId) {
                    qaEditRouteWaypoint -> {
                        val routeIdx = routeSelected
                        val wptIdx = waypointSelected
                        startActivityForResult(
                            Intent(this@MapActivity, WaypointProperties::class.java)
                                .putExtra("INDEX", wptIdx)
                                .putExtra("ROUTE", routeIdx + 1),
                            RESULT_EDIT_ROUTE
                        )
                    }
                    qaAddRouteWaypointToEnd -> {
                        val rte = application!!.getRoute(routeSelected) ?: return
                        val wpt = rte.waypoints[waypointSelected]
                        val newWpt = application!!.editingRoute!!.addWaypoint(
                            wpt.name,
                            wpt.latitude,
                            wpt.longitude,
                            wpt.altitude
                        )
                        application!!.routeEditingWaypoints!!.push(newWpt)
                        addToRouteWaypointSet(newWpt)
                        map!!.invalidate()
                    }
                }
                waypointSelected = -1
                routeSelected = -1
            }
        }

    private val mapObjectActionItemClickListener: QuickAction3D.OnActionItemClickListener =
        object : QuickAction3D.OnActionItemClickListener {
            override fun onItemClick(source: QuickAction3D?, pos: Int, actionId: Int) {
                when (actionId) {
                    qaNavigateToMapObject -> navigationService!!.navigateTo(
                        application!!.getMapObject(
                            mapObjectSelected
                        )!!
                    )
                }
                mapObjectSelected = -1
            }
        }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            RESULT_MANAGE_WAYPOINTS -> {
                application!!.waypointsOverlay!!.clearBitmapCache()
                application!!.saveWaypoints()
            }

            RESULT_LOAD_WAYPOINTS -> {
                if (resultCode == RESULT_OK) {
                    val extras = data!!.getExtras()
                    val count = extras!!.getInt("count")
                    if (count > 0) {
                        application!!.waypointsOverlay!!.clearBitmapCache()
                    }
                }
            }

            RESULT_SAVE_WAYPOINT -> {
                if (resultCode == RESULT_OK) {
                    application!!.waypointsOverlay!!.clearBitmapCache()
                    application!!.saveWaypoints()
                    if (data != null && data.hasExtra("index")
                        && PreferenceManager.getDefaultSharedPreferences(this)!!.getBoolean(
                            getString(R.string.pref_waypoint_visible),
                            getResources().getBoolean(R.bool.def_waypoint_visible)
                        )
                    ) application!!.ensureVisible(
                        application!!.getWaypoint(
                            data.getIntExtra(
                                "index",
                                -1
                            )
                        )!!
                    )
                }
            }

            RESULT_SAVE_WAYPOINTS -> if (resultCode == RESULT_OK) {
                application!!.saveDefaultWaypoints()
            }

            RESULT_MANAGE_TRACKS -> {
                val iter: MutableIterator<TrackOverlay> = application!!.fileTrackOverlays.iterator()
                while (iter.hasNext()) {
                    val to = iter.next()
                    to.onTrackPropertiesChanged()
                    if (to.getTrack().removed) {
                        to.onBeforeDestroy()
                        iter.remove()
                    }
                }
                if (resultCode == RESULT_OK) {
                    val extras = data!!.getExtras()
                    val index = extras!!.getInt("index")
                    startEditTrack(application!!.getTrack(index))
                }
            }

            RESULT_MANAGE_ROUTES -> {
                val iter: MutableIterator<RouteOverlay> = application!!.routeOverlays.iterator()
                while (iter.hasNext()) {
                    val ro = iter.next()
                    ro.onRoutePropertiesChanged()
                    if (ro.getRoute().removed) {
                        ro.onBeforeDestroy()
                        iter.remove()
                    }
                }
                if (resultCode == RESULT_OK) {
                    val extras = data!!.getExtras()
                    val index = extras!!.getInt("index")
                    val dir = extras.getInt("dir")
                    if (dir != 0) startForegroundService(
                        Intent(
                            this,
                            NavigationService::class.java
                        ).setAction(NavigationService.NAVIGATE_ROUTE)
                            .putExtra(NavigationService.EXTRA_ROUTE_INDEX, index)
                            .putExtra(NavigationService.EXTRA_ROUTE_DIRECTION, dir)
                    )
                    else startEditRoute(application!!.getRoute(index))
                }
            }

            RESULT_MANAGE_AREAS -> {
                val iter: MutableIterator<AreaOverlay> = application!!.areaOverlays.iterator()
                while (iter.hasNext()) {
                    val ro = iter.next()
                    ro.onAreaPropertiesChanged()
                    if (ro.area.removed) {
                        ro.onBeforeDestroy()
                        iter.remove()
                    }
                }
                if (resultCode == RESULT_OK) {
                    val extras = data?.getExtras() ?: return
                    val index = extras.getInt("index")
                    val dir = extras.getInt("dir")
                    if (dir != 0) startForegroundService(
                        Intent(
                            this,
                            NavigationService::class.java
                        ).setAction(NavigationService.NAVIGATE_AREA)
                            .putExtra(NavigationService.EXTRA_AREA_INDEX, index)
                    )
                    else startEditArea(application!!.getArea(index))
                }
            }

            RESULT_EDIT_ROUTE -> {
                val iter: MutableIterator<RouteOverlay> = application!!.routeOverlays.iterator()
                while (iter.hasNext()) {
                    val ro = iter.next()
                    if (ro.getRoute().editing) ro.onRoutePropertiesChanged()
                }
            }

            RESULT_EDIT_AREA -> {
                val iter: MutableIterator<AreaOverlay> = application!!.areaOverlays.iterator()
                while (iter.hasNext()) {
                    val ao = iter.next()
                    if (ao.area.editing) ao.onAreaPropertiesChanged()
                }
            }

            RESULT_ROUTE_DETAILS -> {
                if (resultCode == RESULT_OK && data != null) {
                    val extras = data.getExtras()
                    if (extras != null && extras.getBoolean("editRoute", false)) {
                        val index = extras.getInt("index")
                        startEditRoute(application!!.getRoute(index))
                    }
                }
            }

            RESULT_LOAD_MAP -> if (resultCode == RESULT_OK) {
                val extras = data!!.getExtras()
                val id = extras!!.getInt("id")
                synchronized(map!!) {
                    application!!.loadMap(id)
                    map!!.suspendBestMap()
                    setFollowing(false)
                    map!!.updateMapInfo()
                    map!!.update()
                }
            }

            RESULT_LOAD_MAP_ATPOSITION -> if (resultCode == RESULT_OK) {
                val extras = data!!.getExtras()
                val id = extras!!.getInt("id")
                if (application!!.selectMap(id)) {
                    map!!.suspendBestMap()
                    map!!.updateMapInfo()
                    map!!.update()
                } else {
                    map!!.update()
                }
            }
        }
    }

    val backHandler: Handler = Handler()

    override fun onBackPressed() {
        when (exitConfirmation) {
            0 -> {
                // wait for second back
                if (secondBack) {
                    backToast!!.cancel()
                    this@MapActivity.finish()
                } else {
                    secondBack = true
                    backToast!!.show()
                    backHandler.postDelayed(object : Runnable {
                        override fun run() {
                            secondBack = false
                        }
                    }, 2000)
                }
                return
            }

            1 -> {
                // Ask the user if they want to quit
                AlertDialog.Builder(this).setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.quitQuestion)
                    .setPositiveButton(R.string.yes, object : DialogInterface.OnClickListener {
                        override fun onClick(dialog: DialogInterface?, which: Int) {
                            // TODO change context everywhere?
                            stopService(Intent(this@MapActivity, NavigationService::class.java))
                            this@MapActivity.finish()
                        }
                    }).setNegativeButton(R.string.no, null).show()
                return
            }

            else -> super.onBackPressed()
        }
    }

    override fun onClick(v: View) {
        when (v.getId()) {
            R.id.cutbefore -> {
                application!!.editingTrack!!.cutBefore(trackBar!!.getProgress())
                val nb: Int = application!!.editingTrack!!.points.size - 1
                trackBar!!.setMax(nb)
                trackBar!!.setProgress(0)
            }

            R.id.cutafter -> {
                application!!.editingTrack!!.cutAfter(trackBar!!.getProgress())
                val na: Int = application!!.editingTrack!!.points.size - 1
                trackBar!!.setMax(na)
                trackBar!!.setProgress(0)
                trackBar!!.setProgress(na)
            }

            R.id.addpoint -> if (application!!.editingArea != null) {
                if (application!!.editingArea!!.isCircleArea()) {
                    // Circle area: set center = current map center (replace previous if any)
                    val aloc: DoubleArray = application!!.getMapCenter()
                    application!!.editingArea!!.AreaCenter = Waypoint(application!!.editingArea!!.name, "", aloc[0], aloc[1], 0.0)
                    // Recalculate area size
                    application!!.editingArea!!.areaSize = application!!.editingArea!!.calculateArea()
                    // Refresh overlay
                    val iter = application!!.areaOverlays.iterator()
                    while (iter.hasNext()) {
                        val ao = iter.next()
                        if (ao.area.editing) ao.onAreaPropertiesChanged()
                    }
                    map!!.postInvalidate()
                } else {
                    val aloc: DoubleArray = application!!.getMapCenter()
                    application!!.areaEditingWaypoints!!.push(
                        application!!.editingArea!!.addWaypoint(
                            "AWPT" + application!!.editingArea!!.length(),
                            aloc[0],
                            aloc[1]
                        )
                    )
                }
            } else {
                val aloc: DoubleArray = application!!.getMapCenter()
                val alt = lastElevation
                val name = "RWPT" + routeWaypointNameCounter++
                val cursor = application!!.routeEditingCursor
                val wpt = if (cursor != null && cursor < application!!.editingRoute!!.length() - 1) {
                    // Add AFTER the cursor position
                    application!!.editingRoute!!.addWaypointAt(cursor + 1, name, aloc[0], aloc[1], alt)
                } else {
                    // No cursor or cursor at end → add to end
                    application!!.editingRoute!!.addWaypoint(name, aloc[0], aloc[1], alt)
                }
                application!!.routeEditingWaypoints!!.push(wpt)
                addToRouteWaypointSet(wpt)
                // Update cursor to the newly added point
                application!!.routeEditingCursor = application!!.editingRoute!!.length() - 1
                Log.d(TAG, "addpoint: $name lat=${aloc[0]} lon=${aloc[1]} alt=$alt cursor=$cursor editingRoute=${application!!.editingRoute!!.name}")
            }

            R.id.insertpoint -> if (application!!.editingArea != null) {
                val iloc: DoubleArray = application!!.getMapCenter()
                application!!.areaEditingWaypoints!!.push(
                    application!!.editingArea!!.insertWaypoint(
                        "AWPT" + application!!.editingArea!!.length(),
                        iloc[0],
                        iloc[1]
                    )
                )
            } else {
                val iloc: DoubleArray = application!!.getMapCenter()
                val alt = lastElevation
                val name = "RWPT" + routeWaypointNameCounter++
                val cursor = application!!.routeEditingCursor
                val wpt = if (cursor != null && cursor > 0) {
                    // Insert BEFORE the cursor position
                    application!!.editingRoute!!.addWaypointAt(cursor, name, iloc[0], iloc[1], alt)
                } else {
                    // No cursor or cursor at start → use auto-insert (finds best position)
                    application!!.editingRoute!!.insertWaypoint(name, iloc[0], iloc[1], alt)
                }
                application!!.routeEditingWaypoints!!.push(wpt)
                addToRouteWaypointSet(wpt)
                // Update cursor to the newly inserted point
                application!!.routeEditingCursor = application!!.editingRoute!!.length() - 1
                Log.d(TAG, "insertpoint: $name lat=${iloc[0]} lon=${iloc[1]} alt=$alt cursor=$cursor editingRoute=${application!!.editingRoute!!.name}")
            }

            R.id.removepoint -> if (application!!.editingArea != null) {
                if (!application!!.areaEditingWaypoints!!.empty()) {
                    application!!.editingArea!!.removeWaypoint(application!!.areaEditingWaypoints!!.pop()!!)
                }
            } else {
                if (!application!!.routeEditingWaypoints!!.empty()) {
                    application!!.editingRoute!!.removeWaypoint(application!!.routeEditingWaypoints!!.pop()!!)
                }
            }

            R.id.orderpoints -> if (application!!.editingArea != null) {
                startActivityForResult(
                    Intent(this, AreaEdit::class.java).putExtra(
                        "INDEX",
                        application!!.getAreaIndex(application!!.editingArea!!)
                    ), RESULT_EDIT_AREA
                )
            } else {
                // Show point list dialog to select cursor position
                val route = application!!.editingRoute!!
                val currentCursor = application!!.routeEditingCursor
                val cursor = currentCursor ?: (route.length() - 1) // Default: last point
                
                // Mark cursor point with (cursor) label
                val names = route.waypoints.mapIndexed { index, wpt ->
                    if (index == cursor) "${wpt.name} ←CURSOR" else wpt.name
                }.toTypedArray()
                
                AlertDialog.Builder(this)
                    .setTitle("Cursor Position")
                    .setItems(names) { _, which ->
                        application!!.routeEditingCursor = which
                    }
                    .setNegativeButton("Close") { _, _ ->
                        // Simply dismiss - cursor stays at current position
                    }
                    .show()
            }

            R.id.finishedit -> if (application!!.editingArea != null) {
                if ("New area" == application!!.editingArea!!.name || "New Circle" == application!!.editingArea!!.name) {
                    val formatter = SimpleDateFormat("yyyy-MM-dd_HH-mm")
                    application!!.editingArea!!.name = formatter.format(Date())
                }
                // Calculate area size before finishing edit
                application!!.editingArea!!.areaSize = application!!.editingArea!!.calculateArea()
                application!!.editingArea!!.editing = false
                val iter: MutableIterator<AreaOverlay> = application!!.areaOverlays.iterator()
                while (iter.hasNext()) {
                    val ro = iter.next()
                    ro.onAreaPropertiesChanged()
                }
                application!!.editingArea = null
                application!!.areaEditingWaypoints = null
                // Restore all edit buttons visibility
                findViewById<View?>(R.id.insertpoint).setVisibility(View.VISIBLE)
                findViewById<View?>(R.id.removepoint).setVisibility(View.VISIBLE)
                findViewById<View?>(R.id.orderpoints).setVisibility(View.VISIBLE)
                findViewById<View?>(R.id.editroute).setVisibility(View.GONE) //лентата с която се редактира маршрута/зоната изчезва - използва същата лената и а маршрута
                updateGPSStatus()
                if (showDistance == 2) {
                    application!!.distanceOverlay!!.setEnabled(true)
                }
                updateMapViewArea()
                map!!.requestFocus()
            } else {
                if ("New route" == application!!.editingRoute!!.name) {
                    val formatter = SimpleDateFormat("yyyy-MM-dd_HH-mm")
                    application!!.editingRoute!!.name = formatter.format(Date())
                }
                application!!.editingRoute!!.editing = false
                val iter: MutableIterator<RouteOverlay> = application!!.routeOverlays.iterator()
                while (iter.hasNext()) {
                    val ro = iter.next()
                    ro.onRoutePropertiesChanged()
                }
                application!!.editingRoute = null
                application!!.routeEditingWaypoints = null
                application!!.routeEditingCursor = null
                findViewById<View?>(R.id.editroute).setVisibility(View.GONE) //лентата с която се редактира маршрута изчезва
                updateGPSStatus()
                if (showDistance == 2) {
                    application!!.distanceOverlay!!.setEnabled(true)
                }
                updateMapViewArea()
                map!!.requestFocus()
            }

            R.id.finishtrackedit -> {
                application!!.editingTrack!!.editing = false
                application!!.editingTrack!!.editingPos = -1
                application!!.editingTrack = null
                findViewById<View?>(R.id.edittrack).setVisibility(View.GONE)
                findViewById<View?>(R.id.trackdetails).setVisibility(View.GONE)
                updateGPSStatus()
                if (showDistance == 2) {
                    application!!.distanceOverlay!!.setEnabled(true)
                }
                map!!.setFocusable(true)
                map!!.setFocusableInTouchMode(true)
                map!!.requestFocus()
            }
        }
    }

    override fun onWaypointView(waypoint: Waypoint) {
        application!!.ensureVisible(waypoint)
    }

    override fun onWaypointNavigate(waypoint: Waypoint) {
        val intent = Intent(getApplicationContext(), NavigationService::class.java).setAction(
            NavigationService.NAVIGATE_MAPOBJECT
        )
        intent.putExtra(NavigationService.EXTRA_NAME, waypoint.name)
        intent.putExtra(NavigationService.EXTRA_LATITUDE, waypoint.latitude)
        intent.putExtra(NavigationService.EXTRA_LONGITUDE, waypoint.longitude)
        intent.putExtra(NavigationService.EXTRA_PROXIMITY, waypoint.proximity)
        startService(intent)
    }

    override fun onWaypointEdit(waypoint: Waypoint) {
        val index: Int = application!!.getWaypointIndex(waypoint)
        startActivityForResult(
            Intent(this, WaypointProperties::class.java).putExtra(
                "INDEX",
                index
            ), RESULT_SAVE_WAYPOINT
        )
    }

    override fun onWaypointShare(waypoint: Waypoint) {
        val i = Intent(Intent.ACTION_SEND)
        i.setType("text/plain")
        i.putExtra(Intent.EXTRA_SUBJECT, R.string.currentloc)
        val coords =
            coordinates(application!!.coordinateFormat, " ", waypoint.latitude, waypoint.longitude)
        i.putExtra(Intent.EXTRA_TEXT, waypoint.name + " @ " + coords)
        startActivity(Intent.createChooser(i, getString(R.string.menu_share)))
    }

    override fun onWaypointRemove(waypoint: Waypoint) {
        AlertDialog.Builder(this).setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(R.string.removeWaypointQuestion)
            .setPositiveButton(R.string.yes, object : DialogInterface.OnClickListener {
                override fun onClick(dialog: DialogInterface?, which: Int) {
                    val wptset = waypoint.set
                    application!!.removeWaypoint(waypoint)
                    application!!.saveWaypoints(wptset!!)
                    map!!.invalidate()
                }
            }).setNegativeButton(R.string.no, null).show()
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        when (seekBar.getId()) {
            R.id.trackbar -> {
                if (fromUser) {
                    application!!.editingTrack!!.editingPos = progress
                }
                val tp: TrackPoint = application!!.editingTrack!!.getPoint(progress)
                val ele = tp.elevation * elevationFactor
                (findViewById<View?>(R.id.tp_number) as TextView).setText("#" + (progress + 1))
                // FIXME Need UTM support here
                (findViewById<View?>(R.id.tp_latitude) as TextView).setText(
                    coordinate(
                        application!!.coordinateFormat,
                        tp.latitude
                    )
                )
                (findViewById<View?>(R.id.tp_longitude) as TextView).setText(
                    coordinate(
                        application!!.coordinateFormat,
                        tp.longitude
                    )
                )
                (findViewById<View?>(R.id.tp_elevation) as TextView).setText(
                    Math.round(ele).toString() + " " + elevationAbbr
                )
                (findViewById<View?>(R.id.tp_time) as TextView).setText(
                    SimpleDateFormat.getDateTimeInstance(
                        SimpleDateFormat.SHORT, SimpleDateFormat.SHORT
                    ).format(Date(tp.time))
                )
                val mapChanged: Boolean =
                    application!!.setMapCenter(tp.latitude, tp.longitude, false, false)
                if (mapChanged) map!!.updateMapInfo()
                map!!.update()
            }
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {
    }

    override fun onStopTrackingTouch(seekBar: SeekBar?) {
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        Log.e(TAG, "onRestoreInstanceState()")
        lastKnownLocation = savedInstanceState.getParcelable<Location?>("lastKnownLocation")
        lastRenderTime = savedInstanceState.getLong("lastRenderTime")
        lastMagnetic = savedInstanceState.getLong("lastMagnetic")
        lastDim = savedInstanceState.getLong("lastDim")
        lastGeoid = savedInstanceState.getBoolean("lastGeoid")

        waypointSelected = savedInstanceState.getInt("waypointSelected")
        routeSelected = savedInstanceState.getInt("routeSelected")
        areaSelected = savedInstanceState.getInt("areaSelected")
        mapObjectSelected = savedInstanceState.getLong("mapObjectSelected")

        /*
         double[] distAncor = savedInstanceState.getDoubleArray("distAncor");
         if (distAncor != null)
          {
          application.distanceOverlay = new DistanceOverlay(this);
          application.distanceOverlay.setAncor(distAncor);
          }
         */
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.e(TAG, "onSaveInstanceState()")
        outState.putParcelable("lastKnownLocation", lastKnownLocation)
        outState.putLong("lastRenderTime", lastRenderTime)
        outState.putLong("lastMagnetic", lastMagnetic)
        outState.putLong("lastDim", lastDim)
        outState.putBoolean("lastGeoid", lastGeoid)

        outState.putInt("waypointSelected", waypointSelected)
        outState.putInt("routeSelected", routeSelected)
        outState.putInt("areaSelected", areaSelected)
        outState.putLong("mapObjectSelected", mapObjectSelected)

        if (application!!.distanceOverlay != null) {
            outState.putDoubleArray("distAncor", application!!.distanceOverlay!!.getAncor())
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        val resources = getResources()
        // application preferences
        if (getString(R.string.pref_folder_data) == key) {
            application!!.setDataPath(
                Borkozic.PATH_DATA,
                sharedPreferences.getString(key, resources.getString(R.string.def_folder_data))!!
            )
        } else if (getString(R.string.pref_folder_sas) == key) {
            application!!.setDataPath(
                Borkozic.PATH_SAS,
                sharedPreferences.getString(key, resources.getString(R.string.def_folder_sas))!!
            )
        } else if (getString(R.string.pref_folder_icon) == key) {
            application!!.setDataPath(
                Borkozic.PATH_ICONS,
                sharedPreferences.getString(key, resources.getString(R.string.def_folder_icon))!!
            )
        } else if (getString(R.string.pref_plane_type) == key) {
            val planeType = sharedPreferences.getString(key, "L39")!!
            val planesDir = File(application!!.rootPath, "../planes/$planeType")
            application!!.setDataPath(Borkozic.PATH_PLANES, planesDir.absolutePath)
            map!!.planeLogo = planeType
            map!!.setMovingCursorSize(
                sharedPreferences.getInt(
                    "planelogosize",
                    resources.getInteger(R.integer.def_planelogosize)
                )
            )
        } else if (getString(R.string.pref_planelogosize) == key) {
            map!!.setMovingCursorSize(
                sharedPreferences.getInt(
                    key,
                    resources.getInteger(R.integer.def_planelogosize)
                )
            )
        } else if (getString(R.string.pref_orientation) == key) {
            setRequestedOrientation(sharedPreferences.getString(key, "-1")!!.toInt())
        } else if (getString(R.string.pref_grid_mapshow) == key) {
            application!!.mapGrid = sharedPreferences.getBoolean(key, false)
            application!!.initGrids()
        } else if (getString(R.string.pref_grid_usershow) == key) {
            application!!.userGrid = sharedPreferences.getBoolean(key, false)
            application!!.initGrids()
        } else if (getString(R.string.pref_grid_preference) == key) {
            application!!.gridPrefer = sharedPreferences.getString(key, "0")!!.toInt()
            application!!.initGrids()
        } else if (getString(R.string.pref_grid_userscale) == key || getString(R.string.pref_grid_userunit) == key || getString(
                R.string.pref_grid_usermpp
            ) == key
        ) {
            application!!.initGrids()
        } else if (getString(R.string.pref_useonlinemap) == key && sharedPreferences.getBoolean(
                key,
                false
            )
        ) {
            application!!.setOnlineMap(
                sharedPreferences.getString(
                    getString(R.string.pref_onlinemap),
                    resources.getString(R.string.def_onlinemap)
                )!!
            )
        } else if (getString(R.string.pref_onlinemap) == key || getString(R.string.pref_onlinemapscale) == key) {
            application!!.setOnlineMap(
                sharedPreferences.getString(
                    getString(R.string.pref_onlinemap),
                    resources.getString(R.string.def_onlinemap)
                )!!
            )
        } else if (getString(R.string.pref_mapadjacent) == key) {
            application!!.adjacentMaps =
                sharedPreferences.getBoolean(key, resources.getBoolean(R.bool.def_mapadjacent))
        } else if (getString(R.string.pref_mapcropborder) == key) {
            application!!.cropMapBorder =
                sharedPreferences.getBoolean(key, resources.getBoolean(R.bool.def_mapcropborder))
        } else if (getString(R.string.pref_mapdrawborder) == key) {
            application!!.drawMapBorder =
                sharedPreferences.getBoolean(key, resources.getBoolean(R.bool.def_mapdrawborder))
        } else if (getString(R.string.pref_wakelock) == key) {
            keepScreenOn =
                sharedPreferences.getBoolean(key, resources.getBoolean(R.bool.def_wakelock))
            val wnd = getWindow()
            if (wnd != null) {
                if (keepScreenOn) wnd.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else wnd.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        } else if (getString(R.string.pref_exit) == key) {
            exitConfirmation = sharedPreferences.getString(key, "0")!!.toInt()
            secondBack = false
        } else if (getString(R.string.pref_unitprecision) == key) {
            val precision =
                sharedPreferences.getBoolean(key, resources.getBoolean(R.bool.def_unitprecision))
            precisionFormat = if (precision) "%.1f" else "%.0f"
        } else if (getString(R.string.pref_cursorcolor) == key) {
            map!!.setCursorColor(
                sharedPreferences.getInt(
                    key,
                    ContextCompat.getColor(getApplicationContext(), R.color.cursor)
                )
            )
        } else if (getString(R.string.pref_panelactions) == key) {
            val pa: String =
                sharedPreferences.getString(key, resources.getString(R.string.def_panelactions))!!
            activeActions =
                Arrays.asList<String?>(*pa.split(",".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray())
        } else if (getString(R.string.pref_waypoint_width) == key ||
            getString(R.string.pref_waypoint_textsize) == key ||
            getString(R.string.pref_waypoint_color) == key ||
            getString(R.string.pref_waypoint_namecolor) == key ||
            getString(R.string.pref_waypoint_bgcolor) == key ||
            getString(R.string.pref_waypoint_showname) == key
        ) {
            updateOverlays(sharedPreferences, false)
        }
    }




    @SuppressLint("HandlerLeak")
    private inner class FinishHandler(activity: MapActivity?) : Handler() {
        private val target: WeakReference<MapActivity?>

        init {
            this.target = WeakReference<MapActivity?>(activity)
        }

        override fun handleMessage(msg: Message) {
            val mapActivity = target.get()
            if (mapActivity != null) {
                mapActivity.waitBar!!.setVisibility(View.INVISIBLE)
                mapActivity.waitBar!!.setText("")
            }
        }
    }

    /**
     * Finds or creates a "RouteWaypoints" WaypointSet so that waypoints
     * added during route editing are also available in the waypoint list
     * for reuse across routes.
     */
    private fun ensureRouteWaypointSet() {
        if (routeWaypointSet != null) return
        // Look for existing set
        for (wptset in application!!.waypointSets) {
            if ("RouteWaypoints" == wptset.name) {
                routeWaypointSet = wptset
                return
            }
        }
        // Create new set
        val path = application!!.dataPath + File.separator + "RouteWaypoints.wpt"
        routeWaypointSet = WaypointSet(path, "RouteWaypoints")
        application!!.addWaypointSet(routeWaypointSet)
    }

    /**
     * Adds a waypoint to the RouteWaypoints set so it can be reused across routes.
     * Called every time a waypoint is added to the editing route.
     * Borkozic.addWaypoint() assigns wpt.set = defWaypointSet by default;
     * we override it to point to the RouteWaypoints set for proper grouping.
     */
    private fun addToRouteWaypointSet(wpt: Waypoint) {
        ensureRouteWaypointSet()
        application!!.addWaypoint(wpt)
        wpt.set = routeWaypointSet  // override defWaypointSet assignment
        application!!.saveWaypoints(routeWaypointSet!!)
    }

    companion object {
        private const val TAG = "MapActivity"
        const val BTN_TITLE: String = "BtnTitle"
        private const val RESULT_MANAGE_WAYPOINTS = 0x200
        private const val RESULT_LOAD_WAYPOINTS = 0x300
        private const val RESULT_SAVE_WAYPOINT = 0x400
        private const val RESULT_LOAD_MAP = 0x500
        private const val RESULT_MANAGE_TRACKS = 0x600
        private const val RESULT_MANAGE_ROUTES = 0x900
        private const val RESULT_EDIT_ROUTE = 0x110
        private const val RESULT_LOAD_MAP_ATPOSITION = 0x120
        private const val RESULT_SAVE_WAYPOINTS = 0x140

        private const val RESULT_MANAGE_AREAS = 0x150
        private const val RESULT_EDIT_AREA = 0x160
        private const val RESULT_ROUTE_DETAILS = 0x170

        private const val qaAddWaypointToRoute = 1
        private const val qaNavigateToWaypoint = 2
        private const val qaNavigateToMapObject = 2

        private const val qaAddWaypointToArea = 3
        private const val qaEditWaypoint = 4
        private const val qaEditRouteWaypoint = 5
        private const val qaAddRouteWaypointToEnd = 6

        private val SCREEN_ORIENTATION_PORTRAIT = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
}
