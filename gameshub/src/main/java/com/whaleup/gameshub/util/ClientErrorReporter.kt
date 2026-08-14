package com.whaleup.gameshub.util

import android.os.Build
import com.whaleup.gameshub.data.BiomeState
import com.whaleup.gameshub.data.SDKError
import com.whaleup.gameshub.network.APIBridge
import com.whaleup.gameshub.network.APICallback
import com.whaleup.gameshub.network.APIError
import java.util.concurrent.atomic.AtomicBoolean

/** Sends native SDK/API failures to log/client-error without recursive logging. */
object ClientErrorReporter {
    private val inFlight = AtomicBoolean(false)

    fun reportApi(error: APIError) {
        if (error.endpoint == "log/client-error") return
        report(
            errorMessage = error.message,
            errorName = "[api] ${error.method} ${error.endpoint}",
            apiName = error.endpoint,
            statusCode = error.statusCode
        )
    }

    fun reportSdk(error: SDKError) {
        if (error.action == "networkRestored") return
        val endpoint = error.data?.get("endpoint")?.toString()
        if (endpoint == "log/client-error") return
        report(
            errorMessage = error.data?.get("reason")?.toString()
                ?: error.data?.get("message")?.toString()
                ?: error.action,
            errorName = "[${error.type}] ${error.action}",
            apiName = endpoint ?: error.action,
            statusCode = (error.data?.get("statusCode") as? Number)?.toInt()
        )
    }

    private fun report(errorMessage: String, errorName: String, apiName: String, statusCode: Int?) {
        val context = APIBridge.applicationContext ?: return
        if (!SdkErrorPresenter.isInternetAvailable(context) || !inFlight.compareAndSet(false, true)) return
        val payload = buildMap<String, Any?> {
            put("userId", BiomeState.getUserConfig()?.userId)
            put("statusCode", statusCode)
            put("errorMessage", errorMessage)
            put("apiName", apiName)
            put("errorName", errorName)
            put("device", "Android ${Build.VERSION.RELEASE}")
        }
        APIBridge.logClientError(payload, object : APICallback {
            override fun onSuccess(response: String) { inFlight.set(false) }
            override fun onError(code: Int, message: String) { inFlight.set(false) }
        })
    }
}
