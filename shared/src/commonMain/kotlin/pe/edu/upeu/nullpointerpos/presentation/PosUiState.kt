// Autor: Randy Huaman (Actualizado con estado unificado para todos los módulos)

package pe.edu.upeu.nullpointerpos.presentation

import pe.edu.upeu.nullpointerpos.domain.model.*

data class PosUiState(
    val servidor: String = "",
    val sesion: Sesion? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val mensaje: String? = null,
    val pagina: String = "Salón",
    val modoOscuro: Boolean = false,
    val pendientesEmision: List<pe.edu.upeu.nullpointerpos.data.remote.dto.SolicitudEmitirComprobante> = emptyList(),

    // Salón y Menú
    val mesas: List<Mesa> = emptyList(),
    val categorias: List<Categoria> = emptyList(),
    val productos: List<Producto> = emptyList(),
    val productosTodos: List<Producto> = emptyList(),
    val pedidosSalon: List<Pedido> = emptyList(),
    val borradores: List<Borrador> = emptyList(),
    val mesa: Mesa? = null,
    val borrador: Borrador? = null,
    val pedido: Pedido? = null,
    val modoSoloLecturaAviso: String? = null,

    // Cocina KDS
    val cocina: List<Pedido> = emptyList(),
    val cocinaAviso: String? = null,
    val socketConectado: Boolean = false,
    val cocinaActualizada: Long? = null,
    val nuevoPedidoCocinaAviso: Pedido? = null,

    // Caja y Cobros (Sprint 2 y 3)
    val pendientesCobro: List<Pedido> = emptyList(),
    val turnoCaja: TurnoCaja? = null,
    val cierreInfo: CierreCajaInfo? = null,
    val clienteSeleccionado: ClienteDoc? = null,
    val buscandoCliente: Boolean = false,
    val clienteAviso: String? = null,
    val transaccionTarjeta: pe.edu.upeu.nullpointerpos.data.remote.dto.RespuestaSimuladorTarjeta? = null,
    val comprobanteEmitido: pe.edu.upeu.nullpointerpos.data.remote.dto.RespuestaComprobanteSunat? = null,
    val whatsappResultado: pe.edu.upeu.nullpointerpos.data.remote.dto.RespuestaTicketWhatsAppDto? = null,

    // Inventario y Almacén (Sprint 3)
    val insumos: List<Insumo> = emptyList(),
    val alertasStock: List<Insumo> = emptyList(),

    // Administración, Auditoría y Reportes (Sprint 1 y 3)
    val usuarios: List<Cuenta> = emptyList(),
    val ventasPorHora: List<VentaPorHora> = emptyList(),
    val platosPopulares: List<PlatoPopular> = emptyList(),
    val auditoria: List<AuditoriaRegistro> = emptyList()
) {
    val rol get() = sesion?.usuario?.rol.orEmpty()
    val puedeMesas get() = rol in listOf("MOZO", "ADMINISTRADOR")
    val puedeCocina get() = rol in listOf("COCINERO", "ADMINISTRADOR")
    val puedeCaja get() = rol in listOf("CAJERO", "ADMINISTRADOR")
    val puedeAlmacen get() = rol in listOf("ALMACENERO", "ADMINISTRADOR")
    val puedeReportes get() = rol == "ADMINISTRADOR"
    val puedeAdmin get() = rol == "ADMINISTRADOR"

    val editable get() = borrador != null && (pedido == null || pedido.estado == "BORRADOR") &&
            !borrador.creacionIncierta &&
            (pedido == null ||
                sesion?.usuario?.rol?.uppercase() == "ADMINISTRADOR" ||
                pedido.codigoMozo.equals(sesion?.usuario?.codigoTrabajador, ignoreCase = true)) &&
            (mesa?.estado == "LIBRE" || borrador.idPedido != null)
}
