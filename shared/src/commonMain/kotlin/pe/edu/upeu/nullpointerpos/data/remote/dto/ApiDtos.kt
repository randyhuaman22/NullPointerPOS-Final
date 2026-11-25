// Autor: Randy Huaman (Contratos DTO sincronizados con Spring Cloud Gateway :8080)

package pe.edu.upeu.nullpointerpos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import pe.edu.upeu.nullpointerpos.domain.model.Usuario

@Serializable
data class LoginRequest(val codigoTrabajador: String, val clave: String)

@Serializable
data class LoginResponse(val token: String, val tipoToken: String, val expiraEnSegundos: Long, val usuario: Usuario)

@Serializable
data class ProductoDto(
    val idProducto: Long,
    val nombre: String,
    val descripcion: String? = null,
    val precioUnitario: JsonPrimitive,
    val categoria: String,
    val disponible: Boolean,
    val estacionCocina: String? = null
)

@Serializable
data class DetalleRequest(val idProducto: Long, val cantidad: Int, val observaciones: String)

@Serializable
data class CrearPedidoRequest(val idMesa: Long, val observaciones: String, val detalles: List<DetalleRequest>)

@Serializable
data class EditarPedidoRequest(val detalles: List<DetalleRequest>)

@Serializable
data class TrasladoRequest(val idPedido: Long, val idMesaDestino: Long, val motivo: String)

@Serializable
data class DetalleDto(
    @SerialName("idDetallePedido") val idDetallePedido: Long? = null,
    @SerialName("idDetalle") val idDetalleAlt: Long? = null,
    val idProducto: Long = 0L,
    val nombreProducto: String = "",
    val cantidad: Int = 1,
    val precioUnitario: JsonPrimitive? = null,
    val observaciones: String? = null,
    val estado: String = "PENDIENTE"
) {
    val idDetalle: Long? get() = idDetallePedido ?: idDetalleAlt
}

@Serializable
data class PedidoDto(
    val idPedido: Long,
    val codigoPedido: String = "",
    val idMesa: Long = 0L,
    val codigoMesa: String = "",
    val idUsuarioMozo: Long = 0L,
    val codigoMozo: String? = null,
    val estado: String = "BORRADOR",
    val subtotal: JsonPrimitive? = null,
    val totalDescuento: JsonPrimitive? = null,
    val importeTotal: JsonPrimitive = JsonPrimitive("0.00"),
    val fechaCreacion: String? = null,
    val fechaEnvio: String? = null,
    val fechaPago: String? = null,
    val detalles: List<DetalleDto> = emptyList(),
    val observaciones: String? = null,
    val descuentoTotal: JsonPrimitive? = null,
    val porcentajeDescuento: Double? = null,
    val segundosTranscurridos: Long? = null,
    val minutosTranscurridos: Long? = null,
    val nivelDemora: String? = null,
    val siguienteUmbralMinutos: Int? = null
)

@Serializable
data class EnvioResponse(val mensaje: String, val pedido: PedidoDto)

@Serializable
data class CrearUsuarioRequest(
    val codigoTrabajador: String,
    val nombreCompleto: String,
    val correo: String?,
    val clave: String,
    val rol: String
)

// DTOs de Cocina (Sprint 2)
@Serializable
data class DisponibilidadProductoRequest(val idProducto: Long, val disponible: Boolean, val motivo: String? = null)

// DTOs de Caja (Sprint 2 y 3)
@Serializable
data class AperturaCajaRequest(val montoInicial: Double)

@Serializable
data class TurnoCajaResponse(
    val idTurno: Long,
    val codigoCajero: String? = null,
    val cajero: String? = null,
    val montoInicial: JsonPrimitive = JsonPrimitive("0.00"),
    val fechaApertura: String = "",
    val estado: String = "ABIERTO",
    val fechaCierre: String? = null,
    val montoEsperado: JsonPrimitive? = null,
    val montoReal: JsonPrimitive? = null,
    val diferencia: JsonPrimitive? = null
) {
    val cajeroFinal: String get() = cajero?.ifBlank { null } ?: codigoCajero?.ifBlank { null } ?: "Cajero Wendy's"
}

