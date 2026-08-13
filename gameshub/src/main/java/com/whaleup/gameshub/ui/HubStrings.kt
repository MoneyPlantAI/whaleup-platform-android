package com.whaleup.gameshub.ui

import org.json.JSONObject

/** Session-scoped remote UI strings with compiled fallbacks for every leaf. */
object HubStrings {
    private val defaults = JSONObject(
        """{
          "error":{"api":{"title":"Service Unavailable","button":"Close","message":"We are having trouble connecting to our servers."},"oom":{"title":"Memory Full","button":"Reload","message":"Please close other apps and try again."},"generic":{"title":"Something Went Wrong","button":"Try Again","message":"We are working on this, please try again."},"connectivity":{"title":"No Internet","button":"Retry","message":"Check your connection and try again."}},
          "common":{"share":"Share","sharing":"Sharing…","claiming":"Claiming…","continueText":"Continue","playAndEarn":"Play & earn","conversionRate":"1 = ₹1","coins":"coins"},
          "nativeHub":{"title":"WhaleUp Games","badge":"Play","entered":"Native Hub entered"},
          "leaderboard":{"title":"Leaderboard","subtitle":"Top players across WhaleUp","badge":"🏆 Weekly","empty":"No rankings available yet."},
          "gameCard":{"newGame":"New Game","playAndWin":" Play & win ","playAgain":" Play again! "},
          "gameWin":{"eyebrow":"VICTORY","youEarned":"You earned ","claiming":"Claiming…","subtitle":"Yayy, you earned for playing {gameName}!","shareButton":"Share","sharingButton":"Sharing…","continuePlayingButton":"Continue playing","shareTitle":"I won {coins} coins on WhaleUp! 🎉","shareMessage":"I just played {gameName} on WhaleUp and earned {coins} coins!","fallbackGameName":"game"},
          "dailyLogin":{"eyebrow":"DAILY REWARD","dayLabel":"Day {day}","youEarned":"You earned ","claiming":"Claiming…","subtitle":"Yayy, you earned for daily visit!","ctaButton":"Play & earn"},
          "onboarding":{"skip":"Skip","next":"Next","startPlaying":"Start Playing","coinsBanner":"WhaleUp Coins","welcome":{"eyebrow":"WELCOME","headline":"Dive into WhaleUp","body":"Casual games, head-to-head matches, and rewards - all inside one tab."},"browse":{"eyebrow":"STEP 1","headline":"Pick Your Game","body":"Tap any game to jump in."},"earn":{"eyebrow":"STEP 2","headline":"Play & Earn Coins","body":"Every win drops WhaleUp Coins into your wallet."},"leaderboard":{"eyebrow":"STEP 3","headline":"Climb the Leaderboard","body":"Compete with players across the country."},"redeem":{"eyebrow":"STEP 4","headline":"Redeem for Rewards","body":"Trade coins for rewards."}}
        }""".trimIndent()
    )

    @Volatile private var active = JSONObject(defaults.toString())

    fun merge(remote: JSONObject) {
        active = deepMerge(JSONObject(defaults.toString()), remote)
    }

    fun reset() {
        active = JSONObject(defaults.toString())
    }

    fun get(path: String, fallback: String = ""): String {
        var value: Any = active
        path.split('.').forEach { key ->
            value = (value as? JSONObject)?.opt(key) ?: return fallback
        }
        return (value as? String)?.takeIf { it.isNotEmpty() } ?: fallback
    }

    private fun deepMerge(target: JSONObject, source: JSONObject): JSONObject {
        source.keys().forEach { key ->
            val sourceValue = source.opt(key)
            val targetValue = target.opt(key)
            if (sourceValue is JSONObject && targetValue is JSONObject) {
                target.put(key, deepMerge(targetValue, sourceValue))
            } else if (sourceValue != null && sourceValue != JSONObject.NULL) {
                target.put(key, sourceValue)
            }
        }
        return target
    }
}
