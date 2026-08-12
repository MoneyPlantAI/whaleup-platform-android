package com.whaleup.gameshub

import com.whaleup.gameshub.data.HubCatalog
import com.whaleup.gameshub.data.AppEntry
import com.whaleup.gameshub.network.HubEndpoint
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ApiIntegrationTest {

    @Test
    fun testHubEndpointMappings() {
        assertEquals("/api/1/whaleup/games", HubEndpoint.GET_CONFIG.path)
        assertEquals("config/get-config", HubEndpoint.GET_CONFIG.routeUri)
        
        assertEquals("/api/1/whaleup/games", HubEndpoint.GET_USER_PROFILE.path)
        assertEquals("user/get-user", HubEndpoint.GET_USER_PROFILE.routeUri)
        
        assertEquals("/api/1/whaleup/games", HubEndpoint.GAME_STARTED.path)
        assertEquals("game/game-started", HubEndpoint.GAME_STARTED.routeUri)
        
        assertEquals("/api/1/whaleup/games", HubEndpoint.GAME_ENDED.path)
        assertEquals("game/game-ended", HubEndpoint.GAME_ENDED.routeUri)

        assertEquals("/api/1/whaleup/games", HubEndpoint.CLAIM_GULLAK.path)
        assertEquals("gullak/claim-gullak", HubEndpoint.CLAIM_GULLAK.routeUri)
    }

    @Test
    fun testCatalogParsing() {
        val jsonString = """
            {
                "success": true,
                "data": {
                    "games": [
                        {
                            "id": "runner",
                            "name": "Runner",
                            "category": "Runner",
                            "entryUrl": "https://whaleup-runner-game.pages.dev/",
                            "gameConfig": {
                                "maxSpeed": 600,
                                "baseSpeed": 300
                            },
                            "bannerImageUrl": "https://picsum.photos/seed/subway/600/300",
                            "bgUrl": "https://example.com/bg.png",
                            "logoUrl": "https://example.com/logo.png",
                            "pill": {
                                "text": "⚡ Fast Paced",
                                "color": "#00D7FF"
                            }
                        }
                    ],
                    "categories": ["Trending", "Runner"]
                }
            }
        """.trimIndent()

        val jsonObject = JSONObject(jsonString).getJSONObject("data")
        val catalog = HubCatalog.fromJson(jsonObject)

        assertEquals(1, catalog.games.size)
        assertEquals("runner", catalog.games[0].id)
        assertEquals("Runner", catalog.games[0].name)
        assertEquals("Runner", catalog.games[0].category)
        assertEquals("https://whaleup-runner-game.pages.dev/", catalog.games[0].entryUrl)
        assertEquals("https://picsum.photos/seed/subway/600/300", catalog.games[0].bannerImageUrl)
        assertEquals("https://example.com/bg.png", catalog.games[0].bgUrl)
        assertEquals("https://example.com/logo.png", catalog.games[0].logoUrl)
        assertEquals("⚡ Fast Paced", catalog.games[0].pill?.get("text"))
        assertEquals("#00D7FF", catalog.games[0].pill?.get("color"))
        assertEquals(600, (catalog.games[0].gameConfig["maxSpeed"] as Number).toInt())
        
        assertEquals(2, catalog.categories.size)
        assertTrue(catalog.categories.contains("Trending"))
        assertTrue(catalog.categories.contains("Runner"))
    }
}
