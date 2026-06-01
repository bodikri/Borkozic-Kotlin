# Route Details — Compose Screen with Drag-and-Drop

> **Status:** 🟢 Compose (migrated 2026-06-01)
> **Source:** `borkozic/src/main/java/com/borkozic/route/RouteDetails.kt`, `RouteDetailsScreen.kt`

## Overview

Route Details shows all waypoints in a route with distance, course, and altitude for each segment. The screen supports **drag-and-drop reorder** via long-press on any waypoint.

Modes:
- **MANAGE** — normal view: tap shows View/Edit dialog, navigate button in toolbar
- **NAVIGATION** — during active navigation: tap shows View/Navigate, live ETE/ETA/distance updates

## Features

| Feature | Description |
|---|---|
| **Waypoint list** | LazyColumn with index, distance, course, and altitude per segment |
| **Drag-and-drop** | Long-press → drag waypoint vertically → swap on drop |
| **Consecutive duplicate prevention** | Cannot place the same waypoint directly before/after itself |
| **Tap → Edit/View** | Single tap opens dialog with course, distance, altitude + actions |
| **Navigation mode** | Live ETE, ETA, distance remaining from navigation service broadcasts |
| **Total distance** | Updated automatically after any reorder |

## Drag-and-Drop Implementation

### How it works

1. `detectDragGesturesAfterLongPress` on each item in LazyColumn
2. **During drag:** only visual floating offset via `Modifier.graphicsLayer { translationY }` — no list mutation
3. **On drop:** calculate rows moved (`totalOffsetY / itemHeightPx`), perform single swap in `route.waypoints`
4. Recalculate `route.distance` and refresh composable state

### Duplicate prevention

When dropping a waypoint, the code checks its neighbors:
```
Before drop: [A, B, C, D]
Dragging B to position after C:
  Check: neighbor before C != B AND neighbor after C != B
  If pass → insert B after C
  If fail → silently cancel (return to original position)
```

## Architecture

```
RouteDetails (ComponentActivity)
  └── setContent { BorkozicTheme { RouteDetailsScreen(route, mode, ...) } }
        ├── TopAppBar (route name, distance, navigate button)
        ├── LazyColumn (waypoint list with drag)
        │     └── RouteWaypointRow (index, name, distance, course, drag handle)
        └── AlertDialog (tap → View/Edit or View/Navigate)
```

## Related Route Features

### Non-consecutive waypoint re-add (2026-06-01)

When editing a route and tapping an existing route waypoint:
- **Last waypoint** → direct Edit (can't add same point consecutively)
- **Not last** → popup: Edit / Add to end
- Allows `WPT1 → WPT2 → WPT1` but blocks `WPT1 → WPT1`

Implemented in `MapActivity.routeWaypointTapped()` and `waypointActionItemClickListener`.

### Waypoint Edit from map (2026-06-01)

Outside editing mode, tapping a waypoint on the map shows a popup with **Edit** button. Opens `WaypointProperties` for full editing.

Implemented via `qaEditWaypoint` case in `waypointActionItemClickListener`.

## Related Files

- `RouteDetails.kt` — Activity host (ComponentActivity)
- `RouteDetailsScreen.kt` — Compose UI
- `Route.kt` — `moveWaypoint()` utility + waypoint list
- `MapActivity.kt` — route editing integration, waypoint popup handlers
