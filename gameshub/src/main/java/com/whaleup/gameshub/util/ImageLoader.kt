package com.whaleup.gameshub.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object ImageLoader {
    private val memoryCache: LruCache<String, Bitmap>
    private val executor = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8 // Use 1/8th of available memory for cache
        memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }
    }

    fun load(url: String, imageView: ImageView, onComplete: ((Boolean) -> Unit)? = null) {
        val cleanUrl = url.trim().removeSurrounding("[", "]").removeSurrounding("\"", "'")
        if (cleanUrl.isEmpty() || !cleanUrl.startsWith("http")) {
            imageView.setImageBitmap(null)
            onComplete?.invoke(false)
            return
        }

        val cachedBitmap = memoryCache.get(cleanUrl)
        if (cachedBitmap != null) {
            imageView.setImageBitmap(cachedBitmap)
            onComplete?.invoke(true)
            return
        }

        imageView.tag = cleanUrl

        executor.execute {
            try {
                var currentUrl = cleanUrl
                var redirectCount = 0
                var bitmap: Bitmap? = null

                while (redirectCount < 5) {
                    val urlObj = URL(currentUrl)
                    val connection = urlObj.openConnection() as HttpURLConnection
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 15_000
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) Mobile")
                    connection.instanceFollowRedirects = true

                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                        responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                        responseCode == 307 || responseCode == 308) {
                        val newUrl = connection.getHeaderField("Location")
                        if (newUrl != null) {
                            currentUrl = if (newUrl.startsWith("http")) newUrl else URL(urlObj, newUrl).toString()
                            redirectCount++
                            continue
                        }
                    }

                    if (responseCode in 200..299) {
                        val bytes = connection.inputStream.use { it.readBytes() }
                        bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } else {
                        Log.e("ImageLoader", "HTTP $responseCode fetching image $currentUrl")
                    }
                    break
                }

                if (bitmap != null) {
                    memoryCache.put(cleanUrl, bitmap)
                    mainHandler.post {
                        if (imageView.tag == cleanUrl) {
                            imageView.setImageBitmap(bitmap)
                        }
                        onComplete?.invoke(true)
                    }
                } else {
                    mainHandler.post {
                        onComplete?.invoke(false)
                    }
                }
            } catch (e: Exception) {
                Log.e("ImageLoader", "Error fetching image from $cleanUrl", e)
                mainHandler.post {
                    onComplete?.invoke(false)
                }
            }
        }
    }

    fun loadAsset(path: String, imageView: ImageView) {
        try {
            val context = imageView.context
            val inputStream = context.assets.open(path)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            imageView.setImageBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
