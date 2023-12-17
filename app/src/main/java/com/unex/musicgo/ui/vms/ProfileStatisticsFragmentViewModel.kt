package com.unex.musicgo.ui.vms

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.unex.musicgo.MusicGoApplication
import com.unex.musicgo.models.User
import com.unex.musicgo.utils.Repository
import kotlinx.coroutines.launch

class ProfileStatisticsFragmentViewModel(
    private val repository: Repository
): ViewModel() {

    val toastLiveData = MutableLiveData<String>()
    val isLoading = MutableLiveData(false)

    private val _user = MutableLiveData<User>()
    val user: LiveData<User> = _user

    fun fetchUser() {
        viewModelScope.launch {
            isLoading.value = true
            try {
                val user =  repository.auth.currentUser
                val userDb = repository.database.userDao().getUserByEmail(user!!.email!!)
                _user.postValue(userDb)
            } catch (e: Exception) {
                toastLiveData.value = e.message
            } finally {
                isLoading.value = false
            }
        }
    }

    companion object {
        const val TAG = "ProfileStatisticsFragmentViewModel"

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val viewModel = ProfileStatisticsFragmentViewModel(
                    (application as MusicGoApplication).appContainer.repository,
                )
                return viewModel as T
            }
        }
    }
}