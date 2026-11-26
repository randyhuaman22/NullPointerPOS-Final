// Autor: Randy Huaman

package pe.edu.upeu.nullpointerpos.platform
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.flow.Flow
interface Preferencias { fun leer(clave: String): String?; fun guardar(clave: String, valor: String) }
expect fun preferencias(): Preferencias
expect fun motorHttp(): HttpClientEngine
expect fun servidorInicial(): String
expect fun prepararNotificaciones()
expect fun avisarPedido(titulo: String = "Nuevo pedido en cocina", detalle: String = "", idPedido: Long = 0L)
expect fun avisarPedidoListo(titulo: String = "Pedido listo para entregar", detalle: String = "", idPedido: Long = 0L)
expect fun destinosNotificacion(): Flow<String>
expect fun limpiarDestinoNotificacion()
expect fun abrirPdfLocal(nombreArchivo: String, bytes: ByteArray, compartir: Boolean = false): String?
expect fun hayConexionRed(): Boolean
expect fun observarConectividad(onCambio: (Boolean) -> Unit): () -> Unit

@Composable
expect fun WendysLogo(modifier: Modifier = Modifier)

@Composable
expect fun ActualizarBarrasSistema(oscuro: Boolean)

@Composable
expect fun VisorPdfComprobante(
    nombreArchivo: String,
    bytes: ByteArray,
    onCerrar: () -> Unit,
    onAbrirExterno: () -> Unit,
    onCompartir: () -> Unit
)
