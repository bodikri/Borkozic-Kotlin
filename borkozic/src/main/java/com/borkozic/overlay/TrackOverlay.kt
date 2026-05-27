package com.borkozic.overlay

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.preference.PreferenceManager
import com.borkozic.Borkozic
import com.borkozic.MapView
import com.borkozic.R
import com.borkozic.data.Track

open class TrackOverlay : MapOverlay {

    @JvmField
    var paint: Paint = Paint()

    @JvmField
    var track: Track

    private var preserveWidth = false
    private var preserveColor = false

    @JvmOverloads
    constructor(mapActivity: Activity, aTrack: Track? = null) : super(mapActivity) {
        paint.isAntiAlias = true
        paint.strokeWidth = 3f
        paint.style = Paint.Style.STROKE

        if (aTrack != null) {
            track = aTrack
            if (track.width > 0) {
                paint.strokeWidth = track.width.toFloat()
                preserveWidth = true
            } else {
                track.width = paint.strokeWidth.toInt()
            }
            if (track.color != -1) {
                paint.color = track.color
                preserveColor = true
            } else {
                track.color = paint.color
            }
        } else {
            track = Track()
        }

        onPreferencesChanged(PreferenceManager.getDefaultSharedPreferences(context))

        enabled = true
    }

    fun onTrackPropertiesChanged() {
        if (paint.strokeWidth != track.width.toFloat()) {
            paint.strokeWidth = track.width.toFloat()
            preserveWidth = true
        }
        if (paint.color != track.color) {
            paint.color = track.color
            preserveColor = true
        }
    }

    open fun setTrack(track: Track) {
        this.track = track
        onTrackPropertiesChanged()
    }

    fun getTrack(): Track {
        return track
    }

    override fun onMapChanged() {
        val trackpoints = track.points
        synchronized(trackpoints) {
            for (tp in trackpoints) {
                tp.dirty = true
            }
        }
    }

    override fun onDraw(c: Canvas, mapView: MapView, centerX: Int, centerY: Int) {
        if (!track.show) return

        val application = context.application as Borkozic

        val cxy = mapView.mapCenterXY

        val w2 = mapView.width / 2
        val h2 = mapView.height / 2
        val left = cxy[0] - w2
        val right = cxy[0] + w2
        val top = cxy[1] - h2
        val bottom = cxy[1] + h2

        val path = Path()
        var first = true
        var skipped = false
        var lastX = 0
        var lastY = 0
        val trackpoints = track.points
        synchronized(trackpoints) {
            for (tp in trackpoints) {
                val xy: IntArray
                if (tp.dirty) {
                    xy = application.getXYbyLatLon(tp.latitude, tp.longitude)
                    tp.x = xy[0]
                    tp.y = xy[1]
                    tp.dirty = false
                } else {
                    xy = intArrayOf(tp.x, tp.y)
                }

                if (first) {
                    path.setLastPoint(
                        (xy[0] - cxy[0]).toFloat(),
                        (xy[1] - cxy[1]).toFloat()
                    )
                    lastX = xy[0]
                    lastY = xy[1]
                    first = false
                    continue
                }
                if ((lastX == xy[0] && lastY == xy[1]) ||
                    (lastX < left && cxy[0] < left) ||
                    (lastX > right && cxy[0] > right) ||
                    (lastY < top && cxy[1] < top) ||
                    (lastY > bottom && cxy[1] > bottom)
                ) {
                    lastX = xy[0]
                    lastY = xy[1]
                    skipped = true
                    continue
                }
                if (skipped) {
                    path.moveTo(
                        (lastX - cxy[0]).toFloat(),
                        (lastY - cxy[1]).toFloat()
                    )
                    skipped = false
                }
                if (tp.continous)
                    path.lineTo(
                        (xy[0] - cxy[0]).toFloat(),
                        (xy[1] - cxy[1]).toFloat()
                    )
                else
                    path.moveTo(
                        (xy[0] - cxy[0]).toFloat(),
                        (xy[1] - cxy[1]).toFloat()
                    )
                lastX = xy[0]
                lastY = xy[1]
            }
        }
        c.drawPath(path, paint)
    }

    override fun onDrawFinished(c: Canvas, mapView: MapView, centerX: Int, centerY: Int) {
    }

    override fun onPreferencesChanged(settings: SharedPreferences) {
        if (!preserveWidth)
            paint.strokeWidth = settings.getInt(
                context.getString(R.string.pref_tracking_linewidth),
                context.resources.getInteger(R.integer.def_track_linewidth)
            ).toFloat()
        if (!preserveColor)
            paint.color = settings.getInt(
                context.getString(R.string.pref_tracking_currentcolor),
                context.resources.getColor(R.color.currenttrack)
            )
    }
}