@Serializable
data class ResumenPagoDto(
    val idPedido: Long,
    val codigoPedido: String,
    val estado: String,
    val importeTotal: JsonPrimitive,
    val totalDescuento: JsonPrimitive? = null
)

@Serializable
data class RespuestaPagoDto(
    val idPago: Long = 0L,
    val idPedido: Long = 0L,
    val idTurno: Long? = null,
    val metodo: String = "",
    val importe: JsonPrimitive = JsonPrimitive("0.00"),
    val importeRecibido: JsonPrimitive? = null,
    val vuelto: JsonPrimitive? = null,
    val estado: String = "",
    val fechaCreacion: String? = null
)

@Serializable
data class CobroEfectivoRequest(
    val idPedido: Long,
    val importeRecibido: Double,
    val idempotencia: String,
    val idTurnoCaja: Long? = null,
    val importeTotal: Double? = null,
    val llaveIdempotencia: String? = null
)

@Serializable
data class CobroDigitalRequest(
    val idPedido: Long,
    val metodo: String,
    val numeroOperacion: String,
    val ultimosCuatroDigitos: String,
    val idempotencia: String,
    val metodoPago: String? = null,
    val idTurnoCaja: Long? = null,
    val importeTotal: Double? = null,
    val llaveIdempotencia: String? = null
)

@Serializable
data class PartePagoDto(
    val metodo: String,
    val importe: Double,
    val numeroOperacion: String? = null,
    val ultimosCuatroDigitos: String? = null,
    val metodoPago: String? = null
)

@Serializable
data class CobroMixtoRequest(
    val idPedido: Long,
    val idempotencia: String,
    val partes: List<PartePagoDto>,
    val idTurnoCaja: Long? = null,
    val llaveIdempotencia: String? = null
)

@Serializable
data class CierreCajaRequest(
    val importeReal: Double,
    val motivo: String = "CIERRE NORMAL",
    val idTurno: Long? = null,
    val montoEfectivoReal: Double? = null,
    val motivoCierre: String? = null
)

@Serializable
data class CierreCajaResponse(
    val idTurno: Long,
    val estado: String = "CERRADO",
    val montoInicial: JsonPrimitive = JsonPrimitive("0.00"),
    val totalEfectivo: JsonPrimitive? = null,
    val totalEfectivoEsperado: JsonPrimitive? = null,
    val totalEfectivoReal: JsonPrimitive? = null,
    val montoEsperado: JsonPrimitive? = null,
    val montoReal: JsonPrimitive? = null,
    val diferencia: JsonPrimitive? = null,
    val fechaCierre: String? = null
)

@Serializable
data class ClienteDto(
    val tipoDocumento: String = "DNI",
    val numeroDocumento: String = "",
    val razonSocial: String? = null,
    val nombreCompleto: String? = null,
    val direccion: String? = null,
    val correo: String? = null,
    val telefono: String? = null
) {
    val razonSocialFinal: String get() = razonSocial?.ifBlank { null } ?: nombreCompleto?.ifBlank { null } ?: "CLIENTE DE PRUEBA"
}

@Serializable
data class TicketWhatsAppRequest(
    val idPedido: Long,
    val telefono: String
)

@Serializable
data class RespuestaTicketWhatsAppDto(
    val idTicket: Long = 0L,
    val estado: String = "PENDIENTE_CONFIGURACION",
    val archivoPdf: String? = null,
    val mensaje: String = "",
    val detalle: String? = null,
    val contenido: String? = null
)

@Serializable
data class DescuentoPedidoRequest(
    val porcentaje: Double,
    val motivo: String
)

@Serializable
data class AnularPedidoRequest(
    val motivo: String
)

