// Autor: Randy Huaman

package pe.edu.upeu.nullpointerpos.platform
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.ktor.client.engine.HttpClientEngine
interface Preferencias { fun leer(clave: String): String?; fun guardar(clave: String, valor: String) }
expect fun preferencias(): Preferencias
expect fun motorHttp(): HttpClientEngine
expect fun servidorInicial(): String
expect fun avisarPedido()
expect fun abrirPdfLocal(nombreArchivo: String, bytes: ByteArray, compartir: Boolean = false): String?
expect fun hayConexionRed(): Boolean
expect fun observarConectividad(onCambio: (Boolean) -> Unit): () -> Unit

@Composable
expect fun WendysLogo(modifier: Modifier = Modifier)

@Composable
expect fun ActualizarBarrasSistema(oscuro: Boolean)
