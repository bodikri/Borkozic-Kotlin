package com.ericharlow.DragNDrop

import android.view.View
import android.widget.ListView

/**
 * Implement to handle an item being dragged.
 *
 * @author Eric Harlow
 */
interface DragListener {
    /**
     * Called when a drag starts.
     * @param itemView - the view of the item to be dragged i.e. the drag view
     */
    fun onStartDrag(itemView: View)

    /**
     * Called when a drag is to be performed.
     * @param x - horizontal coordinate of MotionEvent.
     * @param y - vertical coordinate of MotionEvent.
     * @param listView - the listView
     */
    fun onDrag(x: Int, y: Int, listView: ListView)

    /**
     * Called when a drag stops.
     * Any changes in onStartDrag need to be undone here
     * so that the view can be used in the list again.
     * @param itemView - the view of the item to be dragged i.e. the drag view
     */
    fun onStopDrag(itemView: View)
}
