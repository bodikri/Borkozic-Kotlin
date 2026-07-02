# Dead Reckoning — Автономно изчисление на позиция

> **Status:** 🔄 Пренаписан — trajectory-based алгоритъм (v2), чака тест
> **Last updated:** 2026-07-02 (rewrite commit `88b4bdf`)
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

## 2. v1: Kalman Filter (архивиран, заменен 2026-07-02)

Първата имплементация използваше 4-state Kalman Filter `[posN, posE, velN, velE]`
с двойна интеграция на линейното ускорение. Основните проблеми:
- **Heading дивергенция 30–64°** за 3 минути — DR heading се разминаваше драстично с реалността
- **Drift 3–4 km за 3 минути** — системата беше неизползваема
- **Filter overconfidence** — covariance конвергираше до ~1.2m, а реалната грешка беше километри
- **Двойна интеграция** — `position = ∫∫(accel) dt²` усилва bias-а квадратично
- **Осредняване на 3-те GPS точки** — губеше trajectory информацията

Пълен анализ: `docs/dr-kalman-design.md`, `Logs/DR_ANALYSIS_REPORT.md`

---

## 3. v2: Trajectory-based алгоритъм (текущ, `88b4bdf`)

### 3.1 Архитектура

| Клас | Редове | Роля |
|------|--------|------|
| `DeadReckoningCalculator.kt` | ~550 | Trajectory-based prediction + exponential GPS correction |
| `DeadReckoningService.kt` | ~540 | Foreground service, сензорна регистрация, state machine |
| `DRLogger.kt` | ~120 | Файлово логване за анализ (Downloads/Borkozic/dr_log_*.txt) |
| `GpsRingBuffer.kt` | ~80 | Кешира последни 3 GPS точки → `getAll()` подава trajectory |

### 3.2 Основна идея

Вместо да започваме от **нула** и да разчитаме само на IMU (v1), използваме
последните 3 GPS точки за да **екстраполираме trajectory-то** напред във времето.
Сензорите не генерират позицията, а само **коригират** векторите на движение.

```
GPS(t-2) ──→ GPS(t-1) ──→ GPS(t_now) ──→ DR(t+1) ──→ DR(t+2) ──→ ...
    ↑ trajectory vectors извлечени от GPS   ↑ коригирани от IMU
       speed, bearing, Δt между точките       gyro→Δbearing, accel→Δspeed
```

### 3.3 Инициализация от GPS trajectory

```kotlin
// Вход: List<GpsSnapshot> — последните 1-3 GPS точки (най-новата първа)
calculator.initialize(snapshots, accuracy)
```

1. **1 точка:** използва speed + bearing на последната точка директно
2. **2 точки:** изчислява velocity вектор между точка 2 → точка 1:
   - `bearing = atan2(Δlon, Δlat)`
   - `speed = distance / Δt`
3. **3 точки:** изчислява два velocity вектора и проверява консистентност:
   - Ако двете trajectory-та са в една посока (Δbearing < 30°) → използва последния
   - Ако са различни → взема последната точка с нейния speed + проверява дали не е завой

**Защо това е по-добре от осредняване:**
- Осредняването на 3 точки дава една статична позиция — губи информация за посоката
- Trajectory векторът улавя **в коя посока и с каква скорост се е движело** устройството
- Gyro + accel само донастройват този познат вектор

### 3.4 Prediction (IMU, до 20Hz)

```
1. Gyro/RotationVector → Δheading°  (колко се е завъртяло устройството)
2. heading += Δheading°              (корекция на посоката)
3. Accelerometer → проекция по посоката на движение:
     accel_along = accel · [cos(heading), sin(heading)]
4. speed += accel_along × dt         (единична интеграция!)
5. position += speed × [cos(heading), sin(heading)] × dt
```

**Ключова разлика с v1:** една интеграция вместо две.
- `position = ∫(speed·direction) dt` — bias влияе линейно върху speed
- v1: `position = ∫∫(accel) dt²` — bias влияе квадратично (0.1 m/s² → 180m за 60s)

