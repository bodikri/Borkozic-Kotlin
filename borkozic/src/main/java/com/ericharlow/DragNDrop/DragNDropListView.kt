package com.ericharlow.DragNDrop

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ImageView
import android.widget.ListView
import com.borkozic.R

class DragNDropListView(context: Context, attrs: AttributeSet?) : ListView(context, attrs) {

    private var mDragMode: Boolean = false

    private var mStartPosition: Int = 0
    private var mEndPosition: Int = 0
    private var mDragPointOffset: Int = 0 // Used to adjust drag view location
    private var mDragItemY: Int = 0

    private var mDragView: ImageView? = null

    private var mDropListener: DropListener? = null
    private var mRemoveListener: RemoveListener? = null
    private var mDragListener: DragListener? = null

    fun setDropListener(l: DropListener?) {
        mDropListener = l
    }

    fun setRemoveListener(l: RemoveListener?) {
        mRemoveListener = l
    }

    fun setDragListener(l: DragListener?) {
        mDragListener = l
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.action
        val x = ev.x.toInt()
        val y = ev.y.toInt()

        if (action == MotionEvent.ACTION_DOWN && x < 40) {
            mDragMode = true
        }

        if (!mDragMode)
            return super.onTouchEvent(ev)

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                mStartPosition = pointToPosition(x, y)
                if (mStartPosition != INVALID_POSITION) {
                    val mItemPosition = mStartPosition - firstVisiblePosition
                    mDragItemY = getChildAt(mItemPosition).top
                    mDragPointOffset = y - mDragItemY
                    mDragPointOffset -= (ev.rawY.toInt() - y)
                    startDrag(mItemPosition, y)
                    drag(x, y)
                }
            }
            MotionEvent.ACTION_MOVE -> drag(x, y)
            else -> {
                mDragMode = false
                mEndPosition = pointToPosition(x, y)
                stopDrag(mStartPosition - firstVisiblePosition, x, y)
                if (mDropListener != null && mStartPosition != INVALID_POSITION && mEndPosition != INVALID_POSITION)
                    mDropListener!!.onDrop(mStartPosition, mEndPosition)
            }
        }
        return true
    }

    // move the drag view
    private fun drag(x: Int, y: Int) {
        val layoutParams = mDragView!!.layoutParams as WindowManager.LayoutParams
        layoutParams.x = if (mRemoveListener != null) x else 0
        layoutParams.y = y - mDragPointOffset
        val mWindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mWindowManager.updateViewLayout(mDragView, layoutParams)

        if (mRemoveListener != null && x > mDragView!!.width * 0.7 && y - mDragItemY > 0 && y - mDragItemY < mDragView!!.height) {
            mDragView!!.setBackgroundResource(R.drawable.alert)
        } else if (mRemoveListener != null) {
            mDragView!!.setBackgroundResource(android.R.drawable.alert_dark_frame)
        }

        if (mDragListener != null)
            mDragListener!!.onDrag(x, y, this)
    }

    // enable the drag view for dragging
    private fun startDrag(itemIndex: Int, y: Int) {
        stopDrag(itemIndex, 0, y)

        val item = getChildAt(itemIndex) ?: return
        item.isDrawingCacheEnabled = true
        mDragListener?.onStartDrag(item)

        // Create a copy of the drawing cache so that it does not get recycled
        // by the framework when the list tries to clean up memory
        val bitmap = Bitmap.createBitmap(item.drawingCache)

        val mWindowParams = WindowManager.LayoutParams()
        mWindowParams.gravity = Gravity.TOP
        mWindowParams.x = 0
        mWindowParams.y = y - mDragPointOffset

        mWindowParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        mWindowParams.width = WindowManager.LayoutParams.WRAP_CONTENT
        mWindowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        mWindowParams.format = PixelFormat.TRANSLUCENT
        mWindowParams.windowAnimations = 0

        val v = ImageView(context)
        v.setImageBitmap(bitmap)

        val mWindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mWindowManager.addView(v, mWindowParams)
        mDragView = v
    }

    // destroy drag view
    private fun stopDrag(itemIndex: Int, x: Int, y: Int) {
        val dragView = mDragView ?: return
        val item = getChildAt(itemIndex)
        mDragListener?.onStopDrag(item)
        if (mRemoveListener != null && x > dragView.width * 0.7 && y - mDragItemY > 0 && y - mDragItemY < dragView.height) {
            mRemoveListener!!.onRemove(itemIndex)
        }
        dragView.visibility = GONE
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.removeView(dragView)
        dragView.setImageDrawable(null)
        mDragView = null
    }
}
