# 🧭 Sliding Right Panel (Плъзгащ се страничен панел)

> **Status:** ✅ Migrated to Jetpack Compose
> **Last updated:** 2026-05-22
> **Component type:** UI Panel / Overlay
> **Visibility:** Handle button (19×57dp) always visible when closed; panel slides in from right edge

---

## 1. Overview (Общ преглед)

The **Sliding Right Panel** is a semi-transparent overlay panel on the right edge of the screen containing action buttons. It opens/closes via a small handle button and features a scrim overlay for tap-to-close. Button icons update reactively based on application state.

**Buttons:**
- **EP** / **NP** — Emergency/Normal procedure preset zoom levels
- **Zoom In/Out, Next/Prev Map, Maps at Cursor, Waypoints, Info** — Configurable via settings
- **Follow, Locate, Tracking, Expand** — Dynamic state-aware icons
- **Zero Level, Clear** — Utility buttons

Visible dynamic buttons are **configurable** via `Settings → Display → Sliding panel actions`.

---

## 2. Current Implementation — Jetpack Compose

### 2.1 Architecture

```
┌──────────────────────────────────────────┐
│ act_main.xml (RelativeLayout)            │
│  ┌────────────────────────────────────┐  │
│  │ inc_map (MapView)                  │  │
│  │                                    │  │
│  │   ┌──────────────────────────┐     │  │
│  │   │ ComposeView             │     │  │
│  │   │  SidePanel()            │     │  │
│  │   │   ├─ Scrim (tap close)  │     │  │
│  │   │   ├─ Handle button      │     │  │
│  │   │   │  (19×57dp, visible  │     │  │
│  │   │   │   only when closed) │     │  │
│  │   │   └─ Panel content      │     │  │
│  │   │      ├─ EP / NP         │     │  │
│  │   │      ├─ Dynamic actions │     │  │
│  │   │      └─ Zero / Clear    │     │  │
│  │   └──────────────────────────┘     │  │
│  └────────────────────────────────────┘  │
└──────────────────────────────────────────┘
     ▲
     │  MapActivity.kt
     │  - onSidePanelAction() dispatches clicks
     │  - MutableState for follow/locate/tracking
     │  - activeActions from SharedPreferences
     │  - No more OnPanelListener / updateMapButtons()
```

### 2.2 Core Component: `SidePanel.kt`

**File:** `borkozic/src/main/java/com/borkozic/ui/SidePanel.kt`
**Lines:** ~275 lines of Kotlin Compose code

**Key features:**
- `AnimatedVisibility` with `slideInHorizontally` / `slideOutHorizontally` for panel animation
- `fadeIn` / `fadeOut` for handle button and scrim
- Handle button: 19×57dp semi-transparent (alpha 0.35), centered vertically, flush to right edge
- Handle button **disappears** when panel opens, **reappears** when panel closes
- Panel content: 80dp wide `LazyColumn` with static + dynamic buttons
- Scrim: tap to close (black at 30% opacity)
- Animation duration: 300ms (`tween`)
- Panel stays flush to right edge — slides in/out from right only

**Why `AnimatedVisibility`, not `ModalNavigationDrawer`:**
- Compose 1.4.3 + Material3 1.1.2 do not include `ModalNavigationDrawer`
- `AnimatedVisibility` + custom layout achieves identical behavior

### 2.3 Layout Structure

**Portrait & Landscape (`act_main.xml`):**
```xml
<androidx.compose.ui.platform.ComposeView
    android:id="@+id/side_panel"
    android:layout_width="wrap_content"
    android:layout_height="match_parent"
    android:layout_alignParentRight="true" />
```

Single layout for both orientations — no more separate portrait/landscape Panel variants.

### 2.4 Compose Component Structure

```kotlin
@Composable
fun SidePanel(
    isOpen: Boolean,
    isOnLeft: Boolean,          // future: landscape left-side support
    onOpenChanged: (Boolean) -> Unit,
    activeActions: List<String>, // from panelactions preference
    isFollowing: Boolean,        // reactive icon state
    isLocating: Boolean,         // reactive icon state
    isTracking: Boolean,         // reactive icon state
    isFullscreen: Boolean,       // expand button state
    onAction: (SidePanelAction) -> Unit,
)

enum class SidePanelAction {
    EP, NP,
    ZOOM_IN, ZOOM_OUT, NEXT_MAP, PREV_MAP,
    MAPS_AT_CURSOR, WAYPOINTS, INFO,
    FOLLOW, LOCATE, TRACKING, EXPAND,
    ZERO_LEVEL, CLEAR
}
```

