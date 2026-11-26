// Autor: Randy Huaman
package pe.edu.upeu.nullpointerpos
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import pe.edu.upeu.nullpointerpos.di.initApp
import pe.edu.upeu.nullpointerpos.platform.EXTRA_DESTINO_NOTIFICACION
import pe.edu.upeu.nullpointerpos.platform.androidContext
import pe.edu.upeu.nullpointerpos.platform.currentActivityRef
import pe.edu.upeu.nullpointerpos.platform.prepararNotificaciones
import pe.edu.upeu.nullpointerpos.platform.registrarDestinoNotificacion

class MainActivity : ComponentActivity() {
    private val permisoNotificacionesLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidContext = applicationContext
        currentActivityRef = java.lang.ref.WeakReference(this)
        prepararNotificaciones()
        solicitarPermisoNotificacionesSiFalta()
        registrarDestinoNotificacion(intent?.getStringExtra(EXTRA_DESTINO_NOTIFICACION))
        initApp()
        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        registrarDestinoNotificacion(intent.getStringExtra(EXTRA_DESTINO_NOTIFICACION))
    }

    override fun onResume() {
        super.onResume()
        currentActivityRef = java.lang.ref.WeakReference(this)
    }

    private fun solicitarPermisoNotificacionesSiFalta() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                permisoNotificacionesLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
