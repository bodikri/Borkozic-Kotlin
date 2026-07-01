# Dead Reckoning Log Analysis Report
## Date: 2026-06-30 | Device: Samsung SM-M236B (Android 14)

---

## 1. SESSION OVERVIEW

| Session | Time | Duration | GPS Corrections | Type | Speed Range (GPS) |
|---------|------|----------|-----------------|------|-------------------|
| 071922 | 07:19:22 | 35.3s | 35 | Static | 0 m/s |
| 072031 | 07:20:31 | 2.7s | 3 | Static (manual) | 0 m/s |
| 120852 | 12:08:52 | 144.5s | 145 | Driving | 11.5–26.6 m/s |
| 121154 | 12:11:54 | 180.0s | 180 | Driving | 13.7–26.0 m/s |
| 121544 | 12:15:44 | 180.0s | 181 | Driving | 17.8–31.4 m/s |
| 14 others | various | 0ms | 0 | App open, no DR | — |

---

## 2. STATIC TEST ANALYSIS (07:19:22 – 07:19:57)

### 2.1 Heading Stability

| Metric | Value |
|--------|-------|
| Initial heading (GPS bearing) | 46.5° |
| Heading after rotation matrix init | ~270° (compass-based) |
| Min heading | 266.60° |
| Max heading | 274.89° |
| **Range (wander)** | **8.29°** |
| Mean heading | 270.88° |

**Finding:** The heading wanders **8.3°** over 35 seconds while the device is completely stationary. This is a significant amount of gyro drift. The heading also jumps from 46.5° (GPS bearing) to ~270° (compass-derived) when the rotation matrix initializes — a **223° discontinuity** that indicates the initial heading is set from GPS bearing (direction of last movement), not from the device's physical orientation.

### 2.2 Position Drift

| Metric | posN (north, m) | posE (east, m) |
|--------|-----------------|-----------------|
| Min | -0.264 | -0.002 |
| Max | +0.004 | +0.086 |
| **Total drift** | **0.268 m** | **0.088 m** |

**Finding:** posN drifts consistently southward (negative), accumulating 0.27m of drift in 35 seconds. posE drifts eastward then stabilizes. The drift rate is approximately **0.46 m/min** northward. This is caused by accelerometer bias being integrated twice.

### 2.3 GPS Corrections

- **35 corrections** in 35 seconds (1 Hz GPS update rate)
- All corrections are near-zero (gpsPosN ≈ 0, gpsPosE ≈ 0) — correct for stationary
- GPS accuracy: 3.8–15.2m
- Corrections are working correctly: they pull the position back toward origin

### 2.4 P_diag Evolution (Uncertainty)

```
Start:  [107.20, 107.20, 1.00, 1.00]
End:    [4.98,  4.98,  0.01, 0.01]
```

**Finding:** P_diag converges from very high initial uncertainty to ~5.0 for position and 0.01 for velocity. The filter IS converging, which is good. However, it converges to **~5.0** rather than near-zero, suggesting a minimum uncertainty floor. The velocity uncertainty converges quickly to 0.01.

### 2.5 Bias Estimation

```
biasN: 0.0000 → 0.0037 (growing over 35s)
biasE: 0.0000 → -0.0018
```

**Finding:** The bias estimates are growing while the device is stationary. This is **problematic** — the filter is attributing the position drift to a changing bias rather than recognizing zero motion. The bias growth correlates with the posN drift.

---

## 3. DRIVING TEST 1 (12:08:52 – 12:11:17, 144.5s)

### 3.1 Heading Accuracy: DR vs GPS Bearing

| Phase | DR Heading | GPS Bearing | Error |
|-------|-----------|-------------|-------|
| Start | 303.2° | 298.1° | +5.1° |
| After init | 270.2° | 300.8° | **-30.6°** |
| Mid | 278.6° | 307.9° | **-29.3°** |
| Late | 222.3° | 266.2° | **-43.9°** |
| End | 211.9° | 269.3° | **-57.4°** |

**Finding: CRITICAL.** DR heading is consistently **28–57° lower** than GPS bearing, and the error **grows over time**. The heading error starts at -30° and widens to -57° by the end. This means the DR system is pointing in a significantly wrong direction, which explains the massive position errors.

### 3.2 Velocity Accuracy: DR Speed vs GPS Speed

| Phase | DR Speed | GPS Speed | Error |
|-------|----------|-----------|-------|
| Start | 8.7 m/s | 11.5 m/s | -2.8 |
| Mid | 13.0 m/s | 22.5 m/s | **-9.5** |
| Late | 20.6 m/s | 24.0 m/s | -3.4 |

