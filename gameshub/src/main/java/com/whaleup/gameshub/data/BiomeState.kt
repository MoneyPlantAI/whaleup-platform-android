package com.whaleup.gameshub.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "BiomeState"
private const val PREFS_NAME = "biome_sdk_state"
private const val STORAGE_VERSION = 1
private const val USER_CONFIG_KEY = "user_config"

/**
 * Singleton state manager for the Biome SDK.
 * Kotlin port of Whaleup's SDKState.ts.
 *
 * Manages:
 * - User profile (with persistence via SharedPreferences)
 * - Current game context (gameId, gameSessionId)
 * - Coin/gem tracking with optimistic updates
 * - Profile hydration from cache on startup
 */
object BiomeState {

    private var prefs: SharedPreferences? = null
    private var userConfig: UserConfig? = null
    private var userProfile: UserProfile? = null
    private var profileTimestamp: Long? = null
    private var profileSource: String? = null // "cache" or "server"

    private var currentGameId: String? = null
    private var currentGameIsMaxGameBonusEarned: Boolean = false
    // Host/user session used by APIBridge as the request-header session.
    private var sessionId: String? = null
    // Per-game session returned by game-start and sent only in game-end payloads.
    private var gameSessionId: String? = null
    private var bonusConfig: BonusConfig? = null

    fun getBonusConfig(): BonusConfig? = bonusConfig

    fun setBonusConfig(config: BonusConfig?) {
        bonusConfig = config
    }

