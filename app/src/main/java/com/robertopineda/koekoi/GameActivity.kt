package com.robertopineda.koekoi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class GameActivity : ComponentActivity() {
    // Use Japanese script for both display and comparison
    private val sentences = listOf(
        "こんにちは" to "こんにちは",
        "ありがとう" to "ありがとう"
    )
    private var currentIndex = 0

    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val spokenText = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
        val expected = sentences[currentIndex].second
        val isCorrect = spokenText?.equals(expected, ignoreCase = true) == true
        setContent {
            GameScreen(
                sentences = sentences,
                currentIndex = currentIndex,
                spokenText = spokenText ?: "",
                isCorrect = isCorrect,
                onSpeak = { startListening() },
                onSkip = { currentIndex = (currentIndex + 1) % sentences.size; startListening() },
                onQuit = { finish() }
            )
        }
        if (isCorrect) currentIndex = (currentIndex + 1) % sentences.size // Loop back
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GameScreen(
                sentences = sentences,
                currentIndex = currentIndex,
                spokenText = "",
                isCorrect = null,
                onSpeak = { startListening() },
                onSkip = { currentIndex = (currentIndex + 1) % sentences.size; startListening() },
                onQuit = { finish() }
            )
        }
        requestAudioPermission()
    }

    private fun requestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say: ${sentences[currentIndex].first}")
        }
        speechLauncher.launch(intent)
    }
}

@Composable
fun GameScreen(
    sentences: List<Pair<String, String>>,
    currentIndex: Int,
    spokenText: String,
    isCorrect: Boolean?,
    onSpeak: () -> Unit,
    onSkip: () -> Unit,
    onQuit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Say: ${sentences[currentIndex].first}")
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "You said: $spokenText")
        Spacer(modifier = Modifier.height(16.dp))
        isCorrect?.let {
            Text(
                text = if (it) "Correct!" else "Incorrect!",
                modifier = Modifier.background(if (it) Color.Green else Color(0xFFEF5350)),
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onSpeak) {
            Text("Speak")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onSkip) {
            Text("Skip")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onQuit) {
            Text("Quit")
        }
    }
}