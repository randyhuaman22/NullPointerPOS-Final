// Autor: Randy Huaman
package pe.edu.upeu.nullpointerpos.platform
import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import java.io.File
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import pe.edu.upeu.nullpointerpos.theme.AmbarDorado
import pe.edu.upeu.nullpointerpos.theme.Borgona

const val EXTRA_DESTINO_NOTIFICACION = "destino_notificacion"
private const val CANAL_COCINA_ID = "wendys_cocina_v2"
private const val CANAL_MOZO_ID = "wendys_mozo_listo_v2"

lateinit var androidContext: Context
var currentActivityRef: WeakReference<Activity>? = null
private val destinoNotificacionState = MutableStateFlow<String?>(null)

fun registrarDestinoNotificacion(destino: String?) {
    if (!destino.isNullOrBlank()) {
        destinoNotificacionState.value = destino
    }
}

actual fun destinosNotificacion(): Flow<String> = destinoNotificacionState.filterNotNull()

actual fun limpiarDestinoNotificacion() {
    destinoNotificacionState.value = null
}

actual fun preferencias(): Preferencias = object : Preferencias {
    private val prefs = androidContext.getSharedPreferences("nullpointer_pos", Context.MODE_PRIVATE)
    override fun leer(clave: String) = prefs.getString(clave, null)
    override fun guardar(clave: String, valor: String) { prefs.edit().putString(clave, valor).commit() }
}
actual fun motorHttp(): HttpClientEngine = OkHttp.create()
actual fun servidorInicial(): String {
    val emulador = android.os.Build.FINGERPRINT.startsWith("generic") || android.os.Build.MODEL.contains("sdk_gphone", ignoreCase = true)
    return if (emulador) "http://10.0.2.2:8080" else "http://10.40.79.37:8080"
}

private fun obtenerUriSonido(nombreRaw: String): Uri {
    val resId = androidContext.resources.getIdentifier(nombreRaw, "raw", androidContext.packageName)
    return if (resId != 0) {
        Uri.parse("android.resource://${androidContext.packageName}/$resId")
    } else {
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }
}

private fun reproducirSonidoFallback(nombreRaw: String) {
    runCatching {
        val am = androidContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (am != null && am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val resId = androidContext.resources.getIdentifier(nombreRaw, "raw", androidContext.packageName)
        if (resId != 0) {
            val mp = MediaPlayer.create(androidContext, resId)
            if (mp != null) {
                mp.setOnCompletionListener { it.release() }
                mp.setOnErrorListener { player, _, _ -> player.release(); true }
                mp.start()
                return
            }
        }
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
        tone.startTone(ToneGenerator.TONE_PROP_ACK, 240)
        Handler(Looper.getMainLooper()).postDelayed({ runCatching { tone.release() } }, 450)
    }
}

actual fun prepararNotificaciones() {
    if (!::androidContext.isInitialized) return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    runCatching {
        val manager = androidContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val canalCocina = NotificationChannel(
            CANAL_COCINA_ID,
            "Comandas nuevas en Cocina",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notificaciones con sonido cuando el mozo envía un pedido a cocina"
            setSound(obtenerUriSonido("sonido_pedido_nuevo"), audioAttrs)
            enableLights(true)
            lightColor = 0xFF7C1D26.toInt()
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 220L, 120L, 220L)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val canalMozo = NotificationChannel(
            CANAL_MOZO_ID,
            "Pedidos listos para Mozo",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notificaciones con sonido cuando cocina marca un pedido como listo"
            setSound(obtenerUriSonido("sonido_pedido_listo"), audioAttrs)
            enableLights(true)
            lightColor = 0xFF1565C0.toInt()
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 180L, 100L, 280L)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        manager.createNotificationChannel(canalCocina)
        manager.createNotificationChannel(canalMozo)
    }
}

private fun mostrarNotificacionSistema(
    canalId: String,
    sonidoRaw: String,
    titulo: String,
    detalle: String,
    destinoTab: String,
    notificationId: Int,
    colorArgb: Int
) {
    if (!::androidContext.isInitialized) return
    prepararNotificaciones()

    val permisoOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(androidContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    if (!permisoOk) {
        reproducirSonidoFallback(sonidoRaw)
        return
    }

    runCatching {
        val launchIntent = androidContext.packageManager.getLaunchIntentForPackage(androidContext.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_DESTINO_NOTIFICACION, destinoTab)
        }
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                androidContext,
                notificationId,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val smallIcon = androidContext.resources
            .getIdentifier("ic_notificacion_pos", "drawable", androidContext.packageName)
            .takeIf { it != 0 } ?: androidContext.applicationInfo.icon
        val largeIconRes = androidContext.resources
            .getIdentifier("logo_wendys", "drawable", androidContext.packageName)
        val largeIcon = if (largeIconRes != 0) BitmapFactory.decodeResource(androidContext.resources, largeIconRes) else null
        val soundUri = obtenerUriSonido(sonidoRaw)
        val textoCuerpo = detalle.ifBlank { titulo }

        val notificacion = NotificationCompat.Builder(androidContext, canalId)
            .setSmallIcon(smallIcon)
            .apply { if (largeIcon != null) setLargeIcon(largeIcon) }
            .setColor(colorArgb)
            .setContentTitle(titulo)
            .setContentText(textoCuerpo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(textoCuerpo))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0L, 220L, 120L, 220L))
            .apply { if (contentIntent != null) setContentIntent(contentIntent) }
            .build()

        NotificationManagerCompat.from(androidContext).notify(notificationId, notificacion)
    }.onFailure {
        reproducirSonidoFallback(sonidoRaw)
    }
}

