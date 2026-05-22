/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2012  Andrey Novikov <http://andreynovikov.info/>
 *
 * This file is part of Androzic application.
 *
 * Androzic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * Androzic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with Androzic.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.borkozic.map

import com.jcraft.jzlib.JZlib
import com.jcraft.jzlib.ZStream
import android.util.Log
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

object OzfDecoder {
    const val OZFX3_KEY_MAX = 256
    const val OZFX3_MAGIC_OFFSET_0 = 14
    const val OZFX3_MAGIC_OFFSET_1 = 165
    const val OZFX3_MAGIC_OFFSET_2 = 0xA2
    const val OZFX3_MAGIC_BLOCKLENGTH_0 = 150
    const val OZFX3_KEY_BLOCK_SIZE = 4

    const val D0_KEY_CYCLE = 0xD
    const val D1_KEY_CYCLE = 0x1A
    const val OZFX3_ZDATA_ENCRYPTION_LENGTH = 16

    const val OZF_TILE_WIDTH = 64
    const val OZF_TILE_HEIGHT = 64
    private const val TILE_SIZE_32 = OZF_TILE_WIDTH * OZF_TILE_HEIGHT

    private val d0_key = byteArrayOf(
        0x2D.toByte(), 0x4A.toByte(), 0x43.toByte(),
        0xF1.toByte(), 0x27.toByte(), 0x9B.toByte(), 0x69.toByte(), 0x4F.toByte(),
        0x36.toByte(), 0x52.toByte(), 0x87.toByte(), 0xEC.toByte(),
        0x5F.toByte(), 0x8D.toByte(), 0x40.toByte(), 0x00.toByte()
    )

    private val d1_key = byteArrayOf(
        0x2D.toByte(), 0x4A.toByte(), 0x43.toByte(),
        0xF1.toByte(), 0x27.toByte(), 0x9B.toByte(), 0x69.toByte(), 0x4F.toByte(),
        0x36.toByte(), 0x52.toByte(), 0x87.toByte(), 0xEC.toByte(),
        0x5F.toByte(), 0x42.toByte(), 0x53.toByte(), 0x22.toByte(),
        0x9E.toByte(), 0x8B.toByte(), 0x2D.toByte(), 0x83.toByte(),
        0x3D.toByte(), 0xD2.toByte(), 0x84.toByte(), 0xBA.toByte(),
        0xD8.toByte(), 0x5B.toByte(), 0x8B.toByte(), 0xC0.toByte()
    )

    private val zip = ZStream()
    private val pixels = IntArray(TILE_SIZE_32)

    @JvmField
    var useNativeCalls = true

    @JvmStatic
    fun readByte(reader: RandomAccessFile): Byte {
        return reader.readByte()
    }

    @JvmStatic
    fun readShort(reader: RandomAccessFile): Int {
        val s = reader.readShort()
        return ((s.toInt() ushr 8) and 0x00FF) or ((s.toInt() shl 8) and 0xFF00)
    }

    @JvmStatic
    fun readInt(reader: RandomAccessFile): Int {
        val i = reader.readInt()
        return (i ushr 24) or (i shl 24) or ((i shl 8) and 0x00FF0000) or ((i ushr 8) and 0x0000FF00)
    }

    private fun getShort(buffer: ByteArray, pos: Int): Int {
        return (buffer[pos].toInt() and 0xFF) or ((buffer[pos + 1].toInt() and 0xFF) shl 8)
    }

    private fun getInt(buffer: ByteArray, pos: Int): Int {
        return (buffer[pos].toInt() and 0xFF) or
               ((buffer[pos + 1].toInt() and 0xFF) shl 8) or
               ((buffer[pos + 2].toInt() and 0xFF) shl 16) or
               ((buffer[pos + 3].toInt() and 0xFF) shl 24)
    }

    @JvmName("scale_dx")
    @JvmStatic
    fun scaleDx(file: OzfFile, scale: Int): Int {
        return file.images!![scale].width
    }

    @JvmName("scale_dy")
    @JvmStatic
    fun scaleDy(file: OzfFile, scale: Int): Int {
        return file.images!![scale].height
    }

