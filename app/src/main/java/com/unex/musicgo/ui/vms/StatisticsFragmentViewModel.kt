package com.unex.musicgo.ui.vms

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.unex.musicgo.MusicGoApplication
import com.unex.musicgo.utils.Repository
import kotlinx.coroutines.launch

class StatisticsFragmentViewModel(
    private val repository: Repository
): ViewModel() {

    val toastLiveData = MutableLiveData<String>()
    val isLoading = MutableLiveData(false)

    private var _favoriteSongTitle = MutableLiveData<String>()
    val favoriteSongTitle: LiveData<String> = _favoriteSongTitle

    private var _favoriteSongArtist = MutableLiveData<String>()
    val favoriteSongArtist: LiveData<String> = _favoriteSongArtist

    private var _favoriteArtistName = MutableLiveData<String>()
    val favoriteArtistName: LiveData<String> = _favoriteArtistName

    private var _totalTimePlayed = MutableLiveData<Long>()
    val totalTimePlayed: LiveData<String> get() = _totalTimePlayed.switchMap { time ->
        MutableLiveData(transformTime(time))
    }

    init {
        viewModelScope.launch {
            try {
                val database = repository.database
                isLoading.postValue(true)
                val songStatistics = database.statisticsDao().getMostPlayedSong()
                val artistStatistics = database.statisticsDao().getMostPlayedArtist()
                val totalTime = database.statisticsDao().getTotalTimePlayed()
                _favoriteSongTitle.postValue(songStatistics.title)
                _favoriteSongArtist.postValue(songStatistics.artist)
                _favoriteArtistName.postValue(artistStatistics.artist)
                _totalTimePlayed.postValue(totalTime)
            } catch (e: Exception) {
                toastLiveData.postValue(e.message)
            } finally {
                isLoading.postValue(false)
            }
        }
    }

    private fun transformTime(time: Long): String {
        val days = time / 86400000
        val hours = (time % 86400000) / 3600000
        val minutes = (time % 3600000) / 60000
        val seconds = (time % 60000) / 1000
        return if (days > 0) {
            "$days days, $hours hours, $minutes minutes and $seconds seconds"
        } else if (hours > 0) {
            "$hours hours, $minutes minutes and $seconds seconds"
        } else if (minutes > 0) {
            "$minutes minutes and $seconds seconds"
        } else {
            "$seconds seconds"
        }
    }

    companion object {
        const val TAG = "StatisticsFragmentViewModel"

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val viewModel = StatisticsFragmentViewModel(
                    (application as MusicGoApplication).appContainer.repository,
                )
                return viewModel as T
            }
        }
    }
}