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

    val favoritesByLanguage = remember { mutableStateOf(loadFavorites(context, languages, gson)) }

    fun refreshFavorites() {
        favoritesByLanguage.value = loadFavorites(context, languages, gson)
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
                text = "Favorite Phrases",
                fontSize = 32.sp,
                color = Color(0xFFE0F7FA), // Light cyan
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn {
                favoritesByLanguage.value.forEach { (language, phrases) ->
                    item {
                        Text(
                            text = language,
                            fontSize = 24.sp,
                            color = Color(0xFFFFB300), // Amber accent
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    items(phrases, key = { it.spoken + it.english }) { phrase ->
                        var isFavorite by remember(phrase) { mutableStateOf(isPhraseFavorite(phrase, language, context)) }
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
                                    removeFavoritePhrase(phrase, language, context)
                                    isFavorite = false
                                    refreshFavorites()
                                    Toast.makeText(context, "Removed from favorites", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Favorite,
                                    contentDescription = "Unfavorite",
                                    tint = if (isFavorite) Color(0xFFFF9999) else Color(0xFFE0F7FA), // Keep red when favorited
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

private fun removeFavoritePhrase(phrase: GameActivity.Phrase, language: String, context: android.content.Context) {
    val prefs = context.getSharedPreferences("FavoritesPrefs", android.content.Context.MODE_PRIVATE)
    val gson = Gson()
    val favoritesJson = prefs.getString("favorites_$language", "[]")
    val type = object : TypeToken<MutableList<GameActivity.Phrase>>() {}.type
    val favorites: MutableList<GameActivity.Phrase> = gson.fromJson(favoritesJson, type) ?: mutableListOf()
    favorites.remove(phrase)
    prefs.edit().putString("favorites_$language", gson.toJson(favorites)).apply()
}

private fun isPhraseFavorite(phrase: GameActivity.Phrase, language: String, context: android.content.Context): Boolean {
    val prefs = context.getSharedPreferences("FavoritesPrefs", android.content.Context.MODE_PRIVATE)
    val gson = Gson()
    val favoritesJson = prefs.getString("favorites_$language", "[]")
    val type = object : TypeToken<List<GameActivity.Phrase>>() {}.type
    val favorites: List<GameActivity.Phrase> = gson.fromJson(favoritesJson, type) ?: emptyList()
    return favorites.contains(phrase)
}