    @JvmName("num_tiles_per_x")
    @JvmStatic
    fun numTilesPerX(file: OzfFile, scale: Int): Int {
        return file.images!![scale].xtiles
    }

    @JvmName("num_tiles_per_y")
    @JvmStatic
    fun numTilesPerY(file: OzfFile, scale: Int): Int {
        return file.images!![scale].ytiles
    }

    @JvmStatic
    fun getTile(file: OzfFile, scale: Int, x: Int, y: Int, w: Int, h: Int): IntArray? {
        if (scale > file.scales - 1)
            return null

        if (x > file.images!![scale].xtiles - 1)
            return null

        if (y > file.images!![scale].ytiles - 1)
            return null

        if (x < 0)
            return null

        if (y < 0)
            return null

        val i = y * file.images!![scale].xtiles + x

        if (useNativeCalls) {
            return getTileNative(
                file.fileptr, file.type, file.key,
                file.images!![scale].encryption_depth,
                file.scales_table!![scale],
                i, w, h,
                file.images!![scale].palette
            )
        }

        val tile: ByteArray
        val tilesize: Int

        try {
            file.reader.seek(file.scales_table!![scale].toLong())
            file.reader.skipBytes(1036)
            file.reader.skipBytes(i * 4)

            val tilepos: Int
            val tilepos1: Int

            if (file.type == OzfFile.OZF_STREAM_ENCRYPTED) {
                val buffer = ByteArray(4)
                file.reader.read(buffer)
                ozfDecode1(buffer, 4, file.key.toByte())
                tilepos = getInt(buffer, 0)
                file.reader.read(buffer)
                ozfDecode1(buffer, 4, file.key.toByte())
                tilepos1 = getInt(buffer, 0)
            } else {
                tilepos = readInt(file.reader)
                tilepos1 = readInt(file.reader)
            }

            tilesize = tilepos1 - tilepos
            tile = ByteArray(tilesize)

            file.reader.seek(tilepos.toLong())
            file.reader.read(tile)
        } catch (e: IOException) {
            Log.e("OZF", "Tile read io error")
            e.printStackTrace()
            return null
        }

        if (file.type == OzfFile.OZF_STREAM_ENCRYPTED) {
            if (file.images!![scale].encryption_depth == -1)
                ozfDecode1(tile, tilesize, file.key.toByte())
            else
                ozfDecode1(tile, file.images!![scale].encryption_depth, file.key.toByte())
        }

        if (!(tile[0] == 0x78.toByte() && (tile[1].toInt() and 0xFF) == 0xDA)) {
            return null
        }

        val decompressedSize = OZF_TILE_WIDTH * OZF_TILE_HEIGHT
        val decompressed = ByteArray(decompressedSize)

        zip.next_in = tile
        zip.avail_in = tilesize
        zip.next_in_index = 0
        zip.next_out = decompressed
        zip.avail_out = decompressedSize
        zip.next_out_index = 0

        zip.inflateInit()
        val err = zip.inflate(JZlib.Z_FINISH)
        if (err != JZlib.Z_OK) {
            if (zip.msg != null) Log.e("OZF", zip.msg + " " + err)
        }
        zip.inflateEnd()

        val palette = file.images!![scale].palette

        var tileX = 0

        for (j in 0 until OZF_TILE_WIDTH * OZF_TILE_HEIGHT) {
            val c = decompressed[j].toInt() and 0xFF
            val r = palette[c * 4 + 2].toInt()
            val g = palette[c * 4 + 1].toInt()
            val b = palette[c * 4 + 0].toInt()
            val a = 255

            val k = ((OZF_TILE_WIDTH - 1) - (j / OZF_TILE_WIDTH)) * OZF_TILE_WIDTH + tileX
            pixels[k] = (a shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

            tileX++
            if (tileX == OZF_TILE_WIDTH)
                tileX = 0
        }

        return pixels
    }

    private fun ozfDecode1(source: ByteArray, n: Int, key: Byte) {
        for (j in 0 until n) {
            val k = j % D1_KEY_CYCLE
            var c = d1_key[k]
            c = (c + (key.toInt() and 0xFF)).toByte()
            c = (c.toInt() xor (source[j].toInt() and 0xFF)).toByte()
            source[j] = c
        }
    }

    @JvmStatic
    @Throws(IOException::class, OutOfMemoryError::class)
    fun open(file: File): OzfFile {
        val reader = RandomAccessFile(file, "r")
        val magic = readShort(reader)
        var type = -1
        when (magic) {
            0x7778 -> type = OzfFile.OZF_STREAM_DEFAULT
            0x7780 -> type = OzfFile.OZF_STREAM_ENCRYPTED
        }
        val ozfFile = OzfFile(file, reader, type)

        if (ozfFile.type == OzfFile.OZF_STREAM_ENCRYPTED) {
            ozfFile.key = calculateKey(ozfFile.reader)
            initEncryptedStream(ozfFile)
        } else if (ozfFile.type == OzfFile.OZF_STREAM_DEFAULT) {
            initRawStream(ozfFile)
        } else {
            throw IllegalArgumentException("Unsupported map image format")
        }

        if (useNativeCalls) {
            ozfFile.fileptr = openImageNative(file.absolutePath)
        }

        return ozfFile
    }

    @Throws(IOException::class, OutOfMemoryError::class)
    private fun initRawStream(ozfFile: OzfFile) {
        val reader = ozfFile.reader
        val h = ozfFile.ozf2!!

        reader.seek(0L)

        h.magic = readShort(reader)
        h.dummy1 = readInt(reader)
        h.dummy2 = readInt(reader)
        h.dummy3 = readInt(reader)
        h.dummy4 = readInt(reader)
        h.width = readInt(reader)
        h.height = readInt(reader)
        h.depth = readShort(reader)
        h.bpp = readShort(reader)
        h.dummy5 = readInt(reader)
        h.memsiz = readInt(reader)
        h.dummy6 = readInt(reader)
        h.dummy7 = readInt(reader)
        h.dummy8 = readInt(reader)
        h.version = readInt(reader)

        Log.d("OZF", "decoded ozf2 header:")
        Log.d("OZF", "\twidth:\t${h.width}")
        Log.d("OZF", "\theight:\t${h.height}")
        Log.d("OZF", "\tdepth:\t${h.depth}")
        Log.d("OZF", "\tbpp:\t${h.bpp}")

        val offset = ozfFile.size - 4
        Log.d("OZF", "Offset:$offset")
        reader.seek(offset)
        val scalesTableOffset = readInt(reader)

        Log.d("OZF", "scales table starts at: $scalesTableOffset")

        ozfFile.scales = ((ozfFile.size - scalesTableOffset - 4) / 4).toInt()
        Log.d("OZF", "scales total: ${ozfFile.scales}")

        ozfFile.scales_table = IntArray(ozfFile.scales)
        ozfFile.newImages()

        reader.seek(scalesTableOffset.toLong())
        for (i in 0 until ozfFile.scales) {
            ozfFile.scales_table!![i] = readInt(reader)
        }

        for (i in 0 until ozfFile.scales) {
            Log.d("OZF", "scale $i header starts at: ${ozfFile.scales_table!![i]}")
            reader.seek(ozfFile.scales_table!![i].toLong())

            ozfFile.images!![i].width = readInt(reader)
            ozfFile.images!![i].height = readInt(reader)
            ozfFile.images!![i].xtiles = readShort(reader)
            ozfFile.images!![i].ytiles = readShort(reader)

            Log.d("OZF", "\twidth:\t${ozfFile.images!![i].width}")
            Log.d("OZF", "\theight:\t${ozfFile.images!![i].height}")
            Log.d("OZF", "\ttiles per x:\t${ozfFile.images!![i].xtiles}")
            Log.d("OZF", "\ttiles per y:\t${ozfFile.images!![i].ytiles}")

            ozfFile.images!![i].palette = ByteArray(256 * 4)
            reader.read(ozfFile.images!![i].palette)
        }
    }

    @Throws(IOException::class, OutOfMemoryError::class)
    private fun initEncryptedStream(ozfFile: OzfFile) {
        var buffer: ByteArray
        val reader = ozfFile.reader

        Log.d("OZF", "processing encrypted stream\n")

        reader.seek(OZFX3_MAGIC_OFFSET_0.toLong())
        val bytesPerInfoBlock = reader.readByte().toInt() and 0xFF
        Log.d("OZF", "bytes per info block: $bytesPerInfoBlock")

        val offset = OZFX3_MAGIC_OFFSET_1 + bytesPerInfoBlock - OZFX3_MAGIC_BLOCKLENGTH_0 + 4
        reader.seek(offset.toLong())

        buffer = ByteArray(4 + 4 + 4 + 2 + 2)
        reader.read(buffer)
        ozfDecode1(buffer, buffer.size, ozfFile.key.toByte())

        val h3 = ozfFile.ozf3!!
        h3.size = getInt(buffer, 0)
        h3.width = getInt(buffer, 4)
        h3.height = getInt(buffer, 8)
        h3.depth = getShort(buffer, 12)
        h3.bpp = getShort(buffer, 14)

        Log.d("OZF", "decoded ozf3 header:")
        Log.d("OZF", "\tsize:\t${h3.size}")
        Log.d("OZF", "\twidth:\t${h3.width}")
        Log.d("OZF", "\theight:\t${h3.height}")
        Log.d("OZF", "\tdepth:\t${h3.depth}")
        Log.d("OZF", "\tbpp:\t${h3.bpp}")

        val offset2 = (ozfFile.size - 4).toInt()
        reader.seek(offset2.toLong())

        buffer = ByteArray(4)
        reader.read(buffer)
        ozfDecode1(buffer, buffer.size, ozfFile.key.toByte())

        val scalesTableOffset = getInt(buffer, 0)
        Log.d("OZF", "scales table starts at: $scalesTableOffset")

        ozfFile.scales = ((ozfFile.size - scalesTableOffset - 4) / 4).toInt()
        Log.d("OZF", "scales total: ${ozfFile.scales}")

        if (h3.size < 0 || h3.width < 0 || h3.height < 0 || h3.bpp < 0 || h3.depth < 0) {
            throw IOException("Couldn't decode OZFX3 file")
        }

        ozfFile.scales_table = IntArray(ozfFile.scales)
        ozfFile.newImages()

        reader.seek(scalesTableOffset.toLong())
        for (i in 0 until ozfFile.scales) {
            buffer = ByteArray(4)
            reader.read(buffer)
            ozfDecode1(buffer, 4, ozfFile.key.toByte())
            ozfFile.scales_table!![i] = getInt(buffer, 0)
        }

        for (i in 0 until ozfFile.scales) {
            Log.d("OZF", "scale $i header starts at: ${ozfFile.scales_table!![i]}")
            reader.seek(ozfFile.scales_table!![i].toLong())

            buffer = ByteArray(4)
            reader.read(buffer)
            ozfDecode1(buffer, 4, ozfFile.key.toByte())
            ozfFile.images!![i].width = getInt(buffer, 0)

            reader.read(buffer)
            ozfDecode1(buffer, 4, ozfFile.key.toByte())
            ozfFile.images!![i].height = getInt(buffer, 0)

            buffer = ByteArray(2)
            reader.read(buffer)
            ozfDecode1(buffer, 2, ozfFile.key.toByte())
            ozfFile.images!![i].xtiles = getShort(buffer, 0) and 0x0000FFFF

            reader.read(buffer)
            ozfDecode1(buffer, 2, ozfFile.key.toByte())
            ozfFile.images!![i].ytiles = getShort(buffer, 0) and 0x0000FFFF

            Log.d("OZF", "\twidth:\t${ozfFile.images!![i].width}")
            Log.d("OZF", "\theight:\t${ozfFile.images!![i].height}")
            Log.d("OZF", "\ttiles per x:\t${ozfFile.images!![i].xtiles}")
            Log.d("OZF", "\ttiles per y:\t${ozfFile.images!![i].ytiles}")

            ozfFile.images!![i].palette = ByteArray(256 * 4)
            reader.read(ozfFile.images!![i].palette)
            ozfDecode1(ozfFile.images!![i].palette, 256 * 4, ozfFile.key.toByte())

            val tiles = IntArray(2)

            buffer = ByteArray(4)
            for (j in 0 until 2) {
                reader.read(buffer)
                ozfDecode1(buffer, 4, ozfFile.key.toByte())
                tiles[j] = getInt(buffer, 0)
            }

            val tilesize = tiles[1] - tiles[0]
            val tile = ByteArray(tilesize)

            reader.seek(tiles[0].toLong())
            reader.read(tile)

            ozfFile.images!![i].encryption_depth = getEncyptionDepth(tile, tilesize, ozfFile.key)
            Log.d("OZF", "\tencryption depth:\t${ozfFile.images!![i].encryption_depth}")
        }
    }

    @Throws(IOException::class)
    private fun calculateKey(reader: RandomAccessFile): Int {
        var key = 0
        var initial = 0
        val keyblock = ByteArray(OZFX3_KEY_BLOCK_SIZE)

        reader.seek(OZFX3_MAGIC_OFFSET_0.toLong())
        val bytesPerInfo = reader.readByte().toInt() and 0xFF

        reader.seek(OZFX3_MAGIC_OFFSET_2.toLong())
        initial = reader.readByte().toInt() and 0xFF

        val offset = (OZFX3_MAGIC_OFFSET_1 + bytesPerInfo - OZFX3_MAGIC_BLOCKLENGTH_0).toLong()
        reader.seek(offset)
        reader.read(keyblock)

        ozfDecode1(keyblock, OZFX3_KEY_BLOCK_SIZE, initial.toByte())

        Log.d("OZF", "key block: " + String.format("%#x", keyblock[0].toInt() and 0xFF))

        when (keyblock[0].toInt() and 0xFF) {
            0xf1 -> initial += 0x8a
            0x18, 0x54 -> initial += 0xa0
            0x56 -> initial += 0xb9
            0x43 -> initial += 0x6a
            0x83 -> initial += 0xa4
            0xc5 -> initial += 0x7e
            0x38 -> initial += 0xc1
            0x76 -> throw IOException("Couldn't decode OZFX3 file")
        }

        key = initial
        return key
    }

    private fun getEncyptionDepth(data: ByteArray, size: Int, key: Int): Int {
        var nEncryptionDepth = -1

        val p = ByteArray(size)
        val nDecompressed = OZF_TILE_WIDTH * OZF_TILE_HEIGHT
        val pDecompressed = ByteArray(nDecompressed)

        for (i in 4..size) {
            System.arraycopy(data, 0, p, 0, size)
            ozfDecode1(p, i, key.toByte())

            nEncryptionDepth = i
            if (decompressTile(pDecompressed, p))
                break
        }

        if (nEncryptionDepth == size)
            nEncryptionDepth = -1

        return nEncryptionDepth
    }

    private fun decompressTile(dest: ByteArray, source: ByteArray): Boolean {
        zip.next_in = source
        zip.avail_in = source.size
        zip.next_in_index = 0
        zip.next_out = dest
        zip.avail_out = dest.size
        zip.next_out_index = 0

        zip.inflateInit()
        val err = zip.inflate(JZlib.Z_FINISH)
        if (err != JZlib.Z_OK && err != JZlib.Z_STREAM_END) {
            return false
        }
        zip.inflateEnd()
        return true
    }

    @JvmStatic
    fun close(file: OzfFile) {
        try {
            file.reader.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        if (useNativeCalls) {
            closeImageNative(file.fileptr)
        }
    }

    @JvmStatic
    @Synchronized
    private external fun openImageNative(path: String): Long

    @JvmStatic
    @Synchronized
    private external fun closeImageNative(ptr: Long)

    @JvmStatic
    @Synchronized
    private external fun getTileNative(
        ptr: Long, type: Int, key: Int, depth: Int,
        offset: Int, i: Int, w: Int, h: Int, palette: ByteArray
    ): IntArray

    init {
        System.loadLibrary("ozfdecoder")
    }
}
