/*
 * This file was semi-automatically converted from the public-domain USGS PROJ source.
 */
/**
 * Added isEqualArea by Bernhard Jenny, May 19 2010.
 */
package com.jhlabs.map.proj

class QuarticAuthalicProjection : STSProjection(2.0, 2.0, false) {

    override fun isEqualArea(): Boolean {
        return true
    }

    override fun toString(): String {
        return "Quartic Authalic"
    }
}