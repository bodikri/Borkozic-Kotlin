# Dead Reckoning Algorithm Design — Kalman Filter Approach

## 1. Проблем с текущата имплементация

### 1.1 Симптоми от DR лога (2026-06-29)
- **Speed collapse**: DR speed пада от 1.79 → 0.17 m/s за 8 секунди, докато GPS показва 2-3.5 m/s
- **Position underestimation**: DR се премества ~11м, GPS — ~89м (8x подценяване)
- **Heading mismatch**: Compass дава ~50°, GPS bearing — 176.8°

### 1.2 Коренни причини

#### Причина 1: Агресивен drag decay
```kotlin
val drag = 0.98.pow(dt * 20.0)
velNorth *= drag
velEast *= drag
```
При dt=0.05s (20Hz): `0.98^1 = 0.98` per frame → ~0.98^20 = 0.668 per second.
За 8 секунди: `0.668^8 = 0.038` → скоростта пада до 3.8% от първоначалната.
Това е **нефизично** — велосипед/мотоцикл има много малко аеродинамично съпротивление при 2-3 m/s.

#### Причина 2: Липса на правилна координатна трансформация
Калкулаторът предполага че Phone X→North, Phone Y→East (хоризонтално закрепен). Но:
- Телефонът се накланя при движение
- Вибрации променят ориентацията
- Няма rotation matrix за трансформация device→Earth frame

#### Причина 3: Липса на bias estimation
MEMS accelerometer-ите имат систематичен bias от 0.02-0.5 m/s². При двойна интеграция:
- 0.1 m/s² bias × 10s = 1 m/s грешка в скоростта
- 0.1 m/s² bias × 10s² / 2 = 5m грешка в позицията
Текущият код има ACCEL_DEADZONE=0.3 m/s², който убива реални малки ускорения.

#### Причина 4: Няма GPS корекция
В manual mode GPS се логва, но не се използва за корекция на филтъра. Филтърът дрифтва безкрайно.

---

## 2. Предложено решение: 4-state Kalman Filter

### 2.1 Архитектура

**State vector** (4D):
```
x = [ posN,    // позиция North (метри от стартовата точка)
      posE,    // позиция East  (метри от стартовата точка)
      velN,    // скорост North (m/s)
      velE ]   // скорост East  (m/s)
```

**Control input** (2D):
```
u = [ accelN,  // ускорение North (m/s²) — трансформирано в Earth frame
      accelE ] // ускорение East  (m/s²)
```

**State transition matrix** F (4×4):
```
F = [ 1  0  dt  0  ]
    [ 0  1  0   dt ]
    [ 0  0  1   0  ]
    [ 0  0  0   1  ]
```

**Control matrix** B (4×2):
```
B = [ 0.5*dt²   0      ]
    [ 0         0.5*dt²]
    [ dt        0      ]
    [ 0         dt     ]
```

**Prediction**: `x = F*x + B*u`
**Covariance**: `P = F*P*F^T + Q`

### 2.2 Measurement (GPS correction)

**Measurement vector** (4D, когато GPS е наличен):
```
z = [ gpsPosN, gpsPosE, gpsVelN, gpsVelE ]
```

**Measurement matrix** H (4×4 identity):
```
H = [ 1  0  0  0 ]
    [ 0  1  0  0 ]
    [ 0  0  1  0 ]
    [ 0  0  0  1 ]
```

**Kalman gain**: `K = P*H^T * (H*P*H^T + R)^{-1}`
**State update**: `x = x + K*(z - H*x)`
**Covariance update**: `P = (I - K*H)*P`

### 2.3 Process noise Q (4×4)

Q моделира несигурността в системата. По-голямо Q = по-бърза адаптация, но по-малко стабилност.

```
Q = [ σ_pos² * dt⁴/4    0              σ_pos² * dt³/2    0             ]
    [ 0                 σ_pos² * dt⁴/4  0                 σ_pos² * dt³/2]
    [ σ_pos² * dt³/2    0              σ_pos² * dt²      0             ]
    [ 0                 σ_pos² * dt³/2  0                 σ_pos² * dt²  ]
```

Където `σ_pos²` е process noise spectral density. За MEMS accelerometer на телефон:
- `σ_accel ≈ 0.1-0.3 m/s²/√s` (типичен noise density)
- `σ_pos² = σ_accel²` 