**Finding:** DR consistently **underestimates** speed by 3–10 m/s. The error peaks mid-session at -9.5 m/s and narrows toward the end. This suggests a scale factor error in the accelerometer integration.

### 3.3 GPS Position Corrections

| Metric | gpsPosN | gpsPosE |
|--------|---------|---------|
| Min | 14.31 | -19.49 |
| Max | 975.56 | -2981.74 |
| **Growth** | **961 m** | **2962 m** |

**Finding: CRITICAL.** GPS corrections reach **nearly 3 km** in the east direction. The DR position is diverging from GPS at an alarming rate. The corrections grow monotonically, indicating the DR system is accumulating error without bound.

### 3.4 P_diag Evolution

```
Start:  [2.68, 2.68, 0.05, 0.05]
End:    [1.17, 1.17, 0.01, 0.01]
```

**Finding:** P_diag converges from 2.68 to ~1.17–1.24. The filter is converging, but the actual position errors are in the **thousands of meters** while the filter thinks uncertainty is only ~1.2m. This is a **severe filter inconsistency** — the filter is overconfident.

### 3.5 Bias Estimation

```
biasN: 0.0463 → 0.4033 (saturates)
```

**Finding:** Bias grows and then **saturates at ~0.40**, after which it stops changing. This suggests the bias estimation has hit a limit or the filter has stopped updating it. The saturated bias cannot correct for the ongoing drift.

---

## 4. DRIVING TEST 2 (12:11:54 – 12:14:54, 180.0s)

### 4.1 Heading Accuracy

| Phase | DR Heading | GPS Bearing | Error |
|-------|-----------|-------------|-------|
| Start | 281.7° | 278.6° | +3.1° |
| After init | 270.8° | 281.1° | -10.3° |
| Mid | 270.1° | 272.3° | -2.2° |
| Late | 244.2° | 281.1° | **-36.9°** |
| End | 244.1° | 281.6° | **-37.5°** |

**Finding:** Heading starts reasonably close (within 10°) but **diverges to -37°** by the end. There's also an **anomalous spike** at one point: DR=304.4° vs GPS=285.4° (a sudden +19° jump). The heading error grows over time, same pattern as Test 1.

### 4.2 Velocity Accuracy

| Phase | DR Speed | GPS Speed | Error |
|-------|----------|-----------|-------|
| Start | 23.7 m/s | 23.8 m/s | -0.1 |
| Late | 25.3 m/s | 22.1 m/s | **+3.2** |

**Finding:** Unlike Test 1, DR speed starts accurate but becomes **higher** than GPS by the end. The error flips from near-zero to +3.2 m/s overestimation. This suggests the speed error depends on the specific motion profile.

### 4.3 GPS Position Corrections

| Metric | gpsPosN | gpsPosE |
|--------|---------|---------|
| Min | -138.93 | -45.49 |
| Max | 220.48 | -4160.62 |
| **Growth** | **359 m** | **4115 m** |

**Finding:** Even worse than Test 1 — corrections reach **4.1 km** eastward. The posN correction crosses zero (vehicle changes north/south direction), but posE grows monotonically negative.

### 4.4 P_diag

```
Start:  [2.54, 2.54, 0.04, 0.04]
End:    [1.17, 1.17, 0.01, 0.01]
```

Same pattern: filter converges to ~1.17 while actual errors are in kilometers.

### 4.5 Bias

```
biasN: -0.0004 → -0.6795 (saturates)
```

Bias saturates at **-0.68**, even higher than Test 1.

---

## 5. DRIVING TEST 3 (12:15:44 – 12:18:45, 180.0s)

### 5.1 Heading Accuracy

| Phase | DR Heading | GPS Bearing | Error |
|-------|-----------|-------------|-------|
| Start | 291.9° | 290.1° | +1.8° |
| After init | 270.8° | 295.4° | -24.6° |
| Mid | 273.9° | 296.1° | -22.2° |
| Late | 217.4° | 281.5° | **-64.1°** |
| End | 220.7° | 268.7° | **-48.0°** |

**Finding:** The worst heading error of all tests — reaches **-64°**. There's an anomalous spike: DR=288.6° vs GPS=271.7° (a sudden +17° jump). The heading error grows from -25° to -64°.

### 5.2 Velocity Accuracy

