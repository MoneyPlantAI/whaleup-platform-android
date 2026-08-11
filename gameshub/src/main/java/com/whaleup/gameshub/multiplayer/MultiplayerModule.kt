package com.whaleup.gameshub.multiplayer

import android.util.Log
import com.whaleup.gameshub.data.BiomeMessageAction
import com.whaleup.gameshub.data.BiomeMessageType
import com.whaleup.gameshub.data.CatalogCache
import com.whaleup.gameshub.data.isMultiplayerGame
import com.whaleup.gameshub.webview.WhaleBridge
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

private const val TAG = "MultiplayerModule"

class MultiplayerModule(
    private val bridge: WhaleBridge,
    private val engineUrl: String,
    private val playerId: String,
    private val currentGameId: () -> String?,
    private val ticketProvider: suspend () -> String
) {
    private var socket: Socket? = null
    private var isConnected = false
    private val scope = CoroutineScope(Dispatchers.IO)
    private val pendingCommands = mutableListOf<() -> Unit>()

    fun connect(reconnect: Boolean = false) {
        if (isConnected && !reconnect) return
        val gameId = currentGameId()
        val game = CatalogCache.findById(gameId ?: "")
        if (game?.isMultiplayerGame() != true) {
            Log.w(TAG, "Ignoring mp_connect — '$gameId' is not a multiplayer game")
            return
        }

        scope.launch {
            try {
                val ticket = ticketProvider()
                openSocket(ticket)
            } catch (e: Exception) {
                Log.e(TAG, "Ticket fetch failed", e)
                sendError("TICKET_FAILED", "Unable to get session ticket: ${e.message}", retryable = true)
            }
        }
    }

    private fun openSocket(ticket: String) {
        val opts = IO.Options().apply {
            query = "playerId=$playerId&ticket=$ticket"
            transports = arrayOf("websocket")
            reconnection = true
            reconnectionAttempts = 5
            reconnectionDelay = 2000
        }

        socket = IO.socket(URI.create(engineUrl), opts).apply {

            // ── Connection lifecycle
            on(Socket.EVENT_CONNECT) {
                isConnected = true
                sendToWebView(BiomeMessageAction.MP_CONNECTED, mapOf("connected" to true, "playerId" to playerId))
                synchronized(pendingCommands) {
                    pendingCommands.forEach { it.invoke() }
                    pendingCommands.clear()
                }
            }

            on(Socket.EVENT_DISCONNECT) { args ->
                isConnected = false
                val first = if (args != null && args.isNotEmpty()) args[0] else null
                val reason = first?.toString() ?: "unknown"
                sendError("DISCONNECTED", reason, retryable = true)
            }

            on(Socket.EVENT_CONNECT_ERROR) { args ->
                val first = if (args != null && args.isNotEmpty()) args[0] else null
                val msg = first?.toString() ?: "connection error"
                sendError("CONNECTION_FAILED", msg, retryable = true)
            }

            // ── Engine → Client: Queue events (listen only, relay to WebView)
            on("queue.timer")              { args -> relay(BiomeMessageAction.MP_QUEUE_TIMER, args) }
            on("queue.exit")               { args -> relay(BiomeMessageAction.MP_QUEUE_EXIT, args) }
            on("queue.disconnect")         { args -> relay(BiomeMessageAction.MP_QUEUE_DISCONNECT, args) }   // bidirectional push

            // ── Engine → Client: Lobby events (listen only, relay to WebView)
            on("lobby.joined")             { args -> relay(BiomeMessageAction.MP_LOBBY_JOINED, args) }        // Engine pushes; client does NOT emit back
            on("lobby.player_joined")      { args -> relay(BiomeMessageAction.MP_LOBBY_PLAYER_JOINED, args) } // FIX: was "lobby.joined"
            on("lobby.player_left")        { args -> relay(BiomeMessageAction.MP_LOBBY_PLAYER_LEFT, args) }   // FIX: was "lobby.left"
            on("lobby.wait_timer")         { args -> relay(BiomeMessageAction.MP_LOBBY_WAIT_TIMER, args) }
            on("lobby.countdown_started")  { args -> relay(BiomeMessageAction.MP_LOBBY_COUNTDOWN, args) }
            on("lobby.countdown_aborted")  { args -> relay(BiomeMessageAction.MP_LOBBY_ABORTED, args) }
            on("lobby.preflight_ping")     { args ->
                emitDirect("lobby.preflight_pong", null)
                relay(BiomeMessageAction.MP_LOBBY_PREFLIGHT, args)
            }
            on("lobby.sealed")             { args -> relay(BiomeMessageAction.MP_LOBBY_SEALED, args) }
            on("lobby.exit")               { args -> relay(BiomeMessageAction.MP_LOBBY_EXIT, args) }
            on("lobby.disbanded")          { args -> relay(BiomeMessageAction.MP_LOBBY_DISBANDED, args) }
            on("lobby.disconnect")         { args -> relay(BiomeMessageAction.MP_LOBBY_DISCONNECT, args) }   // bidirectional push
            on("lobby.reconnected")        { args -> relay(BiomeMessageAction.MP_LOBBY_RECONNECTED, args) }  // Engine pushes automatically

            // ── Engine → Client: GameRoom events (listen only, relay to WebView)
            on("gameRoom.joined")          { args -> relay(BiomeMessageAction.MP_GAMEROOM_JOINED, args) }        // Engine pushes; client does NOT emit back
            on("gameRoom.player_loaded")   { args -> relay(BiomeMessageAction.MP_GAMEROOM_PLAYER_LOADED, args) } // broadcast
            on("gameRoom.start_countdown") { args -> relay(BiomeMessageAction.MP_GAMEROOM_START_CDOWN, args) }
            on("gameRoom.player_left")     { args -> relay(BiomeMessageAction.MP_GAMEROOM_PLAYER_LEFT, args) }   // broadcast
            on("gameRoom.disconnect")      { args -> relay(BiomeMessageAction.MP_GAMEROOM_DISCONNECT, args) }    // bidirectional push
            on("gameRoom.reconnected")     { args -> relay(BiomeMessageAction.MP_GAMEROOM_RECONNECTED, args) }   // Engine pushes automatically
            on("gameRoom.complete")        { args -> relay(BiomeMessageAction.MP_GAMEROOM_COMPLETE, args) }
            on("gameRoom.broadcast")       { args -> relay(BiomeMessageAction.MP_GAMEROOM_BROADCAST, args) }    // FIX: relay whole payload as gameRoom_broadcast

            // ── Engine → Client: Global events
            on("session.status")           { args -> relay(BiomeMessageAction.MP_SESSION_STATUS, args) }
            on("engine.error")             { args -> relay(BiomeMessageAction.MP_ENGINE_ERROR, args) }
            on("evicted")                  { args -> relay(BiomeMessageAction.MP_EVICTED, args) }
            on("timer.tick")               { args -> relay(BiomeMessageAction.MP_TIMER_TICK, args) }            // broadcast to everyone

            connect()
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
        isConnected = false
        synchronized(pendingCommands) {
            pendingCommands.clear()
        }
    }

    /**
     * Handles commands arriving from the Game WebView (Client → Engine direction).
     * Only events where the client initiates an action toward the Engine are listed here.
     * Engine → Client push events are handled purely in openSocket() listeners above.
     */
    fun handleCommand(action: String, data: Any?) {
        if (action == BiomeMessageAction.MP_CONNECT) {
            val reconnect = (data as? Map<*, *>)?.get("reconnect") == true
            connect(reconnect = reconnect)
            return
        }
        if (action == BiomeMessageAction.MP_DISCONNECT) {
            disconnect()
            return
        }

        if (socket?.connected() != true) {
            Log.d(TAG, "Queueing command: $action (socket not connected yet)")
            synchronized(pendingCommands) {
                pendingCommands.add {
                    executeCommand(action, data)
                }
            }
            return
        }

        executeCommand(action, data)
    }

    private fun executeCommand(action: String, data: Any?) {
        when (action) {
            // Queue actions — only queue_join has an ACK
            BiomeMessageAction.MP_QUEUE_JOIN            -> emitWithAck("queue.join", BiomeMessageAction.MP_QUEUE_JOIN_RESP, data)
            BiomeMessageAction.MP_QUEUE_LEAVE           -> emitDirect("queue.leave", data)            // no ACK
            BiomeMessageAction.MP_QUEUE_RECONNECT       -> emitDirect("queue.reconnected", data)      // no ACK
            BiomeMessageAction.MP_QUEUE_DISCONNECT      -> emitDirect("queue.disconnect", data)       // bidirectional, no ACK

            // Lobby actions — no ACKs
            BiomeMessageAction.MP_LOBBY_LEAVE           -> emitDirect("lobby.leave", data)            // no ACK
            BiomeMessageAction.MP_LOBBY_DISCONNECT      -> emitDirect("lobby.disconnect", data)       // bidirectional, no ACK
            BiomeMessageAction.MP_PREFLIGHT_PONG        -> emitDirect("lobby.preflight_pong", null)

            // GameRoom actions — only command and whisper have ACK
            BiomeMessageAction.MP_GAMEROOM_PLAYER_READY -> emitDirect("gameRoom.player_ready", data)  // no ACK
            BiomeMessageAction.MP_GAMEROOM_COMMAND      -> emitGameCommand(data)                      // has ACK
            BiomeMessageAction.MP_GAMEROOM_LEAVE        -> emitDirect("gameRoom.leave", data)         // no ACK
            BiomeMessageAction.MP_GAMEROOM_RECONNECT    -> emitDirect("gameRoom.reconnected", data)   // no ACK
            BiomeMessageAction.MP_GAMEROOM_WHISPER      -> emitWithAck("gameRoom.whisper", BiomeMessageAction.MP_GAMEROOM_WHISPER_RESP, data) // has ACK
            BiomeMessageAction.MP_GAMEROOM_DISCONNECT   -> emitDirect("gameRoom.disconnect", data)    // bidirectional, no ACK
        }
    }

    // ── Emit fire-and-forget (no ACK expected)
    private fun emitDirect(event: String, data: Any?) {
        if (socket == null) return
        if (data != null) socket?.emit(event, JSONObject.wrap(data))
        else socket?.emit(event)
    }

    // ── Emit with ACK callback — used only for queue_join, gameRoom_command, gameRoom_whisper
    private fun emitWithAck(event: String, responseAction: String, data: Any?) {
        if (socket == null) return
        val wrapped = if (data != null) JSONObject.wrap(data) else null
        val ackCallback = io.socket.client.Ack { args ->
            val first = if (args != null && args.isNotEmpty()) args[0] else null
            val ackData = parsePayload(first)
            sendToWebView(responseAction, ackData)
        }
        if (wrapped != null) socket?.emit(event, wrapped, ackCallback)
        else socket?.emit(event, ackCallback)
    }

    // ── Special handler for gameRoom_command (has nested type + payload structure)
    private fun emitGameCommand(data: Any?) {
        val map = data as? Map<*, *>
        val cmdType = map?.get("type") as? String ?: return
        val payload = map["payload"]
        val json = JSONObject().apply {
            put("type", cmdType)
            if (payload != null) put("payload", JSONObject.wrap(payload))
        }
        val ackCallback = io.socket.client.Ack { args ->
            val first = if (args != null && args.isNotEmpty()) args[0] else null
            val ack = parsePayload(first)
            sendToWebView(BiomeMessageAction.MP_GAMEROOM_COMMAND_RESP, ack)
        }
        socket?.emit("gameRoom.command", json, ackCallback)
    }

    // ── Relay Engine push event to WebView
    private fun relay(action: String, args: Array<out Any>?) {
        val first = if (args != null && args.isNotEmpty()) args[0] else null
        val data = parsePayload(first)
        sendToWebView(action, data)
    }

    private fun sendToWebView(action: String, data: Any?) {
        bridge.sendMessageToWebView(BiomeMessageType.MULTIPLAYER, action, data)
    }

    private fun sendError(code: String, message: String, retryable: Boolean) {
        sendToWebView(BiomeMessageAction.MP_ERROR, mapOf(
            "code" to code, "message" to message, "retryable" to retryable
        ))
    }

    private fun parsePayload(arg: Any?): Any? {
        return when (arg) {
            is JSONObject -> arg.toMp()
            is JSONArray  -> (0 until arg.length()).map { parsePayload(arg.opt(it)) }
            is Map<*, *>  -> arg.entries.associate { (k, v) -> k.toString() to parsePayload(v) }
            is List<*>    -> arg.map { parsePayload(it) }
            JSONObject.NULL -> null
            else -> arg
        }
    }

    private fun JSONObject.toMp(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        keys().forEach { key ->
            map[key] = parsePayload(opt(key))
        }
        return map
    }
}
