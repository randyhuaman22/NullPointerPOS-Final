// Autor: Randy Huaman
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package pe.edu.upeu.nullpointerpos

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import pe.edu.upeu.nullpointerpos.data.local.LocalStore
import pe.edu.upeu.nullpointerpos.data.remote.CocinaSocket
import pe.edu.upeu.nullpointerpos.data.remote.PosApi
import pe.edu.upeu.nullpointerpos.data.remote.SessionContext
import pe.edu.upeu.nullpointerpos.data.remote.dto.ItemCompraDto
import pe.edu.upeu.nullpointerpos.data.remote.dto.RespuestaComprobanteSunat
import pe.edu.upeu.nullpointerpos.data.remote.dto.RespuestaSimuladorTarjeta
import pe.edu.upeu.nullpointerpos.data.remote.dto.RespuestaTicketWhatsAppDto
import pe.edu.upeu.nullpointerpos.data.remote.dto.ResumenPagoDto
import pe.edu.upeu.nullpointerpos.data.remote.dto.SolicitudEmitirComprobante
import pe.edu.upeu.nullpointerpos.data.remote.dto.SolicitudSimularTarjeta
import pe.edu.upeu.nullpointerpos.domain.model.*
import pe.edu.upeu.nullpointerpos.domain.repository.PosRepository
import pe.edu.upeu.nullpointerpos.platform.Preferencias
import pe.edu.upeu.nullpointerpos.presentation.PosViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class FakePosRepo : PosRepository {
    val eventosWs = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    var sesionActual = Sesion("jwt", Usuario(4L, "COC001", "Chef", "COCINERO"))
    var pedidosCocinaActual: List<Pedido> = emptyList()
    var mesasActual: List<Mesa> = emptyList()
    var pedidosSalonActual: List<Pedido> = emptyList()

    override suspend fun login(codigo: String, pin: String) = sesionActual
    override suspend fun mesas() = mesasActual
    override suspend fun categorias() = emptyList<Categoria>()
    override suspend fun productos() = emptyList<Producto>()
    override suspend fun todosProductos() = emptyList<Producto>()
    override suspend fun crear(b: Borrador) = error("no usado")
    override suspend fun sincronizarPedidoOffline(
        idMesa: Long,
        observaciones: String,
        lineas: List<Linea>,
        idempotencia: String,
        enviarACocina: Boolean,
        fechaRegistroOffline: String?
    ) = error("no usado")
    override suspend fun pedido(id: Long) = error("no usado")
    override suspend fun pedidosActivosSalon() = pedidosSalonActual
    override suspend fun pendientesCobro() = emptyList<Pedido>()
    override suspend fun pedidoPendienteCobro(idPedido: Long) = error("no usado")
    override suspend fun editar(id: Long, lineas: List<Linea>) = error("no usado")
    override suspend fun enviar(id: Long) = error("no usado")
    override suspend fun trasladar(id: Long, destino: Long, motivo: String) {}
    override suspend fun usuarios() = emptyList<Cuenta>()
    override suspend fun crearUsuario(codigo: String, nombre: String, correo: String, pin: String, rol: String) {}
    override suspend fun activarUsuario(id: Long, activo: Boolean) {}
    override suspend fun cocina() = pedidosCocinaActual
    override fun eventosCocina(): Flow<Unit> = eventosWs
    override suspend fun marcarPlatoListo(idDetalle: Long) {}
    override suspend fun marcarPedidoListoCocina(idPedido: Long) = error("no usado")
    override suspend fun cambiarDisponibilidad(idProducto: Long, disponible: Boolean) {}
    override suspend fun aperturaCaja(montoInicial: Double) = error("no usado")
    override suspend fun turnoAbierto(): TurnoCaja? = null
    override suspend fun resumenPago(idPedido: Long): ResumenPagoDto = error("no usado")
    override suspend fun cobrarEfectivo(idPedido: Long, idTurno: Long, recibido: Double, total: Double) = error("no usado")
    override suspend fun cobrarDigital(idPedido: Long, idTurno: Long, metodo: String, total: Double, operacion: String, ultimos4: String) = error("no usado")
    override suspend fun cobrarMixto(idPedido: Long, idTurno: Long, partes: List<Triple<String, Double, String?>>) = error("no usado")
    override suspend fun cierreCaja(idTurno: Long, efectivoReal: Double, motivo: String) = error("no usado")
    override suspend fun simularTarjeta(solicitud: SolicitudSimularTarjeta): RespuestaSimuladorTarjeta = error("no usado")
    override suspend fun emitirComprobanteSunat(solicitud: SolicitudEmitirComprobante): RespuestaComprobanteSunat = error("no usado")
    override suspend fun buscarCliente(tipo: String, numero: String): ClienteDoc? = null
    override suspend fun guardarCliente(cliente: ClienteDoc) {}
    override suspend fun ticketWhatsApp(idPedido: Long, telefono: String, idTurno: Long): RespuestaTicketWhatsAppDto = error("no usado")
    override suspend fun descargarPdfComprobante(idComprobante: Long) = byteArrayOf()
    override suspend fun aplicarDescuento(idPedido: Long, porcentaje: Double, motivo: String) {}
    override suspend fun anularPedido(idPedido: Long, motivo: String) {}
    override suspend fun insumos() = emptyList<Insumo>()
    override suspend fun crearInsumo(codigo: String, nombre: String, unidad: String, stock: Double, minimo: Double, costo: Double) {}
    override suspend fun alertasStock() = emptyList<Insumo>()
    override suspend fun guardarReceta(idProducto: Long, ingredientes: List<Ingrediente>) {}
    override suspend fun registrarCompra(proveedor: String, doc: String, items: List<ItemCompraDto>, obs: String) {}
    override suspend fun registrarMerma(idInsumo: Long, cantidad: Double, motivo: String, obs: String) {}
    override suspend fun reporteVentasPorHora() = emptyList<VentaPorHora>()
    override suspend fun reportePlatosPopulares() = emptyList<PlatoPopular>()
    override suspend fun auditoriaPedidos() = emptyList<AuditoriaRegistro>()
}

class NotificacionPedidosTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun crearVm(repo: FakePosRepo): PosViewModel {
        val prefs = object : Preferencias {
            val data = mutableMapOf<String, String>()
            override fun leer(clave: String) = data[clave]
            override fun guardar(clave: String, valor: String) { data[clave] = valor }
        }
        val store = LocalStore(prefs).apply { servidor = "http://localhost:8080" }
        val session = SessionContext()
        val dummyClient = HttpClient(MockEngine { respondOk("[]") })
        val socket = CocinaSocket(PosApi(dummyClient, store, session))
        return PosViewModel(repo, store, session, socket)
    }

    @Test fun cocineroDetectaNuevoPedidoEnTiempoRealSinRepetir() = runTest(testDispatcher) {
        val p1 = Pedido(10L, "NP-010", 1L, "M01", "MOZ001", "ENVIADO_COCINA", 3000L, null, emptyList())
        val p2 = Pedido(11L, "NP-011", 2L, "M02", "MOZ001", "ENVIADO_COCINA", 4500L, null, listOf(Linea(1L, "1/2 Pollo", 4500L, 1)))
        val repo = FakePosRepo().apply {
            sesionActual = Sesion("jwt", Usuario(4L, "COC001", "Chef", "COCINERO"))
            pedidosCocinaActual = listOf(p1)
        }
        val vm = crearVm(repo)
        try {
            vm.login("COC001", "1234")
            assertNull(vm.state.value.nuevoPedidoCocinaAviso)

            repo.pedidosCocinaActual = listOf(p1, p2)
            repo.eventosWs.emit(Unit)

            assertEquals(11L, vm.state.value.nuevoPedidoCocinaAviso?.idPedido)
            assertEquals("M02", vm.state.value.nuevoPedidoCocinaAviso?.codigoMesa)

            vm.cerrarNotificacionCocina()
            assertNull(vm.state.value.nuevoPedidoCocinaAviso)

            repo.eventosWs.emit(Unit)
            assertNull(vm.state.value.nuevoPedidoCocinaAviso)
        } finally {
            vm.cerrarSesion()
        }
    }

    @Test fun mozoDetectaPedidoListoEnTiempoRealSinDuplicar() = runTest(testDispatcher) {
        val mesa = Mesa(3L, "M03", "SALON", 4, "OCUPADA")
        val enCocina = Pedido(25L, "NP-025", 3L, "M03", "MOZ001", "ENVIADO_COCINA", 5000L, null, emptyList())
        val listo = enCocina.copy(estado = "LISTO", lineas = listOf(Linea(1L, "Pollo Entero", 5000L, 1, estado = "LISTO")))
        val repo = FakePosRepo().apply {
            sesionActual = Sesion("jwt", Usuario(2L, "MOZ001", "Mozo", "MOZO"))
            mesasActual = listOf(mesa)
            pedidosSalonActual = listOf(enCocina)
        }
        val vm = crearVm(repo)
        try {
            vm.login("MOZ001", "1234")
            assertNull(vm.state.value.pedidoListoAviso)

            repo.pedidosSalonActual = listOf(listo)
            repo.eventosWs.emit(Unit)

            assertEquals(25L, vm.state.value.pedidoListoAviso?.idPedido)
            assertEquals("M03", vm.state.value.pedidoListoAviso?.codigoMesa)

            vm.cerrarNotificacionPedidoListo()
            assertNull(vm.state.value.pedidoListoAviso)

            repo.eventosWs.emit(Unit)
            assertNull(vm.state.value.pedidoListoAviso)
        } finally {
            vm.cerrarSesion()
        }
    }
}