// DTOs de Inventario (Sprint 3)
@Serializable
data class InsumoDto(
    @SerialName("id_insumo") val id_insumo: Long? = null,
    @SerialName("idInsumo") val idInsumoAlt: Long? = null,
    val codigo: String? = "",
    val nombre: String = "",
    val unidad: String? = "und",
    @SerialName("stock_actual") val stock_actual: Double? = null,
    @SerialName("stockActual") val stockActualAlt: Double? = null,
    @SerialName("stock_minimo") val stock_minimo: Double? = null,
    @SerialName("stockMinimo") val stockMinimoAlt: Double? = null,
    @SerialName("costo_unitario") val costo_unitario: JsonPrimitive? = null,
    @SerialName("costoUnitario") val costoUnitarioAlt: JsonPrimitive? = null
) {
    val idInsumo: Long get() = id_insumo ?: idInsumoAlt ?: 0L
    val codigoInsumo: String get() = codigo.orEmpty()
    val unidadMedida: String get() = unidad ?: "und"
    val stockActual: Double get() = stock_actual ?: stockActualAlt ?: 0.0
    val stockMinimo: Double get() = stock_minimo ?: stockMinimoAlt ?: 0.0
    val costoUnitario: JsonPrimitive get() = costo_unitario ?: costoUnitarioAlt ?: JsonPrimitive(0.0)
}

@Serializable
data class CrearInsumoRequest(
    val codigo: String,
    val nombre: String,
    val unidad: String,
    val stockInicial: Double,
    val stockMinimo: Double,
    val costoUnitario: Double
)

@Serializable
data class IngredienteRecetaDto(
    val idInsumo: Long,
    val cantidadPorPlato: Double
)

@Serializable
data class ItemCompraDto(
    val idInsumo: Long,
    val cantidad: Double,
    val costoUnitario: Double
)

@Serializable
data class CompraRequest(
    val proveedor: String,
    val documento: String = "",
    val observacion: String? = null,
    val items: List<ItemCompraDto>
)

@Serializable
data class MermaRequest(
    val idInsumo: Long,
    val cantidad: Double,
    val motivo: String
)

// DTOs de Reportes y Auditoria (Sprint 3 - Tolerantes a snake_case)
@Serializable
data class SolicitudSimularTarjeta(
    val numeroTarjeta: String,
    val mesVencimiento: Int,
    val anioVencimiento: Int,
    val cvv: String,
    val titular: String
)

@Serializable
data class RespuestaSimuladorTarjeta(
    val aprobada: Boolean = false,
    val aprobado: Boolean? = null,
    val marca: String? = null,
    val marcaTarjeta: String? = null,
    val ultimos4Digitos: String? = null,
    val ultimosCuatroDigitos: String? = null,
    val codigoAutorizacion: String? = null,
    val numeroOperacion: String? = null,
    val referencia: String? = null,
    val titular: String? = null,
    val mensaje: String = ""
) {
    val aprobadaFinal: Boolean get() = aprobada || (aprobado == true)
    val marcaFinal: String get() = marca?.ifBlank { null } ?: marcaTarjeta?.ifBlank { null } ?: "TARJETA"
    val ultimos4Final: String get() = ultimos4Digitos?.ifBlank { null } ?: ultimosCuatroDigitos?.ifBlank { null } ?: "0000"
    val numeroOperacionFinal: String get() = numeroOperacion?.ifBlank { null } ?: referencia?.ifBlank { null } ?: codigoAutorizacion?.ifBlank { null } ?: ""
}

@Serializable
data class SolicitudEmitirComprobante(
    val idPedido: Long,
    val idPago: Long,
    val modalidad: String = "SIN_DATOS",
    val tipoComprobante: String = "BOLETA",
    val tipoDocumento: String? = null,
    val numeroDocumento: String? = null,
    val nombreCompleto: String? = null,
    val direccion: String? = null,
    val correo: String? = null,
    val telefono: String? = null
)

