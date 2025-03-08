package com.robertopineda.koekoi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen(
                onStartGame = { language, difficulty ->
                    val intent = Intent(this, GameActivity::class.java).apply {
                        putExtra("LANGUAGE", language)
                        putExtra("DIFFICULTY", difficulty)
                    }
                    startActivity(intent)
                }
            )
        }
    }
}

@Composable
fun MainScreen(onStartGame: (String, String) -> Unit) {
    var selectedLanguage by remember { mutableStateOf("Japanese") } // Default to Japanese
    var selectedDifficulty by remember { mutableStateOf("") } // Store selected difficulty
    var languageExpanded by remember { mutableStateOf(false) } // Toggle language dropdown
    var difficultyExpanded by remember { mutableStateOf(false) } // Toggle difficulty dropdown

    // Define language options
    val languageOptions = listOf("Japanese", "Korean", "Vietnamese", "Spanish")

    // Define difficulty options based on language
    val difficultyOptions = when (selectedLanguage) {
        "Japanese" -> listOf("JLPT N1", "JLPT N2", "JLPT N3", "JLPT N4", "JLPT N5")
        "Korean" -> listOf("TOPIK I", "TOPIK II")
        "Vietnamese" -> listOf("Beginner", "Intermediate", "Advanced")
        "Spanish" -> listOf("Beginner", "Intermediate", "Advanced")
        else -> emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "KoeKoi",
            fontSize = 32.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Language selection with DropdownMenu
        Text("Select Language", fontSize = 18.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Box {
            OutlinedButton(
                onClick = { languageExpanded = true },
                modifier = Modifier.fillMaxWidth(0.6f) // Adjust width as needed
            ) {
                Text(
                    text = selectedLanguage,
                    color = Color.Black
                )
            }
            DropdownMenu(
                expanded = languageExpanded,
                onDismissRequest = { languageExpanded = false },
                modifier = Modifier.fillMaxWidth(0.6f) // Match button width
            ) {
                languageOptions.forEach { language ->
                    DropdownMenuItem(
                        text = { Text(language) },
                        onClick = {
                            selectedLanguage = language
                            selectedDifficulty = "" // Reset difficulty when language changes
                            languageExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Difficulty selection with DropdownMenu
        Text("Select Difficulty", fontSize = 18.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Box {
            OutlinedButton(
                onClick = { difficultyExpanded = true },
                modifier = Modifier.fillMaxWidth(0.6f) // Adjust width as needed
            ) {
                Text(
                    text = if (selectedDifficulty.isEmpty()) "Choose Difficulty" else selectedDifficulty,
                    color = Color.Black
                )
            }
            DropdownMenu(
                expanded = difficultyExpanded,
                onDismissRequest = { difficultyExpanded = false },
                modifier = Modifier.fillMaxWidth(0.6f) // Match button width
            ) {
                difficultyOptions.forEach { difficulty ->
                    DropdownMenuItem(
                        text = { Text(difficulty) },
                        onClick = {
                            selectedDifficulty = difficulty
                            difficultyExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (selectedDifficulty.isNotEmpty()) {
                    onStartGame(selectedLanguage, selectedDifficulty)
                }
            },
            enabled = selectedDifficulty.isNotEmpty() // Disable until difficulty is selected
        ) {
            Text("Start Game")
        }
    }
}