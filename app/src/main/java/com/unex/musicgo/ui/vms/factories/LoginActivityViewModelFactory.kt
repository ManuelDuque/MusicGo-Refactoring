package com.unex.musicgo.ui.vms.factories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth
import com.unex.musicgo.database.MusicGoDatabase
import com.unex.musicgo.ui.vms.LoginActivityViewModel

class LoginActivityViewModelFactory(
    private val database: MusicGoDatabase,
    private val auth: FirebaseAuth
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginActivityViewModel::class.java)) {
            return LoginActivityViewModel(database, auth) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}