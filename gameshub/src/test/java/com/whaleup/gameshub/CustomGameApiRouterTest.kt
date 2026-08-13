package com.whaleup.gameshub

import android.util.Log
import com.whaleup.gameshub.data.BiomeMessageAction
import com.whaleup.gameshub.data.BiomeMessageType
import com.whaleup.gameshub.messaging.BiomeMessage
import com.whaleup.gameshub.messaging.MessageRouter
import com.whaleup.gameshub.messaging.RouteAction
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CustomGameApiRouterTest {
    @Before
    fun mockAndroidLog() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
    }

    @Test
    fun routesCustomGameApiRequest() {
        val actions = MessageRouter.route(
            BiomeMessage(
                type = BiomeMessageType.STATE_SYNC,
                action = BiomeMessageAction.GAME_API_REQUEST,
                data = mapOf(
                    "method" to "PUT",
                    "route" to "game/custom-score",
                    "endpoint" to "/api/custom-composite",
                    "data" to mapOf("score" to 42)
                )
            )
        )

        val apiCall = actions.single() as RouteAction.ApiCall
        assertEquals("customRequest", apiCall.endpoint)
        assertEquals("PUT", apiCall.method)
        assertEquals("game/custom-score", apiCall.route)
        assertEquals("/api/custom-composite", apiCall.customEndpoint)
        assertEquals(42, apiCall.data?.get("score"))
        assertEquals(BiomeMessageAction.GAME_API_RESPONSE, apiCall.respondWith)
    }
}
