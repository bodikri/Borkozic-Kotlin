#!/usr/bin/env python3
"""MapActivity Compose Migration - Phase 1: onCreate + MapUiState wiring."""
import re

PATH = "/mnt/d/Borkozic_Versions/Borkozic-Kotlin_Oki/borkozic/src/main/java/com/borkozic/MapActivity.kt"

with open(PATH, 'r') as f:
    lines = f.readlines()

content = ''.join(lines)

# ── 1. Add uiState after isTrackingState ──────────────────────────────
content = content.replace(
    '    private var isTrackingState by mutableStateOf(false)\n    var disable',
    '    private var isTrackingState by mutableStateOf(false)\n    private var uiState by mutableStateOf(MapUiState())\n    var disable'
)

# ── 2. Replace setContentView + findViewById block + SidePanel findViewById ──────────────
# From setContentView(R.layout.act_main) to just before wptQuickActionAddToRoute
old_block = '''        setContentView(R.layout.act_main)
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
'''

new_block = '''        // ── Create MapView programmatically (was in XML inc_map.xml) ──────
        map = MapView(this)
        map!!.planeLogo = (application!!.planePath!!).substring(36)
        map!!.setMovingCursorSize(
            settings.getInt(
                "planelogosize",
                resources.getInteger(R.integer.def_planelogosize)
            )
        )
        map!!.initialize(application!!)
        map!!.setFocusable(true)
        map!!.setFocusableInTouchMode(true)
        map!!.requestFocus()

        // ── Compose Root ───────────────────────────────────────────────────
        val panelOnLeft = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if (settings.getBoolean(getString(R.string.ui_drawer_open), false)) {
            isPanelOpen = true
        }

        setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { map!! },
                    modifier = Modifier.fillMaxSize()
                )
                MapScreen(
                    uiState = uiState,
                    onAction = { action ->
                        when (action) {
                            MapScreenAction.CutBefore -> onCutBefore()
                            MapScreenAction.CutAfter -> onCutAfter()
                            MapScreenAction.FinishTrackEdit -> onFinishTrackEdit()
                            MapScreenAction.FinishEdit -> onFinishEdit()
                            MapScreenAction.AddPoint -> onAddPoint()
                            MapScreenAction.InsertPoint -> onInsertPoint()
                            MapScreenAction.RemovePoint -> onRemovePoint()
                            MapScreenAction.OrderPoints -> onOrderPoints()
                        }
                    }
                )
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
        }
'''

if old_block in content:
    content = content.replace(old_block, new_block)
    # print("✓ Replaced onCreate block")
else:
    # print("⚠ Could not find exact onCreate block; try manual edit")
    # Debug: show surrounding text
    idx = content.find('setContentView(R.layout.act_main)')
    if idx >= 0:
        # print(f"Found setContentView at {idx}")
        # print(repr(content[idx:idx+200]))
        pass

# ── 3. Move map!!.initialize/app logo BEFORE Compose ────────────────
# These were after trackBar!!.setOnSeekBarChangeListener(this) — remove them
map_init_block = '''        trackBar!!.setOnSeekBarChangeListener(this)
        //Да се заредят planeLogo and size
        //onSharedPreferenceChanged(settings, getString(R.string.pref_exit));
        map!!.planeLogo = (application!!.planePath!!).substring(36)
        map!!.setMovingCursorSize(
            settings.getInt(
                "planelogosize",
                resources.getInteger(R.integer.def_planelogosize)
            )
        )
        map!!.initialize(application!!)
'''

map_init_replacement = '''        // trackBar removed (handled inline in Compose); MapView initialized above
'''

if map_init_block in content:
    content = content.replace(map_init_block, map_init_replacement)

# ── 4. Replace textView.setText + visibility calls with uiState ────
# updateCoordinates
content = re.sub(
    r'this\.runOnUiThread\(object : Runnable \{\s*override fun run\(\) \{\s*coordinates!!\.setText\(pos\)\s*\}\s*\}\)',
    'uiState = uiState.copy(coordinates = pos)',
    content
)

# updateFileInfo
content = re.sub(
    r'if \(title != null\) \{\s*currentFile!!\.setText\(title\)\s*\} else \{\s*currentFile!!\.setText\("-no map-"\)\s*\}',
    'uiState = uiState.copy(currentFile = title ?: "-no map-")',
    content
)

# updateZoomInfo
content = re.sub(
    r'if \(zoom == 0\.0\) \{\s*mapZoom!!\.setText\("---%"\)\s*\} else \{[^{}]*mapZoom!!\.setText\([^\)]+\)[^}]*\}',
    '''if (zoom == 0.0) {
                uiState = uiState.copy(mapZoom = "---%")
            } else {
                val rz = floor(zoom).toInt()
                val zoomStr = if (zoom - rz != 0.0) String.format("%.1f", zoom) else rz.toString()
                uiState = uiState.copy(mapZoom = zoomStr + "%")
            }''',
    content
)

