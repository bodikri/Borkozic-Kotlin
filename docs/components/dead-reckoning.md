# Dead Reckoning — Автономно изчисление на позиция

> **Status:** 🚀 v3.1 — 6-state Kalman Filter + continuous sensors + adaptive bias noise
> **Last updated:** 2026-07-02 (commit `68d0894`)
> **Files:** `borkozic/src/main/java/com/borkozic/location/`

---

## 1. Overview

Dead Reckoning (DR) е система за автономно изчисляване на местоположение чрез бордови сензори
(акселерометър, жироскоп, магнитометър, барометър), когато GPS сигналът е загубен
или временно недостъпен (тунели, гъсти гори, градски каньони).

### Цел
- Поддържане на позиция при GPS загуба до 3 минути
- Точност < 15m за първите 30 секунди без GPS
- Точност < 100m за 3 минути без GPS
- Плавен преход между GPS → DR → GPS

---

## 2. История на версиите

| Версия | Дата | Описание | Резултат |
|--------|------|----------|----------|
| v1 | 2026-06-29 | 4-state Kalman `[posN,posE,velN,velE]`, двойна интеграция | ❌ 3-4 km drift за 3 мин |
| v2 | 2026-07-02 07:09 | Trajectory-based, единична интеграция | ⏳ Заменен преди тест |
| **v3** | **2026-07-02 15:58** | **6-state Kalman + continuous sensors + heading filter** | 🔄 Чака тест |

---

## 3. v3: 6-State Kalman Filter (текущ)

### 3.1 Архитектура

| Клас | Редове | Роля |
|------|--------|------|
| `DeadReckoningKalman.kt` | ~467 | 6-state Kalman филтър с gravity removal и Earth-frame transform |
| `LocationService.kt` | ~1300 | Сензорна регистрация, state machine, Kalman predict/update/broadcast |
| `DRLogger.kt` | ~120 | Файлово логване (Android/data/com.borkozic/files/dr_logs/) |
| `GpsRingBuffer.kt` | ~80 | Кешира последни 3 GPS точки за инициализация |

### 3.2 State Vector (6D)

```
x = [posN,    // Позиция North (метри от референция)
     posE,    // Позиция East  (метри от референция)
     velN,    // Скорост North (m/s)
     velE,    // Скорост East  (m/s)
     biasN,   // Акселерометър bias North (m/s²)
     biasE]   // Акселерометър bias East  (m/s²)
```

### 3.3 Prediction (Accelerometer, ~50Hz)

Всеки accelerometer event → Kalman predict. Математическият модел е **физически коректен**:

```
posN += velN * dt + 0.5 * (accelN - biasN) * dt²
posE += velE * dt + 0.5 * (accelE - biasE) * dt²
velN += (accelN - biasN) * dt
velE += (accelE - biasE) * dt
biasN += 0   (оценен чрез process noise)
biasE += 0
```

**Ключово свойство:** При нулево ускорение → скоростта остава **константна** (1st law на Нютон).
Няма изкуствен speed decay — ако самолет/кола не ускорява, тя поддържа скоростта си.

### 3.4 GPS Update (Position + Velocity, ~1Hz)

Всеки GPS fix → Kalman correction:

```
z = [gpsPosN, gpsPosE, gpsVelN, gpsVelE]

// gpsVelN/E се изчисляват от speed × [cos(bearing), sin(bearing)]
// gpsPosN/E са relative спрямо ref lat/lon
```

Когато GPS работи → Kalman учи **едновременно**:
- Позиция (от GPS координати)
- Скорост (от GPS speed + bearing projection)
- Bias (чрез innovation — разликата предсказана ↔ измерена скорост)

При GPS загуба → само prediction (без correction) → bias вече е предварително оценен.

### 3.5 Process Noise (Q)

```kotlin
// Adaptive process noise:
ACCEL_NOISE_DENSITY_CRUISE   = 0.02   // m/s²/√Hz — плавно движение
ACCEL_NOISE_DENSITY_MANEUVER = 0.3    // m/s²/√Hz — маневри
HIGH_ACCEL_THRESHOLD         = 0.5    // m/s² (~0.05g) — праг cruise↔maneuver

// Adaptive bias noise (v3.1):
BIAS_NOISE_DENSITY_CRUISE   = 0.001  // m/s³/√Hz — плавно движение
BIAS_NOISE_DENSITY_MANEUVER = 0.05   // m/s³/√Hz — 50× при маневри
```

