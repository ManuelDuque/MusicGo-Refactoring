package com.unex.musicgo.ui.vms

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.unex.musicgo.database.MusicGoDatabase
import kotlinx.coroutines.launch

class StatisticsFragmentViewModel(
    private val database: MusicGoDatabase
): ViewModel() {

    val toastLiveData = MutableLiveData<String>()
    val isLoading = MutableLiveData<Boolean>(false)

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

    class Factory(
        private val database: MusicGoDatabase
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(StatisticsFragmentViewModel::class.java)) {
                return StatisticsFragmentViewModel(database) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}