# Update GPS status visibility → showMovingInfo
content = re.sub(
    r'val view = findViewById<View>\(R\.id\.movinginfo\)\s*if \(view\.getVisibility\(\) != v\) \{\s*view\.setVisibility\(v\)\s*updateMapViewArea\(\)\s*\}',
    'val showMoving = map!!.isMoving() && application!!.editingRoute == null && application!!.editingTrack == null\n        if (uiState.showMovingInfo != showMoving) {\n            uiState = uiState.copy(showMovingInfo = showMoving)\n            updateMapViewArea()\n        }',
    content
)

# Wait bar visibility in zoom operations
content = re.sub(
    r'waitBar!!\.visibility = View\.VISIBLE\s*waitBar!!\.setText\(R\.string\.msg_wait\)',
    'uiState = uiState.copy(showWaitBar = true, waitBarText = getString(R.string.msg_wait))',
    content
)

# customizeLayout - showSatInfoBar and showMapInfoBar
content = re.sub(
    r'findViewById<View\?>\(R\.id\.satinfo\)\.setVisibility\(if \(slVisible\) View\.VISIBLE else View\.GONE\)',
    'uiState = uiState.copy(showSatInfoBar = slVisible)',
    content
)
content = re.sub(
    r'findViewById<View\?>\(R\.id\.mapinfo\)\.setVisibility\(if \(mlVisible\) View\.VISIBLE else View\.GONE\)',
    'uiState = uiState.copy(showMapInfoBar = mlVisible)',
    content
)

# startEditTrack
content = re.sub(
    r'findViewById<View\?>\(R\.id\.edittrack\)\.setVisibility\(View\.VISIBLE\)\s*findViewById<View\?>\(R\.id\.trackdetails\)\.setVisibility\(View\.VISIBLE\)',
    'uiState = uiState.copy(showEditTrack = true, showTrackDetails = true)',
    content
)

# startEditRoute
content = re.sub(
    r"findViewById<View\?>\(R\.id\.editroute\)\.setVisibility\(View\.VISIBLE\) //използвам същият панел с бутони за едитване на маршрут",
    'uiState = uiState.copy(showEditRoute = true)',
    content
)

# startEditArea (same editroute panel reused)
content = re.sub(
    r"findViewById<View\?>\(R\.id\.editroute\)\.setVisibility\(View\.VISIBLE\) //използвам същият панел с бутони за едитване на маршрут",
    'uiState = uiState.copy(showEditArea = true)',
    content
)

# finishEdit area/route hide panel (two occurrences - one for area, one for route)
content = re.sub(
    r'findViewById<View\?>\(R\.id\.editroute\)\.setVisibility\(View\.GONE\) //лентата с която се редактира маршрута/зоната изчезва - използва същата лената и а маршрута',
    'uiState = uiState.copy(showEditRoute = false, showEditArea = false)',
    content
)

content = re.sub(
    r'findViewById<View\?>\(R\.id\.editroute\)\.setVisibility\(View\.GONE\) //лентата с която се редактира маршрута изчезва',
    'uiState = uiState.copy(showEditRoute = false, showEditArea = false)',
    content
)

# finishEditTrack
content = re.sub(
    r'findViewById<View\?>\(R\.id\.edittrack\)\.setVisibility\(View\.GONE\)\s*findViewById<View\?>\(R\.id\.trackdetails\)\.setVisibility\(View\.GONE\)',
    'uiState = uiState.copy(showEditTrack = false, showTrackDetails = false)',
    content
)

# onResume speed/elevation/track unit setText
content = re.sub(
    r'speedUnit!!\.setText\(speedAbbr\)\s*val distanceIdx',
    'uiState = uiState.copy(speedUnit = speedAbbr ?: "--")\n        val distanceIdx',
    content
)
content = re.sub(
    r'elevationUnit!!\.setText\(elevationAbbr\)\s*StringFormatter\.distanceFactor',
    'uiState = uiState.copy(elevationUnit = elevationAbbr ?: "m")\n        StringFormatter.distanceFactor',
    content
)
content = re.sub(
    r'trackUnit!!\.setText\(\(if \(application!!\.angleType == 0\) "deg" else getString\(R\.string\.degmag\)\)\)\s*//bearingUnit\.setText',
    'uiState = uiState.copy(trackUnit = if (application!!.angleType == 0) "deg" else getString(R.string.degmag))\n        //bearingUnit.setText',
    content
)

# Edit panel button (early visibility when editingRoute/Area not null)
content = re.sub(
    r'findViewById<View\?>\(R\.id\.editroute\)\.setVisibility\(if \(application!!\.editingRoute != null \|\| application!!\.editingArea != null\) View\.VISIBLE else View\.GONE\) //появяа се само при режим на редакция на маршрут/зона',
    ''
    # This is handled by startEdit... methods already, or we add state update
    # Actually let's set it properly
)

with open(PATH, 'w') as f:
    f.write(content)

# print("Done - Phase 1 written.")
