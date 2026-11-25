// Autor: Randy Huaman (Implementación Rest completa para todos los Sprints)

package pe.edu.upeu.nullpointerpos.data.repository

import pe.edu.upeu.nullpointerpos.data.remote.*
import pe.edu.upeu.nullpointerpos.data.remote.dto.*
import pe.edu.upeu.nullpointerpos.data.mapper.*
import pe.edu.upeu.nullpointerpos.domain.model.*
import pe.edu.upeu.nullpointerpos.domain.repository.PosRepository
import kotlin.random.Random

class PosRepositoryRest(private val api: PosApi, private val socket: CocinaSocket) : PosRepository {
    private fun nuevaIdempotencia() = "IDEM-${Clock_now()}-${Random.nextLong(1000, 9999)}"
    private fun Clock_now() = kotlin.time.Clock.System.now().toEpochMilliseconds()

    // --- SPRINT 1 ---
    override suspend fun login(codigo: String, pin: String) =
        api.login(codigo, pin).let { Sesion(it.token, it.usuario) }

    override suspend fun mesas() = api.mesas()
    override suspend fun categorias() = api.categorias()
    override suspend fun productos() = api.productos().map { it.dominio() }
    override suspend fun todosProductos() = api.todosProductos().map { it.dominio() }
    override suspend fun crear(b: Borrador) =
        api.crear(CrearPedidoRequest(b.idMesa, b.observaciones, b.lineas.map { it.request() })).dominio()

    override suspend fun pedido(id: Long) = api.pedido(id).dominio()
    override suspend fun pedidosActivosSalon() = api.pedidosActivosSalon().map { it.dominio() }
    override suspend fun pendientesCobro() = api.pendientesCobro().map { it.dominio() }
    override suspend fun pedidoPendienteCobro(idPedido: Long) = api.pedidoPendienteCobro(idPedido).dominio()
    override suspend fun editar(id: Long, lineas: List<Linea>) =
        api.editar(id, EditarPedidoRequest(lineas.map { it.request() })).dominio()

    override suspend fun enviar(id: Long) = api.enviar(id).pedido.dominio()
    override suspend fun trasladar(id: Long, destino: Long, motivo: String) =
        api.trasladar(TrasladoRequest(id, destino, motivo))

    override suspend fun usuarios() = api.usuarios()
    override suspend fun crearUsuario(codigo: String, nombre: String, correo: String, pin: String, rol: String) =
        api.crearUsuario(CrearUsuarioRequest(codigo, nombre, correo.ifBlank { null }, pin, rol))

    override suspend fun activarUsuario(id: Long, activo: Boolean) = api.activar(id, activo)

    // --- SPRINT 2 ---
    override suspend fun cocina() = api.cocina().map { it.dominio() }
    override fun eventosCocina() = socket.eventos()
    override suspend fun marcarPlatoListo(idDetalle: Long) = api.marcarPlatoListo(idDetalle)
    override suspend fun cambiarDisponibilidad(idProducto: Long, disponible: Boolean) =
        api.cambiarDisponibilidad(idProducto, disponible)

    override suspend fun aperturaCaja(montoInicial: Double) =
        api.aperturaCaja(montoInicial).dominio()

    override suspend fun turnoAbierto() = api.turnoAbierto()?.dominio()

    override suspend fun resumenPago(idPedido: Long) = api.resumenPago(idPedido)

    override suspend fun cobrarEfectivo(idPedido: Long, idTurno: Long, recibido: Double, total: Double): CobroResultado {
        val idem = nuevaIdempotencia()
        return api.cobrarEfectivo(CobroEfectivoRequest(idPedido, recibido, idem, idTurno, total, idem)).dominio()
    }

    override suspend fun cobrarDigital(idPedido: Long, idTurno: Long, metodo: String, total: Double, operacion: String, ultimos4: String): CobroResultado {
        val idem = nuevaIdempotencia()
        val met = metodo.uppercase()
        val op = operacion.trim()
        require(op.isNotBlank()) { "Ingresa el número de operación o voucher." }
        val u4 = ultimos4.ifBlank { "0000" }.takeLast(4).padStart(4, '0')
        return api.cobrarDigital(CobroDigitalRequest(idPedido, met, op, u4, idem, met, idTurno, total, idem)).dominio()
    }

