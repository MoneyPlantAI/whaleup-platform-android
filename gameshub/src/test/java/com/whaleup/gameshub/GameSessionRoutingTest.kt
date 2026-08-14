package com.whaleup.gameshub

import android.util.Log
import com.whaleup.gameshub.data.BiomeMessageAction
import com.whaleup.gameshub.data.BiomeMessageType
import com.whaleup.gameshub.data.BiomeState
import com.whaleup.gameshub.data.UserConfig
import com.whaleup.gameshub.messaging.BiomeMessage
import com.whaleup.gameshub.messaging.MessageRouter
import com.whaleup.gameshub.messaging.RouteAction
import com.whaleup.gameshub.network.HubEndpoint
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GameSessionRoutingTest {
    private val userSessionId = "abc-123-c9c"
    private val gameSessionId = "fb3873fa-4ab3-40e6-93c9-6672d98175f9"

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        mockkObject(BiomeState)
        every { BiomeState.getCurrentGameId() } returns "ludo"
        every { BiomeState.getUserConfig() } returns UserConfig(
            userId = "whaleupId62837",
            userAgent = "test-agent",
            sessionId = userSessionId,
            apiBaseUrl = "https://example.com",
            allowedDomains = listOf("example.com")
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun gameCompletedUsesGameStartSessionInsteadOfUserHeaderSession() {
        every { BiomeState.getGameSessionId() } returns gameSessionId

        val actions = completedGameActions()
        val apiCall = actions.filterIsInstance<RouteAction.ApiCall>().single()

        assertEquals(HubEndpoint.GAME_ENDED.name, apiCall.endpoint)
        assertEquals(gameSessionId, apiCall.data?.get("gameSessionId"))
        assertFalse(apiCall.data?.get("gameSessionId") == userSessionId)
    }

    @Test
    fun gameCompletedDoesNotCallApiWithoutGameStartSession() {
        every { BiomeState.getGameSessionId() } returns null

        val actions = completedGameActions()

        assertTrue(actions.none { it is RouteAction.ApiCall })
        assertTrue(actions.any { it is RouteAction.SdkEvent })
    }

    private fun completedGameActions(): List<RouteAction> = MessageRouter.route(
        BiomeMessage(
            type = BiomeMessageType.GAMEPLAY,
            action = BiomeMessageAction.GAME_COMPLETED,
            data = mapOf("score" to 10, "playTimeInSec" to 5)
        )
    )
}
