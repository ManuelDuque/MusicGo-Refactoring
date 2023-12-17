package com.unex.musicgo.ui.vms

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.unex.musicgo.MusicGoApplication
import com.unex.musicgo.models.User
import com.unex.musicgo.ui.fragments.SettingsFragment
import com.unex.musicgo.utils.Repository
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

class SettingsFragmentViewModel(
    private val repository: Repository
): ViewModel() {

    val toastLiveData = MutableLiveData<String>()
    val isLoading = MutableLiveData(false)

    private val _user = MutableLiveData<User>()
    val user: LiveData<User> = _user

    init {
        getUser()
    }

    fun getUser() {
        viewModelScope.launch {
            isLoading.value = true
            isLoading.value = false
            val user = repository.database.userDao().getUserByEmail(repository.auth.currentUser?.email.toString())
            _user.postValue(user)
        }
    }

    fun deleteAccount(success: () -> Unit, error: (message: String) -> Unit) {
        viewModelScope.launch {
            val user = repository.auth.currentUser
            val email = user?.email
            Log.d(SettingsFragment.TAG, "Email: $email")
            // Get the user collection
            val userCollection = Firebase.firestore.collection("users").get().await()
            if (userCollection == null) {
                Log.d(SettingsFragment.TAG, "Error getting documents: users")
                error("Error getting documents: users")
                return@launch
            }
            // Get the user document
            val userDoc = userCollection.documents.filter {
                it.data?.get("email") == email
            }
            if (userDoc.isEmpty()) {
                Log.d(SettingsFragment.TAG, "Error getting documents: user")
                error("Error getting documents: user")
                return@launch
            }
            // Delete the user document and the user from the authentication
            try {
                Firebase.firestore.collection("users").document(userDoc[0].id).delete().await()
                user!!.delete().await()
                Log.d(SettingsFragment.TAG, "User account deleted.")
                success()
            } catch (e: Exception) {
                Log.d(SettingsFragment.TAG, "Error deleting user: $e")
                // Restore the user document and the user from the authentication
                try {
                    Firebase.firestore.collection("users").add(userDoc[0].data!!).await()
                } catch (e: Exception) {
                    Log.d(SettingsFragment.TAG, "Error restoring user: $e")
                    error("Error restoring user: $e")
                }
                error("Error deleting user: $e")
            }
        }
    }

    fun logOut(success: () -> Unit) {
        viewModelScope.launch {
            isLoading.value = true
            repository.auth.signOut()
            repository.database.userDao().deleteAll()
            isLoading.value = false
            success()
        }
    }

    fun getLang(currentLocale: Locale): String {
        val languageInfoName = when (currentLocale.language) {
            "en" -> "English"
            "es" -> "Español"
            else -> "English"
        }
        return languageInfoName
    }

    companion object {
        const val TAG = "SettingsFragmentViewModel"

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val viewModel = SettingsFragmentViewModel(
                    (application as MusicGoApplication).appContainer.repository,
                )
                return viewModel as T
            }
        }
    }
}