// Autor: Randy Huaman
package pe.edu.upeu.nullpointerpos.platform
import android.content.Context
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import pe.edu.upeu.nullpointerpos.theme.AmbarDorado
import pe.edu.upeu.nullpointerpos.theme.Borgona

lateinit var androidContext: Context
actual fun preferencias(): Preferencias = object : Preferencias {
    private val prefs = androidContext.getSharedPreferences("nullpointer_pos", Context.MODE_PRIVATE)
    override fun leer(clave: String) = prefs.getString(clave, null)
    override fun guardar(clave: String, valor: String) { prefs.edit().putString(clave, valor).commit() }
}
actual fun motorHttp(): HttpClientEngine = OkHttp.create()
actual fun servidorInicial(): String {
    val emulador = android.os.Build.FINGERPRINT.startsWith("generic") || android.os.Build.MODEL.contains("sdk_gphone", ignoreCase = true)
    return if (emulador) "http://10.0.2.2:8080" else "http://localhost:8080"
}
actual fun avisarPedido() {
    runCatching { val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70); tone.startTone(ToneGenerator.TONE_PROP_ACK, 180); Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 400) }
}

actual fun abrirPdfLocal(nombreArchivo: String, bytes: ByteArray, compartir: Boolean): String? {
    return runCatching {
        val dir = java.io.File(androidContext.cacheDir, "comprobantes").apply { mkdirs() }
        val safe = nombreArchivo.replace(Regex("[^A-Za-z0-9._-]"), "_").let {
            if (it.lowercase().endsWith(".pdf")) it else "$it.pdf"
        }
        val file = java.io.File(dir, safe).apply { writeBytes(bytes) }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            androidContext,
            "${androidContext.packageName}.fileprovider",
            file
        )
        val baseIntent = if (compartir) {
            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_TEXT, "Comprobante Electrónico Wendy's Food: $safe")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val chooser = android.content.Intent.createChooser(
            baseIntent,
            if (compartir) "Compartir comprobante PDF" else "Ver o imprimir comprobante 80mm"
        ).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        androidContext.startActivity(chooser)
        null
    }.getOrElse { e ->
        "No se pudo abrir el PDF en este dispositivo: ${e.message ?: "Error desconocido"}"
    }
}

@Composable
actual fun WendysLogo(modifier: Modifier) {
    val bitmap = remember {
        val resId = androidContext.resources.getIdentifier("logo_wendys", "drawable", androidContext.packageName)
        if (resId != 0) {
            BitmapFactory.decodeResource(androidContext.resources, resId)?.asImageBitmap()
        } else null
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Wendy's Food Logo",
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        Box(modifier.background(Borgona, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.LocalFireDepartment, null, tint = AmbarDorado)
        }
    }
}

@Composable
actual fun ActualizarBarrasSistema(oscuro: Boolean) {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val mascara = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            view.windowInsetsController?.setSystemBarsAppearance(if (oscuro) 0 else mascara, mascara)
        } else {
            @Suppress("DEPRECATION")
            val mascara = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                (if (android.os.Build.VERSION.SDK_INT >= 26) android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
            @Suppress("DEPRECATION")
            val actual = view.systemUiVisibility
            @Suppress("DEPRECATION")
            view.systemUiVisibility = if (oscuro) actual and mascara.inv() else actual or mascara
        }
    }
}
