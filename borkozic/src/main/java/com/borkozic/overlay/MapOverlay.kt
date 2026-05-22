package com.borkozic.overlay

import android.annotation.SuppressLint
import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Rect
import android.view.KeyEvent
import android.view.MotionEvent
import com.borkozic.MapView

abstract class MapOverlay(activity: Activity) {

    @JvmField
    var context: Activity = activity

    @JvmField
    var enabled: Boolean = false

    open fun setMapContext(activity: Activity) {
        context = activity
    }

    abstract fun onPreferencesChanged(settings: SharedPreferences)

    @SuppressLint("WrongCall")
    fun onManagedDraw(c: Canvas, mapView: MapView, centerX: Int, centerY: Int) {
        if (enabled) {
            onDraw(c, mapView, centerX, centerY)
            onDrawFinished(c, mapView, centerX, centerY)
        }
    }

    protected abstract fun onDraw(c: Canvas, mapView: MapView, centerX: Int, centerY: Int)

    protected abstract fun onDrawFinished(c: Canvas, mapView: MapView, centerX: Int, centerY: Int)

    open fun onBeforeDestroy() {
        enabled = false
    }

    open fun onMapChanged() {
    }

    fun setEnabled(enabled: Boolean): Boolean {
        val r = this.enabled
        this.enabled = enabled
        return r
    }

    open fun onKeyDown(keyCode: Int, event: KeyEvent, mapView: MapView): Boolean {
        return false
    }

    open fun onKeyUp(keyCode: Int, event: KeyEvent, mapView: MapView): Boolean {
        return false
    }

    open fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
        return false
    }

    open fun onTrackballEvent(event: MotionEvent, mapView: MapView): Boolean {
        return false
    }

    open fun onSingleTap(e: MotionEvent, mapTap: Rect, mapView: MapView): Boolean {
        return false
    }

    open fun onLongPress(e: MotionEvent, mapTap: Rect, mapView: MapView): Boolean {
        return false
    }
}
