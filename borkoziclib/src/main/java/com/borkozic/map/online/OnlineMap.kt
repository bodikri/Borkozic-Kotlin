package com.borkozic.map.online

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import android.view.View
import com.borkozic.map.Map
import com.borkozic.map.TileRAMCache
import com.jhlabs.map.Ellipsoid
import com.jhlabs.map.proj.ProjectionFactory.fromPROJ4Specification
import java.io.IOException
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Карта от онлайн tile източник (OSM, Google, Bing, и др.).
 *
 * Използва Mercator проекция (EPSG:3857) и tile-базирана структура:
 * - Всеки tile е 256×256 пиксела (TILE_WIDTH × TILE_HEIGHT)
 * - Zoom нивата са степени на 2: при zoom N има 2^N × 2^N tile-а
 * - Координатите се конвертират чрез getXYByLatLon / getLatLonByXY
 *
 * Кеширане: TileRAMCache държи до ~4 екрана tile-ове в паметта.
 * TileController управлява асинхронното зареждане от мрежата/диска.
 *
 * @param provider  източник на tile-ове (TileProvider с URL шаблон, max/min zoom)
 * @param z         начално zoom ниво (Byte, 0–20+)
 */
class OnlineMap(provider: TileProvider, z: Byte) : Map("http://...") {

    // ── Tile инфраструктура ──
    /** Управлява зареждането, кеширането и изобразяването на tile-ове */
    private val tileController: TileController

    /** Доставчик на tile изображения (OSM, Google, custom URL) */
    val tileProvider: TileProvider

    // ── Състояние на картата ──
    /** Дали картата е активна (заредена и готова за рисуване) */
    private var isActive = false

    /** Текущо zoom ниво в tile-координати (0–maxZoom) */
    private var srcZoom: Byte

    /** Базово zoom ниво, зададено при създаване — ползва се за изчисляване на zoom фактор */
    private val defZoom: Byte

