# Dead Reckoning — Автономно изчисление на позиция

> **Status:** 🟡 В процес на разработка — имплементиран Kalman Filter, проблеми с heading точността
> **Last updated:** 2026-07-02
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

## 2. Current Implementation

### 2.1 Архитектура

| Клас | Редове | Роля |
|------|--------|------|
| `DeadReckoningCalculator.kt` | ~1100 | 4-state Kalman Filter — sensor fusion ядро |
| `DeadReckoningService.kt` | ~540 | Foreground service, сензорна регистрация, state machine |
| `DRLogger.kt` | ~120 | Файлово логване за анализ (Downloads/Borkozic/dr_log_*.txt) |
| `GpsRingBuffer.kt` | ~80 | Кешира последни GPS точки за инициализация на DR |

### 2.2 Kalman Filter (DeadReckoningCalculator)

**State vector (4D):**
```
x = [posN, posE, velN, velE]
```

**Prediction step (IMU, 20Hz):**
```
x = F*x + B*u      (state transition + control input)
P = F*P*Fᵀ + Q      (covariance propagation)
```
- Control input `u = [accelN, accelE]` — линейно ускорение в Earth frame
- Трансформация device→Earth чрез rotation matrix от `TYPE_GAME_ROTATION_VECTOR`

**Correction step (GPS, ~1Hz):**
```
K = P*Hᵀ * (H*P*Hᵀ + R)⁻¹    (Kalman gain)
x = x + K*(z - H*x)            (state update)
P = (I - K*H)*P                (covariance update)
```
- Measurement `z = [gpsPosN, gpsPosE, gpsVelN, gpsVelE]`
- H = I (4×4 identity — директно измерване на всички състояния)

**Heading:**
- Първичен източник: rotation vector сензор → `getOrientation()` → azimuth
- Fallback: gyro интеграция + compass complementary filter
- GPS bearing (от скоростния вектор) при скорост > 0.5 m/s

**Bias estimation:**
- Running average: `bias += learnRate * velocity_innovation`
- Learn rate: 0.005 (бавно учене)

**Височина:**
- Барометър (ако е наличен): `h = 44330 * (1 - (P/1013.25)^(1/5.255))`
- Fallback: стартова GPS височина

### 2.3 DeadReckoningService

**State machine:**
- `DR_IDLE` → чака GPS загуба + 5s activation delay
- `DR_ACTIVE` → изчислява позиция от сензори, broadcast на всеки 200ms
- `DR_STOPPED` → 3 минути изтекли или GPS възстановен

**Сензори:**
- `TYPE_GAME_ROTATION_VECTOR` (приоритетен, без magnetometer) → `TYPE_ROTATION_VECTOR` (fallback)
- `TYPE_LINEAR_ACCELERATION` (приоритетен, без gravity) → `TYPE_ACCELEROMETER` (fallback)
- `TYPE_GYROSCOPE` (само за heading fallback)
- `TYPE_MAGNETIC_FIELD` (само за compass heading fallback)
- `TYPE_PRESSURE` (барометър за височина)

**Честоти:**
- Prediction (IMU): 20Hz (`SENSOR_DELAY_GAME`)
- Correction (GPS): ~1Hz
- Broadcast/Logging: 5Hz (200ms)

### 2.4 Два режима на работа

| Режим | Описание | GPS |
|-------|----------|-----|
| **Manual** | Тестов режим, GPS е наличен | Correction стъпка на 1Hz |
| **Real DR** | GPS загубен, истински DR | Само prediction, covariance расте |

---

## 3. Тестови данни (2026-06-30)

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

## 7. Key Parameters (за бъдещ tuning)

| Параметър | Текуща стойност | Описание |
|-----------|----------------|----------|
| `sigmaAccel` | 0.15 m/s²/√s | Process noise spectral density за акселерометър |
| `sigmaGpsVel` | 0.5 m/s | Measurement noise за GPS скорост |
| `ACCEL_DEADZONE` | 0.05 m/s² | Минимално ускорение (под това = noise) |
| `biasLearnRate` | 0.005 | Скорост на учене за bias estimation |
| `COMPASS_WEIGHT` | 0.02 | Тегло на компаса в complementary filter |
| `MAX_DURATION_MS` | 180,000 ms | Максимална продължителност на DR |
| `ACTIVATION_DELAY_MS` | 5,000 ms | Забавяне преди активиране след GPS загуба |
| `DRIFT_RATE` | 2.0 m/s | Предполагаем drift rate за accuracy estimation |

---

## 8. История

| Дата | Събитие |
|------|---------|
| 2026-06-29 | Първоначална имплементация: Kalman Filter, rotation matrix, sensor registration |
| 2026-06-30 | Тестово логване на Samsung M236B — 3 driving + 1 static тест |
| 2026-06-30 | Подробен анализ: идентифицирани 11 проблема, 15 препоръки |
| 2026-07-02 | Създаден този документ — систематизация на състоянието |

---

*Document maintained by Kоки & the Borkozic migration team.*
