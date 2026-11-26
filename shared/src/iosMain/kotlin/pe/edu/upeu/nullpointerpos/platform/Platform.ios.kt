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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
actual fun prepararNotificaciones() {}
actual fun avisarPedido(titulo: String, detalle: String, idPedido: Long) { AudioServicesPlaySystemSound(1007u) }
actual fun avisarPedidoListo(titulo: String, detalle: String, idPedido: Long) { AudioServicesPlaySystemSound(1008u) }
actual fun destinosNotificacion(): Flow<String> = emptyFlow()
actual fun limpiarDestinoNotificacion() {}
actual fun abrirPdfLocal(nombreArchivo: String, bytes: ByteArray, compartir: Boolean): String? =
    "Vista/descarga directa de PDF disponible en Android."
actual fun hayConexionRed(): Boolean = true
actual fun observarConectividad(onCambio: (Boolean) -> Unit): () -> Unit = {}

@Composable
actual fun WendysLogo(modifier: Modifier) {
    Box(modifier.background(Borgona, CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.LocalFireDepartment, null, tint = AmbarDorado)
    }
}

@Composable
actual fun ActualizarBarrasSistema(oscuro: Boolean) {}

@Composable
actual fun VisorPdfComprobante(
    nombreArchivo: String,
    bytes: ByteArray,
    onCerrar: () -> Unit,
    onAbrirExterno: () -> Unit,
    onCompartir: () -> Unit
) {}
