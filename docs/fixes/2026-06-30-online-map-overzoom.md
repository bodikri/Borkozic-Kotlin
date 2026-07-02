# OnlineMap Over-Zoom (надхвърляне на maxZoom)

## Дата: 2026-06-30

## Проблем
При pinch zoom над 100% върху онлайн карта (OnlineMap), визуалният zoom се връщаше на 100% след вдигане на пръстите.

### Причина
- `OnlineMap` има `defZoom` и `maxZoom` от tile provider-а (напр. OSM: minZoom=0, maxZoom=18, defZoom=18)
- При pinch zoom, `setZoom(z)` изчислява `zDiff = ln(z)/ln(2)` и задава `srcZoom = defZoom + zDiff`
- Ако `srcZoom > maxZoom`, се клампва към `maxZoom` — тайловете остават на същото ниво
- `zoom` факторът се запазваше (напр. 2.08), но `drawMap()` рисуваше тайловете **1:1 без мащабиране**
- Резултат: дисплеят показваше 208%, но визуално картата оставаше на 100%

### Допълнителен симптом
При повторни опити за pinch zoom, `zoom` продължаваше да расте (100% → 160% → 220%), но визуално нищо не се променяше. Zoom барът долу вдясно показваше натрупващи се стойности без реален ефект.

## Решение

### 1. Canvas Scale в `drawMap()` (Вариант А)
Когато `zoom > 2^(srcZoom - defZoom)` (т.е. надхвърляме native zoom нивото), прилагаме допълнителен canvas scale:

```kotlin
val tileScale = zoom / 2.0.pow((srcZoom - defZoom).toDouble())
val useCanvasScale = abs(tileScale - 1.0) > 0.001

if (useCanvasScale) {
    c.save()
    c.scale(tileScale.toFloat(), tileScale.toFloat(), width / 2f, height / 2f)
}
// ... рисуване на тайловете 1:1 ...
if (useCanvasScale) {
    c.restore()
}
```

Това мащабира **целият canvas слой** заедно, без шевове между тайловете (за разлика от индивидуално разтягане на всеки тайл с `RectF`).

### 2. 200% Кап в `setZoom()` (Вариант Б)
Ограничава `zoom` до максимум 200% над native zoom нивото:

```kotlin
val nativeZoom = 2.0.pow((srcZoom - defZoom).toDouble())
val maxOverZoom = nativeZoom * 2.0
zoom = minOf(z, maxOverZoom)
```

Това предотвратява прекомерно мащабиране което:
- Натоварва системата (големи bitmap scaling операции)
- Влошава качеството на картината (пикселизация)
- Няма практическа стойност (над 200% разтягане на тайлове е нечетливо)

## Защо не другите подходи

### ❌ Индивидуално scaling на тайлове с `RectF`
Опитано първо — всеки тайл се разтягаше отделно с `c.drawBitmap(tile, src, dst, null)`. Резултатът: **видими шевове** между тайловете, защото всеки 256×256 тайл се разтягаше до ~534×534 независимо от съседите си.

### ❌ Private `doSetZoom` в базовия `Map` клас
Опитано първо — преместих логиката в private метод за да bypass-на subclass override. Но `OnlineMap` има `ozf = null` (ползва `tileController`), така че `ozf!!.setZoom()` → **NPE → краш**.

## Засегнати файлове
- `borkoziclib/src/main/java/com/borkozic/map/online/OnlineMap.kt` — `setZoom()` + `drawMap()`

## Тест
- Pinch zoom над 100% → картата се мащабира плавно ✅
- Maximum zoom = 200% над native → не може да се зумва повече ✅
- Няма шевове между тайлове ✅
- Няма крашове ✅