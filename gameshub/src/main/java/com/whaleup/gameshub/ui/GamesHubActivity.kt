package com.whaleup.gameshub.ui

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.whaleup.gameshub.R
import com.whaleup.gameshub.data.GamesHubSession
import com.whaleup.gameshub.util.InternetErrorRetryHandler

class GamesHubActivity : AppCompatActivity(), InternetErrorRetryHandler {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_GamesHub_Light)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_games_hub_container)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, GamesHubFragment.newInstance())
                .commit()
        }
    }

    override fun retryAfterInternetError() {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? GamesHubFragment
        fragment?.retryAfterInternetError()
    }
}
