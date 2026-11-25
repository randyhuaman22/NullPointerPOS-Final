// Autor: Randy Huaman (Actualizado con funciones para Sprints 1, 2 y 3)

package pe.edu.upeu.nullpointerpos.domain.repository

import kotlinx.coroutines.flow.Flow
import pe.edu.upeu.nullpointerpos.data.remote.dto.ItemCompraDto
import pe.edu.upeu.nullpointerpos.domain.model.*

class FalloApi(val codigo: Int, override val message: String) : Exception(message)

interface PosRepository {
    // Sprint 1
    suspend fun login(codigo: String, pin: String): Sesion
    suspend fun mesas(): List<Mesa>
    suspend fun categorias(): List<Categoria>
    suspend fun productos(): List<Producto>
    suspend fun todosProductos(): List<Producto>
    suspend fun crear(b: Borrador): Pedido
    suspend fun pedido(id: Long): Pedido
    suspend fun pedidosActivosSalon(): List<Pedido>
    suspend fun pendientesCobro(): List<Pedido>
    suspend fun pedidoPendienteCobro(idPedido: Long): Pedido
    suspend fun editar(id: Long, lineas: List<Linea>): Pedido
    suspend fun enviar(id: Long): Pedido
    suspend fun trasladar(id: Long, destino: Long, motivo: String)
    suspend fun usuarios(): List<Cuenta>
    suspend fun crearUsuario(codigo: String, nombre: String, correo: String, pin: String, rol: String)
    suspend fun activarUsuario(id: Long, activo: Boolean)

    // Sprint 2 - Cocina KDS
    suspend fun cocina(): List<Pedido>
    fun eventosCocina(): Flow<Unit>
    suspend fun marcarPlatoListo(idDetalle: Long)
    suspend fun cambiarDisponibilidad(idProducto: Long, disponible: Boolean)

    // Sprint 2 - Caja & Cobros
    suspend fun aperturaCaja(montoInicial: Double): TurnoCaja
    suspend fun turnoAbierto(): TurnoCaja?
    suspend fun resumenPago(idPedido: Long): pe.edu.upeu.nullpointerpos.data.remote.dto.ResumenPagoDto
    suspend fun cobrarEfectivo(idPedido: Long, idTurno: Long, recibido: Double, total: Double): CobroResultado
    suspend fun cobrarDigital(idPedido: Long, idTurno: Long, metodo: String, total: Double, operacion: String, ultimos4: String): CobroResultado
    suspend fun cobrarMixto(idPedido: Long, idTurno: Long, partes: List<Triple<String, Double, String?>>): CobroResultado
    suspend fun cierreCaja(idTurno: Long, efectivoReal: Double, motivo: String): CierreCajaInfo
    suspend fun simularTarjeta(solicitud: pe.edu.upeu.nullpointerpos.data.remote.dto.SolicitudSimularTarjeta): pe.edu.upeu.nullpointerpos.data.remote.dto.RespuestaSimuladorTarjeta
    suspend fun emitirComprobanteSunat(solicitud: pe.edu.upeu.nullpointerpos.data.remote.dto.SolicitudEmitirComprobante): pe.edu.upeu.nullpointerpos.data.remote.dto.RespuestaComprobanteSunat

    // Sprint 3 - Facturación, Descuentos & WhatsApp
    suspend fun buscarCliente(tipo: String, numero: String): ClienteDoc?
    suspend fun guardarCliente(cliente: ClienteDoc)
    suspend fun ticketWhatsApp(idPedido: Long, telefono: String, idTurno: Long): pe.edu.upeu.nullpointerpos.data.remote.dto.RespuestaTicketWhatsAppDto
    suspend fun descargarPdfComprobante(idComprobante: Long): ByteArray
    suspend fun aplicarDescuento(idPedido: Long, porcentaje: Double, motivo: String)
    suspend fun anularPedido(idPedido: Long, motivo: String)

    // Sprint 3 - Inventario & Recetas
    suspend fun insumos(): List<Insumo>
    suspend fun crearInsumo(codigo: String, nombre: String, unidad: String, stock: Double, minimo: Double, costo: Double)
    suspend fun alertasStock(): List<Insumo>
    suspend fun guardarReceta(idProducto: Long, ingredientes: List<Ingrediente>)
    suspend fun registrarCompra(proveedor: String, doc: String, items: List<ItemCompraDto>, obs: String)
    suspend fun registrarMerma(idInsumo: Long, cantidad: Double, motivo: String, obs: String)

    // Sprint 3 - Reportes & Auditoría
    suspend fun reporteVentasPorHora(): List<VentaPorHora>
    suspend fun reportePlatosPopulares(): List<PlatoPopular>
    suspend fun auditoriaPedidos(): List<AuditoriaRegistro>
}