### 2.5 Button Configuration

Same as legacy — controlled by `activeActions` parsed from `panelactions` preference.

**Static buttons (always visible):**
| Button | Enum | Type |
|---|---|---|
| EP | `SidePanelAction.EP` | Text button |
| NP | `SidePanelAction.NP` | Text button |
| Zero Level | `SidePanelAction.ZERO_LEVEL` | Text button |
| Clear | `SidePanelAction.CLEAR` | Text button |

**Dynamic buttons (from activeActions):**
| Key | Enum | Icon Behavior |
|---|---|---|
| `zoomin` | `ZOOM_IN` | Static icon |
| `zoomout` | `ZOOM_OUT` | Static icon |
| `nextmap` | `NEXT_MAP` | Static icon |
| `prevmap` | `PREV_MAP` | Static icon |
| `maps` | `MAPS_AT_CURSOR` | Static icon |
| `waypoints` | `WAYPOINTS` | Static icon |
| `info` | `INFO` | Static icon |
| `follow` | `FOLLOW` | `cursor_drag_arrow` ↔ `target` |
| `locate` | `LOCATE` | `pin_map_no` ↔ `pin_map` |
| `tracking` | `TRACKING` | `doc_delete` ↔ `doc_edit` |
| `expand` | `EXPAND` | `collapse` ↔ `expand` |

### 2.6 Reactive State (Динамични икони)

No manual `updateMapButtons()` calls. Instead, `MutableState` in `MapActivity`:

```kotlin
var isFollowingState by mutableStateOf(false)
var isLocatingState by mutableStateOf(false)
var isTrackingState by mutableStateOf(false)
var isFullscreenState by mutableStateOf(false)
```

Updated directly where state changes (service bind, button clicks). Compose recomposes automatically.

### 2.7 State Persistence

Panel state preserved via `SharedPreferences` key `ui_drawer_open`:
- `isOpen` initialized from `prefs.getBoolean("ui_drawer_open", false)` in `setContent {}`
- On state change: `prefs.edit().putBoolean("ui_drawer_open", isOpen).apply()`

---

## 3. Technical Details

### 3.1 Animation Flow

```
CLOSED STATE:
  Handle button visible (fadeIn), panel content hidden
  User taps handle → onOpenChanged(true)
    → isOpen=true
    → Handle fades out, scrim fades in
    → Panel slides in from right (slideInHorizontally)

OPEN STATE:
  Panel visible, scrim visible, handle button hidden
  User taps scrim → onOpenChanged(false)
    → isOpen=false
    → Panel slides out to right
    → Scrim fades out, handle fades in
```

### 3.2 Key Design Decisions

| Decision | Reason |
|---|---|
| Fixed 80dp panel width | `LazyColumn` (SubcomposeLayout) incompatible with `IntrinsicSize` |
| `Row` + `CenterVertically` for close handle | Handle stays same 19×57dp size, not full-height |
| `AnimatedVisibility` over `ModalNavigationDrawer` | Not available in Compose 1.4.3 / Material3 1.1.2 |
| `remember` keys for scrim clickable | Avoids recomposition of `MutableInteractionSource` |
| `expand.png` kept in resources | Still referenced by `sysarrays.xml` for preference screen |

### 3.3 Issues Encountered & Fixed

| Issue | Fix |
|---|---|
| `IntrinsicSize` crash with `LazyColumn` ("Asking for intrinsic measurements of SubcomposeLayout layouts is not supported") | Replaced `width(IntrinsicSize.Min/Max)` with fixed `80.dp` + `wrapContentWidth()` |
| `AutoMirrored` icons unavailable (material-icons-extended not in dependencies) | Switched to `arrow_left`/`arrow_right` drawables with orientation-aware logic |
| `expand.png` deleted causing AAPT link error (`drawable/expand not found`) | Restored from git — still referenced by `sysarrays.xml` |
| Close handle was full-height in panel | Changed from `fillMaxHeight()` to `height(57.dp)` with `CenterVertically` |
| Panel moved across screen instead of staying flush to right | Removed `Alignment` switching, always use `Alignment.CenterEnd` |

---

## 4. Dependencies — Files Affected by Migration

### 4.1 Created
| File | Purpose |
|---|---|
| `borkozic/src/main/java/com/borkozic/ui/SidePanel.kt` | New Compose component (~275 lines) |

