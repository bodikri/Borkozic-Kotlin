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
package com.jhlabs.map

import java.io.Serializable
import kotlin.math.sqrt

/**
 * A class representing a geographic ellipsoid.
 * Changes: Added Serializable interface by Bernhard Jenny, 18 May 2010
 */
class Ellipsoid : Cloneable, Serializable {

    @JvmField var name: String? = null
    @JvmField var shortName: String? = null
    /**
     * semi-major axis (a)
     */
    @JvmField var equatorRadius = 1.0
    /**
     * semi-minor axis (b)
     */
    @JvmField var poleRadius = 1.0
    /**
     * eccentricity
     */
    @JvmField var eccentricity = 1.0
    /**
     * eccentricity squared
     */
    @JvmField var eccentricity2 = 1.0
    /**
     * flattening
     */
    @JvmField var flattening = 1.0

    constructor()

    // One of poleRadius or reciprocalFlattening must be specified, the other zero
    constructor(shortName: String?, equatorRadius: Double, poleRadius: Double, reciprocalFlattening: Double, name: String?) {
        this.shortName = shortName
        this.name = name
        this.equatorRadius = equatorRadius
        this.poleRadius = poleRadius
        if (reciprocalFlattening != 0.0) {
            flattening = 1.0 / reciprocalFlattening
            eccentricity2 = 2 * flattening - flattening * flattening
            this.poleRadius = equatorRadius * sqrt(1.0 - eccentricity2)
        } else {
            flattening = (equatorRadius - poleRadius) / equatorRadius
            eccentricity2 = 1.0 - (poleRadius * poleRadius) / (equatorRadius * equatorRadius)
        }
        eccentricity = sqrt(eccentricity2)
    }

    constructor(shortName: String?, equatorRadius: Double, eccentricity2: Double, name: String?) {
        this.shortName = shortName
        this.name = name
        this.equatorRadius = equatorRadius
        setEccentricitySquared(eccentricity2)
    }

    public override fun clone(): Any {
        return super.clone() as Ellipsoid
    }

    fun setEccentricitySquared(eccentricity2: Double) {
        this.eccentricity2 = eccentricity2
        poleRadius = equatorRadius * sqrt(1.0 - eccentricity2)
        eccentricity = sqrt(eccentricity2)
        flattening = (equatorRadius - poleRadius) / equatorRadius
    }

    fun getEccentricitySquared(): Double {
        return eccentricity2
    }

    override fun toString(): String {
        return name ?: ""
    }

