// Autor: Randy Huaman (Modelos para Sprints 1, 2 y 3 con interoperabilidad completa)

package pe.edu.upeu.nullpointerpos.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Usuario(
    val idUsuario: Long,
    val codigoTrabajador: String,
    val nombreCompleto: String,
    val rol: String
)

@Serializable
data class Sesion(
    val token: String,
    val usuario: Usuario
)

@Serializable
data class Mesa(
    val idMesa: Long,
    val codigoMesa: String,
    val nombreArea: String,
    val capacidad: Int,
    val estado: String,
    val activo: Boolean = true
)

@Serializable
data class Categoria(
    val idCategoria: Long,
    val nombre: String,
    val descripcion: String? = null,
    val ordenVisualizacion: Int = 0
)

@Serializable
data class Producto(
    val idProducto: Long,
    val nombre: String,
    val descripcion: String? = null,
    val precioCentimos: Long,
    val categoria: String,
    val disponible: Boolean,
    val estacion: String? = null
)

@Serializable
data class Linea(
    val idProducto: Long,
    val nombre: String,
    val precioCentimos: Long,
    val cantidad: Int = 1,
    val observaciones: String = "",
    val idDetalle: Long? = null,
    val estado: String = "PENDIENTE"
)

@Serializable
data class Pedido(
    val idPedido: Long,
    val codigoPedido: String,
    val idMesa: Long,
    val codigoMesa: String,
    val codigoMozo: String,
    val estado: String,
    val importeCentimos: Long,
    val fechaEnvio: String? = null,
    val lineas: List<Linea>,
    val observaciones: String? = null,
    val descuentoCentimos: Long = 0L,
    val porcentajeDescuento: Double = 0.0,
    val fechaCreacion: String? = null,
    val fechaPago: String? = null,
    val segundosTranscurridos: Long? = null,
    val minutosTranscurridos: Long? = null,
    val nivelDemora: String? = null,
    val siguienteUmbralMinutos: Int? = null
) {
    val descuentoPorcentaje: Double get() = porcentajeDescuento
    val descuentoMotivo: String? get() = observaciones
}

@Serializable
data class Cuenta(
    val idUsuario: Long,
    val codigoTrabajador: String,
    val nombreCompleto: String,
    val correo: String? = null,
    val rol: String,
    val activo: Boolean
)

@Serializable
data class Borrador(
    val idMesa: Long,
    val lineas: List<Linea> = emptyList(),
    val observaciones: String = "",
    val idPedido: Long? = null,
    val creacionIncierta: Boolean = false,
    val llaveIdempotencia: String = ""
)

// Modelos de Caja (Sprint 2 y 3)
@Serializable
data class TurnoCaja(
    val idTurno: Long,
    val cajero: String,
    val montoInicialCentimos: Long,
    val fechaApertura: String,
    val estado: String
) {
    val montoApertura: Double get() = montoInicialCentimos / 100.0
    val ventasTotales: Double get() = 0.0
}

@Serializable
data class ClienteDoc(
    val tipoDocumento: String,
    val numeroDocumento: String,
    val razonSocial: String,
    val direccion: String? = null,
    val condicion: String = "",
    val estadoSunat: String = "",
    val origenDatos: String = "",
    val mensaje: String = ""
) {
    val nombreCompleto: String get() = razonSocial
}

@Serializable
data class CierreCajaInfo(
    val idTurno: Long,
    val montoInicialCentimos: Long,
    val totalEfectivoEsperadoCentimos: Long,
    val totalEfectivoRealCentimos: Long,
    val diferenciaCentimos: Long,
    val fechaCierre: String
)

@Serializable
data class CobroResultado(
    val idPago: Long,
    val idPedido: Long,
    val metodo: String,
    val importeCentimos: Long,
    val importeRecibidoCentimos: Long,
    val vueltoCentimos: Long,
    val estado: String
)

// Modelos de Inventario (Sprint 3)
@Serializable
data class Insumo(
    val idInsumo: Long,
    val codigoInsumo: String,
    val nombre: String,
    val unidadMedida: String,
    val stockActual: Double,
    val stockMinimo: Double,
    val costoUnitarioCentimos: Long
) {
    val codigo: String get() = codigoInsumo
    val costoUnitario: Double get() = costoUnitarioCentimos / 100.0
}

