// Autor: Randy Huaman (Actualizado con API Gateway completa para Sprints 1, 2 y 3)

package pe.edu.upeu.nullpointerpos.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import pe.edu.upeu.nullpointerpos.data.local.LocalStore
import pe.edu.upeu.nullpointerpos.data.remote.dto.*
import pe.edu.upeu.nullpointerpos.domain.model.*
import pe.edu.upeu.nullpointerpos.domain.repository.FalloApi

class PosApi(val client: HttpClient, private val store: LocalStore, private val session: SessionContext) {
    fun url(path: String) = store.servidor.trimEnd('/') + "/api/v1/" + path

    private suspend fun call(
        path: String,
        method: HttpMethod = HttpMethod.Get,
        payload: Any? = null,
        auth: Boolean = true
    ): HttpResponse {
        val response = client.request(url(path)) {
            this.method = method
            contentType(ContentType.Application.Json)
            if (auth) session.token?.let { bearerAuth(it) }
            when (payload) {
                is LoginRequest -> setBody(payload)
                is CrearPedidoRequest -> setBody(payload)
                is SincronizarPedidoOfflineRequest -> setBody(payload)
                is EditarPedidoRequest -> setBody(payload)
                is TrasladoRequest -> setBody(payload)
                is CrearUsuarioRequest -> setBody(payload)
                is DisponibilidadProductoRequest -> setBody(payload)
                is AperturaCajaRequest -> setBody(payload)
                is CobroEfectivoRequest -> setBody(payload)
                is CobroDigitalRequest -> setBody(payload)
                is CobroMixtoRequest -> setBody(payload)
                is CierreCajaRequest -> setBody(payload)
                is ClienteDto -> setBody(payload)
                is TicketWhatsAppRequest -> setBody(payload)
                is DescuentoPedidoRequest -> setBody(payload)
                is AnularPedidoRequest -> setBody(payload)
                is CrearInsumoRequest -> setBody(payload)
                is CompraRequest -> setBody(payload)
                is MermaRequest -> setBody(payload)
                is SolicitudSimularTarjeta -> setBody(payload)
                is SolicitudEmitirComprobante -> setBody(payload)
                null -> Unit
                else -> setBody(payload)
            }
        }
        if (!response.status.isSuccess()) {
            val message = runCatching {
                Json.parseToJsonElement(response.bodyAsText()).jsonObject["mensaje"]?.jsonPrimitive?.content
            }.getOrNull()
            throw FalloApi(response.status.value, message ?: "No se pudo completar la operación (HTTP ${response.status.value}).")
        }
        return response
    }

    // --- SPRINT 1 ---
    suspend fun login(codigo: String, pin: String): LoginResponse =
        call("autenticacion/iniciar-sesion", HttpMethod.Post, LoginRequest(codigo, pin), false).body()

    suspend fun mesas(): List<Mesa> = call("mesas").body()
    suspend fun categorias(): List<Categoria> = call("menu/categorias").body()
    suspend fun productos(): List<ProductoDto> = call("menu/productos?soloDisponibles=true").body()
    suspend fun todosProductos(): List<ProductoDto> = call("menu/productos?soloDisponibles=false").body()
    suspend fun crear(b: CrearPedidoRequest): PedidoDto = call("pedidos", HttpMethod.Post, b).body()
    suspend fun sincronizarPedidoOffline(b: SincronizarPedidoOfflineRequest): PedidoDto =
        call("pedidos/sincronizar", HttpMethod.Post, b).body()
    suspend fun pedido(id: Long): PedidoDto = call("pedidos/$id").body()
    suspend fun pedidosActivosSalon(): List<PedidoDto> = call("pedidos/salon-activos").body()
    suspend fun pendientesCobro(): List<PedidoDto> = call("pedidos/pendientes-cobro").body()
    suspend fun pedidoPendienteCobro(idPedido: Long): PedidoDto = call("pedidos/pendientes-cobro/$idPedido").body()
    suspend fun editar(id: Long, b: EditarPedidoRequest): PedidoDto = call("pedidos/$id/detalles", HttpMethod.Put, b).body()
    suspend fun enviar(id: Long): EnvioResponse = call("pedidos/$id/enviar-cocina", HttpMethod.Post).body()
    suspend fun trasladar(b: TrasladoRequest) { call("mesas/trasladar", HttpMethod.Put, b) }
    suspend fun usuarios(): List<Cuenta> = call("administracion/usuarios").body()
    suspend fun crearUsuario(b: CrearUsuarioRequest) { call("administracion/usuarios", HttpMethod.Post, b) }
    suspend fun activar(id: Long, activo: Boolean) {
        call("administracion/usuarios/$id/" + if (activo) "activar" else "desactivar", HttpMethod.Patch)
    }

