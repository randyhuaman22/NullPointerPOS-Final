// Autor: Randy Huaman

package pe.edu.upeu.nullpointerpos.data.local
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pe.edu.upeu.nullpointerpos.platform.Preferencias
import pe.edu.upeu.nullpointerpos.domain.model.*
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

    fun ultimaSesion(): Sesion? = runCatching {
        prefs.leer("sesion:$servidor")?.let { json.decodeFromString<Sesion>(it) }
    }.getOrNull()
    fun guardarUltimaSesion(sesion: Sesion?, huellaPin: String? = null) {
        if (sesion == null) {
            prefs.guardar("sesion:$servidor", "")
            prefs.guardar("pinhash:$servidor", "")
        } else {
            prefs.guardar("sesion:$servidor", json.encodeToString(sesion))
            if (huellaPin != null) prefs.guardar("pinhash:$servidor", huellaPin)
        }
    }
    fun verificarPinOffline(codigo: String, pin: String): Boolean {
        val esperado = prefs.leer("pinhash:$servidor") ?: return false
        return esperado.isNotBlank() && esperado == huellaOffline(codigo, pin)
    }
    fun huellaOffline(codigo: String, pin: String): String {
        val raw = "${codigo.trim().uppercase()}|${pin.trim()}|wendys-offline"
        var h1 = 0x811c9dc5.toInt()
        var h2 = 0x1a2b3c4d
        for (ch in raw) {
            h1 = (h1 xor ch.code) * 0x01000193
            h2 = h2 * 31 + ch.code
        }
        return "$h1:$h2:${raw.length}"
    }

    fun cache(usuario: String): SnapshotOffline = runCatching {
        prefs.leer("cache:$servidor:$usuario")?.let { json.decodeFromString<SnapshotOffline>(it) }
    }.getOrNull() ?: SnapshotOffline()
    fun guardarCache(usuario: String, snapshot: SnapshotOffline) {
        prefs.guardar("cache:$servidor:$usuario", json.encodeToString(snapshot))
    }

    fun colaSync(usuario: String): List<AccionPendienteSync> = runCatching {
        json.decodeFromString<List<AccionPendienteSync>>(prefs.leer("colasync:$servidor:$usuario") ?: "[]")
    }.getOrDefault(emptyList())
    fun guardarColaSync(usuario: String, cola: List<AccionPendienteSync>) {
        prefs.guardar("colasync:$servidor:$usuario", json.encodeToString(cola))
    }
}
