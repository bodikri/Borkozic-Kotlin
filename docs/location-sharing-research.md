# Location Sharing — Проучване и Архитектура

> Дата: 2026-06-28
> Статус: Проучено, чака решение

## Източници
- Оригинален plugin: `D:\Borkozic_Versions\LocationShare\borkozik-plugin-locationshare3d\`
- Сървър: `D:\Borkozic_Versions\LocationShare\androzic.com\uwsgi\loc.py`
- Kotlin_Oki мигрирани: `borkozic/src/main/java/com/borkozic/location/share/`

---

## Текущо състояние в Kotlin_Oki

### ✅ Мигрирани файлове
| Файл | Описание |
|---|---|
| `SharingService.kt` | Foreground service, timer-based HTTP polling |
| `LocationShareClient.kt` | Interface + AndrozicLocationShareClient (HTTP GET) |
| `SituationListActivity.kt` | Списък на потребителите в сесията |
| `Situation.kt` | Data model (lat, lon, speed, track, alt, time, name) |
| `pref_sharing.xml` / `sharing_preferences.xml` | Settings XML |
| `act_userlist.xml` / `situation_list_item.xml` | Layouts |
| `situation_list.xml` / `situation_popup.xml` | Menus |
| Strings в `strings.xml` | Всички sharing-related strings |
| Manifest | SituationListActivity + SharingService декларирани |

### 🔴 Какво НЕ работи
1. **SharingService.kt не добавя ситуации към картата** — има `createSituationBitmap()` но никога не извиква `addMapObject()`. Липсва връзката между получените ситуации и MapView.
2. **Сървърът androzic.com е офлайн** — AndrozicLocationShareClient праща към `http://androzic.com/cgi-bin/loc.cgi` който не съществува.
3. **Няма SituationOverlay** — разчита се на MapObjectsOverlay чрез addMapObject, но никой не подава данни.
4. **Няма Settings checkbox за enable/disable** — има `pref_sharing` в preferences.xml, но няма toggle в pref_location.xml или pref_sharing.xml.
5. **Липсват някои key strings** — `pref_sharing_session` и `pref_sharing_user` като key strings (не само title/summary).

---

## Оригинален протокол (Androzic)

### Client → Server
```
GET http://androzic.com/cgi-bin/loc.cgi?session=XXX;user=YYY;lat=42.1;lon=23.4;speed=45;track=90;ftime=1719561234000;altitude=1500
```

### Server → Client (JSON response)
```json
{
  "session-key": "XXX",
  "users": [
    {
      "id": 123,
      "user": "Pilot1",
      "lat": 42.2,
      "lon": 23.5,
      "speed": 50,
      "track": 85,
      "elevation": 1600,
      "accuracy": 3,
      "ftime": 1719561234000
    }
  ]
}
```

### Server (loc.py)
- Python WSGI, MySQL backend
- INSERT на всяко request (insert + select last per user)
- Timeout: потребители без update за N минути се показват като "lost"
- Test mode: fakerotator (Москва, Melbourne, Montréal)

---

## Предложение: MQTT с OwnTracks-съвместим протокол

### Защо MQTT за авиация
- **Real-time push** — веднага, не на 10с polling. При 120-800 km/h, 10с = 330-2200m изминато
- **Лек** — MQTT keepalive ~1KB/30с, минимална консумация на батерия
- **Работи през 4G/3G** — стабилна връзка дори при слаб сигнал
- **TLS криптиране** — сигурност
- **QoS 1** — гарантирана доставка
- **Стандарт** — OwnTracks протокол, огромна екосистема

### Архитектура

```
Телефон A ──┐                    ┌── Телефон B
            │  MQTT (TLS)        │
            ├──→ Mosquitto ←─────┤
            │    broker (VPS)    │
Телефон C ──┘                    └── Телефон D

Topic structure:
  borkozic/{session}/{user}     → publish own position
  borkozic/{session}/#           → subscribe for all users

Payload (JSON, OwnTracks-compatible):
{
  "_type": "location",
  "lat": 42.123,
  "lon": 23.456,
  "alt": 1500.0,
  "vel": 45,        // m/s
  "bse": 90,         // bearing
  "tst": 1719561234  // epoch seconds
}
```

### Сървър: Mosquitto в Docker

```yaml
# docker-compose.yml
services:
  mosquitto:
    image: eclipse-mosquitto
    restart: unless-stopped
    ports:
      - "8883:8883"   # TLS
      - "9001:9001"   # WebSocket (optional)
    volumes:
      - ./mosquitto.conf:/mosquitto/config/mosquitto.conf
      - ./data:/mosquitto/data
      - ./log:/mosquitto/log
```

```conf
# mosquitto.conf
listener 8883
protocol mqtt
tls_version tlsv1.2
certfile /mosquitto/certs/fullchain.pem
keyfile /mosquitto/certs/privkey.pem
allow_anonymous false
password_file /mosquitto/passwd
```

### Промени в приложението

