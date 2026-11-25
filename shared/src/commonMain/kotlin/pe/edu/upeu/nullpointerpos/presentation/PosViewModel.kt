// Autor: Randy Huaman (PosViewModel unificado para Sprints 1, 2 y 3 con control de roles estricto)

package pe.edu.upeu.nullpointerpos.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import pe.edu.upeu.nullpointerpos.data.local.LocalStore
import pe.edu.upeu.nullpointerpos.data.remote.CocinaSocket
import pe.edu.upeu.nullpointerpos.data.remote.SessionContext
import pe.edu.upeu.nullpointerpos.domain.model.*
import pe.edu.upeu.nullpointerpos.domain.repository.FalloApi
import pe.edu.upeu.nullpointerpos.domain.repository.PosRepository
import pe.edu.upeu.nullpointerpos.platform.avisarPedido

class PosViewModel(
    private val repo: PosRepository,
    private val store: LocalStore,
    private val session: SessionContext,
    private val socket: CocinaSocket
) : ViewModel() {

    private val _state = MutableStateFlow(PosUiState(servidor = store.servidor, modoOscuro = store.modoOscuro))
    val state = _state.asStateFlow()

    fun toggleModoOscuro() {
        val nuevo = !_state.value.modoOscuro
        store.modoOscuro = nuevo
        _state.update { it.copy(modoOscuro = nuevo) }
    }

    private var kitchenJob: Job? = null
    private val kitchenLock = Mutex()
    private var cocinaIniciada = false
    private var idsCocina = emptySet<Long>()
    private val idsNotificados = mutableSetOf<Long>()

    private fun task(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, mensaje = null) }
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { fallo(e) }
            finally { _state.update { it.copy(busy = false) } }
        }
    }

    private fun fallo(e: Exception) {
        val msg = e.message.orEmpty()
        if (e is FalloApi && e.codigo == 401 && _state.value.sesion != null && msg.contains("venc", ignoreCase = true)) {
            cerrarSesion()
            _state.update { it.copy(error = "Tu sesión venció. Vuelve a ingresar.") }
        } else {
            _state.update {
                it.copy(error = if (e is FalloApi || e is IllegalArgumentException) e.message else "Aviso: ${e.message ?: "Verifica la conexión"}")
            }
        }
    }

    fun limpiarAvisos() { _state.update { it.copy(error = null, mensaje = null) } }

    fun configurarServidor(value: String) {
        if (_state.value.sesion != null || _state.value.busy) return
        runCatching {
            var raw = value.trim().trimEnd('/')
            if (!raw.startsWith("http://") && !raw.startsWith("https://")) {
                raw = "http://$raw"
            }
            val url = Url(raw)
            require(url.protocol.name in listOf("http", "https") && url.host.isNotBlank()) {
                "Usa el formato: http://192.168.1.6:8080"
            }
            store.servidor = raw
            _state.update { it.copy(servidor = raw, error = null, mensaje = "Servidor guardado: $raw") }
        }.onFailure { _state.update { s -> s.copy(error = it.message ?: "Dirección no válida") } }
    }

    fun login(codigo: String, pin: String) = task {
        require(codigo.trim().isNotBlank() && pin.length >= 4) { "Ingresa tu código y contraseña / PIN de 4 dígitos." }
        val sesion = repo.login(codigo.trim().uppercase(), pin.trim())
        session.token = sesion.token
        val rol = sesion.usuario.rol
        val destino = when (rol) {
            "MOZO" -> "Salón"
            "COCINERO" -> "Cocina"
            "CAJERO" -> "Caja"
            "ALMACENERO" -> "Almacén"
            "ADMINISTRADOR" -> "Salón"
            else -> "Cuenta"
        }
        _state.value = PosUiState(
            servidor = store.servidor,
            sesion = sesion,
            busy = true,
            pagina = destino,
            modoOscuro = store.modoOscuro,
            borradores = store.borradores(sesion.usuario.codigoTrabajador)
        )
        if (_state.value.puedeCocina) iniciarCocina()
        if (_state.value.puedeMesas) cargarSalon()
        if (_state.value.puedeCaja) cargarCajaDatos()
        if (_state.value.puedeAlmacen) cargarInventario()
        if (_state.value.puedeReportes) cargarReportes()
    }

    fun cerrarSesion() {
        kitchenJob?.cancel(); kitchenJob = null; session.token = null
        cocinaIniciada = false; idsCocina = emptySet(); idsNotificados.clear()
        _state.value = PosUiState(servidor = store.servidor, modoOscuro = store.modoOscuro)
    }

    private suspend fun cargarSalon() {
        runCatching {
            val mesas = repo.mesas()
            val categorias = repo.categorias()
            val productos = repo.productos()
            val productosTodos = runCatching { repo.todosProductos() }.getOrDefault(productos)
            val pedidosSalonRes = runCatching { repo.pedidosActivosSalon() }
            val sActual = _state.value
            val pedidosSalon = pedidosSalonRes.getOrElse { sActual.pedidosSalon }
            val borradoresLimpiados = if (pedidosSalonRes.isSuccess) {
                val idsActivos = pedidosSalon.map { it.idPedido }.toSet()
                val mesasOcupadas = pedidosSalon.map { it.idMesa }.toSet()
                val filtrados = sActual.borradores.filter { b ->
                    when {
                        b.idPedido != null -> b.idPedido in idsActivos
                        b.idMesa in mesasOcupadas -> false
                        else -> true
                    }
                }
                val codigo = sActual.sesion?.usuario?.codigoTrabajador
                if (codigo != null && filtrados != sActual.borradores) {
                    store.guardar(codigo, filtrados)
                }
                filtrados
            } else sActual.borradores

            val idsActivos = pedidosSalon.map { it.idPedido }.toSet()
            val mesaActualizada = sActual.mesa?.let { ma -> mesas.find { it.idMesa == ma.idMesa } ?: ma }
            val pedidoActualizado = if (pedidosSalonRes.isSuccess && sActual.pedido?.idPedido != null) {
                if (sActual.pedido.idPedido in idsActivos) pedidosSalon.find { it.idPedido == sActual.pedido.idPedido } ?: sActual.pedido
                else if (sActual.pagina == "Comanda") null else sActual.pedido
            } else sActual.pedido
            val borradorActualizado = if (pedidosSalonRes.isSuccess && sActual.borrador?.idPedido != null && sActual.borrador.idPedido !in idsActivos) {
                mesaActualizada?.let { Borrador(it.idMesa) }
            } else sActual.borrador

            _state.update {
                it.copy(
                    mesas = mesas,
                    categorias = categorias,
                    productos = productos,
                    productosTodos = productosTodos,
                    pedidosSalon = pedidosSalon,
                    borradores = borradoresLimpiados,
                    mesa = mesaActualizada,
                    pedido = pedidoActualizado,
                    borrador = borradorActualizado
                )
            }
        }
    }

    fun refrescar() = task {
        val s = _state.value
        when (s.pagina) {
            "Cocina" -> if (s.puedeCocina) refrescarCocina()
            "Usuarios" -> if (s.puedeAdmin) { val u = repo.usuarios(); _state.update { it.copy(usuarios = u) } }
            "Caja" -> { cargarCajaDatos(); if (s.puedeMesas) cargarSalon() }
            "Almacén", "Stock" -> if (s.puedeAlmacen) cargarInventario()
            "Reportes" -> if (s.puedeReportes) cargarReportes()
            else -> if (s.puedeMesas) cargarSalon()
        }
    }

    fun navegar(pagina: String) {
        if (_state.value.busy) return
        val s = _state.value
        if (pagina == "Cocina" && !s.puedeCocina) return
        if (pagina == "Usuarios" && !s.puedeAdmin) return
        if (pagina == "Reportes" && !s.puedeReportes) return
        if (pagina in listOf("Salón", "Mesas", "Comanda") && !s.puedeMesas) return
        if (pagina == "Caja" && !s.puedeCaja) return
        if (pagina in listOf("Almacén", "Stock") && !s.puedeAlmacen) return

        _state.update { it.copy(pagina = pagina, error = null, mensaje = null) }
        task {
            when (pagina) {
                "Usuarios" -> { val u = repo.usuarios(); _state.update { it.copy(usuarios = u) } }
                "Caja" -> { cargarCajaDatos(); if (s.puedeMesas) cargarSalon() }
                "Almacén", "Stock" -> cargarInventario()
                "Reportes" -> cargarReportes()
                "Salón", "Mesas" -> cargarSalon()
                "Cocina" -> refrescarCocina()
            }
        }
    }

    // --- SALÓN Y COMANDAS (SPRINT 1) ---
    private fun persistir(b: Borrador) {
        val s = _state.value; val codigo = s.sesion?.usuario?.codigoTrabajador ?: return
        val lista = s.borradores.filterNot { it.idMesa == b.idMesa } + b
        store.guardar(codigo, lista)
        _state.update { it.copy(borrador = b, borradores = lista) }
    }

    fun abrir(m: Mesa) {
        if (_state.value.busy) return
        val s = _state.value
        val activoEnMesa = s.pedidosSalon.find { it.idMesa == m.idMesa }
        val miCodigo = s.sesion?.usuario?.codigoTrabajador.orEmpty()
        val esAdmin = s.rol.uppercase() == "ADMINISTRADOR"
        val borradorLocal = s.borradores.find { it.idMesa == m.idMesa }

        if (activoEnMesa != null) {
            val esPropioOAdmin = esAdmin || activoEnMesa.codigoMozo.equals(miCodigo, ignoreCase = true)
            val avisoLectura = if (!esPropioOAdmin) {
                "Mesa atendida por ${activoEnMesa.codigoMozo} (${activoEnMesa.estado.replace('_', ' ')}). Vista de solo lectura."
            } else null
            val b = Borrador(
                idMesa = m.idMesa,
                lineas = activoEnMesa.lineas.filter { it.estado != "ANULADO" },
                observaciones = activoEnMesa.observaciones.orEmpty(),
                idPedido = activoEnMesa.idPedido,
                creacionIncierta = false
            )
            _state.update {
                it.copy(
                    mesa = m,
                    pedido = activoEnMesa,
                    borrador = b,
                    pagina = "Comanda",
                    modoSoloLecturaAviso = avisoLectura,
                    error = null,
                    mensaje = null
                )
            }
            if (esPropioOAdmin) {
                recuperar(activoEnMesa.idPedido.toString())
            }
            return
        }

        if (m.estado == "LIBRE" && borradorLocal?.idPedido != null) {
            val cod = s.sesion?.usuario?.codigoTrabajador
            val limpios = s.borradores.filterNot { it.idMesa == m.idMesa }
            if (cod != null) store.guardar(cod, limpios)
            _state.update {
                it.copy(
                    borradores = limpios,
                    mesa = m,
                    pedido = null,
                    borrador = Borrador(m.idMesa),
                    pagina = "Comanda",
                    modoSoloLecturaAviso = null,
                    error = null,
                    mensaje = null
                )
            }
            return
        }

        _state.update {
            it.copy(
                mesa = m,
                pedido = null,
                borrador = borradorLocal ?: Borrador(m.idMesa),
                pagina = "Comanda",
                modoSoloLecturaAviso = null,
                error = null,
                mensaje = null
            )
        }

        if (borradorLocal?.idPedido != null) {
            recuperar(borradorLocal.idPedido.toString())
        } else if (m.estado == "OCUPADA") {
            val ordenCocina = s.cocina.find { it.idMesa == m.idMesa || it.codigoMesa == m.codigoMesa }
            if (ordenCocina != null && (esAdmin || ordenCocina.codigoMozo.equals(miCodigo, ignoreCase = true))) {
                recuperar(ordenCocina.idPedido.toString())
            } else {
                task {
                    cargarSalon()
                    val actualizado = _state.value.pedidosSalon.find { it.idMesa == m.idMesa }
                    if (actualizado != null) {
                        val propio = esAdmin || actualizado.codigoMozo.equals(miCodigo, ignoreCase = true)
                        _state.update {
                            it.copy(
                                pedido = actualizado,
                                borrador = Borrador(
                                    m.idMesa,
                                    actualizado.lineas.filter { l -> l.estado != "ANULADO" },
                                    actualizado.observaciones.orEmpty(),
                                    actualizado.idPedido,
                                    false
                                ),
                                modoSoloLecturaAviso = if (propio) null else "Mesa atendida por ${actualizado.codigoMozo}. Vista de solo lectura."
                            )
                        }
                    } else {
                        _state.update { it.copy(error = "La mesa ${m.codigoMesa} figura ocupada en el servidor. Pulsa Recargar para actualizar.") }
                    }
                }
            }
        }
    }

    fun seleccionarPedidoCaja(p: Pedido?) {
        _state.update { it.copy(pedido = p, transaccionTarjeta = null, error = null, mensaje = null) }
    }

    fun cargarPedidoCaja(texto: String) = task {
        val id = texto.toLongOrNull(); require(id != null && id > 0) { "Ingresa un ID de pedido válido." }
        val p = repo.pedidoPendienteCobro(id)
        _state.update { it.copy(pedido = p, transaccionTarjeta = null, mensaje = "Cuenta ${p.codigoPedido} (${p.codigoMesa}) cargada para cobro") }
    }

    fun recuperar(texto: String) = task {
        val id = texto.toLongOrNull(); require(id != null && id > 0) { "Ingresa un ID de pedido válido." }
        val p = repo.pedido(id)
        if (p.estado in listOf("PAGADO", "ANULADO")) {
            liberarMesaYLimpiarPedido(p.idPedido, p.idMesa)
            _state.update {
                it.copy(
                    pedido = null,
                    borrador = null,
                    modoSoloLecturaAviso = null,
                    mensaje = "El pedido ${p.codigoPedido} ya figura como ${p.estado.lowercase()}. Mesa liberada."
                )
            }
            cargarSalon()
            return@task
        }
        val previo = _state.value.borrador
        val esAdmin = _state.value.rol.uppercase() == "ADMINISTRADOR"
        val miCodigo = _state.value.sesion?.usuario?.codigoTrabajador.orEmpty()
        val propio = esAdmin || p.codigoMozo.equals(miCodigo, ignoreCase = true)
        _state.update {
            it.copy(
                pedido = p,
                mesa = _state.value.mesas.find { it.idMesa == p.idMesa } ?: _state.value.mesa,
                pagina = if (it.pagina == "Caja") "Caja" else "Comanda",
                modoSoloLecturaAviso = if (propio) null else "Mesa atendida por ${p.codigoMozo} (${p.estado}). Vista de solo lectura.",
                error = null
            )
        }
        val conservar = previo != null && previo.idPedido == p.idPedido && !previo.creacionIncierta && p.estado == "BORRADOR"
        persistir(Borrador(p.idMesa, if (conservar) previo.lineas else p.lineas,
            if (conservar) previo.observaciones else p.observaciones.orEmpty(), p.idPedido))
    }

    private fun cambio(block: (Borrador) -> Borrador) {
        if (!_state.value.editable || _state.value.busy) return
        persistir(block(_state.value.borrador!!))
    }

    fun agregar(p: Producto) = cambio { b ->
        val i = b.lineas.indexOfFirst { it.idProducto == p.idProducto && it.observaciones.isBlank() }
        b.copy(lineas = if (i < 0) b.lineas + Linea(p.idProducto, p.nombre, p.precioCentimos) else b.lineas.mapIndexed { index, l -> if (index == i) l.copy(cantidad = (l.cantidad + 1).coerceAtMost(999)) else l })
    }

    fun cantidad(index: Int, delta: Int) = cambio { b ->
        b.copy(lineas = b.lineas.mapIndexed { i, l -> if (i == index) l.copy(cantidad = (l.cantidad + delta).coerceIn(1,999)) else l })
    }

    fun quitar(index: Int) = cambio { b ->
        b.copy(lineas = b.lineas.filterIndexed { i, _ -> i != index })
    }

    fun notas(index: Int, texto: String) = cambio { b ->
        b.copy(lineas = b.lineas.mapIndexed { i, l -> if (i == index) l.copy(observaciones = texto.take(300)) else l })
    }

    fun observaciones(texto: String) = cambio { it.copy(observaciones = texto.take(1000)) }

    fun descartarLocal() {
        val b = _state.value.borrador ?: return; val s = _state.value; val codigo = s.sesion?.usuario?.codigoTrabajador ?: return
        val lista = s.borradores.filterNot { it.idMesa == b.idMesa }
        store.guardar(codigo, lista)
        _state.update { it.copy(borrador = Borrador(b.idMesa), pedido = null, borradores = lista, mensaje = "Borrador local descartado") }
    }

    private suspend fun guardar(): Pedido {
        val b = _state.value.borrador ?: error("No hay borrador activo.")
        require(b.lineas.isNotEmpty()) { "Agrega al menos un plato a la comanda." }
        return if (b.idPedido == null) {
            persistir(b.copy(creacionIncierta = true))
            try {
                val p = repo.crear(b)
                persistir(Borrador(b.idMesa, p.lineas, p.observaciones.orEmpty(), p.idPedido, false))
                _state.update { it.copy(pedido = p) }
                cargarSalon()
                p
            } catch (e: Exception) {
                if (e is FalloApi && e.codigo in 400..499) persistir(b.copy(creacionIncierta = false))
                throw e
            }
        } else {
            val p = repo.editar(b.idPedido, b.lineas)
            persistir(Borrador(b.idMesa, p.lineas, p.observaciones.orEmpty(), p.idPedido, false))
            _state.update { it.copy(pedido = p) }
            cargarSalon()
            p
        }
    }

    fun guardarBorrador() = task {
        val p = guardar()
        _state.update { it.copy(mensaje = "Comanda ${p.codigoPedido} guardada en servidor") }
    }

    fun enviar() = task {
        val guardado = guardar()
        val res = repo.enviar(guardado.idPedido)
        persistir(Borrador(guardado.idMesa, res.lineas, res.observaciones.orEmpty(), res.idPedido, false))
        _state.update { it.copy(pedido = res, mensaje = "Comanda enviada a cocina exitosamente") }
        cargarSalon()
    }

    fun trasladar(destino: Long, motivo: String) = task {
        val p = _state.value.pedido ?: error("No hay pedido guardado para trasladar.")
        repo.trasladar(p.idPedido, destino, motivo)
        descartarLocal()
        cargarSalon()
        _state.update { it.copy(pagina = "Salón", mensaje = "Comanda trasladada a nueva mesa") }
    }

    // --- COCINA KDS (SPRINT 2) ---
    fun marcarPlatoListo(idDetalle: Long) = task {
        repo.marcarPlatoListo(idDetalle)
        refrescarCocina()
        _state.update { it.copy(mensaje = "Plato marcado como listo") }
    }

    fun cambiarDisponibilidad(idProducto: Long, disponible: Boolean) = task {
        repo.cambiarDisponibilidad(idProducto, disponible)
        val prodActivos = if (_state.value.puedeMesas) runCatching { repo.productos() }.getOrNull() else null
        val todos = runCatching { repo.todosProductos() }.getOrNull()
        _state.update { s ->
            s.copy(
                productos = prodActivos ?: s.productos.filter { if (it.idProducto == idProducto) disponible else true },
                productosTodos = todos ?: s.productosTodos.map { if (it.idProducto == idProducto) it.copy(disponible = disponible) else it },
                mensaje = if (disponible) "Plato habilitado nuevamente en el menú" else "Plato marcado como agotado"
            )
        }
    }

    private suspend fun refrescarCocina() = kitchenLock.withLock {
        runCatching {
            val pedidos = repo.cocina()
            val todos = runCatching { repo.todosProductos() }.getOrNull()
            val nuevosIds = pedidos.map { it.idPedido }.toSet()
            val entrantes = nuevosIds - idsCocina
            if (cocinaIniciada && entrantes.isNotEmpty() && entrantes.any { it !in idsNotificados }) {
                avisarPedido()
                idsNotificados.addAll(entrantes)
                val nuevo = pedidos.firstOrNull { it.idPedido in entrantes }
                if (nuevo != null) {
                    mostrarNotificacionCocina(nuevo)
                }
            }
            idsCocina = nuevosIds
            cocinaIniciada = true
            _state.update {
                it.copy(
                    cocina = pedidos,
                    productosTodos = todos ?: it.productosTodos,
                    cocinaActualizada = Clock.System.now().toEpochMilliseconds(),
                    cocinaAviso = null
                )
            }
        }.onFailure { e ->
            _state.update { it.copy(cocinaAviso = "KDS: ${e.message}") }
        }
    }

    private var notifJob: Job? = null
    fun mostrarNotificacionCocina(pedido: Pedido) {
        notifJob?.cancel()
        _state.update { it.copy(nuevoPedidoCocinaAviso = pedido) }
        notifJob = viewModelScope.launch {
            delay(7000)
            _state.update { it.copy(nuevoPedidoCocinaAviso = null) }
        }
    }

    fun cerrarNotificacionCocina() {
        notifJob?.cancel()
        _state.update { it.copy(nuevoPedidoCocinaAviso = null) }
    }

    private fun iniciarCocina() {
        if (kitchenJob != null) return
        kitchenJob = viewModelScope.launch {
            refrescarCocina()
            launch {
                while (isActive) {
                    runCatching {
                        _state.update { it.copy(socketConectado = true, cocinaAviso = null) }
                        socket.eventos().collect {
                            refrescarCocina()
                            if (_state.value.puedeMesas) cargarSalon()
                            if (_state.value.puedeCaja) cargarCajaDatos()
                        }
                    }.onFailure { e ->
                        _state.update { it.copy(socketConectado = false, cocinaAviso = "KDS: ${e.message}") }
                        delay(5000)
                    }
                }
            }
            while (isActive) {
                delay(15000)
                if (!_state.value.socketConectado) refrescarCocina()
            }
        }
    }

    // --- CAJA Y COBROS (SPRINT 2 Y 3) ---
    private suspend fun cargarTurnoCaja() {
        runCatching {
            val turno = repo.turnoAbierto()
            _state.update { it.copy(turnoCaja = if (turno != null && turno.estado == "ABIERTO") turno else null) }
        }
    }

    private suspend fun cargarCajaDatos() {
        cargarTurnoCaja()
        runCatching {
            val pendientes = repo.pendientesCobro()
            _state.update { s ->
                val actual = s.pedido
                val actualizado = if (actual != null && s.pagina == "Caja") {
                    pendientes.find { it.idPedido == actual.idPedido }
                } else actual
                s.copy(pendientesCobro = pendientes, pedido = actualizado)
            }
        }
    }

    fun abrirCaja(montoInicial: Double) = task {
        require(montoInicial >= 0.0) { "El fondo inicial no puede ser negativo." }
        val res = repo.aperturaCaja(montoInicial)
        val centStr = dinero(res.montoInicialCentimos)
        _state.update { it.copy(turnoCaja = res, cierreInfo = null, mensaje = "Turno de caja #${res.idTurno} abierto con fondo de $centStr") }
        cargarCajaDatos()
    }

    private fun liberarMesaYLimpiarPedido(idPedido: Long, idMesaOpt: Long?) {
        val s = _state.value
        val idMesa = idMesaOpt
            ?: s.pedido?.idMesa
            ?: s.pendientesCobro.find { it.idPedido == idPedido }?.idMesa
            ?: s.pedidosSalon.find { it.idPedido == idPedido }?.idMesa
            ?: s.mesa?.idMesa
            ?: s.cocina.find { it.idPedido == idPedido }?.idMesa
        val codigoTrabajador = s.sesion?.usuario?.codigoTrabajador
        val listaBorradores = if (codigoTrabajador != null) {
            val nuevaLista = s.borradores.filterNot { (idMesa != null && it.idMesa == idMesa) || it.idPedido == idPedido }
            store.guardar(codigoTrabajador, nuevaLista)
            nuevaLista
        } else s.borradores

        val mesasActualizadas = if (idMesa != null) {
            s.mesas.map { if (it.idMesa == idMesa) it.copy(estado = "LIBRE") else it }
        } else s.mesas

        _state.update {
            it.copy(
                pedido = null,
                mesa = if (it.mesa?.idMesa == idMesa) null else it.mesa,
                borrador = if (it.borrador?.idMesa == idMesa || it.borrador?.idPedido == idPedido) null else it.borrador,
                borradores = listaBorradores,
                mesas = mesasActualizadas,
                cocina = s.cocina.filterNot { p -> p.idPedido == idPedido },
                pedidosSalon = s.pedidosSalon.filterNot { p -> p.idPedido == idPedido },
                pendientesCobro = s.pendientesCobro.filterNot { p -> p.idPedido == idPedido }
            )
        }
    }

    fun cobrarYEmitirComprobante(
        idPedido: Long,
        metodoPago: String,
        recibidoEfectivo: Double = 0.0,
        totalPedido: Double,
        operacionDigital: String = "",
        ultimos4Digital: String = "",
        partesMixto: List<Triple<String, Double, String?>> = emptyList(),
        idMesaOpt: Long? = null,
        emitirSunat: Boolean = true,
        tipoComprobante: String = "BOLETA",
        tipoDoc: String = "Sin Doc",
        numDoc: String = "",
        razonSocial: String = "",
        direccion: String = "",
        correo: String? = null,
        telefonoWhatsapp: String? = null,
        enviarWhatsapp: Boolean = false
    ) = task {
        val t = _state.value.turnoCaja ?: error("Debe abrir turno de caja primero.")
        val idMesa = idMesaOpt
            ?: _state.value.pedido?.idMesa
            ?: _state.value.pendientesCobro.find { it.idPedido == idPedido }?.idMesa
            ?: _state.value.mesa?.idMesa

        val met = metodoPago.uppercase()
        val pago = when (met) {
            "EFECTIVO" -> repo.cobrarEfectivo(idPedido, t.idTurno, recibidoEfectivo, totalPedido)
            "MIXTO" -> repo.cobrarMixto(idPedido, t.idTurno, partesMixto)
            else -> repo.cobrarDigital(idPedido, t.idTurno, met, totalPedido, operacionDigital, ultimos4Digital)
        }

        liberarMesaYLimpiarPedido(idPedido, idMesa)

        val vueltoCent = if (met == "EFECTIVO") {
            if (pago.vueltoCentimos > 0L) pago.vueltoCentimos
            else ((recibidoEfectivo - totalPedido) * 100.0 + 0.5).toLong().coerceAtLeast(0L)
        } else 0L
        val vueltoStr = dinero(vueltoCent)

        var comprobante: pe.edu.upeu.nullpointerpos.data.remote.dto.RespuestaComprobanteSunat? = null
        var avisoExtra = ""

        if (emitirSunat) {
            val nomClean = razonSocial.trim()
            val numClean = numDoc.trim()
            val dirClean = direccion.trim()
            val tipoComp = tipoComprobante.trim().uppercase()
            val modalidad = when {
                tipoComp == "FACTURA" && nomClean.isNotBlank() -> "MANUAL"
                tipoComp == "FACTURA" -> "RUC"
                tipoDoc == "Sin Doc" || (numClean.isBlank() && nomClean.isBlank()) -> "SIN_DATOS"
                nomClean.isNotBlank() -> "MANUAL"
                tipoDoc.equals("DNI", ignoreCase = true) -> "DNI"
                tipoDoc.equals("RUC", ignoreCase = true) -> "RUC"
                else -> "MANUAL"
            }
            val tipoDocFinal = when (modalidad) {
                "SIN_DATOS" -> null
                "DNI" -> "DNI"
                "RUC" -> "RUC"
                else -> if (tipoDoc == "Sin Doc" || numClean.isBlank()) null else tipoDoc.uppercase()
            }
            val reqComp = pe.edu.upeu.nullpointerpos.data.remote.dto.SolicitudEmitirComprobante(
                idPedido = idPedido,
                idPago = pago.idPago,
                modalidad = modalidad,
                tipoComprobante = tipoComp,
                tipoDocumento = tipoDocFinal,
                numeroDocumento = numClean.ifBlank { null },
                nombreCompleto = nomClean.ifBlank { null },
                direccion = dirClean.ifBlank { null },
                correo = correo?.trim()?.ifBlank { null },
                telefono = telefonoWhatsapp?.trim()?.ifBlank { null }
            )
            runCatching { repo.emitirComprobanteSunat(reqComp) }
                .onSuccess { comprobante = it }
                .onFailure { avisoExtra += " · SUNAT: ${it.message}" }
        }

        var whatsappRes: pe.edu.upeu.nullpointerpos.data.remote.dto.RespuestaTicketWhatsAppDto? = null
        if (enviarWhatsapp && !telefonoWhatsapp.isNullOrBlank()) {
            val tel = telefonoWhatsapp.filter { it.isDigit() || it == '+' }
            runCatching { repo.ticketWhatsApp(idPedido, tel, t.idTurno) }
                .onSuccess { res ->
                    whatsappRes = res
                    avisoExtra += when (res.estado) {
                        "ENVIADO" -> " · PDF ${res.archivoPdf ?: ""} enviado por WhatsApp"
                        "PENDIENTE_CONFIGURACION" -> " · WhatsApp pendiente de configurar"
                        else -> " · WhatsApp: ${res.detalle ?: res.mensaje}"
                    }
                }
                .onFailure {
                    whatsappRes = pe.edu.upeu.nullpointerpos.data.remote.dto.RespuestaTicketWhatsAppDto(
                        estado = "ERROR_ENVIO",
                        mensaje = it.message ?: "No se pudo conectar para enviar WhatsApp"
                    )
                    avisoExtra += " · WhatsApp: ${it.message}"
                }
        }

        val resumenCobro = when (met) {
            "EFECTIVO" -> "Cobro en efectivo registrado (#${pago.idPago}). Vuelto: $vueltoStr"
            "MIXTO" -> "Cobro mixto registrado (#${pago.idPago}) con éxito"
            else -> "Cobro $met registrado (#${pago.idPago}) con éxito"
        }

        _state.update {
            it.copy(
                comprobanteEmitido = comprobante,
                whatsappResultado = whatsappRes,
                transaccionTarjeta = null,
                clienteSeleccionado = null,
                mensaje = "$resumenCobro$avisoExtra"
            )
        }

        delay(800)
        cargarCajaDatos()
        if (_state.value.puedeMesas) cargarSalon()
    }

    fun cobrarEfectivo(idPedido: Long, recibido: Double, total: Double, idMesaOpt: Long? = null) =
        cobrarYEmitirComprobante(idPedido, "EFECTIVO", recibido, total, idMesaOpt = idMesaOpt, emitirSunat = false)

    fun cobrarDigital(idPedido: Long, metodo: String, total: Double, operacion: String, ultimos4: String, idMesaOpt: Long? = null) =
        cobrarYEmitirComprobante(idPedido, metodo, totalPedido = total, operacionDigital = operacion, ultimos4Digital = ultimos4, idMesaOpt = idMesaOpt, emitirSunat = false)

    fun cobrarMixto(idPedido: Long, partes: List<Triple<String, Double, String?>>, idMesaOpt: Long? = null) =
        cobrarYEmitirComprobante(idPedido, "MIXTO", totalPedido = partes.sumOf { it.second }, partesMixto = partes, idMesaOpt = idMesaOpt, emitirSunat = false)

    fun cerrarCaja(efectivoReal: Double, motivo: String) = task {
        val t = _state.value.turnoCaja ?: error("No hay turno abierto para cerrar.")
        val res = repo.cierreCaja(t.idTurno, efectivoReal, motivo.ifBlank { "CIERRE NORMAL" })
        _state.update { it.copy(turnoCaja = null, cierreInfo = res, mensaje = "Turno de caja #${t.idTurno} cerrado exitosamente con arqueo.") }
    }

    fun simularPagoTarjeta(
        numeroTarjeta: String,
        mes: String,
        anio: String,
        cvv: String,
        titular: String,
        monto: Double
    ) = task {
        val mesInt = mes.trim().toIntOrNull() ?: throw IllegalArgumentException("Mes de vencimiento inválido (1-12).")
        val anioRaw = anio.trim().toIntOrNull() ?: throw IllegalArgumentException("Año de vencimiento inválido.")
        val anioInt = if (anioRaw in 0..99) 2000 + anioRaw else anioRaw
        val req = pe.edu.upeu.nullpointerpos.data.remote.dto.SolicitudSimularTarjeta(
            numeroTarjeta = numeroTarjeta.replace(" ", "").replace("-", ""),
            mesVencimiento = mesInt,
            anioVencimiento = anioInt,
            cvv = cvv.trim(),
            titular = titular.trim().uppercase()
        )
        val res = repo.simularTarjeta(req)
        if (!res.aprobadaFinal) {
            throw FalloApi(400, res.mensaje.ifBlank { "Transacción con tarjeta rechazada." })
        }
        _state.update {
            it.copy(
                transaccionTarjeta = res,
                mensaje = "Tarjeta ${res.marcaFinal} aprobada (Voucher: ${res.numeroOperacionFinal})"
            )
        }
    }

    fun limpiarTransaccionTarjeta() {
        _state.update { it.copy(transaccionTarjeta = null) }
    }

    fun emitirComprobanteSunat(
        idPedido: Long,
        idPago: Long,
        tipoComprobante: String,
        tipoDoc: String,
        numDoc: String,
        razonSocial: String,
        direccion: String = "",
        correo: String? = null
    ) = task {
        val nomClean = razonSocial.trim()
        val numClean = numDoc.trim()
        val tipoComp = tipoComprobante.trim().uppercase()
        val modalidad = when {
            tipoComp == "FACTURA" && nomClean.isNotBlank() -> "MANUAL"
            tipoComp == "FACTURA" -> "RUC"
            tipoDoc == "Sin Doc" || (numClean.isBlank() && nomClean.isBlank()) -> "SIN_DATOS"
            nomClean.isNotBlank() -> "MANUAL"
            tipoDoc.equals("DNI", ignoreCase = true) -> "DNI"
            tipoDoc.equals("RUC", ignoreCase = true) -> "RUC"
            else -> "MANUAL"
        }
        val req = pe.edu.upeu.nullpointerpos.data.remote.dto.SolicitudEmitirComprobante(
            idPedido = idPedido,
            idPago = idPago,
            modalidad = modalidad,
            tipoComprobante = tipoComp,
            tipoDocumento = if (modalidad == "SIN_DATOS" || numClean.isBlank()) null else tipoDoc.uppercase(),
            numeroDocumento = numClean.ifBlank { null },
            nombreCompleto = nomClean.ifBlank { null },
            direccion = direccion.trim().ifBlank { null },
            correo = correo?.trim()?.ifBlank { null }
        )
        val res = repo.emitirComprobanteSunat(req)
        _state.update {
            it.copy(
                comprobanteEmitido = res,
                mensaje = "Comprobante ${res.numeroFinal} emitido y aprobado por SUNAT Beta"
            )
        }
    }

    fun cerrarComprobanteModal() {
        _state.update { it.copy(comprobanteEmitido = null, whatsappResultado = null) }
    }

    fun buscarCliente(tipo: String, numero: String) = task {
        val cli = repo.buscarCliente(tipo, numero)
        _state.update { it.copy(clienteSeleccionado = cli, mensaje = if (cli != null) "Cliente encontrado: ${cli.razonSocial}" else "Cliente no registrado en SUNAT") }
    }

    fun ticketWhatsApp(idPedido: Long, telefono: String) = task {
        val tel = telefono.filter { it.isDigit() || it == '+' }
        require(tel.count(Char::isDigit) in 9..15) { "Ingresa un número de WhatsApp válido (9 a 15 dígitos)." }
        val res = repo.ticketWhatsApp(idPedido, tel, _state.value.turnoCaja?.idTurno ?: 0L)
        _state.update {
            when (res.estado) {
                "ENVIADO" -> it.copy(
                    whatsappResultado = res,
                    mensaje = "PDF ${res.archivoPdf.orEmpty()} enviado por WhatsApp a $tel"
                )
                "PENDIENTE_CONFIGURACION" -> it.copy(
                    whatsappResultado = res,
                    error = res.mensaje.ifBlank { "Falta configurar Green-API en el servidor." }
                )
                else -> it.copy(
                    whatsappResultado = res,
                    error = res.detalle?.ifBlank { null } ?: res.mensaje.ifBlank { "No se pudo enviar el PDF por WhatsApp." }
                )
            }
        }
    }

    fun abrirPdfComprobante(idComprobante: Long, numeroComprobante: String, compartir: Boolean = false) = task {
        val bytes = repo.descargarPdfComprobante(idComprobante)
        val nombre = numeroComprobante.ifBlank { "Comprobante-$idComprobante" }
        val error = pe.edu.upeu.nullpointerpos.platform.abrirPdfLocal(nombre, bytes, compartir)
        if (error != null) {
            _state.update { it.copy(error = error) }
        } else {
            _state.update {
                it.copy(mensaje = if (compartir) "Compartiendo PDF $nombre..." else "Abriendo PDF 80mm $nombre...")
            }
        }
    }

    fun aplicarDescuento(idPedido: Long, porcentaje: Double, motivo: String) = task {
        require(porcentaje > 0.0 && porcentaje < 100.0) { "Ingresa un porcentaje válido mayor a 0 y menor a 100." }
        require(motivo.isNotBlank()) { "Indica el motivo del descuento para auditoría." }
        repo.aplicarDescuento(idPedido, porcentaje, motivo.trim())
        val actualizado = runCatching { repo.pedidoPendienteCobro(idPedido) }.getOrNull()
        _state.update { s ->
            val fallbackActual = s.pedido?.takeIf { it.idPedido == idPedido }?.let { p ->
                val nuevoCent = ((p.importeCentimos * ((100.0 - porcentaje) / 100.0)) + 0.5).toLong()
                p.copy(
                    importeCentimos = nuevoCent,
                    descuentoCentimos = p.importeCentimos - nuevoCent,
                    porcentajeDescuento = porcentaje,
                    observaciones = motivo.trim()
                )
            }
            val resPedido = actualizado ?: fallbackActual
            s.copy(
                pedido = resPedido,
                pendientesCobro = s.pendientesCobro.map { if (it.idPedido == idPedido && resPedido != null) resPedido else it },
                mensaje = "Descuento del ${porcentaje.toInt()}% aplicado correctamente"
            )
        }
        cargarCajaDatos()
    }

    fun anularPedido(idPedido: Long, motivo: String) = task {
        require(motivo.isNotBlank()) { "Debes ingresar el motivo de anulación." }
        repo.anularPedido(idPedido, motivo.trim())
        liberarMesaYLimpiarPedido(idPedido, null)
        _state.update { it.copy(mensaje = "Pedido anulado correctamente") }
        if (_state.value.puedeMesas) cargarSalon()
        if (_state.value.puedeCaja) cargarCajaDatos()
    }

    // --- INVENTARIO Y ALMACÉN (SPRINT 3) ---
    private suspend fun cargarInventario() {
        runCatching {
            val insumos = repo.insumos()
            val alertas = repo.alertasStock()
            _state.update { it.copy(insumos = insumos, alertasStock = alertas) }
        }.onFailure { e ->
            _state.update { it.copy(error = "Inventario: ${e.message}") }
        }
    }

    fun crearInsumo(codigo: String, nombre: String, unidad: String, stock: Double, minimo: Double, costo: Double) = task {
        repo.crearInsumo(codigo, nombre, unidad, stock, minimo, costo)
        _state.update { it.copy(mensaje = "Insumo '$nombre' registrado exitosamente") }
        cargarInventario()
    }

    fun registrarCompra(proveedor: String, doc: String, items: List<pe.edu.upeu.nullpointerpos.data.remote.dto.ItemCompraDto>, obs: String) = task {
        repo.registrarCompra(proveedor, doc, items, obs)
        _state.update { it.copy(mensaje = "Compra de insumos ingresada") }
        cargarInventario()
    }

    fun registrarMerma(idInsumo: Long, cantidad: Double, motivo: String, obs: String) = task {
        repo.registrarMerma(idInsumo, cantidad, motivo, obs)
        _state.update { it.copy(mensaje = "Merma registrada") }
        cargarInventario()
    }

    // --- REPORTES Y ADMINISTRACIÓN ---
    private suspend fun cargarReportes() {
        runCatching {
            val ventas = repo.reporteVentasPorHora()
            val platos = repo.reportePlatosPopulares()
            val auditoria = repo.auditoriaPedidos()
            val usuarios = runCatching { repo.usuarios() }.getOrDefault(_state.value.usuarios)
            _state.update { it.copy(ventasPorHora = ventas, platosPopulares = platos, auditoria = auditoria, usuarios = usuarios) }
        }.onFailure { e ->
            _state.update { it.copy(error = "Reportes: ${e.message}") }
        }
    }

    fun crearUsuario(codigo: String, nombre: String, correo: String, pin: String, rol: String) = task {
        repo.crearUsuario(codigo, nombre, correo, pin, rol)
        _state.update { it.copy(mensaje = "Usuario $nombre ($rol) creado") }
        val u = repo.usuarios()
        _state.update { it.copy(usuarios = u) }
    }

    fun activar(cuenta: Cuenta) = task {
        repo.activarUsuario(cuenta.idUsuario, !cuenta.activo)
        _state.update { it.copy(mensaje = "Estado de ${cuenta.nombreCompleto} actualizado") }
        val u = repo.usuarios()
        _state.update { it.copy(usuarios = u) }
    }
}