На практика използваме опростен diagonal Q:
```
Q = diag(0.1, 0.1, 0.1, 0.1) * dt
```
Това може да се tune-ва. По-голямо Q позволява на филтъра да "вярва" повече на измерванията.

### 2.4 Measurement noise R (4×4)

R моделира GPS точността:
```
R = diag( gpsAccuracy²,  gpsAccuracy²,  gpsSpeedAccuracy²,  gpsSpeedAccuracy² )
```

- `gpsAccuracy` = GPS location.getAccuracy() (типично 3-10m)
- `gpsSpeedAccuracy` ≈ gpsAccuracy / dt_gps (типично 0.5-2 m/s)

Когато GPS няма fix (real DR mode), корекцията не се прилага — само prediction.

### 2.5 Bias estimation

Accelerometer bias се оценява индиректно чрез Kalman filter innovation:
- Когато GPS е наличен, филтърът сравнява предсказана скорост с GPS скорост
- Разликата (innovation) се използва за корекция на velocity state
- Това indirektly компенсира accelerometer bias чрез velocity correction

За експлицитна bias estimation може да се разшири state vector-а до 6D:
```
x = [ posN, posE, velN, velE, biasN, biasE ]
```
Но за простота започваме с 4D и разчитаме на GPS correction за bias компенсация.

### 2.6 Accelerometer bias running average (допълнително)

Когато GPS е наличен и скоростта е стабилна (ускорение ≈ 0), можем да оценяваме bias:
```kotlin
if (gpsAvailable && speedChange < threshold) {
    estimatedBias = 0.99 * estimatedBias + 0.01 * measuredAccel
}
correctedAccel = measuredAccel - estimatedBias
```

---

## 3. Координатна трансформация: Device → Earth

### 3.1 Проблем
LINEAR_ACCELERATION сензорът дава ускорение в **device frame**:
- X: надолу към дъното на телефона
- Y: наляво
- Z: извън екрана (нагоре)

За да интегрираме скорост и позиция, трябва ускорение в **Earth frame** (North, East, Up).

### 3.2 Решение: Rotation Vector + Matrix умножение

Android предоставя `TYPE_ROTATION_VECTOR` сензор, който комбинира accelerometer, gyroscope и magnetometer за точна оценка на ориентацията.

```kotlin
// 1. Вземе rotation matrix от rotation vector
val R = FloatArray(9)
SensorManager.getRotationMatrixFromVector(R, rotationVectorValues)

// 2. R трансформира device→world (East, North, Up според Android docs)
//    world_vector = R * device_vector
val earthAccel = FloatArray(3)
android.opengl.Matrix.multiplyMV(earthAccel, 0, R, 0, deviceAccel, 0)

// 3. earthAccel[0] = East component
//    earthAccel[1] = North component
//    earthAccel[2] = Up component
```

### 3.3 TYPE_GAME_ROTATION_VECTOR (по-добро за превозни средства)

`TYPE_GAME_ROTATION_VECTOR` не използва magnetometer → не се влияе от магнитни смущения от мотор/рамка. Подходящо за велосипед/мотоциклет.

Ако не е наличен, fallback към `TYPE_ROTATION_VECTOR`.

### 3.4 Heading от rotation matrix

```kotlin
val orientation = FloatArray(3)
SensorManager.getOrientation(R, orientation)
// orientation[0] = azimuth (heading) в радиани [-π, π]
// orientation[1] = pitch
// orientation[2] = roll
val headingDegrees = Math.toDegrees(orientation[0].toDouble())
```

Забележка: `getOrientation` връща heading спрямо магнитния север. За истински heading (спрямо географски север) трябва да се добави magnetic declination.

---

## 4. Инициализация

### 4.1 При стартиране на DR (manual или auto)

```kotlin
fun initialize(lat, lon, alt, speed, bearing, accuracy) {
    // 1. Геодезични координати
    refLatitude = lat
    refLongitude = lon
    refAltitude = alt

    // 2. State vector
    x = [0, 0, speed*cos(bearing), speed*sin(bearing)]

    // 3. Covariance — голяма несигурност в позицията, малка в скоростта
    P = diag(accuracy², accuracy², 1.0, 1.0)

    // 4. Bias
    accelBiasN = 0.0
    accelBiasE = 0.0
    estimatedBiasN = 0.0
    estimatedBiasE = 0.0
}
```

