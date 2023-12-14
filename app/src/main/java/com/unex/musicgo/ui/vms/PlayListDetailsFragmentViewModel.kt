package com.unex.musicgo.ui.vms

import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.unex.musicgo.api.getAuthToken
import com.unex.musicgo.api.getNetworkService
import com.unex.musicgo.database.MusicGoDatabase
import com.unex.musicgo.models.Genre
import com.unex.musicgo.models.PlayList
import com.unex.musicgo.models.PlayListWithSongs
import com.unex.musicgo.models.Song
import com.unex.musicgo.ui.fragments.PlayListDetailsFragment
import kotlinx.coroutines.launch

class PlayListDetailsFragmentViewModel(
    private val database: MusicGoDatabase
): ViewModel() {

    val toastLiveData = MutableLiveData<String>()
    val spinnerActiveLiveData = MutableLiveData<Boolean>()

    private val _state = MutableLiveData<String>()
    val state: LiveData<String> = _state

    private val _playlist = MutableLiveData<PlayList>()
    val playlist: LiveData<PlayList> get() = _playlist
    val playlistWithSongs = playlist.switchMap {
        val playlistWithSongs = MutableLiveData<PlayListWithSongs>()
        viewModelScope.launch {
            val playlistId = it.id
            playlistWithSongs.postValue(database.playListDao().getPlayList(playlistId).value)
        }
        playlistWithSongs
    }

    fun setState(state: String) = _state.postValue(state)
    fun setPlaylist(playlist: PlayList) = _playlist.postValue(playlist)

    fun deleteSongFromPlayList(song: Song) {
        viewModelScope.launch {
            val playlistId = playlist.value?.id
            if (playlistId == null) {
                toastLiveData.postValue("Error")
                return@launch
            }
            val songId = song.id
            try {
                database.playListSongCrossRefDao().delete(playlistId, songId)
            } catch (e: Exception) {
                toastLiveData.postValue(e.message)
            }
        }
    }

    private fun validateTitle(title: String): Boolean {
        if (title.isEmpty()) {
            toastLiveData.postValue("The title of the playlist cannot be empty")
            return false
        }
        return true
    }

    private fun saveTitle(title: String) {
        if (validateTitle(title)) {

        }
    }

    private fun saveTitle() {
        val title = binding.playlistName.text.toString()
        if (playlist == null) {
            playlist = PlayList(title = title, description = "")
            Toast.makeText(
                requireContext(),
                "Add a description to the playlist to save it",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            playlist!!.title = title
            lifecycleScope.launch {
                playlist?.let {
                    if (state == PlayListDetailsFragment.State.CREATE) {
                        db?.playListDao()?.insert(it)
                    } else {
                        db?.playListDao()?.update(it)
                    }
                }
            }
        }
        binding.playlistInfoNames.text = title
    }

}