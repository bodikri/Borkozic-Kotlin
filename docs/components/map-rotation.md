# 🗺️ Map Rotation & Display Modes

> Component: `MapView.kt` | Last updated: 2026-05-31 (smooth bearing + center transition v2)

## Overview

Borkozic supports two map display modes for orientation, plus manual gesture control. The behavior is governed by three key state variables in `MapView.kt`:

| Variable | Type | Description |
|---|---|---|
| `bearing` | `Float` (0–360°, degrees) | Current map heading angle |
| `isTrackUp` | `Boolean` | `true` = Track Up, `false` = North Up (set in Settings → Display → Map Rotation) |
| `isFollowing` | `Boolean` | `true` = map auto-follows GPS location (toggled via double-tap) |

## Display Modes

### 🧭 North Up (isTrackUp = false)

- **Map canvas:** NOT rotated (`rotBearingDeg = 0`)
- **Plane cursor:** rotated by `bearing` — plane icon points to the heading direction
- **Compass needle:** not drawn (Track Up only)
- **Use case:** traditional map reading — North is always at the top of the screen

```
        N (top)
        │
  ──────┼──────  map (fixed)
        │
        ✈️↗      cursor rotates with heading
```

### 🛫 Track Up (isTrackUp = true)

- **Map canvas:** rotated by `canvas.rotate(+bearing)` around screen center
- **Plane cursor:** counter-rotated by `canvas.rotate(-bearing)` in a save/restore block → always points straight up
- **Compass needle:** drawn in the rotated canvas → rotates with the map, always indicates true North
- **Use case:** aviation-style — heading direction is always "up" on the screen

```
    ↘️N          compass shows where North is on the rotated map
     ────
    /    \      map (rotated)
   ✈️     \
  ↑        \   cursor always points up = heading direction
```

## Rendering Pipeline (doDraw)

```
1. canvas.drawRGB(white)                ← clear background
2. canvas.rotate(+bearing)              ← Track Up: rotate everything that follows
3. Borkozic.drawMap(bearing_radians)    ← tile rendering
4. Overlays (routes, tracks, etc.)      ← drawn in rotated canvas
5. Compass needle                       ← Track Up: in rotated canvas → points True North
6. canvas.save()                        ← isolate cursor rendering
     canvas.rotate(-bearing)            ← counter-rotate so cursor stays fixed
     plane cursor                       ← always points up in Track Up
   canvas.restore()
7. Crosshair (!isFollowing)             ← centered in rotated canvas
```

## Gesture Behavior

### Single Finger Drag
- Moves the map center (pan). Rotation compensation depends on display mode:
  - **Track Up:** canvas is already rotated by `+bearing` → apply **forward rotation**
    `(+bearing)` to convert screen delta to map delta.
  - **North Up:** canvas NOT rotated → apply **inverse rotation** `(-bearing)`.
  ```
  rotation = isTrackUp ? (+bearing) : (-bearing)
  mapDx = dx·cos(rot) + dy·sin(rot)
  mapDy = -dx·sin(rot) + dy·cos(rot)
  ```
- Also disables following (`setFollowingThroughContext(false)`).

### Two Finger Pinch-Zoom + Rotate
- **Zoom:** logarithmic scale factor based on finger distance ratio.
  - Formula: `scale = log₁₀(ratio) + 1` (zoom in) or `1/(log₁₀(1/ratio) + 1)` (zoom out).
  - Applied when fingers lift (`ACTION_POINTER_UP`).
- **Rotate:** absolute bearing, no drift accumulation.
  - `bearing = startBearing + (currentAngle - startAngle)`, normalized 0–360°.
  - Disabled when `isFollowing == true`.

### Double Tap
- Toggles `isFollowing` on/off.
- **Follow ON:** Initiates a smooth center transition animation (see below).
- **Follow OFF:** Resets `bearing = 0` to snap back to North.
- Shows toast: "Following enabled" / "Following disabled".

## GPS Integration (setLocation)

When `isFollowing == true`:
- GPS bearing triggers smooth bearing animation if change >5° (Layer 2 above).
- Map center auto-updates to current GPS position (unless smooth center animation
  is active — Layer 1 owns the center during its transition).
- `lookAheadB` is derived from bearing for rotation smoothing (Layer 3).

When `isFollowing == false`:
- GPS bearing is **NOT** used — manual bearing (pinch-rotate) is preserved.
- This prevents the "snap back to North" bug that occurred when GPS updates overwrote manual rotation.

## Smooth Animations (calculateLookAhead)

The animation tick runs at ~33fps during active transitions (10fps idle). Three
animation layers execute in priority order:

### Layer 1: Smooth Center Transition (Double-Tap Follow ON)

When `isFollowing` is toggled ON via double-tap, the map smoothly accelerates from
the current position toward the GPS location, then decelerates as it approaches.

**State variables:**

