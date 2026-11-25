// Autor: Randy Huaman (Actualizado con mapeadores robustos para todos los microservicios)

package pe.edu.upeu.nullpointerpos.data.mapper

import kotlinx.serialization.json.JsonPrimitive
import pe.edu.upeu.nullpointerpos.domain.model.*
import pe.edu.upeu.nullpointerpos.data.remote.dto.*

fun centimos(decimal: String): Long {
    val normal = decimal.trim()
    val num = normal.toDoubleOrNull() ?: 0.0
    return (num * 100.0 + 0.5).toLong()
}

fun ProductoDto.dominio() = Producto(
    idProducto = idProducto,
    nombre = nombre,
    descripcion = descripcion,
    precioCentimos = centimos(precioUnitario.content),
    categoria = categoria,
    disponible = disponible,
    estacion = estacionCocina
)

fun PedidoDto.dominio() = Pedido(
    idPedido = idPedido,
    codigoPedido = codigoPedido,
    idMesa = idMesa,
    codigoMesa = codigoMesa,
    codigoMozo = codigoMozo.orEmpty(),
    estado = estado,
    importeCentimos = centimos(importeTotal.content),
    fechaEnvio = fechaEnvio,
    lineas = detalles.map {
        Linea(
            idProducto = it.idProducto,
            nombre = it.nombreProducto,
            precioCentimos = centimos(it.precioUnitario?.content ?: "0.0"),
            cantidad = it.cantidad,
            observaciones = it.observaciones.orEmpty(),
            idDetalle = it.idDetalle,
            estado = it.estado
        )
    },
    observaciones = observaciones,
    descuentoCentimos = descuentoTotal?.let { centimos(it.content) } ?: 0L,
    porcentajeDescuento = porcentajeDescuento ?: 0.0
)

fun Linea.request() = DetalleRequest(idProducto, cantidad, observaciones)

fun TurnoCajaResponse.dominio() = TurnoCaja(
    idTurno = idTurno,
    cajero = cajeroFinal,
    montoInicialCentimos = centimos(montoInicial.content),
    fechaApertura = fechaApertura,
    estado = estado
)

fun CierreCajaResponse.dominio() = CierreCajaInfo(
    idTurno = idTurno,
    montoInicialCentimos = centimos(montoInicial.content),
    totalEfectivoEsperadoCentimos = centimos((montoEsperado ?: totalEfectivoEsperado ?: totalEfectivo ?: JsonPrimitive("0.00")).content),
    totalEfectivoRealCentimos = centimos((montoReal ?: totalEfectivoReal ?: JsonPrimitive("0.00")).content),
    diferenciaCentimos = centimos((diferencia ?: JsonPrimitive("0.00")).content),
    fechaCierre = fechaCierre ?: ""
)

fun InsumoDto.dominio() = Insumo(
    idInsumo = idInsumo,
    codigoInsumo = codigoInsumo,
    nombre = nombre,
    unidadMedida = unidadMedida,
    stockActual = stockActual,
    stockMinimo = stockMinimo,
    costoUnitarioCentimos = centimos(costoUnitario.content)
)

fun VentaPorHoraDto.dominio() = VentaPorHora(
    hora = hora,
    cantidadPedidos = cantidadPedidos,
    totalVendidoCentimos = centimos(totalVendido.content)
)

fun PlatoPopularDto.dominio() = PlatoPopular(
    idProducto = idProducto,
    nombreProducto = nombreProducto,
    unidadesVendidas = unidadesVendidas,
    totalVendidoCentimos = centimos(totalVendido.content)
)

fun AuditoriaPedidoDto.dominio() = AuditoriaRegistro(
    idAuditoria = idAuditoria,
    idPedido = idPedido,
    accion = accion,
    usuario = usuarioActor,
    motivo = motivo,
    fechaHora = fechaHora
)
