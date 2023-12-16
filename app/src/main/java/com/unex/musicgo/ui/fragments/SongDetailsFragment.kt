package com.unex.musicgo.ui.fragments

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.unex.musicgo.R
import com.unex.musicgo.api.getAuthToken
import com.unex.musicgo.api.getNetworkService
import com.unex.musicgo.data.toSong
import com.unex.musicgo.database.MusicGoDatabase
import com.unex.musicgo.databinding.SongDetailsFragmentBinding
import com.unex.musicgo.models.Comment
import com.unex.musicgo.models.PlayListSongCrossRef
import com.unex.musicgo.models.PlayListWithSongs
import com.unex.musicgo.models.Song
import com.unex.musicgo.ui.vms.SongDetailsFragmentViewModel
import kotlinx.coroutines.launch

class SongDetailsFragment : Fragment() {
    companion object {
        const val TAG = "SongDetailsFragment"
        private const val EXTRA_TRACK_ID = "track_id"

        @JvmStatic
        fun newInstance(song: Song) = SongDetailsFragment().apply {
            arguments = Bundle().apply {
                Log.d(TAG, "newInstance song: $song")
                putString(EXTRA_TRACK_ID, song.id)
            }
        }
    }

    private var _binding: SongDetailsFragmentBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SongDetailsFragmentViewModel by lazy {
        ViewModelProvider(
            this,
            SongDetailsFragmentViewModel.Factory
        )[SongDetailsFragmentViewModel::class.java]
    }

    private var db: MusicGoDatabase? = null

    private var timeListening: Long = 0 // In milliseconds

