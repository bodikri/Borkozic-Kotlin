# Компас сензор — документация

## Обзор

Borkozic използва вградения компас сензор (магнитометър + акселерометър) за автоматично ориентиране на картата според посоката на устройството, когато то е неподвижно.

## Архитектура

### Approach

Използва се **native Android SensorManager** с `TYPE_ACCELEROMETER` + `TYPE_MAGNETIC_FIELD` и `getRotationMatrix()` / `getOrientation()`. Не се използва Google Play Services Fused Orientation API, за да се запази GPL съвместимостта и да се избегнат външни зависимости.

### Компоненти

| Компонент | Файл | Описание |
|---|---|---|
| `CompassSensorHelper` | `borkozic/src/main/java/com/borkozic/location/CompassSensorHelper.kt` | Sensor listener, low-pass filter, display rotation remap |
| `CompassMode` enum | `MapView.kt` | GPS / COMPASS / NORTH_LOCK режим |
| `setBearingFromCompass()` | `MapView.kt` | Обновява bearing от компаса с плавна анимация |
| `lockToNorth()` | `MapView.kt` | Заключва bearing на 0° |
| `clearNorthLock()` | `MapView.kt` | Изчиства North Lock |
| Компас интеграция | `MapActivity.kt` | onCreate, onResume, onPause, setFollowing, onLocationChanged, onSidePanelAction |
| Компас иконка | `inc_mapinfo.xml` | ImageView в долния инфо бар |
| Compass orientation setting | `pref_display.xml` | HelpfulCheckBoxPreference |
| Локализация | `strings.xml` + 18 езика | pref_compass, pref_compass_title, pref_compass_summary |

## Режими на компаса (CompassMode)

### GPS (default)
- Bearing се обновява от `Location.bearing` (GPS)
- Активен когато устройството се движи (speed ≥ 1 m/s)
- Това е стандартното поведение преди добавяне на компаса

### COMPASS
- Bearing се обновява от магнитометъра през `CompassSensorHelper`
- Активен когато устройството е неподвижно (speed < 1 m/s) и компасът е включен в Settings
- При първо превключване GPS→COMPASS с скок > 5°, се стартира плавна анимация (Layer 2)
- След анимацията, компас обновява bearing директно (low-pass филтърът изглажда)

### NORTH_LOCK
- Bearing е заключен на 0° (North Up)
- Активен при натискане на North Up бутона в страничния панел
- Компас и GPS bearing се игнорират
- Изчиства се при:
  - Включване на following (`setFollowing(true)`)
  - Ръчно завъртане на картата с двупръстен жест (pinch rotation)

## Логика на превключване

```
┌─────────────────────────────────────────────────┐
│                 Following ON                     │
├─────────────────────────────────────────────────┤
│                                                  │
│  speed < 1 m/s?  ────── YES ──────►  COMPASS    │
│                  │                              │
│                  NO                             │
│                  │                              │
│                  ▼                              │
│              GPS bearing                        │
│                                                  │
│  North Up бутон?  ──── YES ────►  NORTH_LOCK   │
│                    │                            │
│                    NO                           │
│                    ▼                            │
│              Остава в текущ режим               │
└─────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────┐
│                Following OFF                     │
├─────────────────────────────────────────────────┤
│  bearing = 0°, compassMode = GPS                │
│  компасът се спира                               │
└─────────────────────────────────────────────────┘
```

## Плавна анимация (Smooth Bearing)

MapView използва трислойна анимационна система:

1. **Layer 1 (smoothCenterActive)** — плавен преход на центъра + staggered bearing
2. **Layer 2 (smoothBearActive)** — плавно завъртане при скок > 5° (SMOOTH_BEAR_THRESHOLD)
3. **Layer 3 (lookAheadB/smoothB)** — final bearing smoothing

При превключване GPS→COMPASS:
- Ако |compassBearing - bearing| > 5° → стартира Layer 2 анимация
- Анимацията използваaccel/decel модел (smoothBearSpeed)
- По време на анимацията, компасът обновява `smoothBearTarget` (следва движещата се цел)
- След анимирането, компасът обновява bearing директно

## UI Индикатор

Иконката е в долния ляв ъгъл на инфо бара (`inc_mapinfo.xml`), преди името на картата.

| Състояние | Иконка | Условие |
|---|---|---|
| Скрита (GONE) | — | Няма магнитометър или компасът е изключен в Settings |
| Сива | `compass_needle_north_blue` + GRAY ColorFilter | Компасът е наличен, но не е в COMPASS режим |
| Цветна | `compass_needle_north_blue` (без филтър) | COMPASS режим е активен |

## Settings

- **Settings → Display → "Compass orientation"** (CheckBoxPreference)
- Default: изключен (`false`)
- Ключ: `pref_compass`
- Когато се включи/изключи по време на following:
  - ON → стартира компаса ако following ON
  - OFF → спира компаса, връща compassMode в GPS

## Magnetic Declination

`CompassSensorHelper.declination` се задава от `Borkozic.declination` (изчислява се от `GeomagneticField` на база текущата локация). Деклинацията се добавя към raw azimuth за преобразуване от магнитен север към географски север.

## Sensor Lifecycle

| Събитие | Компас статус |
|---|---|
| `onCreate()` | Инициализация, проверка за сензор |
| `onResume()` | Чете pref_compass, стартира ако following ON |
| `onPause()` | Спира компаса (батерия) |
| `setFollowing(true)` | clearNorthLock + start компаса |
| `setFollowing(false)` | Stop компаса, compassMode = GPS |
| `onLocationChanged` (speed < 1) | Превключва в COMPASS режим |
| `onLocationChanged` (speed ≥ 1) | Превключва в GPS режим |
| North Up бутон | Stop компаса, lockToNorth |
| Pinch rotation | clearNorthLock |