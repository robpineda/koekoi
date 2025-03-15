package com.robertopineda.koekoi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen(
                onStartGame = { language, difficulty, material ->
                    val intent = Intent(this, GameActivity::class.java).apply {
                        putExtra("LANGUAGE", language)
                        putExtra("DIFFICULTY", difficulty)
                        putExtra("MATERIAL", material)
                    }
                    startActivity(intent)
                },
                onShowFavorites = {
                    val intent = Intent(this, FavoritesActivity::class.java)
                    startActivity(intent)
                },
                onShowSettings = {
                    val intent = Intent(this, SettingsActivity::class.java)
                    startActivity(intent)
                }
            )
        }
    }
}

@Composable
fun MainScreen(
    onStartGame: (String, String, String) -> Unit,
    onShowFavorites: () -> Unit,
    onShowSettings: () -> Unit
) {
    var selectedLanguage by remember { mutableStateOf("Korean") }
    var selectedDifficulty by remember { mutableStateOf("") }
    var selectedMaterial by remember { mutableStateOf("Vocabulary") }
    var languageExpanded by remember { mutableStateOf(false) }
    var difficultyExpanded by remember { mutableStateOf(false) }
    var materialExpanded by remember { mutableStateOf(false) }

    val languageOptions = listOf("Japanese", "Korean", "Vietnamese", "Spanish")
    val difficultyOptions = when (selectedLanguage) {
        "Japanese" -> listOf("JLPT N1", "JLPT N2", "JLPT N3", "JLPT N4", "JLPT N5")
        "Korean" -> listOf("TOPIK I", "TOPIK II")
        "Vietnamese" -> listOf("Beginner", "Intermediate", "Advanced")
        "Spanish" -> listOf("Beginner", "Intermediate", "Advanced")
        else -> emptyList()
    }
    val materialOptions = listOf("Vocabulary", "Grammar")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF007893)) // RGB(0, 120, 147)
            .padding(WindowInsets.systemBars.asPaddingValues())
    ) {
        Text(
            text = "KoeKoi",
            fontSize = 38.sp,
            fontFamily = FontFamily.Cursive,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFE0F7FA),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 72.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Select Language",
                fontSize = 18.sp,
                color = Color(0xFFE0F7FA)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box {
                OutlinedButton(
                    onClick = { languageExpanded = true },
                    modifier = Modifier.fillMaxWidth(0.6f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFFFB300)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFFFB300))
                ) {
                    Text(
                        text = selectedLanguage,
                        color = Color(0xFFFFB300)
                    )
                }
                DropdownMenu(
                    expanded = languageExpanded,
                    onDismissRequest = { languageExpanded = false },
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .background(Color(0xFF015D73))
                ) {
                    languageOptions.forEach { language ->
                        DropdownMenuItem(
                            text = { Text(language, color = Color(0xFFE0F7FA)) },
                            onClick = {
                                selectedLanguage = language
                                selectedDifficulty = ""
                                selectedMaterial = "Vocabulary"
                                languageExpanded = false
                            },
                            modifier = Modifier.background(Color(0xFF015D73))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Select Difficulty",
                fontSize = 18.sp,
                color = Color(0xFFE0F7FA)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box {
                OutlinedButton(
                    onClick = { difficultyExpanded = true },
                    modifier = Modifier.fillMaxWidth(0.6f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFFFB300)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFFFB300))
                ) {
                    Text(
                        text = if (selectedDifficulty.isEmpty()) "Choose Difficulty" else selectedDifficulty,
                        color = Color(0xFFFFB300)
                    )
                }
                DropdownMenu(
                    expanded = difficultyExpanded,
                    onDismissRequest = { difficultyExpanded = false },
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .background(Color(0xFF015D73))
                ) {
                    difficultyOptions.forEach { difficulty ->
                        DropdownMenuItem(
                            text = { Text(difficulty, color = Color(0xFFE0F7FA)) },
                            onClick = {
                                selectedDifficulty = difficulty
                                difficultyExpanded = false
                            },
                            modifier = Modifier.background(Color(0xFF015D73))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Select Material",
                fontSize = 18.sp,
                color = Color(0xFFE0F7FA)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box {
                OutlinedButton(
                    onClick = { if (selectedLanguage == "Japanese") materialExpanded = true },
                    modifier = Modifier.fillMaxWidth(0.6f),
                    enabled = selectedLanguage == "Japanese",
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (selectedLanguage == "Japanese") Color(0xFFFFB300) else Color(0xFF90A4AE),
                        disabledContentColor = Color(0xFF90A4AE)
                    ),
                    border = BorderStroke(1.dp, if (selectedLanguage == "Japanese") Color(0xFFFFB300) else Color(0xFF455A64))
                ) {
                    Text(
                        text = selectedMaterial,
                        color = if (selectedLanguage == "Japanese") Color(0xFFFFB300) else Color(0xFF90A4AE)
                    )
                }
                DropdownMenu(
                    expanded = materialExpanded,
                    onDismissRequest = { materialExpanded = false },
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .background(Color(0xFF015D73))
                ) {
                    materialOptions.forEach { material ->
                        DropdownMenuItem(
                            text = { Text(material, color = Color(0xFFE0F7FA)) },
                            onClick = {
                                selectedMaterial = material
                                materialExpanded = false
                            },
                            modifier = Modifier.background(Color(0xFF015D73))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (selectedDifficulty.isNotEmpty()) {
                        onStartGame(selectedLanguage, selectedDifficulty, selectedMaterial)
                    }
                },
                enabled = selectedDifficulty.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF8F00),
                    contentColor = Color(0xFFE0F7FA),
                    disabledContainerColor = Color(0xFF455A64),
                    disabledContentColor = Color(0xFF90A4AE)
                )
            ) {
                Text("Start Game")
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(
                onClick = onShowFavorites,
                modifier = Modifier
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Favorites",
                    tint = Color(0xFFE0F7FA),
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(
                onClick = onShowSettings,
                modifier = Modifier
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = Color(0xFFE0F7FA),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}