Q се изчислява автоматично за всеки predict от `dt` × noise_density²:
- По-малък dt → по-малко Q → по-стабилна оценка
- По-голям dt → по-голямо Q → повече свобода за корекция

При ускорение ≥ `HIGH_ACCEL_THRESHOLD` (0.5 m/s²):
- Position noise: 0.02 → 0.3 (15×)
- Bias noise: 0.001 → 0.05 (50×)
- Throttle: макс веднъж на 2 секунди (да не overfit-ва)

⚠️ **Bias се коригира САМО при GPS update.** При pure DR (без GPS),
bias стойностите остават на последната оценка. Адаптивният Qb подготвя
ковариацията за бърза корекция при следващ GPS fix.

### 3.6 Measurement Noise (R)

```kotlin
R = diag(σ_pos², σ_pos², σ_vel², σ_vel²)
σ_pos = max(gpsAccuracy, 3.0)        // m — минимум 3m да не overfit-ва
σ_vel = max(gpsAccuracy * 0.5, 1.0)  // m/s
```

### 3.7 Matrix Inversion

4×4 innovation covariance `(H*P*Hᵀ + R)` се обръща с **Gauss-Jordan elimination**
(без външна библиотека). Само 4×4, което е numerical stable и O(64) операции.

### 3.8 Tunable Parameters

| Параметър | Стойност | Описание |
|-----------|----------|----------|
| `ACCEL_NOISE_DENSITY_CRUISE` | 0.02 m/s²/√Hz | Process noise при плавно движение |
| `ACCEL_NOISE_DENSITY_MANEUVER` | 0.3 m/s²/√Hz | Process noise при маневри |
| `HIGH_ACCEL_THRESHOLD` | 0.5 m/s² | Праг cruise↔maneuver (~0.05g) |
| `HIGH_ACCEL_Q_INTERVAL_MS` | 2,000 | Throttle интервал за Q injection |
| `BIAS_NOISE_DENSITY_CRUISE` | 0.001 m/s³/√Hz | Bias random walk (cruise) |
| `BIAS_NOISE_DENSITY_MANEUVER` | 0.05 m/s³/√Hz | Bias random walk (maneuver) |
| `MAX_DT` | 1.0s | Safety clamp за predict стъпка |
| `P[0][0], P[1][1]` | 0.1 | Начална позиционна несигурност |
| `P[2][2], P[3][3]` | 0.5 | Начална скоростна несигурност |
| `P[4][4], P[5][5]` | 0.3 | Начална bias несигурност |
| `DR_MAX_DURATION_MS` | 180,000 | Максимална продължителност (3 мин) |
| `DR_BROADCAST_INTERVAL_MS` | 200 | Broadcast на всеки 5Hz |

### 3.9 Earth-Frame Transform

Акселерометър → Земна координатна система чрез постоянна rotation matrix:

```kotlin
// При всяко rotation vector събитие:
SensorManager.getRotationMatrixFromVector(drRotMatrix3x3, event.values)
// 3×3 → 4×4 matrix за android.opengl.Matrix.multiplyMV

// При всяко accel събитие:
deviceAccel = [linearAccelX, linearAccelY, linearAccelZ, 0]
Matrix.multiplyMV(earthAccel, 0, drRotMatrix4x4, 0, deviceAccel, 0)
// earthAccel[0] = East, earthAccel[1] = North
```

### 3.10 Gravity Removal

Ако `TYPE_LINEAR_ACCELERATION` не е наличен, raw `TYPE_ACCELEROMETER` се обработва:

```kotlin
β = 0.9  // low-pass filter constant
gravityX = β * gravityX + (1-β) * rawX
gravityY = β * gravityY + (1-β) * rawY
gravityZ = β * gravityZ + (1-β) * rawZ

linearAccel = rawAccel - gravity  // high-pass = gravity removed
```

### 3.11 Heading Sanity Filter

Предотвратява скокове > 30°:

