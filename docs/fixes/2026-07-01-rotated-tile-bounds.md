---
title: FIX — Rotated viewport tile bounds
date: 2026-07-01
status: implemented ✅
---

# FIX: Rotated viewport tile bounds

## Проблем

Когато потребителят завърти картата (bearing ≠ 0°), `drawMap()` изчислява
видимите тайлове спрямо **незавъртян** viewport. При ротация ъглите на екрана
се проектират върху различни тайлове от тези които axis-aligned bounding box
покрива. Резултат: празни участъци по краищата, най-вече при 90°/270°.

Допълнителни проблеми при наличие на **lookAhead** (изместване на картата
напред при навигация): rotation центърът е отместен спрямо canvas центъра,
което правеше inverse ротацията неточна и създаваше дупки при 180°.

## Имплементирани фиксове (2026-07-01, 4 комита)

### Commit 1: `2dbbcd8` — ViewportTileBounds corner inverse rotation
- Създаден `ViewportTileBounds.kt` — калкулира tile bounds от 4-те ъгъла
  на екрана с inverse (CW) ротация
- Интегриран в `Map.kt drawMap()` — заменя стария axis-aligned bounds
- `bearingRad = 0f` → идентичен резултат с предишното поведение ✅
- Commit: `2dbbcd8 fix: ViewportTileBounds corner inverse rotation (CCW→CW) + debug logging`

### Commit 2: `b2c5fd0` — OnlineMap двойна конверсия на bearing
- **Бъг:** `MapView.doDraw` подава bearing **вече в радиани** (`Math.toRadians(bearing)`)
- `OnlineMap.drawMap` правеше **втора конверсия** `Math.toRadians(bearing)` → третираше радиани като градуси
- 157° → 2.74 rad → Math.toRadians(2.74) = 0.048 rad ≈ 2.7° → практически нулева ротация
- **Fix:** `cos(bearing)` / `sin(bearing)` директно, без `Math.toRadians()`
- Commit: `b2c5fd0 fix: OnlineMap double radian conversion — bearing already in radians from MapView`

### Commit 3: `9cce03d` — OnlineMap lookAhead в rotation центъра
- **Бъг:** Ъглите на екрана се ротираха около `map_xy` (canvas център),
  а не около **rotation центъра** (map_xy + lookAhead)
- При 180° с lookAhead, tile range-ът се изместваше грешно и оставяше
  празни места по краищата
- **Fix:** Ъглите се превръщат спрямо rotation център → CW inverse ротация
  → добавя се глобалната позиция на rotation центъра
- Commit: `9cce03d fix: OnlineMap corner rotation — use rotation center (with lookAhead) + CW inverse rotation`

### Commit 4: `45afb1f` — ViewportTileBounds lookAhead за OziExplorer карти
- Същият lookAhead fix като #3, но за `ViewportTileBounds` (ozf карти)
- Добавен параметър `lookAheadX`, `lookAheadY` на `ViewportTileBounds`
- `Map.kt drawMap()` подава `lookAhead[0], lookAhead[1]`
- Commit: `45afb1f fix: ViewportTileBounds — account for lookAhead in corner rotation`

### Commit 5: `96a4892` — Български коментари
- Подробни коментари на български в трите засегнати файла
- Commit: `96a4892 docs: add Bulgarian comments explaining tile rotation fixes`

## Засегнати файлове

| Файл | Промяна |
|------|---------|
| `borkoziclib/.../map/viewport/ViewportTileBounds.kt` | ⭐ Ново + lookAhead fix + BG коментари |
| `borkoziclib/.../map/Map.kt` | drawMap ползва ViewportTileBounds + lookAhead |
| `borkoziclib/.../map/online/OnlineMap.kt` | Двойна конверсия fix + lookAhead rotation + BG коментари |

## Ключови изводи (lessons learned)

1. **Винаги проверявай какви единици идват от caller-а.**
   `MapView.doDraw` конвертира bearing в радиани → OnlineMap НЕ трябва
   да прави втора конверсия. Това е класически "double conversion bug".

2. **Rotation център ≠ canvas център когато има lookAhead.**
   Canvas-ът се ротира около `(lookAheadX + w/2, lookAheadY + h/2)`,
   не около `(w/2, h/2)`. Inverse ротацията на ъглите трябва да е
   спрямо rotation центъра, иначе при 180° диапазонът се измества
   с 2×lookAhead и оставя дупки.

3. **Canvas-ът вече е ротиран → не ротирай позициите на тайловете.**
   `txb`/`tyb` се изчисляват без cos/sin защото canvas-ът сам се грижи
   за визуалната ротация. Старият код прилагаше cos/sin → двойна ротация
   и тайловете се появяваха на грешни места.

## Оставащи задачи (за следваща сесия)

- Потребителят спомена "има още за доизглаждане" — вероятно edge cases
  при ротация с OnlineMap
- Евентуално премахване на debug логовете (VTB таг) след потвърждение
  че всичко работи коректно