@Serializable
data class Ingrediente(
    val idInsumo: Long,
    val nombreInsumo: String,
    val cantidadRequerida: Double,
    val unidadMedida: String
)

@Serializable
data class Receta(
    val idProducto: Long,
    val ingredientes: List<Ingrediente>
)

// Modelos de Reportes (Sprint 3)
@Serializable
data class VentaPorHora(
    val hora: Int,
    val cantidadPedidos: Int,
    val totalVendidoCentimos: Long
) {
    val totalVentas: Double get() = totalVendidoCentimos / 100.0
}

@Serializable
data class PlatoPopular(
    val idProducto: Long,
    val nombreProducto: String,
    val unidadesVendidas: Int,
    val totalVendidoCentimos: Long
) {
    val nombrePlato: String get() = nombreProducto
    val cantidadVendida: Int get() = unidadesVendidas
    val totalRecaudado: Double get() = totalVendidoCentimos / 100.0
}

@Serializable
data class AuditoriaRegistro(
    val idAuditoria: Long,
    val idPedido: Long,
    val accion: String,
    val usuario: String,
    val motivo: String,
    val fechaHora: String,
    val nombreEntidad: String = "PEDIDO"
) {
    val fecha: String get() = fechaHora
    val codigoPedido: String
        get() = if (nombreEntidad.equals("PEDIDO", ignoreCase = true) && idPedido > 0) {
            "PED-$idPedido"
        } else if (idPedido > 0) {
            "$nombreEntidad #$idPedido"
        } else {
            nombreEntidad
        }
}

@Serializable
enum class TipoAccionSync {
    ENVIAR_PEDIDO_MOZO,
    MARCAR_PLATO_LISTO,
    MARCAR_PEDIDO_LISTO,
    CAMBIAR_DISPONIBILIDAD,
    COBRAR_PEDIDO
}

@Serializable
data class ParteCobroSync(
    val metodo: String,
    val importe: Double,
    val operacion: String? = null
)

@Serializable
data class AccionPendienteSync(
    val idLocal: String,
    val tipo: TipoAccionSync,
    val llaveIdempotencia: String,
    val descripcion: String,
    val idMesa: Long? = null,
    val codigoMesa: String = "",
    val idPedidoLocal: Long? = null,
    val idPedido: Long? = null,
    val idDetalle: Long? = null,
    val idProducto: Long? = null,
    val disponible: Boolean? = null,
    val observaciones: String = "",
    val lineas: List<Linea> = emptyList(),
    val enviarACocina: Boolean = true,
    val fechaRegistroOffline: String? = null,
    val idTurnoCaja: Long? = null,
    val metodoPago: String? = null,
    val recibidoEfectivo: Double = 0.0,
    val totalPedido: Double = 0.0,
    val operacionDigital: String = "",
    val ultimos4Digital: String = "",
    val partesMixto: List<ParteCobroSync> = emptyList(),
    val emitirSunat: Boolean = false,
    val tipoComprobante: String = "BOLETA",
    val tipoDoc: String = "Sin Doc",
    val numDoc: String = "",
    val razonSocial: String = "",
    val direccion: String = "",
    val telefonoWhatsapp: String? = null,
    val enviarWhatsapp: Boolean = false,
    val ultimoError: String? = null
)

@Serializable
data class SnapshotOffline(
    val sesion: Sesion? = null,
    val mesas: List<Mesa> = emptyList(),
    val categorias: List<Categoria> = emptyList(),
    val productos: List<Producto> = emptyList(),
    val productosTodos: List<Producto> = emptyList(),
    val pedidosSalon: List<Pedido> = emptyList(),
    val cocina: List<Pedido> = emptyList(),
    val pendientesCobro: List<Pedido> = emptyList(),
    val turnoCaja: TurnoCaja? = null
)

fun dinero(centimos: Long): String {
    val signo = if (centimos < 0) "-" else ""
    return "S/ $signo${kotlin.math.abs(centimos / 100)}.${kotlin.math.abs(centimos % 100).toString().padStart(2, '0')}"
}
fun List<Linea>.total(): Long = sumOf { it.precioCentimos * it.cantidad }
