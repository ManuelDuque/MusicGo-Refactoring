package com.unex.musicgo.utils

import android.content.Context
import com.unex.musicgo.api.getAuthService
import com.unex.musicgo.api.getNetworkService
import com.unex.musicgo.database.MusicGoDatabase

class AppContainer(context: Context?) {
    private val authService = getAuthService()
    private val networkService = getNetworkService()
    private val db = MusicGoDatabase.getInstance(context!!)!!

    val repository = Repository(authService, networkService, db)
}