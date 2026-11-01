// Autor: Randy Huaman
package pe.edu.upeu.nullpointerpos
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import pe.edu.upeu.nullpointerpos.di.initApp
import pe.edu.upeu.nullpointerpos.platform.androidContext
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidContext = applicationContext
        initApp()
        setContent { App() }
    }
}
