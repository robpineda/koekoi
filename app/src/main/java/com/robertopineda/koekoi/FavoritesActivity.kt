package com.robertopineda.koekoi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
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

class FavoritesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FavoritesScreen(
                onBack = { finish() },
                onPhraseSelected = { phrase, language ->
                    val intent = Intent(this, GameActivity::class.java).apply {
                        putExtra("LANGUAGE", language)
                        putExtra("DIFFICULTY", "Favorites") // Custom difficulty to indicate single phrase
                        putExtra("PHRASE", Gson().toJson(phrase)) // Pass the selected phrase
                    }
                    startActivity(intent)
                }
            )
        }
    }
}

@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onPhraseSelected: (GameActivity.Phrase, String) -> Unit
) {
    val context = LocalContext.current
    val languages = listOf("Japanese", "Korean", "Vietnamese", "Spanish")
    val gson = Gson()

    // Load favorites for each language
    val favoritesByLanguage = languages.map { language ->
        val prefs = context.getSharedPreferences("FavoritesPrefs", android.content.Context.MODE_PRIVATE)
        val favoritesJson = prefs.getString("favorites_$language", "[]")
        val type = object : TypeToken<List<GameActivity.Phrase>>() {}.type
        language to (gson.fromJson<List<GameActivity.Phrase>>(favoritesJson, type) ?: emptyList())
    }.filter { it.second.isNotEmpty() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF212121))
            .padding(WindowInsets.systemBars.asPaddingValues())
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Favorite Phrases",
                fontSize = 32.sp,
                color = Color(0xFFBBDEFB),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn {
                favoritesByLanguage.forEach { (language, phrases) ->
                    item {
                        Text(
                            text = language,
                            fontSize = 24.sp,
                            color = Color(0xFFCE93D8),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    items(phrases) { phrase ->
                        var isFavorite by remember { mutableStateOf(true) }
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
                                    color = Color(0xFFF6F6F6)
                                )
                                Text(
                                    text = phrase.english,
                                    fontSize = 14.sp,
                                    color = Color(0xFFBBBBBB)
                                )
                            }
                            IconButton(
                                onClick = {
                                    removeFavoritePhrase(phrase, language, context)
                                    isFavorite = false // This will trigger recomposition
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Favorite,
                                    contentDescription = "Unfavorite",
                                    tint = Color(0xFFFF9999)
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
                containerColor = Color(0xFFCE93D8),
                contentColor = Color(0xFFFCFCFC)
            )
        ) {
            Text("Back")
        }
    }
}

// Reuse the same helper function from GameActivity
private fun removeFavoritePhrase(phrase: GameActivity.Phrase, language: String, context: android.content.Context) {
    val prefs = context.getSharedPreferences("FavoritesPrefs", android.content.Context.MODE_PRIVATE)
    val gson = Gson()
    val favoritesJson = prefs.getString("favorites_$language", "[]")
    val type = object : TypeToken<MutableList<GameActivity.Phrase>>() {}.type
    val favorites: MutableList<GameActivity.Phrase> = gson.fromJson(favoritesJson, type) ?: mutableListOf()
    favorites.remove(phrase)
    prefs.edit().putString("favorites_$language", gson.toJson(favorites)).apply()
}