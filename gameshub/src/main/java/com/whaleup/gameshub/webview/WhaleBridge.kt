package com.whaleup.gameshub.webview

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.whaleup.gameshub.messaging.BiomeMessage
import com.whaleup.gameshub.messaging.MessageRouter
import com.whaleup.gameshub.messaging.RouteAction
import org.json.JSONObject

private const val TAG = "WhaleBridge"

/**
 * JavaScript bridge injected into the WebView as `window.WhaleBridge`.
 * 
 * Receives messages from WebView JavaScript, parses them via MessageRouter,
 * and dispatches RouteActions to the ActionProcessor.
 *
 * Also supports sending messages from native → WebView via evaluateJavascript.
 */
class WhaleBridge(
    private val context: Context,
    private val actionProcessor: ActionProcessor
) {

    private var webView: WebView? = null

    fun attachWebView(webView: WebView) {
        this.webView = webView
    }

    /**
     * Called from JavaScript: WhaleBridge.postMessage(jsonString)
     */
    @JavascriptInterface
    fun postMessage(messageJson: String) {
        try {
            Log.d(TAG, "Received: $messageJson")
            val message = MessageRouter.parseMessage(messageJson)
            if (message == null) {
                Log.w(TAG, "Could not parse message: $messageJson")
                return
            }

            val actions = MessageRouter.route(message)
            // Process on main thread for UI-related actions
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                actionProcessor.processActions(actions)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing message", e)
        }
    }

    /**
     * Send a message from native to WebView.
     * Dispatches a custom event that the web content listens for.
     */
    fun sendMessageToWebView(message: BiomeMessage) {
        val json = JSONObject().apply {
            put("type", message.type)
            put("action", message.action)
            if (message.data != null) put("data", JSONObject.wrap(message.data))
        }

        val js = """
            (function() {
                var event = new MessageEvent('message', { data: ${json.toString()} });
                window.dispatchEvent(event);
            })();
        """.trimIndent()

        Log.d(TAG, "Sending message to WebView: type=${message.type}, action=${message.action}, data=${message.data}")
        webView?.post {
            webView?.evaluateJavascript(js, null)
        }
    }

    /**
     * Send a message with arbitrary data (Map or List/Array) to WebView.
     */
    fun sendMessageToWebView(type: String, action: String, data: Any? = null) {
        val json = JSONObject().apply {
            put("type", type)
            put("action", action)
            if (data != null) put("data", JSONObject.wrap(data))
        }

        val js = """
            (function() {
                var event = new MessageEvent('message', { data: ${json.toString()} });
                window.dispatchEvent(event);
            })();
        """.trimIndent()

        Log.d(TAG, "Sending message to WebView: type=$type, action=$action, data=$data")
        webView?.post {
            webView?.evaluateJavascript(js, null)
        }
    }
}

/**
 * Interface for processing route actions dispatched by the message router.
 * Implemented by HubWebViewActivity (or a dedicated processor).
 */
interface ActionProcessor {
    fun processActions(actions: List<RouteAction>)
}
