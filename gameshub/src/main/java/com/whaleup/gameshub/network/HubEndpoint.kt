package com.whaleup.gameshub.network

/**
 * All API endpoints used by the SDK.
 * Matches Whaleup's APINames / APIRoutes.
 */
enum class HubEndpoint(val path: String, val routeUri: String) {
    // Catalog
    CATALOG("/api/1/whaleup/games", "config/get-config"),

    // User Management
    GET_USER_PROFILE("/api/1/whaleup/games", "user/get-user"),
    GET_CONFIG      ("/api/1/whaleup/games", "config/get-config"),

    // Game Management
    GAME_STARTED("/api/1/whaleup/games", "game/game-started"),
    GAME_ENDED("/api/1/whaleup/games", "game/game-ended"),

    // Leaderboard
    GET_LEADERBOARD("/api/1/whaleup/games", "user/get-leaderboard"),

    // Gullak / Rewards
    CLAIM_GULLAK("/api/1/whaleup/games", "user/claim-gullak");

    companion object {
        /**
         * Find an endpoint by its name string (used by MessageRouter).
         */
        fun fromName(name: String): HubEndpoint? {
            return entries.find { it.name == name }
        }
    }
}