| # | Какво | Агент | Оценка | Зависимости |
|---|---|---|---|---|
| 1 | `MqttLocationShareClient` — имплементира `LocationShareClient` с Paho MQTT | Моки 🔄 | ~200 реда | Paho library в build.gradle |
| 2 | `SharingService.kt` — замени timer polling с MQTT subscribe + addMapObject при нова ситуация | Моки 🔄 | ~100 реда промяна | #1 готов |
| 3 | Settings — добави MQTT broker URL + port + TLS toggle + username/password | Моки 🔄 | ~30 реда XML | — |
| 4 | SituationOverlay — директен overlay за ситуации (вместо MapObject hack) | Мапи 🗺️ | ~150 реда | MapOverlay базов клас |
| 5 | Mosquitto конфиг — docker-compose + mosquitto.conf + passwd | Коки | ~20 реда | VPS |
| 6 | Fix SharingService — addMapObject() при нова ситуация, removeMapObject() при изчезнал | Моки 🔄 | ~50 реда | #2 готов |
| 7 | Build.gradle — добави `org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5` | Коки | 1 ред | — |

### Paho MQTT Library

```gradle
dependencies {
    implementation 'org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5'
}
```

⚠️ Paho е ~200KB. Ако е проблем, може да се ползва HiveMQ MQTT client (по-лек, pure Kotlin).

### MqttLocationShareClient — псевдокод

```kotlin
class MqttLocationShareClient(
    private val brokerUrl: String,   // ssl://server:8883
    private val username: String,
    private val password: String
) : LocationShareClient {

    private var mqttClient: MqttAsyncClient? = null

    fun connect(session: String, onSituation: (Situation) -> Unit) {
        mqttClient = MqttAsyncClient(brokerUrl, MqttClient.generateClientId())
        val options = MqttConnectOptions().apply {
            userName = username
            password = password.toCharArray()
            isAutomaticReconnect = true
            isCleanSession = true
            connectionTimeout = 10
            keepAliveInterval = 30
        }
        mqttClient?.connect(options)
        // Subscribe към всички потребители в сесията
        mqttClient?.subscribe("borkozic/$session/#", 1) { topic, msg ->
            val user = topic.split("/").last()
            val json = JSONObject(msg.toString())
            val sit = Situation().apply {
                name = user
                latitude = json.getDouble("lat")
                longitude = json.getDouble("lon")
                altitude = json.getDouble("alt")
                speed = json.getDouble("vel")
                track = json.getDouble("bse")
                time = json.getLong("tst") * 1000
            }
            onSituation(sit)
        }
    }

    override fun shareAndFetch(...): List<Situation> {
        // Publish own position
        val payload = JSONObject().apply {
            put("_type", "location")
            put("lat", lat)
            put("lon", lon)
            put("alt", altitude)
            put("vel", speed)
            put("bse", track)
            put("tst", ftime / 1000)
        }
        mqttClient?.publish("borkozic/$session/$user",
            payload.toString().toByteArray(), 1, false)
        // Situations се получават асинхронно чрез callback
        // (не се връщат тук — това променя интерфейса)
    }
}
```

### Важна архитектурна бележка

Моделът на LocationShareClient е синхронен (`shareAndFetch` връща `List<Situation>`).
MQTT е асинхронен (push-based). Два варианта:

**Вариант 1:** Промени interface-а да е callback-based (`onSituationReceived(sit)`)
- По-чист, по-правилен за MQTT
- Breaking change — трябва да се промени SharingService

**Вариант 2:** Запази синхронния интерфейс — MqttClient събира ситуации в queue, shareAndFetch ги източва
- По-малко промени
- Малко по-хакнато

Препоръка: Вариант 1 (callback-based) — по-правилно за real-time.

---

## Алтернативи (ако MQTT не се хареса)

### Опция A: Олекотен HTTP сървър
- Минимален Python Flask / Node.js Express сървър с SQLite
- Същият протокол: HTTP GET за share + fetch
- Deploy на VPS или Raspberry Pi
- Плюс: Минимални промени в приложението
- Минус: Polling на 10с, не е real-time

### Опция C: P2P без сървър (BLE/WiFi Direct)
- Плюс: Няма сървър, няма разходи
- Минус: Ограничен обхват (~100m BLE, ~50m WiFi Direct) — непригодно за авиация

---

## Open Questions (за обсъждане)

1. **VPS?** Имаме ли VPS където да сложим Mosquitto?
2. **TLS сертификати?** Let's Encrypt или self-signed?
3. **Authentication?** Username/password per session или global?
4. **Privacy?** Координати шифрирани in-transit (TLS) — достатъчно ли е?
5. **Offline behavior?** Какво да прави приложението ако няма връзка със сървъра?
6. **SituationOverlay vs MapObject?** Директен overlay е по-чист, но повече код.

---

## Референции

- OwnTracks JSON: https://owntracks.org/booklet/tech/json/
- OwnTracks MQTT: https://owntracks.org/booklet/tech/mqtt/
- OwnTracks Topics: https://owntracks.org/booklet/guide/topics/
- Mosquitto: https://mosquitto.org/
- Paho MQTT Android: https://github.com/eclipse/paho.mqtt.android
- HiveMQ MQTT Client (Kotlin): https://github.com/hivemq/hivemq-mqtt-client