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
                        putExtra("DIFFICULTY", "Favorites")
                        putExtra("PHRASE", Gson().toJson(phrase))
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

    // State to hold favorites, updated when a phrase is removed
    val favoritesByLanguage = remember { mutableStateOf(loadFavorites(context, languages, gson)) }

    // Function to reload favorites and update state
    fun refreshFavorites() {
        favoritesByLanguage.value = loadFavorites(context, languages, gson)
    }

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
                favoritesByLanguage.value.forEach { (language, phrases) ->
                    item {
                        Text(
                            text = language,
                            fontSize = 24.sp,
                            color = Color(0xFFCE93D8),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    items(phrases) { phrase ->
                        var isFavorite by remember { mutableStateOf(true) } // Local state for each item
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
                                    isFavorite = false // Update local state to unfill heart
                                    refreshFavorites() // Refresh the entire list
                                    Toast.makeText(context, "Removed from favorites", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Favorite,
                                    contentDescription = "Unfavorite",
                                    tint = if (isFavorite) Color(0xFFFF9999) else Color(0xFFBBBBBB), // Change color when unfavorited
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
                containerColor = Color(0xFFCE93D8),
                contentColor = Color(0xFFFCFCFC)
            )
        ) {
            Text("Back")
        }
    }
}

// Load favorites from SharedPreferences
private fun loadFavorites(
    context: android.content.Context,
    languages: List<String>,
    gson: Gson
): List<Pair<String, List<GameActivity.Phrase>>> {
    return languages.map { language ->
        val prefs = context.getSharedPreferences("FavoritesPrefs", android.content.Context.MODE_PRIVATE)
        val favoritesJson = prefs.getString("favorites_$language", "[]")
        val type = object : TypeToken<List<GameActivity.Phrase>>() {}.type
        val phrases: List<GameActivity.Phrase> = gson.fromJson(favoritesJson, type) ?: emptyList()
        language to phrases
    }.filter { it.second.isNotEmpty() }
}

// Remove a phrase from favorites
private fun removeFavoritePhrase(phrase: GameActivity.Phrase, language: String, context: android.content.Context) {
    val prefs = context.getSharedPreferences("FavoritesPrefs", android.content.Context.MODE_PRIVATE)
    val gson = Gson()
    val favoritesJson = prefs.getString("favorites_$language", "[]")
    val type = object : TypeToken<MutableList<GameActivity.Phrase>>() {}.type
    val favorites: MutableList<GameActivity.Phrase> = gson.fromJson(favoritesJson, type) ?: mutableListOf()
    favorites.remove(phrase)
    prefs.edit().putString("favorites_$language", gson.toJson(favorites)).apply()
}