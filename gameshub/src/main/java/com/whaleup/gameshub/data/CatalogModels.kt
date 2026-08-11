package com.whaleup.gameshub.data

import org.json.JSONArray
import org.json.JSONObject
import com.whaleup.gameshub.messaging.toMap

data class HubCatalog(
    val games: List<AppEntry>,
    val categories: List<String>,
    val heroBannerUrls: List<String> = emptyList()
) {
    companion object {
        fun fromJson(json: JSONObject): HubCatalog {
            val games = ArrayList<AppEntry>()
            val gamesArray = json.optJSONArray("games")
            if (gamesArray != null) {
                for (i in 0 until gamesArray.length()) {
                    games.add(AppEntry.fromJson(gamesArray.getJSONObject(i)))
                }
            }

            val categories = ArrayList<String>()
            val catsArray = json.optJSONArray("categories")
            if (catsArray != null) {
                for (i in 0 until catsArray.length()) {
                    categories.add(catsArray.getString(i))
                }
            }

            val heroBannerUrls = ArrayList<String>()
            val heroArray = json.optJSONArray("heroBannerUrls")
            if (heroArray != null) {
                for (i in 0 until heroArray.length()) {
                    heroBannerUrls.add(heroArray.getString(i))
                }
            }

            return HubCatalog(games, categories, heroBannerUrls)
        }
    }
}

data class AppEntry(
    val id: String,
    val name: String,
    val category: String,
    val entryUrl: String,
    val gameEngineUrl: String?,
    val bannerImageUrl: String,
    val bgUrl: String,
    val logoUrl: String,
    val pill: Map<String, Any?>?,
    val description: String? = null,
    val gameConfig: Map<String, Any?>
) {
    companion object {
        fun fromJson(json: JSONObject): AppEntry {
            val config = json.optJSONObject("gameConfig")?.toMap() ?: emptyMap()
            val configPill = (config["pill"] as? Map<*, *>)?.toStringKeyMap()
            return AppEntry(
                id = json.optString("id", json.optString("gameId", "")),
                name = json.optString("name", json.optString("gameName", "Unknown")),
                category = json.optString("category", "General"),
                entryUrl = json.optString("entryUrl", ""),
                gameEngineUrl = json.optNullableString("gameEngineUrl"),
                bannerImageUrl = json.optString("bannerImageUrl", json.optString("imageUrl", "")),
                bgUrl = json.optString("bgUrl", config["bgUrl"]?.toString().orEmpty()),
                logoUrl = json.optString("logoUrl", config["logoUrl"]?.toString().orEmpty()),
                pill = json.optJSONObject("pill")?.toMap() ?: configPill,
                description = json.optString("description", ""),
                gameConfig = config
            )
        }

        private fun JSONObject.optNullableString(name: String): String? {
            return if (isNull(name) || !has(name)) null else optString(name)
        }

        private fun Map<*, *>.toStringKeyMap(): Map<String, Any?> {
            return entries.mapNotNull { entry ->
                (entry.key as? String)?.let { key -> key to entry.value }
            }.toMap()
        }
    }
}

fun AppEntry.isMultiplayerGame(): Boolean =
    !gameEngineUrl.isNullOrEmpty()