### 3.5 GPS Correction (експоненциално изглаждане)

Вместо Kalman gain matrix, използваме експоненциално изглаждане:

```
speed_error = gpsSpeed - drSpeed
bearing_error = wrapAngle(gpsBearing - drBearing)

// Корекция с learn rate (β)
speed  += β_speed  × speed_error
bearing += β_bearing × bearing_error

// По-силно дърпане при по-голяма грешка
β = clamp(ε / (ε + accuracy), minβ, maxβ)
```

**Параметри:**
- `β_speed` = 0.15 — умерено дърпане на скоростта
- `β_bearing` = 0.20 — по-бързо коригиране на посоката
- `minβ` = 0.05 — минимално влияние (при много точен GPS)

### 3.6 Turn Detection (gyro-based)

Gyro-то открива завои — когато `∫gyroZ × dt` надвиши 5° за кратък интервал:
- **При завой:** намаляваме β_bearing (не искаме GPS да "дърпа" bearing по време на завой)
- **При прав участък:** нормална GPS корекция

### 3.7 Промени в интеграцията

| Компонент | v1 (Kalman) | v2 (Trajectory) |
|-----------|-------------|-----------------|
| **Инициализация** | `initialize(lat, lon, alt, speed, bearing, acc)` | `initialize(snapshots, accuracy)` |
| **Вход от ring buffer** | `gpsRingBuffer.average()` — осреднена точка | `gpsRingBuffer.getAll()` — всички 3 точки |
| **Модел** | 4-state Kalman: `[posN, posE, velN, velE]` | Скаларни: `heading, speed, lat, lon` |
| **Prediction** | `x = F*x + B*u` + `P = F*P*Fᵀ + Q` | `heading+=Δgyro`, `speed+=accel_proj×dt` |
| **Correction** | `K = P*Hᵀ * (H*P*Hᵀ + R)⁻¹` | `state += β × error` (exponential smoothing) |
| **Интеграция** | Двойна: `∫∫accel` | Единична: `∫speed` |
| **Heading** | Rotation matrix → azimuth | Gyro Δheading + GPS bearing correction |
| **Bias** | Running average на velocity innovation | Няма нужда — speed се коригира директно |
| **Редове** | ~1100 | ~550 |

### 3.8 DeadReckoningService (промени)

**Преди:**
```kotlin
fun startDeadReckoning(lat, lon, alt, speed, bearing, accuracy)
calculator.initialize(lat, lon, alt, speed, bearing, accuracy)
```

**Сега:**
```kotlin
fun startDeadReckoning(snapshots: List<GpsSnapshot>)
calculator.initialize(snapshots, accuracy)
```

MapActivity вече подава `gpsRingBuffer.getAll()` вместо `gpsRingBuffer.average()`.

---

## 4. Режими на работа

| Режим | Описание | GPS |
|-------|----------|-----|
| **Manual** | Тестов режим, GPS е наличен | Exponential correction на ~1Hz |
| **Real DR** | GPS загубен, истински DR | Само prediction от IMU |

### 4.1 State machine (без промяна)
- `DR_IDLE` → чака GPS загуба + 5s activation delay
- `DR_ACTIVE` → изчислява позиция от сензори, broadcast на всеки 200ms
- `DR_STOPPED` → 3 минути изтекли или GPS възстановен

### 4.2 Сензори (без промяна)
- `TYPE_GAME_ROTATION_VECTOR` → `TYPE_ROTATION_VECTOR` (fallback)
- `TYPE_LINEAR_ACCELERATION` → `TYPE_ACCELEROMETER` (fallback)
- `TYPE_GYROSCOPE` — завои и heading интеграция
- `TYPE_MAGNETIC_FIELD` — compass heading fallback
- `TYPE_PRESSURE` — барометър за височина

### 4.3 Честоти (без промяна)
- Prediction (IMU): 20Hz (`SENSOR_DELAY_GAME`)
- Correction (GPS): ~1Hz
- Broadcast/Logging: 5Hz (200ms)

