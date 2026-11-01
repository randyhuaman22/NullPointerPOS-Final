// Autor: Randy Huaman

package pe.edu.upeu.nullpointerpos.data.remote
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withTimeout
// WebSocket nativo con protocolo STOMP del backend; no usa SockJS ni rutas alternativas.
class CocinaSocket(private val api: PosApi) {
    fun eventos() = channelFlow {
        val eventos = this
        api.client.webSocket(urlString = api.websocketUrl(), request = { bearerAuth(api.token()) }) {
            send(Frame.Text("CONNECT\naccept-version:1.2\nhost:nullpointer\nheart-beat:0,0\n\n\u0000"))
            var conectado = false
            var buffer = ""
            withTimeout(15000) {
                while (!conectado) {
                    val frame = incoming.receive()
                    if (frame is Frame.Text) {
                        buffer += frame.readText()
                        if (buffer.contains("ERROR")) error("El servidor rechazó STOMP")
                        if (buffer.contains("CONNECTED") && buffer.contains('\u0000')) conectado = true
                    }
                }
            }
            send(Frame.Text("SUBSCRIBE\nid:cocina\ndestination:/topic/cocina\nack:auto\n\n\u0000"))
            eventos.send(Unit) // Recargar tras cada reconexión para recuperar eventos perdidos.
            buffer = ""
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    buffer += frame.readText()
                    require(buffer.length <= 2_000_000) { "Mensaje de cocina demasiado grande" }
                    while (buffer.contains('\u0000')) {
                        val mensaje = buffer.substringBefore('\u0000').trimStart()
                        buffer = buffer.substringAfter('\u0000')
                        if (mensaje.startsWith("ERROR")) error("Error de suscripción de cocina")
                        if (mensaje.startsWith("MESSAGE")) eventos.send(Unit)
                    }
                }
            }
        }
    }
}