    // --- SPRINT 2 ---
    suspend fun cocina(): List<PedidoDto> = call("cocina/pedidos").body()
    suspend fun marcarPlatoListo(idDetalle: Long) {
        call("pedidos/detalle/$idDetalle", HttpMethod.Patch)
    }
    suspend fun marcarPedidoListoCocina(idPedido: Long): PedidoDto =
        call("pedidos/$idPedido/listo-cocina", HttpMethod.Patch).body()
    suspend fun resumenPago(idPedido: Long): ResumenPagoDto =
        call("pedidos/$idPedido/resumen-pago").body()
    suspend fun cambiarDisponibilidad(idProducto: Long, disponible: Boolean) {
        call("menu/disponibilidad", HttpMethod.Patch, DisponibilidadProductoRequest(idProducto, disponible))
    }

    // Caja (Sprint 2 & 3)
    suspend fun aperturaCaja(montoInicial: Double): TurnoCajaResponse =
        call("caja/apertura", HttpMethod.Post, AperturaCajaRequest(montoInicial)).body()

    suspend fun turnoAbierto(): TurnoCajaResponse? {
        return try { call("caja/turno-abierto").body<TurnoCajaResponse>() }
        catch (e: FalloApi) { if (e.codigo == 404) null else throw e }
    }

    suspend fun cobrarEfectivo(b: CobroEfectivoRequest): RespuestaPagoDto =
        call("caja/cobrar", HttpMethod.Post, b).body()

    suspend fun cobrarDigital(b: CobroDigitalRequest): RespuestaPagoDto =
        call("caja/cobrar-digital", HttpMethod.Post, b).body()

    suspend fun cobrarMixto(b: CobroMixtoRequest): RespuestaPagoDto =
        call("caja/cobrar-mixto", HttpMethod.Post, b).body()

    suspend fun cierreCaja(b: CierreCajaRequest): CierreCajaResponse =
        call("caja/cierre", HttpMethod.Post, b).body()

    suspend fun simularTarjeta(b: SolicitudSimularTarjeta): RespuestaSimuladorTarjeta =
        call("caja/simulador-tarjeta", HttpMethod.Post, b).body()

    suspend fun emitirComprobanteSunat(b: SolicitudEmitirComprobante): RespuestaComprobanteSunat =
        call("comprobantes/emitir", HttpMethod.Post, b).body()

    // --- SPRINT 3 ---
    suspend fun buscarCliente(tipo: String, numero: String): ClienteDto? {
        return try { call("clientes/$tipo/$numero").body<ClienteDto>() }
        catch (e: FalloApi) { if (e.codigo == 404) null else throw e }
    }

    suspend fun guardarCliente(b: ClienteDto) {
        call("clientes", HttpMethod.Put, b)
    }

    suspend fun ticketWhatsApp(b: TicketWhatsAppRequest): RespuestaTicketWhatsAppDto =
        call("tickets/whatsapp", HttpMethod.Post, b).body()

    suspend fun descargarPdfComprobante(idComprobante: Long): ByteArray =
        call("comprobantes/$idComprobante/pdf").readBytes()

    suspend fun aplicarDescuento(idPedido: Long, b: DescuentoPedidoRequest) {
        call("pedidos/$idPedido/descuento", HttpMethod.Patch, b)
    }

    suspend fun anularPedido(idPedido: Long, b: AnularPedidoRequest) {
        call("pedidos/$idPedido/anular", HttpMethod.Post, b)
    }

    // Inventario
    suspend fun insumos(): List<InsumoDto> = call("inventario/insumos").body()
    suspend fun crearInsumo(b: CrearInsumoRequest) { call("inventario/insumos", HttpMethod.Post, b) }
    suspend fun alertasStock(): List<InsumoDto> = call("inventario/alertas").body()
    suspend fun guardarReceta(idProducto: Long, ingredientes: List<IngredienteRecetaDto>) {
        call("inventario/recetas/$idProducto", HttpMethod.Put, ingredientes)
    }
    suspend fun registrarCompra(b: CompraRequest) { call("inventario/compras", HttpMethod.Post, b) }
    suspend fun registrarMerma(b: MermaRequest) { call("inventario/mermas", HttpMethod.Post, b) }

    // Reportes & Auditoría
    suspend fun reporteVentasPorHora(): List<VentaPorHoraDto> = call("reportes/ventas-por-hora").body()
    suspend fun reportePlatosPopulares(): List<PlatoPopularDto> = call("reportes/platos-populares").body()
    suspend fun auditoriaPedidos(): List<AuditoriaPedidoDto> = call("auditoria/pedidos").body()

    fun websocketUrl(): String =
        store.servidor.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://").trimEnd('/') + "/ws/cocina"

    fun token() = session.token.orEmpty()
}
