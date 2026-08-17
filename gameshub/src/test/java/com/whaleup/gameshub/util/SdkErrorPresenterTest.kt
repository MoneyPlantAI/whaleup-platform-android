package com.whaleup.gameshub.util

import com.whaleup.gameshub.data.AppEntry
import com.whaleup.gameshub.data.BiomeMessageAction
import com.whaleup.gameshub.data.BiomeState
import com.whaleup.gameshub.data.CatalogCache
import com.whaleup.gameshub.data.HubCatalog
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SdkErrorPresenterTest {

    @Before
    fun setUp() {
        CatalogCache.clear()
        BiomeState.setCurrentGameId(null)
    }

    @After
    fun tearDown() {
        CatalogCache.clear()
        BiomeState.setCurrentGameId(null)
    }

    @Test
    fun suppressesTransientInternetDialogForConfiguredMultiplayerGame() {
        setActiveGame(engineUrl = "https://engine.example.com")

        assertTrue(
            SdkErrorPresenter.shouldSuppressDialog(
                isInternetError = true,
                errorAction = BiomeMessageAction.NETWORK_INTERRUPTED
            )
        )
    }

    @Test
    fun doesNotSuppressFinalReconnectFailure() {
        setActiveGame(engineUrl = "https://engine.example.com")

        assertFalse(
            SdkErrorPresenter.shouldSuppressDialog(
                isInternetError = true,
                errorAction = BiomeMessageAction.NETWORK_LOAD_ERROR
            )
        )
    }

    @Test
    fun doesNotSuppressDialogForSinglePlayerGame() {
        setActiveGame(engineUrl = null)

        assertFalse(
            SdkErrorPresenter.shouldSuppressDialog(
                isInternetError = true,
                errorAction = BiomeMessageAction.NETWORK_INTERRUPTED
            )
        )
    }

    private fun setActiveGame(engineUrl: String?) {
        val game = AppEntry(
            id = "game-id",
            name = "Game",
            category = "Game",
            entryUrl = "https://games.example.com/game",
            gameEngineUrl = engineUrl,
            bannerImageUrl = "",
            bgUrl = "",
            logoUrl = "",
            iconUrl = "",
            pill = null,
            gameConfig = emptyMap()
        )
        CatalogCache.set(HubCatalog(games = listOf(game), categories = emptyList()))
        BiomeState.setCurrentGameId(game.id)
    }
}
