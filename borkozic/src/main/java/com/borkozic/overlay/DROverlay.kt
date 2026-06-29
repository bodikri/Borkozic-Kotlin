package com.borkozic.overlay

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Canvas
import com.borkozic.Borkozic
import com.borkozic.MapView
import com.borkozic.data.Track

/**
 * DROverlay — визуализира Dead Reckoning изчислената позиция на картата.
 *
 * - Сива следа (trail) от всички изчислени DR позиции (чрез TrackOverlay)
 * - Сиво самолетче на последната DR позиция (същият plane icon, преоцветен в сиво)
 * - Следата НЕ се маха при DR стоп — само спира да добавя нови точки
 * - При нов DR старт започва нова следа (clear() + ново име)
 *
 * Интеграция:
 * - Добавя се в Borkozic.overlays списъка
 * - При BROADCAST_DR_LOCATION → addPoint(lat, lon, alt, speed, bearing, time)
 * - При DR стоп → overlay-ят остава, просто спира да получава нови точки
 */
class DROverlay(mapActivity: Activity) : TrackOverlay(mapActivity) {

    // Последна DR позиция за чертане на триъгълниче
    private var lastDRLat: Double = Double.NaN
    private var lastDRLon: Double = Double.NaN
    private var lastDRBearing: Float = 0f
    private var lastDRXY: IntArray = intArrayOf(0, 0)

    // Paint за сивото триъгълниче
    private val trianglePaint = android.graphics.Paint().apply {
        color = 0xFF888888.toInt()
        style = android.graphics.Paint.Style.FILL
        isAntiAlias = true
    }
    private val triangleOutline = android.graphics.Paint().apply {
        color = 0xFF666666.toInt()
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 1.5f
        isAntiAlias = true
    }

    // Флаг дали overlay-ят е активен (получава нови точки)
    @Volatile
    var isActive: Boolean = false
        private set

    // Брояч за именуване на следите
    private var sessionNumber: Int = 0

    init {
        // Сив цвят за следата
        paint.color = 0xFF888888.toInt()
        paint.strokeWidth = 2.5f
        paint.isAntiAlias = true
        paint.style = android.graphics.Paint.Style.STROKE

        track.name = "DR Track"
        track.show = true
        track.color = 0xFF888888.toInt()
        track.width = 2

        enabled = true
    }

    /**
     * Стартира нова DR сесия — изчиства предишната следа и започва нова.
     */
    fun startNewSession() {
        sessionNumber++
        track.clear()
        track.name = "DR Track #$sessionNumber"
        isActive = true
    }

    /**
     * Спира активната DR сесия — overlay-ят остава видим, но не добавя нови точки.
     */
    fun stopSession() {
        isActive = false
    }

    /**
     * Добавя DR точка към следата и обновява позицията на сивото самолетче.
     */
    fun addDRPoint(lat: Double, lon: Double, alt: Double, speed: Float, bearing: Float, time: Long) {
        if (!isActive) return
        track.addPoint(true, lat, lon, alt, speed.toDouble(), bearing.toDouble(), 0.0, time)
        lastDRLat = lat
        lastDRLon = lon
        lastDRBearing = bearing
    }

    /**
     * Обновява XY координатите на последната DR позиция (вика се при onMapChanged).
     */
    private fun updateDRXY() {
        if (lastDRLat.isNaN() || lastDRLon.isNaN()) return
        val app = context.application as? Borkozic ?: return
        lastDRXY = app.getXYbyLatLon(lastDRLat, lastDRLon)
    }

    override fun onMapChanged() {
        super.onMapChanged()
        updateDRXY()
    }

    override fun onDraw(c: Canvas, mapView: MapView, centerX: Int, centerY: Int) {
        // Чертане на сивата следа (чрез parent TrackOverlay)
        super.onDraw(c, mapView, centerX, centerY)
    }

    override fun onDrawFinished(c: Canvas, mapView: MapView, centerX: Int, centerY: Int) {
        // Чертане на сиво триъгълниче на последната DR позиция
        if (!isActive || lastDRLat.isNaN() || lastDRLon.isNaN()) return

        val cxy = mapView.mapCenterXY
        val x = (lastDRXY[0] - cxy[0]).toFloat()
        val y = (lastDRXY[1] - cxy[1]).toFloat()

        // Проверка дали е в рамките на екрана
        if (x < -mapView.width / 2 || x > mapView.width / 2 ||
            y < -mapView.height / 2 || y > mapView.height / 2) return

        c.save()
        c.translate(x, y)

        // Завъртане според bearing (ако е Track Up, триъгълничето сочи нагоре без завъртане)
        if (!mapView.isTrackUp) {
            c.rotate(lastDRBearing)
        }

        // Триъгълниче: връх нагоре (0° bearing = север/нагоре)
        val size = 12f
        val path = android.graphics.Path()
        path.moveTo(0f, -size)           // връх
        path.lineTo(-size * 0.7f, size * 0.7f)  // долен ляв
        path.lineTo(size * 0.7f, size * 0.7f)   // долен десен
        path.close()

        c.drawPath(path, trianglePaint)
        c.drawPath(path, triangleOutline)

        c.restore()
    }

    override fun onPreferencesChanged(settings: SharedPreferences) {
        // Не викаме super — не искаме да презаписва цвета/ширината от prefs
        // Сивият цвят е фиксиран
    }
}
