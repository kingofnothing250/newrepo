package com.nyxora.music

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import com.google.common.util.concurrent.MoreExecutors
import com.nyxora.music.network.InnertubeRepository
import com.nyxora.music.network.SongItem
import com.nyxora.music.service.NyxoraPlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF121212))) {
                NyxoraApp()
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NyxoraApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    var searchQuery by remember { mutableStateOf("Lo-Fi Study") }
    var searchResults by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var currentSong by remember { mutableStateOf<SongItem?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var subBassLevel by remember { mutableStateOf(0f) }

    // Initialize ExoPlayer MediaController
    LaunchedEffect(Unit) {
        val sessionToken = SessionToken(context, ComponentName(context, NyxoraPlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            mediaController = controllerFuture.get()
            mediaController?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
            })
        }, MoreExecutors.directExecutor())
        
        // Initial Fetch
        isLoading = true
        searchResults = InnertubeRepository.searchSongs(searchQuery)
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NYXORA ∞", fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0A))
            )
        },
        bottomBar = {
            if (currentSong != null) {
                BottomPlayer(
                    song = currentSong!!,
                    isPlaying = isPlaying,
                    subBassLevel = subBassLevel,
                    onPlayPause = {
                        if (isPlaying) mediaController?.pause() else mediaController?.play()
                    },
                    onSubBassChange = {
                        subBassLevel = it
                        NyxoraPlaybackService.audioEngine?.setSubBassBoost(it.toInt())
                    }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(Color(0xFF121212))) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Search Songs, Artists...", color = Color.Gray) },
                trailingIcon = {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            isLoading = true
                            searchResults = InnertubeRepository.searchSongs(searchQuery)
                            isLoading = false
                        }
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF1DB954),
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF1DB954))
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(searchResults) { song ->
                        SongRow(song) {
                            coroutineScope.launch {
                                currentSong = song
                                // 1. Resolve 160kbps Opus Audio Stream
                                val stream = InnertubeRepository.resolveStreamUrl(song.videoId)
                                if (stream != null) {
                                    // 2. Map metadata to Player
                                    val metadata = MediaMetadata.Builder()
                                        .setTitle(song.title)
                                        .setArtist(song.artist)
                                        .setArtworkUri(android.net.Uri.parse(song.artworkUrl))
                                        .build()
                                    val mediaItem = MediaItem.Builder()
                                        .setMediaId(song.videoId)
                                        .setUri(stream.first)
                                        .setMediaMetadata(metadata)
                                        .build()
                                    
                                    // 3. Start Playback
                                    withContext(Dispatchers.Main) {
                                        mediaController?.setMediaItem(mediaItem)
                                        mediaController?.prepare()
                                        mediaController?.play()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SongRow(song: SongItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.artworkUrl,
            contentDescription = "Cover",
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, fontSize = 16.sp)
            Text(song.artist, color = Color.LightGray, fontSize = 13.sp, maxLines = 1)
            Text(song.codecLabel, color = Color(0xFF1DB954), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
fun BottomPlayer(
    song: SongItem,
    isPlaying: Boolean,
    subBassLevel: Float,
    onPlayPause: () -> Unit,
    onSubBassChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = song.artworkUrl,
                contentDescription = null,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(song.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                Text(song.artist, color = Color.Gray, fontSize = 13.sp, maxLines = 1)
            }
            IconButton(onClick = onPlayPause) {
                Icon(
                    if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                    contentDescription = "Play/Pause",
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.SpeakerGroup, contentDescription = "Sub-Bass", tint = Color.Gray, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Hardware DSP Sub-Bass", color = Color.Gray, fontSize = 12.sp)
            Slider(
                value = subBassLevel,
                onValueChange = onSubBassChange,
                valueRange = 0f..100f,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF1DB954),
                    activeTrackColor = Color(0xFF1DB954),
                    inactiveTrackColor = Color.DarkGray
                )
            )
            Text("${subBassLevel.toInt()}%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
