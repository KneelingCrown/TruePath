package ru.truepath.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import ru.truepath.app.ui.TruePathApp
import ru.truepath.app.ui.theme.TruePathTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val model = ViewModelProvider(this)[TruePathViewModel::class.java]
        setContent { TruePathTheme { TruePathApp(model) } }
    }
}
