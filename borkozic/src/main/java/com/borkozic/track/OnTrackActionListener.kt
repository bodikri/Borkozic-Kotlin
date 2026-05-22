package com.borkozic.track

import com.borkozic.data.Track

interface OnTrackActionListener {
    fun onTrackEdit(track: Track)
    fun onTrackEditPath(track: Track)
    fun onTrackToRoute(track: Track)
    fun onTrackSave(track: Track)
}