@Serializable
data class RespuestaComprobanteSunat(
    val idComprobante: Long = 0L,
    val idPedido: Long = 0L,
    val idPago: Long = 0L,
    val tipoComprobante: String = "BOLETA",
    val serie: String = "",
    val correlativo: Long? = null,
    val numero: Long = 0L,
    val numeroCompleto: String = "",
    val numeroComprobante: String = "",
    val modalidad: String = "",
    val rucEmisor: String? = null,
    val razonSocialEmisor: String? = null,
    val tipoDocumentoReceptor: String? = null,
    val numeroDocumentoReceptor: String? = null,
    val denominacionReceptor: String? = null,
    val nombreReceptor: String? = null,
    val direccionReceptor: String? = null,
    val moneda: String? = null,
    val metodoPago: String? = null,
    val estado: String = "EMITIDO_BETA",
    val estadoSunat: String = "",
    val codigoCdr: String? = null,
    val descripcionCdr: String? = null,
    val descripcionSunat: String = "",
    val hashCpe: String? = null,
    val codigoHash: String = "",
    val cadenaQr: String? = null,
    val codigoQr: String = "",
    val totalGravado: Double = 0.0,
    val totalIgv: Double = 0.0,
    val totalImporte: Double = 0.0,
    val fechaEmision: String = "",
    val urlPdf: String? = null,
    val urlXml: String? = null
) {
    val numeroFinal: String get() = numeroCompleto.ifBlank { numeroComprobante.ifBlank { listOf(serie, (correlativo ?: numero.takeIf { it > 0 })?.toString().orEmpty()).filter { it.isNotBlank() }.joinToString("-") } }
    val hashFinal: String get() = hashCpe?.ifBlank { null } ?: codigoHash
    val qrFinal: String get() = cadenaQr?.ifBlank { null } ?: codigoQr
    val descripcionFinal: String get() = descripcionCdr?.ifBlank { null } ?: descripcionSunat.ifBlank { "Aceptado por SUNAT Beta" }
    val estadoFinal: String get() = estadoSunat.ifBlank { estado }
    val receptorFinal: String? get() = denominacionReceptor?.ifBlank { null } ?: nombreReceptor?.ifBlank { null }
}

@Serializable
data class VentaPorHoraDto(
    val hora: Int = 0,
    @SerialName("pedidos") val pedidos: Int? = null,
    @SerialName("cantidadPedidos") val cantidadPedidosAlt: Int? = null,
    @SerialName("total") val total: JsonPrimitive? = null,
    @SerialName("totalVendido") val totalVendidoAlt: JsonPrimitive? = null
) {
    val cantidadPedidos: Int get() = pedidos ?: cantidadPedidosAlt ?: 0
    val totalVendido: JsonPrimitive get() = total ?: totalVendidoAlt ?: JsonPrimitive(0.0)
}

@Serializable
data class PlatoPopularDto(
    @SerialName("id_producto") val id_producto: Long? = null,
    @SerialName("idProducto") val idProductoAlt: Long? = null,
    @SerialName("nombre_producto") val nombre_producto: String? = null,
    @SerialName("nombreProducto") val nombreProductoAlt: String? = null,
    val unidades: Int? = null,
    @SerialName("unidadesVendidas") val unidadesVendidasAlt: Int? = null,
    val venta: JsonPrimitive? = null,
    @SerialName("totalVendido") val totalVendidoAlt: JsonPrimitive? = null
) {
    val idProducto: Long get() = id_producto ?: idProductoAlt ?: 0L
    val nombreProducto: String get() = nombre_producto ?: nombreProductoAlt ?: "Plato"
    val unidadesVendidas: Int get() = unidades ?: unidadesVendidasAlt ?: 0
    val totalVendido: JsonPrimitive get() = venta ?: totalVendidoAlt ?: JsonPrimitive(0.0)
}

@Serializable
data class AuditoriaPedidoDto(
    @SerialName("id_registro_auditoria") val id_registro_auditoria: Long? = null,
    @SerialName("idAuditoria") val idAuditoriaAlt: Long? = null,
    @SerialName("nombre_entidad") val nombre_entidad: String? = null,
    @SerialName("id_entidad") val id_entidad: JsonPrimitive? = null,
    @SerialName("idPedido") val idPedidoAlt: Long? = null,
    val accion: String = "",
    @SerialName("id_usuario_actor") val id_usuario_actor: JsonPrimitive? = null,
    val usuario: String? = null,
    val motivo: String? = null,
    @SerialName("fecha_creacion") val fecha_creacion: String? = null,
    @SerialName("fechaHora") val fechaHoraAlt: String? = null
) {
    val idAuditoria: Long get() = id_registro_auditoria ?: idAuditoriaAlt ?: 0L
    val idPedido: Long get() = id_entidad?.content?.toLongOrNull() ?: idPedidoAlt ?: 0L
    val motivoFinal: String get() = motivo.orEmpty()
    val usuarioActor: String get() = usuario ?: id_usuario_actor?.content ?: "Sistema"
    val fechaHora: String get() = fecha_creacion ?: fechaHoraAlt ?: ""
}
