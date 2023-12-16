package com.unex.musicgo.ui.vms

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.unex.musicgo.MusicGoApplication
import com.unex.musicgo.api.getAuthToken
import com.unex.musicgo.api.getNetworkService
import com.unex.musicgo.data.toSong
import com.unex.musicgo.database.MusicGoDatabase
import com.unex.musicgo.models.Comment
import com.unex.musicgo.models.PlayListSongCrossRef
import com.unex.musicgo.models.PlayListWithSongs
import com.unex.musicgo.models.Song
import com.unex.musicgo.ui.fragments.SongDetailsFragment
import com.unex.musicgo.utils.Repository
import kotlinx.coroutines.launch

class SongDetailsFragmentViewModel(
    private val repository: Repository
): ViewModel() {

    private val database by lazy { repository.database }

    val toastLiveData = MutableLiveData<String>()
    val isLoading = MutableLiveData(false)

    /* Song */
    private var _trackId = MutableLiveData<String>()
    val song: LiveData<Song> = _trackId.switchMap { trackId ->
        val liveData = MutableLiveData<Song>()
        viewModelScope.launch {
            try {
                isLoading.postValue(true)
                var song = database.songsDao().getSongById(trackId)
                if (song == null) {
                    song = fetchSong(trackId)
                }
                liveData.postValue(song)
            } catch (e: Exception) {
                toastLiveData.postValue(e.message)
            } finally {
                isLoading.postValue(false)
            }
        }
        liveData
    }

    /* Favorites playlist */
    private var _favoritesPlaylistLiveData: MutableLiveData<PlayListWithSongs> = MutableLiveData()
    val favoritesPlaylistLiveData: LiveData<PlayListWithSongs> = _favoritesPlaylistLiveData

    /* Comments */
    private val commentsLiveData = MutableLiveData<List<String>>()
    val comments: LiveData<List<String>> = commentsLiveData

    /* Playing controls */
    private val isPlayingLiveData = MutableLiveData<Boolean>(false)
    val isPlaying: LiveData<Boolean> = isPlayingLiveData
    private val progressLiveData = MutableLiveData<Int>(0)
    val progress: LiveData<Int> = progressLiveData

    init {
        viewModelScope.launch {
            _favoritesPlaylistLiveData.postValue(repository.database.playListDao().getFavoritesPlayList())
        }
    }

    fun setTrackId(trackId: String) {
        _trackId.postValue(trackId)
    }

    fun pause() {
        isPlayingLiveData.postValue(false)
    }

    fun play(context: Context) {
        if (isPlaying.value == true) return
        if (isNetworkAvailable(context)) {
            val preview = song.value?.previewUrl
            if (preview.isNullOrEmpty()) {
                toastLiveData.postValue("This song does not have a preview")
                return
            }
        }
        isPlayingLiveData.postValue(true)
    }

    fun createComment(username: String, email: String, comment: String): Comment {
        return Comment(
            songId = _trackId.value!!,
            authorEmail = email,
            username = username,
            description = comment,
            timestamp = System.currentTimeMillis()
        )
    }

    fun updateSongRating(rate: Int) {
        viewModelScope.launch {
            val _song = song.value
            val favorites = favoritesPlaylistLiveData.value
            _song?.let {
                it.isRated = true
                it.rating = rate
                favorites?.let { list ->
                    val songsInFavorites = list.songs.find { song ->
                        song.id == _song.id
                    } != null
                    database.songsDao().insert(it)
                    if (songsInFavorites && rate < 4) {
                        database.playListSongCrossRefDao().delete(list.playlist.id, _song.id)
                    } else if (rate >= 4) {
                        val crossRef = PlayListSongCrossRef(list.playlist.id, _song.id)
                        database.playListSongCrossRefDao().insert(crossRef)
                    }
                }
            }
        }
    }

    private suspend fun fetchSong(trackId: String): Song {
        val auth = getAuthToken()
        val service = getNetworkService()
        val track = service.getTrack(auth, trackId)
        return track.toSong()
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
        const val TAG = "SongDetailsFragmentViewModel"

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val viewModel = SongDetailsFragmentViewModel(
                    (application as MusicGoApplication).appContainer.repository,
                )
                return viewModel as T
            }
        }
    }
}