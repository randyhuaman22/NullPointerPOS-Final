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
            val guardado = prefs.leer("servidor")?.trim()
            return if (guardado.isNullOrBlank()) {
                pe.edu.upeu.nullpointerpos.platform.servidorInicial()
            } else guardado
        }
        set(value) { prefs.guardar("servidor", value) }
    var modoOscuro: Boolean
        get() = prefs.leer("modo_oscuro") == "true"
        set(value) { prefs.guardar("modo_oscuro", value.toString()) }
    fun emisiones(usuario: String): List<pe.edu.upeu.nullpointerpos.data.remote.dto.SolicitudEmitirComprobante> =
        json.decodeFromString(prefs.leer("emisiones:$servidor:$usuario") ?: "[]")
    fun guardarEmisiones(usuario: String, datos: List<pe.edu.upeu.nullpointerpos.data.remote.dto.SolicitudEmitirComprobante>) =
        prefs.guardar("emisiones:$servidor:$usuario", json.encodeToString(datos))

    private fun key(usuario: String) = "borradores:$servidor:$usuario"
    fun borradores(usuario: String): List<Borrador> = runCatching {
        json.decodeFromString<List<Borrador>>(prefs.leer(key(usuario)) ?: "[]")
    }.getOrDefault(emptyList())
    fun guardar(usuario: String, datos: List<Borrador>) = prefs.guardar(key(usuario), json.encodeToString(datos))
}
