package com.unex.musicgo.ui.vms

import android.content.Context
import android.net.ConnectivityManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.unex.musicgo.MusicGoApplication
import com.unex.musicgo.api.getAuthToken
import com.unex.musicgo.api.getNetworkService
import com.unex.musicgo.data.toSong
import com.unex.musicgo.models.Song
import com.unex.musicgo.utils.Repository
import kotlinx.coroutines.launch

class SongListFavoritesFragmentViewModel(
    private val repository: Repository
): ViewModel() {

    val toastLiveData = MutableLiveData<String>()
    val isLoading = MutableLiveData(false)

    private var _songs = MutableLiveData<List<Song>>()
    val songs: LiveData<List<Song>> =  _songs

    suspend fun fetchSongs() {
        val database = repository.database
        val songs = mutableListOf<Song>()
        // Get the songs most played
        val listMostPlayedStatistics = database.statisticsDao().getAllMostPlayedSong(3)
        // Get the songs from database or network
        val service = getNetworkService()
        var token: String? = null
        listMostPlayedStatistics.forEach {
            val songOnDB = database.songsDao().getSongById(it.songId)
            if (songOnDB != null) {
                songs.add(songOnDB)
            } else {
                if (token == null) token = getAuthToken()
                val songOnNetwork = service.getTrack(token!!, it.songId)
                val song = songOnNetwork.toSong()
                songs.add(song)
                database.songsDao().insert(song)
            }
        }
        _songs.postValue(songs)
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        if (activeNetwork == null) toastLiveData.postValue("No internet connection")
        return activeNetwork != null
    }

    fun load(context: Context, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                isLoading.postValue(true)
                if(isNetworkAvailable(context)) {
                    block()
                }
            } catch (e: Exception) {
                toastLiveData.postValue(e.message)
            } finally {
                isLoading.postValue(false)
            }
        }
    }

    companion object {
        const val TAG = "SongListFavoritesFragmentViewModel"

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val viewModel = SongListFavoritesFragmentViewModel(
                    (application as MusicGoApplication).appContainer.repository,
                )
                return viewModel as T
            }
        }
    }
}