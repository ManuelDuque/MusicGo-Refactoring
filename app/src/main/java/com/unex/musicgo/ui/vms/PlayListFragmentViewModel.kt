package com.unex.musicgo.ui.vms

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.unex.musicgo.MusicGoApplication
import com.unex.musicgo.models.PlayList
import com.unex.musicgo.models.PlayListSongCrossRef
import com.unex.musicgo.models.Song
import com.unex.musicgo.utils.Repository
import kotlinx.coroutines.launch

class PlayListFragmentViewModel(
    private val repository: Repository
): ViewModel() {

    val toastLiveData = MutableLiveData<String>()
    val isLoading = MutableLiveData(false)

    private var _song = MutableLiveData<Song>()
    val song: LiveData<Song> = _song

    private var _playlists = MutableLiveData<List<PlayList>>()
    val playlists: LiveData<List<PlayList>> = _playlists

    fun setSong(song: Song?) {
        song?.let {
            _song.postValue(it)
        }
    }

    fun fetchPlayLists() {
        this.load {
            val playlists = repository.database.playListDao().getPlayListsCreatedByUserWithoutSongs()
            _playlists.postValue(playlists)
        }
    }

    fun addSongToPlayList(playList: PlayList, onSuccess: (message: String) -> Unit = {}) {
        this.load {
            // Create a new cross reference between the song and the playlist
            val crossRef = PlayListSongCrossRef(playList.id, song.value!!.id)
            repository.database.playListSongCrossRefDao().insert(crossRef)
            onSuccess("Song added to playlist ${playList.title}")
        }
    }

    private fun load(block: suspend () -> Unit) {
        viewModelScope.launch {
            isLoading.postValue(true)
            try {
                block()
            } catch (e: Exception) {
                toastLiveData.postValue(e.message)
            } finally {
                isLoading.postValue(false)
            }
        }
    }

    fun songIsNull(): Boolean {
        return _song.value == null
    }

    companion object {
        const val TAG = "PlayListFragmentViewModel"

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val viewModel = PlayListFragmentViewModel(
                    (application as MusicGoApplication).appContainer.repository,
                )
                return viewModel as T
            }
        }
    }
}