    private var mediaPlayer: MediaPlayer? = null
    private var progressBarChecker: Runnable? = object : Runnable {
        override fun run() {
            Log.d(TAG, "songProgressChecker")
            mediaPlayer?.let {
                if (it.isPlaying) {
                    val progress = it.currentPosition
                    Log.d(TAG, "progress: $progress")
                    binding.songProgress.progress = progress
                    binding.songProgress.postDelayed(this, 1000)

                    // Update the time listening
                    timeListening += 1000
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate SongDetailsFragment")

        arguments?.let {
            val trackId = it.getString(EXTRA_TRACK_ID)
            viewModel.setTrackId(trackId!!)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView SongDetailsFragment")
        _binding = SongDetailsFragmentBinding.inflate(inflater, container, false)
        // restore(savedInstanceState)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpViewModel()
    }

    private fun setUpViewModel() {
        viewModel.toastLiveData.observe(viewLifecycleOwner) {
            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
        }
        viewModel.song.observe(viewLifecycleOwner) {
            Log.d(TAG, "song: $it")
            bindSong(it)
        }
        viewModel.isPlaying.observe(viewLifecycleOwner) { isPlaying ->
            Log.d(TAG, "isPlaying: $isPlaying")
            if (isPlaying) {
                // Play the song
                playSong()
            } else {
                // Pause the song
            }
        }
    }

    /*
    private fun restore(savedInstanceState: Bundle?) {
        var trackId: String? = null
        if(savedInstanceState != null) {
            val song = savedInstanceState.getSerializable("song") as Song?
            trackId = savedInstanceState.getString("trackId")
            val isPlaying = savedInstanceState.getBoolean("isPlaying")
            val currentPosition = savedInstanceState.getInt("currentPosition")
            // restoreState(isPlaying, currentPosition)
        } else if (trackId == null) {
            Toast.makeText(requireContext(), "No track id", Toast.LENGTH_SHORT).show()
        } else {
            bindSong()
        }
    }
    */

    /*
    private fun restoreState(isPlaying: Boolean, currentPosition: Int) {
        Log.d(TAG, "Restoring state")
        bindSong()
        try {
            lifecycleScope.launch {
                Log.d(TAG, "isPlaying: $isPlaying")
                Log.d(TAG, "currentPosition: $currentPosition")
                if (isPlaying) {
                    mediaPlayer = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        setDataSource(song?.previewUrl)
                        setOnPreparedListener { mp ->
                            mp.seekTo(currentPosition)
                            initProgressBar()
                            playSong()
                        }
                        prepareAsync()
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error: $e")
            Toast.makeText(requireContext(), "Error while fetching song", Toast.LENGTH_SHORT)
                .show()
        }
    }
    */

    private fun destroyMediaPlayer() {
        showPlayButton()
        binding.songProgress.progress = 0
        binding.songProgress.removeCallbacks(progressBarChecker)
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        // Update the statistics of the song
        lifecycleScope.launch {
            val song = viewModel.song.value
            song?.let {
                Log.d(TAG, "timeListening: $timeListening")
                db?.statisticsDao()?.registerPlay(
                    it.id,
                    it.title,
                    it.artist,
                    timeListening
                )
            }
        }
    }

    private fun initProgressBar() {
        try {
            Log.d(TAG, "initProgressChecker")
            with(binding) {
                songProgress.max = mediaPlayer?.duration ?: 0
                songProgress.postDelayed(progressBarChecker, 1000)
                mediaPlayer?.setOnCompletionListener {
                    destroyMediaPlayer()
                }
                // When the seek bar is changed, seek the media player
                songProgress.setOnSeekBarChangeListener(object :
                    android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: android.widget.SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        if (fromUser) mediaPlayer?.seekTo(progress)
                    }

                    override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
                })
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error: $e")
        }
    }

    private fun playSong() {
        // Check if the media player exists
        try {
            Log.d(TAG, "playSong")
            if (mediaPlayer == null) {
                Log.d(TAG, "mediaPlayer is null")
                // If not exists, create a new one
                lifecycleScope.launch {
                    val song = viewModel.song.value
                    mediaPlayer = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        setDataSource(song?.previewUrl)
                        prepare()
                    }
                    // Save the song as a recent song
                    val db = MusicGoDatabase.getInstance(requireContext())
                    val recentPlayList = db?.playListDao()?.getRecentPlayList()
                    recentPlayList?.let {
                        db.songsDao().insert(song!!)
                        val crossRef = PlayListSongCrossRef(it.playlist.id, song!!.id)
                        db.playListSongCrossRefDao().insert(crossRef)
                    }
                }
            }
            // Resume the song
            togglePlayAndPause()
        } catch (e: Exception) {
            Log.d(TAG, "Error: $e")
            Toast.makeText(
                requireContext(),
                "Error while playing song",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun resumeSong() {
        try {
            Log.d(TAG, "resumeSong")
            mediaPlayer?.start()
            initProgressBar()
        } catch (e: Exception) {
            Log.d(TAG, "Error: $e")
            Toast.makeText(
                requireContext(),
                "Error while resuming song",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showPlayButton() {
        binding.playButton.visibility = View.VISIBLE
        binding.pauseButton.visibility = View.GONE
    }

    private fun showPauseButton() {
        binding.playButton.visibility = View.GONE
        binding.pauseButton.visibility = View.VISIBLE
    }

    private fun togglePlayAndPause() {
        Log.d(TAG, "togglePlayAndPause")
        with(binding) {
            if (mediaPlayer?.isPlaying == true) {
                // Pause the song and show the play button
                mediaPlayer?.pause()
                showPlayButton()
            } else {
                // Resume the song and show the pause button
                resumeSong()
                showPauseButton()
            }
        }
    }

    private fun fillStars(stars: Int) {
        with(binding) {
            when (stars) {
                1 -> {
                    star1.setImageResource(R.drawable.ic_star_filled)
                    star2.setImageResource(R.drawable.ic_star)
                    star3.setImageResource(R.drawable.ic_star)
                    star4.setImageResource(R.drawable.ic_star)
                    star5.setImageResource(R.drawable.ic_star)
                }

                2 -> {
                    star1.setImageResource(R.drawable.ic_star_filled)
                    star2.setImageResource(R.drawable.ic_star_filled)
                    star3.setImageResource(R.drawable.ic_star)
                    star4.setImageResource(R.drawable.ic_star)
                    star5.setImageResource(R.drawable.ic_star)
                }

                3 -> {
                    star1.setImageResource(R.drawable.ic_star_filled)
                    star2.setImageResource(R.drawable.ic_star_filled)
                    star3.setImageResource(R.drawable.ic_star_filled)
                    star4.setImageResource(R.drawable.ic_star)
                    star5.setImageResource(R.drawable.ic_star)
                }

                4 -> {
                    star1.setImageResource(R.drawable.ic_star_filled)
                    star2.setImageResource(R.drawable.ic_star_filled)
                    star3.setImageResource(R.drawable.ic_star_filled)
                    star4.setImageResource(R.drawable.ic_star_filled)
                    star5.setImageResource(R.drawable.ic_star)
                }

                5 -> {
                    star1.setImageResource(R.drawable.ic_star_filled)
                    star2.setImageResource(R.drawable.ic_star_filled)
                    star3.setImageResource(R.drawable.ic_star_filled)
                    star4.setImageResource(R.drawable.ic_star_filled)
                    star5.setImageResource(R.drawable.ic_star_filled)
                }
            }
        }
    }

    private fun bindSong(song: Song) {
        with(binding) {
            songInfoNames.text = song.title
            songInfoArtistMain.text = song.artist
            songInfoGenreMain.text = song.genres ?: "Unknown"
            song.isRated.let {
                if (it) {
                    fillStars(song.rating)
                }
            }
            /** Use glide to load the image */
            Glide.with(requireContext())
                .load(song?.coverPath)
                .into(songIcon)
            /** Add click listener to stars */
            star1.setOnClickListener {
                fillStars(1)
                viewModel.updateSongRating(1)
            }
            star2.setOnClickListener {
                fillStars(2)
                viewModel.updateSongRating(2)
            }
            star3.setOnClickListener {
                fillStars(3)
                viewModel.updateSongRating(3)
            }
            star4.setOnClickListener {
                fillStars(4)
                viewModel.updateSongRating(4)
            }
            star5.setOnClickListener {
                fillStars(5)
                viewModel.updateSongRating(5)
            }
            playButton.setOnClickListener {
                viewModel.play(requireContext())
            }
            pauseButton.setOnClickListener {
                viewModel.pause()
            }
            /** Pause button can be used to pause the song */
            pauseButton.setOnClickListener {
                togglePlayAndPause()
            }
            /** Stop button can be used to stop the song */
            stopButton.setOnClickListener {
                if (mediaPlayer != null) {
                    destroyMediaPlayer()
                }
            }
            /** Bind the comments section with the CommentListFragment */
            launchCommentsFragment()
            /** New comment input */
            val handler = Handler(Looper.getMainLooper())
            val delay = 3000L // 3 seconds
            newCommentField.addTextChangedListener(object : TextWatcher {
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    // Stop the previous operation of saving the text
                    handler.removeCallbacksAndMessages(null)

                    // Save the text after delay
                    handler.postDelayed({
                        postComment()
                    }, delay)
                }

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
    }

    private fun SongDetailsFragmentBinding.postComment() {
        Log.d(TAG, "postComment")
        val comment: String = newCommentField.text.toString()
        if (comment.isEmpty()) return
        // Save comment on the firebase database
        val email = Firebase.auth.currentUser?.email
        if (email == null) {
            Toast.makeText(
                requireContext(),
                "You must be logged in to comment",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        lifecycleScope.launch {
            val username = db?.userDao()?.getUserByEmail(email)?.username
            username?.let {
                val commentRef = Firebase.firestore.collection("comments").document()
                val commentObj = viewModel.createComment(it, email, comment)
                commentRef.set(commentObj)
                Toast.makeText(
                    requireContext(),
                    "Comment posted",
                    Toast.LENGTH_SHORT
                ).show()
                newCommentField.clearFocus()
                newCommentField.text.clear()
                // Refresh the comments list
                launchCommentsFragment()
            }
        }
    }

    private fun launchCommentsFragment() {
        val song = viewModel.song.value
        val commentListFragment = CommentListFragment.newInstance(song!!)
        childFragmentManager.beginTransaction()
            .replace(binding.songInfoComments.id, commentListFragment)
            .commit()
    }

    /*
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.d(TAG, "Saving state")
        // Save the state of the media player
        mediaPlayer?.let {
            if (it.isPlaying) {
                Log.d(TAG, "isPlaying: true")
                outState.putBoolean("isPlaying", true)
                Log.d(TAG, "currentPosition: ${it.currentPosition}")
                outState.putInt("currentPosition", it.currentPosition)
            }
        }
        // Save the state of the song
        outState.putString("trackId", trackId)
        outState.putSerializable("song", song)
    }
    */

    override fun onDestroyView() {
        super.onDestroyView()
        destroyMediaPlayer()
        _binding = null // avoid memory leaks
    }

}