---

## 5. Очаквани подобрения спрямо v1

| Метрика | v1 (Kalman) | v2 (Trajectory) — очаквано |
|---------|-------------|---------------------------|
| Heading грешка (3 мин) | -30° до -64° | < 15° |
| Позиционен drift (3 мин) | 3–4 km | < 200 m |
| Статичен drift | 0.46 m/min | ~0 m/min (без двойна интеграция) |
| Bias amplification | Квадратична | Линейна |
| Сложност | Висока (матрици, инверсия) | Ниска (скалари) |

---

## 6. Тестови данни (2026-06-30) — от v1 (архивни)

### 3.1 Устройство
- **Модел:** Samsung SM-M236B (Galaxy A23 5G?)
- **Android:** 14

### 3.2 Статичен тест (35s)

| Метрика | Стойност |
|---------|----------|
| Heading wander | 8.3° (266.6° – 274.9°) |
| Position drift | 0.27m north, 0.09m east |
| Drift rate | ~0.46 m/min |
| P_diag | 107 → 5.0 (конвергира) |

### 3.3 Три driving теста (~3 мин всеки)

| Метрика | Тест 1 | Тест 2 | Тест 3 |
|---------|--------|--------|--------|
| **Heading грешка (край)** | -57° | -37° | -48° |
| **Позиционен drift** | 3.1 km | 4.1 km | 4.3 km |
| **Скоростна грешка (край)** | -3.4 m/s | +3.2 m/s | +9.8 m/s |
| **P_diag (край)** | 1.17 | 1.17 | 1.17 |
| **Bias saturation** | 0.40 | -0.68 | -0.40 |

---

## 4. Идентифицирани проблеми

### 🔴 CRITICAL

1. **Heading дивергенция (30–64°):** DR heading се разминава с GPS bearing с 30–64°
   за 3 минути. Това е основната причина за огромните позиционни грешки —
   грешен heading проектира скоростта в грешна посока.

2. **Масивен позиционен drift (3–4 km за 3 минути):** GPS corrections достигат
   километри. DR системата е неизползваема за навигация след 30 секунди.

3. **Filter overconfidence:** P_diag конвергира до ~1.2m, а реалната грешка е
   хиляди метри. Филтърът е драстично прекалибриран — твърде уверен в оценките си.

### 🟡 MAJOR

4. **Bias saturation:** Акселерометър bias-ът спира да расте при 0.4–0.68,
   което пречи на филтъра да коригира нататъшния drift.

5. **Velocity scale factor:** DR скорост подценява с -10 m/s (Тест 1) и
   надценява с +10 m/s (Тест 3). Акселерометър scale factor не е калибриран.

6. **Heading initialization:** Начален heading скача от GPS bearing (46.5°)
   до compass heading (~270°) — 223° скок. Ориентацията на устройство ≠ посока на движение.

### 🟠 MODERATE

7. **Статичен drift:** Heading блуждае 8.3°, позиция дрифтва 0.28m за 35s
   при напълно неподвижно устройство.

8. **Anomalous heading spikes:** Внезапни скокове от 17–19° в heading-а
   (вероятно rotation matrix discontinuities или сензорни гличове).

9. **P_diag floor при 1.17:** Несигурността никога не пада под 1.17 —
   R (measurement noise) е твърде голямо или Q (process noise) пречи на конвергенция.

### 🟢 MINOR

10. **Липса на ZUPT:** Няма zero-velocity detection. При покой системата
    продължава да трупа drift вместо да замрази позицията.

11. **GPS accuracy деградация:** От 3.8m до 11–15m в някои сесии.

---

## 5. Следващи стъпки (приоритизирани)

### Priority 1 — Heading fix (най-голямо влияние)
1. **GPS bearing като директно измерване** в Kalman филтъра — constrain-ва heading
2. **Gyro bias калибрация при старт** — 2–5 сек статично събиране преди активиране
3. **Compass за инициализация**, не GPS bearing (ориентация ≠ посока на движение)

