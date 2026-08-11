package com.whaleup.gameshub.data

/**
 * Structured user profile data matching Whaleup's UserProfile.ts.
 * Stored in BiomeState and persisted via SharedPreferences.
 */
data class UserProfile(
    val basic: UserProfileBasic = UserProfileBasic(),
    val login: UserProfileLogin = UserProfileLogin(),
    val gameStats: UserProfileGameStats = UserProfileGameStats(),
    val earnings: UserProfileEarnings = UserProfileEarnings(),
    val claimableRewards: UserProfileClaimableRewards = UserProfileClaimableRewards()
)

data class UserProfileBasic(
    val userId: String? = null,
    val userName: String? = null,
    val avatarUrl: String? = null,
    val authToken: String? = null,
    val ftueCompleted: Boolean? = null
)

data class UserProfileLogin(
    val lastLoggedInOn: String? = null,
    val loginDay: Int = 1,
    val loginStreak: Int = 1,
    val dailyLoginAwarded: Boolean = false,
    val isFirstVisit: Boolean = false,
    val ftueRewardGiven: Boolean = false
)

data class UserProfileGameStats(
    val gamesPlayedToday: Int = 0,
    val gamesPlayedTotal: Int = 0,
    val mostPlayedGame: String = "",
    val mostPlayedGameCount: Int = 0,
    val totalPlayTimeSec: Int = 0
)

data class UserProfileEarnings(
    val gemsEarnedToday: Int = 0,
    val gemsEarnedTotal: Int = 0,
    val coinsEarnedTotal: Int = 0,
    val totalCoinsEarnedToday: Int = 0,
    val currentGems: Int = 0
)

data class UserProfileClaimableRewards(
    val loginRewardCoinsForToday: Int = 0,
    val perGameRewardCoinsForToday: Int = 0,
    val maxEarnableCoinForToday: Int = 0,
    val claimableGameRewardCoins: Int = 0,
    val claimableLoginRewardCoins: Int = 0,
    val claimableSignupRewardCoins: Int = 0,
    val lockedLoginRewardCoins: Int = 0,
    val lockedGameRewardCoins: Int = 0,
    val monthlyRewardsBonusAwarded: Boolean = false
)
