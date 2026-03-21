package com.guruswarupa.launch.widgets

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.services.LaunchNotificationListenerService

class MediaControllerWidget(private val context: Context, private val rootView: View) {
    private val trackTitle: TextView = rootView.findViewById(R.id.media_track_title)
    private val artistName: TextView = rootView.findViewById(R.id.media_artist)
    private val playPauseBtn: ImageButton = rootView.findViewById(R.id.media_play_pause)
    private val prevBtn: ImageButton = rootView.findViewById(R.id.media_prev)
    private val nextBtn: ImageButton = rootView.findViewById(R.id.media_next)

    private var activeController: MediaController? = null
    private val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val componentName = ComponentName(context, LaunchNotificationListenerService::class.java)

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updatePlaybackState(state)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateMetadata(metadata)
        }
    }

    init {
        setupListeners()
        refreshController()
    }

    private fun setupListeners() {
        playPauseBtn.setOnClickListener {
            activeController?.playbackState?.let { state ->
                if (state.state == PlaybackState.STATE_PLAYING) {
                    activeController?.transportControls?.pause()
                } else {
                    activeController?.transportControls?.play()
                }
            }
        }
        prevBtn.setOnClickListener { activeController?.transportControls?.skipToPrevious() }
        nextBtn.setOnClickListener { activeController?.transportControls?.skipToNext() }
    }

    fun refreshController() {
        try {
            val controllers = mediaSessionManager.getActiveSessions(componentName)
            val newController = controllers.firstOrNull()
            
            if (newController != activeController) {
                activeController?.unregisterCallback(callback)
                activeController = newController
                activeController?.registerCallback(callback)
                
                updateMetadata(activeController?.metadata)
                updatePlaybackState(activeController?.playbackState)
            }
        } catch (e: SecurityException) {
            trackTitle.text = "Permission Required"
            artistName.text = "Enable Notification Access"
        }
    }

    private fun updateMetadata(metadata: MediaMetadata?) {
        if (metadata != null) {
            trackTitle.text = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown Track"
            artistName.text = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown Artist"
        } else {
            trackTitle.text = "Not Playing"
            artistName.text = ""
        }
    }

    private fun updatePlaybackState(state: PlaybackState?) {
        if (state != null && state.state == PlaybackState.STATE_PLAYING) {
            playPauseBtn.setImageResource(R.drawable.ic_pause)
        } else {
            playPauseBtn.setImageResource(R.drawable.ic_play)
        }
    }
}
