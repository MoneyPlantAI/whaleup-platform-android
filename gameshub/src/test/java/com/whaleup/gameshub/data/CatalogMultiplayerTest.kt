package com.whaleup.gameshub.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogMultiplayerTest {

    @Test
    fun ludoWithoutConfiguredEngineRemainsSinglePlayer() {
        val game = AppEntry.fromJson(
            JSONObject(
                """{
                    "id":"ludo",
                    "url":"https://whaleupco.in/games/hike/ludo-game-multi-dev/",
                    "name":"Ludo",
                    "gameConfig":{}
                }"""
            )
        )

        assertNull(game.gameEngineUrl)
        assertFalse(game.isMultiplayerGame())
    }

    @Test
    fun explicitEngineUrlEnablesMultiplayer() {
        val game = AppEntry.fromJson(
            JSONObject(
                """{
                    "id":"ludo",
                    "url":"https://whaleupco.in/games/hike/ludo-game-multi-dev/",
                    "gameEngineUrl":"https://engine.example.com"
                }"""
            )
        )

        assertEquals("https://engine.example.com", game.gameEngineUrl)
    }

    @Test
    fun nestedEngineUrlEnablesMultiplayer() {
        val game = AppEntry.fromJson(
            JSONObject(
                """{
                    "id":"ludo",
                    "url":"https://games.example.com/ludo",
                    "gameConfig":{"engineUrl":"https://engine.example.com"}
                }"""
            )
        )

        assertEquals("https://engine.example.com", game.gameEngineUrl)
    }

    @Test
    fun blankOrNullEngineUrlDoesNotEnableMultiplayer() {
        val game = AppEntry.fromJson(
            JSONObject(
                """{
                    "id":"ludo",
                    "gameEngineUrl":"  ",
                    "gameConfig":{"engineUrl":null}
                }"""
            )
        )

        assertNull(game.gameEngineUrl)
        assertFalse(game.isMultiplayerGame())
    }

    @Test
    fun ordinaryGameWithoutEngineRemainsSinglePlayer() {
        val game = AppEntry.fromJson(
            JSONObject("""{"id":"puzzle","url":"https://games.example.com/puzzle"}""")
        )

        assertNull(game.gameEngineUrl)
        assertFalse(game.isMultiplayerGame())
    }
}
