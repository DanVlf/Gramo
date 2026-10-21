package dev.gramo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTextField
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

/** Imports music: download a zip over HTTPS or import Tool Manager uploads. */
class ImportScreen(
    sealedActivity: SealedLightActivity,
    private val gramoViewModel: GramoViewModel,
) : SimpleLightScreen<Unit>(sealedActivity) {

    private enum class EditorKind {
        URL,
        COOKIE,
    }

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by gramoViewModel.uiState.collectAsState()

        var openEditor by remember { mutableStateOf<EditorKind?>(null) }
        var editorSession by remember { mutableIntStateOf(0) }

        val urlFieldState = rememberTextFieldState("")
        val cookieFieldState = rememberTextFieldState("")
        val keyboardOptionsFlow = rememberKeyboardOptions()

        fun submitDownload() {
            gramoViewModel.importFromUrl(
                urlFieldState.text.toString(),
                cookieFieldState.text.toString(),
            )
        }

        LaunchedEffect(Unit) {
            if (urlFieldState.text.isBlank() && state.tracks.isEmpty()) {
                editorSession++
                openEditor = EditorKind.URL
            }
        }

        LightTheme(colors = themeColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    LightTopBar(
                        leftButton = LightBarButton.LightIcon(
                            icon = LightIcons.BACK,
                            onClick = { goBack() },
                            contentDescription = "Back",
                        ),
                        center = LightTopBarCenter.Text("IMPORT"),
                        modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
                    )

                    LightScrollView(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 1f.gridUnitsAsDp()),
                    ) {
                        SectionLabel("FROM THE WEB (HTTPS)")
                        LightTextField(
                            label = "ARCHIVE URL",
                            value = urlFieldState.text.toString(),
                            placeholder = "https://...",
                            onClick = {
                                editorSession++
                                openEditor = EditorKind.URL
                            },
                            modifier = Modifier.padding(bottom = 0.75f.gridUnitsAsDp()),
                        )
                        LightTextField(
                            label = "COOKIE (OPTIONAL)",
                            value = cookieFieldState.text.toString(),
                            placeholder = "For Google Takeout links",
                            onClick = {
                                editorSession++
                                openEditor = EditorKind.COOKIE
                            },
                            modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                        )
                        LightText(
                            text = "DOWNLOAD AND IMPORT",
                            variant = LightTextVariant.Button,
                            modifier = Modifier
                                .fillMaxWidth()
                                .lightClickable { submitDownload() }
                                .padding(bottom = 1f.gridUnitsAsDp()),
                        )
                        state.importStatus?.let { status ->
                            LightText(
                                text = status.describe(),
                                variant = LightTextVariant.Detail,
                                lighten = true,
                                modifier = Modifier.padding(bottom = 1.5f.gridUnitsAsDp()),
                            )
                        }
                        LightText(
                            text = "Google Takeout: export \"YouTube and YouTube Music\" " +
                                "as .zip on a computer, then paste the download link. " +
                                "Takeout links can require a cookie header from your " +
                                "logged-in browser session — or re-host the zip anywhere " +
                                "plain HTTPS and use that link.",
                            variant = LightTextVariant.Superfine,
                            lighten = true,
                            modifier = Modifier.padding(bottom = 2f.gridUnitsAsDp()),
                        )

                        SectionLabel("FROM TOOL MANAGER")
                        LightText(
                            text = "IMPORT UPLOADED ARCHIVES",
                            variant = LightTextVariant.Button,
                            modifier = Modifier
                                .fillMaxWidth()
                                .lightClickable { gramoViewModel.importFromToolManager() }
                                .padding(bottom = 1f.gridUnitsAsDp()),
                        )
                        LightText(
                            text = "Upload .zip files to Gramo in the Tool Manager " +
                                "(Settings, same wifi network), then import them here.",
                            variant = LightTextVariant.Superfine,
                            lighten = true,
                        )
                    }

                    LightBottomBar(
                        items = listOf(
                            LightBarButton.LightIcon(
                                icon = LightIcons.BACK,
                                onClick = { goBack() },
                                contentDescription = "Back",
                            ),
                            null,
                            null,
                        ),
                    )
                }

                when (openEditor) {
                    EditorKind.URL -> {
                        LightTextInputEditor(
                            title = "Archive URL",
                            state = urlFieldState,
                            onSubmit = { openEditor = null },
                            onBack = { openEditor = null },
                            keyboardOptionsFlow = keyboardOptionsFlow,
                            submitIcon = LightIcons.DOWNLOAD_ARROW,
                            singleLine = true,
                            editorKey = editorSession,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    EditorKind.COOKIE -> {
                        LightTextInputEditor(
                            title = "Cookie Header",
                            state = cookieFieldState,
                            onSubmit = { openEditor = null },
                            onBack = { openEditor = null },
                            keyboardOptionsFlow = keyboardOptionsFlow,
                            singleLine = true,
                            editorKey = editorSession,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    null -> Unit
                }
            }
        }
    }
}

private fun ImportStatus.describe(): String = when (this) {
    is ImportStatus.Downloading -> {
        val total = totalBytes?.let { " of ${FileSizeFormat.format(it)}" } ?: ""
        "Downloading $label... ${FileSizeFormat.format(bytes)}$total"
    }

    is ImportStatus.Importing -> "Importing $archiveName... $imported tracks"

    is ImportStatus.Done ->
        "Import finished: $imported new tracks, $totalTracks in library."
}

@Composable
private fun SectionLabel(text: String) {
    LightText(
        text = text,
        variant = LightTextVariant.Superfine,
        lighten = true,
        modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
    )
}
