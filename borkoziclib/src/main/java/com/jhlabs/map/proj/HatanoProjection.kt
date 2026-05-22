/*
Copyright 2006 Jerry Huxtable

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

/*
 * This file was semi-automatically converted from the public-domain USGS PROJ source.
 *
 * Bernhard Jenny, 23 September 2010: changed name to Hatano Asymmetrical, added
 * isEqualArea
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath

class HatanoProjection : Projection() {

    companion object {
        private const val NITER = 20
        private const val EPS = 1e-7
        private const val ONETOL = 1.000001
        private const val CN = 2.67595
        private const val CS = 2.43763
        private const val RCN = 0.37369906014686373063
        private const val RCS = 0.41023453108141924738
        private const val FYCN = 1.75859
        private const val FYCS = 1.93052
        private const val RYCN = 0.56863737426006061674
        private const val RYCS = 0.51799515156538134803
        private const val FXC = 0.85
        private const val RXC = 1.17647058823529411764
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        var lpphiVar = lpphi
        val c: Double = Math.sin(lpphiVar) * if (lpphiVar < 0.0) CS else CN
        var th1: Double
        var i = NITER

        while (i > 0) {
            th1 = (lpphiVar + Math.sin(lpphiVar) - c) / (1.0 + Math.cos(lpphiVar))
            lpphiVar -= th1
            if (Math.abs(th1) < EPS) {
                break
            }
            i--
        }
        out.x = FXC * lplam * Math.cos(lpphiVar * 0.5)
        out.y = Math.sin(lpphiVar * 0.5) * if (lpphiVar < 0.0) FYCS else FYCN
        return out
    }

    override fun projectInverse(xyx: Double, xyy: Double, out: Point2D.Double): Point2D.Double {
        var th = xyy * if (xyy < 0.0) RYCS else RYCN

        if (Math.abs(th) > 1.0) {
            if (Math.abs(th) > ONETOL) {
                throw ProjectionException("I")
            } else {
                th = if (th > 0.0) MapMath.HALFPI else -MapMath.HALFPI
            }
        } else {
            th = Math.asin(th)
        }
        out.x = RXC * xyx / Math.cos(th)
        th += th
        out.y = (th + Math.sin(th)) * if (xyy < 0.0) RCS else RCN

        if (Math.abs(out.y) > 1.0) {
            if (Math.abs(out.y) > ONETOL) {
                throw ProjectionException("I")
            } else {
                out.y = if (out.y > 0.0) MapMath.HALFPI else -MapMath.HALFPI
            }
        } else {
            out.y = Math.asin(out.y)
        }
        return out
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun isEqualArea(): Boolean {
        return true
    }

    override fun toString(): String {
        return "Hatano Asymmetrical"
    }
}