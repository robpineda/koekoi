package com.robertopineda.koekoi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

class GameActivity : ComponentActivity() {
    private val sentences = listOf(
        "Testing" to "Testing"
    )

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var mediaPlayer: MediaPlayer
    private var currentOnResult: ((String) -> Unit)? = null

    // Define the RecognitionListener as a separate property
    private val speechListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d("GameActivity", "Ready for speech")
            currentOnResult?.invoke("Listening...")
        }

        override fun onResults(results: Bundle?) {
            val spokenText = results?.getStringArrayList(RecognizerIntent.EXTRA_RESULTS)?.get(0) ?: ""
            Log.d("GameActivity", "Final result: $spokenText")
            currentOnResult?.invoke(spokenText)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partialText = partialResults?.getStringArrayList(RecognizerIntent.EXTRA_RESULTS)?.get(0) ?: ""
            Log.d("GameActivity", "Partial result: $partialText")
            if (partialText.isNotEmpty()) currentOnResult?.invoke(partialText)
        }

        override fun onError(error: Int) {
            Log.e("GameActivity", "Speech error: $error")
            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No match found. Try again."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input. Speak louder."
                else -> "Error $error"
            }
            Log.e("GameActivity", "Speech error details: $errorMessage")
            currentOnResult?.invoke(errorMessage)
        }

        override fun onBeginningOfSpeech() {
            Log.d("GameActivity", "Beginning of speech detected")
        }

        override fun onEndOfSpeech() {
            Log.d("GameActivity", "End of speech detected")
        }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if the device has the necessary services for speech recognition
        val pm = packageManager
        val activities = pm.queryIntentActivities(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0
        )
        if (activities.size == 0) {
            Log.e("GameActivity", "No activities found to handle speech recognition intent")
            Toast.makeText(this, "Speech recognition not supported on this device", Toast.LENGTH_LONG).show()
        } else {
            Log.d("GameActivity", "Found ${activities.size} activities to handle speech recognition")
        }

        // Initialize SpeechRecognizer and set the listener explicitly
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(speechListener) // Set before any commands

        mediaPlayer = MediaPlayer.create(this, R.raw.speak)

        setContent {
            GameScreen(
                sentences = sentences,
                onStartListening = { index, onResult -> startListening(index, onResult) },
                onQuit = { finish() }
            )
        }

        requestAudioPermission()

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e("GameActivity", "Speech recognition not available on this device")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        mediaPlayer.release()
    }

    private fun requestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    private fun startListening(currentIndex: Int, onResult: (String) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onResult("Permission denied")
            return
        }

        // Destroy and recreate the SpeechRecognizer
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(speechListener)

        // Update the callback for the existing listener
        currentOnResult = onResult

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // Try using just the language code without regional variants
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            Log.d("GameActivity", "Starting SpeechRecognizer for: ${sentences[currentIndex].first}")
            speechRecognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e("GameActivity", "Error starting speech recognizer", e)
            onResult("Error: ${e.message}")
        }
    }
}

@Composable
fun GameScreen(
    sentences: List<Pair<String, String>>,
    onStartListening: (Int, (String) -> Unit) -> Unit,
    onQuit: () -> Unit
) {
    var currentIndex by remember { mutableStateOf(0) }
    var spokenText by remember { mutableStateOf("") }
    var isCorrect by remember { mutableStateOf<Boolean?>(null) }
    var showResult by remember { mutableStateOf(false) }

    val backgroundColor by animateColorAsState(
        targetValue = when (isCorrect) {
            true -> Color(0xFFB2DFDB) // Light teal for correct
            false -> Color(0xFFFFCCCB) // Light red for incorrect
            null -> Color(0xFFE6F0FA) // Light blue for neutral
        },
        animationSpec = tween(durationMillis = 300)
    )

    LaunchedEffect(spokenText) {
        if (spokenText.isNotEmpty() && spokenText != "Listening...") {
            val expected = sentences[currentIndex].second
            isCorrect = spokenText.equals(expected, ignoreCase = true)
            Log.d("GameScreen", "Spoken: $spokenText, Expected: $expected, IsCorrect: $isCorrect")
            showResult = true
            if (isCorrect == true) {
                delay(1500)
                spokenText = ""
                isCorrect = null
                showResult = false
                currentIndex = (currentIndex + 1) % sentences.size
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(WindowInsets.systemBars.asPaddingValues())
        ) {
        Button(
            onClick = onQuit,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Text("Quit")
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Say: ${sentences[currentIndex].first}",
                fontSize = 24.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "You said: $spokenText",
                fontSize = 24.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(
                visible = showResult,
                enter = slideInVertically(
                    initialOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(durationMillis = 500)
                ),
                exit = fadeOut()
            ) {
                isCorrect?.let {
                    Text(
                        text = if (it) "Correct!" else "Incorrect!",
                        modifier = Modifier.background(if (it) Color.Green else Color(0xFFEF5350)),
                        color = Color.White,
                        fontSize = 30.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        val context = LocalContext.current
        val mediaPlayer = remember { MediaPlayer.create(context, R.raw.speak) }
        IconButton(
            onClick = {
                mediaPlayer.start()
                onStartListening(currentIndex) { result ->
                    spokenText = result
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .size(56.dp)
                .background(Color.Gray, shape = CircleShape)
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = "Speak",
                tint = Color.White
            )
        }

        Button(
            onClick = {
                val newIndex = (currentIndex + 1) % sentences.size
                currentIndex = newIndex
                mediaPlayer.start()
                onStartListening(newIndex) { result ->
                    spokenText = result
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Text("Skip")
        }
    }
}