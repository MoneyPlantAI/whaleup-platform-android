package com.whaleup.gameshub.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.whaleup.gameshub.messaging.toMap
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

private const val TAG = "APIBridge"

/**
 * Enhanced API bridge with composite API support and retry logic.
 * Kotlin port of Whaleup's APIBridge.ts.
 *
 * Features:
 * - Composite API envelope/unwrap (POST to single endpoint, routes inside payload)
 * - Linear backoff retry (2 retries, 10s initial, 5s increment, 30s max)
 * - Auth token and user-agent injection
 * - Error handler callback for host notification
 */
object APIBridge {
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    var baseUrl: String = ""
        set(value) {
            field = if (value.endsWith("/")) value.substring(0, value.length - 1) else value
            Log.d(TAG, "Base URL updated to: $field")
        }

    var authToken: String? = null
        set(value) {
            field = value
            Log.d(TAG, "Auth token ${if (value != null) "set" else "cleared"}")
        }

    var userAgent: String? = null

    private var sessionId: String? = null

    fun setSessionId(id: String?) {
        val usableId = id?.takeIf { it.isUsableSessionId() }
        sessionId = usableId
        Log.d(TAG, "Host Session ID ${if (usableId != null) "set" else "cleared"}")
    }

    fun resetSession() {
        sessionId = null
        authToken = null
        userAgent = null
    }

    private fun String.isUsableSessionId(): Boolean =
        isNotBlank() && this != "sessionId"
    var compositeEndpoint: String = "/api/1/whaleup/games"
        set(value) {
            field = if (value.startsWith("/")) value else "/$value"
            Log.d(TAG, "Composite endpoint updated to: $field")
        }

    var timezone: String = "Asia/Kolkata" // Default

    // Retry configuration matching defaults
    private var maxRetries = 2
    private var initialDelayMs = 10_000L
    private var delayIncrementMs = 5_000L
    private var maxDelayMs = 30_000L