### Priority 2 — Filter recalibration
4. **Recalibrate Q и R** — увеличение на process noise, корекция на measurement noise
5. **Махни bias saturation** или увеличи лимита — да може да расте при нужда
6. **Adaptive Kalman filter** — автоматична настройка на Q/R по innovation

### Priority 3 — Допълнителни подобрения
7. **ZUPT** (Zero-Velocity Update) — замразяване при покой
8. **Accelerometer scale factor** като допълнително състояние
9. **Low-pass филтър** на gyro/accel преди интеграция
10. **Rotation matrix валидация** — проверка за discontinuities

---

## 6. Dependencies (засегнати файлове)

### Source files
- `borkozic/src/main/java/com/borkozic/location/DeadReckoningCalculator.kt`
- `borkozic/src/main/java/com/borkozic/location/DeadReckoningService.kt`
- `borkozic/src/main/java/com/borkozic/location/DRLogger.kt`
- `borkozic/src/main/java/com/borkozic/location/GpsRingBuffer.kt`
- `borkozic/src/main/java/com/borkozic/location/CompassSensorHelper.kt`
- `borkozic/src/main/java/com/borkozic/location/NmeaParser.kt`

### Integration points
- `borkozic/src/main/java/com/borkozic/MapActivity.kt` — UI integration, DR индикатор
- `borkozic/src/main/java/com/borkozic/MapView.kt` — показване на DR позиция на картата
- `borkozic/src/main/java/com/borkozic/HSIActivity.kt` — HSI дисплей (използва heading)

### Design docs & analysis
- `docs/dr-kalman-design.md` — Пълен дизайн на Kalman филтъра
- `Logs/DR_ANALYSIS_REPORT.md` — Подробен анализ на тестови логове
- `Logs/kalman_analysis.py` — Python скрипт за анализ на логове
- `Logs/kalman_optimize.py` — Python скрипт за оптимизация на параметри

---

## 7. Key Parameters — v2 (Trajectory-based)

| Параметър | Стойност | Описание |
|-----------|----------|----------|
| `β_speed` | 0.15 | Exponential smoothing factor за скоростна корекция |
| `β_bearing` | 0.20 | Exponential smoothing factor за bearing корекция |
| `minβ` | 0.05 | Минимален smoothing factor (висока GPS точност) |
| `TURN_THRESHOLD_DEG` | 5° | Gyro интеграция над този праг = завой |
| `TRAJECTORY_DIRECTION_CONSISTENCY` | 30° | Максимален Δbearing между две trajectory-та за консистентност |
| `ACCEL_DEADZONE` | 0.05 m/s² | Минимално ускорение (под това = noise) |
| `STATIONARY_SPEED` | 0.3 m/s | Под тази скорост → считаме устройството за неподвижно |
| `MAX_DURATION_MS` | 180,000 ms | Максимална продължителност на DR |
| `ACTIVATION_DELAY_MS` | 5,000 ms | Забавяне преди активиране след GPS загуба |
| `DRIFT_RATE` | 2.0 m/s | Предполагаем drift rate за accuracy estimation |

---

## 8. История

| Дата | Събитие |
|------|---------|
| 2026-06-29 | v1: Kalman Filter, rotation matrix, sensor registration |
| 2026-06-30 | Тестове на Samsung M236B — 3 driving + 1 static тест |
| 2026-06-30 | Анализ: 11 проблема идентифицирани, Kalman overconfidence, heading divergence |
| 2026-07-02 07:00 | Създаден `docs/components/dead-reckoning.md` — систематизация на v1 |
| 2026-07-02 07:09 | **v2 rewrite** `88b4bdf`: trajectory-based вместо Kalman Filter |
| 2026-07-02 07:12 | `DeadReckoningService.kt` + `MapActivity.kt` обновени за `getAll()` вместо `average()` |
| 2026-07-02 07:17 | Документация обновена — v2 описана, v1 архивирана за справка |

---

*Document maintained by Kоки & the Borkozic migration team.*
