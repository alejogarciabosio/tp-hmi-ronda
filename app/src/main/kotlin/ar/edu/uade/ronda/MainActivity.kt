package ar.edu.uade.ronda

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import ar.edu.uade.ronda.ui.navigation.RondaNavHost
import ar.edu.uade.ronda.ui.theme.RondaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RondaTheme {
                Surface(Modifier.fillMaxSize()) {
                    RondaNavHost(modifier = Modifier.safeDrawingPadding())
                }
            }
        }
    }
}