    private val executor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())

    var onError: ((APIError) -> Unit)? = null

    /**
     * Make a composite API call with retry logic.
     * Wraps the request in the composite envelope and unwraps the response.
     */
    fun compositeRequest(
        method: String,
        endpoint: HubEndpoint,
        data: Map<String, Any?>? = null,
        callback: APICallback,
        gameModeType: String? = null
    ) {
        executor.execute {
            try {
                val payload = if (data != null) JSONObject(data) else JSONObject()
                Log.d(TAG, "Executing composite request: ${endpoint.routeUri} $payload")
                val envelope = JSONObject().apply {
                    put("requestMethod", method)
                    put("routeUri", endpoint.routeUri)
                    put("payload", payload)
                }

                val result = executeWithRetry(
                    "POST",
                    compositeEndpoint,
                    envelope.toString(),
                    0,
                    "route: ${endpoint.routeUri}",
                    gameModeType
                )
                val response = JSONObject(result)

                Log.d(TAG, "Full composite response for ${endpoint.routeUri}: $result")

                val isSuccess = response.optBoolean("success", response.optBoolean("isSuccess", response.has("data")))
                if (!isSuccess) {
                    val errorData = response.optJSONObject("data")
                    val errorObject = response.optJSONObject("error")
                    val errorMsg = errorData?.optString("errorMessage")?.takeIf { it.isNotBlank() }
                        ?: errorObject?.optString("message")?.takeIf { it.isNotBlank() }
                        ?: response.optString("error").takeIf { it.isNotBlank() }
                        ?: response.optString("message").takeIf { it.isNotBlank() }
                        ?: "Composite API request failed"
                    Log.e(TAG, "Composite request unsuccessful: $errorMsg")
                    mainHandler.post { callback.onError(-1, errorMsg) }
                    return@execute
                }

                val responseData = response.optJSONObject("data") ?: response.optJSONObject("body") ?: response
                Log.d(TAG, "Extracted data for ${endpoint.routeUri}: $responseData")
                mainHandler.post { callback.onSuccess(responseData.toString()) }
            } catch (e: Exception) {
                Log.e(TAG, "Composite request failed for ${endpoint.routeUri}", e)
                if (endpoint == HubEndpoint.GAME_ENDED && e.message?.contains("HTTP 409") == true) {
                    Log.i(TAG, "Treating HTTP 409 for GAME_ENDED as success")
                    mainHandler.post {
                        callback.onSuccess("{\"success\":true}")
                    }
                    return@execute
                }
                val apiError = APIError(
                    message = e.message ?: "Unknown error",
                    endpoint = endpoint.routeUri,
                    method = method
                )
                mainHandler.post {
                    onError?.invoke(apiError)
                    callback.onError(-1, e.message ?: "Unknown error")
                }
            }
        }
    }

    /**
     * Simple GET request (for non-composite endpoints like catalog).
     */
    fun get(endpoint: HubEndpoint, callback: APICallback) {
        executor.execute {
            try {
                val result = executeWithRetry("GET", endpoint.path, null, 0)
                mainHandler.post { callback.onSuccess(result) }
            } catch (e: Exception) {
                mainHandler.post { callback.onError(-1, e.message ?: "Unknown Error") }
            }
        }
    }

    /**
     * Simple POST request (for non-composite endpoints).
     */
    fun post(endpoint: HubEndpoint, body: String, callback: APICallback) {
        executor.execute {
            try {
                val result = executeWithRetry("POST", endpoint.path, body, 0)
                mainHandler.post { callback.onSuccess(result) }
            } catch (e: Exception) {
                mainHandler.post { callback.onError(-1, e.message ?: "Unknown Error") }
            }
        }
    }

    // region Typed API Methods

    fun getUserProfile(data: Map<String, Any?>?, callback: APICallback) {
        compositeRequest("GET", HubEndpoint.GET_USER_PROFILE, data, callback)
    }

    fun gameStarted(data: Map<String, Any?>?, gameModeType: String, callback: APICallback) {
        compositeRequest("POST", HubEndpoint.GAME_STARTED, data, callback, gameModeType)
    }

    fun gameStarted(data: Map<String, Any?>?, callback: APICallback) {
        gameStarted(data, "SP", callback)
    }

    fun gameEnded(data: Map<String, Any?>?, gameModeType: String, callback: APICallback) {
        compositeRequest("POST", HubEndpoint.GAME_ENDED, data, callback, gameModeType)
    }

    fun gameEnded(data: Map<String, Any?>?, callback: APICallback) {
        gameEnded(data, "SP", callback)
    }

    /*
    fun claimGullak(data: Map<String, Any?>?, callback: APICallback) {
        compositeRequest("POST", HubEndpoint.CLAIM_GULLAK, data, callback)
    }
    */

    fun getConfig(callback: APICallback) {
        compositeRequest("GET", HubEndpoint.GET_CONFIG, emptyMap(), callback)
    }

    fun getGullak(userId: String, callback: APICallback) {
        withRequiredUserId(userId, "get Gullak", callback) { payload ->
            compositeRequest("GET", HubEndpoint.GET_GULLAK, payload, callback)
        }
    }

    fun claimGullak(userId: String, callback: APICallback) {
        withRequiredUserId(userId, "claim reward", callback) { payload ->
            compositeRequest("POST", HubEndpoint.CLAIM_GULLAK, payload, callback)
        }
    }

    fun videoWatched(userId: String, callback: APICallback) {
        withRequiredUserId(userId, "record video watch", callback) { payload ->
            compositeRequest("POST", HubEndpoint.VIDEO_WATCHED, payload, callback)
        }
    }

    fun logClientError(data: Map<String, Any?>, callback: APICallback) {
        val message = data["errorMessage"]?.toString()?.trim().orEmpty()
        if (message.isEmpty()) {
            mainHandler.post { callback.onError(400, "Cannot log client error without errorMessage") }
            return
        }
        compositeRequest("POST", HubEndpoint.LOG_CLIENT_ERROR, data, callback)
    }

    fun getStrings(callback: APICallback) {
        compositeRequest("GET", HubEndpoint.GET_STRINGS, emptyMap(), callback)
    }

    private fun withRequiredUserId(
        userId: String,
        action: String,
        callback: APICallback,
        block: (Map<String, Any?>) -> Unit
    ) {
        val normalized = userId.trim()
        if (normalized.isEmpty()) {
            mainHandler.post { callback.onError(400, "Cannot $action without a userId") }
            return
        }
        block(mapOf("userId" to normalized))
    }

    suspend fun getMultiplayerTicketSuspend(playerId: String, engineUrl: String): String {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val payload = JSONObject().apply { put("playerId", playerId) }
            val targetUrl = if (engineUrl.endsWith("/")) "${engineUrl}api/v1/session" else "$engineUrl/api/v1/session"
            val url = URL(targetUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("accept", "*/*")
            
            connection.doOutput = true
            val writer = OutputStreamWriter(connection.outputStream, "UTF-8")
            writer.write(payload.toString())
            writer.flush()
            writer.close()
            
            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8"))
                val result = reader.use { it.readText() }
                val json = JSONObject(result)
                val data = json.getJSONObject("data")
                data.getString("ticket")
            } else {
                val errorStream = connection.errorStream
                val errorMsg = if (errorStream != null) {
                    BufferedReader(InputStreamReader(errorStream, "UTF-8")).use { it.readText() }
                } else {
                    "HTTP $responseCode"
                }
                throw Exception("Local engine session failed: $errorMsg")
            }
        }
    }
    // endregion

    /**
     * Execute HTTP request with linear backoff retry.
     * Runs on background thread — DO NOT call from main thread.
     */
    private fun executeWithRetry(
        method: String,
        path: String,
        body: String?,
        attempt: Int,
        context: String? = null,
        gameModeType: String? = null
    ): String {
        if (baseUrl.isEmpty()) {
            throw IllegalStateException("[APIBridge] Base URL not set. Please provide apiBaseUrl in UserConfig.")
        }
        if (!isNetworkAvailable()) {
            throw NetworkUnavailableException()
        }

        var connection: HttpURLConnection? = null
        try {
            val url = URL("$baseUrl$path")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            // Set headers
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("accept", "*/*")
            authToken?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            userAgent?.let { connection.setRequestProperty("X-User-Agent", it) }
            connection.setRequestProperty("X-User-Timezone", timezone)
            gameModeType?.let {
                connection.setRequestProperty("X-GAME-MODE-TYPE", it)
            }
            sessionId?.let { connection.setRequestProperty("sessionId", it) }
            // Print all request headers
//            Log.d(TAG, "===== REQUEST HEADERS =====")
//            connection.requestProperties.forEach { (key, values) ->
//                Log.d(TAG, "$key: ${values.joinToString(", ")}")
//            }
//            Log.d(TAG, "===========================")

            // Write body for POST/PUT
            if (body != null && (method == "POST" || method == "PUT")) {
                connection.doOutput = true
                val writer = OutputStreamWriter(connection.outputStream, "UTF-8")
                writer.write(body)
                writer.flush()
                writer.close()
            }

            Log.d(TAG, "$method $path (attempt ${attempt + 1}) - Body: $body")

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8"))
                val result = reader.use { it.readText() }
                Log.d(TAG, "$method $path SUCCESS - Response: $result")
                return result
            } else {
                val errorStream = connection.errorStream
                var errorBody = ""
                if (errorStream != null) {
                    val reader = BufferedReader(InputStreamReader(errorStream))
                    errorBody = reader.use { it.readText() }
                }
                val finalErrorMsg = if (errorBody.isNotEmpty()) "HTTP $responseCode: $errorBody" else "HTTP $responseCode: ${connection.responseMessage}"
                val logPrefix = if (context != null) "APIBridge ($context)" else "APIBridge"
                Log.e(TAG, "$logPrefix - Error Response Body ($responseCode) for $baseUrl$path: $errorBody")
                if (responseCode in 400..499 && responseCode != 408 && responseCode != 429) {
                    throw NonRetryableException(finalErrorMsg)
                } else {
                    throw Exception(finalErrorMsg)
                }
            }
        } catch (e: Exception) {
            if (e is NonRetryableException || e is NetworkUnavailableException) {
                throw e
            }
            if (!isNetworkAvailable()) {
                throw NetworkUnavailableException()
            }
            val isLastAttempt = attempt >= maxRetries

            if (isLastAttempt) {
                Log.e(TAG, "$method $path - Failed after ${attempt + 1} attempts", e)
                throw e
            }

            // Calculate backoff delay
            val delay = minOf(initialDelayMs + attempt * delayIncrementMs, maxDelayMs)
            val urlString = baseUrl + path
            val contextInfo = if (context != null) "($context) " else ""
            Log.w(TAG, "$method $urlString ${contextInfo}- Attempt ${attempt + 1} failed, retrying in ${delay}ms: ${e.message}")
            Thread.sleep(delay)

            return executeWithRetry(
                method, path, body, attempt + 1, context, gameModeType
            )
        } finally {
            connection?.disconnect()
        }
    }

    fun getLeaderboard(data: Map<String, Any?>? = null, callback: APICallback) {
        compositeRequest("GET", HubEndpoint.GET_LEADERBOARD, data, callback)
    }

    private fun isNetworkAvailable(): Boolean {
        val context = appContext ?: return true
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

class NonRetryableException(message: String) : Exception(message)
class NetworkUnavailableException : Exception("No internet connection")

data class APIError(
    val message: String,
    val statusCode: Int? = null,
    val endpoint: String,
    val method: String
)

interface APICallback {
    fun onSuccess(response: String)
    fun onError(code: Int, message: String)
}
