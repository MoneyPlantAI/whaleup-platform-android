package com.whaleup.gameshub.data

/**
 * In-memory singleton that holds the fetched game catalog.
 * Allows activities to look up game details (like entry URL and config) by ID.
 */
object CatalogCache {
    private var catalog: HubCatalog? = null

    fun set(c: HubCatalog) {
        catalog = c
    }

    fun get(): HubCatalog? = catalog

    fun findById(gameId: String): AppEntry? =
        catalog?.games?.firstOrNull { it.id == gameId }
}
