package com.unex.musicgo.ui.vms

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.unex.musicgo.database.MusicGoDatabase
import com.unex.musicgo.models.User
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SignUpActivityViewModel(
    private val database: MusicGoDatabase,
    private val auth: FirebaseAuth
): ViewModel() {

    val toastLiveData = MutableLiveData<String>()
    val isLoggedLiveData = MutableLiveData<Boolean>()

    init {
        if (auth.currentUser != null) {
            isLoggedLiveData.value = true
        }
    }

    fun validateCredentials(email: String, password: String, username: String, userSurname: String): Boolean {
        if (email.isEmpty()) {
            toastLiveData.value = "Please enter your email."
            return false
        }
        if (!email.matches(Regex("[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+"))) {
            toastLiveData.value = "Please enter a valid email."
            return false
        }
        if (password.isEmpty()) {
            toastLiveData.value = "Please enter your password."
            return false
        }
        if (password.length < 6) {
            toastLiveData.value = "Password must be at least 6 characters."
            return false
        }
        if (username.isEmpty()) {
            toastLiveData.value = "Please enter your username."
            return false
        }
        if (userSurname.isEmpty()) {
            toastLiveData.value = "Please enter your surname."
            return false
        }
        return true
    }

    fun signUp(email: String, password: String, username: String, userSurname: String) {
        viewModelScope.launch {
            try {
                val user = hashMapOf(
                    "username" to username,
                    "userSurname" to userSurname,
                    "email" to email,
                    "password" to password,
                )
                auth.createUserWithEmailAndPassword(email, password).await()
                Firebase.firestore.collection("users").add(user).await()
                val userData = User(
                    email = email,
                    userSurname = userSurname,
                    username = username,
                )
                database.userDao().deleteAll()
                database.userDao().insertUser(userData)
                isLoggedLiveData.value = true
            } catch (e: Exception) {
                toastLiveData.value = e.message
            }
        }
    }

}