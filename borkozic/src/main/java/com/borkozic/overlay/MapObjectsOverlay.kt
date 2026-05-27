package com.borkozic.overlay

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Paint.Align
import android.graphics.Rect
import android.graphics.Typeface
import androidx.preference.PreferenceManager
import android.view.MotionEvent
import com.borkozic.Borkozic
import com.borkozic.MapActivity
import com.borkozic.MapView
import com.borkozic.R
import com.borkozic.data.MapObject
import java.io.File
import java.util.WeakHashMap

open class MapObjectsOverlay(mapActivity: Activity) : MapOverlay(mapActivity) {

    private val bitmaps = WeakHashMap<MapObject, Bitmap>()

    private val borderPaint: Paint = Paint()
    private val fillPaint: Paint = Paint()
    private val textPaint: Paint = Paint()
    private val textFillPaint: Paint = Paint()
    private val proximityPaint: Paint = Paint()

    private var pointWidth = 0
    private var showNames = false
    private var mpp = 0.0

    init {
        enabled = true

        fillPaint.isAntiAlias = false
        fillPaint.strokeWidth = 1f
        fillPaint.style = Paint.Style.FILL_AND_STROKE
        fillPaint.color = context.resources.getColor(R.color.waypoint)
        borderPaint.isAntiAlias = false
        borderPaint.strokeWidth = 1f
        borderPaint.style = Paint.Style.STROKE
        borderPaint.color = context.resources.getColor(R.color.waypointtext)
        textPaint.isAntiAlias = true
        textPaint.strokeWidth = 2f
        textPaint.style = Paint.Style.FILL
        textPaint.textAlign = Align.LEFT
        textPaint.textSize = 10f
        textPaint.typeface = Typeface.SANS_SERIF
        textPaint.color = context.resources.getColor(R.color.waypointtext)
        textFillPaint.isAntiAlias = false
        textFillPaint.strokeWidth = 1f
        textFillPaint.style = Paint.Style.FILL_AND_STROKE
        textFillPaint.color = context.resources.getColor(R.color.waypointbg)
        proximityPaint.isAntiAlias = false
        proximityPaint.strokeWidth = 1f
        proximityPaint.style = Paint.Style.FILL_AND_STROKE
        proximityPaint.color = context.resources.getColor(R.color.proximity)

        mpp = 0.0

        onPreferencesChanged(PreferenceManager.getDefaultSharedPreferences(context))
    }

    fun clearBitmapCache() {
        bitmaps.clear()
    }

    override fun onBeforeDestroy() {
        super.onBeforeDestroy()
        clearBitmapCache()
    }

    @Synchronized
    override fun onMapChanged() {
        val application = context.application as Borkozic
        val map = application.currentMap ?: return

        mpp = map.mpp / map.zoom
    }

    override fun onSingleTap(e: MotionEvent, mapTap: Rect, mapView: MapView): Boolean {
        val application = context.application as Borkozic
        val mapObjects = application.getMapObjects().iterator()
        while (mapObjects.hasNext()) {
            val mo = mapObjects.next() ?: continue
            synchronized(mo) {
                val pointXY = application.getXYbyLatLon(mo.latitude, mo.longitude)
                if (mapTap.contains(pointXY[0], pointXY[1]) && context is MapActivity) {
                    return (context as MapActivity).mapObjectTapped(mo._id, e.x.toInt(), e.y.toInt())
                }
            }
        }
        return false
    }

