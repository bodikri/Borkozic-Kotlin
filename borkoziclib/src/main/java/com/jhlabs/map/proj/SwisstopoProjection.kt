package com.jhlabs.map.proj

import com.jhlabs.Point2D

/*
 * Copyright 2010 Jerry Huxtable
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
class SwisstopoProjection : CylindricalProjection() {
    override fun project(lam: Double, phi: Double, xy: Point2D.Double): Point2D.Double {
        return super.project(lam, phi, xy)
    }
    override fun projectInverse(x: Double, y: Double, lp: Point2D.Double): Point2D.Double {
        return super.projectInverse(x, y, lp)
    }
    override fun hasInverse(): Boolean = true
    override fun toString(): String = "Swisstopo"
}
