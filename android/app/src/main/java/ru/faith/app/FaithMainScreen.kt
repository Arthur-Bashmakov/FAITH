package ru.faith.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun FaithMainScreen(
    contentResolver: android.content.ContentResolver,
    language: String,
    sharedAudio: Uri? = null,
    onSharedAudioConsumed: () -> Unit = {},
) {
    MaterialTheme {
        val context = LocalContext.current
        val localizedContext = remember(language) { context.withLanguage(language) }
        var selectedFile by remember { mutableStateOf<Uri?>(null) }
        var selectedAudioInfo by remember { mutableStateOf<SelectedAudioInfo?>(null) }
        var result by remember { mutableStateOf<AnalysisResult?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        var validationIssue by remember { mutableStateOf(false) }
        var uploadProgress by remember { mutableStateOf(0f) }
        var page by remember { mutableStateOf(AudioPage.HOME) }
        var analysisRequestId by remember { mutableStateOf(0) }
        var recordingSeconds by remember { mutableStateOf(0) }
        var isPlaying by remember { mutableStateOf(false) }
        var playbackProgress by remember { mutableStateOf(0f) }
        var playbackError by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        val authStorage = remember { AuthStorage(context.applicationContext) }
        var accountEmail by remember { mutableStateOf(authStorage.email()) }
        var showAuth by remember { mutableStateOf(false) }
        var authBusy by remember { mutableStateOf(false) }
        var authError by remember { mutableStateOf<String?>(null) }
        var historyItems by remember { mutableStateOf<List<AnalysisHistoryItem>>(emptyList()) }
        var historyLoading by remember { mutableStateOf(false) }
        var historyError by remember { mutableStateOf<String?>(null) }
        var historyRequestId by remember { mutableStateOf(0) }
        val amplitudeSamples = remember { mutableStateListOf<Float>() }
        val scope = rememberCoroutineScope()
        val screenScrollState = rememberScrollState()
        val api = remember(authStorage) {
            ApiClient(tokenProvider = { authStorage.token() })
        }
        val recorder = remember { WavRecorder() }
        val audioPlayer = remember { AudioPlayer(context.applicationContext) }
        LaunchedEffect(sharedAudio) {
            sharedAudio?.let { uri ->
                selectedFile = uri
                selectedAudioInfo = readAudioInfo(contentResolver, uri)
                result = null
                error = null
                validationIssue = false
                page = AudioPage.PREVIEW
                onSharedAudioConsumed()
            }
        }
        DisposableEffect(recorder) {
            onDispose {
                recorder.stop()
                audioPlayer.stop()
            }
        }
        LaunchedEffect(selectedFile) {
            audioPlayer.stop { isPlaying = it }
            playbackProgress = 0f
            playbackError = false
        }
        LaunchedEffect(isPlaying) {
            if (isPlaying) {
                while (true) {
                    playbackProgress = audioPlayer.progress()
                    delay(100)
                }
            } else if (audioPlayer.progress() == 0f) {
                playbackProgress = 0f
            }
        }
        LaunchedEffect(page) {
            if (page == AudioPage.RECORDING) {
                recordingSeconds = 0
                while (true) {
                    delay(1_000)
                    recordingSeconds += 1
                }
            }
        }
        LaunchedEffect(analysisRequestId) {
            if (analysisRequestId == 0) return@LaunchedEffect
            val uri = selectedFile ?: run {
                page = AudioPage.HOME
                return@LaunchedEffect
            }
            error = null
            result = null
            validationIssue = false
            uploadProgress = 0f
            audioPlayer.stop { isPlaying = it }
            page = AudioPage.PROCESSING
            try {
                result = api.analyze(contentResolver, uri) { progress ->
                    scope.launch { uploadProgress = progress }
                }
            } catch (cancelled: CancellationException) {
                page = AudioPage.PREVIEW
                throw cancelled
            } catch (throwable: Throwable) {
                error = throwable.localizedUserMessage(localizedContext)
                validationIssue = throwable is ApiServerException && throwable.statusCode == 422
            } finally {
                if (analysisRequestId != 0) page = AudioPage.RESULT
            }
        }
        LaunchedEffect(historyRequestId) {
            if (historyRequestId == 0) return@LaunchedEffect
            historyLoading = true
            historyError = null
            try {
                historyItems = api.analysisHistory()
            } catch (throwable: Throwable) {
                historyError = throwable.localizedUserMessage(localizedContext)
            } finally {
                historyLoading = false
            }
        }
        LaunchedEffect(page) {
            screenScrollState.scrollTo(0)
            amplitudeSamples.clear()
            if (page == AudioPage.RECORDING) {
                repeat(32) { amplitudeSamples.add(0f) }
                while (true) {
                    amplitudeSamples.add(recorder.amplitude())
                    if (amplitudeSamples.size > 32) amplitudeSamples.removeAt(0)
                    delay(60)
                }
            }
        }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            selectedFile = uri
            selectedAudioInfo = uri?.let { readAudioInfo(contentResolver, it) }
            result = null
            error = null
            validationIssue = false
            if (uri != null) page = AudioPage.PREVIEW
        }
        val microphonePermission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted && recorder.start(context)) {
                error = null
                validationIssue = false
                recordingSeconds = 0
                page = AudioPage.RECORDING
            } else {
                error = localizedContext.getString(
                    if (granted) R.string.recording_start_error
                    else R.string.microphone_permission_denied,
                )
                validationIssue = false
                page = AudioPage.RESULT
            }
        }

        Surface(modifier = Modifier.fillMaxSize(), color = DarkBackground) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF17102E), DarkBackground))
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(screenScrollState)
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            localizedContext.getString(R.string.app_name),
                            color = Color.White,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Light,
                        )
                        Text(
                            localizedContext.getString(R.string.app_subtitle),
                            color = LightPurple.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                        )
                    }
                }
                Spacer(Modifier.height(30.dp))
                Icon(
                    Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = Purple,
                    modifier = Modifier.height(68.dp),
                )
                Spacer(Modifier.height(20.dp))
                when (page) {
                    AudioPage.HOME -> HomeScreen(
                        context = localizedContext,
                        onChooseAudio = { picker.launch(arrayOf("audio/*")) },
                        onRecordVoice = {
                            if (ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO,
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                if (recorder.start(context)) {
                                    error = null
                                    validationIssue = false
                                    recordingSeconds = 0
                                    page = AudioPage.RECORDING
                                } else {
                                    error = localizedContext.getString(R.string.recording_start_error)
                                    validationIssue = false
                                    page = AudioPage.RESULT
                                }
                            } else {
                                microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                    )
                    AudioPage.RECORDING -> RecordingScreen(
                        context = localizedContext,
                        recordingSeconds = recordingSeconds,
                        amplitudes = amplitudeSamples,
                        onStopRecording = {
                            scope.launch {
                                val file = withContext(Dispatchers.IO) { recorder.stop() }
                                if (file != null) {
                                    val uri = Uri.fromFile(file)
                                    selectedFile = uri
                                    selectedAudioInfo = readAudioInfo(contentResolver, uri)
                                    result = null
                                    error = null
                                    validationIssue = false
                                    page = AudioPage.PREVIEW
                                } else {
                                    error = localizedContext.getString(R.string.recording_start_error)
                                    validationIssue = false
                                    page = AudioPage.RESULT
                                }
                            }
                        },
                    )
                    AudioPage.PREVIEW -> PreviewScreen(
                        context = localizedContext,
                        audioInfo = selectedAudioInfo,
                        hasSelectedFile = selectedFile != null,
                        isPlaying = isPlaying,
                        playbackProgress = playbackProgress,
                        playbackError = playbackError,
                        onTogglePlayback = {
                            selectedFile?.let { uri ->
                                playbackError = !audioPlayer.toggle(uri) { isPlaying = it }
                            }
                        },
                        onAnalyze = { analysisRequestId += 1 },
                        onChooseAnother = { picker.launch(arrayOf("audio/*")) },
                        onClear = {
                            selectedFile = null
                            selectedAudioInfo = null
                            page = AudioPage.HOME
                        },
                    )
                    AudioPage.PROCESSING -> ProcessingScreen(
                        context = localizedContext,
                        uploadProgress = uploadProgress,
                        onCancel = { analysisRequestId = 0 },
                    )
                    AudioPage.RESULT -> AnalysisResultScreen(
                        context = localizedContext,
                        result = result,
                        audioInfo = selectedAudioInfo,
                        error = error,
                        validationIssue = validationIssue,
                        hasSelectedFile = selectedFile != null,
                        isPlaying = isPlaying,
                        playbackProgress = playbackProgress,
                        onTogglePlayback = {
                            selectedFile?.let { uri ->
                                playbackError = !audioPlayer.toggle(uri) { isPlaying = it }
                            }
                        },
                        onTryAgain = { analysisRequestId += 1 },
                        onBackHome = {
                            selectedFile = null
                            selectedAudioInfo = null
                            result = null
                            error = null
                            validationIssue = false
                            page = AudioPage.HOME
                        },
                    )
                    AudioPage.HISTORY -> {
                        AnalysisHistoryContent(
                            items = historyItems,
                            loading = historyLoading,
                            error = historyError,
                            context = localizedContext,
                            onRefresh = { historyRequestId += 1 },
                            onBack = { page = AudioPage.HOME },
                        )
                    }
                }
                }
                if (page == AudioPage.HOME) {
                    IconButton(
                        onClick = { showSettings = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = 12.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = localizedContext.getString(R.string.settings_title),
                            tint = LightPurple,
                        )
                    }
                }
                if (page == AudioPage.HISTORY) {
                    FaithVerticalScrollbar(
                        scrollState = screenScrollState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(vertical = 24.dp, horizontal = 4.dp),
                    )
                }
            }
        }
        if (showSettings) {
            FaithSettingsDialog(
                context = localizedContext,
                accountEmail = accountEmail,
                onOpenAccount = {
                    showSettings = false
                    authError = null
                    showAuth = true
                },
                onLogout = {
                    authStorage.clear()
                    accountEmail = null
                },
                onOpenHistory = {
                    showSettings = false
                    page = AudioPage.HISTORY
                    historyRequestId += 1
                },
                onSave = {
                    showSettings = false
                },
                onDismiss = { showSettings = false },
            )
        }
        if (showAuth) {
            FaithAuthDialog(
                context = localizedContext,
                busy = authBusy,
                error = authError,
                onSubmit = { email, password, register ->
                    scope.launch {
                        authBusy = true
                        authError = null
                        try {
                            val session = api.authenticate(email.trim(), password, register)
                            authStorage.save(session)
                            accountEmail = session.email
                            showAuth = false
                        } catch (throwable: Throwable) {
                            authError = throwable.localizedUserMessage(localizedContext)
                        } finally {
                            authBusy = false
                        }
                    }
                },
                onDismiss = { showAuth = false },
            )
        }
    }
}
