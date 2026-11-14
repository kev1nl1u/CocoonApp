package com.cocoonapp.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cocoonapp.data.Rule

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CocoonTheme { Root() } }
    }
}

private sealed interface Screen {
    data object Home : Screen
    data object Picker : Screen
    data class Editor(val rule: Rule, val isNew: Boolean) : Screen
}

@Composable
private fun Root() {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }
    when (val current = screen) {
        Screen.Home -> HomeScreen(
            onAdd = { screen = Screen.Picker },
            onEdit = { screen = Screen.Editor(it, isNew = false) },
        )
        Screen.Picker -> PickerScreen(
            onBack = { screen = Screen.Home },
            onPick = { screen = Screen.Editor(Rule(pkg = it), isNew = true) },
        )
        is Screen.Editor -> EditorScreen(current.rule, current.isNew, onDone = { screen = Screen.Home })
    }
}
