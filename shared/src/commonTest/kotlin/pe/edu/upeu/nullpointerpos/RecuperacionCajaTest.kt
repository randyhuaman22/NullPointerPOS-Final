package pe.edu.upeu.nullpointerpos

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import pe.edu.upeu.nullpointerpos.data.local.LocalStore
import pe.edu.upeu.nullpointerpos.data.remote.*
import pe.edu.upeu.nullpointerpos.data.remote.dto.*
import pe.edu.upeu.nullpointerpos.data.repository.PosRepositoryRest
import pe.edu.upeu.nullpointerpos.domain.model.dinero
import pe.edu.upeu.nullpointerpos.domain.repository.FalloApi
import pe.edu.upeu.nullpointerpos.platform.Preferencias
import kotlin.test.*

class RecuperacionCajaTest {
    private val prefs = object : Preferencias {
        val data = mutableMapOf<String, String>()
        override fun leer(clave: String) = data[clave]
        override fun guardar(clave: String, valor: String) { data[clave] = valor }
    }

    @Test fun elPagoPendienteSeRecuperaYSeAislaPorServidorYUsuario() {
        val store = LocalStore(prefs).apply { servidor = "http://servidor-a" }
        val solicitud = SolicitudEmitirComprobante(9, 17)
        store.guardarEmisiones("CAJ001", listOf(solicitud))
        assertEquals(listOf(solicitud), LocalStore(prefs).emisiones("CAJ001"))
        assertTrue(store.emisiones("CAJ002").isEmpty())
        store.servidor = "http://servidor-b"
        assertTrue(store.emisiones("CAJ001").isEmpty())
    }

    @Test fun importesNegativosConservanSignoYCentimos() {
        assertEquals("S/ -0.50", dinero(-50))
        assertEquals("S/ -12.34", dinero(-1234))
        assertEquals("S/ 0.00", dinero(0))
        assertEquals("S/ 19.90", dineroD(19.90))
    }

    @Test fun ausenciaDeClienteSeDistingueDeUnErrorDeAutorizacion() = runTest {
        val store = LocalStore(prefs).apply { servidor = "http://servidor-a" }
        for (status in listOf(HttpStatusCode.NotFound, HttpStatusCode.Unauthorized, HttpStatusCode.ServiceUnavailable)) {
            val engine = MockEngine { respond("{\"mensaje\":\"Error de prueba\"}", status, headersOf(HttpHeaders.ContentType, "application/json")) }
            val client = HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
            try {
                val api = PosApi(client, store, SessionContext())
                if (status == HttpStatusCode.NotFound) {
                    assertNull(api.buscarCliente("DNI", "12345678"))
                    assertNull(api.turnoAbierto())
                } else {
                    assertEquals(status.value, assertFailsWith<FalloApi> { api.buscarCliente("DNI", "12345678") }.codigo)
                    assertEquals(status.value, assertFailsWith<FalloApi> { api.turnoAbierto() }.codigo)
                }
            } finally { client.close() }
        }
    }

    @Test fun unaRespuestaSinCdrNuncaAfirmaAceptacion() {
        val respuesta = RespuestaComprobanteSunat(estadoSunat = "PENDIENTE_FIRMA")
        assertFalse(respuesta.descripcionFinal.contains("Aceptado"))
    }

    @Test fun reintentarCobroConservaLaMismaIdempotencia() = runTest {
        val claves = mutableListOf<String>()
        val engine = MockEngine { request ->
            val contenido = (request.body as io.ktor.http.content.OutgoingContent.ByteArrayContent).bytes().decodeToString()
            val cuerpo = Json.decodeFromString<CobroEfectivoRequest>(contenido)
            claves += cuerpo.idempotencia
            respond("{\"idPago\":17,\"idPedido\":9,\"idTurno\":1,\"metodo\":\"EFECTIVO\",\"importe\":65.0,\"importeRecibido\":70.0,\"vuelto\":5.0,\"estado\":\"CONFIRMADO\",\"fechaCreacion\":\"2026-11-25T10:00:00-05:00\"}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        try {
            val api = PosApi(client, LocalStore(prefs).apply { servidor = "http://servidor-a" }, SessionContext())
            val repo = PosRepositoryRest(api, CocinaSocket(api))
            repeat(2) { repo.cobrarEfectivo(9, 1, 70.0, 65.0) }
            assertEquals(2, claves.size)
            assertEquals(claves.first(), claves.last())
        } finally { client.close() }
    }
}
