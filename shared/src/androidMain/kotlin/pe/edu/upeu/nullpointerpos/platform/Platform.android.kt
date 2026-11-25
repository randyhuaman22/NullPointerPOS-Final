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
actual fun servidorInicial() = "http://192.168.1.6:8080"
actual fun avisarPedido() {
    runCatching { val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70); tone.startTone(ToneGenerator.TONE_PROP_ACK, 180); Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 400) }
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