### 4.2 При GPS загуба (real DR mode)

Ако DR е бил активен с GPS корекция (manual mode), при GPS загуба:
- Филтърът продължава само prediction (без correction)
- Covariance P нараства с времето (нарастваща несигурност)
- Позицията се изчислява само от IMU

---

## 5. Работа в два режима

### 5.1 Manual mode (тестов режим)
- GPS е наличен → correction стъпка на всеки GPS fix (~1Hz)
- DR_LOC се логва на 5Hz (от филтъра)
- GPS_REAL се логва на 5Hz (от raw GPS)
- Филтърът се калибрира непрекъснато

### 5.2 Real DR mode (GPS загуба)
- GPS не е наличен → само prediction стъпка (20Hz от IMU)
- Позицията дрифтва, но много по-бавно от naive double integration
- Covariance нараства → accuracy се влошава прогресивно
- Максимална продължителност: 3 минути (180s)

---

## 6. Практически implmentационни детайли

### 6.1 Matrix операции

За 4×4 матрици използваме simple DoubleArray(16) с ръчни операции:
- F*x: matrix-vector multiply (4×4 × 4)
- F*P*F^T: matrix-matrix multiply (4×4 × 4×4 × 4×4)
- P*H^T: matrix-matrix multiply
- (H*P*H^T + R)^{-1}: 4×4 matrix inversion

Не е нужен външен library. За 4×4 инверсия използваме Gauss-Jordan елиминация.

### 6.2 Sensor регистрация

```kotlin
// Primary: TYPE_GAME_ROTATION_VECTOR (без magnetometer)
rotationVector = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

// Accelerometer: TYPE_LINEAR_ACCELERATION (без gravity)
accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

// Запазваме magnetometer за compass heading (fallback)
magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
```

### 6.3 Честота на обновяване

- **Prediction (IMU)**: 20Hz (SENSOR_DELAY_GAME)
- **Correction (GPS)**: ~1Hz (всяка GPS точка)
- **Broadcast/Logging**: 5Hz (всеки 200ms)

### 6.4 Tuning параметри

| Параметър | Стойност | Описание |
|-----------|----------|----------|
| `Q_POS` | 0.1 | Process noise за позиция |
| `Q_VEL` | 0.1 | Process noise за скорост |
| `R_POS` | gpsAccuracy² | Measurement noise за позиция |
| `R_VEL` | 1.0 | Measurement noise за скорост |
| `BIAS_LEARN_RATE` | 0.005 | Bias estimation learn rate |
| `ACCEL_NOISE_TH` | 0.05 m/s² | Noise threshold (под това = noise) |

---

## 7. Очаквани резултати

### 7.1 С GPS корекция (manual mode)
- DR_LOC трябва да следва GPS_REAL с грешка < 5m
- Скоростта трябва да съвпада с GPS скоростта ± 0.3 m/s
- Heading трябва да съвпада с GPS bearing ± 10°

### 7.2 Без GPS (real DR, до 30 секунди)
- Позиционна грешка: < 15m за 30s (vs ~80m при текущия алгоритъм)
- Скоростна грешка: < 0.5 m/s за 30s
- Heading: ± 15° (от gyro интеграция + compass)

### 7.3 Без GPS (real DR, до 3 минути)
- Позиционна грешка: < 100m за 180s (кофти, но по-добре от без нищо)
- Основният фактор за грешката ще бъде accelerometer bias drift

---

## 8. Референтни източници

1. **mad-location-manager** (github.com/maddevsio/mad-location-manager)
   - GPSAccKalmanFilter.java — 4-state KF с acceleration control input
   - KalmanLocationService.java — sensor registration, frame conversion
   - Matrix.java — simple matrix operations implementation

2. **RIDI: Robust IMU Double Integration** (arxiv.org/abs/1712.09004)
   - NN-based bias estimation for IMU double integration
   - Показва че bias correction е ключов за < 10m точност

3. **Android SensorManager docs**
   - getRotationMatrixFromVector: R transforms device→world
   - getOrientation: azimuth, pitch, roll from R
   - TYPE_GAME_ROTATION_VECTOR: без magnetometer

4. **Allan Variance for MEMS characterization**
   - Smartphone MEMS accelerometer noise: ~0.1-0.3 m/s²/√s
   - Bias instability: ~0.02-0.1 m/s² over 1-10 minutes