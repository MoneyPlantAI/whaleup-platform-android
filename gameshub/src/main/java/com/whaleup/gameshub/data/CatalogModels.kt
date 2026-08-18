package com.whaleup.gameshub.data

import org.json.JSONArray
import org.json.JSONObject
import com.whaleup.gameshub.messaging.toMap

data class CoinBonus(
    val loginDay: Int,
    val loginBonus: Int,
    val perGameBonus: Int
)

data class BonusConfigImages(
    val treasureBox: String? = null,
    val supercoinIcon: String? = null
) {
    companion object {
        fun fromJson(json: JSONObject?): BonusConfigImages = BonusConfigImages(
            treasureBox = json?.optString("treasureBox")?.takeIf(String::isNotBlank),
            supercoinIcon = json?.optString("supercoinIcon")?.takeIf(String::isNotBlank)
        )
    }
}

data class BonusConfig(
    val rewardsJourneyDays: Int = 7,
    val afterJourneyDailyRewardCoins: Int = 50,
    val coinBonus: List<CoinBonus> = emptyList(),
    val images: BonusConfigImages = BonusConfigImages()
) {
    companion object {
        fun fromJson(json: JSONObject?): BonusConfig {
            if (json == null) return BonusConfig()
            val coinBonusList = ArrayList<CoinBonus>()
            val coinArr = json.optJSONArray("coinBonus")
            if (coinArr != null) {
                for (i in 0 until coinArr.length()) {
                    val obj = coinArr.optJSONObject(i) ?: continue
                    coinBonusList.add(
                        CoinBonus(
                            loginDay = obj.optInt("loginDay", i + 1),
                            loginBonus = obj.optInt("loginBonus", 50),
                            perGameBonus = obj.optInt("perGameBonus", 10)
                        )
                    )
                }
            }
            val images = BonusConfigImages.fromJson(json.optJSONObject("images"))
            return BonusConfig(
                rewardsJourneyDays = json.optInt("rewardsJourneyDays", 7),
                afterJourneyDailyRewardCoins = json.optInt("afterJourneyDailyRewardCoins", 50),
                coinBonus = coinBonusList,
                images = images
            )
        }
    }
}

data class HubCatalog(
    val games: List<AppEntry>,
    val categories: List<String>,
    val heroBannerUrls: List<String> = emptyList(),
    val bonusConfig: BonusConfig = BonusConfig(),
    val imageConfig: BonusConfigImages = BonusConfigImages()
) {
    companion object {
        fun fromJson(json: JSONObject): HubCatalog {
            val targetJson = json.optJSONObject("data") 
                ?: json.optJSONObject("config") 
                ?: json.optJSONObject("body") 
                ?: json

            val games = ArrayList<AppEntry>()
            val gamesArray = targetJson.optJSONArray("games") 
                ?: json.optJSONArray("games")
            if (gamesArray != null) {
                for (i in 0 until gamesArray.length()) {
                    val gameObj = gamesArray.optJSONObject(i)
                    if (gameObj != null) {
                        games.add(AppEntry.fromJson(gameObj))
                    }
                }
            }

            val categories = ArrayList<String>()
            val catsArray = targetJson.optJSONArray("categories") 
                ?: json.optJSONArray("categories")
            if (catsArray != null) {
                for (i in 0 until catsArray.length()) {
                    categories.add(catsArray.getString(i))
                }
            }

            val heroBannerUrls = ArrayList<String>()
            fun extractUrls(arr: JSONArray?) {
                if (arr == null) return
                for (i in 0 until arr.length()) {
                    val str = arr.optString(i, "")
                    if (str.isNotBlank() && str.startsWith("http") && !heroBannerUrls.contains(str)) {
                        heroBannerUrls.add(str)
                    }
                }
            }

            val bonusObj = targetJson.optJSONObject("bonus") ?: json.optJSONObject("bonus")

            // Match React Native: top banners come only from bonus.heroBannerUrl.
            if (bonusObj != null) {
                extractUrls(bonusObj.optJSONArray("heroBannerUrl"))
                extractUrls(bonusObj.optJSONArray("heroBannerUrls"))

                val singleUrl = bonusObj.optString("heroBannerUrl", "")
                if (singleUrl.isNotBlank() && singleUrl.startsWith("http") && !heroBannerUrls.contains(singleUrl)) {
                    heroBannerUrls.add(singleUrl)
                }
            }

            val bonusConfig = BonusConfig.fromJson(bonusObj)
            val imageConfig = BonusConfigImages.fromJson(
                targetJson.optJSONObject("images") ?: json.optJSONObject("images")
            )

            val activeGames = games
                .filter { it.isActive }
                .sortedWith(
                    compareBy<AppEntry> { it.displayOrder ?: Int.MAX_VALUE }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                )

            return HubCatalog(activeGames, categories, heroBannerUrls, bonusConfig, imageConfig)
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
    val iconUrl: String,
    val isActive: Boolean = true,
    val displayOrder: Int? = null,
    val pill: Map<String, Any?>?,
    val description: String? = null,
    val gameConfig: Map<String, Any?>
) {
    companion object {
        fun fromJson(json: JSONObject): AppEntry {
            val config = json.optJSONObject("gameConfig")?.toMap() ?: emptyMap()
            val configPill = (config["pill"] as? Map<*, *>)?.toStringKeyMap()

            fun booleanValue(key: String, defaultValue: Boolean): Boolean {
                val value = if (json.has(key) && !json.isNull(key)) json.opt(key) else config[key]
                return when (value) {
                    is Boolean -> value
                    is Number -> value.toInt() != 0
                    is String -> when (value.lowercase()) {
                        "true", "1" -> true
                        "false", "0" -> false
                        else -> defaultValue
                    }
                    else -> defaultValue
                }
            }

            fun intValue(key: String): Int? {
                val value = if (json.has(key) && !json.isNull(key)) json.opt(key) else config[key]
                return when (value) {
                    is Number -> value.toInt()
                    is String -> value.toIntOrNull()
                    else -> null
                }
            }

            fun strIsValidUrl(str: String): Boolean =
                str.startsWith("http://") || str.startsWith("https://")

            fun getValidUrl(vararg keys: String): String {
                for (key in keys) {
                    val valStr = json.optString(key, "")
                    if (valStr.isNotBlank() && strIsValidUrl(valStr)) {
                        return valStr
                    }
                }
                return ""
            }

            val bannerUrl = getValidUrl("bannerImageUrl", "imageUrl", "bannerUrl", "iconUrl", "icon")
            val gameEngineUrl = sequenceOf(
                json.optNullableString("gameEngineUrl"),
                json.optNullableString("engineUrl"),
                config["gameEngineUrl"] as? String,
                config["engineUrl"] as? String
            ).firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }

            return AppEntry(
                id = json.optString("id", json.optString("gameId", "")),
                name = json.optString("name", json.optString("gameName", "Unknown")),
                category = json.optString("category", "General"),
                entryUrl = json.optString("entryUrl", json.optString("url", "")),
                gameEngineUrl = gameEngineUrl,
                bannerImageUrl = bannerUrl,
                bgUrl = json.optString("bgUrl", config["bgUrl"]?.toString().orEmpty()),
                logoUrl = getValidUrl("logoUrl", "iconUrl", "icon")
                    .ifEmpty { config["logoUrl"]?.toString().orEmpty() },
                iconUrl = json.optString("icon", "").trim(),
                isActive = booleanValue("isActive", true),
                displayOrder = intValue("displayOrder"),
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
    !gameEngineUrl.isNullOrBlank()