    companion object {
        // From: USGS PROJ package
        // Populated with NIMA 8350.2 4 July 1977 and MADTRAN 1 October 1996 
        @JvmField val SPHERE = Ellipsoid("sphere", 6371008.7714, 6371008.7714, 0.0, "Sphere")
        @JvmField val AIRY = Ellipsoid("airy", 6377563.396, 6356256.910, 299.3249646, "Airy 1830")
        @JvmField val AIRY_MOD = Ellipsoid("airymod", 6377340.189, 0.0, 299.3249646, "Airy 1830 Modified")
        @JvmField val AUSTRALIAN = Ellipsoid("australian", 6378160.0, 6356774.7, 298.25, "Australian National")
        @JvmField val BESSEL = Ellipsoid("bessel", 6377397.155, 0.0, 299.1528128, "Bessel 1841")
        @JvmField val BESSEL_NOR = Ellipsoid("bessel_nor", 6377492.0176, 0.0, 299.152800, "Bessel 1841 (Norway)")
        @JvmField val BESSEL_NAM = Ellipsoid("bessel_nam", 6377483.865, 0.0, 299.1528128, "Bessel 1841 (Namibia)")
        @JvmField val CLARKE_1858 = Ellipsoid("clrk58", 6378350.87, 0.0, 294.26, "Clarke 1858")
        @JvmField val CLARKE_1866 = Ellipsoid("clrk66", 6378206.4, 6356583.8, 294.9786982, "Clarke 1866")
        @JvmField val CLARKE_1880_MOD = Ellipsoid("clrk80mod", 6378249.145, 0.0, 293.4663, "Clarke 1880 mod.")
        @JvmField val CLARKE_1880 = Ellipsoid("clrk80", 6378249.145, 0.0, 293.465, "Clarke 1880")
        @JvmField val CLARKE_1880_PAL = Ellipsoid("clrk80pal", 6378300.789, 0.0, 293.466, "Clarke 1880 Palestine")
        @JvmField val CLARKE_1880_IGN = Ellipsoid("clrk80ign", 6378249.2, 0.0, 293.466021, "Clarke 1880 IGN")
        @JvmField val EVEREST_56 = Ellipsoid("evrst56", 6377301.243, 0.0, 300.8017, "Everest (India 1956)")
        @JvmField val EVEREST_30 = Ellipsoid("evrst30", 6377276.345, 0.0, 300.8017, "Everest (India 1830)")
        @JvmField val EVEREST_48 = Ellipsoid("evrst48", 6377304.063, 0.0, 300.8017, "Everest (Malay & Sing 1948)")
        @JvmField val EVEREST_69 = Ellipsoid("evrst69", 6377295.664, 0.0, 300.8017, "Everest (Malaysia 1969)")
        @JvmField val EVEREST_PA = Ellipsoid("evrstpa", 6377309.613, 0.0, 300.8017, "Everest (Pakistan)")
        @JvmField val EVEREST_SS = Ellipsoid("evrstss", 6377298.556, 0.0, 300.8017, "Everest (Sabah Sarawak)")
        @JvmField val HAYFORD = Ellipsoid("hayford", 6378388.0, 0.0, 296.959263, "Hayford 1909")
        @JvmField val HELMET = Ellipsoid("helmet", 6378200.0, 0.0, 298.3, "Helmert 1906")
        @JvmField val HOUGH = Ellipsoid("hough", 6378270.0, 0.0, 297.0, "Hough 1960")
        @JvmField val INDONESIAN = Ellipsoid("indonesian", 6378160.0, 0.0, 298.247, "Indonesian 1974")
        @JvmField val INTERNATIONAL_1924 = Ellipsoid("intl24", 6378388.0, 0.0, 297.0, "International 1924")
        @JvmField val INTERNATIONAL_1967 = Ellipsoid("intl67", 6378157.5, 6356772.2, 0.0, "International 1967")
        @JvmField val KRASOVSKY = Ellipsoid("krass", 6378245.0, 0.0, 298.3, "Krassovsky, 1940")
        @JvmField val FISCHER_MOD = Ellipsoid("fschr60m", 6378155.0, 0.0, 298.3, "Modified Fischer 1960")
        @JvmField val PLESSIS = Ellipsoid("plessis", 6376523.0, 0.0, 308.6409971, "Plessis 1817 (France)")
        @JvmField val SA_1969 = Ellipsoid("sa1969", 6378160.0, 0.0, 298.25, "South American 1969")
        @JvmField val WGS_1960 = Ellipsoid("WGS60", 6378165.0, 0.0, 298.3, "WGS 60")
        @JvmField val WGS_1966 = Ellipsoid("WGS66", 6378145.0, 0.0, 298.25, "WGS 66")
        @JvmField val WGS_1972 = Ellipsoid("WGS72", 6378135.0, 0.0, 298.26, "WGS 72")
        @JvmField val WGS_1984 = Ellipsoid("WGS84", 6378137.0, 0.0, 298.257223563, "WGS 84")
        @JvmField val GRS_1980 = Ellipsoid("GRS80", 6378137.0, 0.0, 298.257222101, "GRS 1980 (IUGG, 1980)")

        @JvmField val ellipsoids = arrayOf(
            SPHERE,
            Ellipsoid("MERIT", 6378137.0, 0.0, 298.257, "MERIT 1983"),
            Ellipsoid("SGS85", 6378136.0, 0.0, 298.257, "Soviet Geodetic System 85"),
            GRS_1980,
            Ellipsoid("IAU76", 6378140.0, 0.0, 298.257, "IAU 1976"),
            AIRY,
            Ellipsoid("APL4.9", 6378137.0, 0.0, 298.25, "Appl. Physics. 1965"),
            Ellipsoid("NWL9D", 6378145.0, 298.25, 0.0, "Naval Weapons Lab., 1965"),
            AIRY_MOD,
            Ellipsoid("andrae", 6377104.43, 300.0, 0.0, "Andrae 1876 (Den., Iclnd.)"),
            AUSTRALIAN,
            Ellipsoid("GRS67", 6378160.0, 0.0, 298.2471674270, "GRS 67 (IUGG 1967)"),
            BESSEL,
            BESSEL_NAM,
            CLARKE_1866,
            CLARKE_1880,
            Ellipsoid("CPM", 6375738.7, 0.0, 334.29, "Comm. des Poids et Mesures 1799"),
            Ellipsoid("delmbr", 6376428.0, 0.0, 311.5, "Delambre 1810 (Belgium)"),
            Ellipsoid("engelis", 6378136.05, 0.0, 298.2566, "Engelis 1985"),
            EVEREST_56,
            EVEREST_48,
            EVEREST_56,
            EVEREST_69,
            EVEREST_SS,
            Ellipsoid("fschr60", 6378166.0, 0.0, 298.3, "Fischer (Mercury Datum) 1960"),
            FISCHER_MOD,
            Ellipsoid("fschr68", 6378150.0, 0.0, 298.3, "Fischer 1968"),
            HELMET,
            HOUGH,
            Ellipsoid("intl09", 6378388.0, 0.0, 297.0, "International 1909 (Hayford)"),
            KRASOVSKY,
            Ellipsoid("kaula", 6378163.0, 0.0, 298.24, "Kaula 1961"),
            Ellipsoid("lerch", 6378139.0, 0.0, 298.257, "Lerch 1979"),
            Ellipsoid("mprts", 6397300.0, 0.0, 191.0, "Maupertius 1738"),
            INTERNATIONAL_1967,
            Ellipsoid("plessis", 6376523.0, 6355863.0, 0.0, "Plessis 1817 France)"),
            Ellipsoid("SEasia", 6378155.0, 6356773.3205, 0.0, "Southeast Asia"),
            Ellipsoid("walbeck", 6376896.0, 6355834.8467, 0.0, "Walbeck"),
            WGS_1960,
            WGS_1966,
            WGS_1972,
            WGS_1984,
            Ellipsoid("NAD27", 6378249.145, 0.0, 293.4663, "NAD27: Clarke 1880 mod."),
            Ellipsoid("NAD83", 6378137.0, 0.0, 298.257222101, "NAD83: GRS 1980 (IUGG, 1980)")
        )
    }
}