    override suspend fun cobrarMixto(idPedido: Long, idTurno: Long, partes: List<Triple<String, Double, String?>>): CobroResultado {
        val idem = nuevaIdempotencia()
        val partesDto = partes.map { (metodo, imp, op) ->
            val met = metodo.uppercase()
            val u4 = if (met.equals("EFECTIVO", ignoreCase = true)) null else (op?.takeLast(4)?.padStart(4, '0') ?: "0000")
            PartePagoDto(met, imp, op?.takeIf { it.isNotBlank() }, u4, met)
        }
        return api.cobrarMixto(CobroMixtoRequest(idPedido, idem, partesDto, idTurno, idem)).dominio()
    }

    override suspend fun cierreCaja(idTurno: Long, efectivoReal: Double, motivo: String) =
        api.cierreCaja(CierreCajaRequest(efectivoReal, motivo, idTurno, efectivoReal, motivo)).dominio()

    override suspend fun simularTarjeta(solicitud: SolicitudSimularTarjeta): RespuestaSimuladorTarjeta {
        return api.simularTarjeta(solicitud)
    }

    override suspend fun emitirComprobanteSunat(solicitud: SolicitudEmitirComprobante): RespuestaComprobanteSunat {
        return api.emitirComprobanteSunat(solicitud)
    }

    // --- SPRINT 3 ---
    override suspend fun buscarCliente(tipo: String, numero: String): ClienteDoc? {
        val res = runCatching { api.buscarCliente(tipo.trim().uppercase(), numero.trim()) }.getOrNull() ?: return null
        return ClienteDoc(res.tipoDocumento, res.numeroDocumento, res.razonSocialFinal, res.direccion)
    }

    override suspend fun guardarCliente(cliente: ClienteDoc) {
        api.guardarCliente(ClienteDto(cliente.tipoDocumento, cliente.numeroDocumento, cliente.razonSocial, cliente.razonSocial, cliente.direccion))
    }

    override suspend fun ticketWhatsApp(idPedido: Long, telefono: String, idTurno: Long) =
        api.ticketWhatsApp(TicketWhatsAppRequest(idPedido, telefono))

    override suspend fun descargarPdfComprobante(idComprobante: Long): ByteArray =
        api.descargarPdfComprobante(idComprobante)

    override suspend fun aplicarDescuento(idPedido: Long, porcentaje: Double, motivo: String) {
        api.aplicarDescuento(idPedido, DescuentoPedidoRequest(porcentaje, motivo))
    }

    override suspend fun anularPedido(idPedido: Long, motivo: String) {
        api.anularPedido(idPedido, AnularPedidoRequest(motivo))
    }

    override suspend fun insumos() = api.insumos().map { it.dominio() }

    override suspend fun crearInsumo(codigo: String, nombre: String, unidad: String, stock: Double, minimo: Double, costo: Double) {
        api.crearInsumo(CrearInsumoRequest(codigo, nombre, unidad, stock, minimo, costo))
    }

    override suspend fun alertasStock() = api.alertasStock().map { it.dominio() }

    override suspend fun guardarReceta(idProducto: Long, ingredientes: List<Ingrediente>) {
        val dtos = ingredientes.map { IngredienteRecetaDto(it.idInsumo, it.cantidadRequerida) }
        api.guardarReceta(idProducto, dtos)
    }

    override suspend fun registrarCompra(proveedor: String, doc: String, items: List<ItemCompraDto>, obs: String) {
        api.registrarCompra(CompraRequest(proveedor, doc, obs.ifBlank { null }, items))
    }

    override suspend fun registrarMerma(idInsumo: Long, cantidad: Double, motivo: String, obs: String) {
        val mot = if (obs.isBlank()) motivo else "$motivo ($obs)"
        api.registrarMerma(MermaRequest(idInsumo, cantidad, mot))
    }

    override suspend fun reporteVentasPorHora() = api.reporteVentasPorHora().map { it.dominio() }
    override suspend fun reportePlatosPopulares() = api.reportePlatosPopulares().map { it.dominio() }
    override suspend fun auditoriaPedidos() = api.auditoriaPedidos().map { it.dominio() }
}
