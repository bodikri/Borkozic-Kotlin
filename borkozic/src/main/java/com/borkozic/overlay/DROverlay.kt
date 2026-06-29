package com.borkozic.overlay

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.borkozic.Borkozic
import com.borkozic.MapView
import com.borkozic.R
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

    // Последна DR позиция за чертане на самолетче
    private var lastDRLat: Double = Double.NaN
    private var lastDRLon: Double = Double.NaN
    private var lastDRBearing: Float = 0f
    private var lastDRXY: IntArray = intArrayOf(0, 0)

    // Сив plane icon — същият като основния, но с GRAY color filter
    private var grayPlaneIcon: Drawable? = null
    private var planeIconSize: Int = 100 // default

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

    /**
     * Зарежда сивия plane icon според текущите настройки.
     */
    fun updatePlaneIcon(planeLogo: String?, planeLogoSize: Int) {
        this.planeIconSize = planeLogoSize
        val resId = when (planeLogo) {
            "MiG29" -> when (planeLogoSize) {
                60 -> R.drawable.pic_mig29_60
                80 -> R.drawable.pic_mig29_80
                100 -> R.drawable.pic_mig29_100
                120 -> R.drawable.pic_mig29_120
                140 -> R.drawable.pic_mig29_140
                160 -> R.drawable.pic_mig29_160
                else -> R.drawable.pic_mig29
            }
            "L39" -> when (planeLogoSize) {
                60 -> R.drawable.pic_l39_60
                80 -> R.drawable.pic_l39_80
                100 -> R.drawable.pic_l39_100
                120 -> R.drawable.pic_l39_120
                140 -> R.drawable.pic_l39_140
                160 -> R.drawable.pic_l39_160
                else -> R.drawable.pic_l39
            }
            else -> when (planeLogoSize) {
                60 -> R.drawable.pic_mig29_60
                80 -> R.drawable.pic_mig29_80
                100 -> R.drawable.pic_mig29_100
                120 -> R.drawable.pic_mig29_120
                140 -> R.drawable.pic_mig29_140
                160 -> R.drawable.pic_mig29_160
                else -> R.drawable.pic_mig29
            }
        }
        grayPlaneIcon = ContextCompat.getDrawable(context, resId)
        // Преоцветяване в сиво
        grayPlaneIcon?.colorFilter = PorterDuffColorFilter(0xFF888888.toInt(), PorterDuff.Mode.SRC_IN)
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
        // Чертане на сивото самолетче на последната DR позиция
        if (!isActive || lastDRLat.isNaN() || lastDRLon.isNaN()) return
        val icon = grayPlaneIcon ?: return

        val cxy = mapView.mapCenterXY
        val x = (lastDRXY[0] - cxy[0]).toFloat()
        val y = (lastDRXY[1] - cxy[1]).toFloat()

        // Проверка дали е в рамките на екрана
        if (x < -mapView.width / 2 || x > mapView.width / 2 ||
            y < -mapView.height / 2 || y > mapView.height / 2) return

        c.save()
        c.translate(x, y)

        // Завъртане според bearing (ако е Track Up, самолетчето сочи нагоре без завъртане)
        if (!mapView.isTrackUp) {
            c.rotate(lastDRBearing)
        }

        val hw = icon.intrinsicWidth / 2
        val hh = icon.intrinsicHeight / 2
        icon.setBounds(-hw, -hh - 25, hw, hh - 25)
        icon.draw(c)

        c.restore()
    }

    override fun onPreferencesChanged(settings: SharedPreferences) {
        // Не викаме super — не искаме да презаписва цвета/ширината от prefs
        // Сивият цвят е фиксиран
    }
}
