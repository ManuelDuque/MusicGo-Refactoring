package com.unex.musicgo.ui.vms.factories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.unex.musicgo.database.MusicGoDatabase
import com.unex.musicgo.ui.vms.HomeActivityViewModel

class HomeActivityViewModelFactory(
    private val database: MusicGoDatabase
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeActivityViewModel::class.java)) {
            return HomeActivityViewModel(database) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}