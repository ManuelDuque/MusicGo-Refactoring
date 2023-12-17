package com.unex.musicgo.ui.vms

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.unex.musicgo.MusicGoApplication
import com.unex.musicgo.api.getAuthToken
import com.unex.musicgo.api.getNetworkService
import com.unex.musicgo.data.api.common.Items
import com.unex.musicgo.data.toSong
import com.unex.musicgo.models.PlayListWithSongs
import com.unex.musicgo.models.Song
import com.unex.musicgo.ui.enums.SongListFragmentOption
import com.unex.musicgo.utils.Repository

class SongListFragmentViewModel(
    private val repository: Repository
) : ViewModel() {

    val toastLiveData = MutableLiveData<String>()
    val isLoading = MutableLiveData(false)

    /* Option */
    private var _option = MutableLiveData<String>()
    val option: LiveData<String> = _option

    /* Search */
    private var _query = MutableLiveData<String>()
    val query: LiveData<String> = _query

    /* Favorites */
    private var _stars = MutableLiveData<Int?>()
    private val stars: LiveData<Int?> = _stars

    /* PlayList */
    private var _playlist = MutableLiveData<PlayListWithSongs?>()
    private val playlist: LiveData<PlayListWithSongs?> = _playlist

    /* Recommendations */
    private var _genreSeed = MutableLiveData<String>()
    private val genreSeed: LiveData<String> = _genreSeed
    private var _artistSeed = MutableLiveData<String>()
    private val artistSeed: LiveData<String> = _artistSeed

    /* Songs data */
    private var _songs = MutableLiveData<List<Song>>()
    val songs: LiveData<List<Song>> = _songs

    /* Fetch trigger */
    private val _fetchTrigger = MediatorLiveData<Unit>()
    val canFetch: LiveData<Unit> = _fetchTrigger

    init {
        _fetchTrigger.addSource(_option) {
            checkToFetchData()
        }
        _fetchTrigger.addSource(_query) {
            checkToFetchData()
        }
        _fetchTrigger.addSource(_stars) {
            checkToFetchData()
        }
        _fetchTrigger.addSource(_genreSeed) {
            checkToFetchData()
        }
        _fetchTrigger.addSource(_artistSeed) {
            checkToFetchData()
        }
        _fetchTrigger.addSource(_playlist) {
            checkToFetchData()
        }
    }

    private fun checkToFetchData() {
        val optionIsNullOrEmpty = option.value.isNullOrEmpty()
        if (optionIsNullOrEmpty) return
        when (SongListFragmentOption.valueOf(option.value!!).name) {
            SongListFragmentOption.RECENT.name -> {
                _fetchTrigger.postValue(Unit)
            }
            SongListFragmentOption.SEARCH.name -> {
                val queryIsNullOrEmpty = query.value.isNullOrEmpty()
                if (!queryIsNullOrEmpty) {
                    _fetchTrigger.postValue(Unit)
                }
            }
            SongListFragmentOption.FAVORITES.name -> {
                val starsNotDefined = stars.value == null
                if (!starsNotDefined) {
                    _fetchTrigger.postValue(Unit)
                }
            }
            SongListFragmentOption.RECOMMENDATION.name -> {
                _fetchTrigger.postValue(Unit)
            }
            SongListFragmentOption.PLAYLIST.name -> {
                _fetchTrigger.postValue(Unit)
            }
        }
    }

    fun setOption(option: SongListFragmentOption) {
        _option.postValue(option.name)
    }

    fun showTrash() = _option.value == SongListFragmentOption.PLAYLIST.name

    suspend fun fetch() {
        when (option.value) {
            SongListFragmentOption.RECENT.name -> fetchRecentSongs()
            SongListFragmentOption.SEARCH.name -> fetchSearch()
            SongListFragmentOption.FAVORITES.name -> fetchFavoriteSongs()
            SongListFragmentOption.RECOMMENDATION.name -> fetchRecommendations()
            SongListFragmentOption.PLAYLIST.name -> fetchPlayList()
            else -> throw Exception("Option not found")
        }
    }

    private suspend fun fetchRecentSongs() {
        val database = repository.database
        val songs = database.playListDao().getRecentPlayList().songs
        if(songs.isEmpty()) throw Exception("Recent songs not found")
        _songs.postValue(songs)
    }

    private suspend fun fetchFavoriteSongs() {
        Log.d(TAG, "fetchFavoriteSongs: ${stars.value}")
        val database = repository.database
        val favoritePlayList = database.playListDao().getFavoritesPlayList()
        var songs = favoritePlayList.songs
        if(songs.isEmpty()) throw Exception("Favorites not found")
        if (stars.value != 0) {
            songs = songs.filter {
                it.rating == stars.value
            }
        }
        _songs.postValue(songs)
    }

    private suspend fun fetchRecommendations() {
        var listOfSongs: List<Song>
        val service = getNetworkService()
        val authToken = getAuthToken()
        if (artistSeed.value.isNullOrEmpty() && genreSeed.value.isNullOrEmpty()) {
            val recommendations = service.getRecommendations(authToken, limit = 50)
            val tracks = recommendations.tracks
            listOfSongs = tracks.map(Items::toSong)
            listOfSongs = listOfSongs.filter { song -> song.previewUrl != null }
            _songs.postValue(listOfSongs)
        } else {
            val recommendations = service.getRecommendations(
                authToken,
                limit = 50,
                seedTracks = "",
                seedArtists = artistSeed.value ?: "",
                seedGenres = genreSeed.value ?: ""
            )
            val tracks = recommendations.tracks
            listOfSongs = tracks.map(Items::toSong)
            listOfSongs = listOfSongs.filter { song -> song.previewUrl != null }
            _songs.postValue(listOfSongs)
        }
    }

    private suspend fun fetchSearch() {
        Log.d("SongListFragmentViewModel", "fetchSearch: ${query.value}")
        if(query.value.isNullOrEmpty()) throw Exception("Query cannot be null")
        val database = repository.database
        val authToken = getAuthToken()
        val networkSongs = getNetworkService().search(authToken, query.value!!)
        val items = networkSongs.tracks?.items
        val fetchedSongs = items?.map(Items::toSong)
            ?: throw Exception("Unable to fetch data from API", null)
        val songs = fetchedSongs.filter { song -> song.previewUrl != null }
        database.songsDao().insertAll(songs)
        _songs.postValue(songs)
    }

    private fun fetchPlayList() {
        val songs = playlist.value?.songs ?: throw Exception("Playlist not found")
        _songs.postValue(songs)
    }

    fun setRecommendationSeeds(genre: String?, artist: String?) {
        _genreSeed.postValue(genre ?: "")
        _artistSeed.postValue(artist ?: "")
    }

    fun setQuery(query: String?) {
        query?.let {
            Log.d("SongListFragmentViewModel", "setQuery: $it")
            _query.postValue(it)
        }
    }

    fun setPlayList(playList: PlayListWithSongs?) {
        if(playList == null) return
        if(playList == _playlist.value) return
        _playlist.postValue(playList)
    }

    fun setStars(stars: Int?) {
        if (stars == null) return
        if (stars == _stars.value) return
        _stars.postValue(stars)
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
        const val TAG = "SongListFragmentViewModel"

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                val application = checkNotNull(extras[APPLICATION_KEY])
                val viewModel = SongListFragmentViewModel(
                    (application as MusicGoApplication).appContainer.repository,
                )
                return viewModel as T
            }
        }
    }
}