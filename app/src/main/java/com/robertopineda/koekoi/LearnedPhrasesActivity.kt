package com.robertopineda.koekoi

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class LearnedPhrasesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LearnedPhrasesScreen(
                onBack = { finish() },
                onPhraseSelected = { phrase, language ->
                    val intent = Intent(this, GameActivity::class.java).apply {
                        putExtra("LANGUAGE", language)
                        putExtra("DIFFICULTY", "Learned")
                        putExtra("PHRASE", Gson().toJson(phrase))
                    }
                    startActivity(intent)
                }
            )
        }
    }
}

@Composable
fun LearnedPhrasesScreen(
    onBack: () -> Unit,
    onPhraseSelected: (GameActivity.Phrase, String) -> Unit
) {
    val context = LocalContext.current
    val languages = listOf("Japanese", "Korean", "Vietnamese", "Spanish")
    val gson = Gson()

    val learnedByLanguage = remember { mutableStateOf(loadLearned(context, languages, gson)) }

    fun refreshLearned() {
        learnedByLanguage.value = loadLearned(context, languages, gson)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF007893)) // Teal background
            .padding(WindowInsets.systemBars.asPaddingValues())
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Review Learned Phrases",
                fontSize = 32.sp,
                color = Color(0xFFE0F7FA), // Light cyan
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn {
                learnedByLanguage.value.forEach { (language, phrases) ->
                    item {
                        Text(
                            text = language,
                            fontSize = 20.sp,
                            color = Color(0xFFFFB300), // Amber accent
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    items(phrases, key = { it.spoken + it.english }) { phrase ->
                        var isLearned by remember(phrase) {
                            mutableStateOf(
                                isPhraseLearned(
                                    phrase,
                                    language,
                                    context
                                )
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPhraseSelected(phrase, language) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = phrase.spoken,
                                    fontSize = 18.sp,
                                    color = Color(0xFFE0F7FA) // Light cyan
                                )
                                Text(
                                    text = phrase.english,
                                    fontSize = 14.sp,
                                    color = Color(0xFF90A4AE) // Blue-grey
                                )
                            }
                            IconButton(
                                onClick = {
                                    removeLearnedPhrase(phrase, language, context)
                                    isLearned = false
                                    refreshLearned()
                                    Toast.makeText(
                                        context,
                                        "Removed from learned phrases",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lightbulb,
                                    contentDescription = "Unlearn",
                                    tint = if (isLearned) Color(0xFFFFD700) else Color(0xFFE0F7FA), // Keep yellow when learned
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFF8F00), // Deep amber
                contentColor = Color(0xFFE0F7FA) // Light cyan
            )
        ) {
            Text("Back")
        }
    }
}

private fun loadLearned(
    context: android.content.Context,
    languages: List<String>,
    gson: Gson
): List<Pair<String, List<GameActivity.Phrase>>> {
    return languages.map { language ->
        val prefs =
            context.getSharedPreferences("LearnedPrefs", android.content.Context.MODE_PRIVATE)
        val learnedJson = prefs.getString("learned_$language", "[]")
        val type = object : TypeToken<List<GameActivity.Phrase>>() {}.type
        val phrases: List<GameActivity.Phrase> = gson.fromJson(learnedJson, type) ?: emptyList()
        language to phrases
    }.filter { it.second.isNotEmpty() }
}

private fun removeLearnedPhrase(
    phrase: GameActivity.Phrase,
    language: String,
    context: android.content.Context
) {
    val prefs = context.getSharedPreferences("LearnedPrefs", android.content.Context.MODE_PRIVATE)
    val gson = Gson()
    val learnedJson = prefs.getString("learned_$language", "[]")
    val type = object : TypeToken<MutableList<GameActivity.Phrase>>() {}.type
    val learned: MutableList<GameActivity.Phrase> =
        gson.fromJson(learnedJson, type) ?: mutableListOf()
    learned.remove(phrase)
    prefs.edit().putString("learned_$language", gson.toJson(learned)).apply()
}

private fun isPhraseLearned(
    phrase: GameActivity.Phrase,
    language: String,
    context: android.content.Context
): Boolean {
    val prefs = context.getSharedPreferences("LearnedPrefs", android.content.Context.MODE_PRIVATE)
    val gson = Gson()
    val learnedJson = prefs.getString("learned_$language", "[]")
    val type = object : TypeToken<List<GameActivity.Phrase>>() {}.type
    val learned: List<GameActivity.Phrase> = gson.fromJson(learnedJson, type) ?: emptyList()
    return learned.contains(phrase)
}