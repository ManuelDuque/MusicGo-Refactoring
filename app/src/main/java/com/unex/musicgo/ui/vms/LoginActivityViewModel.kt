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

class LoginActivityViewModel(
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

    fun signInWithEmailAndPassword(email: String, password: String) {
        if (email.isEmpty()) {
            toastLiveData.value = "Please enter your email."
            return
        }
        if (!email.matches(Regex("[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+"))) {
            toastLiveData.value = "Please enter a valid email."
            return
        }
        if (password.isEmpty()) {
            toastLiveData.value = "Please enter your password."
            return
        }
        if (password.length < 6) {
            toastLiveData.value = "Password must be at least 6 characters."
            return
        }
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    saveUser()
                } else {
                    toastLiveData.value = "Authentication failed."
                }
            }
    }

    private fun saveUser() {
        val email = auth.currentUser?.email
        val userCollection = Firebase.firestore.collection("users")
        userCollection.whereEqualTo("email", email).get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val userEmail = document.data["email"].toString()
                    val userSurname = document.data["userSurname"].toString()
                    val username = document.data["username"].toString()
                    val user = User(
                        email = userEmail,
                        userSurname = userSurname,
                        username = username,
                    )
                    viewModelScope.launch {
                        database.userDao().deleteAll()
                        database.userDao().insertUser(user)
                        isLoggedLiveData.value = true
                    }
                }
            }
            .addOnFailureListener {
                toastLiveData.value = "Error getting user data."
            }
    }

}