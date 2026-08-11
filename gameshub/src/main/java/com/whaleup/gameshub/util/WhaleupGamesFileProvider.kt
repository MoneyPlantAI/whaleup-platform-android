package com.whaleup.gameshub.util

import androidx.core.content.FileProvider

/**
 * Custom FileProvider to avoid manifest merger conflicts with host apps.
 */
class WhaleupGamesFileProvider : FileProvider()
