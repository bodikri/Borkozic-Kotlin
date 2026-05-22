package com.ericharlow.DragNDrop

/**
 * Implement to handle an item being dropped.
 * An adapter handling the underlying data
 * will most likely handle this interface.
 *
 * @author Eric Harlow
 */
interface DropListener {

    /**
     * Called when an item is to be dropped.
     * @param from - index item started at.
     * @param to - index to place item at.
     */
    fun onDrop(from: Int, to: Int)
}