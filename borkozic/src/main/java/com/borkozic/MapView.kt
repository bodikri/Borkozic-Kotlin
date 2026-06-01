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
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Core map rendering surface — the heart of Borkozic's navigation display.
 *
 * ## Architecture
 * - **DrawingThread**: renders ~10fps (33fps during animations) on a SurfaceView canvas.
 * - **Gesture state machine**: single tap, double tap, drag, pinch-zoom, pinch-rotate.
 * - **Animation engine** (calculateLookAhead): three-layer smooth animation system
 *   for bearing changes, center transitions, and lookAhead shifting.
 *
 * ## Display Modes
 * - **North Up** (isTrackUp=false): map fixed, cursor rotates with heading.
 * - **Track Up** (isTrackUp=true): map rotates, cursor fixed, compass shows North.
 *
 * ## Following
 * - Toggled via double-tap. When active, map auto-centers on GPS and bearing
 *   tracks heading. Smooth animations prevent jarring transitions.
 *
 * @see docs/components/map-rotation.md for detailed documentation
 */
open class MapView : SurfaceView, SurfaceHolder.Callback {
    companion object {
        private const val TAG = "MapView"
        private const val MAX_ROTATION_SPEED = 20f
        private const val INC_ROTATION_SPEED = 0.5f
        private const val MAX_SHIFT_SPEED = 20f
        private const val INC_SHIFT_SPEED = 2f

        // ── Smooth Bearing Animation constants ──────────────────────────────
        /** Minimum bearing change (degrees) to trigger smooth animation instead of instant snap. */
        private const val SMOOTH_BEAR_THRESHOLD = 5f
        /** Maximum angular velocity during smooth bearing animation (°/frame). */
        private const val SMOOTH_BEAR_MAX_SPEED = 15f
        /** Angular acceleration increment (°/frame²). */
        private const val SMOOTH_BEAR_INC = 0.3f

        // ── Smooth Center Transition constants ───────────────────────────────
        /** Maximum progress velocity during center transition animation (progress/frame). */
        private const val SMOOTH_CENTER_MAX_SPEED = 0.08
        /** Progress acceleration increment per frame. */
        private const val SMOOTH_CENTER_INC = 0.004
        /** Bearing change threshold (degrees) for staggered bearing rotation during center animation. */
        private const val STAGGERED_BEAR_THRESHOLD = 10f

        private const val TAP = 1
        private const val CANCEL = 2

        private const val GESTURE_NOTHING = 0
        private const val GESTURE_DRAG = 1
        private const val GESTURE_PINCH = 2
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
    @JvmField
    var planeLogo: String? = null
    private var plLogSize = 0
    private var lastBestMap: Long = 0
    private var bestMapEnabled = true

    private var tapHandler: GestureHandler? = null
    private var firstTapTime: Long = 0  // timestamp of first tap (double-tap detection)
    private var wasDoubleTap = false    // last gesture was a double-tap
    private var upEvent: MotionEvent? = null  // cached ACTION_UP event for overlay dispatch
    private var penX = 0
    private var penY = 0
    private var penOX = 0  // pen-down origin X (for tap location tracking)
    private var penOY = 0  // pen-down origin Y
    /** Pixel offset from screen center to the lookahead cursor position. */
    var lookAheadXY = intArrayOf(0, 0)

    private var lookAhead = 0
    private var lookAheadC = 0f   // target lookahead distance
    private var lookAheadS = 0f   // current (smoothed) lookahead distance
    private var lookAheadSS = 0f  // lookahead smoothing velocity
    private var lookAheadPst = 0  // persisted lookahead percentage setting
    /** Viewport area for overlay clipping (set externally). */
    var viewArea = Rect()

    /** Current map center in geographic coordinates [lat, lon]. */
    var mapCenter = doubleArrayOf(0.0, 0.0)
    /** Current map center in pixel coordinates (based on current map projection). */
    var mapCenterXY = intArrayOf(0, 0)
    /** Current GPS location [lat, lon] (null when no fix). */
    var currentLocation: DoubleArray? = null
    /** Current GPS location in pixel coordinates. */
    var currentLocationXY = intArrayOf(0, 0)
    private var lookAheadB = 0f   // target bearing (rounded to nearest 10°)
    private var smoothB = 0f      // current smoothed bearing (Layer 3)
    private var smoothBS = 0f     // bearing smoothing velocity (Layer 3)
    // ── Rotation & Display State ──────────────────────────────────────
    /**
     * Map rotation angle in DEGREES (0–360).
     * - North Up:  bearing is the heading — cursor rotates, map stays fixed.
     * - Track Up:  bearing is the heading — map rotates, cursor stays fixed.
     * - Manual:    set by 2-finger pinch-rotate gesture when not following.
     * - GPS:       set from Location.bearing when isFollowing == true.
     */
    var bearing = 0f
    /**
     * Track Up mode: when true (Settings → Display → Map Rotation → Track Up),
     * the map canvas rotates to keep the heading direction "up" on screen.
     * When false (North Up), the map stays fixed and only the cursor rotates.
     */
    var isTrackUp = true
    /**
     * True when map scrolls automatically to keep the current GPS location centered.
     * Toggled via double-tap on map.
     */
    private var isFollowing = false

    // ── Smooth Bearing Animation State ─────────────────────────────────
    /** Target GPS bearing for smooth animation (set from setLocation when following). */
    private var smoothBearTarget = 0f
    /** Current animated bearing value — replaces `bearing` during smooth transition. */
    private var smoothBearCurrent = 0f
    /** Angular velocity (°/frame) — accelerates then decelerates toward target. */
    private var smoothBearSpeed = 0f
    /** True when smooth bearing animation is active (triggered on bearing jump > SMOOTH_BEAR_THRESHOLD). */
    private var smoothBearActive = false

    // ── Smooth Center Transition Animation State ───────────────────────
    /** True when map center smoothly animates to GPS position (activated on double-tap follow ON). */
    private var smoothCenterActive = false
    /** Starting map center latitude for the center transition animation. */
    private var smoothCenterStartLat = 0.0
    /** Starting map center longitude for the center transition animation. */
    private var smoothCenterStartLon = 0.0
    /** Bearing at the start of center transition — anchor for staggered bearing rotation. */
    private var smoothCenterStartBearing = 0f
    /** Current progress (0.0 → 1.0) of the center transition animation. */
    private var smoothCenterProgress = 0.0
    /** Progress velocity — accelerates then decelerates. */
    private var smoothCenterSpeed = 0.0
    /** Current GPS speed (m/s) from latest location update. */
    private var speed = 0f
    /** Meters per pixel at current map zoom — used for vector length calculation. */
    private var mpp = 0.0
    /** Cached directional vector line length in pixels. */
    private var vectorLength = 0
    /** Proximity value (meters) for vector Type 1 calculation. */
    private var proximity = 0

    private var movingCursor: Drawable? = null  // plane icon drawable
    private var compasNeedl: Drawable? = null   // compass needle drawable
    private var compassAhead = 0                // compass offset from center (pixels)
    private var crossPaint: Paint? = null       // crosshair paint
    private var pointerPaint: Paint? = null     // directional vector + off-screen arrow paint
    private var active: PorterDuffColorFilter? = null  // color filter for fixed (valid) cursor

    private var application: Borkozic? = null

    private var cachedHolder: SurfaceHolder? = null
    private var drawingThread: DrawingThread? = null
    private val lock = Any()

    // ── Gesture state machine ────────────────────────────────────────────
    /**
     * Current gesture: NOTHING(0)/DRAG(1)/PINCH(2).
     * NOTHING → idle, awaiting touch. DRAG → single-finger pan.
     * PINCH → two-finger zoom + rotate.
     */
    private var gestureMode = GESTURE_NOTHING
    /** Distance between two fingers at pinch start (zoom baseline). */
    private var gestureStartPinchDist = 0f
    /** Angle between two fingers at pinch start (rotation baseline). */
    private var gestureStartAngle = 0f
    /** Map bearing at pinch start — anchor for absolute rotation (no drift). */
    private var gestureStartBearing = 0f
    private var gestureStartScale = 1f
    private var gestureStartMapX = 0
    private var gestureStartMapY = 0
    private var gestureDragX = 0  // last drag position
    private var gestureDragY = 0
    private var wasMultitouch = false
    private var scale = 1f  // current pinch scale factor


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
        tapHandler = GestureHandler(this)

        viewArea = Rect()

        Log.d(TAG, "Map initialize")
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        compassAhead = (width / 4.35).toInt() + 40  // +40px (~10mm) extra distance from cursor
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

        /**
         * Drawing loop — renders map frames continuously.
         * Wrapped in try-catch to prevent uncaught exceptions from killing
         * the process. Exceptions are logged so they can be diagnosed.
         */
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
                            try {
                                mapView.doDraw(canvas)
                            } catch (e: Exception) {
                                Log.e(TAG, "doDraw crashed", e)
                                // Stop drawing on unrecoverable error
                                runFlag = false
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "DrawingThread lockCanvas error", e)
                } finally {
                    if (canvas != null) {
                        try {
                            surfaceHolder.unlockCanvasAndPost(canvas)
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }
    }

    /**
     * Main drawing routine — called by DrawingThread ~10x/sec.
     *
     * Rendering order (bottom → top):
     * 1. White background fill.
     * 2. Map rotation (Track Up only): canvas.rotate(+bearing) around screen center.
     *    - North Up (isTrackUp=false):  rotBearingDeg=0, canvas NOT rotated.
     *    - Track Up (isTrackUp=true):   canvas rotated so heading direction is "up" on screen.
     * 3. Map tiles: Borkozic.drawMap() with bearing in radians for coordinate transforms.
     * 4. Overlays (routes, tracks, etc.) — drawn in rotated canvas.
     * 5. Compass needle (Track Up): drawn in rotated canvas → rotates with map, always points
     *    to true North on the map.
     * 6. Plane cursor: save/restore block with canvas.rotate(-bearing) to counter-rotate
     *    so the cursor always points straight up (heading direction).
     *    - North Up: cursor.rotate(+bearing) rotates the plane to the heading angle.
     * 7. Crosshair (when !isFollowing): centered, drawn in rotated canvas.
     */
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

        // Rotation — bearing is in DEGREES now, canvas.rotate() expects degrees
        // Android Canvas.rotate() is clockwise. We need counter-clockwise rotation
        // so that GPS heading (bearing) points UP on screen in Track Up mode.
        val rotBearingDeg = if (isTrackUp) bearing else 0f
        if (rotBearingDeg != 0f) {
            canvas.rotate(-rotBearingDeg, (lookAheadXY[0] + cx).toFloat(), (lookAheadXY[1] + cy).toFloat())
        }
        // drawMap needs bearing in RADIANS for coordinate transforms
        application?.drawMap(Math.toRadians(bearing.toDouble()).toFloat(), mapCenter, lookAheadXY, loadBestMap, width, height, canvas)

        canvas.translate((lookAheadXY[0] + cx).toFloat(), (lookAheadXY[1] + cy).toFloat())

        if (!scaled && (penOX == 0 && penOY == 0 || !hideOnDrag)) {
            application?.getOverlays(Borkozic.ORDER_DRAW_PREFERENCE)
                ?.forEach { mo -> mo.onManagedDraw(canvas, this, cx, cy) }
        }

        // ── Cursor rendering (always topmost) ─────────────────────
        if (!scaled && currentLocation != null) {
            // Compass needle — drawn in the rotated canvas, rotates with map
            if (isTrackUp) {
                canvas.translate(0f, -compassAhead.toFloat())
                compasNeedl?.draw(canvas)
                canvas.translate(0f, compassAhead.toFloat())
            }
            // Cursor — save/restore block for isolated transform
            // First translate to cursor position in ROTATED canvas space
            // (matches how drawMap renders tiles), then counter-rotate so the
            // cursor icon always points straight up (heading direction).
            canvas.save()
            canvas.translate(
                (-mapCenterXY[0] + currentLocationXY[0]).toFloat(),
                (-mapCenterXY[1] + currentLocationXY[1]).toFloat()
            )
            if (isTrackUp) {
                // Counter-rotate cursor to stay pointing UP (opposite of canvas rotation)
                canvas.rotate(bearing)
            }
            if (isMoving) {
                if (!isTrackUp) {
                    canvas.rotate(bearing, 0f, 0f)
                }
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

            // Off-screen pointer arrow — visible when cursor is outside viewport
            // Points toward the GPS location from the edge of the screen
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

    /**
     * Updates map position and bearing from GPS location.
     *
     * When isFollowing:
     * - GPS bearing becomes the target for smooth bearing animation if the absolute
     *   change exceeds SMOOTH_BEAR_THRESHOLD (5°). The animation accelerates then
     *   decelerates toward the target, replacing instant snap.
     * - Map center is updated from GPS (unless smoothCenterActive handles it).
     */
    fun setLocation(loc: Location) {
        synchronized(lock) {
            speed = loc.speed

            if (currentLocation == null) {
                currentLocation = DoubleArray(2)
            }
            currentLocation!![0] = loc.latitude
            currentLocation!![1] = loc.longitude
            currentLocationXY = application?.getXYbyLatLon(currentLocation!![0], currentLocation!![1]) ?: intArrayOf(0, 0)

            val lastLocationMillis = loc.time

            if (isFollowing) {
                val gpsBearing = loc.bearing

                // ── Smooth bearing: animate if jump > threshold ────────
                if (smoothCenterActive) {
                    // During smooth center animation, stash the target —
                    // staggered bearing will converge toward it in calculateLookAhead
                    smoothBearTarget = (gpsBearing / 10).toInt() * 10f
                } else {
                    val targetB = (gpsBearing / 10).toInt() * 10f
                    val deltaB = ((targetB - bearing) % 360f + 540f) % 360f - 180f

                    if (abs(deltaB) > SMOOTH_BEAR_THRESHOLD) {
                        smoothBearTarget = targetB
                        smoothBearCurrent = bearing
                        smoothBearSpeed = 0f
                        smoothBearActive = true
                        // Don't overwrite bearing — animation handles it
                    } else {
                        bearing = gpsBearing
                        smoothBearActive = false
                    }
                }
                // Update lookAheadB for calculateLookAhead to use (only when center is done)
                if (!smoothCenterActive && !smoothBearActive) {
                    lookAheadB = (bearing / 10).toInt() * 10f
                }

                // ── Map center update: skip if smooth center animation is active ──
                if (!smoothCenterActive) {
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
        }
        calculateVectorLength()
    }

    /**
     * Clears current location and resets map state.
     * Called when GPS fix is lost or user disconnects location source.
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

    /**
     * Updates local map metadata after a map change.
     * Resets zoom scale, recalculates meters-per-pixel (mpp),
     * notifies overlays and updates file info display.
     */
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
     * Per-frame animation tick for smooth transitions.
     *
     * Handles three animation layers (in priority order):
     * 1. **Smooth Center Transition** — interpolates mapCenter from start position
     *    toward GPS location with accelerate/decelerate. When |Δbearing| >
     *    STAGGERED_BEAR_THRESHOLD, bearing converges proportionally to center
     *    progress so the map starts orienting while it "flies" toward the target.
     *    When complete → transitions to Phase 2 via setMapCenter call.
     * 2. **Smooth Bearing** — accelerates/decelerates bearing toward target when
     *    GPS heading change exceeds SMOOTH_BEAR_THRESHOLD. Same pattern as
     *    lookAhead shift animation.
     * 3. **LookAhead** — smooth positional offset and bearing smoothing during
     *    normal following (existing behavior, unchanged).
     *
     * @return True if any animation was recalculated (triggers faster redraw ~33fps).
     */
    private fun calculateLookAhead(): Boolean {
        var recalculated = false
        synchronized(lock) {
            // ── Layer 1: Smooth Center Transition ────────────────────
            if (smoothCenterActive) {
                val targetLat = currentLocation?.get(0) ?: return false
                val targetLon = currentLocation?.get(1) ?: return false
                val targetB = smoothBearTarget

                val progress = smoothCenterProgress
                val diff = 1.0 - progress

                // Accelerate/decelerate progress (same pattern as lookAhead shift)
                if (abs(diff) > abs(smoothCenterSpeed) * (SMOOTH_CENTER_MAX_SPEED / SMOOTH_CENTER_INC)) {
                    smoothCenterSpeed += kotlin.math.sign(diff) * SMOOTH_CENTER_INC
                    if (abs(smoothCenterSpeed) > SMOOTH_CENTER_MAX_SPEED) {
                        smoothCenterSpeed = kotlin.math.sign(smoothCenterSpeed) * SMOOTH_CENTER_MAX_SPEED
                    }
                } else if (kotlin.math.sign(diff) != kotlin.math.sign(smoothCenterSpeed)) {
                    smoothCenterSpeed += kotlin.math.sign(diff) * SMOOTH_CENTER_INC * 2
                } else if (abs(smoothCenterSpeed) > SMOOTH_CENTER_INC) {
                    smoothCenterSpeed -= kotlin.math.sign(diff) * SMOOTH_CENTER_INC * 0.5
                }

                if (abs(diff) < SMOOTH_CENTER_INC * 1.5) {
                    // Animation complete — snap to target and transition to Phase 2
                    application?.setMapCenter(targetLat, targetLon, true, false)
                    // Sync local state so renderer sees the correct position
                    mapCenter[0] = targetLat
                    mapCenter[1] = targetLon
                    mapCenterXY = application?.getXYbyLatLon(targetLat, targetLon) ?: intArrayOf(0, 0)
                    updateMapInfo()
                    smoothCenterActive = false
                    smoothCenterProgress = 1.0
                    smoothCenterSpeed = 0.0
                    // Hand over to normal following
                    bearing = targetB
                    lookAheadB = (targetB / 10).toInt() * 10f
                    smoothBearActive = false
                    recalculated = true
                } else {
                    smoothCenterProgress = progress + smoothCenterSpeed
                    if (smoothCenterProgress > 1.0) smoothCenterProgress = 1.0
                    if (smoothCenterProgress < 0.0) smoothCenterProgress = 0.0

                    val p = smoothCenterProgress
                    val interpLat = smoothCenterStartLat + (targetLat - smoothCenterStartLat) * p
                    val interpLon = smoothCenterStartLon + (targetLon - smoothCenterStartLon) * p
                    // Directly update Borkozic.mapCenter to avoid setMapCenter() overhead per frame
                    // (setMapCenter calls updateLocationMaps which does map coverage checks)
                    application?.let { app ->
                        app.setMapCenter(interpLat, interpLon, false, false)
                    }
                    // Sync local copies — doDraw() reads these, not Borkozic's copies
                    mapCenter[0] = interpLat
                    mapCenter[1] = interpLon
                    mapCenterXY = application?.getXYbyLatLon(interpLat, interpLon) ?: intArrayOf(0, 0)

                    // ── Staggered bearing: converge proportionally ──
                    val deltaB = ((targetB - smoothCenterStartBearing) % 360f + 540f) % 360f - 180f
                    if (abs(deltaB) > STAGGERED_BEAR_THRESHOLD) {
                        bearing = smoothCenterStartBearing + deltaB * p.toFloat()
                        bearing = ((bearing % 360f) + 360f) % 360f
                    }
                    recalculated = true
                }
            }

            // ── Layer 2: Smooth Bearing Animation ────────────────────
            if (smoothBearActive && !smoothCenterActive) {
                var turn = smoothBearTarget - smoothBearCurrent
                if (abs(turn) > 180) {
                    turn -= kotlin.math.sign(turn) * 360f
                }

                if (abs(turn) > abs(smoothBearSpeed) * (SMOOTH_BEAR_MAX_SPEED / SMOOTH_BEAR_INC)) {
                    smoothBearSpeed += kotlin.math.sign(turn) * SMOOTH_BEAR_INC
                    if (abs(smoothBearSpeed) > SMOOTH_BEAR_MAX_SPEED) {
                        smoothBearSpeed = kotlin.math.sign(smoothBearSpeed) * SMOOTH_BEAR_MAX_SPEED
                    }
                } else if (kotlin.math.sign(turn) != kotlin.math.sign(smoothBearSpeed)) {
                    smoothBearSpeed += kotlin.math.sign(turn) * SMOOTH_BEAR_INC * 2
                } else if (abs(smoothBearSpeed) > SMOOTH_BEAR_INC) {
                    smoothBearSpeed -= kotlin.math.sign(turn) * SMOOTH_BEAR_INC * 0.5f
                }

                if (abs(turn) < SMOOTH_BEAR_INC) {
                    bearing = smoothBearTarget
                    smoothBearCurrent = smoothBearTarget
                    smoothBearSpeed = 0f
                    smoothBearActive = false
                    lookAheadB = (bearing / 10).toInt() * 10f
                } else {
                    smoothBearCurrent += smoothBearSpeed
                    if (smoothBearCurrent >= 360f) smoothBearCurrent -= 360f
                    if (smoothBearCurrent < 0f) smoothBearCurrent += 360f
                    bearing = smoothBearCurrent
                }
                recalculated = true
            }

            // ── Layer 3: LookAhead position & bearing smoothing ──────
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
            if (lookAheadB != smoothB && !smoothCenterActive && !smoothBearActive) {
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

    /**
     * Calculates the length of the directional vector line drawn ahead of
     * the plane cursor. The length depends on:
     * - vectorType: 0=fixed(7px), 1=proximity-based, 2=speed-based
     * - vectorMultiplier: user-configurable scale factor
     */
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

    /**
     * Updates the plane cursor size variant. Called when user changes
     * cursor settings in Preferences.
     *
     * @param planeLogoSize Pixel size variant code (60, 80, 100, 120, 140, 160)
     */
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
            mc.colorFilter = if (isFixed) active else null  // dimmed when no GPS fix
        }
    }

    fun setMoving(moving: Boolean) {
        isMoving = moving
    }

    fun isMoving(): Boolean = isMoving

    /**
     * Toggles auto-follow mode.
     *
     * When follow is enabled:
     * - Initiates a smooth center transition animation from the current map center
     *   to the GPS location (accelerate → decelerate).
     * - If the bearing change exceeds SMOOTH_BEAR_THRESHOLD, a smooth bearing
     *   animation is also started (handled in setLocation).
     * - The map center animation has two phases:
     *   Phase 1: Center interpolates toward GPS. If |Δbearing| > STAGGERED_BEAR_THRESHOLD,
     *            bearing starts converging proportionally to center progress.
     *   Phase 2 (after center reaches target): Bearing final alignment + lookAhead
     *            taken over by calculateLookAhead().
     */
    fun setFollowing(follow: Boolean) {
        if (currentLocation == null) return

        if (isFollowing != follow) {
            synchronized(lock) {
                if (follow) {
                    Toast.makeText(context, R.string.following_enabled, Toast.LENGTH_SHORT).show()

                    // ── Initiate smooth center transition ──────────────
                    smoothCenterStartLat = mapCenter[0]
                    smoothCenterStartLon = mapCenter[1]
                    smoothCenterStartBearing = bearing
                    smoothCenterProgress = 0.0
                    smoothCenterSpeed = 0.0
                    smoothCenterActive = true
                    // smoothBearTarget will be set by the next setLocation() call
                    // Don't call setMapCenter yet — animation handles it frame by frame
                } else {
                    Toast.makeText(context, R.string.following_disabled, Toast.LENGTH_SHORT).show()
                    smoothCenterActive = false
                    smoothBearActive = false
                }
                isFollowing = follow
            }
            update()
        }
    }

    /**
     * Toggles follow mode via MapActivity context.
     * Used internally from gesture/key handlers — routes through
     * the activity to ensure proper state propagation.
     */
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

    /**
     * Sets the lookahead percentage — how far ahead of the GPS position
     * the cursor is displayed (0–100%). Higher values shift the cursor
     * further ahead in the direction of travel, optimizing visible space.
     *
     * @param ahead Percentage value (0–100). 0 = no offset, cursor at GPS position.
     */
    fun setLookAhead(ahead: Int) {
        synchronized(lock) {
            lookAheadPst = ahead
            val w = width
            val h = height
            val half = if (w > h) h / 2 else w / 2
            lookAhead = (half * ahead * 0.01).toInt()
        }
    }

    /**
     * Sets Track Up display mode from Settings.
     *
     * @param isTrUp Preference string: "0" = North Up, "1" = Track Up
     */
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

    /**
     * Synchronizes local mapCenter/mapCenterXY and currentLocationXY
     * from the Borkozic application state. Called after external state
     * changes (map scroll, location update, screen rotation restore).
     */
    fun update() {
        synchronized(lock) {
            val mc = application?.getMapCenter()
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

    /**
     * Scrolls the map by delta pixels in screen space.
     * Called after a single-finger drag gesture completes.
     * Triggers map info refresh if the map changed.
     */
    private fun onDragFinished(deltaX: Int, deltaY: Int) {
        synchronized(lock) {
            // Always drag in screen space — finger direction = map movement direction
            // No bearing rotation needed (canvas handles visual rotation separately)
            val mapChanged = application?.scrollMap(-deltaX, -deltaY) ?: false
            if (mapChanged) updateMapInfo()
            update()
        }
    }

    /**
     * Handles a single tap on the map.
     * Converts screen coordinates to map coordinates, accounting for
     * Track Up rotation, then dispatches to overlays for hit testing.
     */
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

    /**
     * Toggles auto-follow mode.
     * - Follow ON:  initiates smooth center + bearing transition animation.
     * - Follow OFF: resets bearing to 0 (North = up), cancels any running animations.
     */
    private fun onDoubleTap(x: Int, y: Int) {
        setFollowingThroughContext(!isFollowing)
        // Zero bearing when exiting follow — snap back to North
        if (!isFollowing) {
            synchronized(lock) {
                bearing = 0f
            }
        }
    }

    /**
     * Gesture detection helper — schedules single-tap vs double-tap
     * resolution on the main looper.
     */
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

    // ── Touch event handling ───────────────────────────────────────────
    /**
     * Gesture state machine for map interaction.
     *
     * Supports:
     * - Single tap → overlay hit testing via onSingleTap()
     * - Double tap → toggle auto-follow mode
     * - Single finger drag → pan the map (rotation-compensated when !isFollowing)
     * - Pinch (2 fingers) → zoom + rotate
     *
     * Double-tap detection: first tap arms a delayed TAP handler (via
     * doubleTapTimeout). If a second DOWN arrives within the timeout window,
     * onDoubleTap() fires. Otherwise, the delayed TAP fires a regular tap.
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val pointerCount = event.pointerCount

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                gestureMode = GESTURE_DRAG
                val hadTapMessage = tapHandler?.hasMessages(TAP) == true
                if (hadTapMessage) tapHandler?.removeMessages(TAP)
                tapHandler?.removeMessages(CANCEL)

                if (event.eventTime - firstTapTime <= doubleTapTimeout) {
                    onDoubleTap(penOX, penOY)
                    cancelMotionEvent()
                    wasDoubleTap = true
                    gestureMode = GESTURE_NOTHING
                    return true
                } else {
                    firstTapTime = event.downTime
                }

                penOX = event.x.toInt()
                penOY = event.y.toInt()
                penX = penOX
                penY = penOY
                gestureDragX = penOX
                gestureDragY = penOY
                wasMultitouch = false
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (pointerCount == 2) {
                    gestureMode = GESTURE_PINCH
                    wasMultitouch = true
                    tapHandler?.removeMessages(TAP)
                    tapHandler?.removeMessages(CANCEL)
                    gestureStartPinchDist = distanceBetweenFingers(event)
                    gestureStartAngle = angleBetweenFingers(event)
                    gestureStartBearing = bearing
                    gestureStartScale = 1f
                }
            }

            MotionEvent.ACTION_MOVE -> {
                when (gestureMode) {
                    GESTURE_DRAG -> {
                        if (!isFollowing || !strictUnfollow) {
                            val currentX = event.x.toInt()
                            val currentY = event.y.toInt()
                            val dx = -(gestureDragX - currentX)
                            val dy = -(gestureDragY - currentY)

                            if (abs(dx) > 0 || abs(dy) > 0) {
                                gestureDragX = currentX
                                gestureDragY = currentY

                                if (!isFollowing) {
                                    // Track Up: canvas is already rotated by +bearing → apply
                                    // forward rotation to convert screen delta to map delta.
                                    // North Up: canvas NOT rotated → need inverse rotation.
                                    val rad = Math.toRadians((if (isTrackUp) bearing else -bearing).toDouble())
                                    val mapDx = (dx * cos(rad) + dy * sin(rad)).toInt()
                                    val mapDy = (-dx * sin(rad) + dy * cos(rad)).toInt()
                                    onDragFinished(mapDx, mapDy)
                                }
                                if (!strictUnfollow) setFollowingThroughContext(false)
                            }
                        }
                    }

                    GESTURE_PINCH -> {
                        if (pointerCount == 2) {
                            val currentDist = distanceBetweenFingers(event)
                            if (gestureStartPinchDist > 0f) {
                                val ratio = currentDist / gestureStartPinchDist
                                scale = if (ratio > 1) {
                                    kotlin.math.log10(ratio.toDouble()).toFloat() + 1f
                                } else {
                                    1f / (kotlin.math.log10(1.0 / ratio).toFloat() + 1f)
                                }
                            }

                            if (!isFollowing) {
                                val currentAngle = angleBetweenFingers(event)
                                val deltaAngle = currentAngle - gestureStartAngle
                                var normalized = deltaAngle % 360f
                                if (normalized > 180f) normalized -= 360f
                                if (normalized < -180f) normalized += 360f
                                val newBearing = gestureStartBearing + normalized
                                bearing = ((newBearing % 360f) + 360f) % 360f
                            }
                        }
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (pointerCount == 2) {
                    try {
                        val borkozic = context as MapActivity
                        borkozic.zoomMap(scale)
                    } catch (_: Exception) {
                    }
                    scale = 1f
                    gestureMode = GESTURE_DRAG
                    gestureDragX = event.x.toInt()
                    gestureDragY = event.y.toInt()
                }
            }

            MotionEvent.ACTION_UP -> {
                upEvent?.recycle()
                upEvent = MotionEvent.obtain(event)

                if (gestureMode == GESTURE_PINCH) {
                    try {
                        val borkozic = context as MapActivity
                        borkozic.zoomMap(scale)
                    } catch (_: Exception) {
                    }
                    scale = 1f
                }

                val dx = -(penOX - event.x.toInt())
                val dy = -(penOY - event.y.toInt())
                if (gestureMode == GESTURE_DRAG && !wasMultitouch && !wasDoubleTap &&
                    abs(dx) < gestureThresholdDp && abs(dy) < gestureThresholdDp
                ) {
                    tapHandler?.sendEmptyMessageDelayed(TAP, doubleTapTimeout.toLong())
                } else if (wasMultitouch || wasDoubleTap) {
                    cancelMotionEvent()
                } else {
                    tapHandler?.sendEmptyMessageDelayed(CANCEL, doubleTapTimeout.toLong())
                }

                wasMultitouch = false
                wasDoubleTap = false
                gestureMode = GESTURE_NOTHING
            }

            MotionEvent.ACTION_CANCEL -> {
                wasMultitouch = false
                wasDoubleTap = false
                gestureMode = GESTURE_NOTHING
                scale = 1f
                cancelMotionEvent()
            }
        }

        return true
    }

    private fun distanceBetweenFingers(event: MotionEvent): Float {
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    private fun angleBetweenFingers(event: MotionEvent): Float {
        val dx = event.getX(1) - event.getX(0)
        val dy = event.getY(1) - event.getY(0)
        val deg = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
        return ((deg % 360f) + 360f) % 360f
    }

    // ── Key/trackball input ───────────────────────────────────────────
    /**
     * D-pad and hardware key input for map navigation.
     * - Center button → toggle follow
     * - Arrow keys → pan by 10px (disables follow if strictUnfollow enabled)
     */
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

    /**
     * Trackball events: click toggles follow, scroll pans the map.
     * Legacy hardware support for devices with optical trackpads.
     */
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

            smoothBearTarget = state.getFloat("smoothBearTarget")
            smoothBearCurrent = state.getFloat("smoothBearCurrent")
            smoothBearSpeed = state.getFloat("smoothBearSpeed")
            smoothBearActive = state.getBoolean("smoothBearActive")
            smoothCenterActive = state.getBoolean("smoothCenterActive")
            smoothCenterStartLat = state.getDouble("smoothCenterStartLat")
            smoothCenterStartLon = state.getDouble("smoothCenterStartLon")
            smoothCenterStartBearing = state.getFloat("smoothCenterStartBearing")
            smoothCenterProgress = state.getDouble("smoothCenterProgress")
            smoothCenterSpeed = state.getDouble("smoothCenterSpeed")

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

        bundle.putFloat("smoothBearTarget", smoothBearTarget)
        bundle.putFloat("smoothBearCurrent", smoothBearCurrent)
        bundle.putFloat("smoothBearSpeed", smoothBearSpeed)
        bundle.putBoolean("smoothBearActive", smoothBearActive)
        bundle.putBoolean("smoothCenterActive", smoothCenterActive)
        bundle.putDouble("smoothCenterStartLat", smoothCenterStartLat)
        bundle.putDouble("smoothCenterStartLon", smoothCenterStartLon)
        bundle.putFloat("smoothCenterStartBearing", smoothCenterStartBearing)
        bundle.putDouble("smoothCenterProgress", smoothCenterProgress)
        bundle.putDouble("smoothCenterSpeed", smoothCenterSpeed)

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


}
