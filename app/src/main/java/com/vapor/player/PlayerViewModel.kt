package com.vapor.player

import android.app.Application
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("vapor", Context.MODE_PRIVATE)

    var tracks by mutableStateOf<List<Track>>(emptyList()); private set
    var currentId by mutableStateOf<Long?>(null); private set
    var isPlaying by mutableStateOf(false); private set
    var position by mutableLongStateOf(0L); private set
    var duration by mutableLongStateOf(0L); private set
    var shuffle by mutableStateOf(false); private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF); private set
    var vapor by mutableStateOf(false); private set
    var sleepMinutes by mutableIntStateOf(0); private set
    var favorites by mutableStateOf(
        prefs.getStringSet("fav", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
    ); private set

    private var controller: MediaController? = null
    private var queueReady = false
    private var sleepJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync()
    }

    init {
        val app2 = getApplication<Application>()
        val token = SessionToken(app2, ComponentName(app2, PlaybackService::class.java))
        val future = MediaController.Builder(app2, token).buildAsync()
        future.addListener({
            controller = future.get().also { it.addListener(listener) }
            sync()
        }, ContextCompat.getMainExecutor(app2))

        viewModelScope.launch {
            while (true) {
                controller?.let { if (it.isPlaying) position = it.currentPosition }
                delay(250)
            }
        }
    }

    private fun sync() {
        val c = controller ?: return
        isPlaying = c.isPlaying
        currentId = c.currentMediaItem?.mediaId?.toLongOrNull()
        duration = c.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        shuffle = c.shuffleModeEnabled
        repeatMode = c.repeatMode
        position = c.currentPosition
    }

    // ---------- библиотека ----------
    fun load() {
        viewModelScope.launch {
            tracks = withContext(Dispatchers.IO) { queryTracks() }
            queueReady = false
        }
    }

    private fun queryTracks(): List<Track> {
        val list = mutableListOf<Track>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION
        )
        val sel = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 30000"
        getApplication<Application>().contentResolver.query(
            collection, proj, sel, null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val artist = c.getString(iArtist)?.takeIf { it != "<unknown>" } ?: "Unknown Artist"
                list += Track(
                    id, c.getString(iTitle) ?: "Untitled", artist, c.getLong(iDur),
                    ContentUris.withAppendedId(collection, id)
                )
            }
        }
        return list
    }

    private fun toItem(t: Track) = MediaItem.Builder()
        .setMediaId(t.id.toString())
        .setUri(t.uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).build())
        .build()

    // ---------- управление ----------
    fun play(track: Track) {
        val c = controller ?: return
        if (!queueReady) {
            c.setMediaItems(tracks.map(::toItem))
            queueReady = true
        }
        val idx = tracks.indexOfFirst { it.id == track.id }
        if (idx < 0) return
        c.seekTo(idx, 0)
        c.prepare()
        c.play()
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.currentMediaItem == null) tracks.firstOrNull()?.let { play(it) }
        else if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNext() }
    fun prev() { controller?.seekToPrevious() }
    fun seek(ms: Long) { controller?.seekTo(ms); position = ms }

    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    /** Slowed-режим: скорость и питч 0.8x — тот самый вейпорвейв-звук. */
    fun toggleVapor() {
        vapor = !vapor
        controller?.setPlaybackParameters(
            if (vapor) PlaybackParameters(0.8f, 0.8f) else PlaybackParameters.DEFAULT
        )
    }

    fun cycleSleep() {
        val steps = listOf(0, 15, 30, 60)
        sleepMinutes = steps[(steps.indexOf(sleepMinutes) + 1) % steps.size]
        sleepJob?.cancel()
        if (sleepMinutes > 0) {
            val ms = sleepMinutes * 60_000L
            sleepJob = viewModelScope.launch {
                delay(ms)
                controller?.pause()
                sleepMinutes = 0
            }
        }
    }

    fun toggleFav(id: Long) {
        favorites = if (id in favorites) favorites - id else favorites + id
        prefs.edit().putStringSet("fav", favorites.map { it.toString() }.toSet()).apply()
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        super.onCleared()
    }
}