    init {
        // ── Инициализация на проекция и датум ──
        // Всички онлайн карти ползват WGS84 датум и Mercator проекция
        datum = "WGS84"
        projection = fromPROJ4Specification(
            "+proj=merc".split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        )
        projection!!.setEllipsoid(Ellipsoid.WGS_1984)
        projection!!.initialize()

        // ── Съхраняване на provider и начално zoom ──
        tileProvider = provider
        tileController = TileController()

        // Заглавие на картата: име на provider + zoom ниво
        title = String.format("%s (%d)", tileProvider.name, z)
        srcZoom = z
        defZoom = z
        zoom = 1.0

        // ── Изчисляване на meters-per-pixel (mpp) ──
        // Формула за мащаб на Web Mercator tile:
        //   S = C * cos(φ) / 2^(z + 8)
        // където:
        //   C = обиколка на екватора (equatorRadius * 2π)
        //   φ = географска ширина (0° на екватора за базово изчисление)
        //   z = zoom ниво
        //   +8 = константа от tile системата (256px tile = 2^8)
        //
        // При φ=0 (екватор), cos(0)=1 → максимален мащаб.
        // С увеличаване на ширината, cos(φ) намалява → mpp намалява.
        mpp = projection!!.getEllipsoid().equatorRadius * Math.PI * 2 *
              cos(0.0) / 2.0.pow((srcZoom + 8).toDouble())
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // Активиране / Деактивиране
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Активира картата — инициализира кеш и tile controller.
     *
     * Извиква се когато картата става видима на екрана.
     * Възстановява запазено zoom (savedZoom) ако има такова,
     * или ползва текущото zoom.
     *
     * @param view    Android View за invalidate() при зареждане на tile
     * @param pixels  брой пиксели на екрана — за изчисляване на cache size
     * @throws IOException       при грешка в tile зареждане
     * @throws OutOfMemoryError  при недостатъчна памет за кеш
     */
    @Throws(IOException::class, OutOfMemoryError::class)
    public override fun activate(view: View?, pixels: Int) {
        // Възстановяване на zoom: ако има savedZoom (от предишна сесия), ползвай него
        setZoom(if (savedZoom == 0.0) zoom else savedZoom)
        savedZoom = 0.0

        // Кеш размер: ~4 екрана tile-ове (pixels / tile_size * 4)
        // Това гарантира че при панорамиране има достатъчно буфер
        val cacheSize = (pixels / (TILE_WIDTH * TILE_HEIGHT) * 4)
        cache = TileRAMCache(cacheSize)

        // Свързване на tile controller с view, cache и provider
        tileController.setView(view)
        tileController.setCache(cache)
        tileController.setProvider(tileProvider)
        isActive = true
    }

    /**
     * Деактивира картата — освобождава ресурси.
     *
     * Извиква се когато картата става невидима или приложение минава в background.
     * Прекратява текущи tile заявки, унищожава кеша, запазва zoom за следващо активиране.
     */
    public override fun deactivate() {
        if (!isActive) return
        isActive = false

        // Прекратяване на всички активни tile заявки
        tileController.interrupt()

        // Унищожаване на RAM кеша — освобождава памет
        cache!!.destroy()

        // Запазване на zoom за следващо активиране
        if (savedZoom != 0.0) zoom = savedZoom
        savedZoom = 0.0
        cache = null
    }

    /**
     * Проверява дали картата е активна.
     * @return true ако картата е заредена и готова за рисуване
     */
    public override fun activated(): Boolean {
        return isActive
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // Покритие на координати
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Проверява дали картата покрива дадена географска точка.
     *
     * Web Mercator проекцията покрива ширини между ~±85.051129°.
     * Извън този диапазон проекцията се разминава към безкрайност.
     *
     * Ако картата не е активна, преизчислява mpp за дадената ширина
     * (използва се за предварителна проверка при избор на карта).
     *
     * @param lat  географска ширина в градуси
     * @param lon  географска дължина в градуси
     * @return true ако точката е в обхвата на Mercator проекцията
     */
    public override fun coversLatLon(lat: Double, lon: Double): Boolean {
        if (!isActive) {
            // Преизчисляване на mpp за конкретната ширина
            // (използва се когато картата още не е активна)
            mpp = projection!!.getEllipsoid().equatorRadius * Math.PI * 2 *
                  cos(Math.toRadians(lat)) / 2.0.pow((srcZoom + 8).toDouble())
        }
        // Mercator проекцията работи само между ~±85°
        return lat < 85.051129 && lat > -85.047336
    }

    /**
     * Проверява дали картата покрива текущия екран.
     *
     * За онлайн карти винаги връща true — те покриват целия свят
     * (освен полюсите, но това се проверява в coversLatLon).
     *
     * TODO: Трябва да проверява северния и южния ръб на екрана
     *       за излизане извън Mercator границите.
     */
    public override fun coversScreen(map_xy: IntArray, width: Int, height: Int): Boolean {
        // TODO Should check North and South edges
        return true
    }

    /**
     * Проверява дали картата съдържа дадена област (Bounds).
     *
     * За онлайн карти винаги връща false — те не се използват
     * като adjacent maps (съседни карти) в multi-map режим.
     *
     * FIXME: Може да се разреши за онлайн карти в бъдеще.
     */
    public override fun containsArea(area: Bounds): Boolean {
        // FIXME disabled online maps in adjacent maps
        return false
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // Рисуване на картата
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Рисува онлайн картата върху Canvas.
     *
     * Използва универсален **bounding-box подход** вместо старите 6 отделни
     * code path-а за различни bearing диапазони:
     *
     * **Алгоритъм (5 стъпки):**
     * 1. Взема 4-те ъгъла на екрана в пикселни координати
     *    (спрямо map_xy = lookAhead курсор позиция)
     * 2. Завърта ъглите с bearing чрез 2D rotation matrix около origin
     * 3. Конвертира завъртените пикселни координати в tile индекси
     * 4. Намира min/max tile bounding box + uniform padding (PAD = 1)
     * 5. Итерира всички tile-ове в bounding box-а и ги рисува
     *
     * **Предимства пред стария код:**
     * - lookAhead X и Y участват естествено (не само Y както преди)
     * - Еднакъв padding за всички bearing-и (няма голи ръбове)
     * - Работи за всеки ъгъл 0–360° без специални случаи
     * - ~30 реда вместо ~350
     *
     * **Trade-off:** bounding box зарежда ≤4 излишни tile-а в ъглите
     * (извън завъртяния правоъгълник), но кешът ги държи в RAM.
     *
     * @param bearing     ъгъл на завъртане на картата в градуси (0° = север)
     * @param loc         географска позиция на lookAhead курсора [lat, lon]
     * @param lookAhead   отместване на курсора от центъра на екрана в пиксели [x, y]
     * @param width       ширина на екрана в пиксели
     * @param height      височина на екрана в пиксели
     * @param cropBorder  дали да се изреже border-а (не се използва за онлайн карти)
     * @param drawBorder  дали да се рисува border (не се използва за онлайн карти)
     * @param c           Android Canvas за рисуване
     * @return true ако картата покрива изцяло екрана; false ако излиза извън границите
     * @throws OutOfMemoryError при недостатъчна памет за tile bitmap-и
     */
    @Throws(OutOfMemoryError::class)
    public override fun drawMap(
        bearing: Float,
        loc: DoubleArray,
        lookAhead: IntArray,
        width: Int,
        height: Int,
        cropBorder: Boolean,
        drawBorder: Boolean,
        c: Canvas
    ): Boolean {
        android.util.Log.d("OnlineMap", "drawMap: zoom=$zoom, srcZoom=$srcZoom")
        // ── Стъпка 0: Конвертиране на lat/lon → пикселни координати ──
        // map_xy е позицията на lookAhead курсора в глобалната tile пикселна мрежа.
        // Глобалната мрежа е с размери (2^zoom * TILE_WIDTH) × (2^zoom * TILE_HEIGHT).
        val map_xy = IntArray(2)
        getXYByLatLon(loc[0], loc[1], map_xy)

        // Изваждаме lookAhead отместването — и X, и Y.
        // За разлика от стария код, който ползваше само Y за mt/kt компенсацията,
        // тук и двете компоненти участват в позиционирането.
        map_xy[0] -= lookAhead[0]
        map_xy[1] -= lookAhead[1]

        // ── Стъпка 1: 4-те ъгъла на екрана в пикселни координати ──
        // Координатна система: origin = map_xy (lookAhead курсор),
        // X надясно →, Y надолу ↓ (екранна ориентация)
        val hw = width / 2f   // половина ширина на екрана
        val hh = height / 2f  // половина височина на екрана

        // ── Стъпка 2: Завъртане на ъглите с bearing ──
        // 2D rotation matrix около origin (0,0):
        //   x' = x·cos(θ) − y·sin(θ)
        //   y' = x·sin(θ) + y·cos(θ)
        // bearing е в градуси: 0° = север (екранен Y нагоре ↑),
        // 90° = изток, 180° = юг, 270° = запад.
        val cosB = cos(Math.toRadians(bearing.toDouble()))
        val sinB = sin(Math.toRadians(bearing.toDouble()))

        // 4-те ъгъла на екрана преди завъртане (top-left, top-right, bottom-right, bottom-left)
        // Координатите са спрямо map_xy (origin = център на екрана)
        val corners = arrayOf(
            -hw to -hh,   // горе-ляво  (↖)
             hw to -hh,   // горе-дясно (↗)
             hw to  hh,   // долу-дясно (↘)
            -hw to  hh    // долу-ляво  (↙)
        )

        // Прилагаме rotation matrix към всеки ъгъл
        val rotatedCorners = corners.map { (px, py) ->
            val rx = px * cosB - py * sinB
            val ry = px * sinB + py * cosB
            rx to ry
        }

        // ── Стъпка 3: Конвертиране на завъртени пикселни координати → tile индекси ──
        // Добавяме map_xy (глобалната позиция на курсора) към завъртените
        // пикселни координати и делим на TILE_WIDTH/HEIGHT за tile индекс.
        // Целочисленото деление (/ TILE_WIDTH).toInt() дава tile колона/ред.
        val tileCorners = rotatedCorners.map { (px, py) ->
            ((map_xy[0] + px) / TILE_WIDTH).toInt() to
            ((map_xy[1] + py) / TILE_HEIGHT).toInt()
        }

        // ── Стъпка 4: Bounding box + uniform padding ──
        // PAD = 1 гарантира, че tile-ове които дори частично попадат
        // на екрана ще бъдат заредени. Това решава проблема с "голи ръбове"
        // (дупки по краищата на екрана при завъртане).
        val PAD = 1
        val tMinX = tileCorners.minOf { it.first } - PAD
        val tMaxX = tileCorners.maxOf { it.first } + PAD
        val tMinY = tileCorners.minOf { it.second } - PAD
        val tMaxY = tileCorners.maxOf { it.second } + PAD

        // ── Проверка на границите на света (zoom level) ──
        // При zoom N, валидните tile индекси са [0, 2^N).
        // Ако някой tile индекс излиза извън този диапазон,
        // картата не покрива изцяло екрана → result = false.
        // Това се случва при много малък zoom (цялата земя на един екран)
        // или при панорамиране извън картата.
        val maxTile = 2.0.pow(srcZoom.toDouble()).toInt()
        var result = true
        if (tMinX < 0 || tMinY < 0 || tMaxX >= maxTile || tMaxY >= maxTile) {
            result = false
        }

        // ── Стъпка 5: Итериране и рисуване на всички tile-ове в bounding box-а ──
        // Всеки tile се рисува на екранна позиция, изчислена спрямо центъра:
        //   screenX = width/2 − map_xy[0] + tileX · TILE_WIDTH
        //   screenY = height/2 − map_xy[1] + tileY · TILE_HEIGHT
        //
        // Това позиционира tile-овете така, че map_xy точката
        // (lookAhead курсорът) да е точно в центъра на екрана.
        for (tx in tMinX..tMaxX) {
            for (ty in tMinY..tMaxY) {
                // Екранна позиция на горния ляв ъгъл на tile-а
                val sx = width / 2 - map_xy[0] + tx * TILE_WIDTH
                val sy = height / 2 - map_xy[1] + ty * TILE_HEIGHT

                // Зареждане на tile от кеша/мрежата
                val tile = getTile(tx, ty)
                if (tile != null && !tile.isRecycled()) {
                    // Рисуване на tile bitmap върху Canvas
                    c.drawBitmap(tile, sx.toFloat(), sy.toFloat(), null)
                }
            }
        }

        return result
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // Tile зареждане
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Връща Bitmap за конкретен tile от кеша/мрежата.
     *
     * TileController управлява асинхронното зареждане:
     * 1. Проверява RAM кеш (TileRAMCache)
     * 2. Ако го няма — проверява дисков кеш
     * 3. Ако го няма — зарежда от мрежата (HTTP)
     *
     * @param x  tile колона (0 → 2^zoom − 1)
     * @param y  tile ред (0 → 2^zoom − 1)
     * @return Bitmap на tile-а, или null ако още не е зареден
     */
    fun getTile(x: Int, y: Int): Bitmap? {
        val tile = tileController.getTile(x, y, srcZoom)
        return tile.bitmap
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // Граници на картата
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Връща географските граници на картата.
     *
     * За онлайн карти (Web Mercator), границите са фиксирани:
     * - Ширина: ±85.051129° (Mercator проекцията не покрива полюсите)
     * - Дължина: ±180° (целият свят)
     *
     * @return Bounds обект с min/max lat/lon
     */
    public override fun getBounds(): Bounds {
        if (bounds == null) {
            bounds = Bounds()
            bounds.minLat = -85.047336
            bounds.maxLat = 85.051129
            bounds.minLon = -180.0
            bounds.maxLon = 180.0
        }
        return bounds
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // Координатни трансформации
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Конвертира пикселни координати → географски координати (lat/lon).
     *
     * Обратна трансформация на Mercator проекцията.
     * Поддържа два режима в зависимост от tileProvider.ellipsoid:
     *
     * **Елипсоиден режим (ellipsoid = true):**
     * Използва итеративен метод за обратно изчисление с елипсоида WGS84
     * (ексцентрицитет e = 0.0818197). Конвергира за ≤100 000 итерации
     * с точност 0.0000001 радиана.
     *
     * **Сферичен режим (ellipsoid = false):**
     * Използва опростена формула със sinh (хиперболичен синус).
     * По-бърз, но по-малко точен за големи ширини.
     *
     * @param x   пикселна X координата в глобалната мрежа
     * @param y   пикселна Y координата в глобалната мрежа
     * @param ll  изходен масив [lat, lon] — резултатът се записва тук
     * @return true (винаги успешно за онлайн карти)
     */
    public override fun getLatLonByXY(x: Int, y: Int, ll: DoubleArray): Boolean {
        // Нормализиране на пикселните координати към [0, 1] диапазон
        val dx: Double = x * 1.0 / TILE_WIDTH
        val dy: Double = y * 1.0 / TILE_HEIGHT

        // n = 2^zoom — брой tile-ове по всяка ос
        val n = 2.0.pow(srcZoom.toDouble())

        if (tileProvider.ellipsoid) {
            // ── Елипсоиден режим (WGS84) ──
            // Първоначално приближение чрез сферична формула
            ll[0] = (y - TILE_HEIGHT * n / 2) / -(TILE_HEIGHT * n / (2 * Math.PI))
            ll[0] = (2 * atan(exp(ll[0])) - Math.PI / 2) * 180 / Math.PI

            // Итеративно прецизиране с елипсоидна корекция
            // e = 0.0818197 = ексцентрицитет на WGS84 елипсоида
            var Zu = Math.toRadians(ll[0])
            var Zum1 = Zu + 1
            val yy: Double = (y - TILE_HEIGHT * n / 2)
            var i = 100000  // max итерации (предпазване от безкраен цикъл)
            while ((abs(Zum1 - Zu) > 0.0000001) && (i != 0)) {
                i--
                Zum1 = Zu
                // Итеративна формула за обратна Mercator с елипсоид:
                // Zu = asin(1 − ((1+sin(Zum1))·(1−e·sin(Zum1))^e) /
                //          (exp(2·yy/(−R)) · (1+e·sin(Zum1))^e))
                Zu = asin(
                    1 - ((1 + sin(Zum1)) * (1 - 0.0818197 * sin(Zum1)).pow(0.0818197))
                            / (exp((2 * yy) / -(TILE_HEIGHT * n / (2 * Math.PI))) *
                               (1 + 0.0818197 * sin(Zum1)).pow(0.0818197))
                )
            }
            ll[0] = Math.toDegrees(Zu)
        } else {
            // ── Сферичен режим (опростен) ──
            // Формула: lat = arctan(sinh(π · (1 − 2·dy/n)))
            ll[0] = Math.toDegrees(atan((sinh(Math.PI * (1 - 2 * dy / n)))))
        }

        // Дължината е линейна в Mercator:
        // lon = dx · 360° / n − 180°
        ll[1] = dx * 360.0 / n - 180.0

        return true
    }

    /**
     * Конвертира географски координати (lat/lon) → пикселни координати.
     *
     * Права Mercator проекция. Поддържа два режима:
     *
     * **Елипсоиден режим (ellipsoid = true):**
     * y = (1 − (atanh(sin φ) − e·atanh(e·sin φ)) / π) / 2 · n · TILE_HEIGHT
     *
     * **Сферичен режим (ellipsoid = false):**
     * y = (1 − ln(tan φ + sec φ) / π) / 2 · n · TILE_HEIGHT
     *
     * @param lat  географска ширина в градуси
     * @param lon  географска дължина в градуси
     * @param xy   изходен масив [x, y] — резултатът се записва тук
     * @return true (винаги успешно за онлайн карти)
     */
    public override fun getXYByLatLon(lat: Double, lon: Double, xy: IntArray): Boolean {
        // n = 2^zoom — брой tile-ове по всяка ос
        val n = 2.0.pow(srcZoom.toDouble())

        // X координата: линейна трансформация на дължината
        // x = floor((lon + 180°) / 360° · n · TILE_WIDTH)
        xy[0] = floor((lon + 180.0) / 360.0 * n * TILE_WIDTH).toInt()

        if (tileProvider.ellipsoid) {
            // ── Елипсоиден режим (WGS84) ──
            // z = sin(φ)
            // y = floor((1 − (atanh(z) − e·atanh(e·z)) / π) / 2 · n · TILE_HEIGHT)
            val z = sin(Math.toRadians(lat))
            xy[1] = floor(
                (1 - (atanh(z) - 0.0818197 * atanh(0.0818197 * z)) / Math.PI) / 2 *
                n * TILE_HEIGHT
            ).toInt()
        } else {
            // ── Сферичен режим (опростен) ──
            // y = floor((1 − ln(tan φ + sec φ) / π) / 2 · n · TILE_HEIGHT)
            xy[1] = floor(
                (1 - (ln(tan(Math.toRadians(lat)) + 1 / cos(Math.toRadians(lat))) /
                      Math.PI)) / 2 * n * TILE_HEIGHT
            ).toInt()
        }
        return true
    }

    /**
     * Конвертира lat/lon → OSM tile индекси (колона, ред).
     *
     * Разликата с getXYByLatLon: тук връща **tile индекс** (0 → 2^zoom−1),
     * а не пикселна координата. Използва се за директно адресиране на tile-ове
     * в OSM URL схемата: /zoom/x/y.png
     *
     * @param lat  географска ширина в градуси
     * @param lon  географска дължина в градуси
     * @param xy   изходен масив [tileX, tileY] — резултатът се записва тук
     * @return true
     */
    fun getOsmXYByLatLon(lat: Double, lon: Double, xy: IntArray): Boolean {
        val n = 2.0.pow(srcZoom.toDouble())

        // X tile индекс: floor((lon + 180) / 360 * n)
        xy[0] = floor((lon + 180) / 360 * n).toInt()
        // Корекция: при lon = 180°, x = n (извън диапазона) → x = n−1
        if (xy[0].toDouble() == n) xy[0] -= 1

        if (tileProvider.ellipsoid) {
            val z = sin(Math.toRadians(lat))
            xy[1] = floor(
                (1 - (atanh(z) - 0.0818197 * atanh(0.0818197 * z)) / Math.PI) / 2 * n
            ).toInt()
        } else {
            xy[1] = floor(
                (1 - ln(tan(Math.toRadians(lat)) + 1 / cos(Math.toRadians(lat))) /
                 Math.PI) / 2 * n
            ).toInt()
        }
        // Y не може да е отрицателен (при lat > 85.051129° би излязло)
        if (xy[1] < 0) xy[1] = 0
        return true
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // Zoom управление
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Връща zoom фактора за следващото (по-детайлно) zoom ниво.
     *
     * Zoom факторът е относителен спрямо defZoom (базовото zoom):
     *   nextZoom = 2^(srcZoom + 1 − defZoom)
     *
     * Ако текущото zoom е максималното (tileProvider.maxZoom),
     * връща 0.0 — няма по-детайлно ниво.
     *
     * @return zoom фактор (2^разлика), или 0.0 ако е max zoom
     */
    public override fun getNextZoom(): Double {
        if (srcZoom >= tileProvider.maxZoom) return 0.0
        // Log.e("ONLINE", "Next zoom: " + 2.0.pow((this.srcZoom + 1 - defZoom).toDouble()))
        return 2.0.pow((this.srcZoom + 1 - defZoom).toDouble())
    }

    /**
     * Връща zoom фактора за предишното (по-общо) zoom ниво.
     *
     *   prevZoom = 2^(srcZoom − 1 − defZoom)
     *
     * Ако текущото zoom е минималното (tileProvider.minZoom),
     * връща 0.0 — няма по-общо ниво.
     *
     * @return zoom фактор, или 0.0 ако е min zoom
     */
    public override fun getPrevZoom(): Double {
        if (srcZoom <= tileProvider.minZoom) return 0.0
        // Log.e("ONLINE", "Prev zoom: " + 2.0.pow((this.srcZoom - 1 - defZoom).toDouble()))
        return 2.0.pow((this.srcZoom - 1 - defZoom).toDouble())
    }

    /**
     * Връща текущия zoom фактор.
     * @return zoom (1.0 = базово ниво, 2.0 = 2× увеличение, 0.5 = 2× намаление)
     */
    public override fun getZoom(): Double {
        return zoom
    }

    /**
     * Задава нов zoom фактор и преизчислява srcZoom.
     *
     * Алгоритъм:
     * 1. Изчислява разликата в zoom нива: zDiff = ln(z) / ln(2)
     *    (натурален логаритъм — колко степени на 2 сме от defZoom)
     * 2. srcZoom = defZoom + zDiff
     * 3. Ограничава srcZoom в [minZoom, maxZoom] на tileProvider
     * 4. Коригира zDiff спрямо реалното srcZoom
     * 5. Обновява zoom фактора и заглавието
     * 6. Reset-ва tileController (изчиства кеша за старото zoom)
     *
     * @param z  нов zoom фактор (1.0 = базово, 2.0 = 2×, 0.5 = ½×)
     */
    public override fun setZoom(z: Double) {
        android.util.Log.d("OnlineMap", "setZoom: z=$z, before zoom=$zoom, srcZoom=$srcZoom, defZoom=$defZoom")
        // Изчисляване на разликата в zoom нива чрез натурален логаритъм
        // zDiff = колко нива сме от defZoom (положително = по-детайлно, отрицателно = по-общо)
        var zDiff = (ln(z) / ln(2.0)).toInt()
        // Log.e("ONLINE", "Zoom: " + z + " diff: " + zDiff)

        // Ново абсолютно zoom ниво
        srcZoom = (defZoom + zDiff).toByte()

        // Ограничаване в допустимия диапазон на tileProvider
        if (srcZoom > tileProvider.maxZoom) {
            zDiff -= srcZoom - tileProvider.maxZoom
            srcZoom = tileProvider.maxZoom
        }
        if (srcZoom < tileProvider.minZoom) {
            zDiff -= srcZoom - tileProvider.minZoom
            srcZoom = tileProvider.minZoom
        }

        // Запазване на реалния zoom фактор
        zoom = z
        android.util.Log.d("OnlineMap", "setZoom: AFTER, zoom=$zoom, srcZoom=$srcZoom, zDiff=$zDiff")
        // Log.e("ONLINE", "z: " + srcZoom + " zoom: " + zoom + " diff: " + zDiff)

        // Нулиране на tile controller — изчиства кеша и презарежда с новото zoom
        tileController.reset()

        // Обновяване на заглавието с новото zoom ниво
        title = String.format("%s (%d)", tileProvider.name, srcZoom)
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // Размери на картата
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Мащабирана ширина на картата в пиксели.
     *
     * Формула: 2^srcZoom · TILE_WIDTH · zoom
     *
     * При zoom=1.0: 2^srcZoom · 256 (реалния размер на tile мрежата)
     * При zoom=2.0: 2 пъти по-голяма (по-детайлно)
     */
    override val scaledWidth: Int
        get() = (2.0.pow(srcZoom.toDouble()) * TILE_WIDTH * zoom).toInt()

    /**
     * Мащабирана височина на картата в пиксели.
     *
     * Същата формула като scaledWidth, но с TILE_HEIGHT.
     */
    override val scaledHeight: Int
        get() = (2.0.pow(srcZoom.toDouble()) * TILE_HEIGHT * zoom).toInt()

    // ═══════════════════════════════════════════════════════════════════════════════
    // Информация за картата
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Връща списък с информационни редове за картата.
     *
     * Използва се за debug и показване на информация в UI.
     * Включва: заглавие, проекция (име, EPSG код, PROJ4 описание),
     * датум, мащаб (mpp).
     *
     * @return списък от String-ове с информация
     */
    public override fun info(): List<String> {
        val info = ArrayList<String>()

        info.add("title: " + title)
        if (projection != null) {
            info.add("projection: " + prjName + " (" + projection!!.getEPSGCode() + ")")
            info.add("\t" + projection!!.pROJ4Description)
        }
        info.add("datum: " + datum)
        info.add("scale (mpp): " + mpp)

        return info
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // Companion object — константи и utility функции
    // ═══════════════════════════════════════════════════════════════════════════════

    companion object {
        /** Serial version UID за съвместимост с Java serialization */
        private const val serialVersionUID = 1L

        /**
         * Ширина на един tile в пиксели.
         *
         * Стандартна стойност за OSM/Slippy Map tile системата.
         * Всеки tile е квадрат 256×256 px.
         */
        const val TILE_WIDTH: Int = 256

        /**
         * Височина на един tile в пиксели.
         *
         * Стандартна стойност за OSM/Slippy Map tile системата.
         */
        const val TILE_HEIGHT: Int = 256

        /**
         * Изчислява обратния хиперболичен тангенс (atanh).
         *
         * Формула: atanh(x) = ½ · ln((1 + x) / (1 − x))
         *
         * Дефиниран за |x| < 1. Използва се в Mercator проекцията
         * с елипсоидна корекция за WGS84.
         *
         * @param arg  аргумент (|arg| < 1)
         * @return atanh(arg)
         */
        private fun atanh(arg: Double): Double {
            return 0.5 * ln((1 + arg) / (1 - arg))
        }
    }
}
