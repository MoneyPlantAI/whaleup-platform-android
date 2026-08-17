package com.whaleup.gameshub.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import android.widget.ImageView
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

private const val RAW_GITHUB_PREFIX = "https://raw.githubusercontent.com/"

internal fun normalizeImageUrl(value: String): String? {
    val normalized = value.trim()
        .removeSurrounding("[", "]")
        .trim()
        .removeSurrounding("\"")
        .removeSurrounding("'")
        .trim()
    val parsedUrl = runCatching { URL(normalized) }.getOrNull() ?: return null
    return normalized.takeIf { parsedUrl.protocol == "http" || parsedUrl.protocol == "https" }
}

internal fun imageUrlCandidates(url: String): List<String> = buildList {
    if (url.startsWith(RAW_GITHUB_PREFIX)) {
        val parts = url.removePrefix(RAW_GITHUB_PREFIX).split('/', limit = 4)
        if (parts.size == 4) {
            add("https://cdn.jsdelivr.net/gh/${parts[0]}/${parts[1]}@${parts[2]}/${parts[3]}")
        }
    }
    add(url)
}.distinct()

internal fun isRetryableImageResponse(responseCode: Int): Boolean =
    responseCode == -1 || responseCode == 408 || responseCode == 425 ||
        responseCode == 429 || responseCode in 500..599

object ImageLoader {
    private const val TAG = "ImageLoader"
    private const val MAX_ATTEMPTS = 3
    private const val MAX_REDIRECTS = 5
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000

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
        val cleanUrl = normalizeImageUrl(url)
        if (cleanUrl == null) {
            imageView.tag = null
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
        imageView.setImageBitmap(null)

        executor.execute {
            val bitmap = imageUrlCandidates(cleanUrl).firstNotNullOfOrNull { candidateUrl ->
                loadWithRetry(candidateUrl)
            }

            if (bitmap != null) {
                memoryCache.put(cleanUrl, bitmap)
            }

            mainHandler.post {
                if (imageView.tag == cleanUrl) {
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                    }
                    onComplete?.invoke(bitmap != null)
                }
            }
        }
    }

    private fun loadWithRetry(url: String): Bitmap? {
        repeat(MAX_ATTEMPTS) { attempt ->
            var connection: HttpURLConnection? = null
            try {
                connection = openFollowingRedirects(url)
                val responseCode = connection.responseCode

                // Hike's loader decodes the input stream directly. Keep that behavior for -1:
                // some Android network stacks expose a usable response body without a status line.
                if (responseCode == -1 || responseCode in 200..299) {
                    val bitmap = connection.inputStream.buffered().use(BitmapFactory::decodeStream)
                    if (bitmap != null) return bitmap
                    Log.w(TAG, "Unable to decode image from $url")
                    return null
                }

                val shouldRetry = isRetryableImageResponse(responseCode)
                if (!shouldRetry || attempt == MAX_ATTEMPTS - 1) {
                    Log.e(TAG, "HTTP $responseCode fetching image $url")
                    return null
                }

                sleepBeforeRetry(attempt, connection.getHeaderField("Retry-After"))
            } catch (error: IOException) {
                if (attempt == MAX_ATTEMPTS - 1) {
                    Log.e(TAG, "Error fetching image from $url after $MAX_ATTEMPTS attempts", error)
                    return null
                }
                sleepBeforeRetry(attempt, null)
            } catch (error: Exception) {
                Log.e(TAG, "Error fetching image from $url", error)
                return null
            } finally {
                connection?.disconnect()
            }
        }
        return null
    }

    private fun openFollowingRedirects(initialUrl: String): HttpURLConnection {
        var currentUrl = URL(initialUrl)
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val connection = currentUrl.openConnection() as HttpURLConnection
            try {
                connection.apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    doInput = true
                    instanceFollowRedirects = false
                    setRequestProperty("Accept", "image/*")
                    setRequestProperty("User-Agent", "WhaleUp-Android-SDK")
                    connect()
                }
            } catch (error: Exception) {
                connection.disconnect()
                throw error
            }
            val responseCode = try {
                connection.responseCode
            } catch (error: Exception) {
                connection.disconnect()
                throw error
            }
            if (responseCode !in REDIRECT_CODES) return connection

            val location = connection.getHeaderField("Location")
            if (location.isNullOrBlank() || redirectCount == MAX_REDIRECTS) {
                return connection
            }
            try {
                currentUrl = URL(currentUrl, location)
            } finally {
                connection.disconnect()
            }
        }
        error("Unreachable")
    }

    private fun sleepBeforeRetry(attempt: Int, retryAfter: String?) {
        val retryAfterMs = retryAfter?.toLongOrNull()?.times(1_000)
        val exponentialDelayMs = 500L shl attempt
        val delayMs = (retryAfterMs ?: exponentialDelayMs).coerceAtMost(5_000L)
        try {
            Thread.sleep(delayMs)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
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

    private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
}
