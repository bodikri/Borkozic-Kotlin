package com.jhlabs.map.proj

class UniversalTransverseMercatorProjection : TransverseMercatorProjection() {
    override fun initialize() {
        // TODO
        // if (!P->es) E_ERROR(-34);
        if (utmzone < 0) {
            val zone = getZoneFromNearestMeridian(projectionLongitude * RTD).toInt()
            setUTMZone(zone)
        }
        super.initialize()
    }

    fun setIsSouth(south: Boolean) {
        falseNorthing = if (south) 10000000.0 else 0.0
    }

    override fun toString(): String {
        return "Universal Transverse Mercator"
    }
}