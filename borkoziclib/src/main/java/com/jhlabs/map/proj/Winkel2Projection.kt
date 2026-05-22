/*
 * Winkel2Projection.kt
 *
 * Created on July 17, 2007, 9:04 AM
 *
 */

package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath

/**
 * Ported from Proj4 by Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
class Winkel2Projection : PseudoCylindricalProjection() {
    
    /**
     * latitude of true scale on central meridian. Default: arccos(2/Pi)
     */
    private var phi1 = Math.acos(2.0 / Math.PI)
    
    /**
     * cosine of latitude of true scale on central meridian
     */
    private var cosphi1 = 2.0 / Math.PI
    
    companion object {
        private const val MAX_ITER = 10
        private const val LOOP_TOL = 1e-7
        private const val TWO_D_PI = 0.636619772367581343
    }
    
    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        
        var lpphiVar = lpphi
        out.y = lpphiVar * TWO_D_PI
        val k = Math.PI * Math.sin(lpphiVar)
        lpphiVar *= 1.8
        for (i in MAX_ITER downTo 1) {
            val V = (lpphiVar + Math.sin(lpphiVar) - k) / (1.0 + Math.cos(lpphiVar))
            lpphiVar -= V
            if (Math.abs(V) < LOOP_TOL)
                break
        }
        if (lpphiVar == 0.0)
            lpphiVar = if (lpphi < 0.0) -MapMath.HALFPI else MapMath.HALFPI
        else
            lpphiVar *= 0.5
        out.x = 0.5 * lplam * (Math.cos(lpphiVar) + cosphi1)
        out.y = MapMath.QUARTERPI * (Math.sin(lpphiVar) + out.y)
        
        return out
        
    }

    fun setLatitudeOfTrueScale(phi1: Double) {
        if (phi1 < -MapMath.HALFPI || phi1 > MapMath.HALFPI)
            throw ProjectionException()
        this.phi1 = phi1
        this.cosphi1 = Math.cos(this.phi1)
    }
    
    override fun toString(): String {
        return "Winkel II"
    }
    
}