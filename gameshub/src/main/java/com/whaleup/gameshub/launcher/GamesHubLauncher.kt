package com.whaleup.gameshub.launcher

import android.content.Context
import android.content.Intent
import androidx.fragment.app.Fragment
import com.whaleup.gameshub.data.GamesHubSession
import com.whaleup.gameshub.ui.GamesHubActivity
import com.whaleup.gameshub.ui.GamesHubFragment

object GamesHubLauncher {

    fun logout() {
        GamesHubSession.logout()
    }

    fun open(context: Context, props: BiomeSdkProps) {
        GamesHubSession.initialize(context)
        GamesHubSession.props = props
        val intent = Intent(context, GamesHubActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Returns a Fragment instance for the Games Hub.
     * Host app must initialize the SDK via initialize() if not using GamesHubLauncher.open().
     */
    fun getFragment(context: Context, props: BiomeSdkProps): Fragment {
        GamesHubSession.initialize(context)
        GamesHubSession.props = props
        return GamesHubFragment.newInstance(props)
    }
}
