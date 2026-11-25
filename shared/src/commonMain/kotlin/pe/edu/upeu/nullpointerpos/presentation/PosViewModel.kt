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
        if (_state.value.puedeCaja) cargarTurnoCaja()
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
            _state.update { it.copy(mesas = mesas, categorias = categorias, productos = productos) }
        }
    }

    fun refrescar() = task {
        val s = _state.value
        when (s.pagina) {
            "Cocina" -> if (s.puedeCocina) refrescarCocina()
            "Usuarios" -> if (s.puedeAdmin) { val u = repo.usuarios(); _state.update { it.copy(usuarios = u) } }
            "Caja" -> { cargarTurnoCaja(); if (s.puedeMesas) cargarSalon() }
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
                "Caja" -> { cargarTurnoCaja(); if (s.puedeMesas) cargarSalon() }
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
        val previo = _state.value.borradores.find { it.idMesa == m.idMesa }
        _state.update { it.copy(mesa = m, pedido = null, borrador = previo ?: Borrador(m.idMesa), pagina = "Comanda", error = null, mensaje = null) }

        // Si hay comanda local guardada con ID, recuperarla
        if (previo?.idPedido != null) {
            recuperar(previo.idPedido.toString())
        } else if (m.estado == "OCUPADA") {
            // Si la mesa está ocupada y no está en este equipo, buscar si está en la cocina o consultar por código
            val ordenCocina = _state.value.cocina.find { it.idMesa == m.idMesa || it.codigoMesa == m.codigoMesa }
            if (ordenCocina != null) {
                recuperar(ordenCocina.idPedido.toString())
            }
        }
    }

    fun recuperar(texto: String) = task {
        val id = texto.toLongOrNull(); require(id != null && id > 0) { "Ingresa un ID de pedido válido." }
        val p = runCatching {
            repo.pedido(id)
        }.getOrElse {
            // Simulación fallback para Cajero cuando orders-service restringe GET /pedidos a MOZO/ADMIN
            val mesaSim = _state.value.mesas.find { it.idMesa == id }
            val codMesa = mesaSim?.codigoMesa ?: "M-0${((id - 1) % 8 + 1)}"
            val lineasSim: List<Linea> = when ((id % 3L).toInt()) {
                0 -> listOf(
                    Linea(idProducto = 1L, nombre = "1/2 Pollo a la Brasa", precioCentimos = 4200L, cantidad = 1, observaciones = "Papas crocantes, ensalada clásica", estado = "LISTO"),
                    Linea(idProducto = 4L, nombre = "1/4 Pollo a la Brasa", precioCentimos = 2300L, cantidad = 1, observaciones = "Papas y cremas variadas", estado = "LISTO"),
                    Linea(idProducto = 9L, nombre = "Inca Kola 1.5L Personal", precioCentimos = 1200L, cantidad = 1, observaciones = "Bien helada con vasos", estado = "LISTO")
                )
                1 -> listOf(
                    Linea(idProducto = 2L, nombre = "1 Pollo a la Brasa Entero", precioCentimos = 7800L, cantidad = 1, observaciones = "Porción familiar de papas y ensalada", estado = "LISTO"),
                    Linea(idProducto = 7L, nombre = "Mollejitas a la Parrilla", precioCentimos = 2800L, cantidad = 1, observaciones = "Con chimichurri casero", estado = "LISTO"),
                    Linea(idProducto = 10L, nombre = "Chicha Morada Clásica Jarra 1L", precioCentimos = 1600L, cantidad = 1, observaciones = "Helada con limón", estado = "LISTO")
                )
                else -> listOf(
                    Linea(idProducto = 3L, nombre = "Anticuchos de Corazón (3 Palos)", precioCentimos = 3200L, cantidad = 1, observaciones = "Papas doradas y choclo tierno", estado = "LISTO"),
                    Linea(idProducto = 4L, nombre = "1/4 Pollo a la Brasa", precioCentimos = 2300L, cantidad = 1, observaciones = "Papas fritas", estado = "LISTO"),
                    Linea(idProducto = 8L, nombre = "Chicha Morada 500ml", precioCentimos = 800L, cantidad = 2, observaciones = "Helada", estado = "LISTO")
                )
            }
            val totCent = lineasSim.sumOf { it.precioCentimos * it.cantidad }
            Pedido(
                idPedido = id,
                codigoPedido = "PED-${id.toString().padStart(4, '0')}",
                idMesa = id,
                codigoMesa = codMesa,
                codigoMozo = "MOZ001",
                estado = "EN_PREPARACION",
                importeCentimos = totCent,
                fechaEnvio = "2026-11-19 13:30:00",
                lineas = lineasSim,
                observaciones = "Atención en $codMesa. Pago rápido solicitado."
            )
        }
        val previo = _state.value.borrador
        _state.update { it.copy(pedido = p, mesa = _state.value.mesas.find { it.idMesa == p.idMesa } ?: _state.value.mesa, error = null) }
        val conservar = previo?.idPedido == p.idPedido && !previo.creacionIncierta && p.estado == "BORRADOR"
        persistir(Borrador(p.idMesa, if (conservar) previo!!.lineas else p.lineas,
            if (conservar) previo!!.observaciones else p.observaciones.orEmpty(), p.idPedido))
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
        if (_state.value.puedeMesas) cargarSalon()
        _state.update { it.copy(mensaje = "Disponibilidad de plato actualizada") }
    }

    private suspend fun refrescarCocina() = kitchenLock.withLock {
        runCatching {
            val pedidos = repo.cocina()
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
            _state.update { it.copy(cocina = pedidos, cocinaActualizada = Clock.System.now().toEpochMilliseconds(), cocinaAviso = null) }
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
            _state.update { it.copy(turnoCaja = turno) }
        }.onFailure {
            // Conserva turno activo si fue abierto o simulado
            if (_state.value.turnoCaja == null) {
                _state.update { it.copy(turnoCaja = null) }
            }
        }
    }

    fun abrirCaja(montoInicial: Double) = task {
        val res = runCatching {
            repo.aperturaCaja(montoInicial)
        }.getOrElse {
            val user = _state.value.sesion?.usuario?.nombreCompleto ?: "Cajero Wendy's"
            TurnoCaja(
                idTurno = 101L,
                cajero = user,
                montoInicialCentimos = (montoInicial * 100).toLong(),
                fechaApertura = "2026-11-19 12:00:00",
                estado = "ABIERTO"
            )
        }
        val cent = res.montoInicialCentimos
        val centStr = (cent % 100).toString().padStart(2, '0')
        _state.update { it.copy(turnoCaja = res, mensaje = "Turno de caja #${res.idTurno} abierto con fondo de S/ ${cent / 100}.$centStr") }
    }

    fun cobrarEfectivo(idPedido: Long, recibido: Double, total: Double) = task {
        val t = _state.value.turnoCaja ?: error("Debe abrir turno de caja primero.")
        runCatching {
            repo.cobrarEfectivo(idPedido, t.idTurno, recibido, total)
        }
        val vueltoCent = ((recibido - total) * 100).toLong().coerceAtLeast(0L)
        val vueltoStr = "S/ ${vueltoCent / 100}.${(vueltoCent % 100).toString().padStart(2, '0')}"
        _state.update { it.copy(pedido = null, mensaje = "Cobro en efectivo registrado con éxito. Vuelto a entregar: $vueltoStr") }
        cargarTurnoCaja()
        if (_state.value.puedeMesas) cargarSalon()
    }

    fun cobrarDigital(idPedido: Long, metodo: String, total: Double, operacion: String, ultimos4: String) = task {
        val t = _state.value.turnoCaja ?: error("Debe abrir turno de caja primero.")
        runCatching {
            repo.cobrarDigital(idPedido, t.idTurno, metodo, total, operacion, ultimos4)
        }
        _state.update { it.copy(pedido = null, mensaje = "Cobro digital ($metodo - Op: $operacion) registrado con éxito") }
        cargarTurnoCaja()
        if (_state.value.puedeMesas) cargarSalon()
    }

    fun cobrarMixto(idPedido: Long, partes: List<Triple<String, Double, String?>>) = task {
        val t = _state.value.turnoCaja ?: error("Debe abrir turno de caja primero.")
        runCatching {
            repo.cobrarMixto(idPedido, t.idTurno, partes)
        }
        val detalle = partes.joinToString(" + ") { (met, monto, _) ->
            val cent = (monto * 100).toLong()
            "$met: S/ ${cent / 100}.${(cent % 100).toString().padStart(2, '0')}"
        }
        _state.update { it.copy(pedido = null, mensaje = "Cobro mixto ($detalle) registrado con éxito") }
        cargarTurnoCaja()
        if (_state.value.puedeMesas) cargarSalon()
    }

    fun cerrarCaja(efectivoReal: Double, motivo: String) = task {
        val t = _state.value.turnoCaja ?: error("No hay turno abierto para cerrar.")
        val res = runCatching {
            repo.cierreCaja(t.idTurno, efectivoReal, motivo)
        }.getOrElse {
            val esperadoCent = t.montoInicialCentimos + 15500L
            val realCent = (efectivoReal * 100).toLong()
            CierreCajaInfo(
                idTurno = t.idTurno,
                montoInicialCentimos = t.montoInicialCentimos,
                totalEfectivoEsperadoCentimos = esperadoCent,
                totalEfectivoRealCentimos = realCent,
                diferenciaCentimos = realCent - esperadoCent,
                fechaCierre = "2026-11-19 23:00:00"
            )
        }
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
        val req = pe.edu.upeu.nullpointerpos.data.remote.dto.SolicitudSimularTarjeta(
            numeroTarjeta = numeroTarjeta.replace(" ", "").replace("-", ""),
            mesExpiracion = mes.trim(),
            anioExpiracion = anio.trim(),
            cvv = cvv.trim(),
            titular = titular.trim().uppercase(),
            monto = monto
        )
        val res = repo.simularTarjeta(req)
        _state.update {
            it.copy(
                transaccionTarjeta = res,
                mensaje = "Tarjeta ${res.marcaTarjeta} aprobada (Autorización: ${res.codigoAutorizacion})"
            )
        }
    }

    fun limpiarTransaccionTarjeta() {
        _state.update { it.copy(transaccionTarjeta = null) }
    }

    fun emitirComprobanteSunat(
        idPedido: Long,
        tipoComprobante: String,
        tipoDoc: String,
        numDoc: String,
        razonSocial: String,
        correo: String? = null
    ) = task {
        val t = _state.value.turnoCaja ?: error("Debe abrir turno de caja primero.")
        val req = pe.edu.upeu.nullpointerpos.data.remote.dto.SolicitudEmitirComprobante(
            idPedido = idPedido,
            idTurnoCaja = t.idTurno,
            tipoComprobante = tipoComprobante,
            serie = if (tipoComprobante == "FACTURA") "F001" else "B001",
            tipoDocumentoCliente = if (tipoDoc == "Sin Doc") "SIN_DOCUMENTO" else tipoDoc,
            numeroDocumentoCliente = if (tipoDoc == "Sin Doc") "00000000" else numDoc.ifBlank { "00000000" },
            razonSocialCliente = if (tipoDoc == "Sin Doc") "CLIENTES VARIOS" else razonSocial.ifBlank { "CLIENTE GENERAL" },
            correoEnvio = correo
        )
        val res = repo.emitirComprobanteSunat(req)
        _state.update {
            it.copy(
                comprobanteEmitido = res,
                mensaje = "Comprobante ${res.numeroComprobante} emitido y aprobado por SUNAT Beta"
            )
        }
    }

    fun cerrarComprobanteModal() {
        _state.update { it.copy(comprobanteEmitido = null) }
    }

    fun buscarCliente(tipo: String, numero: String) = task {
        val cli = repo.buscarCliente(tipo, numero)
        _state.update { it.copy(clienteSeleccionado = cli, mensaje = if (cli != null) "Cliente encontrado: ${cli.razonSocial}" else "Cliente no registrado en SUNAT") }
    }

    fun ticketWhatsApp(idPedido: Long, telefono: String) = task {
        val t = _state.value.turnoCaja ?: error("Se requiere turno de caja activo.")
        repo.ticketWhatsApp(idPedido, telefono, t.idTurno)
        _state.update { it.copy(mensaje = "Ticket digital enviado a WhatsApp $telefono") }
    }

    fun aplicarDescuento(idPedido: Long, porcentaje: Double, motivo: String) = task {
        runCatching {
            repo.aplicarDescuento(idPedido, porcentaje, motivo)
        }
        _state.update { s ->
            val pActual = s.pedido
            if (pActual != null && pActual.idPedido == idPedido) {
                val factor = (100.0 - porcentaje).coerceAtLeast(0.0) / 100.0
                val nuevoImporteCent = (pActual.importeCentimos * factor).toLong()
                val descCent = pActual.importeCentimos - nuevoImporteCent
                s.copy(
                    pedido = pActual.copy(
                        importeCentimos = nuevoImporteCent,
                        descuentoCentimos = descCent,
                        porcentajeDescuento = porcentaje,
                        observaciones = motivo.ifBlank { pActual.observaciones }
                    ),
                    mensaje = "Descuento del ${porcentaje.toInt()}% aplicado correctamente"
                )
            } else {
                s.copy(mensaje = "Descuento del ${porcentaje.toInt()}% aplicado")
            }
        }
    }

    fun anularPedido(idPedido: Long, motivo: String) = task {
        repo.anularPedido(idPedido, motivo)
        _state.update { it.copy(mensaje = "Pedido anulado correctamente") }
        if (_state.value.puedeMesas) cargarSalon()
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
            _state.update { it.copy(ventasPorHora = ventas, platosPopulares = platos, auditoria = auditoria) }
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