| Phase | DR Speed | GPS Speed | Error |
|-------|----------|-----------|-------|
| Start | 25.8 m/s | 26.7 m/s | -0.9 |
| Late | 28.5 m/s | 18.8 m/s | **+9.8** |

**Finding:** DR speed diverges dramatically — overestimating by **9.8 m/s** by the end. This is the worst velocity error across all tests.

### 5.3 GPS Position Corrections

| Metric | gpsPosN | gpsPosE |
|--------|---------|---------|
| Min | 20.39 | -49.91 |
| Max | 1649.48 | -4006.17 |
| **Growth** | **1629 m** | **3956 m** |

**Finding:** Corrections reach **1.6 km north** and **4.0 km east**. Same pattern of monotonic growth.

### 5.4 P_diag

```
Start:  [2.60, 2.60, 0.04, 0.04]
End:    [1.17, 1.17, 0.01, 0.01]
```

Same convergence pattern.

### 5.5 Bias

```
biasN: 0.0207 → -0.3981 (saturates)
```

Saturates at **-0.40**.

---

## 6. KEY METRICS SUMMARY

### 6.1 Position Correction Magnitudes

| Test | gpsPosN Range | gpsPosE Range | Total Drift |
|------|--------------|--------------|-------------|
| Static | ~0 | ~0 | 0.28 m |
| Drive 1 | 14 → 976 | -19 → -2982 | ~3.1 km |
| Drive 2 | -139 → 220 | -45 → -4161 | ~4.1 km |
| Drive 3 | 20 → 1649 | -50 → -4006 | ~4.3 km |

### 6.2 Heading Error (DR – GPS Bearing)

| Test | Start Error | End Error | Trend |
|------|------------|-----------|-------|
| Drive 1 | +5° → -31° | -57° | **Worsening** |
| Drive 2 | +3° → -10° | -37° | **Worsening** |
| Drive 3 | +2° → -25° | -48° | **Worsening** |

### 6.3 Velocity Error (DR Speed – GPS Speed)

| Test | Start Error | End Error | Trend |
|------|------------|-----------|-------|
| Drive 1 | -2.8 | -3.4 | Underestimates |
| Drive 2 | -0.1 | +3.2 | Flips to overestimate |
| Drive 3 | -0.9 | +9.8 | Severe overestimate |

### 6.4 P_diag Convergence

| Test | Start P_diag[0] | End P_diag[0] | Converged? |
|------|-----------------|----------------|------------|
| Static | 107.20 | 4.98 | Yes, to ~5.0 |
| Drive 1 | 2.68 | 1.17 | Yes, to ~1.2 |
| Drive 2 | 2.54 | 1.17 | Yes, to ~1.2 |
| Drive 3 | 2.60 | 1.17 | Yes, to ~1.2 |

### 6.5 Bias Saturation

| Test | Final biasN | Saturated? |
|------|------------|------------|
| Static | 0.0037 | No (growing) |
| Drive 1 | 0.4033 | **Yes** |
| Drive 2 | -0.6795 | **Yes** |
| Drive 3 | -0.3981 | **Yes** |

---

## 7. PROBLEMS IDENTIFIED

### 🔴 CRITICAL

1. **Heading Divergence (30–64° error):** DR heading consistently diverges from GPS bearing by 30–64° in all driving tests. The error grows monotonically over time. This is the **root cause** of the massive position errors — if the heading is wrong, the entire velocity vector is projected in the wrong direction.

2. **Massive Position Drift (3–4 km in 3 minutes):** GPS position corrections reach thousands of meters. The DR system is essentially useless for navigation within 2–3 minutes of driving.

3. **Filter Overconfidence:** P_diag converges to ~1.2m while actual position errors are in kilometers. The Kalman filter believes it's accurate to ~1 meter when it's actually off by thousands. This means the filter's noise parameters are **badly miscalibrated**.

### 🟡 MAJOR

4. **Bias Estimation Saturation:** The accelerometer bias estimates saturate at 0.4–0.68 and stop updating. This prevents the filter from correcting the ongoing drift. The saturation limit appears too low or the bias process model is wrong.

5. **Velocity Scale Factor Error:** DR speed is consistently off from GPS speed (underestimating by up to 10 m/s in Test 1, overestimating by up to 10 m/s in Test 3). This suggests the accelerometer scale factor is not calibrated.

6. **Heading Initialization Problem:** The heading jumps from GPS bearing (46.5°) to compass heading (~270°) when the rotation matrix initializes. This 223° jump indicates the initial heading is set from the last GPS movement direction, not the device's physical orientation. For a phone-based DR system, the device orientation ≠ vehicle heading.