### 4.2 Modified
| File | Changes |
|---|---|
| `MapActivity.kt` | Removed OnPanelListener, updateMapButtons(), legacy imports; added MutableState + onSidePanelAction() |
| `act_main.xml` (portrait) | `Panel` → `ComposeView` |
| `act_main.xml` (landscape) | `Panel` → `ComposeView` |

### 4.3 Deleted (36 files)
| Category | Files |
|---|---|
| Java widgets | `Panel.java`, `Switcher.java` |
| Kotlin interpolators | 11 files: Back, Bounce, Circ, Cubic, EasingType, Elastic, Expo, Quad, Quart, Quint, Sine |
| Layout XML | `inc_mapbuttons.xml`, `inc_panelhandle.xml` |
| Drawable XML | 4 switcher background state-lists |
| PNG assets | 16 switcher .9.png files (4 states × 4 densities) |

### 4.4 Kept (referenced by settings UI)
| File | Reason |
|---|---|
| `expand.png` (hdpi, mdpi) | Referenced by `panel_action_images` in `sysarrays.xml` |
| `collapse.png` (hdpi, mdpi) | Used by sidebar expand button |
| `sysarrays.xml` | `panel_action_values`, `panel_action_images` — used by preference screen |
| `arrays.xml` | `panel_action_names` — localized labels for preference screen |
| `attr.xml` | Still contains `Panel`/`Switcher` styleables (can be cleaned later) |

---

## 5. Behavior

### 5.1 User Interaction

1. **Open panel:** Tap the small semi-transparent handle button (19×57dp, right edge, vertically centered)
2. **Close panel:** Tap anywhere on the scrim (semi-transparent overlay)
3. **Button actions:** Tap any button → `onSidePanelAction()` dispatches by `SidePanelAction` enum
4. **State persistence:** Panel open/closed state saved to `ui_drawer_open` preference

### 5.2 Visual Properties

| Element | Property | Value |
|---|---|---|
| Handle button | Size | 19×57dp |
| Handle button | Background | White, alpha 0.35 |
| Handle button | Arrow icon | 14dp, DarkGray |
| Panel content | Width | 80dp |
| Panel content | Background | White, alpha 0.37 |
| Scrim | Background | Black, alpha 0.30 |
| Animation | Duration | 300ms (tween) |

---

## 6. Settings & Configuration

Unchanged from legacy:
- **Preference screen:** `Settings → Display → Sliding panel actions`
- **Preference type:** `ImageMultiChoiceListPreference`
- **Key:** `panelactions`
- **Default:** `zoomin,zoomout,nextmap,prevmap,locate,tracking,info,follow`

---

## 7. History

| Version | Date | Description | Commit |
|---|---|---|---|
| **v1** | Original | Legacy `org.miscwidgets.widget.Panel` — custom Java `LinearLayout` with `TranslateAnimation` and `GestureDetector`. Handle buttons with static/dynamic icons in `ScrollView`. State persisted via `ui_drawer_open`. | in git history |
| **v2** | 2026-05-22 | **Compose migration complete.** `SidePanel.kt` replaces `Panel.java`. `AnimatedVisibility` + `slideInHorizontally` for slide animation. Reactive `MutableState` replaces `updateMapButtons()`. 36 legacy files deleted. Handle button 19×57dp with show/hide behavior. | `8a9d97a` |
| **v2.1** | 2026-05-22 | Fixed `IntrinsicSize` crash with `LazyColumn`. Fixed close handle size (full-height → 57dp). Adjusted transparency (handle 0.35, panel 0.37). Panel stays flush to right edge. | `16a25c9` |

---

## 8. Key Code Locations

| What | Where |
|---|---|
| Compose panel component | `borkozic/src/main/java/com/borkozic/ui/SidePanel.kt` |
| ComposeView declaration | `act_main.xml` (portrait & landscape) |
| SidePanel invocation | `MapActivity.kt` — `setContent {}` block |
| Action dispatch | `MapActivity.kt` — `onSidePanelAction()` |
| Reactive state vars | `MapActivity.kt` — `isFollowingState`, `isLocatingState`, `isTrackingState`, `isFullscreenState` |
| State persistence | `MapActivity.kt` — `ui_drawer_open` SharedPreferences |
| activeActions source | `MapActivity.kt` — `onSharedPreferenceChanged("panelactions")` |
| Preference screen | `pref_display.xml:35-40` |
| Action definitions | `sysarrays.xml:187-198` |

---

*Document maintained by Kоки. Last updated 2026-05-22.*
