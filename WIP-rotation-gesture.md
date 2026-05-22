# 🔧 WIP: Map Rotation & Gesture Rework
> Started: 2026-05-22 21:00 | Status: ✅ BUILD SUCCESSFUL, чака емулаторен тест

## Goal
Replace legacy `MultiTouchController.java` (2010, reflection-based) with native `MotionEvent` API in `MapView.kt`.
Implement proper single-finger drag (rotation-compensated), 2-finger pinch+rotate, and absolute bearing tracking.

## Plan

| Фаза | Статус | Описание |
|---|---|---|
| 1. Премахване MultiTouchController | ✅ done | Изтриване на .java файла (758 реда) и всичките му интерфейси от MapView |
| 2. Нов gesture handler | ✅ done | State machine: GESTURE_NOTHING/DRAG/PINCH, чист MotionEvent API |
| 3. Drag с rotation компенсация | ✅ done | Трансформация screen→map: `cos(-bearing)*dx + sin(-bearing)*dy` |
| 4. Pinch zoom | ✅ done | Логаритмичен scale factor, запазва оригиналната формула |
| 5. Абсолютно завъртане | ✅ done | `bearing = startBearing + (currentAngle - startAngle)`, нормализация 0-360 |
| 6. Защита на isFollowing | ✅ done | Ръчно завъртане игнорирано при follow; `ACTION_POINTER_UP` финализира zoom |
| 7. Емулаторен тест | ⏳ pending | Проверка в емулатор: drag, pinch, rotate, double-tap |

## Files changed
- `MapView.kt` — пренаписан `onTouchEvent()`, нов gesture state machine, bearing в градуси
- `MultiTouchController.java` — **ИЗТРИТ**

## Key decisions
- **Bearing в градуси** — `canvas.rotate()` директно, `drawMap()` получава `Math.toRadians(bearing)`
- **2 пръста = pinch zoom + rotate** (стандарт)
- **Абсолютен bearing** — без натрупване, винаги относителен към жеста
- **`event.rawX/rawY`** вместо `event.x/y` — по-точна drag детекция