actual fun avisarPedido(titulo: String, detalle: String, idPedido: Long) {
    if (!::androidContext.isInitialized) return
    val notifId = if (idPedido > 0L) (10_000 + (idPedido % 9_000)).toInt() else (10_000 + (System.currentTimeMillis() % 9_000)).toInt()
    mostrarNotificacionSistema(
        canalId = CANAL_COCINA_ID,
        sonidoRaw = "sonido_pedido_nuevo",
        titulo = titulo,
        detalle = detalle.ifBlank { "Se recibió un nuevo pedido para preparar en cocina." },
        destinoTab = "Cocina",
        notificationId = notifId,
        colorArgb = 0xFF7C1D26.toInt()
    )
}

actual fun avisarPedidoListo(titulo: String, detalle: String, idPedido: Long) {
    if (!::androidContext.isInitialized) return
    val notifId = if (idPedido > 0L) (20_000 + (idPedido % 9_000)).toInt() else (20_000 + (System.currentTimeMillis() % 9_000)).toInt()
    mostrarNotificacionSistema(
        canalId = CANAL_MOZO_ID,
        sonidoRaw = "sonido_pedido_listo",
        titulo = titulo,
        detalle = detalle.ifBlank { "El pedido ya está listo en cocina para llevarlo a la mesa." },
        destinoTab = "Salón",
        notificationId = notifId,
        colorArgb = 0xFF1565C0.toInt()
    )
}

private fun crearArchivoPdfCache(nombreArchivo: String, bytes: ByteArray): Pair<String, File> {
    val dir = File(androidContext.cacheDir, "comprobantes").apply { mkdirs() }
    val safe = nombreArchivo.replace(Regex("[^A-Za-z0-9._-]"), "_").let {
        if (it.lowercase().endsWith(".pdf")) it else "$it.pdf"
    }
    val file = File(dir, safe).apply { writeBytes(bytes) }
    return safe to file
}

private fun guardarCopiaEnDescargas(safe: String, bytes: ByteArray) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    runCatching {
        val resolver = androidContext.contentResolver
        val relativeDir = "${Environment.DIRECTORY_DOWNLOADS}/WendysPOS"
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
            arrayOf(safe, "%WendysPOS%"),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val existing = android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                resolver.delete(existing, null, null)
            }
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, safe)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, relativeDir)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }
}

