package com.whaleup.gameshub.util

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AlertDialog
import com.whaleup.gameshub.data.BiomeMessageAction
import com.whaleup.gameshub.data.BiomeMessageType
import com.whaleup.gameshub.data.GamesHubSession
import com.whaleup.gameshub.data.SDKError
import com.whaleup.gameshub.ui.GamesHubActivity
import com.whaleup.gameshub.webview.HubWebViewActivity

interface InternetErrorRetryHandler {
    fun retryAfterInternetError()
}

object SdkErrorPresenter {
    private const val TAG = "SdkErrorPresenter"
    const val DEVICE_OFFLINE_ERROR_CODE = "01"
    const val API_TIMEOUT_ERROR_CODE = "02"
    const val USER_CONFIG_ERROR_CODE = "03"
    const val DNS_LOOKUP_ERROR_CODE = "04"
    const val HOST_UNREACHABLE_ERROR_CODE = "05"
    const val CONNECTION_LOST_ERROR_CODE = "06"
    const val HTTP_CLIENT_ERROR_CODE = "07"
    const val HTTP_SERVER_ERROR_CODE = "08"
    const val RATE_LIMIT_ERROR_CODE = "09"
    const val EMPTY_RESPONSE_ERROR_CODE = "10"
    const val INVALID_URL_ERROR_CODE = "11"
    const val UNKNOWN_NETWORK_ERROR_CODE = "12"
    const val INTERNET_ERROR_MESSAGE = "Please check your internet connection and try again."

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeDialog: AlertDialog? = null

    fun report(context: Context?, error: SDKError) {
        GamesHubSession.props?.onWhaleupSDKError?.invoke(error)
        showDialog(context, error)
    }

    fun isInternetAvailable(context: Context): Boolean = hasActiveInternet(context)

    private fun showDialog(context: Context?, error: SDKError) {
        if (context == null) return

        mainHandler.post {
            val activity = context as? Activity
            if (activity?.isFinishing == true || activity?.isDestroyed == true) return@post

            try {
                activeDialog?.dismiss()

                val isInternetError = isInternetError(context, error)
                val errorCode = errorCodeFor(context, error)
                val dialog = AlertDialog.Builder(context)
                    .setTitle(if (isInternetError) "Internet Connection Issue ($errorCode)" else "GamesHub Error")
                    .setMessage(formatError(context, error, isInternetError))
                    .setPositiveButton(if (isInternetError) "CLOSE" else "Close SDK") { _, _ ->
                        (GamesHubSession.props?.onCloseSdk ?: GamesHubSession.props?.onClose)?.invoke()
                        closeSdkOwnedActivity(activity)
                    }
                    .setCancelable(false)
                    .show()

                activeDialog = dialog
            } catch (e: Exception) {
                Log.e(TAG, "Unable to show SDK error dialog", e)
            }
        }
    }

    private fun formatError(context: Context, error: SDKError, isInternetError: Boolean): String {
        if (isInternetError) {
            return formatInternetMessage()
        }

        val reason = error.data?.get("reason")?.toString()?.takeIf { it.isNotBlank() }
        val message = reason ?: "Something went wrong. Please close the SDK and try again."
        val errorCode = errorCodeFor(context, error)
        return "$message ($errorCode)"
    }

    fun formatInternetMessage(reason: String? = null): String {
        return reason?.takeIf { it.isNotBlank() } ?: INTERNET_ERROR_MESSAGE
    }

    fun errorCodeFor(context: Context, error: SDKError): String {
        return when {
            isUserConfigIssue(error) -> USER_CONFIG_ERROR_CODE
            isInvalidUrlIssue(error) -> INVALID_URL_ERROR_CODE
            !hasActiveInternet(context) || isDeviceOfflineIssue(error) -> DEVICE_OFFLINE_ERROR_CODE
            isDnsLookupIssue(error) -> DNS_LOOKUP_ERROR_CODE
            isHostUnreachableIssue(error) -> HOST_UNREACHABLE_ERROR_CODE
            isConnectionLostIssue(error) -> CONNECTION_LOST_ERROR_CODE
            isApiTimeoutIssue(error) -> API_TIMEOUT_ERROR_CODE
            isRateLimitIssue(error) -> RATE_LIMIT_ERROR_CODE
            isHttpClientIssue(error) -> HTTP_CLIENT_ERROR_CODE
            isHttpServerIssue(error) -> HTTP_SERVER_ERROR_CODE
            isEmptyResponseIssue(error) -> EMPTY_RESPONSE_ERROR_CODE
            else -> UNKNOWN_NETWORK_ERROR_CODE
        }
    }

    fun isInternetError(context: Context, error: SDKError): Boolean {
        if (!hasActiveInternet(context)) return true
        if (error.type == BiomeMessageType.NETWORK_INTERRUPTION) return true
        if (
            error.action == BiomeMessageAction.NETWORK_INTERRUPTED ||
            error.action == BiomeMessageAction.NETWORK_LOAD_ERROR ||
            error.action == BiomeMessageAction.RETRY_AFTER_NETWORK
        ) {
            return true
        }

        val hasNetworkMessage = errorTextValues(error).any { value ->
            val message = value.lowercase()
            message.contains("network") ||
                    message.contains("internet") ||
                    message.contains("unable to resolve host") ||
                    message.contains("failed to connect") ||
                    message.contains("connection refused") ||
                    message.contains("connection reset") ||
                    message.contains("cannot connect") ||
                    message.contains("cannot find host") ||
                    message.contains("dns") ||
                    message.contains("timeout") ||
                    message.contains("timed out") ||
                    message.contains("err_internet_disconnected") ||
                    message.contains("net::")
        }
        if (hasNetworkMessage) return true

        val webViewErrorCode = error.data?.get("code")?.toString()?.toIntOrNull()
        return webViewErrorCode in setOf(-2, -6, -8)
    }

