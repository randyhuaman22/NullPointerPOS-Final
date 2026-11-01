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

@Composable
expect fun WendysLogo(modifier: Modifier = Modifier)
