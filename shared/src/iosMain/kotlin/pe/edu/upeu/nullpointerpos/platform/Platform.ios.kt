// Autor: Randy Huaman
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package pe.edu.upeu.nullpointerpos.platform
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import pe.edu.upeu.nullpointerpos.theme.AmbarDorado
import pe.edu.upeu.nullpointerpos.theme.Borgona
import platform.Foundation.NSUserDefaults
import platform.AudioToolbox.AudioServicesPlaySystemSound
actual fun preferencias(): Preferencias = object : Preferencias {
    override fun leer(clave: String) = NSUserDefaults.standardUserDefaults.stringForKey(clave)
    override fun guardar(clave: String, valor: String) { NSUserDefaults.standardUserDefaults.setObject(valor, clave) }
}
actual fun motorHttp(): HttpClientEngine = Darwin.create()
actual fun servidorInicial() = "http://localhost:8080"
actual fun avisarPedido() { AudioServicesPlaySystemSound(1007u) }
actual fun abrirPdfLocal(nombreArchivo: String, bytes: ByteArray, compartir: Boolean): String? =
    "Vista/descarga directa de PDF disponible en Android."

@Composable
actual fun WendysLogo(modifier: Modifier) {
    Box(modifier.background(Borgona, CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.LocalFireDepartment, null, tint = AmbarDorado)
    }
}