    /**
     * Initialize with application context. Must be called before any other method.
     */
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (userConfig == null) {
            userConfig = hydrateUserConfig()
        }
    }

    // region User Config

    fun setUserConfig(config: UserConfig?) {
        if (config != null && config.userId.isBlank()) {
            Log.w(TAG, "Ignoring user config with blank userId")
            return
        }

        userConfig = config
        if (config == null) {
            prefs?.edit()?.remove(USER_CONFIG_KEY)?.apply()
        } else {
            persistUserConfig(config)
        }
    }

    fun getUserConfig(): UserConfig? {
        if (userConfig == null) {
            userConfig = hydrateUserConfig()
        }
        return userConfig
    }

    // endregion

    // region User Profile

    /**
     * Set user profile from API response.
     * @param profileData Map from API response
     * @param source "server" for fresh API data, "cache" for hydrated data
     */
    fun setUserProfile(profileData: Map<String, Any?>?, source: String = "server") {
        Log.d(TAG, "setUserProfile from $source: $profileData")
        if (profileData == null || profileData.isEmpty()) {
            Log.d(TAG, "Clearing or empty user profile received")
            if (profileData == null) {
                userProfile = null
                profileTimestamp = null
                profileSource = null
                clearPersistedProfile()
            }
            return
        }

        profileTimestamp = System.currentTimeMillis()
        profileSource = source

        try {
            val root = (profileData["userProfile"] as? Map<*, *>)
                ?: (profileData["profile"] as? Map<*, *>)
                ?: (profileData["user"] as? Map<*, *>)
                ?: profileData
            val basicData = root["basic"] as? Map<*, *> ?: root
            val loginData = root["login"] as? Map<*, *> ?: root
            val gameStatsData = root["gameStats"] as? Map<*, *> ?: root
            val earningsData = root["earnings"] as? Map<*, *> ?: root
            val rewardsData = root["claimableRewards"] as? Map<*, *> ?: root
            userProfile = UserProfile(
                basic = UserProfileBasic(
                    userId = basicData["userId"] as? String ?: basicData["id"] as? String,
                    userName = basicData["userName"] as? String ?: basicData["name"] as? String,
                    avatarUrl = basicData["avatarUrl"] as? String ?: basicData["avatar"] as? String,
                    ftueCompleted = basicData["ftueCompleted"] as? Boolean
                ),
                login = UserProfileLogin(
                    lastLoggedInOn = loginData["lastLoggedInOn"] as? String,
                    loginDay = (loginData["loginDay"] as? Number)?.toInt() ?: 1,
                    loginStreak = (loginData["loginStreak"] as? Number)?.toInt() ?: 1,
                    dailyLoginAwarded = loginData["dailyLoginAwarded"] as? Boolean ?: false,
                    isFirstLoginToday = loginData["isFirstLoginToday"] as? Boolean ?: false,
                    isFirstVisit = loginData["isFirstVisit"] as? Boolean ?: false,
                    ftueRewardGiven = loginData["ftueRewardGiven"] as? Boolean ?: false
                ),
                gameStats = UserProfileGameStats(
                    gamesPlayedToday = (gameStatsData["gamesPlayedToday"] as? Number)?.toInt() ?: 0,
                    gamesPlayedTotal = (gameStatsData["gamesPlayedTotal"] as? Number)?.toInt() ?: 0,
                    mostPlayedGame = gameStatsData["mostPlayedGame"] as? String ?: "",
                    mostPlayedGameCount = (gameStatsData["mostPlayedGameCount"] as? Number)?.toInt() ?: 0,
                    totalPlayTimeSec = (gameStatsData["totalPlayTimeSec"] as? Number)?.toInt() ?: 0
                ),
                earnings = UserProfileEarnings(
                    gemsEarnedToday = (earningsData["gemsEarnedToday"] as? Number)?.toInt() ?: 0,
                    gemsEarnedTotal = (earningsData["gemsEarnedTotal"] as? Number)?.toInt() ?: 0,
                    coinsEarnedTotal = (earningsData["coinsEarnedTotal"] as? Number)?.toInt() ?: 0,
                    totalCoinsEarnedToday = (earningsData["totalCoinsEarnedToday"] as? Number)?.toInt() ?: 0,
                    currentGems = (earningsData["currentGems"] as? Number)?.toInt() ?: 0
                ),
                claimableRewards = UserProfileClaimableRewards(
                    loginRewardCoinsForToday = (rewardsData["loginRewardCoinsForToday"] as? Number)?.toInt() ?: 0,
                    perGameRewardCoinsForToday = (rewardsData["perGameRewardCoinsForToday"] as? Number)?.toInt() ?: 0,
                    maxEarnableCoinForToday = (rewardsData["maxEarnableCoinForToday"] as? Number)?.toInt() ?: 0,
                    claimableGameRewardCoins = (rewardsData["claimableGameRewardCoins"] as? Number)?.toInt() ?: 0,
                    claimableLoginRewardCoins = (rewardsData["claimableLoginRewardCoins"] as? Number)?.toInt() ?: 0,
                    claimableSignupRewardCoins = (rewardsData["claimableSignupRewardCoins"] as? Number)?.toInt() ?: 0,
                    lockedLoginRewardCoins = (rewardsData["lockedLoginRewardCoins"] as? Number)?.toInt() ?: 0,
                    lockedGameRewardCoins = (rewardsData["lockedGameRewardCoins"] as? Number)?.toInt() ?: 0
                )
            )

            Log.d(TAG, "User profile updated from $source for user: ${userProfile?.basic?.userId}")
            persistUserProfile()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing user profile from $source", e)
        }
    }

    fun getUserProfile(): UserProfile? = userProfile

    fun getProfileSource(): String? = profileSource

    fun getProfileTimestamp(): Long? = profileTimestamp

    fun getDefaultUser(config: UserConfig): UserProfile {
        return UserProfile(
            basic = UserProfileBasic(
                userId = config.userId,
                userName = config.name,
                avatarUrl = config.avatar,
                authToken = config.authToken
            )
        )
    }

    /**
     * Increment coins earned (optimistic update before server confirms).
     */
    fun incrementCoinsEarned(amount: Int) {
        userProfile?.let { profile ->
            Log.d(TAG, "Incrementing coins by $amount (optimistic)")
            userProfile = profile.copy(
                earnings = profile.earnings.copy(
                    coinsEarnedTotal = profile.earnings.coinsEarnedTotal + amount,
                    totalCoinsEarnedToday = profile.earnings.totalCoinsEarnedToday + amount
                ),
                claimableRewards = profile.claimableRewards.copy(
                    lockedGameRewardCoins = profile.claimableRewards.lockedGameRewardCoins + amount
                )
            )
            persistUserProfile()
        }
    }

    /**
     * Increment gems earned (optimistic update).
     */
    fun incrementGemsEarned(amount: Int) {
        userProfile?.let { profile ->
            userProfile = profile.copy(
                earnings = profile.earnings.copy(
                    currentGems = profile.earnings.currentGems + amount,
                    gemsEarnedToday = profile.earnings.gemsEarnedToday + amount,
                    gemsEarnedTotal = profile.earnings.gemsEarnedTotal + amount
                )
            )
            persistUserProfile()
        }
    }

    /**
     * Override coins with server-authoritative values (after API response).
     */
    fun setCoinsEarnedOnApiResponse(totalCoinsEarnedToday: Int, coinsEarnedTotal: Int) {
        userProfile?.let { profile ->
            userProfile = profile.copy(
                earnings = profile.earnings.copy(
                    totalCoinsEarnedToday = if (totalCoinsEarnedToday >= 0) totalCoinsEarnedToday else profile.earnings.totalCoinsEarnedToday,
                    coinsEarnedTotal = if (coinsEarnedTotal >= 0) coinsEarnedTotal else profile.earnings.coinsEarnedTotal
                )
            )
            persistUserProfile()
        }
    }

    /**
     * Update gullak-specific fields from API response.
     */
    fun updateGullakProfile(gullakData: Map<String, Any?>) {
        userProfile?.let { profile ->
            userProfile = profile.copy(
                earnings = profile.earnings.copy(
                    totalCoinsEarnedToday = (gullakData["totalCoinsEarnedToday"] as? Number)?.toInt()
                        ?: profile.earnings.totalCoinsEarnedToday
                ),
                claimableRewards = profile.claimableRewards.copy(
                    loginRewardCoinsForToday = (gullakData["loginRewardCoinsForToday"] as? Number)?.toInt()
                        ?: profile.claimableRewards.loginRewardCoinsForToday,
                    perGameRewardCoinsForToday = (gullakData["perGameRewardCoinsForToday"] as? Number)?.toInt()
                        ?: profile.claimableRewards.perGameRewardCoinsForToday,
                    maxEarnableCoinForToday = (gullakData["maxEarnableCoinForToday"] as? Number)?.toInt()
                        ?: profile.claimableRewards.maxEarnableCoinForToday,
                    claimableGameRewardCoins = (gullakData["claimableGameRewardCoins"] as? Number)?.toInt()
                        ?: profile.claimableRewards.claimableGameRewardCoins,
                    claimableLoginRewardCoins = (gullakData["claimableLoginRewardCoins"] as? Number)?.toInt()
                        ?: profile.claimableRewards.claimableLoginRewardCoins,
                    lockedLoginRewardCoins = (gullakData["lockedLoginRewardCoins"] as? Number)?.toInt()
                        ?: profile.claimableRewards.lockedLoginRewardCoins,
                    lockedGameRewardCoins = (gullakData["lockedGameRewardCoins"] as? Number)?.toInt()
                        ?: profile.claimableRewards.lockedGameRewardCoins
                )
            )
            persistUserProfile()
        }
    }

    fun setFtueCompleted(completed: Boolean) {
        userProfile?.let { profile ->
            userProfile = profile.copy(
                basic = profile.basic.copy(ftueCompleted = completed),
                login = profile.login.copy(isFirstVisit = !completed)
            )
            persistUserProfile()
        }
    }

    fun setFtueRewardGiven(given: Boolean) {
        userProfile?.let { profile ->
            userProfile = profile.copy(
                login = profile.login.copy(ftueRewardGiven = given)
            )
            persistUserProfile()
        }
    }

    fun getCoinsEarnedToday(): Int = userProfile?.earnings?.totalCoinsEarnedToday ?: 0
    fun getCoinsEarnedTotal(): Int = userProfile?.earnings?.coinsEarnedTotal ?: 0
    fun getPerGameCoinReward(): Int = userProfile?.claimableRewards?.perGameRewardCoinsForToday ?: 0

    // endregion

    // region Game Context

    fun setCurrentGameId(id: String?) { currentGameId = id }
    fun getCurrentGameId(): String? = currentGameId

    fun setCurrentGameIsMaxGameBonusEarned(value: Boolean) { currentGameIsMaxGameBonusEarned = value }
    fun getCurrentGameIsMaxGameBonusEarned(): Boolean = currentGameIsMaxGameBonusEarned

    fun setSessionId(id: String?) {
        val usableId = id?.takeIf { it.isUsableSessionId() }
        sessionId = usableId
        if (usableId != null) {
            PlayerPrefsManager.set("sessionId", usableId)
        } else {
            PlayerPrefsManager.delete("sessionId")
        }
        prefs?.edit()?.remove("sessionId")?.apply() // remove legacy global value
    }
    fun getSessionId(): String? = sessionId

    fun setGameSessionId(id: String?) {
        gameSessionId = id?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun getGameSessionId(): String? = gameSessionId

    // endregion

    // region Persistence

    /**
     * Hydrate user profile from SharedPreferences cache.
     */
    fun hydrateUserProfile(userId: String? = null) {
        try {
            val currentUserId = userProfile?.basic?.userId
            if (!currentUserId.isNullOrBlank() && !userId.isNullOrBlank() && currentUserId != userId) {
                userProfile = null
                profileSource = null
                profileTimestamp = null
            }
            val key = getStorageKey(userId)
            val json = prefs?.getString(key, null) ?: return
            val cached = JSONObject(json)

            if (cached.optInt("version") != STORAGE_VERSION) {
                prefs?.edit()?.remove(key)?.apply()
                return
            }

            val profileJson = cached.optJSONObject("profile") ?: return
            // Rebuild UserProfile from cached JSON
            val basic = profileJson.optJSONObject("basic")
            val login = profileJson.optJSONObject("login")
            val gameStats = profileJson.optJSONObject("gameStats")
            val earnings = profileJson.optJSONObject("earnings")
            val rewards = profileJson.optJSONObject("claimableRewards")

            userProfile = UserProfile(
                basic = UserProfileBasic(
                    userId = basic?.optString("userId"),
                    userName = basic?.optString("userName"),
                    avatarUrl = basic?.optString("avatarUrl"),
                    ftueCompleted = if (basic?.has("ftueCompleted") == true) basic.optBoolean("ftueCompleted") else null
                ),
                login = UserProfileLogin(
                    lastLoggedInOn = login?.optString("lastLoggedInOn"),
                    loginDay = login?.optInt("loginDay", 1) ?: 1,
                    loginStreak = login?.optInt("loginStreak", 1) ?: 1,
                    dailyLoginAwarded = login?.optBoolean("dailyLoginAwarded") ?: false,
                    isFirstLoginToday = login?.optBoolean("isFirstLoginToday") ?: false,
                    isFirstVisit = login?.optBoolean("isFirstVisit") ?: false,
                    ftueRewardGiven = login?.optBoolean("ftueRewardGiven") ?: false
                ),
                gameStats = UserProfileGameStats(
                    gamesPlayedToday = gameStats?.optInt("gamesPlayedToday") ?: 0,
                    gamesPlayedTotal = gameStats?.optInt("gamesPlayedTotal") ?: 0,
                    mostPlayedGame = gameStats?.optString("mostPlayedGame") ?: "",
                    mostPlayedGameCount = gameStats?.optInt("mostPlayedGameCount") ?: 0,
                    totalPlayTimeSec = gameStats?.optInt("totalPlayTimeSec") ?: 0
                ),
                earnings = UserProfileEarnings(
                    gemsEarnedToday = earnings?.optInt("gemsEarnedToday") ?: 0,
                    gemsEarnedTotal = earnings?.optInt("gemsEarnedTotal") ?: 0,
                    coinsEarnedTotal = earnings?.optInt("coinsEarnedTotal") ?: 0,
                    totalCoinsEarnedToday = earnings?.optInt("totalCoinsEarnedToday") ?: 0,
                    currentGems = earnings?.optInt("currentGems") ?: 0
                ),
                claimableRewards = UserProfileClaimableRewards(
                    loginRewardCoinsForToday = rewards?.optInt("loginRewardCoinsForToday") ?: 0,
                    perGameRewardCoinsForToday = rewards?.optInt("perGameRewardCoinsForToday") ?: 0,
                    maxEarnableCoinForToday = rewards?.optInt("maxEarnableCoinForToday") ?: 0,
                    claimableGameRewardCoins = rewards?.optInt("claimableGameRewardCoins") ?: 0,
                    claimableLoginRewardCoins = rewards?.optInt("claimableLoginRewardCoins") ?: 0,
                    claimableSignupRewardCoins = rewards?.optInt("claimableSignupRewardCoins") ?: 0,
                    lockedLoginRewardCoins = rewards?.optInt("lockedLoginRewardCoins") ?: 0,
                    lockedGameRewardCoins = rewards?.optInt("lockedGameRewardCoins") ?: 0
                )
            )
            profileSource = "cache"
            profileTimestamp = cached.optLong("timestamp", System.currentTimeMillis())

            Log.d(TAG, "Profile hydrated from cache for user: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hydrate profile", e)
        }
    }

    private fun persistUserProfile() {
        try {
            val profile = userProfile ?: return
            val json = JSONObject().apply {
                put("version", STORAGE_VERSION)
                put("timestamp", profileTimestamp)
                put("profile", JSONObject().apply {
                    put("basic", JSONObject().apply {
                        put("userId", profile.basic.userId)
                        put("userName", profile.basic.userName)
                        put("avatarUrl", profile.basic.avatarUrl)
                        profile.basic.ftueCompleted?.let { put("ftueCompleted", it) }
                    })
                    put("login", JSONObject().apply {
                        put("lastLoggedInOn", profile.login.lastLoggedInOn)
                        put("loginDay", profile.login.loginDay)
                        put("loginStreak", profile.login.loginStreak)
                        put("dailyLoginAwarded", profile.login.dailyLoginAwarded)
                        put("isFirstLoginToday", profile.login.isFirstLoginToday)
                        put("isFirstVisit", profile.login.isFirstVisit)
                        put("ftueRewardGiven", profile.login.ftueRewardGiven)
                    })
                    put("gameStats", JSONObject().apply {
                        put("gamesPlayedToday", profile.gameStats.gamesPlayedToday)
                        put("gamesPlayedTotal", profile.gameStats.gamesPlayedTotal)
                        put("mostPlayedGame", profile.gameStats.mostPlayedGame)
                        put("mostPlayedGameCount", profile.gameStats.mostPlayedGameCount)
                        put("totalPlayTimeSec", profile.gameStats.totalPlayTimeSec)
                    })
                    put("earnings", JSONObject().apply {
                        put("gemsEarnedToday", profile.earnings.gemsEarnedToday)
                        put("gemsEarnedTotal", profile.earnings.gemsEarnedTotal)
                        put("coinsEarnedTotal", profile.earnings.coinsEarnedTotal)
                        put("totalCoinsEarnedToday", profile.earnings.totalCoinsEarnedToday)
                        put("currentGems", profile.earnings.currentGems)
                    })
                    put("claimableRewards", JSONObject().apply {
                        put("loginRewardCoinsForToday", profile.claimableRewards.loginRewardCoinsForToday)
                        put("perGameRewardCoinsForToday", profile.claimableRewards.perGameRewardCoinsForToday)
                        put("maxEarnableCoinForToday", profile.claimableRewards.maxEarnableCoinForToday)
                        put("claimableGameRewardCoins", profile.claimableRewards.claimableGameRewardCoins)
                        put("claimableLoginRewardCoins", profile.claimableRewards.claimableLoginRewardCoins)
                        put("claimableSignupRewardCoins", profile.claimableRewards.claimableSignupRewardCoins)
                        put("lockedLoginRewardCoins", profile.claimableRewards.lockedLoginRewardCoins)
                        put("lockedGameRewardCoins", profile.claimableRewards.lockedGameRewardCoins)
                    })
                })
            }
            prefs?.edit()?.putString(getStorageKey(), json.toString())?.apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist profile", e)
        }
    }

    private fun clearPersistedProfile() {
        prefs?.edit()?.remove(getStorageKey())?.apply()
    }

    private fun persistUserConfig(config: UserConfig) {
        try {
            val json = JSONObject().apply {
                put("version", STORAGE_VERSION)
                put("userId", config.userId)
                put("sessionId", config.sessionId)
                put("apiBaseUrl", config.apiBaseUrl)
                put("timezone", config.timezone)
                put("userAgent", config.userAgent)
                put("authToken", config.authToken)
                put("name", config.name)
                put("avatar", config.avatar)
                put("allowedDomains", JSONArray(config.allowedDomains))
            }
            prefs?.edit()?.putString(USER_CONFIG_KEY, json.toString())?.apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist user config", e)
        }
    }

    private fun hydrateUserConfig(): UserConfig? {
        return try {
            val jsonString = prefs?.getString(USER_CONFIG_KEY, null) ?: return null
            val json = JSONObject(jsonString)
            if (json.optInt("version") != STORAGE_VERSION) {
                prefs?.edit()?.remove(USER_CONFIG_KEY)?.apply()
                return null
            }
            val userId = json.optString("userId").takeIf { it.isNotBlank() }
            if (userId == null) {
                prefs?.edit()?.remove(USER_CONFIG_KEY)?.apply()
                return null
            }

            val allowedDomainsJson = json.optJSONArray("allowedDomains")
            val allowedDomains = allowedDomainsJson?.let { array ->
                List(array.length()) { index -> array.optString(index) }
                    .filter { it.isNotBlank() }
            } ?: emptyList()

            UserConfig(
                userId = userId,
                sessionId = json.optNullableString("sessionId") ?: return null,
                apiBaseUrl = json.getString("apiBaseUrl"),
                userAgent = json.optNullableString("userAgent") ?: return null,
                timezone = json.optNullableString("timezone"),
                authToken = json.optNullableString("authToken"),
                name = json.optNullableString("name"),
                avatar = json.optNullableString("avatar"),
                allowedDomains = allowedDomains
            ).also { Log.d(TAG, "User config hydrated for user: ${it.userId}") }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hydrate user config", e)
            null
        }
    }

    private fun getStorageKey(userId: String? = null): String {
        val resolvedUserId = userId?.takeIf { it.isNotBlank() }
            ?: userConfig?.userId?.takeIf { it.isNotBlank() }
            ?: "default"
        return "biome_state_$resolvedUserId"
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private fun String?.isUsableSessionId(): Boolean =
        !isNullOrBlank() && this != "sessionId"

    /**
     * Convert the current profile to a Map for sending to WebView.
     */
    fun getProfileAsMap(): Map<String, Any?> {
        val profile = userProfile ?: return emptyMap()
        return getProfileAsMap(profile)
    }

    /**
     * Convert the given profile to a Map for sending to WebView.
     */
    fun getProfileAsMap(profile: UserProfile): Map<String, Any?> {
        return mapOf(
            "basic" to mapOf(
                "userId" to profile.basic.userId,
                "userName" to profile.basic.userName,
                "avatarUrl" to profile.basic.avatarUrl,
                "ftueCompleted" to profile.basic.ftueCompleted
            ),
            "login" to mapOf(
                "lastLoggedInOn" to profile.login.lastLoggedInOn,
                "loginDay" to profile.login.loginDay,
                "loginStreak" to profile.login.loginStreak,
                "dailyLoginAwarded" to profile.login.dailyLoginAwarded,
                "isFirstLoginToday" to profile.login.isFirstLoginToday,
                "isFirstVisit" to profile.login.isFirstVisit,
                "ftueRewardGiven" to profile.login.ftueRewardGiven
            ),
            "gameStats" to mapOf(
                "gamesPlayedToday" to profile.gameStats.gamesPlayedToday,
                "gamesPlayedTotal" to profile.gameStats.gamesPlayedTotal,
                "mostPlayedGame" to profile.gameStats.mostPlayedGame,
                "mostPlayedGameCount" to profile.gameStats.mostPlayedGameCount,
                "totalPlayTimeSec" to profile.gameStats.totalPlayTimeSec
            ),
            "earnings" to mapOf(
                "gemsEarnedToday" to profile.earnings.gemsEarnedToday,
                "gemsEarnedTotal" to profile.earnings.gemsEarnedTotal,
                "coinsEarnedTotal" to profile.earnings.coinsEarnedTotal,
                "totalCoinsEarnedToday" to profile.earnings.totalCoinsEarnedToday,
                "currentGems" to profile.earnings.currentGems
            ),
            "claimableRewards" to mapOf(
                "loginRewardCoinsForToday" to profile.claimableRewards.loginRewardCoinsForToday,
                "perGameRewardCoinsForToday" to profile.claimableRewards.perGameRewardCoinsForToday,
                "maxEarnableCoinForToday" to profile.claimableRewards.maxEarnableCoinForToday,
                "claimableGameRewardCoins" to profile.claimableRewards.claimableGameRewardCoins,
                "claimableLoginRewardCoins" to profile.claimableRewards.claimableLoginRewardCoins,
                "claimableSignupRewardCoins" to profile.claimableRewards.claimableSignupRewardCoins,
                "lockedLoginRewardCoins" to profile.claimableRewards.lockedLoginRewardCoins,
                "lockedGameRewardCoins" to profile.claimableRewards.lockedGameRewardCoins
            )
        )
    }

    // endregion

    /**
     * Reset all state (e.g. on logout).
     */
    fun reset() {
        prefs?.edit()?.remove(getStorageKey())?.apply()
        userConfig = null
        userProfile = null
        profileTimestamp = null
        profileSource = null
        currentGameId = null
        currentGameIsMaxGameBonusEarned = false
        sessionId = null
        gameSessionId = null
        bonusConfig = null
        prefs?.edit()
            ?.remove(USER_CONFIG_KEY)
            ?.remove("sessionId")
            ?.apply()
    }
}
