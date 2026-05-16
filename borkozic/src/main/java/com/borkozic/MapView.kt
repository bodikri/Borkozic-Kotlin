/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2012 Andrey Novikov <http://andreynovikov.info/>
 *
 * This file is part of Androzic application.
 *
 * Androzic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Androzic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Androzic. If not, see <http://www.gnu.org/licenses/>.
 */

package com.borkozic

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewConfiguration
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.borkozic.map.Map
import com.borkozic.overlay.MapOverlay
import com.borkozic.util.Geo
import org.metalev.multitouch.controller.MultiTouchController
import org.metalev.multitouch.controller.MultiTouchController.MultiTouchObjectCanvas
import org.metalev.multitouch.controller.MultiTouchController.PointInfo
import org.metalev.multitouch.controller.MultiTouchController.PositionAndScale
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

open class MapView : SurfaceView, SurfaceHolder.Callback, MultiTouchObjectCanvas<Any> {
    companion object {
        private const val TAG = "MapView"
        private const val MAX_ROTATION_SPEED = 20f
        private const val INC_ROTATION_SPEED = 0.5f
        private const val MAX_SHIFT_SPEED = 20f
        private const val INC_SHIFT_SPEED = 2f

        private const val TAP = 1
        private const val CANCEL = 2
    }

    private var vectorType = 1
    private var vectorMultiplier = 10
    private var strictUnfollow = true
    private var hideOnDrag = true
    private var loadBestMap = true
    private var bestMapInterval = 5000 // 5 seconds
    private var drawPeriod = 200L * 1000000 // 200 milliseconds

    /**
     * True when there is a valid location
     */
    var isFixed = false
        set(value) {
            field = value
            movingCursor?.colorFilter = if (isFixed) active else null
            update()
        }
    /**
     * True when there is a valid bearing
     */
    private var isMoving = false
    /**
     * True when map moves with location cursor
     */
    private var isFollowing = false
    /**
     * True when map rotation set track up
     */
    var isTrackUp = true
    var planeLogo: String? = null
    private var plLogSize = 0
    private var lastBestMap: Long = 0
    private var bestMapEnabled = true

    private var tapHandler: GestureHandler? = null
    private var firstTapTime: Long = 0
    private var wasDoubleTap = false
    private var upEvent: MotionEvent? = null
    private var penX = 0
    private var penY = 0
    private var penOX = 0
    private var penOY = 0
    var lookAheadXY = intArrayOf(0, 0)

    private var lookAhead = 0
    private var lookAheadC = 0f
    private var lookAheadS = 0f
    private var lookAheadSS = 0f
    private var lookAheadPst = 0
    var viewArea = Rect()

    var mapCenter = doubleArrayOf(0.0, 0.0)
    var mapCenterXY = intArrayOf(0, 0)
    var currentLocation: DoubleArray? = null
    var currentLocationXY = intArrayOf(0, 0)
    private var lookAheadB = 0f
    private var smoothB = 0f
    private var smoothBS = 0f
    var bearing = 0f
    var fingerBearing = 0f
    private var speed = 0f
    private var mpp = 0.0
    private var vectorLength = 0
    private var proximity = 0

    private var movingCursor: Drawable? = null
    private var compasNeedl: Drawable? = null
    private var compassAhead = 0
    private var crossPaint: Paint? = null
    private var pointerPaint: Paint? = null
    private var active: PorterDuffColorFilter? = null

    private var application: Borkozic? = null

    private var cachedHolder: SurfaceHolder? = null
    private var drawingThread: DrawingThread? = null
    private val lock = Any()

    private var multiTouchController: MultiTouchController<Any>? = null
    private var pinch = 0f
    private var scale = 1f
    private var wasMultitouch = false

    private val gestureThresholdDp: Int = (ViewConfiguration.get(BaseApplication.getApplication<Borkozic>() ?: context).scaledTouchSlop * 3)
    private val doubleTapTimeout: Int = ViewConfiguration.getDoubleTapTimeout()

    constructor(context: Context) : super(context) {
        setWillNotDraw(false)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        setWillNotDraw(false)
    }

