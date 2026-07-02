# 2026-06-28 — Навигационни fix-ове

## Проблем 1: Tap на точка от картата → "Navigate" не прави нищо

### Симптоми
- Тап на waypoint от картата → QuickAction меню → "Navigate"
- NavigationService стартира (логва `ensureForeground`, `Location service connected`)
- Но жълтата линия и info лентата не се появяват
- При вече стартирана навигация (превключване от маршрут към точка) работи

### Диагноза
`qNavigateToWaypoint` в MapActivity викаше `navigationService?.navigateTo(wpt)`. При **първа** навигация `navigationService` е `null` (service още не е bind-нат). `?.` safe call безопасно прескача извикването → нищо не се случва.

След рестарт service е вече bind-нат → `?.` работи → затова превключването работи.

### Fix
Когато `navigationService` е null, стартираме NavigationService чрез `NAVIGATE_MAPOBJECT` intent (същият механизъм като оригиналния Java код). Service се стартира, bind-ва се, и обработва навигацията в `onStartCommand()`.

```kotlin
val svc = navigationService
if (svc != null) {
    svc.navigateTo(wpt)              // Service вече bind-нат
} else {
    // Service не е bind-нат — старт чрез intent
    val intent = Intent(this@MapActivity, NavigationService::class.java)
    intent.action = NavigationService.NAVIGATE_MAPOBJECT
    intent.putExtra(NavigationService.EXTRA_NAME, wpt.name)
    intent.putExtra(NavigationService.EXTRA_LATITUDE, wpt.latitude)
    intent.putExtra(NavigationService.EXTRA_LONGITUDE, wpt.longitude)
    intent.putExtra(NavigationService.EXTRA_PROXIMITY, wpt.proximity)
    startService(intent)
}
```

**Commit:** `290084a`

---

## Проблем 2: Broadcast-ите не достигат MapActivity

### Симптоми
- NavigationService изпраща `State dispatched: 1` и `Status dispatched`
- MapActivity не логва `Broadcast:` — receiver-ът не получава нищо
- Жълтата линия и info лентата не се обновяват

### Диагноза
На **Android 13+ (Tiramisu)**, `registerReceiver()` с `RECEIVER_NOT_EXPORTED` изисква broadcast-ите да са **explicit** (с set package). `sendBroadcast(Intent(action))` е implicit broadcast → не се доставя до `RECEIVER_NOT_EXPORTED` receivers.

### Fix
Добавен `.setPackage(packageName)` към всички broadcast-и в NavigationService:

```kotlin
// Преди (implicit — не се доставя на Android 13+):
sendBroadcast(Intent(BROADCAST_NAVIGATION_STATE).putExtra("state", state))

// След (explicit — работи навсякъде):
sendBroadcast(Intent(BROADCAST_NAVIGATION_STATE).putExtra("state", state).setPackage(packageName))
```

**Commit:** `01b5a05`

---

## Проблем 3: Info лентата не се изчиства при Stop

### Симптоми
- При спиране на навигацията (Stop бутона) info лентата горе остава видима
- Жълтата линия остава на картата

### Диагноза
`stopNavigation()` изпраща `STATE_STOPED` broadcast, но service-ът се убива (`stopForeground`, `disconnect`) преди broadcast-ът да достигне MapActivity. Без да получи broadcast, MapActivity не извиква `updateNavigationStatus()` → UI-то не се изчиства.

### Fix
Добавен директен `updateNavigationStatus()` call веднага след `stopNavigation()`:

```kotlin
R.id.menuStopNavigation -> {
    navigationService!!.stopNavigation()
    updateNavigationStatus()  // ← директно изчистване, не чакаме broadcast
    return true
}
```

**Commit:** `e5d53a7`

---

## Обобщение

| # | Проблем | Причина | Fix | Commit |
|---|---|---|---|---|
| 1 | Navigate от QuickAction не работи | `navigationService?.navigateTo()` прескача при null | Start service чрез intent | `290084a` |
| 2 | Broadcasts не достигат MapActivity | Implicit broadcast + RECEIVER_NOT_EXPORTED | `.setPackage(packageName)` | `01b5a05` |
| 3 | Info лентата не се изчиства при Stop | Service убит преди broadcast | Директен `updateNavigationStatus()` | `e5d53a7` |

## Бележки
- Всички коментари в кода са на български
- Debug логовете са премахнати в commit `8496659`
- Тествано на емулатор — всичко работи коректно