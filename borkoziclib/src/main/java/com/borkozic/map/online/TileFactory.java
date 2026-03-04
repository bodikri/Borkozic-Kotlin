package com.borkozic.map.online;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.util.Log;

import com.borkozic.BaseApplication;
import com.borkozic.map.Tile;
import com.borkozic.map.TileRAMCache;

public class TileFactory
{
	private static final int MAX_REDIRECTS = 5;

	public static Bitmap downloadTile(TileProvider provider, int x, int y, byte z)
	{
		String url = provider.getTileUri(x, y, z);
		Log.d("TILE_DEBUG", "Attempting to download: " + url);

		HttpURLConnection connection = null;
		int redirectCount = 0;
		String currentUrl = url;

		try
		{
			while (redirectCount < MAX_REDIRECTS) {
				URL tileUrl = new URL(currentUrl);
				connection = (HttpURLConnection) tileUrl.openConnection();
				connection.setRequestProperty("User-Agent", "Borkozic/1.0 (Android)");
				connection.setConnectTimeout(50000);
				connection.setReadTimeout(30000);
				connection.setInstanceFollowRedirects(false); // управляваме пренасочванията ръчно

				int status = connection.getResponseCode();

				// Проверка за пренасочване (3xx)
				if (status == HttpURLConnection.HTTP_MOVED_PERM ||
						status == HttpURLConnection.HTTP_MOVED_TEMP ||
						status == HttpURLConnection.HTTP_SEE_OTHER ||
						status == 307 || status == 308) {
					String newUrl = connection.getHeaderField("Location");
					Log.d("TILE_DEBUG", "Redirect (" + status + ") to: " + newUrl);

					connection.disconnect();
					connection = null;
					currentUrl = newUrl;
					redirectCount++;
					continue;
				}

				// Ако не е 200 OK, отказваме
				if (status != HttpURLConnection.HTTP_OK) {
					Log.w("TILE_DEBUG", "Server returned HTTP " + status + " for " + currentUrl);
					return null;
				}

				// Проверка на Content-Type
				String contentType = connection.getContentType();
				if (contentType == null || !contentType.startsWith("image/")) {
					Log.w("TILE_DEBUG", "Content-Type is not image: " + contentType);
					return null;
				}

				// Декодиране на изображението
				Bitmap bitmap = BitmapFactory.decodeStream(connection.getInputStream());
				if (bitmap != null) {
					Log.d("TILE_DEBUG", "Download successful: " + x + "," + y + " zoom=" + z);
				} else {
					Log.w("TILE_DEBUG", "Download returned null (bitmap decoding failed)");
				}
				return bitmap;
			}

			Log.w("TILE_DEBUG", "Too many redirects for: " + url);
			return null;
		}
		catch (Exception e)
		{
			Log.e("TILE_DEBUG", "Download failed: " + e.getMessage(), e);
			return null;
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}

	public static void downloadTile(TileProvider provider, Tile t)
	{
		Bitmap bitmap = downloadTile(provider, t.x, t.y, t.zoomLevel);
		if ( bitmap != null )
		{
			t.bitmap = bitmap;
			t.generated = false;
		}
	}

	public static byte[] loadTile(TileProvider provider, int tx, int ty, byte z)
	{
		BaseApplication application = BaseApplication.getApplication();
		if (application == null)
			return null;

		String filename = z + File.separator + tx + "-" + ty;
		File file = new File(application.getRootPath() + File.separator + "tiles" + File.separator + provider.code + File.separator + filename);
		if (file.exists() == false)
			return null;
		try
		{
			FileInputStream fileInputStream;
			fileInputStream = new FileInputStream(file);
			byte[] dat = new byte[(int) file.length()];
			fileInputStream.read(dat);
			fileInputStream.close();
			return dat;
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		return null;
	}

	public static void loadTile(TileProvider provider, Tile t)
	{
		byte[] data = loadTile(provider, t.x, t.y, t.zoomLevel);
		if (data != null) {
			Log.d("TILE_DEBUG", "Tile loaded from disk: " + t.x + "," + t.y + " zoom=" + t.zoomLevel + " data size=" + data.length);
			t.bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
			if (t.bitmap == null) {
				Log.w("TILE_DEBUG", "BitmapFactory.decodeByteArray returned null – possibly corrupt file");
			}
		} else {
			Log.d("TILE_DEBUG", "Tile NOT found on disk: " + t.x + "," + t.y + " zoom=" + t.zoomLevel);
		}
	}

	public static void generateTile(TileProvider provider, TileRAMCache cache, Tile t)
	{
		byte parentTileZoom = (byte) (t.zoomLevel - 1);
		int parentTileX = t.x / 2, parentTileY = t.y / 2, scale = 2;

		// Search for parent tile
		for (; parentTileZoom >= 0; parentTileZoom--, parentTileX /= 2, parentTileY /= 2, scale *= 2)
		{
			Tile parentTile = new Tile(parentTileX, parentTileY, parentTileZoom);

			if (cache.containsKey(parentTile.getKey()))
				parentTile = cache.get(parentTile.getKey());
			else
				TileFactory.loadTile(provider, parentTile);

			if (parentTile.bitmap != null && scale <= parentTile.bitmap.getWidth() && scale <= parentTile.bitmap.getHeight())
			{
				Matrix matrix = new Matrix();
				matrix.postScale(scale, scale);

				int miniTileWidth = parentTile.bitmap.getWidth() / scale;
				int miniTileHeight = parentTile.bitmap.getHeight() / scale;
				int fromX = (t.x % scale) * miniTileWidth;
				int fromY = (t.y % scale) * miniTileHeight;

				// Create mini bitmap which will be stretched to tile
				Bitmap miniTileBitmap = Bitmap.createBitmap(parentTile.bitmap, fromX, fromY, miniTileWidth, miniTileHeight);

				// Create tile bitmap from mini bitmap
				t.bitmap = Bitmap.createBitmap(miniTileBitmap, 0, 0, miniTileWidth, miniTileHeight, matrix, false);
				t.generated = true;
				miniTileBitmap.recycle();
				break;
			}
		}
	}

	public static void saveTile(TileProvider provider, byte[] dat, int tx, int ty, byte z)
	{
		BaseApplication application = BaseApplication.getApplication();
		if (application == null)
			return;

		String filename = tx + "-" + ty;
		File file = new File(application.getRootPath() + File.separator + "tiles" + File.separator + provider.code + File.separator + z + File.separator);
		file.mkdirs();
		file = new File(file.getAbsolutePath() + File.separator + filename);
		if (!file.exists())
		{
			FileOutputStream fileOutputStream;
			try
			{
				fileOutputStream = new FileOutputStream(file);
				fileOutputStream.write(dat);
				fileOutputStream.flush();
				fileOutputStream.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	public static void saveTile(TileProvider provider, Tile t)
	{
		if (t.bitmap != null && ! t.bitmap.isRecycled())
		{
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			t.bitmap.compress(CompressFormat.PNG, 0 /*ignored for PNG*/, bos);
			byte[] data = bos.toByteArray();
			Log.d("TILE_DEBUG", "Saving tile to disk: " + t.x + "," + t.y + " zoom=" + t.zoomLevel + " size=" + data.length);
			saveTile(provider, data, t.x, t.y, t.zoomLevel);
		}
		else
		{
			Log.w("TILE_DEBUG", "Cannot save tile – bitmap is null or recycled: " + t.x + "," + t.y);
		}
	}
}