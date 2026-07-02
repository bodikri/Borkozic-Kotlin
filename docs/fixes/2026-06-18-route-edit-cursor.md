# 2026-06-18 — Route Edit: PointList Cursor + Route Recalc

## Проблеми

1. **ViewTreeLifecycleOwner crash** — `IllegalStateException: ViewTreeLifecycleOwner not found` при inflate на Compose UI в `AlertDialog` в MapActivity
2. **waypointTapped в route edit mode** — не показваше "Add to Route" менюто при маркиране на точка от картата
3. **PointList cursor** — нямаше визуална индикация къде е курсорът; "No cursor" бутон беше излишен
4. **Route Details не преизчисляваше** — при редакция/изтриване на точка не се обновяваше `route.distance`

## Решения

### 1. ViewTreeLifecycleOwner crash

**Файл:** `MapActivity.kt` — `orderpoints` handler

Заменен Compose-based `RoutePointListDialog` с native `AlertDialog`:
```kotlin
AlertDialog.Builder(this)
    .setTitle("Cursor Position")
    .setItems(names) { _, which ->
        application!!.routeEditingCursor = which
    }
    .setNegativeButton("Close") { _, _ -> }
    .show()
```

### 2. waypointTapped — Add to Route меню с cursor support

**Файл:** `MapActivity.kt` — `waypointTapped()` + `waypointActionItemClickListener`

- `waypointTapped()` в route edit mode показва `wptQuickActionAddToRoute`
- `qaAddWaypointToRoute` в action listener:
  - Ако `routeEditingCursor != null` → добавя **след** курсора, обновява курсора
  - Ако `routeEditingCursor == null` → добавя в края (старо поведение)

### 3. PointList cursor dialog

**Файл:** `MapActivity.kt` — `orderpoints` handler

- По подразбиране курсорът е последната точка: `cursor = currentCursor ?: (route.length() - 1)`
- Курсорът се маркира: `"${wpt.name} ←CURSOR"`
- "No cursor" → "Close" (просто dismiss, курсорът остава)
- Back бутон затваря диалога

### 4. Route Details преизчисляване

**Файлове:** `RouteDetails.kt` + `RouteDetailsScreen.kt`

#### RouteDetails.kt:
- `onEditWaypoint` → `startActivityForResult(..., RESULT_SAVE_WAYPOINT)`
- Добавен `onActivityResult`:
  - `RESULT_SAVE_WAYPOINT` — преизчислява `route.distance` + `refreshKey++`
  - `RESULT_EDIT_ROUTE` — също преизчислява
- `onRemoveWaypoint` — преизчислява `route.distance` (или нулира при ≤1 точка) + `refreshKey++`

#### RouteDetailsScreen.kt:
- Нов параметър `refreshKey: Int = 0`
- `waypointList` се memorializa с `remember(route, refreshKey)`
- `LaunchedEffect(route.waypoints.size, refreshKey)` синхронизира списъка

## Комити

| Commit | Описание |
|--------|----------|
| `0403916` | FIX: orderpoints dialog uses simple AlertDialog with setItems/NeutralButton |
| `f42b099` | FIX: waypointTapped in route edit mode shows Add to Route menu with cursor support |
| `4444073` | ENH: PointList cursor highlight; RouteDetails recalc on waypoint edit/property change |
| `d745929` | FIX: recalculate route distance on waypoint remove in RouteDetails |

## Архитектура

### Cursor концепция
- `routeEditingCursor` — `Int?` в `Borkozic` (Application)
- `null` = "последна точка" (default)
- Se използва при добавяне на точка от картата в route edit mode
- Se настройва чрез PointList диалога (orderpoints)

### refreshKey pattern
Прост Compose recomposition trigger:
```kotlin
// В Activity:
private var refreshKey by mutableStateOf(0)
// При промяна: refreshKey++

// В Composable:
fun RouteDetailsScreen(..., refreshKey: Int = 0, ...) {
    var waypointList by remember(route, refreshKey) { mutableStateOf(waypoints.toMutableList()) }
    LaunchedEffect(route.waypoints.size, refreshKey) {
        waypointList = route.waypoints.toMutableList()
    }
}
```