    protected fun drawMapObject(c: Canvas, mo: MapObject, application: Borkozic, cxy: IntArray) {
        val xy = application.getXYbyLatLon(mo.latitude, mo.longitude)

        var bitmap: Bitmap? = null
        var dx = 0
        var dy = 0

        if (mo.bitmap != null) {
            bitmap = mo.bitmap
            dx = mo.bitmap!!.width / 2
            dy = mo.bitmap!!.height / 2
        }

        if (bitmap == null)
            bitmap = bitmaps[mo]

        if (bitmap == null) {
            var width = pointWidth
            var height = pointWidth

            var icon: Bitmap? = null
            if (mo.image.isNotEmpty() && application.iconsEnabled) {
                icon = BitmapFactory.decodeFile(application.iconPath + File.separator + mo.image)
                if (icon == null) {
                    mo.drawImage = false
                } else {
                    width = icon.width
                    height = icon.height
                    mo.drawImage = true
                }
            }

            val rect = Rect(0, 0, width, height)

            val bounds = Rect()

            if (showNames) {
                textPaint.getTextBounds(mo.name, 0, mo.name.length, bounds)
                bounds.right += 4
                bounds.bottom += 4
                width += 6 + bounds.width()
                if (height < bounds.height())
                    height = bounds.height()
            }

            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val bc = Canvas(bitmap!!)

            if (mo.drawImage) {
                bc.drawBitmap(icon!!, 0f,
                    if (icon.height > bounds.height()) 0f else ((bounds.height() - icon.height) / 2).toFloat(),
                    null
                )
            } else {
                var tc = 0
                var bgc = 0
                if (mo.textcolor != Integer.MIN_VALUE) {
                    tc = borderPaint.color
                    borderPaint.color = mo.textcolor
                }
                if (mo.backcolor != Integer.MIN_VALUE) {
                    bgc = fillPaint.color
                    fillPaint.color = mo.backcolor
                }
                bc.save()
                bc.translate(0f,
                    if (pointWidth > bounds.height()) 0f else ((bounds.height() - pointWidth) / 2).toFloat()
                )
                bc.drawRect(rect, borderPaint)
                rect.inset(1, 1)
                bc.drawRect(rect, fillPaint)
                bc.restore()
                if (mo.textcolor != Integer.MIN_VALUE) {
                    borderPaint.color = tc
                }
                if (mo.backcolor != Integer.MIN_VALUE) {
                    fillPaint.color = bgc
                }
            }

            if (showNames) {
                var tc = 0
                if (mo.textcolor != Integer.MIN_VALUE) {
                    tc = textPaint.color
                    textPaint.color = mo.textcolor
                }
                bc.translate((width - bounds.right).toFloat(),
                    (-bounds.top + (height - bounds.height()) / 2).toFloat()
                )
                bc.drawRect(bounds, textFillPaint)
                bc.drawText(mo.name, 2f, 2f, textPaint)
                if (mo.textcolor != Integer.MIN_VALUE) {
                    textPaint.color = tc
                }
            }
            bitmaps[mo] = bitmap
        }

        if (mo.bitmap == null) {
            dx = if (mo.drawImage) application.iconX else pointWidth / 2
            dy = if (mo.drawImage) application.iconY else bitmap!!.height / 2
        }

        if (mo.proximity > 0 && mpp > 0)
            c.drawCircle(
                (xy[0] - cxy[0]).toFloat(),
                (xy[1] - cxy[1]).toFloat(),
                (mo.proximity / mpp).toFloat(),
                proximityPaint
            )

        c.drawBitmap(bitmap!!, (xy[0] - dx - cxy[0]).toFloat(), (xy[1] - dy - cxy[1]).toFloat(), null)
    }

    override fun onDraw(c: Canvas, mapView: MapView, centerX: Int, centerY: Int) {
    }

    override fun onDrawFinished(c: Canvas, mapView: MapView, centerX: Int, centerY: Int) {
        val application = context.application as Borkozic

        val cxy = mapView.mapCenterXY

        val mapObjects = application.getMapObjects().iterator()
        while (mapObjects.hasNext()) {
            val mo = mapObjects.next() ?: continue
            synchronized(mo) {
                drawMapObject(c, mo, application, cxy)
            }
        }
    }

    override fun onPreferencesChanged(settings: SharedPreferences) {
        pointWidth = settings.getInt(
            context.getString(R.string.pref_waypoint_width),
            context.resources.getInteger(R.integer.def_waypoint_width)
        )
        showNames = settings.getBoolean(context.getString(R.string.pref_waypoint_showname), true)
        fillPaint.color = settings.getInt(
            context.getString(R.string.pref_waypoint_color),
            context.resources.getColor(R.color.waypoint)
        )
        val alpha = textFillPaint.alpha
        textFillPaint.color = settings.getInt(
            context.getString(R.string.pref_waypoint_bgcolor),
            context.resources.getColor(R.color.waypointbg)
        )
        textFillPaint.alpha = alpha
        borderPaint.color = settings.getInt(
            context.getString(R.string.pref_waypoint_namecolor),
            context.resources.getColor(R.color.waypointtext)
        )
        textPaint.color = settings.getInt(
            context.getString(R.string.pref_waypoint_namecolor),
            context.resources.getColor(R.color.waypointtext)
        )
        textPaint.textSize = pointWidth * 1.5f
        clearBitmapCache()
    }
}
