// Autor: Randy Huaman

package pe.edu.upeu.nullpointerpos.data.local
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pe.edu.upeu.nullpointerpos.platform.Preferencias
import pe.edu.upeu.nullpointerpos.domain.model.Borrador
class LocalStore(private val prefs: Preferencias) {
    private val json = Json { ignoreUnknownKeys = true }
    var servidor: String
        get() {
            val guardado = prefs.leer("servidor")
            return if (guardado == null || guardado.contains("10.0.2.2") || guardado.contains("localhost")) {
                pe.edu.upeu.nullpointerpos.platform.servidorInicial()
            } else guardado
        }
        set(value) { prefs.guardar("servidor", value) }
    var modoOscuro: Boolean
        get() = prefs.leer("modo_oscuro") == "true"
        set(value) { prefs.guardar("modo_oscuro", value.toString()) }
    private fun key(usuario: String) = "borradores:$servidor:$usuario"
    fun borradores(usuario: String): List<Borrador> = runCatching {
        json.decodeFromString<List<Borrador>>(prefs.leer(key(usuario)) ?: "[]")
    }.getOrDefault(emptyList())
    fun guardar(usuario: String, datos: List<Borrador>) = prefs.guardar(key(usuario), json.encodeToString(datos))
}