actual fun abrirPdfLocal(nombreArchivo: String, bytes: ByteArray, compartir: Boolean): String? {
    return runCatching {
        val (safe, file) = crearArchivoPdfCache(nombreArchivo, bytes)
        guardarCopiaEnDescargas(safe, bytes)
        val uri = FileProvider.getUriForFile(
            androidContext,
            "${androidContext.packageName}.fileprovider",
            file
        )
        val baseIntent = if (compartir) {
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Comprobante Electrónico Wendy's Food: $safe")
                clipData = ClipData.newRawUri(safe, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                clipData = ClipData.newRawUri(safe, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val resInfos = androidContext.packageManager.queryIntentActivities(
            baseIntent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        for (info in resInfos) {
            androidContext.grantUriPermission(
                info.activityInfo.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        if (resInfos.isEmpty()) {
            return@runCatching if (compartir) {
                "No se encontró una aplicación para compartir archivos PDF."
            } else {
                "No hay un lector de PDF externo instalado. Se guardó una copia en Descargas/WendysPOS/$safe."
            }
        }
        val activity = currentActivityRef?.get()?.takeUnless { it.isFinishing || it.isDestroyed }
        val launcher: Context = activity ?: androidContext
        val chooser = Intent.createChooser(
            baseIntent,
            if (compartir) "Compartir comprobante PDF" else "Ver o imprimir comprobante 80mm"
        ).apply {
            clipData = ClipData.newRawUri(safe, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (activity == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            launcher.startActivity(chooser)
        }.recoverCatching {
            if (activity == null) baseIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            launcher.startActivity(baseIntent)
        }.getOrThrow()
        null
    }.getOrElse { e ->
        "No se pudo abrir el PDF en este dispositivo: ${e.message ?: "Error desconocido"}"
    }
}

@Composable
actual fun VisorPdfComprobante(
    nombreArchivo: String,
    bytes: ByteArray,
    onCerrar: () -> Unit,
    onAbrirExterno: () -> Unit,
    onCompartir: () -> Unit
) {
    var cargando by remember(nombreArchivo, bytes.size) { mutableStateOf(true) }
    var paginas by remember(nombreArchivo, bytes.size) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var errorRender by remember(nombreArchivo, bytes.size) { mutableStateOf<String?>(null) }

    LaunchedEffect(nombreArchivo, bytes.size, bytes.contentHashCode()) {
        cargando = true
        errorRender = null
        val resultado = withContext(Dispatchers.IO) {
            runCatching {
                val (safe, file) = crearArchivoPdfCache(nombreArchivo, bytes)
                guardarCopiaEnDescargas(safe, bytes)
                val renderizadas = mutableListOf<Bitmap>()
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        for (index in 0 until renderer.pageCount) {
                            renderer.openPage(index).use { page ->
                                val targetWidth = (page.width * 3).coerceIn(600, 1200)
                                val targetHeight = (targetWidth * page.height.toFloat() / page.width.coerceAtLeast(1))
                                    .toInt()
                                    .coerceAtLeast(1)
                                val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                                android.graphics.Canvas(bmp).drawColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                renderizadas.add(bmp)
                            }
                        }
                    }
                }
                renderizadas.toList()
            }
        }
        resultado.onSuccess { lista ->
            paginas = lista
            if (lista.isEmpty()) errorRender = "El comprobante PDF no contiene páginas visibles."
        }.onFailure { err ->
            errorRender = "No se pudo renderizar el PDF internamente: ${err.message ?: "Formato no válido"}"
        }
        cargando = false
    }

    AlertDialog(
        onDismissRequest = onCerrar,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Print, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text("Ticket PDF 80mm", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        nombreArchivo,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        text = {
            when {
                cargando -> {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("Generando vista previa 80mm...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                errorRender != null -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            errorRender.orEmpty(),
                            modifier = Modifier.padding(12.dp),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                else -> {
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        paginas.forEachIndexed { idx, bmp ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Comprobante $nombreArchivo página ${idx + 1}",
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.fillMaxWidth().padding(4.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onAbrirExterno,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Print, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Abrir App", fontSize = 11.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = onCompartir,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Compartir", fontSize = 11.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }
                Button(
                    onClick = onCerrar,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cerrar Vista Previa", maxLines = 1, softWrap = false)
                }
            }
        }
    )
}

actual fun hayConexionRed(): Boolean {
    if (!::androidContext.isInitialized) return true
    return runCatching {
        val cm = androidContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            ?: return true
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)
}

actual fun observarConectividad(onCambio: (Boolean) -> Unit): () -> Unit {
    if (!::androidContext.isInitialized) return {}
    val cm = androidContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        ?: return {}
    val mainHandler = Handler(Looper.getMainLooper())
    val callback = object : android.net.ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) {
            mainHandler.post { onCambio(true) }
        }
        override fun onLost(network: android.net.Network) {
            mainHandler.post { onCambio(hayConexionRed()) }
        }
        override fun onCapabilitiesChanged(network: android.net.Network, networkCapabilities: android.net.NetworkCapabilities) {
            val ok = networkCapabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            mainHandler.post { onCambio(ok) }
        }
    }
    return runCatching<() -> Unit> {
        cm.registerDefaultNetworkCallback(callback)
        val cancelar: () -> Unit = { runCatching { cm.unregisterNetworkCallback(callback) } }
        cancelar
    }.getOrDefault({})
}

@Composable
actual fun WendysLogo(modifier: Modifier) {
    val bitmap = remember {
        val resId = androidContext.resources.getIdentifier("logo_wendys", "drawable", androidContext.packageName)
        if (resId != 0) {
            BitmapFactory.decodeResource(androidContext.resources, resId)?.asImageBitmap()
        } else null
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Wendy's Food Logo",
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        Box(modifier.background(Borgona, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.LocalFireDepartment, null, tint = AmbarDorado)
        }
    }
}

@Composable
actual fun ActualizarBarrasSistema(oscuro: Boolean) {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val mascara = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            view.windowInsetsController?.setSystemBarsAppearance(if (oscuro) 0 else mascara, mascara)
        } else {
            @Suppress("DEPRECATION")
            val mascara = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                (if (android.os.Build.VERSION.SDK_INT >= 26) android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
            @Suppress("DEPRECATION")
            val actual = view.systemUiVisibility
            @Suppress("DEPRECATION")
            view.systemUiVisibility = if (oscuro) actual and mascara.inv() else actual or mascara
        }
    }
}
