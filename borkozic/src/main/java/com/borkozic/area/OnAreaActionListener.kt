package com.borkozic.area

import com.borkozic.data.Area

interface OnAreaActionListener {
    fun onAreaDetails(area: Area)
    fun onAreaEdit(area: Area)
    fun onAreaSave(area: Area)
    fun onAreaNavigate(area: Area)
    fun onAreaEditPath(area: Area)
}
