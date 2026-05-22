package com.borkozic.data

class Situation {
    @JvmField var speed: Double = 0.0
    @JvmField var track: Double = 0.0
    @JvmField var altitude: Double = 0.0
    @JvmField var time: Long = 0
    @JvmField var name: String? = null
    @JvmField var id: Long = 0
    @JvmField var latitude: Double = 0.0
    @JvmField var longitude: Double = 0.0
    @JvmField var silent: Boolean = false

    constructor() {
        speed = 0.0
        track = 0.0
        time = 0
        altitude = 0.0
    }

    constructor(name: String) : this() {
        this.name = name
    }
}
