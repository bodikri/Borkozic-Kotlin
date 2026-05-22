package com.borkozic.map

import com.borkozic.util.FileList
import com.borkozic.util.MapFilenameFilter
import java.io.File
import java.io.IOException
import java.io.Serializable
import java.util.*

class MapIndex(path: String, charset: String) : Serializable {
    private val maps: ArrayList<Map>
    private val mapsRoot: String
    private val hashCode: Int
    private val comparator: Comparator<Map> = MapComparator()

    init {
        maps = ArrayList()
        mapsRoot = path
        val root = File(mapsRoot)
        val files = FileList.getFileListing(root, MapFilenameFilter())
        for (file in files) {
            try {
                maps.add(MapLoader.load(file, charset))
            } catch (e: IOException) {
                // TODO Auto-generated catch block
                e.printStackTrace()
            }
        }
        hashCode = getMapsHash(files)
    }

    companion object {
        private const val serialVersionUID: Long = 6L

        @JvmStatic
        fun getMapsHash(path: String): Int {
            val root = File(path)
            val files = FileList.getFileListing(root, MapFilenameFilter())
            return getMapsHash(files)
        }

        private fun getMapsHash(files: List<File>): Int {
            var result = 13
            for (file in files) {
                result = 31 * result + file.absolutePath.hashCode()
            }
            return result
        }
    }

    override fun hashCode(): Int {
        return hashCode
    }

    fun addMap(map: Map) {
        if (!maps.contains(map)) {
            val iter = maps.listIterator()
            iter.add(map)
        }
    }

    fun removeMap(map: Map) {
        val iter = maps.listIterator()
        while (iter.hasNext()) {
            val m = iter.next()
            if (m == map) {
                iter.remove()
                return
            }
        }
    }

    fun getCoveringMaps(refMap: Map, area: Map.Bounds, covered: Boolean, bestmap: Boolean): List<Map> {
        val llmaps: MutableList<Map> = ArrayList()

        for (map in maps) {
            if (map.mpp > 200 || map == refMap) continue
            val ratio = refMap.mpp / map.mpp
            if ((!covered && ratio > 0.2 || ratio > 1) && (bestmap || !covered && ratio < 5) && map.containsArea(area)) {
                llmaps.add(map)
            }
        }

        Collections.sort(llmaps, comparator)
        Collections.reverse(llmaps)

        return llmaps
    }

    fun getMaps(lat: Double, lon: Double): List<Map> {
        val llmaps: MutableList<Map> = ArrayList()

        for (map in maps) {
            if (map.coversLatLon(lat, lon)) {
                llmaps.add(map)
            }
        }

        Collections.sort(llmaps, comparator)

        return llmaps
    }

    fun getMaps(): List<Map> {
        return maps
    }

    fun cleanBadMaps() {
        val iter = maps.listIterator()
        while (iter.hasNext()) {
            val map = iter.next()
            if (map.loadError != null) {
                iter.remove()
            }
        }
    }

    private inner class MapComparator : Comparator<Map>, Serializable {
        private val serialVersionUID: Long = 1L

        override fun compare(o1: Map, o2: Map): Int {
            return java.lang.Double.compare(o1.mpp, o2.mpp)
        }
    }
}