### 🟠 MODERATE

7. **Static Drift:** Even when stationary, heading wanders 8.3° and position drifts 0.28m in 35 seconds. The bias grows during static periods instead of being detected as zero motion.

8. **Anomalous Heading Spikes:** Sudden heading jumps of 17–19° occur in Tests 2 and 3 that don't correspond to GPS bearing changes. These may be caused by sensor glitches or rotation matrix discontinuities.

9. **P_diag Floor at ~1.17:** The position uncertainty never drops below ~1.17, even after hundreds of GPS corrections. This suggests the measurement noise covariance R is set too high, or the process noise Q is preventing further convergence.

### 🟢 MINOR

10. **No Zero-Velocity Detection:** The system doesn't detect when the device is stationary and continues to accumulate drift.

11. **GPS Accuracy Degradation:** GPS accuracy starts at 3.8m but degrades to 11–15m in some sessions, which affects correction quality.

---

## 8. RECOMMENDATIONS

### 8.1 Heading — HIGHEST PRIORITY

1. **Add GPS bearing as a direct measurement in the Kalman filter.** Currently, only GPS position and velocity are used for corrections. GPS bearing should be an additional measurement to constrain the heading estimate.

2. **Implement gyro bias calibration during stationary initialization.** Before starting DR, collect 2–5 seconds of gyro data while stationary to estimate and subtract the gyro bias.

3. **Use compass heading for initialization, not GPS bearing.** The device's physical orientation (from compass/magnetometer) should be the initial heading, not the last GPS movement direction. GPS bearing and device heading are different things.

4. **Increase heading process noise.** The heading uncertainty should grow faster to allow larger corrections when GPS bearing is available.

### 8.2 Velocity & Position

5. **Add an accelerometer scale factor state to the Kalman filter.** The consistent speed offset (under/over estimation) suggests a scale factor error. A 3-state bias model (bias + scale factor per axis) would help.

6. **Increase the bias process noise or remove the saturation limit.** The bias saturating at 0.4–0.68 prevents the filter from tracking larger errors. Either increase the saturation limit or use an adaptive process noise model.

7. **Implement zero-velocity detection (ZUPT).** When the device is stationary (low accelerometer variance, zero GPS speed), freeze the position and velocity estimates and only update biases.

### 8.3 Filter Tuning

8. **Recalibrate the measurement noise covariance R.** The P_diag converging to 1.17 while actual errors are in kilometers suggests R is too small (the filter trusts GPS too much) or Q is too small (the filter doesn't allow enough uncertainty growth between updates).

9. **Increase the process noise covariance Q for position states.** The position uncertainty should grow more between GPS updates to reflect the true error accumulation.

10. **Consider an adaptive Kalman filter** that adjusts Q and R based on the innovation (difference between predicted and measured values). When innovations are consistently large, increase Q.

### 8.4 Sensor Handling

11. **Validate the rotation matrix more frequently.** The anomalous heading spikes may indicate the rotation matrix is occasionally incorrect. Add checks for rotation matrix consistency.

12. **Filter gyro and accel data with a low-pass filter** before integration to reduce noise-induced drift.

13. **Log the full rotation matrix** (all 9 elements) periodically to debug orientation issues.

### 8.5 Architecture

14. **Consider a 15-state or 21-state INS/GPS loosely-coupled filter** instead of the current simple model. Standard INS error-state Kalman filters include states for position error, velocity error, attitude error, gyro bias, and accelerometer bias.

15. **Add an observability analysis** to verify that all states are observable given the available measurements (GPS position + velocity only may not make gyro bias observable during straight-line driving).

---

## 9. CONCLUSION

The Dead Reckoning system shows **fundamental issues** that make it unusable for navigation beyond ~30 seconds:

- **Heading error** is the primary failure mode, growing to 30–64° within 2–3 minutes
- **Position error** reaches 3–4 km in the same timeframe
- The **Kalman filter is overconfident**, reporting ~1m uncertainty while actual errors are in kilometers
- **Bias estimation saturates** and stops correcting drift

The most impactful fix would be to **add GPS bearing as a direct measurement** to constrain the heading estimate, combined with **gyro bias calibration** during initialization. Without these fixes, the DR system will continue to diverge rapidly from truth.

The static test shows the core sensor integration works (P_diag converges, corrections are applied), but the driving tests reveal that the system cannot maintain accuracy under dynamic conditions with the current filter configuration.