```
Плъзгащ прозорец от 5 heading стойности
1. Медиана на валидните стойности
2. Ако нов heading е в рамките на 30° от медианата → приема се
3. Иначе → проверка дали 3+ от последните 5 са в новата посока
   - Да → реален завой → приема се
   - Не → отхвърля се, връща се drFilteredHeading или медианата
```

## 4. Continuous Sensor Pipeline

**Преди (v1/v2):** Сензорите се регистрират само при GPS загуба → 5s cold start delay.
**Сега (v3):** Сензорите регистрирани **ПОСТОЯННО** в `onCreate()`:

```
LocationService.onCreate()
  └── registerDrSensorsContinuously()   // SENSOR_DELAY_GAME, ~50Hz
        ├── TYPE_GAME_ROTATION_VECTOR → TYPE_ROTATION_VECTOR (fallback)
        ├── TYPE_LINEAR_ACCELERATION → TYPE_ACCELEROMETER (fallback)
        ├── TYPE_GYROSCOPE
        ├── TYPE_MAGNETIC_FIELD
        └── TYPE_PRESSURE

При GPS ON:
  - Kalman.predict() работи НЕПРЕКЪСНАТО → учи гравитация + bias
  - Kalman.updateWithGPS() на всеки GPS fix → коригира state

При GPS загуба:
  - DR state → ACTIVE (без delay!)
  - Kalman продължава predict (без update)
  - DR dispatch → 5Hz broadcast през normal location pipeline
```

### Предимства на continuous режима:

1. **Няма 5s activation delay** — Kalman вече е "горещ" когато GPS падне
2. **Bias предварително оценен** — докато GPS работи, bias states учат offset-а
3. **Gravity вече филтрирана** — low-pass филтърът е конвергирал
4. **Rotation matrix стабилна** — постоянно се обновява от rotation vector сензора

## 5. 6-Component Snapshot (при GPS загуба)

При `startDeadReckoning()` се записва:

```
DR_START: OK (Kalman) — lat=X, lon=Y, speed=S, bearing=B, acc=A
KALMAN_SNAPSHOT: velN=..., velE=..., biasN=..., biasE=..., covPos=..., covVel=...
SENSOR_SNAPSHOT: accel=[x,y,z], gyro=[x,y,z], mag=[x,y,z], rot=[x,y,z], earthAccel=[N,E]
```

Това дава **пълна baseline** за post-hoc анализ на дрифта.

## 6. DR Lifecycle State Machine

| State | Условие | Действие |
|-------|---------|----------|
| `DR_IDLE` | GPS активен | Kalman predict + update, без broadcast |
| `DR_ACTIVE` | GPS загубен | Kalman predict only, 5Hz broadcast |
| `DR_STOPPED` | 3 мин изтекли ИЛИ GPS възстановен | Спира broadcast, Kalman продължава |

### Entry conditions за DR_ACTIVE:
- `ENABLE_LOCATIONS` ИЛИ GPS provider enabled **AND** GPS вече не дава fix (onLocationChanged не се вика)
- Алтернативно: `DISABLE_LOCATIONS` intent → forced DR start
- Ring buffer НЕ е празен (има последна GPS точка за инициализация)

### Kalman lazy init:
При първия GPS fix Kalman се инициализира **автоматично** от `onLocationChanged`.
Това значи че още **преди DR да е активен**, Kalman-ът вече работи и учи bias.

## 7. Debug Output Format

Всеки 10-ти dispatch (≈ на 2 сек):
```
DR_DISPATCH: lat=X, lon=Y, speed=S, bearing=B, distFromGPS=Dm
DR_KALMAN: heading=H°, speed=Sm/s, posN=Pn, posE=Pe, velN=Vn, velE=Ve,
           biasN=Bn, biasE=Be, covPos=Cp, covVel=Cv, gpsAge=As(#N), Q=Qa, Qb=Qb, pred=N#
TRACK_DR: point written lat=X lon=Y speed=S
```

Toast съобщения за UX обратна връзка:
```
"ACTIVE (K)! speed=1.8m/s"    — DR стартиран
"STOPPED (45s)"                — DR спрян
"FAILED: ring buffer empty"    — Не може да стартира
```

## 8. Лог файлове

