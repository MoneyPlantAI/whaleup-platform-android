package com.whaleup.gameshub.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
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

    fun load(url: String, imageView: ImageView) {
        // Handle empty or null URLs by clearing the view or letting the caller handle it
        if (url.isEmpty()) {
            imageView.setImageBitmap(null)
            return
        }

        // Check cache first
        val cachedBitmap = memoryCache.get(url)
        if (cachedBitmap != null) {
            imageView.setImageBitmap(cachedBitmap)
            return
        }

        // Set placeholder (optional) usually handled by views, here we clear just in case
        imageView.setImageBitmap(null)
        imageView.tag = url // Tag to prevent wrong image on recycled view

        executor.execute {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.doInput = true
                connection.connect()
                val input = connection.inputStream
                val bitmap = BitmapFactory.decodeStream(input)
                
                if (bitmap != null) {
                    memoryCache.put(url, bitmap)
                    
                    mainHandler.post {
                        if (imageView.tag == url) {
                            imageView.setImageBitmap(bitmap)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
