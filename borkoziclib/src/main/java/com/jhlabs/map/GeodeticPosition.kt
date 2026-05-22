package com.jhlabs.map

class GeodeticPosition {
    @JvmField var lat: Double = 0.0
    @JvmField var lon: Double = 0.0
    @JvmField var h: Double = 0.0

    constructor() {
        lat = 0.0
        lon = 0.0
        h = 0.0
    }

    constructor(lat: Double, lon: Double) {
        this.lat = lat
        this.lon = lon
        this.h = 0.0
    }

    constructor(lat: Double, lon: Double, h: Double) {
        this.lat = lat
        this.lon = lon
        this.h = h
    }
}