package com.ericharlow.DragNDrop

/**
 * Implement to handle removing items.
 * An adapter handling the underlying data
 * will most likely handle this interface.
 *
 * @author Eric Harlow
 */
interface RemoveListener {

    /**
     * Called when an item is to be removed
     * @param which - indicates which item to remove.
     */
    fun onRemove(which: Int)
}