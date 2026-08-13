package com.whaleup.gameshub.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class HubSessionFlow { FTUE, DAILY_LOGIN, FREE_ROAM }

/** Shared, user-scoped eligibility contract for native hub overlays. */
object SessionEligibility {
    fun currentFlow(): HubSessionFlow {
        val profile = BiomeState.getUserProfile()
        val hasServerProfile = BiomeState.getProfileSource() == "server" && profile != null
        val ftueCompleted = PlayerPrefsManager.get("ftue_completed", false).asBoolean()
        val legacyFirstVisit = PlayerPrefsManager.get("is_first_visit", true).asBoolean(true)
        val locallyCompletedFtue = ftueCompleted || !legacyFirstVisit

        val showFtue = if (hasServerProfile) {
            profile?.login?.isFirstVisit == true && !locallyCompletedFtue
        } else {
            !locallyCompletedFtue
        }
        if (showFtue) return HubSessionFlow.FTUE

        val completed = PlayerPrefsManager.get("daily_login_completed", false).asBoolean()
        val lastDate = PlayerPrefsManager.get("last_login_date", "") as? String ?: ""
        val claimedToday = completed && lastDate == today()
        val backendEligible = profile?.let {
            it.claimableRewards.claimableLoginRewardCoins > 0 ||
                (it.login.isFirstLoginToday && !it.login.dailyLoginAwarded)
        } ?: false

        return if (!claimedToday && (!hasServerProfile || backendEligible)) {
            HubSessionFlow.DAILY_LOGIN
        } else {
            HubSessionFlow.FREE_ROAM
        }
    }

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun Any?.asBoolean(default: Boolean = false): Boolean = when (this) {
        is Boolean -> this
        is Number -> toInt() != 0
        is String -> equals("true", ignoreCase = true)
        else -> default
    }
}
