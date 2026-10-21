package dev.gramo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.audio.DefaultLightAudio
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightFullscreenModal
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightProgressBar
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

@InitialScreen
class LibraryScreen(private val sealedActivity: SealedLightActivity) :
    LightScreen<Unit, GramoViewModel>(sealedActivity) {

    override val viewModelClass: Class<GramoViewModel>
        get() = GramoViewModel::class.java

    override fun createViewModel(): GramoViewModel =
        GramoViewModel(DefaultLightAudio(sealedActivity), lightContext.filesDir)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.uiState.collectAsState()
        val isPlaying by viewModel.isPlaying.collectAsState()
        val positionMs by viewModel.positionMs.collectAsState()
        val durationMs by viewModel.durationMs.collectAsState()

        LightTheme(colors = themeColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    LightTopBar(
                        center = LightTopBarCenter.Text("GRAMO"),
                        rightButton = LightBarButton.Text(
                            text = "${state.tracks.size} TRACKS",
                            onClick = null,
                        ),
                        modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
                    )

                    state.currentTrack?.let { track ->
                        NowPlaying(
                            track = track,
                            isPlaying = isPlaying,
                            positionMs = positionMs,
                            durationMs = durationMs,
                            onToggle = viewModel::togglePlayPause,
                            onPrevious = viewModel::skipToPrevious,
                            onNext = viewModel::skipToNext,
                        )
                    }

                    if (state.tracks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 2f.gridUnitsAsDp()),
                            contentAlignment = Alignment.Center,
                        ) {
                            LightText(
                                text = "No music yet.\n\nUse IMPORT to download your " +
                                    "Google Takeout archive or upload zips through the Tool Manager.",
                                variant = LightTextVariant.Copy,
                                align = TextAlign.Center,
                                lighten = true,
                            )
                        }
                    } else {
                        LightScrollView(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(start = 1f.gridUnitsAsDp()),
                        ) {
                            state.tracks.forEachIndexed { index, track ->
                                val isCurrent = index == state.currentIndex
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .lightClickable { viewModel.playAt(index) }
                                        .padding(vertical = 0.5f.gridUnitsAsDp()),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (isCurrent) {
                                        LightIcon(
                                            icon = if (isPlaying) LightIcons.PLAY else LightIcons.PAUSE,
                                            size = 1.1f,
                                            modifier = Modifier.padding(end = 0.5f.gridUnitsAsDp()),
                                            contentDescription = "Now playing",
                                        )
                                    }
                                    LightText(
                                        text = track.title,
                                        variant = LightTextVariant.Copy,
                                        lighten = !isCurrent,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }

                    LightBottomBar(
                        items = listOf(
                            LightBarButton.LightIcon(
                                icon = LightIcons.ADD,
                                onClick = { navigateTo(screenFactory = { ImportScreen(it, viewModel) }) },
                                contentDescription = "Import music",
                            ),
                            LightBarButton.LightIcon(
                                icon = LightIcons.REFRESH,
                                onClick = viewModel::refresh,
                                contentDescription = "Rescan library",
                            ),
                            null,
                        ),
                    )
                }

                state.error?.let { message ->
                    LightFullscreenModal(
                        message = message,
                        onClose = viewModel::dismissError,
                    )
                }
            }
        }
    }
}

@Composable
private fun NowPlaying(
    track: Track,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val progress = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 1f.gridUnitsAsDp())
            .padding(bottom = 0.5f.gridUnitsAsDp()),
    ) {
        LightText(
            text = track.title,
            variant = LightTextVariant.Heading,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 0.3f.gridUnitsAsDp()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LightText(
                text = formatClock(positionMs),
                variant = LightTextVariant.Superfine,
                lighten = true,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 0.75f.gridUnitsAsDp()),
            ) {
                LightProgressBar(
                    colors = LightThemeTokens.colors,
                    progress = progress,
                )
            }
            LightText(
                text = formatClock(durationMs),
                variant = LightTextVariant.Superfine,
                lighten = true,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportIcon(
                icon = LightIcons.REWIND,
                contentDescription = "Previous track",
                onClick = onPrevious,
                modifier = Modifier.weight(1f),
            )
            TransportIcon(
                icon = if (isPlaying) LightIcons.PAUSE else LightIcons.PLAY,
                contentDescription = if (isPlaying) "Pause" else "Play",
                onClick = onToggle,
                modifier = Modifier.weight(1f),
            )
            TransportIcon(
                icon = LightIcons.FAST_FORWARD,
                contentDescription = "Next track",
                onClick = onNext,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TransportIcon(
    icon: com.thelightphone.sdk.ui.LightIconConfiguration,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        LightIcon(
            icon = icon,
            size = 2.2f,
            contentDescription = contentDescription,
            modifier = Modifier
                .lightClickable(onClick = onClick)
                .padding(vertical = 0.5f.gridUnitsAsDp()),
        )
    }
}

private fun formatClock(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
