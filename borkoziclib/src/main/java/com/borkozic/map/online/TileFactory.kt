package com.borkozic.map.online

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import com.borkozic.BaseApplication
import com.borkozic.map.Tile
import com.borkozic.map.TileRAMCache
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class TileFactory {
    companion object {
        private const val MAX_REDIRECTS = 5

        @JvmStatic
        fun downloadTile(provider: TileProvider, x: Int, y: Int, z: Byte): Bitmap? {
            val url = provider.getTileUri(x, y, z)
            Log.d("TILE_DEBUG", "Attempting to download: $url")

            var connection: HttpURLConnection? = null
            var redirectCount = 0
            var currentUrl = url

            try {
                while (redirectCount < MAX_REDIRECTS) {
                    val tileUrl = URL(currentUrl)
                    connection = tileUrl.openConnection() as HttpURLConnection
                    connection.setRequestProperty("User-Agent", "Borkozic/1.0 (Android)")
                    connection.connectTimeout = 50000
                    connection.readTimeout = 30000
                    connection.instanceFollowRedirects = false // управляваме пренасочванията ръчно

                    val status = connection.responseCode

                    // Проверка за пренасочване (3xx)
                    if (status == HttpURLConnection.HTTP_MOVED_PERM ||
                        status == HttpURLConnection.HTTP_MOVED_TEMP ||
                        status == HttpURLConnection.HTTP_SEE_OTHER ||
                        status == 307 || status == 308
                    ) {
                        val newUrl = connection.getHeaderField("Location")
                        Log.d("TILE_DEBUG", "Redirect ($status) to: $newUrl")

                        connection.disconnect()
                        connection = null
                        currentUrl = newUrl
                        redirectCount++
                        continue
                    }

                    // Ако не е 200 OK, отказваме
                    if (status != HttpURLConnection.HTTP_OK) {
                        Log.w("TILE_DEBUG", "Server returned HTTP $status for $currentUrl")
                        return null
                    }

                    // Проверка на Content-Type
                    val contentType = connection.contentType
                    if (contentType == null || !contentType.startsWith("image/")) {
                        Log.w("TILE_DEBUG", "Content-Type is not image: $contentType")
                        return null
                    }

                    // Декодиране на изображението
                    val bitmap = BitmapFactory.decodeStream(connection.inputStream)
                    if (bitmap != null) {
                        Log.d("TILE_DEBUG", "Download successful: $x,$y zoom=$z")
                    } else {
                        Log.w("TILE_DEBUG", "Download returned null (bitmap decoding failed)")
                    }
                    return bitmap
                }

                Log.w("TILE_DEBUG", "Too many redirects for: $url")
                return null
            } catch (e: Exception) {
                Log.e("TILE_DEBUG", "Download failed: ${e.message}", e)
                return null
            } finally {
                connection?.disconnect()
            }
        }

        @JvmStatic
        fun downloadTile(provider: TileProvider, t: Tile) {
            val bitmap = downloadTile(provider, t.x, t.y, t.zoomLevel)
            if (bitmap != null) {
                t.bitmap = bitmap
                t.generated = false
            }
        }

        @JvmStatic
        fun loadTile(provider: TileProvider, tx: Int, ty: Int, z: Byte): ByteArray? {
            val application = BaseApplication.getApplication<BaseApplication>() ?: return null

            val filename = "$z${File.separator}$tx-$ty"
            val file = File(
                application.rootPath + File.separator + "tiles" + File.separator +
                        provider.code + File.separator + filename
            )
            if (!file.exists()) return null
            try {
                val fileInputStream = FileInputStream(file)
                val dat = ByteArray(file.length().toInt())
                fileInputStream.read(dat)
                fileInputStream.close()
                return dat
            } catch (e: IOException) {
                e.printStackTrace()
            }
            return null
        }

        @JvmStatic
        fun loadTile(provider: TileProvider, t: Tile) {
            val data = loadTile(provider, t.x, t.y, t.zoomLevel)
            if (data != null) {
                Log.d(
                    "TILE_DEBUG",
                    "Tile loaded from disk: ${t.x},${t.y} zoom=${t.zoomLevel} data size=${data.size}"
                )
                t.bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                if (t.bitmap == null) {
                    Log.w("TILE_DEBUG", "BitmapFactory.decodeByteArray returned null – possibly corrupt file")
                }
            } else {
                Log.d("TILE_DEBUG", "Tile NOT found on disk: ${t.x},${t.y} zoom=${t.zoomLevel}")
            }
        }

        @JvmStatic
        fun generateTile(provider: TileProvider, cache: TileRAMCache, t: Tile) {
            var parentTileZoom = (t.zoomLevel - 1).toByte()
            var parentTileX = t.x / 2
            var parentTileY = t.y / 2
            var scale = 2

            // Search for parent tile
            while (parentTileZoom >= 0) {
                val parentTile = Tile(parentTileX, parentTileY, parentTileZoom)

                val cached = if (cache.containsKey(parentTile.getKey())) cache[parentTile.getKey()] else null
                val workingTile = cached ?: parentTile.also { loadTile(provider, it) }

                if (workingTile.bitmap != null && scale <= workingTile.bitmap!!.width && scale <= workingTile.bitmap!!.height) {
                    val matrix = Matrix()
                    matrix.postScale(scale.toFloat(), scale.toFloat())

                    val miniTileWidth = workingTile.bitmap!!.width / scale
                    val miniTileHeight = workingTile.bitmap!!.height / scale
                    val fromX = (t.x % scale) * miniTileWidth
                    val fromY = (t.y % scale) * miniTileHeight

                    // Create mini bitmap which will be stretched to tile
                    val miniTileBitmap = Bitmap.createBitmap(
                        workingTile.bitmap!!, fromX, fromY, miniTileWidth, miniTileHeight
                    )

                    // Create tile bitmap from mini bitmap
                    t.bitmap = Bitmap.createBitmap(
                        miniTileBitmap, 0, 0, miniTileWidth, miniTileHeight, matrix, false
                    )
                    t.generated = true
                    miniTileBitmap.recycle()
                    break
                }
                parentTileZoom = (parentTileZoom - 1).toByte()
                parentTileX /= 2
                parentTileY /= 2
                scale *= 2
            }
        }

        @JvmStatic
        fun saveTile(provider: TileProvider, dat: ByteArray, tx: Int, ty: Int, z: Byte) {
            val application = BaseApplication.getApplication<BaseApplication>() ?: return

            val filename = "$tx-$ty"
            var file = File(
                application.rootPath + File.separator + "tiles" + File.separator +
                        provider.code + File.separator + z + File.separator
            )
            file.mkdirs()
            file = File(file.absolutePath + File.separator + filename)
            if (!file.exists()) {
                try {
                    val fileOutputStream = FileOutputStream(file)
                    fileOutputStream.write(dat)
                    fileOutputStream.flush()
                    fileOutputStream.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }

        @JvmStatic
        fun saveTile(provider: TileProvider, t: Tile) {
            if (t.bitmap != null && !t.bitmap!!.isRecycled) {
                val bos = ByteArrayOutputStream()
                t.bitmap!!.compress(CompressFormat.PNG, 0 /*ignored for PNG*/, bos)
                val data = bos.toByteArray()
                Log.d(
                    "TILE_DEBUG",
                    "Saving tile to disk: ${t.x},${t.y} zoom=${t.zoomLevel} size=${data.size}"
                )
                saveTile(provider, data, t.x, t.y, t.zoomLevel)
            } else {
                Log.w(
                    "TILE_DEBUG",
                    "Cannot save tile – bitmap is null or recycled: ${t.x},${t.y}"
                )
            }
        }
    }
}
