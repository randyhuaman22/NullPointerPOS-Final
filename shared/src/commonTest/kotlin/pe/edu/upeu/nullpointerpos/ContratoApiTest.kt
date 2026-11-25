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
    @Test fun contratosSunatTarjetaYAuditoriaAlineadosConBackend() = runTest {
        val jsonParser = Json { ignoreUnknownKeys = true }
        val compJson = """{"idComprobante":1,"idPedido":9,"idPago":4,"tipoComprobante":"BOLETA","serie":"B001","correlativo":1,"numeroCompleto":"B001-00000001","fechaEmision":"2026-11-25T07:50:00Z","denominacionReceptor":"CLIENTE DE PRUEBA","totalGravado":55.08,"totalIgv":9.92,"totalImporte":65.00,"hashCpe":"abcd1234efgh","estadoSunat":"ACEPTADO","descripcionCdr":"Aceptado SUNAT","cadenaQr":"20609123456|03|B001|00000001|9.92|65.00|2026-11-25|1|12345678|abcd|"}"""
        val comp = jsonParser.decodeFromString<RespuestaComprobanteSunat>(compJson)
        assertEquals("B001-00000001", comp.numeroFinal)
        assertEquals("abcd1234efgh", comp.hashFinal)
        assertEquals("CLIENTE DE PRUEBA", comp.receptorFinal)

        val tarjetaJson = """{"aprobado":true,"marca":"VISA","codigoAutorizacion":"AUTH-1122","numeroOperacion":"VOUCH-9988","ultimosCuatroDigitos":"0366","titular":"RANDY HUAMAN","mensaje":"Aprobado"}"""
        val tarjeta = jsonParser.decodeFromString<RespuestaSimuladorTarjeta>(tarjetaJson)
        assertTrue(tarjeta.aprobadaFinal)
        assertEquals("VISA", tarjeta.marcaFinal)
        assertEquals("VOUCH-9988", tarjeta.numeroOperacionFinal)
        assertEquals("0366", tarjeta.ultimos4Final)

        val audJson = """{"id_registro_auditoria":5,"id_usuario_actor":1,"accion":"MESA_BLOQUEADA","nombre_entidad":"MESA","id_entidad":"M01","motivo":null,"fecha_creacion":"2026-11-25T07:50:00Z"}"""
        val aud = jsonParser.decodeFromString<AuditoriaPedidoDto>(audJson).dominio()
        assertEquals("MESA", aud.nombreEntidad)
        assertEquals("", aud.motivo)
        assertEquals("1", aud.usuario)
    }
}