| Variable | Description |
|---|---|
| `smoothCenterActive` | `true` while center animation is running |
| `smoothCenterStartLat/Lon` | Map center position at animation start |
| `smoothCenterProgress` | 0.0 → 1.0 (interpolation factor) |
| `smoothCenterSpeed` | Progress velocity (accelerate/decelerate) |

**Two phases:**

1. **Phase 1 — Center movement:** `mapCenter` interpolates from start to GPS location
   using `smoothCenterProgress` with accelerate/decelerate pattern.
   - During this phase, `setLocation()` does **not** update `mapCenter` — the
     animation owns the position.

2. **Phase 2 — Handover:** When progress reaches 1.0, `setMapCenter(target, true)`
   is called, and `smoothCenterActive = false`. Normal following + lookAhead
   smoothing take over from here.

**Staggered bearing:** If the absolute bearing change exceeds `STAGGERED_BEAR_THRESHOLD`
(10°), the bearing converges proportionally to the center progress:

```
bearing = startBearing + deltaBearing × progress
```

This means the map starts orienting while it "flies" toward the target — avoiding
an abrupt rotation after reaching the destination. At progress 100%, the bearing
is at the heading target, and Phase 2 handles any final fine-tuning.

**Constants:**
- `SMOOTH_CENTER_MAX_SPEED = 0.08` progress/frame
- `SMOOTH_CENTER_INC = 0.004`
- `STAGGERED_BEAR_THRESHOLD = 10°`

### Layer 2: Smooth Bearing Animation

When `isFollowing` is active and the GPS bearing changes by more than
`SMOOTH_BEAR_THRESHOLD` (5°), a smooth bearing animation replaces the instant snap.
This uses the same accelerate/decelerate pattern as lookAhead shift smoothing.

**State variables:**

| Variable | Description |
|---|---|
| `smoothBearActive` | `true` while bearing animation is running |
| `smoothBearTarget` | Target GPS bearing (set from `setLocation()`) |
| `smoothBearCurrent` | Current animated bearing value |
| `smoothBearSpeed` | Angular velocity (°/frame) |

**Logic:**
- If `|Δbearing| ≤ 5°` → direct set (no animation, too small to notice)
- If `|Δbearing| > 5°` → animation accelerates to `SMOOTH_BEAR_MAX_SPEED`, then
  decelerates as it approaches the target

**Constants:**
- `SMOOTH_BEAR_THRESHOLD = 5°`
- `SMOOTH_BEAR_MAX_SPEED = 15°/frame`
- `SMOOTH_BEAR_INC = 0.3°/frame²`

### Layer 3: LookAhead Shift + Bearing Smoothing

Existing behavior (unchanged). During normal following:
- `lookAheadS` smoothly shifts the cursor ahead in the direction of travel.
- `smoothB` converges to `lookAheadB` (target bearing rounded to nearest 10°).
- Both use the same accelerate/decelerate pattern.

- `MAX_ROTATION_SPEED = 20°` per frame, `INC_ROTATION_SPEED = 0.5°`
- `MAX_SHIFT_SPEED = 20` pixels per frame, `INC_SHIFT_SPEED = 2`

## Key Methods

| Method | Location | Description |
|---|---|---|
| `doDraw()` | MapView.kt | Main render loop — applies rotation, draws map + overlays + cursor |
| `onTouchEvent()` | MapView.kt | Gesture state machine: DRAG, PINCH, double-tap |
| `setLocation(loc)` | MapView.kt | Updates bearing + position from GPS |
| `setTrackUp(isTrUp)` | MapView.kt | Called from MapActivity when Settings change |
| `setFollowing(follow)` | MapView.kt | Toggle auto-follow mode |
| `drawMap(bearing, ...)` | Borkozic.kt | Delegates to Map.drawMap() with bearing in radians |
| `drawMap(bearing, ...)` | Map.kt (lib) | Tile-based map rendering with bearing offset |
| `zoomMap(factor)` | MapActivity.kt | Applies zoom from pinch gesture |

## Configuration

Track Up / North Up is configured via:
- **Settings → Display → Map Rotation** → "North Up" or "Track Up"
- Stored as `pref_trackup` preference (`"0"` = North Up, `"1"` = Track Up)
- Propagated via `MapActivity` → `MapView.setTrackUp(value)`

## Related Files

- `MapView.kt` — gesture handling, rendering, rotation state, touch events
- `Borkozic.kt` — `drawMap()`, `scrollMap()`, `zoomBy()`
- `MapActivity.kt` — `zoomMap()`, `setFollowing()`, Settings integration
- `borkoziclib/.../map/Map.kt` — tile rendering with bearing offset

## Touch Coordinate Fix (2026-06-01)

All touch events in `onTouchEvent()` use `event.x`/`event.y` (view-local coordinates)
instead of `event.rawX`/`event.rawY` (absolute screen coordinates). This fixes a
~190px vertical offset between tap position and visual crosshair center, caused by
status bar + toolbar height. The crosshair is drawn at MapView center (`width/2`,
`height/2`), so touch coordinates must be in the same coordinate system for
accurate overlay hit testing.
