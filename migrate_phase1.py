#!/usr/bin/env python3
"""Phase 1: Replace setContentView block with Compose."""
import re

PATH = "/mnt/d/Borkozic_Versions/Borkozic-Kotlin_Oki/borkozic/src/main/java/com/borkozic/MapActivity.kt"

with open(PATH, 'r') as f:
    content = f.read()

# 1. Add uiState declaration after isTrackingState
content = content.replace(
    '    private var isTrackingState by mutableStateOf(false)\n    var disable',
    '    private var isTrackingState by mutableStateOf(false)\n    private var uiState by mutableStateOf(MapUiState())\n    var disable'
)

# 2. Replace the entire setContentView + findViewById block + SidePanel block
marker_start = '        setContentView(R.layout.act_main)\n'
marker_end   = '        wptQuickActionAddToRoute = QuickAction3D('

idx1 = content.find(marker_start)
idx2 = content.find(marker_end)
if idx1 == -1 or idx2 == -1:
    print(f"Markers not found: start={idx1}, end={idx2}")
    exit(1)

old = content[idx1:idx2]

# We need to find the start of old block from idx1, but actually idx1 is AFTER map = findViewById
# Wait, no — idx1 IS setContentView line. Let's find from idx1 back to get context
start_line = content.rfind('\n', 0, idx1)
print(f"Replacing from line {start_line} to {idx2}")

replacement = """        // ── Compose Root + MapView ──────────────────────────────────────
        map = MapView(this@MapActivity)
        map!!.setFocusable(true)
        map!!.setFocusableInTouchMode(true)
        map!!.requestFocus()

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
            }
        }

"""

content = content[:idx1] + replacement + content[idx2:]

# 3. Remove map!!.initialize block that's now redundant (after trackBar!!.setOnSeekBarChangeListener)
# Find from just after mobQuickAction...onActionItemClickListener to dimView = RelativeLayout(this)
old_block = """        mobQuickAction!!.setOnActionItemClickListener(mapObjectActionItemClickListener)

        trackBar!!.setOnSeekBarChangeListener(this)
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

        dimView"""

new_block = """        mobQuickAction!!.setOnActionItemClickListener(mapObjectActionItemClickListener)

        dimView"""

if old_block in content:
    content = content.replace(old_block, new_block)
else:
    print("WARNING: could not find map init block")
    idx = content.find('trackBar!!.setOnSeekBarChangeListener(this)')
    if idx >= 0:
        print(f"Found at {idx}: {content[idx-30:idx+100]}")

with open(PATH, 'w') as f:
    f.write(content)
print("Phase 1 done.")
