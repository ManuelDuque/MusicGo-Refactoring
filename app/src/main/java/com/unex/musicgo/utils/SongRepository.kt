package com.unex.musicgo.utils

import android.util.Log
import com.unex.musicgo.api.MusicGoAPI
import com.unex.musicgo.api.getAuthToken
import com.unex.musicgo.data.toSong
import com.unex.musicgo.database.dao.SongsDao
import com.unex.musicgo.models.Song
import com.unex.musicgo.ui.vms.SongDetailsFragmentViewModel

class SongRepository(
    private val networkService: MusicGoAPI,
    private val songsDao: SongsDao
) {

    private val TAG = "SongRepository"

    /**
     * Fetch a song from the database or the network with the given trackId.
     * @param trackId the id of the song to fetch
     * @return the song fetched
     */
    suspend fun fetchSong(
        trackId: String,
        onSuccess: (Song) -> Unit,
    ) {
        Log.d(TAG, "fetchSong trackId: $trackId")
        if(trackId.isEmpty()) return
        val song = songsDao.getSongById(trackId).value
        Log.d(TAG, "fetchSong song: $song")
        if (song != null) {
            onSuccess(song)
            return
        }
        Log.d(TAG, "fetchSong song from network")
        val track = networkService.getTrack(getAuthToken(), trackId)
        Log.d(SongDetailsFragmentViewModel.TAG, "fetchSong track: $track")
        val songFromTrack = track.toSong()
        Log.d(SongDetailsFragmentViewModel.TAG, "fetchSong songFromTrack: $songFromTrack")
        songsDao.insert(songFromTrack)
        onSuccess(songFromTrack)
    }

}

