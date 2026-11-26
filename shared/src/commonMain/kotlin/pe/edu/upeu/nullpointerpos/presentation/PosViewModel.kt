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
import pe.edu.upeu.nullpointerpos.platform.avisarPedidoListo
import pe.edu.upeu.nullpointerpos.platform.destinosNotificacion
import pe.edu.upeu.nullpointerpos.platform.hayConexionRed
import pe.edu.upeu.nullpointerpos.platform.limpiarDestinoNotificacion
import pe.edu.upeu.nullpointerpos.platform.observarConectividad

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
    private var clienteJob: Job? = null
    private var unobserveRed: (() -> Unit)? = null
    private val kitchenLock = Mutex()
    private val syncLock = Mutex()
    private var cocinaIniciada = false
    private var idsCocina = emptySet<Long>()
    private val idsNotificados = mutableSetOf<Long>()
    private var notifListoJob: Job? = null
    private var salonIniciado = false
    private var estadosSalonPrevios = emptyMap<Long, String>()
    private val idsListosNotificados = mutableSetOf<Long>()

    override fun onCleared() {
        unobserveRed?.invoke()
        unobserveRed = null
        kitchenJob?.cancel()
        clienteJob?.cancel()
        super.onCleared()
    }

    private fun esErrorDeConexion(e: Throwable): Boolean {
        if (e is CancellationException) return false
        if (!hayConexionRed()) return true
        return when (e) {
            is FalloApi -> e.codigo in listOf(0, 408, 502, 503, 504)
            is IllegalArgumentException, is IllegalStateException -> false
            else -> true
        }
    }

    private fun guardarSnapshotActual(s: PosUiState = _state.value) {
        val ses = s.sesion ?: return
        val codigo = ses.usuario.codigoTrabajador
        store.guardarCache(
            codigo,
            SnapshotOffline(
                sesion = ses,
                mesas = s.mesas,
                categorias = s.categorias,
                productos = s.productos,
                productosTodos = s.productosTodos,
                pedidosSalon = s.pedidosSalon.filter { it.idPedido > 0 },
                cocina = s.cocina,
                pendientesCobro = s.pendientesCobro,
                turnoCaja = s.turnoCaja
            )
        )
    }

    private fun aplicarColaSobreEstado(base: PosUiState, cola: List<AccionPendienteSync>): PosUiState {
        var mesas = base.mesas
        var salon = base.pedidosSalon.filter { it.idPedido > 0 }
        var cocina = base.cocina
        var cobro = base.pendientesCobro
        var prods = base.productos
        var prodsTodos = base.productosTodos
        val codMozo = base.sesion?.usuario?.codigoTrabajador.orEmpty()

        for (accion in cola) {
            when (accion.tipo) {
                TipoAccionSync.ENVIAR_PEDIDO_MOZO -> {
                    val idLoc = accion.idPedidoLocal ?: accion.idPedido ?: -1L
                    val codigoLocal = "LOCAL-" + kotlin.math.abs(idLoc).toString().takeLast(4)
                    val mesaCod = accion.codigoMesa.ifBlank {
                        mesas.find { it.idMesa == accion.idMesa }?.codigoMesa ?: "M${accion.idMesa ?: 0}"
                    }
                    val pedidoOffline = Pedido(
                        idPedido = idLoc,
                        codigoPedido = codigoLocal,
                        idMesa = accion.idMesa ?: 0L,
                        codigoMesa = mesaCod,
                        codigoMozo = codMozo,
                        estado = "PEDIDO_POR_SINCRONIZAR",
                        importeCentimos = accion.lineas.total(),
                        fechaEnvio = accion.fechaRegistroOffline,
                        lineas = accion.lineas.map { it.copy(estado = "PENDIENTE") },
                        observaciones = accion.observaciones.ifBlank { null },
                        fechaCreacion = accion.fechaRegistroOffline
                    )
                    salon = salon.filterNot { it.idPedido == idLoc || (accion.idMesa != null && it.idMesa == accion.idMesa && it.idPedido < 0) } + pedidoOffline
                    if (accion.idMesa != null) {
                        mesas = mesas.map { if (it.idMesa == accion.idMesa) it.copy(estado = "OCUPADA") else it }
                    }
                }
                TipoAccionSync.MARCAR_PLATO_LISTO -> {
                    val idDet = accion.idDetalle ?: continue
                    val marcarLista = { lista: List<Pedido> ->
                        lista.map { p ->
                            if (p.lineas.none { it.idDetalle == idDet }) p
                            else {
                                val lineasAct = p.lineas.map { l -> if (l.idDetalle == idDet) l.copy(estado = "LISTO_POR_SINCRONIZAR") else l }
                                val todasListas = lineasAct.filter { it.estado != "ANULADO" }.all { it.estado in listOf("LISTO", "LISTO_POR_SINCRONIZAR", "ENTREGADO") }
                                p.copy(lineas = lineasAct, estado = if (todasListas) "LISTO_POR_SINCRONIZAR" else p.estado)
                            }
                        }
                    }
                    cocina = marcarLista(cocina)
                    salon = marcarLista(salon)
                    cobro = marcarLista(cobro)
                }
                TipoAccionSync.MARCAR_PEDIDO_LISTO -> {
                    val idPed = accion.idPedido ?: continue
                    val marcarTodo = { lista: List<Pedido> ->
                        lista.map { p ->
                            if (p.idPedido != idPed) p
                            else {
                                val lineasAct = p.lineas.map { l ->
                                    if (l.estado in listOf("PENDIENTE", "EN_PREPARACION")) l.copy(estado = "LISTO_POR_SINCRONIZAR") else l
                                }
                                p.copy(lineas = lineasAct, estado = "LISTO_POR_SINCRONIZAR")
                            }
                        }
                    }
                    cocina = marcarTodo(cocina)
                    salon = marcarTodo(salon)
                    cobro = marcarTodo(cobro)
                }
                TipoAccionSync.CAMBIAR_DISPONIBILIDAD -> {
                    val idProd = accion.idProducto ?: continue
                    val disp = accion.disponible ?: true
                    prods = if (disp) {
                        val existente = prodsTodos.find { it.idProducto == idProd }
                        if (existente != null && prods.none { it.idProducto == idProd }) prods + existente.copy(disponible = true)
                        else prods.map { if (it.idProducto == idProd) it.copy(disponible = true) else it }
                    } else {
                        prods.filterNot { it.idProducto == idProd }
                    }
                    prodsTodos = prodsTodos.map { if (it.idProducto == idProd) it.copy(disponible = disp) else it }
                }
                TipoAccionSync.COBRAR_PEDIDO -> {
                    val idPed = accion.idPedido ?: continue
                    cobro = cobro.filterNot { it.idPedido == idPed }
                    salon = salon.filterNot { it.idPedido == idPed }
                    cocina = cocina.filterNot { it.idPedido == idPed }
                    if (accion.idMesa != null) {
                        mesas = mesas.map { if (it.idMesa == accion.idMesa) it.copy(estado = "LIBRE") else it }
                    }
                }
            }
        }
        return base.copy(
            colaSync = cola,
            mesas = mesas,
            pedidosSalon = salon,
            cocina = cocina,
            pendientesCobro = cobro,
            productos = prods,
            productosTodos = prodsTodos
        )
    }

    private fun actualizarColaSync(nuevaCola: List<AccionPendienteSync>) {
        val codigo = _state.value.sesion?.usuario?.codigoTrabajador ?: return
        store.guardarColaSync(codigo, nuevaCola)
        _state.update { aplicarColaSobreEstado(it, nuevaCola) }
    }

    fun descartarAccionSync(idLocal: String) {
        val nueva = _state.value.colaSync.filterNot { it.idLocal == idLocal }
        actualizarColaSync(nueva)
        _state.update { it.copy(error = null, mensaje = "Operación pendiente descartada.") }
    }

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
                "Usa el formato: http://10.40.79.37:8080"
            }
            store.servidor = raw
            _state.update { it.copy(servidor = raw, error = null, mensaje = "Servidor guardado: $raw") }
        }.onFailure { _state.update { s -> s.copy(error = it.message ?: "Dirección no válida") } }
    }

    fun login(codigo: String, pin: String) = task {
        val cod = codigo.trim().uppercase()
        val p = pin.trim()
        require(cod.isNotBlank() && p.length >= 4) { "Ingresa tu código y contraseña / PIN de 4 dígitos." }
        try {
            val sesion = repo.login(cod, p)
            session.token = sesion.token
            store.guardarUltimaSesion(sesion, store.huellaOffline(cod, p))
            val rol = sesion.usuario.rol
            val destino = destinoPorRol(rol)
            val cache = store.cache(sesion.usuario.codigoTrabajador)
            val cola = store.colaSync(sesion.usuario.codigoTrabajador)
            val inicial = aplicarColaSobreEstado(
                PosUiState(
                    servidor = store.servidor,
                    sesion = sesion,
                    busy = true,
                    pagina = destino,
                    modoOscuro = store.modoOscuro,
                    conectadoServidor = true,
                    pendientesEmision = store.emisiones(sesion.usuario.codigoTrabajador),
                    borradores = store.borradores(sesion.usuario.codigoTrabajador),
                    mesas = cache.mesas,
                    categorias = cache.categorias,
                    productos = cache.productos,
                    productosTodos = cache.productosTodos,
                    pedidosSalon = cache.pedidosSalon,
                    cocina = cache.cocina,
                    pendientesCobro = cache.pendientesCobro,
                    turnoCaja = cache.turnoCaja
                ),
                cola
            )
            _state.value = inicial
            iniciarMonitorRedYEventos()
            if (cola.isNotEmpty()) {
                viewModelScope.launch { sincronizarPendientes(silencioso = false) }
            }
            recargarSegunPermisos(incluirReportes = true)
        } catch (e: Exception) {
            val cached = store.ultimaSesion()
            if (esErrorDeConexion(e) && cached != null &&
                cached.usuario.codigoTrabajador.equals(cod, ignoreCase = true) &&
                store.verificarPinOffline(cod, p)
            ) {
                session.token = cached.token
                val usr = cached.usuario.codigoTrabajador
                val cache = store.cache(usr)
                val cola = store.colaSync(usr)
                val offlineState = aplicarColaSobreEstado(
                    PosUiState(
                        servidor = store.servidor,
                        sesion = cached,
                        busy = false,
                        pagina = destinoPorRol(cached.usuario.rol),
                        modoOscuro = store.modoOscuro,
                        conectadoServidor = false,
                        pendientesEmision = store.emisiones(usr),
                        borradores = store.borradores(usr),
                        mesas = cache.mesas,
                        categorias = cache.categorias,
                        productos = cache.productos,
                        productosTodos = cache.productosTodos,
                        pedidosSalon = cache.pedidosSalon,
                        cocina = cache.cocina,
                        pendientesCobro = cache.pendientesCobro,
                        turnoCaja = cache.turnoCaja,
                        mensaje = "Sin conexión · Modo Offline activo con datos guardados."
                    ),
                    cola
                )
                _state.value = offlineState
                iniciarMonitorRedYEventos()
            } else {
                throw e
            }
        }
    }

    private fun destinoPorRol(rol: String) = when (rol) {
        "MOZO" -> "Salón"
        "COCINERO" -> "Cocina"
        "CAJERO" -> "Caja"
        "ALMACENERO" -> "Almacén"
        "ADMINISTRADOR" -> "Salón"
        else -> "Cuenta"
    }

    private suspend fun recargarSegunPermisos(incluirReportes: Boolean = false) {
        val s = _state.value
        if (s.sesion == null) return
        if (s.puedeCocina) refrescarCocina()
        if (s.puedeMesas) cargarSalon()
        if (s.puedeCaja) cargarCajaDatos()
        if (s.puedeAlmacen) cargarInventario()
        if (incluirReportes && s.puedeReportes) cargarReportes()
        guardarSnapshotActual()
    }

    fun cerrarSesion() {
        unobserveRed?.invoke(); unobserveRed = null
        kitchenJob?.cancel(); kitchenJob = null
        notifJob?.cancel(); notifListoJob?.cancel()
        session.token = null
        cocinaIniciada = false; idsCocina = emptySet(); idsNotificados.clear()
        salonIniciado = false; estadosSalonPrevios = emptyMap(); idsListosNotificados.clear()
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
            val pedidosSalon = pedidosSalonRes.getOrElse { sActual.pedidosSalon.filter { p -> p.idPedido > 0 } }
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

            if (pedidosSalonRes.isSuccess) {
                val estadosActuales = pedidosSalon.associate { it.idPedido to it.estado }
                if (salonIniciado && sActual.puedeMesas) {
                    val recienListos = pedidosSalon.filter { p ->
                        p.estado == "LISTO" &&
                            estadosSalonPrevios[p.idPedido] != "LISTO" &&
                            p.idPedido !in idsListosNotificados
                    }
                    if (recienListos.isNotEmpty()) {
                        recienListos.forEach { listo ->
                            idsListosNotificados.add(listo.idPedido)
                            val platos = listo.lineas.joinToString(", ") { "${it.cantidad}x ${it.nombre}" }
                                .ifBlank { "Platos listos para servir" }
                            avisarPedidoListo(
                                titulo = "Pedido LISTO · Mesa ${listo.codigoMesa}",
                                detalle = "${listo.codigoPedido} (${listo.codigoMozo}) · $platos",
                                idPedido = listo.idPedido
                            )
                        }
                        mostrarNotificacionPedidoListo(recienListos.first())
                    }
                }
                estadosSalonPrevios = estadosActuales
                salonIniciado = true
            }

            val idsActivos = pedidosSalon.map { it.idPedido }.toSet()
            val mesaActualizada = sActual.mesa?.let { ma -> mesas.find { it.idMesa == ma.idMesa } ?: ma }
            val pedidoActualizado = if (pedidosSalonRes.isSuccess && sActual.pedido?.idPedido != null && sActual.pedido.idPedido > 0) {
                if (sActual.pedido.idPedido in idsActivos) pedidosSalon.find { it.idPedido == sActual.pedido.idPedido } ?: sActual.pedido
                else if (sActual.pagina == "Comanda") null else sActual.pedido
            } else sActual.pedido
            val borradorActualizado = if (pedidosSalonRes.isSuccess && sActual.borrador?.idPedido != null && sActual.borrador.idPedido > 0 && sActual.borrador.idPedido !in idsActivos) {
                mesaActualizada?.let { Borrador(it.idMesa) }
            } else sActual.borrador

            _state.update {
                aplicarColaSobreEstado(
                    it.copy(
                        conectadoServidor = true,
                        mesas = mesas,
                        categorias = categorias,
                        productos = productos,
                        productosTodos = productosTodos,
                        pedidosSalon = pedidosSalon,
                        borradores = borradoresLimpiados,
                        mesa = mesaActualizada,
                        pedido = pedidoActualizado,
                        borrador = borradorActualizado
                    ),
                    it.colaSync
                )
            }
            guardarSnapshotActual()
        }.onFailure { e ->
            if (e is CancellationException) throw e
            if (esErrorDeConexion(e)) {
                _state.update { it.copy(conectadoServidor = false) }
            } else if (e is Exception) fallo(e)
        }
    }

    fun refrescar() = task {
        val s = _state.value
        if (s.colaSync.isNotEmpty()) {
            sincronizarPendientes(silencioso = true)
        }
        when (s.pagina) {
            "Cocina" -> if (s.puedeCocina) refrescarCocina()
            "Usuarios" -> if (s.puedeAdmin) { val u = repo.usuarios(); _state.update { it.copy(usuarios = u, conectadoServidor = true) } }
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
        if (!s.conectadoServidor) return
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
            val esOfflineLocal = activoEnMesa.idPedido < 0 || activoEnMesa.estado == "PEDIDO_POR_SINCRONIZAR"
            val esPropioOAdmin = esAdmin || activoEnMesa.codigoMozo.equals(miCodigo, ignoreCase = true)
            val avisoLectura = when {
                esOfflineLocal -> "Pedido tomado sin conexión · Estado: PEDIDO (POR SINCRONIZAR). Se enviará a cocina al volver la red."
                !esPropioOAdmin -> "Mesa atendida por ${activoEnMesa.codigoMozo} (${activoEnMesa.estado.replace('_', ' ')}). Vista de solo lectura."
                else -> null
            }
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
            if (esPropioOAdmin && !esOfflineLocal && s.conectadoServidor) {
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
        val b = _state.value.borrador ?: error("No hay borrador activo.")
        require(b.lineas.isNotEmpty()) { "Agrega al menos un plato a la comanda." }
        val llave = b.llaveIdempotencia.ifBlank {
            "PED-${b.idMesa}-${Clock.System.now().toEpochMilliseconds()}"
        }
        val ahoraIso = Clock.System.now().toString()

        if (b.idPedido != null && b.idPedido < 0) {
            val nuevaCola = _state.value.colaSync.map { acc ->
                if (acc.tipo == TipoAccionSync.ENVIAR_PEDIDO_MOZO && acc.idPedidoLocal == b.idPedido) {
                    acc.copy(lineas = b.lineas, observaciones = b.observaciones, fechaRegistroOffline = ahoraIso, ultimoError = null)
                } else acc
            }
            actualizarColaSync(nuevaCola)
            _state.update { it.copy(mensaje = "Pedido local actualizado (POR SINCRONIZAR).") }
            if (hayConexionRed()) sincronizarPendientes(silencioso = false)
            return@task
        }

        persistir(b.copy(llaveIdempotencia = llave))
        try {
            val res = if (b.idPedido == null) {
                repo.sincronizarPedidoOffline(b.idMesa, b.observaciones, b.lineas, llave, true, ahoraIso)
            } else {
                val editado = repo.editar(b.idPedido, b.lineas)
                repo.enviar(editado.idPedido)
            }
            val codigo = _state.value.sesion?.usuario?.codigoTrabajador
            val limpios = _state.value.borradores.filterNot { it.idMesa == res.idMesa }
            if (codigo != null) store.guardar(codigo, limpios)
            _state.update {
                it.copy(
                    conectadoServidor = true,
                    pedido = res,
                    borrador = Borrador(res.idMesa, res.lineas, res.observaciones.orEmpty(), res.idPedido, false, llave),
                    borradores = limpios,
                    modoSoloLecturaAviso = null,
                    mensaje = "Comanda enviada a cocina exitosamente"
                )
            }
            cargarSalon()
        } catch (e: Exception) {
            if (esErrorDeConexion(e)) {
                encolarPedidoMozoOffline(b.copy(llaveIdempotencia = llave), llave, ahoraIso)
            } else {
                throw e
            }
        }
    }

    private fun encolarPedidoMozoOffline(b: Borrador, llave: String, ahoraIso: String) {
        val s = _state.value
        val mesaObj = s.mesas.find { it.idMesa == b.idMesa } ?: s.mesa
        val codMesa = mesaObj?.codigoMesa ?: "M${b.idMesa}"
        val idLocal = b.idPedido ?: -kotlin.math.abs(Clock.System.now().toEpochMilliseconds()).coerceAtLeast(1L)
        val accion = AccionPendienteSync(
            idLocal = "sync-$llave",
            tipo = TipoAccionSync.ENVIAR_PEDIDO_MOZO,
            llaveIdempotencia = llave,
            descripcion = "Pedido $codMesa (${b.lineas.sumOf { it.cantidad }} platos)",
            idMesa = b.idMesa,
            codigoMesa = codMesa,
            idPedidoLocal = if (idLocal < 0) idLocal else null,
            idPedido = if (idLocal > 0) idLocal else null,
            observaciones = b.observaciones,
            lineas = b.lineas,
            enviarACocina = true,
            fechaRegistroOffline = ahoraIso
        )
        val nuevaCola = s.colaSync.filterNot { it.idLocal == accion.idLocal } + accion
        val usr = s.sesion?.usuario?.codigoTrabajador
        val borradoresSinMesa = s.borradores.filterNot { it.idMesa == b.idMesa }
        if (usr != null) store.guardar(usr, borradoresSinMesa)

        _state.update {
            it.copy(
                conectadoServidor = false,
                borradores = borradoresSinMesa,
                error = null
            )
        }
        actualizarColaSync(nuevaCola)
        val pedidoLocal = _state.value.pedidosSalon.find { it.idMesa == b.idMesa && (it.idPedido == idLocal || it.estado == "PEDIDO_POR_SINCRONIZAR") }
        _state.update {
            it.copy(
                pedido = pedidoLocal ?: it.pedido,
                borrador = pedidoLocal?.let { pl -> Borrador(b.idMesa, pl.lineas, pl.observaciones.orEmpty(), pl.idPedido, false, llave) } ?: it.borrador,
                modoSoloLecturaAviso = "Pedido tomado sin conexión · Estado: PEDIDO (POR SINCRONIZAR). Se enviará a cocina al volver la red.",
                mensaje = "Sin conexión: $codMesa pasó a PEDIDO (POR SINCRONIZAR)."
            )
        }
        guardarSnapshotActual()
    }

    fun trasladar(destino: Long, motivo: String) = task {
        val p = _state.value.pedido ?: error("No hay pedido guardado para trasladar.")
        require(p.idPedido > 0) { "Sincroniza el pedido antes de trasladarlo a otra mesa." }
        repo.trasladar(p.idPedido, destino, motivo)
        descartarLocal()
        cargarSalon()
        _state.update { it.copy(pagina = "Salón", mensaje = "Comanda trasladada a nueva mesa") }
    }

    // --- COCINA KDS (SPRINT 2) ---
    fun marcarPlatoListo(idDetalle: Long) = task {
        try {
            repo.marcarPlatoListo(idDetalle)
            _state.update { it.copy(conectadoServidor = true) }
            refrescarCocina()
            _state.update { it.copy(mensaje = "Plato marcado como listo") }
        } catch (e: Exception) {
            if (esErrorDeConexion(e)) {
                val s = _state.value
                val pedidoRel = (s.cocina + s.pedidosSalon).find { p -> p.lineas.any { it.idDetalle == idDetalle } }
                val nombrePlato = pedidoRel?.lineas?.find { it.idDetalle == idDetalle }?.nombre ?: "Plato #$idDetalle"
                val llave = "PLATO-$idDetalle"
                val yaEnCola = s.colaSync.any { it.tipo == TipoAccionSync.MARCAR_PLATO_LISTO && it.idDetalle == idDetalle }
                val nuevaCola = if (yaEnCola) s.colaSync else s.colaSync + AccionPendienteSync(
                    idLocal = "sync-$llave",
                    tipo = TipoAccionSync.MARCAR_PLATO_LISTO,
                    llaveIdempotencia = llave,
                    descripcion = "Listo: $nombrePlato (${pedidoRel?.codigoMesa.orEmpty()})",
                    idPedido = pedidoRel?.idPedido,
                    codigoMesa = pedidoRel?.codigoMesa.orEmpty(),
                    idDetalle = idDetalle
                )
                _state.update { it.copy(conectadoServidor = false, error = null) }
                actualizarColaSync(nuevaCola)
                guardarSnapshotActual()
                _state.update { it.copy(mensaje = "Sin conexión: '$nombrePlato' marcado como LISTO (POR SINCRONIZAR).") }
            } else {
                throw e
            }
        }
    }

    fun marcarPedidoListoCocina(idPedido: Long) = task {
        require(idPedido > 0) { "El pedido aún espera sincronización desde Salón." }
        try {
            val res = repo.marcarPedidoListoCocina(idPedido)
            _state.update { it.copy(conectadoServidor = true) }
            refrescarCocina()
            _state.update { it.copy(mensaje = "Pedido ${res.codigoPedido} completo listo en cocina") }
        } catch (e: Exception) {
            if (esErrorDeConexion(e)) {
                val s = _state.value
                val pedidoRel = (s.cocina + s.pedidosSalon).find { it.idPedido == idPedido }
                val llave = "PEDIDO-LISTO-$idPedido"
                val yaEnCola = s.colaSync.any { it.tipo == TipoAccionSync.MARCAR_PEDIDO_LISTO && it.idPedido == idPedido }
                val nuevaCola = if (yaEnCola) s.colaSync else s.colaSync + AccionPendienteSync(
                    idLocal = "sync-$llave",
                    tipo = TipoAccionSync.MARCAR_PEDIDO_LISTO,
                    llaveIdempotencia = llave,
                    descripcion = "Todo Listo ${pedidoRel?.codigoMesa ?: "#$idPedido"}",
                    idPedido = idPedido,
                    codigoMesa = pedidoRel?.codigoMesa.orEmpty()
                )
                _state.update { it.copy(conectadoServidor = false, error = null) }
                actualizarColaSync(nuevaCola)
                guardarSnapshotActual()
                _state.update { it.copy(mensaje = "Sin conexión: pedido marcado LISTO (POR SINCRONIZAR).") }
            } else {
                throw e
            }
        }
    }

    fun cambiarDisponibilidad(idProducto: Long, disponible: Boolean) = task {
        try {
            repo.cambiarDisponibilidad(idProducto, disponible)
            val prodActivos = if (_state.value.puedeMesas) runCatching { repo.productos() }.getOrNull() else null
            val todos = runCatching { repo.todosProductos() }.getOrNull()
            _state.update { s ->
                aplicarColaSobreEstado(
                    s.copy(
                        conectadoServidor = true,
                        productos = prodActivos ?: s.productos.filter { if (it.idProducto == idProducto) disponible else true },
                        productosTodos = todos ?: s.productosTodos.map { if (it.idProducto == idProducto) it.copy(disponible = disponible) else it },
                        mensaje = if (disponible) "Plato habilitado nuevamente en el menú" else "Plato marcado como agotado"
                    ),
                    s.colaSync
                )
            }
            guardarSnapshotActual()
        } catch (e: Exception) {
            if (esErrorDeConexion(e)) {
                val s = _state.value
                val nombre = s.productosTodos.find { it.idProducto == idProducto }?.nombre ?: "Producto #$idProducto"
                val accion = AccionPendienteSync(
                    idLocal = "sync-DISP-$idProducto",
                    tipo = TipoAccionSync.CAMBIAR_DISPONIBILIDAD,
                    llaveIdempotencia = "DISP-$idProducto-${Clock.System.now().toEpochMilliseconds()}",
                    descripcion = "${if (disponible) "Habilitar" else "Agotar"} $nombre",
                    idProducto = idProducto,
                    disponible = disponible
                )
                val nuevaCola = s.colaSync.filterNot { it.idLocal == accion.idLocal } + accion
                _state.update { it.copy(conectadoServidor = false, error = null) }
                actualizarColaSync(nuevaCola)
                guardarSnapshotActual()
                _state.update { it.copy(mensaje = "Sin conexión: cambio de '$nombre' guardado para sincronizar.") }
            } else {
                throw e
            }
        }
    }

    private suspend fun refrescarCocina() = kitchenLock.withLock {
        runCatching {
            val pedidos = repo.cocina()
            val todos = runCatching { repo.todosProductos() }.getOrNull()
            val nuevosIds = pedidos.map { it.idPedido }.toSet()
            val entrantes = (nuevosIds - idsCocina).filter { it !in idsNotificados }
            if (cocinaIniciada && _state.value.puedeCocina && entrantes.isNotEmpty()) {
                val nuevosPedidos = pedidos.filter { it.idPedido in entrantes }
                idsNotificados.addAll(entrantes)
                nuevosPedidos.forEach { nuevo ->
                    val platos = nuevo.lineas.joinToString(", ") { "${it.cantidad}x ${it.nombre}" }
                        .ifBlank { "Nueva comanda recibida" }
                    avisarPedido(
                        titulo = "Nuevo pedido en Cocina · Mesa ${nuevo.codigoMesa}",
                        detalle = "${nuevo.codigoPedido} (${nuevo.codigoMozo}) · $platos",
                        idPedido = nuevo.idPedido
                    )
                }
                nuevosPedidos.firstOrNull()?.let { mostrarNotificacionCocina(it) }
            }
            idsCocina = nuevosIds
            cocinaIniciada = true
            _state.update {
                aplicarColaSobreEstado(
                    it.copy(
                        conectadoServidor = true,
                        cocina = pedidos,
                        productosTodos = todos ?: it.productosTodos,
                        cocinaActualizada = Clock.System.now().toEpochMilliseconds(),
                        cocinaAviso = null
                    ),
                    it.colaSync
                )
            }
            guardarSnapshotActual()
        }.onFailure { e ->
            if (e is CancellationException) throw e
            _state.update {
                it.copy(
                    conectadoServidor = if (esErrorDeConexion(e)) false else it.conectadoServidor,
                    cocinaAviso = "KDS: ${e.message}"
                )
            }
        }
    }

    private var notifJob: Job? = null
    fun mostrarNotificacionCocina(pedido: Pedido) {
        notifJob?.cancel()
        _state.update { it.copy(nuevoPedidoCocinaAviso = pedido) }
        notifJob = viewModelScope.launch {
            delay(9000)
            _state.update { it.copy(nuevoPedidoCocinaAviso = null) }
        }
    }

    fun cerrarNotificacionCocina() {
        notifJob?.cancel()
        _state.update { it.copy(nuevoPedidoCocinaAviso = null) }
    }

    fun mostrarNotificacionPedidoListo(pedido: Pedido) {
        notifListoJob?.cancel()
        _state.update { it.copy(pedidoListoAviso = pedido) }
        notifListoJob = viewModelScope.launch {
            delay(9000)
            _state.update { it.copy(pedidoListoAviso = null) }
        }
    }

    fun cerrarNotificacionPedidoListo() {
        notifListoJob?.cancel()
        _state.update { it.copy(pedidoListoAviso = null) }
    }

    private fun iniciarMonitorRedYEventos() {
        unobserveRed?.invoke()
        unobserveRed = observarConectividad { disponible ->
            _state.update { it.copy(conectadoServidor = disponible) }
            if (disponible && _state.value.sesion != null) {
                viewModelScope.launch {
                    sincronizarPendientes(silencioso = false)
                    recargarSegunPermisos()
                }
            }
        }
        if (kitchenJob != null) return
        kitchenJob = viewModelScope.launch {
            launch {
                destinosNotificacion().collect { destino ->
                    limpiarDestinoNotificacion()
                    val sActual = _state.value
                    if (sActual.sesion != null) {
                        if (destino == "Cocina" && sActual.puedeCocina) {
                            navegar("Cocina")
                        } else if (destino == "Salón" && sActual.puedeMesas) {
                            navegar("Salón")
                        }
                    }
                }
            }
            launch {
                while (isActive) {
                    runCatching {
                        repo.eventosCocina().collect {
                            _state.update { it.copy(socketConectado = true, conectadoServidor = true, cocinaAviso = null) }
                            sincronizarPendientes(silencioso = true)
                            recargarSegunPermisos()
                        }
                    }.onFailure { e ->
                        if (e is CancellationException) throw e
                        _state.update {
                            it.copy(
                                socketConectado = false,
                                cocinaAviso = if (it.puedeCocina) "KDS: ${e.message}" else null
                            )
                        }
                        delay(5000)
                    }
                }
            }
            while (isActive) {
                delay(if (_state.value.socketConectado) 12000 else 4000)
                if (_state.value.colaSync.isNotEmpty() && hayConexionRed()) {
                    sincronizarPendientes(silencioso = true)
                } else if (!_state.value.socketConectado && hayConexionRed()) {
                    recargarSegunPermisos()
                }
            }
        }
    }

    fun sincronizarAhora() {
        viewModelScope.launch {
            val cant = sincronizarPendientes(silencioso = false)
            recargarSegunPermisos()
            if (cant == 0 && _state.value.colaSync.isEmpty() && _state.value.conectadoServidor) {
                _state.update { it.copy(mensaje = "Todo está sincronizado al día.") }
            }
        }
    }

    suspend fun sincronizarPendientes(silencioso: Boolean = false): Int = syncLock.withLock {
        val colaOriginal = _state.value.colaSync
        if (colaOriginal.isEmpty()) return 0
        val usr = _state.value.sesion?.usuario?.codigoTrabajador ?: return 0

        _state.update { it.copy(sincronizandoCola = true) }
        val pendientesRestantes = colaOriginal.toMutableList()
        var exitosas = 0
        var cortoRed = false

        try {
            val iter = colaOriginal.toList()
            for (accion in iter) {
                try {
                    when (accion.tipo) {
                        TipoAccionSync.ENVIAR_PEDIDO_MOZO -> {
                            val res = if (accion.idPedido != null && accion.idPedido > 0) {
                                val ed = repo.editar(accion.idPedido, accion.lineas)
                                if (accion.enviarACocina) repo.enviar(ed.idPedido) else ed
                            } else {
                                repo.sincronizarPedidoOffline(
                                    idMesa = accion.idMesa ?: error("Mesa inválida"),
                                    observaciones = accion.observaciones,
                                    lineas = accion.lineas,
                                    idempotencia = accion.llaveIdempotencia,
                                    enviarACocina = accion.enviarACocina,
                                    fechaRegistroOffline = accion.fechaRegistroOffline
                                )
                            }
                            if (_state.value.pedido?.idPedido == accion.idPedidoLocal) {
                                _state.update {
                                    it.copy(
                                        pedido = res,
                                        borrador = Borrador(res.idMesa, res.lineas, res.observaciones.orEmpty(), res.idPedido, false, accion.llaveIdempotencia),
                                        modoSoloLecturaAviso = null
                                    )
                                }
                            }
                        }
                        TipoAccionSync.MARCAR_PLATO_LISTO -> {
                            repo.marcarPlatoListo(accion.idDetalle ?: error("Detalle inválido"))
                        }
                        TipoAccionSync.MARCAR_PEDIDO_LISTO -> {
                            repo.marcarPedidoListoCocina(accion.idPedido ?: error("Pedido inválido"))
                        }
                        TipoAccionSync.CAMBIAR_DISPONIBILIDAD -> {
                            repo.cambiarDisponibilidad(accion.idProducto ?: error("Producto inválido"), accion.disponible ?: true)
                        }
                        TipoAccionSync.COBRAR_PEDIDO -> {
                            val idPed = accion.idPedido ?: error("Pedido inválido")
                            val idTurn = accion.idTurnoCaja ?: _state.value.turnoCaja?.idTurno ?: error("Sin turno de caja")
                            val met = (accion.metodoPago ?: "EFECTIVO").uppercase()
                            val pago = when (met) {
                                "EFECTIVO" -> repo.cobrarEfectivo(idPed, idTurn, accion.recibidoEfectivo, accion.totalPedido)
                                "MIXTO" -> repo.cobrarMixto(idPed, idTurn, accion.partesMixto.map { Triple(it.metodo, it.importe, it.operacion) })
                                else -> repo.cobrarDigital(idPed, idTurn, met, accion.totalPedido, accion.operacionDigital, accion.ultimos4Digital)
                            }
                            if (accion.emitirSunat) {
                                val nomClean = accion.razonSocial.trim()
                                val numClean = accion.numDoc.trim()
                                val tipoComp = accion.tipoComprobante.trim().uppercase()
                                val modalidad = when {
                                    tipoComp == "FACTURA" && nomClean.isNotBlank() -> "MANUAL"
                                    tipoComp == "FACTURA" -> "RUC"
                                    accion.tipoDoc == "Sin Doc" || (numClean.isBlank() && nomClean.isBlank()) -> "SIN_DATOS"
                                    nomClean.isNotBlank() -> "MANUAL"
                                    accion.tipoDoc.equals("DNI", ignoreCase = true) -> "DNI"
                                    accion.tipoDoc.equals("RUC", ignoreCase = true) -> "RUC"
                                    else -> "MANUAL"
                                }
                                val reqComp = pe.edu.upeu.nullpointerpos.data.remote.dto.SolicitudEmitirComprobante(
                                    idPedido = idPed,
                                    idPago = pago.idPago,
                                    modalidad = modalidad,
                                    tipoComprobante = tipoComp,
                                    tipoDocumento = when (modalidad) {
                                        "SIN_DATOS" -> null
                                        "DNI" -> "DNI"
                                        "RUC" -> "RUC"
                                        else -> if (accion.tipoDoc == "Sin Doc" || numClean.isBlank()) null else accion.tipoDoc.uppercase()
                                    },
                                    numeroDocumento = numClean.ifBlank { null },
                                    nombreCompleto = nomClean.ifBlank { null },
                                    direccion = accion.direccion.trim().ifBlank { null },
                                    telefono = accion.telefonoWhatsapp?.trim()?.ifBlank { null }
                                )
                                guardarEmisionPendiente(reqComp)
                                runCatching {
                                    val cpe = repo.emitirComprobanteSunat(reqComp)
                                    actualizarEmisionPendiente(reqComp, cpe)
                                }
                            }
                            if (accion.enviarWhatsapp && !accion.telefonoWhatsapp.isNullOrBlank()) {
                                val tel = accion.telefonoWhatsapp.filter { it.isDigit() || it == '+' }
                                runCatching { repo.ticketWhatsApp(idPed, tel, idTurn) }
                            }
                        }
                    }
                    pendientesRestantes.removeAll { it.idLocal == accion.idLocal }
                    exitosas++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (esErrorDeConexion(e)) {
                        cortoRed = true
                        _state.update { it.copy(conectadoServidor = false) }
                        break
                    } else {
                        val idx = pendientesRestantes.indexOfFirst { it.idLocal == accion.idLocal }
                        if (idx >= 0) {
                            pendientesRestantes[idx] = pendientesRestantes[idx].copy(ultimoError = e.message ?: "Error al sincronizar")
                        }
                        _state.update { it.copy(error = "Conflicto al sincronizar (${accion.descripcion}): ${e.message}") }
                    }
                }
            }
        } finally {
            store.guardarColaSync(usr, pendientesRestantes)
            _state.update {
                aplicarColaSobreEstado(
                    it.copy(
                        sincronizandoCola = false,
                        conectadoServidor = if (cortoRed) false else true
                    ),
                    pendientesRestantes
                )
            }
        }

        if (exitosas > 0) {
            recargarSegunPermisos()
            if (!silencioso || pendientesRestantes.isEmpty()) {
                val txt = if (exitosas == 1) "1 operación sincronizada" else "$exitosas operaciones sincronizadas"
                _state.update { it.copy(mensaje = "Conexión restablecida: $txt con éxito.") }
            }
        }
        return exitosas
    }

    // --- CAJA Y COBROS (SPRINT 2 Y 3) ---
    private suspend fun cargarTurnoCaja() {
        runCatching {
            val turno = repo.turnoAbierto()
            _state.update { it.copy(conectadoServidor = true, turnoCaja = if (turno != null && turno.estado == "ABIERTO") turno else null) }
            guardarSnapshotActual()
        }.onFailure { e ->
            if (e is CancellationException) throw e
            if (esErrorDeConexion(e)) {
                _state.update { it.copy(conectadoServidor = false) }
            } else if (e is Exception) fallo(e)
        }
    }

    private suspend fun cargarCajaDatos() {
        cargarTurnoCaja()
        runCatching {
            val pendientes = repo.pendientesCobro()
            _state.update { s ->
                val actual = s.pedido
                val actualizado = if (actual != null && actual.idPedido > 0 && s.pagina == "Caja") {
                    pendientes.find { it.idPedido == actual.idPedido }
                } else actual
                aplicarColaSobreEstado(s.copy(conectadoServidor = true, pendientesCobro = pendientes, pedido = actualizado), s.colaSync)
            }
            guardarSnapshotActual()
        }.onFailure { e ->
            if (e is CancellationException) throw e
            if (esErrorDeConexion(e)) {
                _state.update { it.copy(conectadoServidor = false) }
            } else if (e is Exception) fallo(e)
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
        require(idPedido > 0) { "Este pedido aún está en estado PEDIDO (POR SINCRONIZAR). Sincronízalo al volver la red antes de cobrar." }
        val idMesa = idMesaOpt
            ?: _state.value.pedido?.idMesa
            ?: _state.value.pendientesCobro.find { it.idPedido == idPedido }?.idMesa
            ?: _state.value.mesa?.idMesa

        val met = metodoPago.uppercase()
        val pago = try {
            when (met) {
                "EFECTIVO" -> repo.cobrarEfectivo(idPedido, t.idTurno, recibidoEfectivo, totalPedido)
                "MIXTO" -> repo.cobrarMixto(idPedido, t.idTurno, partesMixto)
                else -> repo.cobrarDigital(idPedido, t.idTurno, met, totalPedido, operacionDigital, ultimos4Digital)
            }
        } catch (e: Exception) {
            if (esErrorDeConexion(e)) {
                val pedObj = _state.value.pendientesCobro.find { it.idPedido == idPedido } ?: _state.value.pedido
                val llave = "APP-PAGO-$idPedido"
                val accion = AccionPendienteSync(
                    idLocal = "sync-$llave",
                    tipo = TipoAccionSync.COBRAR_PEDIDO,
                    llaveIdempotencia = llave,
                    descripcion = "Cobro ${pedObj?.codigoMesa ?: "#$idPedido"} ($met ${dinero((totalPedido * 100.0).toLong())})",
                    idMesa = idMesa,
                    codigoMesa = pedObj?.codigoMesa.orEmpty(),
                    idPedido = idPedido,
                    idTurnoCaja = t.idTurno,
                    metodoPago = met,
                    recibidoEfectivo = recibidoEfectivo,
                    totalPedido = totalPedido,
                    operacionDigital = operacionDigital,
                    ultimos4Digital = ultimos4Digital,
                    partesMixto = partesMixto.map { ParteCobroSync(it.first, it.second, it.third) },
                    emitirSunat = emitirSunat,
                    tipoComprobante = tipoComprobante,
                    tipoDoc = tipoDoc,
                    numDoc = numDoc,
                    razonSocial = razonSocial,
                    direccion = direccion,
                    telefonoWhatsapp = telefonoWhatsapp,
                    enviarWhatsapp = enviarWhatsapp
                )
                val nuevaCola = _state.value.colaSync.filterNot { it.idLocal == accion.idLocal } + accion
                liberarMesaYLimpiarPedido(idPedido, idMesa)
                _state.update { it.copy(conectadoServidor = false, error = null) }
                actualizarColaSync(nuevaCola)
                guardarSnapshotActual()
                _state.update {
                    it.copy(
                        transaccionTarjeta = null,
                        clienteSeleccionado = null,
                        buscandoCliente = false,
                        clienteAviso = null,
                        mensaje = "Sin conexión: cobro #Pedido ($met) en cola (POR SINCRONIZAR). Se enviará a Caja y SUNAT al volver la red."
                    )
                }
                return@task
            }
            throw e
        }

        _state.update { it.copy(conectadoServidor = true) }
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
            guardarEmisionPendiente(reqComp)
            try {
                comprobante = repo.emitirComprobanteSunat(reqComp)
                actualizarEmisionPendiente(reqComp, comprobante)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { avisoExtra += " · Comprobante pendiente. Puedes recuperarlo desde Caja: ${e.message}" }
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
                buscandoCliente = false,
                clienteAviso = null,
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
                mensaje = "Comprobante ${res.numeroFinal}: ${res.estadoFinal}"
            )
        }
    }

    private fun guardarEmisionPendiente(solicitud: pe.edu.upeu.nullpointerpos.data.remote.dto.SolicitudEmitirComprobante) {
        val codigo = _state.value.sesion?.usuario?.codigoTrabajador ?: return
        val lista = _state.value.pendientesEmision.filterNot { it.idPago == solicitud.idPago } + solicitud
        store.guardarEmisiones(codigo, lista)
        _state.update { it.copy(pendientesEmision = lista) }
    }

    private fun actualizarEmisionPendiente(solicitud: pe.edu.upeu.nullpointerpos.data.remote.dto.SolicitudEmitirComprobante, respuesta: pe.edu.upeu.nullpointerpos.data.remote.dto.RespuestaComprobanteSunat) {
        if (respuesta.estadoFinal.startsWith("PENDIENTE")) return
        val codigo = _state.value.sesion?.usuario?.codigoTrabajador ?: return
        val lista = _state.value.pendientesEmision.filterNot { it.idPago == solicitud.idPago }
        store.guardarEmisiones(codigo, lista)
        _state.update { it.copy(pendientesEmision = lista) }
    }

    fun recuperarComprobante(solicitud: pe.edu.upeu.nullpointerpos.data.remote.dto.SolicitudEmitirComprobante) = task {
        val respuesta = repo.emitirComprobanteSunat(solicitud)
        actualizarEmisionPendiente(solicitud, respuesta)
        _state.update { it.copy(comprobanteEmitido = respuesta, mensaje = "Comprobante ${respuesta.numeroFinal}: ${respuesta.estadoFinal}") }
    }

    fun cerrarComprobanteModal() {
        _state.update { it.copy(comprobanteEmitido = null, whatsappResultado = null, pdfVistaPrevia = null) }
    }

    fun limpiarCliente() {
        clienteJob?.cancel()
        _state.update { it.copy(clienteSeleccionado = null, buscandoCliente = false, clienteAviso = null) }
    }

    fun buscarCliente(tipo: String, numero: String) {
        val tipoNorm = tipo.trim().uppercase()
        val numNorm = numero.trim()
        clienteJob?.cancel()
        _state.update { it.copy(clienteSeleccionado = null, buscandoCliente = true, clienteAviso = null, error = null) }
        clienteJob = viewModelScope.launch {
            try {
                val cli = repo.buscarCliente(tipoNorm, numNorm)
                if (cli != null) {
                    val etiquetaOrigen = when (cli.origenDatos.uppercase()) {
                        "APISPERU" -> "APISPERU"
                        "DECOLECTA" -> "Decolecta"
                        "REGISTRO_LOCAL" -> "BD local"
                        "DEMO_LOCAL" -> "Demo local"
                        else -> ""
                    }
                    val sufijo = if (etiquetaOrigen.isNotBlank()) " ($etiquetaOrigen)" else ""
                    _state.update {
                        it.copy(
                            clienteSeleccionado = cli,
                            clienteAviso = null,
                            mensaje = "Cliente encontrado$sufijo: ${cli.razonSocial}"
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            clienteSeleccionado = null,
                            clienteAviso = "No registrado en padrón. Ingresa los datos manualmente (se guardarán para la próxima venta)."
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is FalloApi && e.codigo == 401) {
                    fallo(e)
                } else {
                    _state.update { it.copy(clienteAviso = e.message ?: "No se pudo consultar el documento") }
                }
            } finally {
                _state.update { it.copy(buscandoCliente = false) }
            }
        }
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
        if (bytes.isEmpty()) {
            _state.update { it.copy(error = "El PDF del comprobante está vacío.") }
            return@task
        }
        val nombre = numeroComprobante.ifBlank { "Comprobante-$idComprobante" }
        if (compartir) {
            val error = pe.edu.upeu.nullpointerpos.platform.abrirPdfLocal(nombre, bytes, compartir = true)
            _state.update {
                if (error != null) it.copy(error = error)
                else it.copy(mensaje = "Compartiendo PDF $nombre...")
            }
        } else {
            _state.update {
                it.copy(
                    pdfVistaPrevia = PdfComprobanteUi(nombre = nombre, bytes = bytes),
                    error = null
                )
            }
        }
    }

    fun abrirPdfExterno(compartir: Boolean) {
        val pdf = _state.value.pdfVistaPrevia ?: return
        val error = pe.edu.upeu.nullpointerpos.platform.abrirPdfLocal(pdf.nombre, pdf.bytes, compartir)
        _state.update {
            if (error != null) it.copy(pdfVistaPrevia = null, error = error)
            else it.copy(
                error = null,
                mensaje = if (compartir) "Compartiendo PDF ${pdf.nombre}..." else "Abriendo PDF 80mm ${pdf.nombre}..."
            )
        }
    }

    fun cerrarVistaPreviaPdf() {
        _state.update { it.copy(pdfVistaPrevia = null) }
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
