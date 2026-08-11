package com.whaleup.gameshub.network

/**
 * All API endpoints used by the SDK.
 * Matches Whaleup's APINames / APIRoutes.
 */
enum class HubEndpoint(val path: String, val routeUri: String) {
    // Catalog
    CATALOG("/api/v1/composite", "config/get-config"),

    // User Management
    GET_USER_PROFILE("/api/v1/composite", "user/get-user"),
    GET_CONFIG      ("/api/v1/composite", "config/get-config"),

    // Game Management
    GAME_STARTED("/api/v1/composite", "game/game-started"),
    GAME_ENDED("/api/v1/composite", "game/game-ended");

    // Gullak / Rewards (Reserved for future use)
    // CLAIM_GULLAK("/api/v1/composite", "game/claim-gullak"),

    companion object {
        /**
         * Find an endpoint by its name string (used by MessageRouter).
         */
        fun fromName(name: String): HubEndpoint? {
            return entries.find { it.name == name }
        }
    }
}