- **Път:** `Android/data/com.borkozic/files/dr_logs/dr_YYYYMMDD_HHMMSS.txt`
- **Достъп:** През произволен file manager (без root/adb)
- **Формат:** Timestamp + pipe-delimited стойности
- **Размер:** ≤ 10 log файла (старите се трият)

## 9. Очаквани подобрения спрямо v1 (4-state Kalman)

| Метрика | v1 (4-state) | v3 (6-state) — очаквано |
|---------|-------------|--------------------------|
| Heading грешка (3 мин) | -30° до -64° | < 15° (heading filter) |
| Позиционен drift (3 мин) | 3–4 km | TBD (чака тест) |
| Скоростен drift | ±10 m/s | < 0.5 m/s (няма decay) |
| Cold start delay | 5s | 0s (continuous) |
| Bias estimation | Saturation при 0.4-0.68 | Gradual (Q-based, без saturation) |
| Battery | Само при DR | Continuous (50Hz → ~1-2% extra) |

## 10. Файлова структура

```
borkozic/src/main/java/com/borkozic/location/
├── DeadReckoningKalman.kt       # 6-state Kalman filter engine (467 lines)
├── LocationService.kt           # Sensor pipeline + state machine (~1300 lines)
├── DRLogger.kt                  # File logging to external storage (~120 lines)
├── GpsRingBuffer.kt             # Last 3 GPS positions cache (~80 lines)
├── DeadReckoningCalculator.kt   # [DEPRECATED] v2 trajectory-based
└── DeadReckoningService.kt      # [UNUSED] v1 separate service
```

### Integration points
- `borkozic/src/main/java/com/borkozic/MapActivity.kt` — DR бутон/индикатор, `DISABLE_LOCATIONS` intent
- `borkozic/src/main/java/com/borkozic/MapView.kt` — Показване на DR позиция на картата

### Design docs & analysis
- `docs/components/dead-reckoning.md` — Този документ
- `docs/dr-kalman-design.md` — Първоначален Kalman дизайн (v1)
- `docs/fixes/2026-07-02-dr-kalman-v3.md` — Changelog за v3
- `Logs/DR_ANALYSIS_REPORT.md` — Подробен анализ на v1 тестове

## 11. Тестови процедури

### 11.1 Статичен тест
1. Стартиране на приложението с GPS ON
2. Изчакване 10s за Kalman initialization
3. `DISABLE_LOCATIONS` → DR стартира
4. Наблюдение на toast и позиция на картата (не трябва да мърда)

### 11.2 Динамичен тест (пеша/кола)
1. GPS ON → започване на движение (≥ 1.5 m/s)
2. След 10-15s движение → `DISABLE_LOCATIONS`
3. Наблюдение: позицията продължава да се движи в същата посока
4. Проверка на логовете за speed/cov/bias stability

### 11.3 Какво да се наблюдава в логовете
- **`speed`** — трябва да е стабилна около началната GPS скорост (не 5.2 m/s от 1.8)
- **`biasN/E`** — бавна конвергенция към стабилна стойност
- **`covPos`** — монотонно нарастване (нормално — несигурност без GPS)
- **`heading`** — без скокове > 30° между dispatch съобщения

## 12. История

| Дата | Събитие |
|------|---------|
| 2026-06-29 | v1: 4-state Kalman Filter, rotation matrix, sensor registration |
| 2026-06-30 | Тестове на Samsung SM-M236B — 3 driving + 1 static тест |
| 2026-06-30 | Анализ: 11 проблема идентифицирани, heading divergence 30-64° |
| 2026-07-02 07:00 | `docs/components/dead-reckoning.md` — систематизация на v1 |
| 2026-07-02 07:09 | v2 rewrite: trajectory-based вместо Kalman Filter |
| 2026-07-02 07:12 | `DeadReckoningService.kt` + `MapActivity.kt` → `getAll()` вместо `average()` |
| 2026-07-02 15:58 | **v3 rewrite** `55c632e`: 6-state Kalman + continuous sensors + heading filter |
| 2026-07-02 15:58 | `DeadReckoningKalman.kt` създаден (467 реда) |
| 2026-07-02 16:00 | Release APK build successful, чака тест от потребителя |

---

*Document maintained by Kоки & the Borkozic migration team.*
