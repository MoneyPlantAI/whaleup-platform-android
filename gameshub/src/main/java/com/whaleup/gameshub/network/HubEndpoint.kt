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
    GET_LEADERBOARD("/api/1/whaleup/games", "game/get-leaderboard"),

    // Gullak / Rewards
    GET_GULLAK("/api/1/whaleup/games", "gullak/get-gullak"),
    CLAIM_GULLAK("/api/1/whaleup/games", "gullak/claim-gullak"),

    // Engagement / diagnostics / localisation
    VIDEO_WATCHED("/api/1/whaleup/games", "video/video-watched"),
    LOG_CLIENT_ERROR("/api/1/whaleup/games", "log/client-error"),
    GET_STRINGS("/api/1/whaleup/games", "config/get-strings");

    companion object {
        /**
         * Find an endpoint by its name string (used by MessageRouter).
         */
        fun fromName(name: String): HubEndpoint? {
            val normalized = name.replace("_", "").replace("-", "").lowercase()
            return entries.find {
                it.name.replace("_", "").lowercase() == normalized ||
                    it.routeUri == name
            }
        }
    }
}