    private fun isDeviceOfflineIssue(error: SDKError): Boolean {
        return errorTextValues(error).any { value ->
            val message = value.lowercase()
            message.contains("no internet") ||
                    message.contains("offline") ||
                    message.contains("not connected") ||
                    message.contains("err_internet_disconnected") ||
                    message.contains("network unavailable")
        }
    }

    private fun isApiTimeoutIssue(error: SDKError): Boolean {
        val webViewErrorCode = error.data?.get("code")?.toString()?.toIntOrNull()
        if (webViewErrorCode == -8) return true
        if (httpStatusCode(error) in setOf(408, 504)) return true

        return errorTextValues(error).any { value ->
            val message = value.lowercase()
            message.contains("timeout") || message.contains("timed out")
        }
    }

    private fun isDnsLookupIssue(error: SDKError): Boolean {
        val webViewErrorCode = error.data?.get("code")?.toString()?.toIntOrNull()
        if (webViewErrorCode == -2) return true

        return errorTextValues(error).any { value ->
            val message = value.lowercase()
            message.contains("dns") ||
                    message.contains("cannot find host") ||
                    message.contains("unknownhost") ||
                    message.contains("unable to resolve host") ||
                    message.contains("name not resolved") ||
                    message.contains("err_name_not_resolved")
        }
    }

    private fun isHostUnreachableIssue(error: SDKError): Boolean {
        val webViewErrorCode = error.data?.get("code")?.toString()?.toIntOrNull()
        if (webViewErrorCode == -6) return true

        return errorTextValues(error).any { value ->
            val message = value.lowercase()
            message.contains("failed to connect") ||
                    message.contains("cannot connect") ||
                    message.contains("connection refused") ||
                    message.contains("host unreachable") ||
                    message.contains("err_connection_refused") ||
                    message.contains("err_address_unreachable")
        }
    }

    private fun isConnectionLostIssue(error: SDKError): Boolean {
        return errorTextValues(error).any { value ->
            val message = value.lowercase()
            message.contains("connection reset") ||
                    message.contains("connection lost") ||
                    message.contains("network connection was lost") ||
                    message.contains("err_connection_reset") ||
                    message.contains("err_network_changed")
        }
    }

    private fun isRateLimitIssue(error: SDKError): Boolean {
        return httpStatusCode(error) == 429 || errorTextValues(error).any { it.lowercase().contains("too many requests") }
    }

    private fun isHttpClientIssue(error: SDKError): Boolean {
        val status = httpStatusCode(error) ?: return false
        return status in 400..499
    }

    private fun isHttpServerIssue(error: SDKError): Boolean {
        val status = httpStatusCode(error) ?: return false
        return status in 500..599
    }

    private fun isEmptyResponseIssue(error: SDKError): Boolean {
        return errorTextValues(error).any { value ->
            val message = value.lowercase()
            message.contains("no data received") ||
                    message.contains("empty response") ||
                    message.contains("end of input") ||
                    message.contains("unexpected end")
        }
    }

    private fun isInvalidUrlIssue(error: SDKError): Boolean {
        return errorTextValues(error).any { value ->
            val message = value.lowercase()
            message.contains("invalid url") ||
                    message.contains("malformedurl") ||
                    message.contains("no protocol") ||
                    message.contains("unknown protocol")
        }
    }

    private fun isUserConfigIssue(error: SDKError): Boolean {
        return errorTextValues(error).any { value ->
            val message = value.lowercase()
            message.contains("user config") ||
                    message.contains("userconfig") ||
                    message.contains("api base url") ||
                    message.contains("base url") ||
                    message.contains("apibaseurl") ||
                    message.contains("user profile or config")
        }
    }

    private fun httpStatusCode(error: SDKError): Int? {
        error.data?.get("statusCode")?.toString()?.toIntOrNull()?.let { return it }
        error.data?.get("httpStatus")?.toString()?.toIntOrNull()?.let { return it }
        error.data?.get("code")?.toString()?.toIntOrNull()?.takeIf { it in 100..599 }?.let { return it }

        val httpStatusPattern = Regex("""HTTP\s+(\d{3})""", RegexOption.IGNORE_CASE)
        return errorTextValues(error)
            .firstNotNullOfOrNull { value -> httpStatusPattern.find(value)?.groupValues?.getOrNull(1)?.toIntOrNull() }
    }

    private fun errorTextValues(error: SDKError): List<String> {
        return listOfNotNull(
            error.data?.get("reason")?.toString(),
            error.data?.get("message")?.toString(),
            error.data?.get("description")?.toString()
        )
    }

    private fun hasActiveInternet(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun closeSdkOwnedActivity(activity: Activity?) {
        when (activity) {
            is HubWebViewActivity,
            is GamesHubActivity -> activity.finish()
        }
    }
}
