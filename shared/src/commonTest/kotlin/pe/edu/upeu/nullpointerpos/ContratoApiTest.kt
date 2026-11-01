// Autor: Randy Huaman
package pe.edu.upeu.nullpointerpos

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import pe.edu.upeu.nullpointerpos.data.local.LocalStore
import pe.edu.upeu.nullpointerpos.data.mapper.*
import pe.edu.upeu.nullpointerpos.data.remote.*
import pe.edu.upeu.nullpointerpos.data.remote.dto.*
import pe.edu.upeu.nullpointerpos.domain.repository.FalloApi
import pe.edu.upeu.nullpointerpos.platform.Preferencias
import kotlin.test.*

class ContratoApiTest {
    private val prefs = object : Preferencias {
        val data = mutableMapOf<String,String>()
        override fun leer(clave: String) = data[clave]
        override fun guardar(clave: String, valor: String) { data[clave] = valor }
    }
    @Test fun importesExactos() {
        assertEquals(1990L, centimos("19.90"))
        assertEquals(1L, centimos("0.01"))
        assertEquals(100L, centimos("1"))
        assertFailsWith<IllegalArgumentException> { centimos("1.999") }
    }
    @Test fun loginUsaContratoReal() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/api/v1/autenticacion/iniciar-sesion", request.url.encodedPath)
            assertEquals(HttpMethod.Post, request.method)
            assertNull(request.headers[HttpHeaders.Authorization])
            respond("""{"token":"jwt","tipoToken":"Bearer","expiraEnSegundos":3600,"usuario":{"idUsuario":2,"codigoTrabajador":"MOZ001","nombreCompleto":"Mozo","rol":"MOZO"}}""", headers = headersOf(HttpHeaders.ContentType,"application/json"))
        }
        val client = HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys=true }) } }
        val store = LocalStore(prefs).apply { servidor="http://localhost:8080" }
        try {
            val response = PosApi(client,store,SessionContext()).login("MOZ001","1234")
            assertEquals("MOZ001",response.usuario.codigoTrabajador)
            assertEquals("jwt",response.token)
        } finally { client.close() }
    }
    @Test fun envioDesenvuelvePedidoYConservaImportes() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/api/v1/pedidos/9/enviar-cocina",request.url.encodedPath)
            assertEquals(HttpMethod.Post,request.method)
            assertEquals("Bearer jwt",request.headers[HttpHeaders.Authorization])
            respond("""{"mensaje":"Pedido enviado a cocina correctamente.","pedido":{"idPedido":9,"codigoPedido":"NP-ABC","idMesa":1,"codigoMesa":"M01","codigoMozo":"MOZ001","estado":"ENVIADO_COCINA","importeTotal":39.80,"fechaEnvio":"2026-11-05T12:00:00Z","detalles":[{"idProducto":1,"nombreProducto":"Pollo","cantidad":2,"precioUnitario":19.90,"observaciones":"Sin crema"}]}}""", headers=headersOf(HttpHeaders.ContentType,"application/json"))
        }
        val client=HttpClient(engine){install(ContentNegotiation){json(Json{ignoreUnknownKeys=true})}}
        val store=LocalStore(prefs).apply{servidor="http://localhost:8080"}
        val session=SessionContext().apply{token="jwt"}
        try {
            val pedido=PosApi(client,store,session).enviar(9).pedido.dominio()
            assertEquals(3980L,pedido.importeCentimos)
            assertEquals("Sin crema",pedido.lineas.single().observaciones)
        } finally { client.close() }
    }
}