    @SuppressLint("ObsoleteSdkInt")
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        setWillNotDraw(false)
    }

    fun initialize(application: Borkozic) {
        this.application = application
        holder.addCallback(this)
        crossPaint = Paint().apply {
            isAntiAlias = true
            strokeWidth = 3f
            style = Paint.Style.STROKE
            color = ContextCompat.getColor(context, R.color.mapcross)
        }
        pointerPaint = Paint().apply {
            isAntiAlias = true
            strokeWidth = 3f
            style = Paint.Style.STROKE
            color = ContextCompat.getColor(context, R.color.mapcross)
        }

        compasNeedl = ContextCompat.getDrawable(context, R.drawable.compass_needle_north_blue)
        compasNeedl?.setBounds(
            -compasNeedl!!.intrinsicWidth / 7, 0,
            compasNeedl!!.intrinsicWidth / 7, compasNeedl!!.intrinsicHeight / 3
        )
        multiTouchController = MultiTouchController(this, false)
        tapHandler = GestureHandler(this)

        viewArea = Rect()

        Log.d(TAG, "Map initialize")
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        compassAhead = (width / 4.35).toInt()
        synchronized(lock) {
            setLookAhead(lookAheadPst)
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        drawingThread = DrawingThread(holder, this).apply {
            setRunning(true)
            start()
        }
        cachedHolder = null
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        var retry = true
        drawingThread?.setRunning(false)
        while (retry) {
            try {
                drawingThread?.join()
                retry = false
            } catch (_: InterruptedException) {
            }
        }
    }

    /**
     * Pauses map drawing
     */
    fun pause() {
        if (cachedHolder != null || drawingThread == null) return
        cachedHolder = drawingThread!!.surfaceHolder
        surfaceDestroyed(cachedHolder!!)
    }

    /**
     * Resumes map drawing
     */
    fun resume() {
        cachedHolder?.let { surfaceCreated(it) }
    }

    /**
     * Checks if map drawing is paused
     */
    fun isPaused(): Boolean = cachedHolder != null

    inner class DrawingThread(val surfaceHolder: SurfaceHolder, private val mapView: MapView) : Thread() {
        private var runFlag = false
        private var prevTime: Long = System.nanoTime()

        fun setRunning(run: Boolean) {
            runFlag = run
        }

        override fun run() {
            var canvas: Canvas?
            while (runFlag) {
                val elapsedTime = System.nanoTime() - prevTime
                if (elapsedTime < drawPeriod) {
                    try {
                        Thread.sleep((drawPeriod - elapsedTime) / 1000000)
                    } catch (_: InterruptedException) {
                    }
                }
                prevTime = System.nanoTime()
                canvas = null
                try {
                    canvas = surfaceHolder.lockCanvas()
                    synchronized(lock) {
                        drawPeriod = 1000000L * if (mapView.calculateLookAhead()) 30 else 100
                        if (canvas != null) {
                            mapView.doDraw(canvas)
                        }
                    }
                } finally {
                    if (canvas != null) {
                        surfaceHolder.unlockCanvasAndPost(canvas)
                    }
                }
            }
        }
    }

    protected fun doDraw(canvas: Canvas) {
        val scaled = scale > 1.1f || scale < 0.9f
        if (scaled) {
            val dx = width * (1 - scale) / 2
            val dy = height * (1 - scale) / 2
            canvas.translate(dx, dy)
            val matrix = Matrix()
            matrix.postScale(scale, scale)
            canvas.concat(matrix)
        }
        canvas.drawRGB(0xFF, 0xFF, 0xFF)

        val cx = width / 2
        val cy = height / 2

        if (isTrackUp) {
            if (!scaled && !isFollowing) {
                Log.i(TAG, "fingerBearing$fingerBearing")
                canvas.rotate(-fingerBearing, (lookAheadXY[0] + cx).toFloat(), (lookAheadXY[1] + cy).toFloat())
                application?.drawMap(fingerBearing, mapCenter, lookAheadXY, loadBestMap, width, height, canvas)
            } else {
                canvas.rotate(-bearing, (lookAheadXY[0] + cx).toFloat(), (lookAheadXY[1] + cy).toFloat())
                application?.drawMap(bearing, mapCenter, lookAheadXY, loadBestMap, width, height, canvas)
            }
        } else {
            application?.drawMap(0f, mapCenter, lookAheadXY, loadBestMap, width, height, canvas)
        }

        canvas.translate((lookAheadXY[0] + cx).toFloat(), (lookAheadXY[1] + cy).toFloat())

        if (!scaled && (penOX == 0 && penOY == 0 || !hideOnDrag)) {
            application?.getOverlays(Borkozic.ORDER_DRAW_PREFERENCE)
                ?.forEach { mo -> mo.onManagedDraw(canvas, this, cx, cy) }
        }

        // draw cursor (it is always topmost)
        if (!scaled && currentLocation != null) {
            canvas.save()
            if (isTrackUp) {
                canvas.translate(0f, -compassAhead.toFloat())
                compasNeedl?.draw(canvas)
                canvas.translate(0f, compassAhead.toFloat())
            }
            canvas.translate(
                (-mapCenterXY[0] + currentLocationXY[0]).toFloat(),
                (-mapCenterXY[1] + currentLocationXY[1]).toFloat()
            )
            if (isMoving) {
                canvas.rotate(bearing, 0f, 0f)
                if (isFixed) {
                    canvas.drawLine(0f, 0f, 0f, -vectorLength.toFloat(), pointerPaint!!)
                }
                canvas.translate(0f, -25f)
                movingCursor?.draw(canvas)
            } else {
                canvas.drawCircle(0f, 0f, 1f, pointerPaint!!)
                canvas.drawCircle(0f, 0f, 40f, pointerPaint!!)
                canvas.drawLine(20f, 0f, 60f, 0f, pointerPaint!!)
                canvas.drawLine(-20f, 0f, -60f, 0f, pointerPaint!!)
                canvas.drawLine(0f, 20f, 0f, 60f, pointerPaint!!)
                canvas.drawLine(0f, -20f, 0f, -60f, pointerPaint!!)
            }
            canvas.restore()

            val sx = currentLocationXY[0] - mapCenterXY[0] + cx
            val sy = currentLocationXY[1] - mapCenterXY[1] + cy

            if (sx < 0 || sy < 0 || sx > width || sy > height) {
                canvas.save()
                val bearing = Geo.bearing(mapCenter!![0], mapCenter!![1], currentLocation!![0], currentLocation!![1])
                canvas.rotate(bearing.toFloat(), 0f, 0f)
                canvas.drawLine(-10f, -50f, 0f, -70f, pointerPaint!!)
                canvas.drawLine(0f, -70f, 10f, -50f, pointerPaint!!)
                canvas.drawLine(-10f, -50f, 10f, -50f, pointerPaint!!)
                canvas.restore()
            }
        }

        if (!scaled && !isFollowing) {
            canvas.drawCircle(0f, 0f, 1f, crossPaint!!)
            canvas.drawCircle(0f, 0f, 40f, crossPaint!!)
            canvas.drawLine(20f, 0f, 120f, 0f, crossPaint!!)
            canvas.drawLine(-20f, 0f, -120f, 0f, crossPaint!!)
            canvas.drawLine(0f, 20f, 0f, 120f, crossPaint!!)
            canvas.drawLine(0f, -20f, 0f, -120f, crossPaint!!)
        }

        if (isMoving && isFollowing && isFixed) {
            lookAheadC = lookAhead.toFloat()
        } else {
            lookAheadC = 0f
        }
    }

    fun setLocation(loc: Location) {
        synchronized(lock) {
            bearing = loc.bearing
            speed = loc.speed

            if (currentLocation == null) {
                currentLocation = DoubleArray(2)
            }
            currentLocation!![0] = loc.latitude
            currentLocation!![1] = loc.longitude
            currentLocationXY = application?.getXYbyLatLon(currentLocation!![0], currentLocation!![1]) ?: intArrayOf(0, 0)

            lookAheadB = (bearing / 10).toInt() * 10f

            val lastLocationMillis = loc.time

            if (isFollowing) {
                var newMap = false
                if (bestMapEnabled && bestMapInterval > 0 && lastLocationMillis - lastBestMap >= bestMapInterval) {
                    application?.let { app ->
                        newMap = app.setMapCenter(currentLocation!![0], currentLocation!![1], false, loadBestMap)
                    }
                    lastBestMap = lastLocationMillis
                } else {
                    application?.let { app ->
                        newMap = app.setMapCenter(currentLocation!![0], currentLocation!![1], false, false)
                    }
                    if (newMap) loadBestMap = bestMapEnabled
                }
                if (newMap) updateMapInfo()
            }
        }
        calculateVectorLength()
    }

    /**
     * Clears current location from map.
     */
    fun clearLocation() {
        setFollowingThroughContext(false)
        synchronized(lock) {
            currentLocation = null
            bearing = 0f
            speed = 0f
        }
        calculateVectorLength()
    }

    fun updateMapInfo() {
        synchronized(lock) {
            scale = 1f
            val map = application?.currentMap
            mpp = if (map == null) 0.0 else map.mpp / map.zoom
        }
        calculateVectorLength()
        application?.notifyOverlays()
        try {
            val borkozic = context as MapActivity
            borkozic.updateFileInfo()
        } finally {
        }
    }

    /**
     * При промяна на курса с цел да оптимизира разполагаем екран
     * когато не се върти картата премества самолетчето
     * @return True if look ahead position was recalculated
     */
    private fun calculateLookAhead(): Boolean {
        var recalculated = false
        synchronized(lock) {
            if (lookAheadC != lookAheadS) {
                recalculated = true

                val diff = lookAheadC - lookAheadS
                if (abs(diff) > abs(lookAheadSS) * (MAX_SHIFT_SPEED / INC_SHIFT_SPEED)) {
                    lookAheadSS += kotlin.math.sign(diff) * INC_SHIFT_SPEED
                    if (abs(lookAheadSS) > MAX_SHIFT_SPEED) {
                        lookAheadSS = kotlin.math.sign(lookAheadSS) * MAX_SHIFT_SPEED
                    }
                } else if (kotlin.math.sign(diff) != kotlin.math.sign(lookAheadSS)) {
                    lookAheadSS += kotlin.math.sign(diff) * INC_SHIFT_SPEED * 2
                } else if (abs(lookAheadSS) > INC_SHIFT_SPEED) {
                    lookAheadSS -= kotlin.math.sign(diff) * INC_SHIFT_SPEED * 0.5f
                }
                if (abs(diff) < INC_SHIFT_SPEED) {
                    lookAheadS = lookAheadC
                    lookAheadSS = 0f
                } else {
                    lookAheadS += lookAheadSS
                }
            }
            if (lookAheadB != smoothB) {
                recalculated = true

                var turn = lookAheadB - smoothB
                if (abs(turn) > 180) {
                    turn -= kotlin.math.sign(turn) * 360
                }
                if (abs(turn) > abs(smoothBS) * (MAX_ROTATION_SPEED / INC_ROTATION_SPEED)) {
                    smoothBS += kotlin.math.sign(turn) * INC_ROTATION_SPEED
                    if (abs(smoothBS) > MAX_ROTATION_SPEED) {
                        smoothBS = kotlin.math.sign(smoothBS) * MAX_ROTATION_SPEED
                    }
                } else if (kotlin.math.sign(turn) != kotlin.math.sign(smoothBS)) {
                    smoothBS += kotlin.math.sign(turn) * INC_ROTATION_SPEED * 2
                } else if (abs(smoothBS) > INC_ROTATION_SPEED) {
                    smoothBS -= kotlin.math.sign(turn) * INC_ROTATION_SPEED * 0.5f
                }
                if (abs(turn) < INC_ROTATION_SPEED) {
                    smoothB = lookAheadB
                    smoothBS = 0f
                } else {
                    smoothB += smoothBS
                    if (smoothB >= 360) smoothB -= 360
                    if (smoothB < 0) smoothB = 360 - smoothB
                }
            }
            if (recalculated) {
                if (isTrackUp) {
                    lookAheadXY[0] = 0
                    lookAheadXY[1] = (lookAheadS / 1.25).toInt()
                } else {
                    lookAheadXY[0] = (sin(Math.toRadians(smoothB.toDouble())) * -lookAheadS).toInt()
                    lookAheadXY[1] = (cos(Math.toRadians(smoothB.toDouble())) * lookAheadS).toInt()
                }
            }
        }
        return recalculated
    }

    private fun calculateVectorLength() {
        synchronized(lock) {
            if (mpp == 0.0) {
                vectorLength = 0
                return
            }
            vectorLength = when (vectorType) {
                0 -> 7
                1 -> proximity / 7
                2 -> (speed * 60 / 5).toInt()
                else -> 7
            }
            vectorLength *= vectorMultiplier / 2
        }
    }

    fun setMovingCursorSize(planeLogoSize: Int) {
        when (planeLogo) {
            "MiG29" -> {
                movingCursor = when (planeLogoSize) {
                    60 -> ContextCompat.getDrawable(context, R.drawable.pic_mig29_60)
                    80 -> ContextCompat.getDrawable(context, R.drawable.pic_mig29_80)
                    100 -> ContextCompat.getDrawable(context, R.drawable.pic_mig29_100)
                    120 -> ContextCompat.getDrawable(context, R.drawable.pic_mig29_120)
                    140 -> ContextCompat.getDrawable(context, R.drawable.pic_mig29_140)
                    160 -> ContextCompat.getDrawable(context, R.drawable.pic_mig29_160)
                    else -> ContextCompat.getDrawable(context, R.drawable.pic_mig29)
                }
            }
            "L39" -> {
                movingCursor = when (planeLogoSize) {
                    60 -> ContextCompat.getDrawable(context, R.drawable.pic_l39_60)
                    80 -> ContextCompat.getDrawable(context, R.drawable.pic_l39_80)
                    100 -> ContextCompat.getDrawable(context, R.drawable.pic_l39_100)
                    120 -> ContextCompat.getDrawable(context, R.drawable.pic_l39_120)
                    140 -> ContextCompat.getDrawable(context, R.drawable.pic_l39_140)
                    160 -> ContextCompat.getDrawable(context, R.drawable.pic_l39_160)
                    else -> ContextCompat.getDrawable(context, R.drawable.pic_l39)
                }
            }
        }
        val mc = movingCursor
        if (mc != null) {
            mc.setBounds(-mc.intrinsicWidth / 2, 0, mc.intrinsicWidth / 2, mc.intrinsicHeight)
            mc.colorFilter = if (isFixed) active else null
        }
    }

    fun setMoving(moving: Boolean) {
        isMoving = moving
    }

    fun isMoving(): Boolean = isMoving

    fun setFollowing(follow: Boolean) {
        if (currentLocation == null) return

        if (isFollowing != follow) {
            synchronized(lock) {
                if (follow) {
                    Toast.makeText(context, R.string.following_enabled, Toast.LENGTH_SHORT).show()
                    val newMap = application?.setMapCenter(currentLocation!![0], currentLocation!![1], true, false) ?: false
                    if (newMap) updateMapInfo()
                } else {
                    Toast.makeText(context, R.string.following_disabled, Toast.LENGTH_SHORT).show()
                }
                isFollowing = follow
            }
            update()
        }
    }

    private fun setFollowingThroughContext(follow: Boolean) {
        if (isFollowing != follow) {
            try {
                val borkozic = context as MapActivity
                borkozic.setFollowing(!isFollowing)
            } catch (_: Exception) {
                setFollowing(false)
            }
        }
    }

    fun isFollowing(): Boolean = isFollowing

    fun setStrictUnfollow(mode: Boolean) {
        strictUnfollow = mode
    }

    fun getStrictUnfollow(): Boolean = strictUnfollow

    fun setHideOnDrag(hide: Boolean) {
        hideOnDrag = hide
    }

    fun setBestMapEnabled(best: Boolean) {
        bestMapEnabled = best
    }

    fun suspendBestMap() {
        loadBestMap = false
    }

    fun isBestMapEnabled(): Boolean = loadBestMap

    fun setBestMapInterval(best: Int) {
        bestMapInterval = best
    }

    fun setLookAhead(ahead: Int) {
        synchronized(lock) {
            lookAheadPst = ahead
            val w = width
            val h = height
            val half = if (w > h) h / 2 else w / 2
            lookAhead = (half * ahead * 0.01).toInt()
        }
    }

    fun setTrackUp(isTrUp: String) {
        synchronized(lock) {
            isTrackUp = isTrUp == "1"
        }
    }

    fun setCursorColor(color: Int) {
        active = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        movingCursor?.colorFilter = if (isFixed) active else null
        pointerPaint?.color = color
    }

    fun setCursorVector(type: Int, multiplier: Int) {
        vectorType = type
        vectorMultiplier = multiplier
    }

    fun setProximity(proximity: Int) {
        this.proximity = proximity
    }

    fun updateViewArea(area: Rect) {
        Log.e(TAG, "updateViewArea()")
        viewArea.set(area)
    }

    fun update() {
        synchronized(lock) {
            val mc = application?.mapCenter
            if (mc != null) {
                mapCenter = mc
                mapCenterXY = application?.getXYbyLatLon(mapCenter[0], mapCenter[1]) ?: intArrayOf(0, 0)
            }
            if (currentLocation != null) {
                currentLocationXY = application?.getXYbyLatLon(currentLocation!![0], currentLocation!![1]) ?: intArrayOf(0, 0)
            }
        }
        try {
            val activity = context as MapActivity
            activity.updateCoordinates(mapCenter)
        } finally {
        }
    }

    private fun onDragFinished(deltaX: Int, deltaY: Int) {
        synchronized(lock) {
            val mapChanged = if (isTrackUp) {
                val rad = Math.toRadians(-bearing.toDouble())
                val dX = (deltaX * cos(rad) + deltaY * sin(rad)).toInt()
                val dY = (deltaX * sin(-rad) + deltaY * cos(rad)).toInt()
                application?.scrollMap(-dX, -dY) ?: false
            } else {
                application?.scrollMap(-deltaX, -deltaY) ?: false
            }
            if (mapChanged) updateMapInfo()
            update()
        }
    }

    private fun onSingleTap(x: Int, y: Int) {
        synchronized(lock) {
            val mapTapX: Int
            val mapTapY: Int
            if (isTrackUp) {
                val rad = Math.toRadians(-bearing.toDouble())
                val dX = ((x - width / 2) * cos(rad) + (y - lookAheadXY[1] - height / 2) * sin(rad)).toInt()
                val dY = ((x - width / 2) * sin(-rad) + (y - lookAheadXY[1] - height / 2) * cos(rad)).toInt()
                mapTapX = mapCenterXY[0] + dX
                mapTapY = mapCenterXY[1] + dY
            } else {
                mapTapX = x + mapCenterXY[0] - width / 2 - lookAheadXY[0]
                mapTapY = y + mapCenterXY[1] - height / 2 - lookAheadXY[1]
            }

            val dt = gestureThresholdDp / 2
            val tap = Rect(mapTapX - dt, mapTapY - dt, mapTapX + dt, mapTapY + dt)
            val ue = upEvent
            if (ue != null) {
                application?.getOverlays(Borkozic.ORDER_SHOW_PREFERENCE)
                    ?.forEach { mo ->
                        if (mo.onSingleTap(ue, tap, this)) return
                    }
            }
        }
    }

    private fun onDoubleTap(x: Int, y: Int) {
        setFollowingThroughContext(!isFollowing)
    }

    @SuppressLint("HandlerLeak")
    private inner class GestureHandler(view: MapView) : Handler(Looper.getMainLooper()) {
        private val target = WeakReference(view)

        override fun handleMessage(msg: Message) {
            val mapView = target.get() ?: return
            when (msg.what) {
                TAP -> {
                    mapView.onSingleTap(penOX, penOY)
                    mapView.cancelMotionEvent()
                }
                CANCEL -> mapView.cancelMotionEvent()
                else -> throw RuntimeException("Unknown message $msg")
            }
        }
    }

    private fun cancelMotionEvent() {
        tapHandler?.removeMessages(TAP)
        tapHandler?.removeMessages(CANCEL)
        upEvent?.recycle()
        upEvent = null
        penX = 0
        penY = 0
        penOX = 0
        penOY = 0
        firstTapTime = 0
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        var d = 0f
        if (multiTouchController?.onTouchEvent(event) == true) {
            wasMultitouch = true
            return true
        }

        val action = event.action and MotionEvent.ACTION_MASK

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                val hadTapMessage = tapHandler?.hasMessages(TAP) == true
                if (hadTapMessage) tapHandler?.removeMessages(TAP)
                tapHandler?.removeMessages(CANCEL)

                if (event.eventTime - firstTapTime <= doubleTapTimeout) {
                    onDoubleTap(penOX, penOY)
                    cancelMotionEvent()
                    wasDoubleTap = true
                } else {
                    firstTapTime = event.downTime
                }

                penOX = event.x.toInt()
                penOY = event.y.toInt()
                penX = penOX
                penY = penOY
            }
            MotionEvent.ACTION_MOVE -> {
                if (!wasMultitouch && (!isFollowing || !strictUnfollow)) {
                    val x = event.x.toInt()
                    val y = event.y.toInt()
                    val dx = -(penX - x)
                    val dy = -(penY - y)

                    if (!isFollowing && (abs(dx) > 0 || abs(dy) > 0)) {
                        penX = x
                        penY = y
                        onDragFinished(dx, dy)
                    }
                    if (abs(dx) > gestureThresholdDp || abs(dy) > gestureThresholdDp) {
                        if (!strictUnfollow) setFollowingThroughContext(false)
                    }
                } else {
                    if (event.pointerCount == 2) {
                        Log.i(TAG, "ACTION_MOVE;getPointerCount=2")
                        val newRot = rotation(event)
                        fingerBearing = newRot - d
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                upEvent?.recycle()
                upEvent = MotionEvent.obtain(event)

                val dx = -(penOX - event.x.toInt())
                val dy = -(penOY - event.y.toInt())
                if (!wasMultitouch && !wasDoubleTap && abs(dx) < gestureThresholdDp && abs(dy) < gestureThresholdDp) {
                    tapHandler?.sendEmptyMessageDelayed(TAP, doubleTapTimeout.toLong())
                } else if (wasMultitouch || wasDoubleTap) {
                    wasMultitouch = false
                    wasDoubleTap = false
                    cancelMotionEvent()
                } else {
                    tapHandler?.sendEmptyMessageDelayed(CANCEL, doubleTapTimeout.toLong())
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                wasMultitouch = false
                wasDoubleTap = false
                cancelMotionEvent()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                Log.i(TAG, "ACTION_POINTER_DOWN")
                d = rotation(event)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                Log.i(TAG, "ACTION_POINTER_UP")
            }
        }

        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                setFollowingThroughContext(!isFollowing)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!isFollowing || !strictUnfollow) {
                    var dx = 0
                    var dy = 0
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_DOWN -> dy -= 10
                        KeyEvent.KEYCODE_DPAD_UP -> dy += 10
                        KeyEvent.KEYCODE_DPAD_LEFT -> dx += 10
                        KeyEvent.KEYCODE_DPAD_RIGHT -> dx -= 10
                    }
                    if (isFollowing) setFollowingThroughContext(false)
                    onDragFinished(dx, dy)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return super.onKeyUp(keyCode, event)
    }

    override fun onTrackballEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_UP -> {
                setFollowingThroughContext(!isFollowing)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isFollowing) {
                    val n = event.historySize
                    val scaleX = event.xPrecision
                    val scaleY = event.yPrecision
                    var dx = (-event.x * scaleX).toInt()
                    var dy = (-event.y * scaleY).toInt()
                    for (i in 0 until n) {
                        dx += (-event.getHistoricalX(i) * scaleX).toInt()
                        dy += (-event.getHistoricalY(i) * scaleY).toInt()
                    }
                    if (abs(dx) > 0 || abs(dy) > 0) {
                        onDragFinished(dx, dy)
                    }
                }
            }
        }
        return true
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            super.onRestoreInstanceState(state.getParcelable("instanceState"))

            vectorType = state.getInt("vectorType")
            vectorMultiplier = state.getInt("vectorMultiplier")
            isFollowing = state.getBoolean("autoFollow")
            strictUnfollow = state.getBoolean("strictUnfollow")
            hideOnDrag = state.getBoolean("hideOnDrag")
            loadBestMap = state.getBoolean("loadBestMap")
            bestMapInterval = state.getInt("bestMapInterval")

            isFixed = state.getBoolean("isFixed")
            isMoving = state.getBoolean("isMoving")
            lastBestMap = state.getLong("lastBestMap")

            penX = state.getInt("penX")
            penY = state.getInt("penY")
            penOX = state.getInt("penOX")
            penOY = state.getInt("penOY")
            lookAheadXY = state.getIntArray("lookAheadXY") ?: intArrayOf(0, 0)
            lookAhead = state.getInt("lookAhead")
            lookAheadC = state.getFloat("lookAheadC")
            lookAheadS = state.getFloat("lookAheadS")
            lookAheadSS = state.getFloat("lookAheadSS")
            lookAheadPst = state.getInt("lookAheadPst")
            lookAheadB = state.getFloat("lookAheadB")
            smoothB = state.getFloat("smoothB")
            smoothBS = state.getFloat("smoothBS")

            mapCenter = state.getDoubleArray("mapCenter") ?: doubleArrayOf(0.0, 0.0)
            currentLocation = state.getDoubleArray("currentLocation")
            mapCenterXY = state.getIntArray("mapCenterXY") ?: intArrayOf(0, 0)
            currentLocationXY = state.getIntArray("currentLocationXY") ?: intArrayOf(0, 0)
            bearing = state.getFloat("bearing")
            speed = state.getFloat("speed")
            mpp = state.getDouble("mpp")
            vectorLength = state.getInt("vectorLength")
            proximity = state.getInt("proximity")

            movingCursor?.colorFilter = if (isFixed) active else null
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    override fun onSaveInstanceState(): Parcelable? {
        val bundle = Bundle()
        bundle.putParcelable("instanceState", super.onSaveInstanceState())

        bundle.putInt("vectorType", vectorType)
        bundle.putInt("vectorMultiplier", vectorMultiplier)
        bundle.putBoolean("autoFollow", isFollowing)
        bundle.putBoolean("strictUnfollow", strictUnfollow)
        bundle.putBoolean("hideOnDrag", hideOnDrag)
        bundle.putBoolean("loadBestMap", loadBestMap)
        bundle.putInt("bestMapInterval", bestMapInterval)

        bundle.putBoolean("isFixed", isFixed)
        bundle.putBoolean("isMoving", isMoving)
        bundle.putLong("lastBestMap", lastBestMap)

        bundle.putInt("penX", penX)
        bundle.putInt("penY", penY)
        bundle.putInt("penOX", penOX)
        bundle.putInt("penOY", penOY)
        bundle.putIntArray("lookAheadXY", lookAheadXY)
        bundle.putInt("lookAhead", lookAhead)
        bundle.putFloat("lookAheadC", lookAheadC)
        bundle.putFloat("lookAheadS", lookAheadS)
        bundle.putFloat("lookAheadSS", lookAheadSS)
        bundle.putInt("lookAheadPst", lookAheadPst)
        bundle.putFloat("lookAheadB", lookAheadB)
        bundle.putFloat("smoothB", smoothB)
        bundle.putFloat("smoothBS", smoothBS)

        bundle.putDoubleArray("mapCenter", mapCenter)
        bundle.putDoubleArray("currentLocation", currentLocation)
        bundle.putIntArray("mapCenterXY", mapCenterXY)
        bundle.putIntArray("currentLocationXY", currentLocationXY)
        bundle.putFloat("bearing", bearing)
        bundle.putFloat("speed", speed)
        bundle.putDouble("mpp", mpp)
        bundle.putInt("vectorLength", vectorLength)
        bundle.putInt("proximity", proximity)

        return bundle
    }

    override fun getDraggableObjectAtPoint(touchPoint: PointInfo): Any {
        pinch = 0f
        scale = 1f
        return this
    }

    override fun getPositionAndScale(obj: Any, objPosAndScaleOut: PositionAndScale) {
    }

    override fun selectObject(obj: Any?, touchPoint: PointInfo) {
        if (obj == null) {
            pinch = 0f
            Log.e(TAG, "Scale: $scale")
            try {
                val borkozic = context as MapActivity
                borkozic.zoomMap(scale)
            } finally {
            }
        }
    }

    override fun setPositionAndScale(obj: Any, newObjPosAndScale: PositionAndScale, touchPoint: PointInfo): Boolean {
        if (touchPoint.isDown && touchPoint.numTouchPoints == 2) {
            if (pinch == 0f) {
                pinch = touchPoint.multiTouchDiameterSq
            }
            fingerBearing = touchPoint.multiTouchAngle
            Log.i(TAG, "setPositionAndScale$fingerBearing")
            synchronized(lock) {
                scale = touchPoint.multiTouchDiameterSq / pinch
                scale = if (scale > 1) {
                    kotlin.math.log10(scale) + 1
                } else {
                    1 / (kotlin.math.log10(1 / scale) + 1)
                }.toFloat()
            }
        }
        return true
    }

    private fun rotation(event: MotionEvent): Float {
        val deltaX = (event.getX(0) - event.getX(1))
        val deltaY = (event.getY(0) - event.getY(1))
        val radians = kotlin.math.atan2(deltaY.toDouble(), deltaX.toDouble())
        return Math.toDegrees(radians).toFloat()
    }
}
