// Autor: Randy Huaman - Celula G5 (NullPointer POS - Wendy's Food)
// Sistema de Diseno "Bistro & Gourmet Contemporaneo" con Barra Lateral Dashboard y soporte integral Sprints 1, 2 y 3

package pe.edu.upeu.nullpointerpos

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Clock
import org.koin.compose.viewmodel.koinViewModel
import pe.edu.upeu.nullpointerpos.domain.model.*
import pe.edu.upeu.nullpointerpos.platform.VisorPdfComprobante
import pe.edu.upeu.nullpointerpos.platform.WendysLogo
import pe.edu.upeu.nullpointerpos.presentation.*
import pe.edu.upeu.nullpointerpos.theme.*

fun dineroD(monto: Double): String = dinero((monto * 100).roundToLong())

fun formatearHoraLima(fechaIso: String): String {
    if (fechaIso.isBlank()) return ""
    val clean = fechaIso.trim()
    if (clean.length < 16 || !clean.contains('T')) return clean.take(16)
    val y = clean.substring(0, 4).toIntOrNull() ?: return clean.take(16)
    val m = clean.substring(5, 7).toIntOrNull() ?: return clean.take(16)
    val d = clean.substring(8, 10).toIntOrNull() ?: return clean.take(16)
    val hh = clean.substring(11, 13).toIntOrNull() ?: return clean.take(16)
    val mm = clean.substring(14, 16)
    val yaEsLima = clean.contains("-05:00") || clean.endsWith("-0500")
    val esUtc = clean.endsWith("Z") || clean.contains("+00:00") || clean.contains("+0000")
    if (yaEsLima || !esUtc) {
        return "${d.toString().padStart(2, '0')}/${m.toString().padStart(2, '0')} ${hh.toString().padStart(2, '0')}:$mm"
    }
    var nh = hh - 5
    var nd = d
    var nm = m
    var ny = y
    if (nh < 0) {
        nh += 24
        nd -= 1
        if (nd < 1) {
            nm -= 1
            if (nm < 1) { nm = 12; ny -= 1 }
            nd = when (nm) {
                1, 3, 5, 7, 8, 10, 12 -> 31
                4, 6, 9, 11 -> 30
                2 -> if ((ny % 4 == 0 && ny % 100 != 0) || ny % 400 == 0) 29 else 28
                else -> 30
            }
        }
    }
    return "${nd.toString().padStart(2, '0')}/${nm.toString().padStart(2, '0')} ${nh.toString().padStart(2, '0')}:$mm"
}

@Composable
fun App() {
    val vm: PosViewModel = koinViewModel()
    val s by vm.state.collectAsStateWithLifecycle()
    var mostrarSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(1600)
        mostrarSplash = false
    }

    WendysTheme(darkTheme = s.modoOscuro) {
        pe.edu.upeu.nullpointerpos.platform.ActualizarBarrasSistema(s.modoOscuro)
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AnimatedContent(
                targetState = mostrarSplash,
                transitionSpec = {
                    fadeIn(animationSpec = tween(450)) togetherWith fadeOut(animationSpec = tween(400))
                },
                label = "SplashTransition"
            ) { splashActivo ->
                if (splashActivo) {
                    SplashScreen(darkTheme = s.modoOscuro)
                } else {
                    AnimatedContent(
                        targetState = s.sesion == null,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(320)) + slideInVertically { it / 16 }) togetherWith
                                fadeOut(animationSpec = tween(220))
                        },
                        label = "AuthTransition"
                    ) { sinSesion ->
                        if (sinSesion) Login(s, vm) else Shell(s, vm)
                    }
                }
            }
        }
    }
}

@Composable
private fun SplashScreen(darkTheme: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scaleAnim by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Surface(
                shape = CircleShape,
                shadowElevation = 10.dp,
                color = Color.Transparent,
                modifier = Modifier
                    .size(170.dp)
                    .graphicsLayer {
                        scaleX = scaleAnim
                        scaleY = scaleAnim
                    }
            ) {
                WendysLogo(Modifier.fillMaxSize())
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Wendy's Food",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (darkTheme) AmbarDorado else Borgona,
                letterSpacing = 1.sp
            )
            Text(
                "SABORES QUE CONQUISTAN",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = AmbarProfundo,
                letterSpacing = 2.sp
            )
        }

        // WhatsApp-style bottom branding
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                "from",
                style = MaterialTheme.typography.labelSmall,
                color = LocalColores.current.textoSecundario.copy(alpha = 0.65f),
                letterSpacing = 1.5.sp
            )
            Text(
                "NULLPOINTER POS",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (darkTheme) AmbarDorado else Borgona,
                letterSpacing = 2.sp
            )
            Text(
                "UPeU · CÉLULA G5",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = LocalColores.current.textoSecundario,
                fontSize = 10.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

// -------------------------------------------------------------
// COMPONENTES COMUNES Y BRANDING
// -------------------------------------------------------------

@Composable
private fun Brand(compact: Boolean = false) {
    val isDark = MaterialTheme.colorScheme.background == FondoOscuro
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = Color.Transparent,
            shadowElevation = if (compact) 2.dp else 6.dp
        ) {
            WendysLogo(modifier = Modifier.size(if (compact) 34.dp else 54.dp))
        }
        Column {
            Text(
                "Wendy's Food",
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (isDark) AmbarDorado else Borgona
            )
            Text(
                "NULLPOINTER POS · G5",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = AmbarProfundo,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun EstadoPill(
    texto: String,
    colorContenido: Color,
    colorFondo: Color,
    borde: Color = colorContenido.copy(alpha = 0.28f),
    icono: ImageVector? = null,
    dotColor: Color? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = colorFondo,
        border = BorderStroke(1.dp, borde),
        modifier = modifier
    ) {
        Row(
            Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (dotColor != null) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor))
            } else if (icono != null) {
                Icon(icono, contentDescription = null, modifier = Modifier.size(13.dp), tint = colorContenido)
            }
            Text(
                texto,
                style = MaterialTheme.typography.labelSmall.tabular(),
                fontWeight = FontWeight.Bold,
                color = colorContenido,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun KpiMetricCard(
    etiqueta: String,
    valor: String,
    icono: ImageVector,
    acentoColor: Color,
    acentoFondo: Color,
    modifier: Modifier = Modifier
) {
    val c = LocalColores.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    etiqueta.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textoSecundario,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = acentoFondo,
                    border = BorderStroke(1.dp, acentoColor.copy(alpha = 0.22f)),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icono, null, tint = acentoColor, modifier = Modifier.size(15.dp))
                    }
                }
            }
            Text(
                valor,
                style = MaterialTheme.typography.headlineSmall.tabular(),
                fontWeight = FontWeight.ExtraBold,
                color = acentoColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun Notice(text: String, error: Boolean = false, onClose: (() -> Unit)? = null) {
    val c = LocalColores.current
    Surface(
        color = if (error) MaterialTheme.colorScheme.errorContainer else c.verdeBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (error) MaterialTheme.colorScheme.error.copy(alpha = 0.35f) else c.verde.copy(alpha = 0.42f)),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                if (error) Icons.Default.ErrorOutline else Icons.Default.CheckCircleOutline,
                contentDescription = null,
                tint = if (error) MaterialTheme.colorScheme.error else c.verde,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = if (error) MaterialTheme.colorScheme.onErrorContainer else c.textoPrincipal
            )
            if (onClose != null) {
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, "Cerrar aviso", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun Heading(title: String, subtitle: String, modifier: Modifier = Modifier) {
    val c = LocalColores.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = c.textoPrincipal)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textoSecundario)
    }
}

@Composable
private fun Empty(title: String, text: String, icon: ImageVector = Icons.Default.RestaurantMenu) {
    val c = LocalColores.current
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = c.acentoSuave,
            border = BorderStroke(1.dp, c.acento.copy(alpha = 0.22f)),
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(30.dp), tint = c.acento)
            }
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.textoPrincipal)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.textoSecundario, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Chips(opciones: List<String>, actual: String, cambiar: (String) -> Unit) {
    val c = LocalColores.current
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(opciones) { opcion ->
            val seleccionado = opcion == actual
            val colorChip by animateColorAsState(if (seleccionado) c.acento else MaterialTheme.colorScheme.surface, animationSpec = tween(180), label = "Filtro")
            val bordeChip by animateColorAsState(if (seleccionado) c.acento else MaterialTheme.colorScheme.outlineVariant, animationSpec = tween(180), label = "FiltroBorde")
            val textoChip = if (seleccionado) (if (c.esOscuro) Color(0xFF1A1205) else BlancoPuro) else c.textoPrincipal
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = colorChip,
                border = BorderStroke(1.dp, bordeChip),
                shadowElevation = if (seleccionado) 3.dp else 0.dp,
                modifier = Modifier.clickable { cambiar(opcion) }
            ) {
                Text(
                    text = opcion,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (seleccionado) FontWeight.Bold else FontWeight.Medium,
                    color = textoChip
                )
            }
        }
    }
}

@Composable
private fun Confirmar(title: String, text: String, cerrar: () -> Unit, aceptar: () -> Unit) {
    AlertDialog(
        onDismissRequest = cerrar,
        shape = RoundedCornerShape(16.dp),
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(text) },
        confirmButton = {
            Button(
                onClick = aceptar,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text("Confirmar") }
        },
        dismissButton = {
            TextButton(onClick = cerrar) { Text("Cancelar") }
        }
    )
}

// -------------------------------------------------------------
// 1. PANTALLA DE LOGIN Y AUTENTICACION RAPIDA (HU-01)
// -------------------------------------------------------------

@Composable
private fun Login(s: PosUiState, vm: PosViewModel) {
    val c = LocalColores.current
    var codigo by rememberSaveable { mutableStateOf("") }
    var pin by rememberSaveable { mutableStateOf("") }
    var mostrarPin by rememberSaveable { mutableStateOf(false) }
    var modalServidor by rememberSaveable { mutableStateOf(false) }
    var servidorTemp by rememberSaveable(s.servidor) { mutableStateOf(s.servidor) }

    Box(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(c.fondo),
        contentAlignment = Alignment.Center
    ) {
        // Barra superior sutil de controles (Modo Oscuro & Servidor engranaje discreto)
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopEnd)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = vm::toggleModoOscuro) {
                Icon(
                    if (s.modoOscuro) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Alternar Modo Oscuro",
                    tint = if (s.modoOscuro) AmbarDorado else Borgona,
                    modifier = Modifier.size(24.dp)
                )
            }
            IconButton(onClick = { modalServidor = true }) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Ajustes de red",
                    tint = c.textoSecundario.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Column(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(10.dp))

            // Logo oficial grande y centrado
            Surface(
                shape = CircleShape,
                color = Color.Transparent,
                shadowElevation = 8.dp,
                modifier = Modifier.size(110.dp)
            ) {
                WendysLogo(Modifier.fillMaxSize())
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Wendy's Food",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (s.modoOscuro) AmbarDorado else Borgona
                )
                Text(
                    "SISTEMA DE GESTIÓN & PUNTO DE VENTA",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = c.textoSecundario,
                    letterSpacing = 1.5.sp
                )
            }

            Spacer(Modifier.height(4.dp))

            AnimatedVisibility(
                visible = s.error != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                s.error?.let { Notice(it, true, vm::limpiarAvisos) }
            }
            AnimatedVisibility(
                visible = s.mensaje != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                s.mensaje?.let { Notice(it, onClose = vm::limpiarAvisos) }
            }

            // 1. Campo Código de Trabajador con teclado nativo
            OutlinedTextField(
                value = codigo,
                onValueChange = { codigo = it.uppercase().take(20) },
                label = { Text("Código de Colaborador") },
                placeholder = { Text("ej. MOZ001, CAJ001, ADM001") },
                singleLine = true,
                enabled = !s.busy,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Badge, null, tint = c.acento) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.acento,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedLabelColor = c.acento,
                    unfocusedLabelColor = c.textoSecundario,
                    focusedTextColor = c.textoPrincipal,
                    unfocusedTextColor = c.textoPrincipal,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                )
            )

            // 2. Campo PIN de Seguridad con teclado numérico nativo del celular
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter(Char::isDigit).take(6) },
                label = { Text("PIN de Acceso") },
                placeholder = { Text("••••") },
                singleLine = true,
                enabled = !s.busy,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Lock, null, tint = c.acento) },
                trailingIcon = {
                    IconButton(onClick = { mostrarPin = !mostrarPin }) {
                        Icon(
                            if (mostrarPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Mostrar PIN",
                            tint = c.textoSecundario
                        )
                    }
                },
                visualTransformation = if (mostrarPin) VisualTransformation.None else PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.acento,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedLabelColor = c.acento,
                    unfocusedLabelColor = c.textoSecundario,
                    focusedTextColor = c.textoPrincipal,
                    unfocusedTextColor = c.textoPrincipal,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (codigo.isNotBlank() && pin.length >= 4) {
                            vm.login(codigo, pin)
                            pin = ""
                        }
                    }
                )
            )

            Spacer(Modifier.height(4.dp))

            // Indicador y botón rápido de Conexión de Red (IP del Servidor)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().clickable {
                    servidorTemp = s.servidor
                    modalServidor = true
                }
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(shape = CircleShape, color = VerdeEsmeralda, modifier = Modifier.size(8.dp)) {}
                        Text("Servidor:", fontSize = 11.sp, color = c.textoSecundario)
                        Text(s.servidor, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.textoPrincipal)
                    }
                    Text("Cambiar IP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.acento)
                }
            }

            // 3. Botón Principal de Ingreso al Turno
            Button(
                onClick = { vm.login(codigo, pin); pin = "" },
                enabled = !s.busy && codigo.isNotBlank() && pin.length >= 4,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = BlancoPuro,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    disabledContentColor = BlancoPuro.copy(alpha = 0.7f)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 1.dp)
            ) {
                if (s.busy) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = BlancoPuro, strokeWidth = 2.5.dp)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Login, null)
                    Spacer(Modifier.width(10.dp))
                    Text("Ingresar al Sistema", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Footer sobrio
            Text(
                "Wendy's Food · NullPointer POS\nCélula G5 · UPeU",
                style = MaterialTheme.typography.labelSmall,
                color = c.textoSecundario.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                fontSize = 11.sp
            )
        }
    }

    // Modal discreto de ajuste de conexión (solo visible si se presiona el engranaje)
    if (modalServidor) {
        AlertDialog(
            onDismissRequest = { modalServidor = false },
            shape = RoundedCornerShape(16.dp),
            title = { Text("Conexión de Red", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Dirección URL del servidor:", style = MaterialTheme.typography.bodySmall, color = c.textoSecundario)
                    OutlinedTextField(
                        value = servidorTemp,
                        onValueChange = { servidorTemp = it },
                        label = { Text("Servidor") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SuggestionChip(onClick = { servidorTemp = "http://10.40.79.37:8080" }, label = { Text("Wi-Fi Laptop (10.40.79.37)") })
                        SuggestionChip(onClick = { servidorTemp = "http://localhost:8080" }, label = { Text("USB Cable (localhost)") })
                        SuggestionChip(onClick = { servidorTemp = "http://192.168.1.6:8080" }, label = { Text("Wi-Fi Casa (192.168.1.6)") })
                        SuggestionChip(onClick = { servidorTemp = "http://10.0.2.2:8080" }, label = { Text("Emulador (10.0.2.2)") })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.configurarServidor(servidorTemp)
                        modalServidor = false
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { modalServidor = false }) { Text("Cancelar") }
            }
        )
    }
}

// -------------------------------------------------------------
// SHELL PRINCIPAL CON BARRA LATERAL TIPO DASHBOARD (DRAWER)
// -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Shell(s: PosUiState, vm: PosViewModel) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Destinos autorizados según rol
    val destinos = buildList {
        if (s.puedeMesas) {
            add(Triple("Salón", Icons.Default.GridView, "Mesas y atención"))
            add(Triple("Comanda", Icons.AutoMirrored.Filled.ReceiptLong, "Toma de pedidos"))
        }
        if (s.puedeCocina) {
            add(Triple("Cocina", Icons.Default.OutdoorGrill, "KDS Producción"))
        }
        if (s.puedeCaja) {
            add(Triple("Caja", Icons.Default.PointOfSale, "Cobros y facturación"))
        }
        if (s.puedeAlmacen) {
            add(Triple("Almacén", Icons.Default.Inventory2, "Stock e insumos"))
        }
        if (s.puedeReportes) {
            add(Triple("Reportes", Icons.Default.BarChart, "Ventas y auditoría"))
        }
        if (s.puedeAdmin) {
            add(Triple("Usuarios", Icons.Default.People, "Personal y roles"))
        }
        add(Triple("Cuenta", Icons.Default.AccountCircle, "Mi perfil"))
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.width(310.dp)
            ) {
                // Cabecera estilizada de la barra lateral Dashboard
                Surface(
                    color = Borgona,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Transparent,
                                shadowElevation = 4.dp
                            ) {
                                WendysLogo(Modifier.size(46.dp))
                            }
                            Column {
                                Text(
                                    "Wendy's Food",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = BlancoPuro,
                                    fontSize = 18.sp
                                )
                                Text(
                                    "PANEL DASHBOARD POS",
                                    fontWeight = FontWeight.SemiBold,
                                    color = AmbarDorado,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        // Tarjeta del Usuario Activo
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = BlancoPuro.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = AmbarDorado,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            s.sesion?.usuario?.codigoTrabajador?.take(2)?.uppercase().orEmpty(),
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Borgona,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        s.sesion?.usuario?.nombreCompleto.orEmpty(),
                                        fontWeight = FontWeight.Bold,
                                        color = BlancoPuro,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            Modifier.size(6.dp).clip(CircleShape).background(VerdeEsmeralda)
                                        )
                                        Text(
                                            "${s.sesion?.usuario?.codigoTrabajador} · ${s.rol}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = BlancoPuro.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    "MÓDULOS DEL RESTAURANTE",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalColores.current.textoSecundario,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                // Lista de Items de Navegación Lateral
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(destinos) { (nombre, icono, desc) ->
                        val seleccionado = s.pagina == nombre || (nombre == "Salón" && s.pagina == "Mesas") || (nombre == "Almacén" && s.pagina == "Stock")
                        val itemBg by animateColorAsState(
                            if (seleccionado) MaterialTheme.colorScheme.primary else Color.Transparent,
                            animationSpec = tween(180),
                            label = "DrawerItemBg"
                        )
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = itemBg,
                            modifier = Modifier.fillMaxWidth().clickable {
                                scope.launch { drawerState.close() }
                                vm.navegar(nombre)
                            }
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Icon(
                                    icono,
                                    contentDescription = nombre,
                                    tint = if (seleccionado) AmbarDorado else LocalColores.current.acento,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        nombre,
                                        fontWeight = if (seleccionado) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        color = if (seleccionado) BlancoPuro else MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        desc,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (seleccionado) BlancoPuro.copy(alpha = 0.78f) else LocalColores.current.textoSecundario
                                    )
                                }
                                if (nombre == "Salón") {
                                    val ocupadas = s.mesas.count { it.estado == "OCUPADA" }
                                    if (ocupadas > 0) {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (seleccionado) BlancoPuro else LocalColores.current.rojoBg
                                        ) {
                                            Text(
                                                "$ocupadas",
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall.tabular(),
                                                fontWeight = FontWeight.Bold,
                                                color = if (seleccionado) Borgona else RojoCoral
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Pie del Drawer con Modo Oscuro, Salir y Servidor
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Switch de Modo Oscuro
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth().clickable { vm.toggleModoOscuro() }
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = LocalColores.current.acentoSuave,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (s.modoOscuro) Icons.Default.DarkMode else Icons.Default.LightMode,
                                        contentDescription = null,
                                        tint = LocalColores.current.acento,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Modo Oscuro",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    if (s.modoOscuro) "Tema Noche & KDS" else "Tema Claro Bistro",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LocalColores.current.textoSecundario
                                )
                            }
                            Switch(
                                checked = s.modoOscuro,
                                onCheckedChange = { vm.toggleModoOscuro() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = AmbarDorado,
                                    checkedTrackColor = Borgona,
                                    uncheckedThumbColor = BlancoPuro,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (s.conectadoServidor) VerdeEsmeralda else RojoCoral))
                        Text(
                            s.servidor.ifBlank { "http://10.40.79.37:8080" },
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalColores.current.textoSecundario,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch { drawerState.close() }
                            vm.cerrarSesion()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RojoCoral),
                        border = BorderStroke(1.dp, RojoCoral.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Cerrar Sesión", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, "Abrir menú lateral", tint = LocalColores.current.acento, modifier = Modifier.size(28.dp))
                            }
                        },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Transparent,
                                    shadowElevation = 2.dp
                                ) {
                                    WendysLogo(Modifier.size(34.dp))
                                }
                                Column {
                                    Text(
                                        s.pagina,
                                        fontWeight = FontWeight.ExtraBold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = LocalColores.current.acento
                                    )
                                    Text(
                                        "Wendy's Food POS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (s.modoOscuro) TextoOscuroSecundario else AmbarProfundo,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = vm::toggleModoOscuro) {
                                Icon(
                                    if (s.modoOscuro) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = "Cambiar modo oscuro",
                                    tint = LocalColores.current.acento
                                )
                            }
                            val dotEstado = when {
                                !s.conectadoServidor -> RojoCoral
                                s.colaSync.isNotEmpty() || s.sincronizandoCola -> AmbarAlerta
                                else -> VerdeEsmeralda
                            }
                            EstadoPill(
                                texto = s.rol,
                                colorContenido = LocalColores.current.acento,
                                colorFondo = MaterialTheme.colorScheme.surfaceContainerLow,
                                borde = MaterialTheme.colorScheme.outlineVariant,
                                dotColor = dotEstado,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            IconButton(
                                onClick = vm::refrescar,
                                enabled = !s.busy && s.pagina != "Cuenta"
                            ) {
                                Icon(Icons.Default.Refresh, "Actualizar", tint = LocalColores.current.acento)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                }
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).background(MaterialTheme.colorScheme.background)
            ) {
                AnimatedVisibility(
                    visible = s.busy,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth(),
                        color = LocalColores.current.acento
                    )
                }
                AnimatedVisibility(
                    visible = !s.conectadoServidor || s.colaSync.isNotEmpty() || s.sincronizandoCola,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    val cSync = LocalColores.current
                    val tieneErrorSync = s.colaSync.any { !it.ultimoError.isNullOrBlank() }
                    val bgSync = if (!s.conectadoServidor || tieneErrorSync) cSync.rojoBg else cSync.ambarBg
                    val fgSync = if (!s.conectadoServidor || tieneErrorSync) RojoCoral else if (s.modoOscuro) AmbarDorado else AmbarProfundo
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = bgSync,
                        border = BorderStroke(1.dp, fgSync.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    if (!s.conectadoServidor) Icons.Default.CloudOff else Icons.Default.CloudUpload,
                                    null,
                                    tint = fgSync,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(Modifier.weight(1f)) {
                                    val tituloSync = when {
                                        s.sincronizandoCola -> "Sincronizando (${s.colaSync.size} pendientes)..."
                                        !s.conectadoServidor -> "Sin conexión · ${s.colaSync.size} pendientes"
                                        else -> "Conexión lista · ${s.colaSync.size} por sincronizar"
                                    }
                                    Text(tituloSync, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge.tabular(), color = fgSync)
                                    val subtituloSync = s.colaSync.firstOrNull { !it.ultimoError.isNullOrBlank() }?.ultimoError
                                        ?: if (!s.conectadoServidor) "Pedidos y cambios guardados localmente; se enviarán al volver la red."
                                        else "Pulsa Sincronizar o espera un momento para actualizar los roles."
                                    Text(subtituloSync, fontSize = 10.sp, color = cSync.textoSecundario, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                TextButton(
                                    onClick = vm::sincronizarAhora,
                                    enabled = !s.sincronizandoCola
                                ) {
                                    if (s.sincronizandoCola) {
                                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = fgSync)
                                    } else {
                                        Text("Sincronizar", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = fgSync)
                                    }
                                }
                            }
                            s.colaSync.firstOrNull { !it.ultimoError.isNullOrBlank() }?.let { falloSync ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { vm.descartarAccionSync(falloSync.idLocal) }) {
                                        Text("Descartar conflicto", fontSize = 11.sp, color = RojoCoral, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = s.error != null || s.mensaje != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Notice(s.error ?: s.mensaje.orEmpty(), s.error != null, vm::limpiarAvisos)
                    }
                }
                AnimatedVisibility(
                    visible = s.pedidoListoAviso != null,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                ) {
                    s.pedidoListoAviso?.let { listo ->
                        val cl = LocalColores.current
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = cl.azulBg,
                            border = BorderStroke(2.dp, cl.azul),
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clickable {
                                    vm.cerrarNotificacionPedidoListo()
                                    if (s.pagina != "Salón") vm.navegar("Salón")
                                }
                        ) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = cl.azul,
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.NotificationsActive, null, tint = BlancoPuro, modifier = Modifier.size(22.dp))
                                    }
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            "¡PEDIDO LISTO PARA SERVIR!",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.sp,
                                            color = cl.azul
                                        )
                                        Surface(shape = RoundedCornerShape(6.dp), color = cl.azul) {
                                            Text("LISTO", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BlancoPuro)
                                        }
                                    }
                                    Text(
                                        "Mesa ${listo.codigoMesa} (${listo.codigoPedido}) · Ya puedes llevar los platos",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = cl.textoPrincipal
                                    )
                                    if (listo.lineas.isNotEmpty()) {
                                        Text(
                                            listo.lineas.joinToString(", ") { "${it.cantidad}x ${it.nombre}" }.take(65) + if (listo.lineas.size > 2) "..." else "",
                                            fontSize = 11.sp,
                                            color = cl.textoSecundario,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                IconButton(onClick = { vm.cerrarNotificacionPedidoListo() }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Close, null, tint = cl.azul, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = s.nuevoPedidoCocinaAviso != null && s.pagina != "Cocina",
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                ) {
                    s.nuevoPedidoCocinaAviso?.let { nuevo ->
                        val cl = LocalColores.current
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = cl.verdeBg,
                            border = BorderStroke(2.dp, cl.verde),
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clickable {
                                    vm.cerrarNotificacionCocina()
                                    vm.navegar("Cocina")
                                }
                        ) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = cl.verde,
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.NotificationsActive, null, tint = BlancoPuro, modifier = Modifier.size(22.dp))
                                    }
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        "¡NUEVA COMANDA EN COCINA!",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        color = cl.verde
                                    )
                                    Text(
                                        "Mesa ${nuevo.codigoMesa} (${nuevo.codigoPedido}) · ${nuevo.lineas.size} platos · Toca para abrir Cocina",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = cl.textoPrincipal,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { vm.cerrarNotificacionCocina() }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Close, null, tint = cl.verde, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AnimatedContent(
                        targetState = s.pagina,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(220)) + slideInHorizontally { it / 20 }) togetherWith
                                (fadeOut(animationSpec = tween(180)) + slideOutHorizontally { -it / 20 })
                        },
                        label = "PageTransition"
                    ) { pag ->
                        when (pag) {
                            "Salón", "Mesas" -> Salon(s, vm)
                            "Comanda" -> Comanda(s, vm)
                            "Cocina" -> CocinaKds(s, vm)
                            "Caja" -> CajaScreen(s, vm)
                            "Almacén", "Stock" -> AlmacenScreen(s, vm)
                            "Reportes" -> ReportesScreen(s, vm)
                            "Usuarios" -> Usuarios(s, vm)
                            else -> Cuenta(s, vm)
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 2. SALON INTERACTIVO Y GESTION DE MESAS (HU-02, HU-06)
// -------------------------------------------------------------

@Composable
private fun Salon(s: PosUiState, vm: PosViewModel) {
    val c = LocalColores.current
    val acento = if (s.modoOscuro) AmbarDorado else Borgona
    var filtro by rememberSaveable { mutableStateOf("Todas") }
    var area by rememberSaveable { mutableStateOf("Todo el Salón") }

    val totalLibres by animateIntAsState(s.mesas.count { it.estado == "LIBRE" }, label = "libres")
    val totalOcupadas by animateIntAsState(s.mesas.count { it.estado == "OCUPADA" }, label = "ocupadas")
    val totalMesas = s.mesas.size

    val pulsoTransition = rememberInfiniteTransition(label = "liveDot")
    val pulsoAlpha by pulsoTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "dotAlpha"
    )

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(2.dp))

        // 1. Barra de Conexión en Tiempo Real (:8080/ws/cocina)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EstadoPill(
                texto = if (s.socketConectado) "WebSocket En Vivo · Sincronizado" else "Conectado a ${s.servidor.removePrefix("http://").removePrefix("https://")}",
                colorContenido = c.textoPrincipal,
                colorFondo = MaterialTheme.colorScheme.surface,
                borde = if (s.socketConectado) c.verde.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant,
                dotColor = c.verde.copy(alpha = if (s.socketConectado) pulsoAlpha else 1f),
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = c.acentoSuave,
                border = BorderStroke(1.dp, acento.copy(alpha = 0.25f)),
                modifier = Modifier.clickable(enabled = !s.busy) { vm.refrescar() }
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp), tint = acento)
                    Text("Sincronizar", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = acento, maxLines = 1, softWrap = false)
                }
            }
        }

        // 2. Ticker Operativo Gastronómico (3 tarjetas KPI con jerarquía visual M3)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            KpiMetricCard(
                etiqueta = "Libres",
                valor = totalLibres.toString().padStart(2, '0'),
                icono = Icons.Default.EventAvailable,
                acentoColor = c.verde,
                acentoFondo = c.verdeBg,
                modifier = Modifier.weight(1f)
            )
            KpiMetricCard(
                etiqueta = "Ocupadas",
                valor = totalOcupadas.toString().padStart(2, '0'),
                icono = Icons.Default.Restaurant,
                acentoColor = RojoCoral,
                acentoFondo = c.rojoBg,
                modifier = Modifier.weight(1f)
            )
            KpiMetricCard(
                etiqueta = "Total Salón",
                valor = totalMesas.toString().padStart(2, '0'),
                icono = Icons.Default.TableRestaurant,
                acentoColor = if (s.modoOscuro) AmbarDorado else AmbarProfundo,
                acentoFondo = c.ambarBg,
                modifier = Modifier.weight(1f)
            )
        }

        // 3. Filtros de Áreas tipo Píldora
        val areas = listOf("Todo el Salón") + s.mesas.map { it.nombreArea }.distinct().filter { it.isNotBlank() }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(areas) { a ->
                val activa = area == a
                val cant = if (a == "Todo el Salón") s.mesas.size else s.mesas.count { it.nombreArea == a }
                val areaBg by animateColorAsState(
                    if (activa) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    animationSpec = tween(180),
                    label = "AreaBg"
                )
                val areaBorder by animateColorAsState(
                    if (activa) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    animationSpec = tween(180),
                    label = "AreaBorder"
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = areaBg,
                    border = BorderStroke(1.dp, areaBorder),
                    shadowElevation = if (activa) 2.dp else 0.dp,
                    modifier = Modifier.clickable { area = a }
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            a,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (activa) FontWeight.Bold else FontWeight.Medium,
                            color = if (activa) BlancoPuro else c.textoPrincipal
                        )
                        Text(
                            "($cant)",
                            style = MaterialTheme.typography.labelSmall.tabular(),
                            fontWeight = FontWeight.Bold,
                            color = if (activa) AmbarDorado else c.textoSecundario
                        )
                    }
                }
            }
        }

        // Filtro de Estado Rápido
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Todas", "Libres", "Ocupadas").forEach { f ->
                val sel = filtro == f
                val fBg by animateColorAsState(
                    if (sel) c.acentoSuave else Color.Transparent,
                    animationSpec = tween(180),
                    label = "EstadoFiltroBg"
                )
                val fBorder by animateColorAsState(
                    if (sel) acento.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant,
                    animationSpec = tween(180),
                    label = "EstadoFiltroBorde"
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = fBg,
                    border = BorderStroke(1.dp, fBorder),
                    modifier = Modifier.clickable { filtro = f }
                ) {
                    Text(
                        f,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        color = if (sel) acento else c.textoSecundario
                    )
                }
            }
        }

        val mesasFiltradas = s.mesas.filter {
            (area == "Todo el Salón" || it.nombreArea == area) &&
            (filtro == "Todas" || it.estado == if (filtro == "Libres") "LIBRE" else "OCUPADA")
        }

        if (mesasFiltradas.isEmpty()) {
            Empty("Sin mesas registradas", "Verifica la conexión con el servidor o cambia el filtro de salón.", Icons.Default.TableRestaurant)
        } else {
            LazyVerticalGrid(
                GridCells.Adaptive(160.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                gridItems(mesasFiltradas, key = { it.idMesa }) { mesa ->
                    val pedidoActivo = s.pedidosSalon.find { it.idMesa == mesa.idMesa } ?: s.cocina.find { it.idMesa == mesa.idMesa }
                    val esPedidoOffline = pedidoActivo?.estado == "PEDIDO_POR_SINCRONIZAR"
                    val esListoOffline = pedidoActivo?.estado == "LISTO_POR_SINCRONIZAR"
                    val esListoCocina = pedidoActivo?.estado == "LISTO" || esListoOffline
                    val ocupadaServidor = mesa.estado != "LIBRE" || pedidoActivo != null
                    val borradorLocal = s.borradores.find { it.idMesa == mesa.idMesa }
                    val tieneBorradorLocal = !ocupadaServidor && borradorLocal != null && borradorLocal.lineas.isNotEmpty()
                    val libre = !ocupadaServidor && !tieneBorradorLocal
                    val consumoCentimos = pedidoActivo?.importeCentimos ?: borradorLocal?.lineas?.total() ?: 0L
                    val colorAcento = when {
                        esPedidoOffline -> if (s.modoOscuro) AmbarDorado else AmbarProfundo
                        esListoCocina -> c.azul
                        ocupadaServidor -> RojoCoral
                        tieneBorradorLocal -> if (s.modoOscuro) AmbarDorado else AmbarProfundo
                        else -> c.verde
                    }
                    val colorBgBadge = when {
                        esPedidoOffline -> c.ambarBg
                        esListoCocina -> c.azulBg
                        ocupadaServidor -> c.rojoBg
                        tieneBorradorLocal -> c.ambarBg
                        else -> c.verdeBg
                    }
                    val textoEstado = when {
                        esPedidoOffline -> "PEDIDO (SYNC)"
                        esListoOffline -> "LISTO (SYNC)"
                        esListoCocina -> "LISTO"
                        ocupadaServidor -> "OCUPADA"
                        tieneBorradorLocal -> "BORRADOR"
                        else -> "LIBRE"
                    }
                    val mesaBorder by animateColorAsState(
                        when {
                            tieneBorradorLocal || esPedidoOffline -> colorAcento.copy(alpha = 0.55f)
                            esListoCocina -> colorAcento.copy(alpha = 0.45f)
                            ocupadaServidor -> colorAcento.copy(alpha = 0.30f)
                            else -> MaterialTheme.colorScheme.outlineVariant
                        },
                        animationSpec = tween(220),
                        label = "MesaBorde"
                    )

                    // Tarjeta de Mesa Estilo Bistro & Gourmet M3
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, mesaBorder),
                        shadowElevation = 2.dp,
                        modifier = Modifier.animateItem().fillMaxWidth().animateContentSize().clickable(enabled = !s.busy) { vm.abrir(mesa) }
                    ) {
                        Column {
                            // Franja superior de acento de estado
                            Box(
                                Modifier.fillMaxWidth().height(5.dp).background(colorAcento)
                            )

                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                // Cabecera: Código de Mesa y Badge de Estado
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            mesa.codigoMesa,
                                            style = MaterialTheme.typography.titleLarge.tabular(),
                                            fontWeight = FontWeight.ExtraBold,
                                            color = c.textoPrincipal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            mesa.nombreArea.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = c.textoSecundario,
                                            fontSize = 9.sp,
                                            letterSpacing = 0.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    EstadoPill(
                                        texto = textoEstado,
                                        colorContenido = colorAcento,
                                        colorFondo = colorBgBadge,
                                        borde = colorAcento.copy(alpha = 0.30f),
                                        dotColor = colorAcento
                                    )
                                }

                                // Información operativa: Capacidad y Mozo
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.People,
                                        null,
                                        modifier = Modifier.size(14.dp),
                                        tint = c.textoSecundario
                                    )
                                    Text(
                                        "${mesa.capacidad} pax",
                                        style = MaterialTheme.typography.bodySmall.tabular(),
                                        color = c.textoSecundario,
                                        fontSize = 11.sp
                                    )
                                    if (ocupadaServidor) {
                                        Text("·", color = MaterialTheme.colorScheme.outlineVariant, fontWeight = FontWeight.Bold)
                                        Text(
                                            pedidoActivo?.codigoMozo?.takeIf { it.isNotBlank() }?.let { "Mozo $it" } ?: "En atención",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = c.textoSecundario,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    } else if (tieneBorradorLocal) {
                                        Text("·", color = MaterialTheme.colorScheme.outlineVariant, fontWeight = FontWeight.Bold)
                                        Text(
                                            "Sin enviar",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = acento,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // Banda Inferior de Consumo Gastronómico
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (libre) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainer,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            when {
                                                libre -> "Mesa libre"
                                                esPedidoOffline -> "Por sincronizar:"
                                                tieneBorradorLocal -> "Borrador:"
                                                else -> pedidoActivo?.codigoPedido ?: "Consumo:"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = c.textoSecundario,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            if (libre) "Disponible" else dinero(consumoCentimos),
                                            style = MaterialTheme.typography.labelLarge.tabular(),
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            softWrap = false,
                                            color = when {
                                                libre -> c.verde
                                                esPedidoOffline || tieneBorradorLocal -> acento
                                                esListoCocina -> c.azul
                                                else -> acento
                                            }
                                        )
                                    }
                                }

                                // Botón de Acción Táctil
                                Button(
                                    onClick = { vm.abrir(mesa) },
                                    modifier = Modifier.fillMaxWidth().height(38.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = when {
                                            libre -> MaterialTheme.colorScheme.primary
                                            esPedidoOffline || tieneBorradorLocal -> AmbarProfundo
                                            esListoCocina -> AzulListo
                                            else -> RojoCoral
                                        },
                                        contentColor = BlancoPuro
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(
                                        when {
                                            libre -> Icons.Default.AddShoppingCart
                                            esPedidoOffline -> Icons.Default.CloudUpload
                                            esListoCocina -> Icons.Default.CheckCircle
                                            tieneBorradorLocal -> Icons.Default.EditNote
                                            else -> Icons.AutoMirrored.Filled.ReceiptLong
                                        },
                                        null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        when {
                                            libre -> "Tomar Pedido"
                                            esPedidoOffline -> "Pedido (Sync)"
                                            esListoCocina -> "Listo · Ver"
                                            tieneBorradorLocal -> "Continuar"
                                            else -> "Ver Comanda"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 3. TOMA DE COMANDAS Y RESUMEN AUTORITATIVO (HU-03 A HU-07)
// -------------------------------------------------------------

@Composable
private fun Comanda(s: PosUiState, vm: PosViewModel) {
    val c = LocalColores.current
    val b = s.borrador
    if (b == null) {
        Empty(
            "Selecciona una mesa",
            "Ve al Salón y selecciona una mesa para crear un nuevo pedido o revisar la cuenta.",
            Icons.Default.TableRestaurant
        )
        return
    }

    var tab by rememberSaveable(b.idMesa) { mutableStateOf(if (s.pedido != null) "Comanda" else "Menú") }
    var categoria by rememberSaveable(b.idMesa) { mutableStateOf("Todo") }
    var buscar by rememberSaveable(b.idMesa) { mutableStateOf("") }
    var recuperarId by rememberSaveable(b.idMesa) { mutableStateOf("") }
    var editarNota by remember { mutableStateOf<Int?>(null) }
    var quitar by remember { mutableStateOf<Int?>(null) }
    var confirmarEnvio by remember { mutableStateOf(false) }
    var mover by remember { mutableStateOf(false) }
    var descartar by remember { mutableStateOf(false) }

    // Si hay un pedido del servidor cargado, usar sus líneas prioritariamente
    val pedidoServidor = s.pedido
    val lineasVisibles = if (pedidoServidor != null && pedidoServidor.lineas.isNotEmpty()) pedidoServidor.lineas else b.lineas

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Heading(
                    "Mesa ${s.mesa?.codigoMesa.orEmpty()}",
                    pedidoServidor?.let { "${it.codigoPedido} · ${it.estado.replace('_', ' ')}" } ?: "Borrador en edición"
                )
                EstadoPill(
                    texto = "${lineasVisibles.sumOf { it.cantidad }} platos",
                    colorContenido = c.acento,
                    colorFondo = c.acentoSuave,
                    borde = c.acento.copy(alpha = 0.25f),
                    icono = Icons.Default.RestaurantMenu
                )
            }

            if (b.creacionIncierta) {
                Notice("No se pudo confirmar la creación del pedido. Ingresa el ID para recuperarlo antes de continuar.", true)
            }
            s.modoSoloLecturaAviso?.let { aviso ->
                Notice(aviso, false)
            }

            if (s.pedido == null && s.modoSoloLecturaAviso == null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = recuperarId,
                        onValueChange = { recuperarId = it.filter(Char::isDigit).take(18) },
                        label = { Text("Recuperar por ID de Pedido") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Button(
                        onClick = { vm.recuperar(recuperarId); recuperarId = "" },
                        enabled = !s.busy && recuperarId.isNotBlank(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text("Cargar") }
                }
            }

            // Pestañas Menú / Resumen de comanda
            PrimaryTabRow(
                selectedTabIndex = if (tab == "Menú") 0 else 1,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = c.acento
            ) {
                Tab(
                    selected = tab == "Menú",
                    onClick = { tab = "Menú" },
                    text = { Text("Catálogo Menú (${s.productos.size})", style = MaterialTheme.typography.labelLarge.tabular(), fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = tab == "Comanda",
                    onClick = { tab = "Comanda" },
                    text = { Text("Ver Comanda (${lineasVisibles.sumOf { it.cantidad }})", style = MaterialTheme.typography.labelLarge.tabular(), fontWeight = FontWeight.Bold) }
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Box(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            if (tab == "Menú") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = buscar,
                        onValueChange = { buscar = it },
                        label = { Text("Buscar pollo, parrillada, bebida...") },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = c.acento) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    val categoriasMenu = listOf("Todo") + s.categorias.map { it.nombre }.ifEmpty {
                        listOf("Pollo a la Brasa", "Parrillas", "Bebidas", "Guarniciones")
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        items(categoriasMenu) { cat ->
                            val sel = categoria == cat
                            val emoji = when {
                                cat.contains("Pollo", true) || cat.contains("Brasa", true) -> "🔥 "
                                cat.contains("Parrilla", true) || cat.contains("Carne", true) -> "🥩 "
                                cat.contains("Guarnicion", true) || cat.contains("Papa", true) -> "🍟 "
                                cat.contains("Bebida", true) || cat.contains("Cerveza", true) -> "🍹 "
                                cat.contains("Postre", true) -> "🍰 "
                                cat == "Todo" -> "🍽️ "
                                else -> "🍴 "
                            }
                            val catBg by animateColorAsState(
                                if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                animationSpec = tween(180),
                                label = "CatBg"
                            )
                            val catBorder by animateColorAsState(
                                if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                animationSpec = tween(180),
                                label = "CatBorder"
                            )
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = catBg,
                                border = BorderStroke(1.dp, catBorder),
                                shadowElevation = if (sel) 2.dp else 0.dp,
                                modifier = Modifier.clickable { categoria = cat }
                            ) {
                                Text(
                                    text = "$emoji$cat",
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (sel) BlancoPuro else c.textoPrincipal
                                )
                            }
                        }
                    }

                    val listaProductos = s.productos.filter { p ->
                        (categoria == "Todo" || p.categoria == categoria) &&
                        (p.nombre.contains(buscar, ignoreCase = true) || p.categoria.contains(buscar, ignoreCase = true))
                    }

                    if (listaProductos.isEmpty()) {
                        Empty("No hay platos disponibles", "Intenta con otro término o categoría.")
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(listaProductos, key = { it.idProducto }) { p ->
                                val cantBorrador = b.lineas.filter { it.idProducto == p.idProducto }.sumOf { it.cantidad }
                                val emojiPlato = when {
                                    p.categoria.contains("Pollo", true) || p.categoria.contains("Brasa", true) -> "🍗"
                                    p.categoria.contains("Parrilla", true) || p.categoria.contains("Carne", true) -> "🥩"
                                    p.categoria.contains("Guarnicion", true) || p.categoria.contains("Papa", true) -> "🍟"
                                    p.categoria.contains("Bebida", true) || p.categoria.contains("Cerveza", true) -> "🍹"
                                    p.categoria.contains("Postre", true) -> "🍰"
                                    else -> "🍽️"
                                }
                                val platoBg by animateColorAsState(
                                    if (cantBorrador > 0) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface,
                                    animationSpec = tween(180),
                                    label = "PlatoBg"
                                )
                                val platoBorder by animateColorAsState(
                                    if (cantBorrador > 0) c.acento.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant,
                                    animationSpec = tween(180),
                                    label = "PlatoBorder"
                                )

                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = platoBg,
                                    border = BorderStroke(1.dp, platoBorder),
                                    shadowElevation = if (cantBorrador > 0) 3.dp else 1.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (cantBorrador > 0) c.acentoSuave else MaterialTheme.colorScheme.surfaceContainer,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                                modifier = Modifier.size(54.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(emojiPlato, fontSize = 26.sp)
                                                }
                                            }

                                            Column(Modifier.weight(1f)) {
                                                Row(
                                                    Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        p.nombre,
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = c.textoPrincipal,
                                                        modifier = Modifier.weight(1f),
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (p.idProducto % 2L == 0L) {
                                                        Spacer(Modifier.width(6.dp))
                                                        EstadoPill(
                                                            texto = "TOP",
                                                            colorContenido = if (s.modoOscuro) AmbarDorado else AmbarProfundo,
                                                            colorFondo = c.ambarBg,
                                                            borde = AmbarDorado.copy(alpha = 0.35f)
                                                        )
                                                    }
                                                }

                                                Text(
                                                    p.descripcion ?: "${p.categoria} · Wendy's Food Gourmet",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = c.textoSecundario,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                Spacer(Modifier.height(8.dp))

                                                Row(
                                                    Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        dinero(p.precioCentimos),
                                                        style = MaterialTheme.typography.titleMedium.tabular(),
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = c.acento
                                                    )

                                                    if (cantBorrador == 0) {
                                                        Button(
                                                            onClick = { vm.agregar(p) },
                                                            enabled = !s.busy && s.editable,
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = MaterialTheme.colorScheme.primary,
                                                                contentColor = BlancoPuro
                                                            ),
                                                            shape = RoundedCornerShape(10.dp),
                                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                            modifier = Modifier.height(36.dp)
                                                        ) {
                                                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                                            Spacer(Modifier.width(4.dp))
                                                            Text("Agregar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                        }
                                                    } else {
                                                        Surface(
                                                            shape = RoundedCornerShape(10.dp),
                                                            color = c.acentoSuave,
                                                            border = BorderStroke(1.dp, c.acento.copy(alpha = 0.38f))
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                                modifier = Modifier.padding(2.dp)
                                                            ) {
                                                                val indice = b.lineas.indexOfFirst { it.idProducto == p.idProducto }
                                                                IconButton(
                                                                    onClick = {
                                                                        if (indice >= 0) {
                                                                            val actual = b.lineas[indice]
                                                                            if (actual.cantidad > 1) {
                                                                                vm.cantidad(indice, -1)
                                                                            } else {
                                                                                vm.quitar(indice)
                                                                            }
                                                                        }
                                                                    },
                                                                    modifier = Modifier.size(32.dp),
                                                                    enabled = !s.busy && s.editable
                                                                ) {
                                                                    Icon(Icons.Default.Remove, null, modifier = Modifier.size(16.dp), tint = c.acento)
                                                                }

                                                                Text(
                                                                    cantBorrador.toString(),
                                                                    fontWeight = FontWeight.ExtraBold,
                                                                    style = MaterialTheme.typography.titleMedium.tabular(),
                                                                    color = c.acento,
                                                                    modifier = Modifier.widthIn(min = 22.dp),
                                                                    textAlign = TextAlign.Center
                                                                )

                                                                IconButton(
                                                                    onClick = { vm.agregar(p) },
                                                                    modifier = Modifier.size(32.dp),
                                                                    enabled = !s.busy && s.editable
                                                                ) {
                                                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp), tint = c.acento)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Vista de Resumen de Comanda (Muestra pedidos del servidor o borrador local)
                if (lineasVisibles.isEmpty()) {
                    Empty("La comanda está vacía", "Agrega platos desde la pestaña 'Catálogo Menú' o recupera el pedido por ID.")
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        if (pedidoServidor != null) {
                            item {
                                val esOfflinePedido = pedidoServidor.estado == "PEDIDO_POR_SINCRONIZAR"
                                val esListoOffline = pedidoServidor.estado == "LISTO_POR_SINCRONIZAR"
                                val esListoPedido = pedidoServidor.estado == "LISTO"
                                val colorCard = when {
                                    esOfflinePedido || esListoOffline -> c.ambarBg
                                    esListoPedido -> c.azulBg
                                    else -> c.verdeBg
                                }
                                val tintCard = when {
                                    esOfflinePedido || esListoOffline -> if (s.modoOscuro) AmbarDorado else AmbarProfundo
                                    esListoPedido -> c.azul
                                    else -> c.verde
                                }
                                val tituloEstado = when (pedidoServidor.estado) {
                                    "PEDIDO_POR_SINCRONIZAR" -> "PEDIDO (POR SINCRONIZAR)"
                                    "LISTO_POR_SINCRONIZAR" -> "LISTO EN COCINA (POR SINCRONIZAR)"
                                    "LISTO" -> "Pedido Listo para Entregar"
                                    else -> "Pedido Registrado en Servidor"
                                }
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = colorCard,
                                    border = BorderStroke(1.dp, tintCard.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(tituloEstado, fontWeight = FontWeight.Bold, color = tintCard)
                                            Text(
                                                "Mozo: ${pedidoServidor.codigoMozo.orEmpty()} · Estado: ${pedidoServidor.estado.replace('_', ' ')}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = c.textoSecundario
                                            )
                                        }
                                        Icon(
                                            if (esOfflinePedido || esListoOffline) Icons.Default.CloudUpload else Icons.Default.CloudDone,
                                            null,
                                            tint = tintCard
                                        )
                                    }
                                }
                            }
                        }

                        itemsIndexed(lineasVisibles) { index, l ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                shadowElevation = 2.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(l.nombre, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), color = c.textoPrincipal)
                                        Text(dinero(l.precioCentimos * l.cantidad), fontWeight = FontWeight.ExtraBold, color = c.acento, style = MaterialTheme.typography.titleMedium.tabular())
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("${dinero(l.precioCentimos)} c/u", style = MaterialTheme.typography.bodySmall.tabular(), color = c.textoSecundario)
                                        if (pedidoServidor != null) {
                                            val estColor = when (l.estado) {
                                                "LISTO", "ENTREGADO" -> c.verde
                                                "LISTO_POR_SINCRONIZAR" -> if (s.modoOscuro) AmbarDorado else AmbarProfundo
                                                else -> c.textoSecundario
                                            }
                                            val estBg = when (l.estado) {
                                                "LISTO", "ENTREGADO" -> c.verdeBg
                                                "LISTO_POR_SINCRONIZAR" -> c.ambarBg
                                                else -> MaterialTheme.colorScheme.surfaceContainerLow
                                            }
                                            EstadoPill(
                                                texto = when (l.estado) {
                                                    "LISTO_POR_SINCRONIZAR" -> "LISTO (POR SINCRONIZAR)"
                                                    else -> l.estado.replace('_', ' ')
                                                },
                                                colorContenido = estColor,
                                                colorFondo = estBg,
                                                borde = estColor.copy(alpha = 0.3f),
                                                icono = when (l.estado) {
                                                    "LISTO", "ENTREGADO" -> Icons.Default.CheckCircle
                                                    "LISTO_POR_SINCRONIZAR" -> Icons.Default.CloudUpload
                                                    else -> Icons.Default.Schedule
                                                }
                                            )
                                        }
                                    }

                                    if (l.observaciones.isNotBlank()) {
                                        Surface(shape = RoundedCornerShape(10.dp), color = c.ambarBg) {
                                            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Info, null, modifier = Modifier.size(14.dp), tint = if (s.modoOscuro) AmbarDorado else AmbarProfundo)
                                                Spacer(Modifier.width(4.dp))
                                                Text(l.observaciones, style = MaterialTheme.typography.labelSmall, color = if (s.modoOscuro) AmbarDorado else AmbarProfundo, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        if (s.editable) {
                                            IconButton(
                                                onClick = { vm.cantidad(index, -1) },
                                                enabled = !s.busy && l.cantidad > 1
                                            ) { Icon(Icons.Default.RemoveCircleOutline, "Restar", tint = c.acento) }

                                            Text(
                                                l.cantidad.toString(),
                                                fontWeight = FontWeight.ExtraBold,
                                                style = MaterialTheme.typography.titleMedium.tabular(),
                                                modifier = Modifier.padding(horizontal = 8.dp),
                                                color = c.textoPrincipal
                                            )

                                            IconButton(
                                                onClick = { vm.cantidad(index, 1) },
                                                enabled = !s.busy && l.cantidad < 99
                                            ) { Icon(Icons.Default.AddCircleOutline, "Sumar", tint = c.acento) }

                                            Spacer(Modifier.weight(1f))

                                            IconButton(onClick = { editarNota = index }, enabled = !s.busy) {
                                                Icon(Icons.Default.EditNote, "Notas", tint = if (s.modoOscuro) AmbarDorado else AmbarProfundo)
                                            }
                                            IconButton(onClick = { quitar = index }, enabled = !s.busy) {
                                                Icon(Icons.Default.DeleteOutline, "Eliminar", tint = RojoCoral)
                                            }
                                        } else {
                                            Text("Cantidad: ${l.cantidad} unidad(es)", style = MaterialTheme.typography.bodyMedium.tabular(), fontWeight = FontWeight.Bold, color = c.textoPrincipal)
                                            Spacer(Modifier.weight(1f))
                                            EstadoPill(
                                                texto = l.estado.replace('_', ' '),
                                                colorContenido = if (l.estado == "LISTO") c.verde else (if (s.modoOscuro) AmbarDorado else AmbarProfundo),
                                                colorFondo = if (l.estado == "LISTO") c.verdeBg else c.ambarBg
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            OutlinedTextField(
                                value = b.observaciones.ifBlank { pedidoServidor?.observaciones.orEmpty() },
                                onValueChange = vm::observaciones,
                                label = { Text("Nota general del pedido") },
                                enabled = s.editable && b.idPedido == null && !s.busy,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 3
                            )
                        }

                        if (pedidoServidor != null) {
                            val esPropioOAdmin = s.rol.uppercase() == "ADMINISTRADOR" ||
                                pedidoServidor.codigoMozo.equals(s.sesion?.usuario?.codigoTrabajador, ignoreCase = true)
                            item {
                                OutlinedButton(
                                    onClick = { mover = true },
                                    enabled = !s.busy && esPropioOAdmin && pedidoServidor.estado !in listOf("PAGADO", "ANULADO"),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.SwapHoriz, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Trasladar comanda a otra mesa")
                                }
                            }
                        }

                        if (b.idPedido == null && !b.creacionIncierta) {
                            item {
                                TextButton(
                                    onClick = { descartar = true },
                                    enabled = !s.busy,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Descartar borrador local", color = RojoCoral)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Dock Inferior Táctil Flotante (Menú) o Resumen Completo (Comanda)
        if (tab == "Menú") {
            AnimatedVisibility(
                visible = b.lineas.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    color = c.superficie,
                    border = BorderStroke(1.dp, c.borde),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(shape = CircleShape, color = Borgona) {
                                    Text(
                                        "${b.lineas.sumOf { it.cantidad }}",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = BlancoPuro,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text("Platos seleccionados", style = MaterialTheme.typography.labelMedium, color = c.textoSecundario)
                            }
                            Text(
                                dinero(b.lineas.total()),
                                style = MaterialTheme.typography.titleLarge.tabular(),
                                fontWeight = FontWeight.ExtraBold,
                                color = c.acento
                            )
                        }

                        Button(
                            onClick = { tab = "Comanda" },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Ver Comanda", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        } else {
            // Barra inferior de totales y despacho
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val totalCalculado = pedidoServidor?.importeCentimos ?: lineasVisibles.total()
                    val subtotalCentimos = (totalCalculado / 1.18).toLong()
                    val igvCentimos = totalCalculado - subtotalCentimos

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Subtotal:", style = MaterialTheme.typography.bodySmall, color = c.textoSecundario)
                        Text(dinero(subtotalCentimos), style = MaterialTheme.typography.bodySmall.tabular(), color = c.textoPrincipal)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("IGV (18%):", style = MaterialTheme.typography.bodySmall, color = c.textoSecundario)
                        Text(dinero(igvCentimos), style = MaterialTheme.typography.bodySmall.tabular(), color = c.textoPrincipal)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Total Comanda:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.textoPrincipal)
                        Text(dinero(totalCalculado), style = MaterialTheme.typography.headlineSmall.tabular(), fontWeight = FontWeight.ExtraBold, color = c.acento)
                    }

                    if (s.editable) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = vm::guardarBorrador,
                                enabled = !s.busy && b.lineas.isNotEmpty(),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) { Text("Guardar Borrador", fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }

                            Button(
                                onClick = { confirmarEnvio = true },
                                enabled = !s.busy && b.lineas.isNotEmpty(),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Enviar a Cocina", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }

    editarNota?.let { i ->
        b.lineas.getOrNull(i)?.let { l ->
            NotaDialog(l.observaciones, { editarNota = null }) { vm.notas(i, it); editarNota = null }
        }
    }
    quitar?.let { i ->
        Confirmar("¿Eliminar plato?", "¿Deseas quitar '${b.lineas.getOrNull(i)?.nombre}' de la comanda?", { quitar = null }) {
            vm.quitar(i); quitar = null
        }
    }
    if (confirmarEnvio) {
        Confirmar(
            "Enviar comanda a cocina",
            "Se notificará al KDS de cocina en tiempo real. Después del envío, las modificaciones se gestionan como adiciones.",
            { confirmarEnvio = false }
        ) {
            confirmarEnvio = false
            vm.enviar()
        }
    }
    if (descartar) {
        Confirmar(
            "Descartar comanda local",
            "Se borrarán los platos acumulados en este borrador.",
            { descartar = false }
        ) {
            descartar = false
            vm.descartarLocal()
        }
    }
    if (mover) {
        TrasladoDialog(s, { mover = false }) { destino, motivo ->
            mover = false
            vm.trasladar(destino, motivo)
        }
    }
}

@Composable
private fun NotaDialog(inicial: String, cerrar: () -> Unit, guardar: (String) -> Unit) {
    val c = LocalColores.current
    var nota by remember { mutableStateOf(inicial) }
    AlertDialog(
        onDismissRequest = cerrar,
        shape = RoundedCornerShape(16.dp),
        title = { Text("Instrucciones para Cocina", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Sugerencias rápidas:", style = MaterialTheme.typography.bodySmall, color = c.textoSecundario)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Sin mayonesa", "Papas bien fritas", "Pechuga", "Pierna", "Ají extra", "Sin tártara").forEach { sugerencia ->
                        SuggestionChip(
                            onClick = { nota = if (nota.isBlank()) sugerencia else "$nota. $sugerencia" },
                            label = { Text(sugerencia, fontSize = 12.sp) }
                        )
                    }
                }
                OutlinedTextField(
                    value = nota,
                    onValueChange = { nota = it.take(300) },
                    maxLines = 4,
                    label = { Text("Nota al plato") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("${nota.length}/300") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { guardar(nota) },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Guardar Nota")
            }
        },
        dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } }
    )
}

@Composable
private fun TrasladoDialog(s: PosUiState, cerrar: () -> Unit, aceptar: (Long, String) -> Unit) {
    var destino by remember { mutableStateOf<Long?>(null) }
    var motivo by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = cerrar,
        shape = RoundedCornerShape(16.dp),
        title = { Text("Trasladar Comanda", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Selecciona la nueva mesa libre en salón:", style = MaterialTheme.typography.bodySmall)
                val libres = s.mesas.filter { it.estado == "LIBRE" && it.idMesa != s.mesa?.idMesa }
                if (libres.isEmpty()) {
                    Text("No hay mesas libres disponibles en este momento.", color = RojoCoral)
                }
                libres.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clickable { destino = m.idMesa }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = destino == m.idMesa, onClick = { destino = m.idMesa })
                        Text("${m.codigoMesa} · ${m.nombreArea} (${m.capacidad} pers.)", fontWeight = FontWeight.Medium)
                    }
                }
                OutlinedTextField(
                    value = motivo,
                    onValueChange = { motivo = it.take(250) },
                    label = { Text("Motivo de traslado (opcional)") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { destino?.let { aceptar(it, motivo) } },
                enabled = destino != null,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text("Confirmar Traslado") }
        },
        dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } }
    )
}

// -------------------------------------------------------------
// 4. KDS PRODUCCION EN COCINA (HU-08 A HU-12)
// -------------------------------------------------------------

@Composable
private fun CocinaKds(s: PosUiState, vm: PosViewModel) {
    val c = LocalColores.current
    var buscar by rememberSaveable { mutableStateOf("") }
    var filtro by rememberSaveable { mutableStateOf("Todos") }
    var ahora by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
    var verAgotados by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            ahora = Clock.System.now().toEpochMilliseconds()
            delay(1000)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(4.dp))

        // Banner Deslizable de Nueva Comanda Entrante (HU-10 / Notificación en tiempo real)
        AnimatedVisibility(
            visible = s.nuevoPedidoCocinaAviso != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            s.nuevoPedidoCocinaAviso?.let { nuevo ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (s.modoOscuro) Color(0xFF1B5E20) else Color(0xFFE8F5E9),
                    border = BorderStroke(2.dp, VerdeEsmeralda),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = VerdeEsmeralda,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.NotificationsActive, null, tint = BlancoPuro, modifier = Modifier.size(24.dp))
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "¡NUEVA COMANDA RECIBIDA!",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    color = if (s.modoOscuro) BlancoPuro else Color(0xFF1B5E20)
                                )
                                Surface(shape = RoundedCornerShape(6.dp), color = VerdeEsmeralda) {
                                    Text("EN VIVO", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BlancoPuro)
                                }
                            }
                            Text(
                                "Mesa ${nuevo.codigoMesa} · ${nuevo.lineas.size} items para preparar",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (s.modoOscuro) BlancoPuro.copy(alpha = 0.9f) else Color(0xFF2E7D32)
                            )
                            if (nuevo.lineas.isNotEmpty()) {
                                Text(
                                    nuevo.lineas.joinToString(", ") { "${it.cantidad}x ${it.nombre}" }.take(65) + if (nuevo.lineas.size > 2) "..." else "",
                                    fontSize = 11.sp,
                                    color = c.textoSecundario,
                                    maxLines = 1
                                )
                            }
                        }
                        IconButton(onClick = { vm.cerrarNotificacionCocina() }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, null, tint = if (s.modoOscuro) BlancoPuro else Color(0xFF1B5E20), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Heading(
                    "Monitor KDS Cocina",
                    if (s.socketConectado) "En Vivo · WebSocket Conectado" else "Sondeo cada 15s"
                )
                EstadoPill(
                    texto = if (s.socketConectado) "WebSocket En Vivo · KDS" else "Modo Sondeo (15s)",
                    colorContenido = if (s.socketConectado) c.verde else (if (s.modoOscuro) AmbarDorado else AmbarProfundo),
                    colorFondo = if (s.socketConectado) c.verdeBg else c.ambarBg,
                    dotColor = if (s.socketConectado) c.verde else AmbarAlerta
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { verAgotados = true },
                colors = ButtonDefaults.buttonColors(containerColor = AmbarProfundo, contentColor = BlancoPuro),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Block, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Agotados", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        s.cocinaAviso?.let { Notice(it, true) }

        OutlinedTextField(
            value = buscar,
            onValueChange = { buscar = it },
            label = { Text("Filtrar mesa o plato en cocina...") },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = c.acento) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // Filtros de comandas
        Chips(listOf("Todos", "Enviados", "En preparación", "Listos"), filtro) { filtro = it }

        val pedidosCocina = s.cocina.filter { p ->
            (filtro == "Todos" || p.estado == when (filtro) {
                "Enviados" -> "ENVIADO_COCINA"
                "Listos" -> "LISTO"
                else -> "EN_PREPARACION"
            }) &&
            (p.codigoMesa.contains(buscar, true) || p.lineas.any { it.nombre.contains(buscar, true) })
        }.sortedBy { it.fechaEnvio.orEmpty() }

        if (pedidosCocina.isEmpty()) {
            Empty("Sin órdenes pendientes", "La cocina está al día. Las nuevas comandas aparecerán automáticamente.", Icons.Default.DoneAll)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(pedidosCocina, key = { it.idPedido }) { p ->
                    val minEspera = (p.minutosTranscurridos ?: 0L).toInt().coerceAtLeast(0)
                    val nivel = p.nivelDemora ?: when {
                        minEspera >= 15 -> "ROJO"
                        minEspera >= 10 -> "AMARILLO"
                        else -> "VERDE"
                    }
                    val colorEspera = when (nivel) {
                        "ROJO", "CRITICO" -> RojoCoral
                        "AMARILLO", "ALERTA" -> AmbarAlerta
                        else -> c.verde
                    }
                    val bgEspera = when (nivel) {
                        "ROJO", "CRITICO" -> c.rojoBg
                        "AMARILLO", "ALERTA" -> c.ambarBg
                        else -> c.verdeBg
                    }
                    val etiquetaSemaforo = when (nivel) {
                        "ROJO", "CRITICO" -> "URGENTE"
                        "AMARILLO", "ALERTA" -> "ATENCIÓN"
                        else -> "A TIEMPO"
                    }
                    val bordeAnimado by animateColorAsState(colorEspera, label = "kdsBorder")
                    val listosCount = p.lineas.count { it.estado == "LISTO" || it.estado == "ENTREGADO" }
                    val progresoKds by animateFloatAsState(
                        targetValue = if (p.lineas.isEmpty()) 0f else listosCount.toFloat() / p.lineas.size,
                        label = "kdsProg"
                    )

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.5.dp, bordeAnimado),
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth().animateContentSize()
                    ) {
                        Column {
                            // Cabecera Semáforo de Cocina
                            Surface(
                                color = bgEspera.copy(alpha = if (c.esOscuro) 0.55f else 0.72f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                "MESA ${p.codigoMesa}",
                                                style = MaterialTheme.typography.titleLarge.tabular(),
                                                fontWeight = FontWeight.ExtraBold,
                                                color = c.acento
                                            )
                                            EstadoPill(
                                                texto = etiquetaSemaforo,
                                                colorContenido = colorEspera,
                                                colorFondo = MaterialTheme.colorScheme.surface,
                                                borde = colorEspera.copy(alpha = 0.45f),
                                                dotColor = colorEspera
                                            )
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            "${p.codigoPedido} · Mozo: ${p.codigoMozo.orEmpty()} · $listosCount/${p.lineas.size} listos",
                                            style = MaterialTheme.typography.labelMedium.tabular(),
                                            color = c.textoSecundario
                                        )
                                    }
                                    EstadoPill(
                                        texto = "$minEspera min",
                                        colorContenido = colorEspera,
                                        colorFondo = MaterialTheme.colorScheme.surface,
                                        borde = colorEspera.copy(alpha = 0.45f),
                                        icono = Icons.Default.Timer
                                    )
                                }
                            }

                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                LinearProgressIndicator(
                                    progress = { progresoKds },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = if (listosCount == p.lineas.size && p.lineas.isNotEmpty()) c.verde else colorEspera,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )

                                // Lista de platos con botón táctil individual FUNCIONAL (idDetallePedido)
                                p.lineas.forEach { l ->
                                    val platoListoSync = l.estado == "LISTO_POR_SINCRONIZAR"
                                    val platoListo = l.estado in listOf("LISTO", "LISTO_POR_SINCRONIZAR", "ENTREGADO")
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (platoListo) MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f) else MaterialTheme.colorScheme.surfaceContainerLow,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (platoListoSync) c.ambarBg else if (platoListo) c.verdeBg else c.acentoSuave,
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        "${l.cantidad}x",
                                                        style = MaterialTheme.typography.titleSmall.tabular(),
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = if (platoListoSync) (if (s.modoOscuro) AmbarDorado else AmbarProfundo) else if (platoListo) c.verde else c.acento
                                                    )
                                                }
                                            }

                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    l.nombre,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = if (platoListo) c.textoSecundario else c.textoPrincipal,
                                                    textDecoration = if (platoListo) TextDecoration.LineThrough else TextDecoration.None
                                                )
                                                if (l.observaciones.isNotBlank()) {
                                                    Text(
                                                        "Nota: ${l.observaciones}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (s.modoOscuro) Color(0xFFFFCC66) else AmbarProfundo,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }

                                            Button(
                                                onClick = { l.idDetalle?.let { vm.marcarPlatoListo(it) } },
                                                enabled = !s.busy && !platoListo && l.idDetalle != null,
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (platoListoSync) AmbarProfundo else if (platoListo) VerdeEsmeralda else MaterialTheme.colorScheme.primary,
                                                    contentColor = BlancoPuro,
                                                    disabledContainerColor = if (platoListoSync) c.ambarBg else c.verdeBg
                                                ),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                if (platoListoSync) {
                                                    Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(14.dp), tint = if (s.modoOscuro) AmbarDorado else AmbarProfundo)
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Listo (Sync)", color = if (s.modoOscuro) AmbarDorado else AmbarProfundo, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, softWrap = false)
                                                } else if (platoListo) {
                                                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp), tint = c.verde)
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Listo", color = c.verde, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false)
                                                } else {
                                                    Text("Listo ✓", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false)
                                                }
                                            }
                                        }
                                    }
                                }

                                if (p.idPedido > 0 && listosCount < p.lineas.size) {
                                    Button(
                                        onClick = { vm.marcarPedidoListoCocina(p.idPedido) },
                                        enabled = !s.busy,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda, contentColor = BlancoPuro),
                                        modifier = Modifier.fillMaxWidth().height(44.dp)
                                    ) {
                                        Icon(Icons.Default.DoneAll, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Marcar todo el pedido listo", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }

                                if (!p.observaciones.isNullOrBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = c.ambarBg,
                                        border = BorderStroke(1.dp, AmbarDorado.copy(alpha = 0.3f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "Comanda: ${p.observaciones}",
                                            modifier = Modifier.padding(10.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (s.modoOscuro) Color(0xFFFFCC66) else AmbarProfundo
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (verAgotados) {
        AgotadosDialog(s, { verAgotados = false }) { idProd, disp ->
            vm.cambiarDisponibilidad(idProd, disp)
        }
    }
}

@Composable
private fun AgotadosDialog(s: PosUiState, cerrar: () -> Unit, cambiar: (Long, Boolean) -> Unit) {
    val c = LocalColores.current
    val listaCatalogo = s.productosTodos.ifEmpty { s.productos }
    AlertDialog(
        onDismissRequest = cerrar,
        title = { Text("Disponibilidad de Platos (Agotados)", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Desactiva o reactiva los platos disponibles en el menú.", style = MaterialTheme.typography.bodySmall, color = c.textoSecundario)
                if (listaCatalogo.isEmpty()) {
                    Text("No se cargaron platos del catálogo.", style = MaterialTheme.typography.bodySmall, color = c.textoSecundario)
                } else {
                    listaCatalogo.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(p.nombre, fontWeight = FontWeight.Medium, color = c.textoPrincipal)
                                Text(
                                    if (p.disponible) p.categoria else "${p.categoria} · AGOTADO",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (p.disponible) c.textoSecundario else RojoCoral
                                )
                            }
                            Switch(
                                checked = p.disponible,
                                onCheckedChange = { checked -> cambiar(p.idProducto, checked) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = cerrar) { Text("Cerrar") } }
    )
}

// -------------------------------------------------------------
// 5. CAJA, FACTURACION Y COBROS (HU-13 A HU-17)
// -------------------------------------------------------------

@Composable
private fun CajaScreen(s: PosUiState, vm: PosViewModel) {
    val c = LocalColores.current
    var montoApertura by rememberSaveable { mutableStateOf("150.00") }
    var cerrarCajaModal by remember { mutableStateOf(false) }
    var efectivoArqueo by remember { mutableStateOf("") }
    var motivoArqueo by remember { mutableStateOf("") }

    // Cobro State
    var buscarPedidoId by rememberSaveable { mutableStateOf("") }
    var metodoPago by rememberSaveable { mutableStateOf("Efectivo") }
    var efectivoRecibido by rememberSaveable { mutableStateOf("") }
    var numOperacion by rememberSaveable { mutableStateOf("") }
    var mixtoMetodo by rememberSaveable { mutableStateOf("Yape") }
    var mixtoEfectivo by rememberSaveable { mutableStateOf("") }
    var mixtoDigital by rememberSaveable { mutableStateOf("") }
    var mixtoNumOp by rememberSaveable { mutableStateOf("") }
    var telefonoWhatsApp by rememberSaveable { mutableStateOf("") }
    var enviarWhatsApp by rememberSaveable { mutableStateOf(false) }

    // Tarjeta Simulator State
    var tarjetaNumero by rememberSaveable { mutableStateOf("4532 0151 1283 0366") }
    var tarjetaMes by rememberSaveable { mutableStateOf("12") }
    var tarjetaAnio by rememberSaveable { mutableStateOf("2028") }
    var tarjetaCvv by rememberSaveable { mutableStateOf("123") }
    var tarjetaTitular by rememberSaveable { mutableStateOf("RANDY HUAMAN") }

    // Cliente SUNAT State
    var tipoDoc by rememberSaveable { mutableStateOf("DNI") }
    var numDoc by rememberSaveable { mutableStateOf("") }
    var razonSocialCliente by rememberSaveable { mutableStateOf("") }
    var direccionCliente by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(s.clienteSeleccionado) {
        s.clienteSeleccionado?.let { cli ->
            if (cli.tipoDocumento.equals(tipoDoc, ignoreCase = true) && cli.numeroDocumento == numDoc) {
                if (cli.razonSocial.isNotBlank()) razonSocialCliente = cli.razonSocial
                if (!cli.direccion.isNullOrBlank()) direccionCliente = cli.direccion
            }
        }
    }

    LaunchedEffect(tipoDoc, numDoc) {
        val largoRequerido = when (tipoDoc) {
            "DNI" -> 8
            "RUC" -> 11
            else -> 0
        }
        if (largoRequerido > 0 && numDoc.length == largoRequerido) {
            val yaCargado = s.clienteSeleccionado?.let {
                it.tipoDocumento.equals(tipoDoc, ignoreCase = true) && it.numeroDocumento == numDoc
            } == true
            if (!yaCargado) {
                kotlinx.coroutines.delay(280)
                vm.buscarCliente(tipoDoc, numDoc)
            }
        }
    }

    LaunchedEffect(s.pedido?.idPedido) {
        numDoc = ""
        razonSocialCliente = ""
        direccionCliente = ""
        efectivoRecibido = ""
        enviarWhatsApp = false
        telefonoWhatsApp = ""
        vm.limpiarCliente()
    }

    // Descuento y Anulación Modal State
    var descuentoModal by remember { mutableStateOf(false) }
    var porcentajeDesc by remember { mutableStateOf("10.0") }
    var motivoDesc by remember { mutableStateOf("") }
    var anularModal by remember { mutableStateOf(false) }
    var motivoAnular by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(4.dp))
        Heading("Caja & Facturación Electrónica", "Apertura de turno, boletas, facturas y cobros multicanal")
        s.colaSync.filter { it.tipo == TipoAccionSync.COBRAR_PEDIDO }.forEach { cobroPend ->
            val acentoAmbar = if (s.modoOscuro) AmbarDorado else AmbarProfundo
            Surface(Modifier.fillMaxWidth(), color = c.ambarBg, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, acentoAmbar.copy(alpha = 0.4f))) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CloudUpload, null, tint = acentoAmbar)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        EstadoPill(
                            texto = "Cobro en cola · POR SINCRONIZAR",
                            colorContenido = acentoAmbar,
                            colorFondo = MaterialTheme.colorScheme.surface,
                            borde = acentoAmbar.copy(alpha = 0.35f)
                        )
                        Text(cobroPend.descripcion, style = MaterialTheme.typography.bodySmall, color = c.textoSecundario)
                    }
                }
            }
        }
        s.pendientesEmision.forEach { solicitud ->
            Surface(
                Modifier.fillMaxWidth(),
                color = c.ambarBg,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, AmbarDorado.copy(alpha = 0.4f))
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Comprobante pendiente · Pedido #${solicitud.idPedido}", style = MaterialTheme.typography.titleSmall.tabular(), fontWeight = FontWeight.Bold)
                    Text("Pago #${solicitud.idPago} confirmado.", style = MaterialTheme.typography.bodySmall.tabular())
                    TextButton(onClick = { vm.recuperarComprobante(solicitud) }, enabled = !s.busy) { Text("Recuperar comprobante") }
                }
            }
        }

        // 1. Estado del Turno de Caja (Apertura o Resumen)
        val turno = s.turnoCaja
        if (turno == null || turno.estado != "ABIERTO") {
            s.cierreInfo?.let { cierre ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.5.dp, c.verde),
                    shadowElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Turno #${cierre.idTurno} Cerrado", style = MaterialTheme.typography.titleMedium.tabular(), fontWeight = FontWeight.Bold, color = c.verde)
                            EstadoPill(
                                texto = "Arqueo Finalizado",
                                colorContenido = c.verde,
                                colorFondo = c.verdeBg,
                                icono = Icons.Default.CheckCircle
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Fondo Inicial:", color = c.textoSecundario, fontSize = 13.sp)
                            Text(dinero(cierre.montoInicialCentimos), style = MaterialTheme.typography.bodyMedium.tabular(), fontWeight = FontWeight.Bold, color = c.textoPrincipal)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Efectivo Esperado:", color = c.textoSecundario, fontSize = 13.sp)
                            Text(dinero(cierre.totalEfectivoEsperadoCentimos), style = MaterialTheme.typography.bodyMedium.tabular(), fontWeight = FontWeight.Bold, color = c.textoPrincipal)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Efectivo Real en Gaveta:", color = c.textoSecundario, fontSize = 13.sp)
                            Text(dinero(cierre.totalEfectivoRealCentimos), style = MaterialTheme.typography.bodyMedium.tabular(), fontWeight = FontWeight.Bold, color = c.textoPrincipal)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Diferencia de Cuadre:", color = c.textoSecundario, fontSize = 13.sp)
                            val cuadreOk = cierre.diferenciaCentimos == 0L
                            Text(
                                if (cuadreOk) "S/ 0.00 (Exacto)" else dinero(cierre.diferenciaCentimos),
                                style = MaterialTheme.typography.bodyMedium.tabular(),
                                fontWeight = FontWeight.ExtraBold,
                                color = if (cuadreOk) c.verde else RojoCoral
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.5.dp, if (s.modoOscuro) AmbarDorado else AmbarProfundo),
                shadowElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = c.ambarBg,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.LockClock, null, tint = if (s.modoOscuro) AmbarDorado else AmbarProfundo, modifier = Modifier.size(18.dp))
                            }
                        }
                        Text("Turno de Caja No Iniciado", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.textoPrincipal, modifier = Modifier.weight(1f))
                        if (s.pendientesCobro.isNotEmpty()) {
                            EstadoPill(
                                texto = "${s.pendientesCobro.size} en espera",
                                colorContenido = if (s.modoOscuro) AmbarDorado else AmbarProfundo,
                                colorFondo = c.ambarBg
                            )
                        }
                    }
                    Text("Ingresa el fondo inicial en efectivo para habilitar los cobros del restaurante.", style = MaterialTheme.typography.bodyMedium, color = c.textoSecundario)
                    OutlinedTextField(
                        value = montoApertura,
                        onValueChange = { montoApertura = it },
                        label = { Text("Fondo Inicial (S/.)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { vm.abrirCaja(montoApertura.toDoubleOrNull() ?: 150.0) },
                        enabled = !s.busy && (montoApertura.toDoubleOrNull() ?: 0.0) >= 0,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Abrir Turno de Caja", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // Tarjeta de Turno Abierto con gradiente profesional Bistro
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                shadowElevation = 5.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Borgona, BorgonaOscuro)))
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(VerdeEsmeralda))
                                Text("Turno Abierto #${turno.idTurno}", style = MaterialTheme.typography.titleMedium.tabular(), fontWeight = FontWeight.ExtraBold, color = BlancoPuro)
                            }
                            Button(
                                onClick = { cerrarCajaModal = true },
                                colors = ButtonDefaults.buttonColors(containerColor = BlancoPuro.copy(alpha = 0.18f), contentColor = BlancoPuro),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Cerrar Turno", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text("Cajero: ${s.sesion?.usuario?.nombreCompleto} (${turno.cajero})", style = MaterialTheme.typography.bodyMedium, color = BlancoPuro.copy(alpha = 0.9f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Fondo Inicial: ${dinero(turno.montoInicialCentimos)}", style = MaterialTheme.typography.bodySmall.tabular(), color = AmbarDorado, fontWeight = FontWeight.Bold)
                            EstadoPill(
                                texto = "OPERATIVO · ${s.pendientesCobro.size} POR COBRAR",
                                colorContenido = BlancoPuro,
                                colorFondo = VerdeEsmeralda.copy(alpha = 0.25f),
                                borde = VerdeEsmeralda.copy(alpha = 0.55f),
                                dotColor = VerdeEsmeralda
                            )
                        }
                    }
                }
            }

            // 2. Selección de Pedido para Cobrar (Cuentas reales enviadas pendientes de pago)
            Text("Buscar Comanda para Cobrar:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = c.textoPrincipal)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = buscarPedidoId,
                    onValueChange = { buscarPedidoId = it.filter(Char::isDigit) },
                    label = { Text("Número de Pedido (ID)") },
                    placeholder = { Text("ej. 1, 2, 3") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        vm.cargarPedidoCaja(buscarPedidoId)
                    },
                    enabled = !s.busy && buscarPedidoId.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("Cargar Cuenta") }
            }

            // Cuentas pendientes reales (disponible para Cajero y Administrador)
            val cuentasPendientes = s.pendientesCobro
            if (cuentasPendientes.isNotEmpty()) {
                Text("Cuentas Pendientes de Cobro (${cuentasPendientes.size}):", style = MaterialTheme.typography.labelLarge.tabular(), color = c.textoSecundario)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    items(cuentasPendientes, key = { it.idPedido }) { ped ->
                        val seleccionada = s.pedido?.idPedido == ped.idPedido
                        val bgPend by animateColorAsState(
                            if (seleccionada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            animationSpec = tween(180),
                            label = "PendienteBg"
                        )
                        val borderPend by animateColorAsState(
                            if (seleccionada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            animationSpec = tween(180),
                            label = "PendienteBorder"
                        )
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = bgPend,
                            border = BorderStroke(1.dp, borderPend),
                            shadowElevation = if (seleccionada) 3.dp else 0.dp,
                            modifier = Modifier.clickable {
                                vm.seleccionarPedidoCaja(ped)
                                buscarPedidoId = ped.idPedido.toString()
                                efectivoRecibido = ""
                            }
                        ) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(
                                    "${ped.codigoMesa} · ${ped.codigoPedido}",
                                    style = MaterialTheme.typography.titleSmall.tabular(),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (seleccionada) BlancoPuro else c.textoPrincipal
                                )
                                Text(
                                    "${dinero(ped.importeCentimos)} · ${ped.estado.replace('_', ' ')}",
                                    style = MaterialTheme.typography.labelSmall.tabular(),
                                    color = if (seleccionada) AmbarDorado else c.textoSecundario,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = c.verde, modifier = Modifier.size(18.dp))
                        Text(
                            "No hay cuentas pendientes de cobro.",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textoSecundario
                        )
                    }
                }
            }

            val pedidoActual = s.pedido
            val totalCentimos = pedidoActual?.importeCentimos ?: 0L
            val totalDouble = totalCentimos / 100.0

            if (pedidoActual != null && totalCentimos > 0) {
                // Tarjeta de Detalle del Pedido a Cobrar
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Pedido ${pedidoActual.codigoPedido}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = c.textoPrincipal)
                                Text("Mesa: ${pedidoActual.codigoMesa}", style = MaterialTheme.typography.bodySmall, color = c.textoSecundario)
                            }
                            Text(dinero(totalCentimos), fontWeight = FontWeight.ExtraBold, color = c.acento, style = MaterialTheme.typography.headlineSmall.tabular())
                        }

                        // Detalle del consumo
                        if (pedidoActual.lineas.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Detalle del Consumo:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall, color = c.textoSecundario)
                                    pedidoActual.lineas.forEach { linea ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("${linea.cantidad}x ${linea.nombre}", fontSize = 13.sp, color = c.textoPrincipal, modifier = Modifier.weight(1f))
                                            Text(dinero(linea.precioCentimos * linea.cantidad), style = MaterialTheme.typography.bodySmall.tabular(), fontWeight = FontWeight.Bold, color = c.textoPrincipal)
                                        }
                                    }
                                }
                            }
                        }

                        // Botones de Descuento (HU-17) y Anulación (solo Admin)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { descuentoModal = true },
                                enabled = !s.busy,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Percent, null, tint = c.acento, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Descuento", color = c.acento)
                            }
                            if (s.puedeAdmin) {
                                OutlinedButton(
                                    onClick = { anularModal = true },
                                    enabled = !s.busy,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, RojoCoral.copy(alpha = 0.5f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Cancel, null, tint = RojoCoral, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Anular", color = RojoCoral)
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // 3. Consulta de Cliente SUNAT (DNI / RUC / Sin Doc)
                        Text("Comprobante y Datos del Cliente (SUNAT):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = c.textoPrincipal)
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("DNI", "RUC", "Sin Doc").forEach { t ->
                                FilterChip(
                                    selected = tipoDoc == t,
                                    onClick = {
                                        tipoDoc = t
                                        numDoc = ""
                                        razonSocialCliente = ""
                                        direccionCliente = ""
                                        vm.limpiarCliente()
                                    },
                                    label = {
                                        Text(
                                            if (t == "RUC") "RUC (Factura)" else if (t == "DNI") "DNI (Boleta)" else "Boleta Sin Doc",
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                )
                            }
                        }
                        if (tipoDoc != "Sin Doc") {
                            val largoRequerido = if (tipoDoc == "DNI") 8 else 11
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = numDoc,
                                    onValueChange = {
                                        val filtrado = it.filter(Char::isDigit).take(largoRequerido)
                                        if (filtrado != numDoc) {
                                            if (s.clienteSeleccionado != null) {
                                                razonSocialCliente = ""
                                                direccionCliente = ""
                                            }
                                            numDoc = filtrado
                                            vm.limpiarCliente()
                                        }
                                    },
                                    label = { Text("Número de $tipoDoc ($largoRequerido dígitos)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = { vm.buscarCliente(tipoDoc, numDoc) },
                                    enabled = !s.busy && !s.buscandoCliente && numDoc.length == largoRequerido,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    if (s.buscandoCliente) {
                                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                    } else {
                                        Text("Validar")
                                    }
                                }
                            }
                            AnimatedVisibility(visible = s.clienteSeleccionado != null) {
                                s.clienteSeleccionado?.let { cliente ->
                                    val esRuc = cliente.tipoDocumento.equals("RUC", ignoreCase = true)
                                    val tieneAlertaSunat = esRuc && (
                                        (cliente.condicion.isNotBlank() && !cliente.condicion.equals("HABIDO", ignoreCase = true)) ||
                                        (cliente.estadoSunat.isNotBlank() && !cliente.estadoSunat.equals("ACTIVO", ignoreCase = true))
                                    )
                                    val cardBg = if (tieneAlertaSunat) c.ambarBg else c.verdeBg
                                    val cardTint = if (tieneAlertaSunat) (if (s.modoOscuro) AmbarDorado else AmbarProfundo) else c.verde
                                    val origenLabel = when (cliente.origenDatos.uppercase()) {
                                        "APISPERU" -> "APISPERU"
                                        "DECOLECTA" -> "DECOLECTA"
                                        "REGISTRO_LOCAL" -> "BD LOCAL"
                                        "DEMO_LOCAL" -> "DEMO"
                                        else -> if (esRuc) "SUNAT" else "RENIEC"
                                    }
                                    val subtituloEstado = when {
                                        esRuc && (cliente.estadoSunat.isNotBlank() || cliente.condicion.isNotBlank()) ->
                                            listOf(cliente.estadoSunat, cliente.condicion).filter { it.isNotBlank() }.joinToString(" · ")
                                        cliente.origenDatos.equals("DEMO_LOCAL", ignoreCase = true) ->
                                            "Datos ficticios de demostración"
                                        cliente.origenDatos.equals("REGISTRO_LOCAL", ignoreCase = true) ->
                                            "Cliente guardado en base local"
                                        else ->
                                            if (esRuc) "SUNAT verificado" else "RENIEC verificado"
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = cardBg,
                                        border = BorderStroke(1.dp, cardTint.copy(alpha = 0.35f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    if (tieneAlertaSunat) Icons.Default.Warning else Icons.Default.Verified,
                                                    null,
                                                    tint = cardTint,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    cliente.razonSocial,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = c.textoPrincipal,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                EstadoPill(
                                                    texto = origenLabel,
                                                    colorContenido = cardTint,
                                                    colorFondo = MaterialTheme.colorScheme.surface,
                                                    borde = cardTint.copy(alpha = 0.35f)
                                                )
                                            }
                                            Text(
                                                subtituloEstado,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = cardTint
                                            )
                                            if (!cliente.direccion.isNullOrBlank()) {
                                                Text(
                                                    cliente.direccion,
                                                    fontSize = 11.sp,
                                                    color = c.textoSecundario
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            AnimatedVisibility(visible = s.clienteSeleccionado == null && !s.clienteAviso.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = c.ambarBg,
                                    border = BorderStroke(1.dp, AmbarDorado.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Info, null, tint = if (s.modoOscuro) AmbarDorado else AmbarProfundo, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            s.clienteAviso.orEmpty(),
                                            fontSize = 11.sp,
                                            color = if (s.modoOscuro) AmbarDorado else AmbarProfundo
                                        )
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = razonSocialCliente,
                                onValueChange = { razonSocialCliente = it.take(140) },
                                label = { Text(if (tipoDoc == "RUC") "Razón Social *" else "Nombre completo (o autollenado)") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (tipoDoc == "RUC") {
                                OutlinedTextField(
                                    value = direccionCliente,
                                    onValueChange = { direccionCliente = it.take(180) },
                                    label = { Text("Dirección Fiscal (obligatoria para Factura) *") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // 4. Métodos de Pago
                        Text("Método de Pago:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = c.textoPrincipal)
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Efectivo", "Yape", "Plin", "Tarjeta", "Mixto").forEach { m ->
                                FilterChip(
                                    selected = metodoPago == m,
                                    onClick = { metodoPago = m },
                                    label = { Text(m, maxLines = 1, softWrap = false) }
                                )
                            }
                        }

                        if (metodoPago == "Efectivo") {
                            // Presets de billetes en efectivo
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("Exacto", "50", "100", "200").forEach { preset ->
                                    OutlinedButton(
                                        onClick = {
                                            efectivoRecibido = if (preset == "Exacto") {
                                                "${totalCentimos / 100}.${(totalCentimos % 100).toString().padStart(2, '0')}"
                                            } else preset
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    ) { Text(if (preset == "Exacto") "Exacto" else "S/ $preset", maxLines = 1, softWrap = false) }
                                }
                            }
                            OutlinedTextField(
                                value = efectivoRecibido,
                                onValueChange = { efectivoRecibido = it },
                                label = { Text("Efectivo Recibido (S/.)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            val recibidoD = efectivoRecibido.toDoubleOrNull() ?: 0.0
                            val vueltoD = (recibidoD - totalDouble).coerceAtLeast(0.0)
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = c.verdeBg,
                                border = BorderStroke(1.dp, c.verde.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Vuelto a Entregar:", fontWeight = FontWeight.Bold, color = c.verde)
                                    Text(dineroD(vueltoD), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge.tabular(), color = c.verde)
                                }
                            }
                        } else if (metodoPago in listOf("Yape", "Plin")) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.QrCode2, null, Modifier.size(64.dp), tint = c.acento)
                                    Text("Escanear QR Wendy's Food en Mostrador", fontWeight = FontWeight.Bold, color = c.acento)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = numOperacion,
                                    onValueChange = { numOperacion = it.filter(Char::isDigit).take(12) },
                                    label = { Text("N° de operación $metodoPago") },
                                    placeholder = { Text("ej. 684921") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = {
                                        numOperacion = (Clock.System.now().toEpochMilliseconds() % 90000000L + 10000000L).toString()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                ) { Text("Ref Única", fontSize = 11.sp, maxLines = 1, softWrap = false) }
                            }
                        } else if (metodoPago == "Tarjeta") {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, if (s.transaccionTarjeta != null) c.verde else MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.CreditCard, null, tint = c.acento)
                                        Text("Simulador POS Tarjeta Propio", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = c.textoPrincipal)
                                    }
                                    Row(
                                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                tarjetaNumero = "4532 0151 1283 0366"
                                                tarjetaTitular = "RANDY HUAMAN"
                                                vm.limpiarTransaccionTarjeta()
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) { Text("Visa Demo", fontSize = 11.sp, maxLines = 1, softWrap = false) }
                                        OutlinedButton(
                                            onClick = {
                                                tarjetaNumero = "5412 7512 3412 3456"
                                                tarjetaTitular = "EMANUEL JANAMPA"
                                                vm.limpiarTransaccionTarjeta()
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) { Text("MC Demo", fontSize = 11.sp, maxLines = 1, softWrap = false) }
                                    }

                                    OutlinedTextField(
                                        value = tarjetaNumero,
                                        onValueChange = { tarjetaNumero = it },
                                        label = { Text("Número de Tarjeta (16 dígitos / Luhn)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = tarjetaMes,
                                            onValueChange = { tarjetaMes = it.filter(Char::isDigit).take(2) },
                                            label = { Text("Mes (MM)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = tarjetaAnio,
                                            onValueChange = { tarjetaAnio = it.filter(Char::isDigit).take(4) },
                                            label = { Text("Año (AAAA)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = tarjetaCvv,
                                            onValueChange = { tarjetaCvv = it.filter(Char::isDigit).take(4) },
                                            label = { Text("CVV") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    OutlinedTextField(
                                        value = tarjetaTitular,
                                        onValueChange = { tarjetaTitular = it.uppercase() },
                                        label = { Text("Nombre del Titular") },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Button(
                                        onClick = {
                                            vm.simularPagoTarjeta(
                                                tarjetaNumero,
                                                tarjetaMes,
                                                tarjetaAnio,
                                                tarjetaCvv,
                                                tarjetaTitular,
                                                totalDouble
                                            )
                                        },
                                        enabled = !s.busy && tarjetaNumero.isNotBlank() && tarjetaCvv.length >= 3,
                                        colors = ButtonDefaults.buttonColors(containerColor = if (s.transaccionTarjeta != null) VerdeEsmeralda else MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(if (s.transaccionTarjeta != null) Icons.Default.CheckCircle else Icons.Default.Sensors, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (s.transaccionTarjeta != null) "✓ Tarjeta Autorizada en Simulador POS" else "Procesar en Simulador POS (${dinero(totalCentimos)})")
                                    }

                                    s.transaccionTarjeta?.let { trx ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = c.verdeBg,
                                            border = BorderStroke(1.dp, c.verde),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text("OPERACIÓN APROBADA", fontWeight = FontWeight.ExtraBold, color = c.verde, fontSize = 12.sp)
                                                    Text(trx.marcaFinal, fontWeight = FontWeight.Bold, color = c.verde, fontSize = 12.sp)
                                                }
                                                Text("Código Autorización: ${trx.codigoAutorizacion.orEmpty()}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.textoPrincipal)
                                                Text("Tarjeta: **** **** **** ${trx.ultimos4Final} · Ref: ${trx.numeroOperacionFinal}", fontSize = 11.sp, color = c.textoSecundario)
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (metodoPago == "Mixto") {
                            Chips(listOf("Yape", "Plin"), mixtoMetodo) { mixtoMetodo = it }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("Pago Dividido / Mixto", fontWeight = FontWeight.Bold, color = c.textoPrincipal, modifier = Modifier.weight(1f))
                                        Spacer(Modifier.width(8.dp))
                                        OutlinedButton(
                                            onClick = {
                                                val mitadC = totalCentimos / 2
                                                val restoC = totalCentimos - mitadC
                                                mixtoEfectivo = "${mitadC / 100}.${(mitadC % 100).toString().padStart(2, '0')}"
                                                mixtoDigital = "${restoC / 100}.${(restoC % 100).toString().padStart(2, '0')}"
                                                if (mixtoNumOp.isBlank()) {
                                                    mixtoNumOp = (Clock.System.now().toEpochMilliseconds() % 90000000L + 10000000L).toString()
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("Dividir 50% / 50%", fontSize = 11.sp, maxLines = 1, softWrap = false)
                                        }
                                    }

                                    OutlinedTextField(
                                        value = mixtoEfectivo,
                                        onValueChange = {
                                            mixtoEfectivo = it
                                            val d = it.toDoubleOrNull()
                                            if (d != null && d in 0.0..totalDouble) {
                                                val resto = ((totalDouble - d) * 100).toLong() / 100.0
                                                mixtoDigital = resto.toString()
                                            }
                                        },
                                        label = { Text("Parte 1: Efectivo (S/.)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    OutlinedTextField(
                                        value = mixtoDigital,
                                        onValueChange = { mixtoDigital = it },
                                        label = { Text("Parte 2: $mixtoMetodo (S/.)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    OutlinedTextField(
                                        value = mixtoNumOp,
                                        onValueChange = { mixtoNumOp = it },
                                        label = { Text("N° Operación Yape/Plin (6-8 dígitos)") },
                                        placeholder = { Text("ej. 839201") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    val efecD = mixtoEfectivo.toDoubleOrNull() ?: 0.0
                                    val digD = mixtoDigital.toDoubleOrNull() ?: 0.0
                                    val sumaPartes = efecD + digD
                                    val diferencia = totalDouble - sumaPartes
                                    val cuadreOk = abs(diferencia) < 0.05

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (cuadreOk) c.verdeBg else c.rojoBg,
                                        border = BorderStroke(1.dp, (if (cuadreOk) c.verde else RojoCoral).copy(alpha = 0.35f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            Modifier.padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                if (cuadreOk) "Cuadre Exacto: 100% Cubierto" else "Diferencia Pendiente:",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = if (cuadreOk) c.verde else RojoCoral
                                            )
                                            Text(
                                                if (cuadreOk) dinero(totalCentimos) else dineroD(diferencia),
                                                style = MaterialTheme.typography.titleSmall.tabular(),
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (cuadreOk) c.verde else RojoCoral
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // WhatsApp e-Ticket
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Checkbox(checked = enviarWhatsApp, onCheckedChange = { enviarWhatsApp = it })
                            Text("Enviar comprobante por WhatsApp", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (enviarWhatsApp) {
                            OutlinedTextField(
                                value = telefonoWhatsApp,
                                onValueChange = { telefonoWhatsApp = it.filter(Char::isDigit).take(9) },
                                label = { Text("Número de WhatsApp (ej. 987654321)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        val esFactura = tipoDoc == "RUC"
                        val docOk = when (tipoDoc) {
                            "RUC" -> numDoc.length == 11 && razonSocialCliente.isNotBlank() && direccionCliente.isNotBlank()
                            "DNI" -> numDoc.length == 8 && razonSocialCliente.isNotBlank()
                            else -> true
                        }
                        val pagoOk = when (metodoPago) {
                            "Efectivo" -> (efectivoRecibido.toDoubleOrNull() ?: 0.0) >= totalDouble
                            "Yape", "Plin" -> numOperacion.isNotBlank()
                            "Tarjeta" -> s.transaccionTarjeta?.aprobadaFinal == true
                            "Mixto" -> {
                                val ef = mixtoEfectivo.toDoubleOrNull() ?: 0.0
                                val di = mixtoDigital.toDoubleOrNull() ?: 0.0
                                abs((ef + di) - totalDouble) < 0.05 && (di <= 0.0 || mixtoNumOp.isNotBlank())
                            }
                            else -> true
                        }

                        // Botón de Cobro Final y Emisión SUNAT Beta en una sola transacción
                        Button(
                            onClick = {
                                val idPed = pedidoActual.idPedido
                                val idMesa = pedidoActual.idMesa
                                val metParam = when (metodoPago) {
                                    "Efectivo" -> "EFECTIVO"
                                    "Yape", "Plin" -> metodoPago.uppercase()
                                    "Tarjeta" -> "TARJETA"
                                    else -> "MIXTO"
                                }
                                val efecParam = efectivoRecibido.toDoubleOrNull() ?: totalDouble
                                val opParam = when (metodoPago) {
                                    "Tarjeta" -> s.transaccionTarjeta?.numeroOperacionFinal.orEmpty()
                                    "Yape", "Plin" -> numOperacion.trim()
                                    else -> ""
                                }
                                val ult4Param = if (metodoPago == "Tarjeta") s.transaccionTarjeta?.ultimos4Final.orEmpty() else ""
                                val partesMix = if (metodoPago == "Mixto") {
                                    val efecD = mixtoEfectivo.toDoubleOrNull() ?: 0.0
                                    val digD = mixtoDigital.toDoubleOrNull() ?: 0.0
                                    listOfNotNull(
                                        if (efecD > 0) Triple("EFECTIVO", efecD, null) else null,
                                        if (digD > 0) Triple(mixtoMetodo.uppercase(), digD, mixtoNumOp.trim()) else null
                                    )
                                } else emptyList()

                                vm.cobrarYEmitirComprobante(
                                    idPedido = idPed,
                                    metodoPago = metParam,
                                    recibidoEfectivo = efecParam,
                                    totalPedido = totalDouble,
                                    operacionDigital = opParam,
                                    ultimos4Digital = ult4Param,
                                    partesMixto = partesMix,
                                    idMesaOpt = idMesa,
                                    emitirSunat = true,
                                    tipoComprobante = if (esFactura) "FACTURA" else "BOLETA",
                                    tipoDoc = tipoDoc,
                                    numDoc = numDoc,
                                    razonSocial = razonSocialCliente,
                                    direccion = direccionCliente,
                                    telefonoWhatsapp = telefonoWhatsApp,
                                    enviarWhatsapp = enviarWhatsApp
                                )
                                efectivoRecibido = ""
                                numOperacion = ""
                                mixtoNumOp = ""
                            },
                            enabled = !s.busy && pagoOk && docOk,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Default.Receipt, null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (esFactura) "Cobrar y generar factura" else "Cobrar y generar boleta",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal de Cierre de Caja
    if (cerrarCajaModal) {
        AlertDialog(
            onDismissRequest = { cerrarCajaModal = false },
            shape = RoundedCornerShape(16.dp),
            title = { Text("Arqueo y Cierre de Turno", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Cuenta el efectivo en gaveta e ingresa el monto final:", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = efectivoArqueo,
                        onValueChange = { efectivoArqueo = it },
                        label = { Text("Efectivo Físico Contado (S/.)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = motivoArqueo,
                        onValueChange = { motivoArqueo = it },
                        label = { Text("Observaciones / Motivo de descuadre") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val real = efectivoArqueo.toDoubleOrNull() ?: 0.0
                        vm.cerrarCaja(real, motivoArqueo)
                        cerrarCajaModal = false
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RojoCoral)
                ) { Text("Confirmar Cierre") }
            },
            dismissButton = { TextButton(onClick = { cerrarCajaModal = false }) { Text("Cancelar") } }
        )
    }

    // Modal de Descuento (HU-17)
    if (descuentoModal) {
        AlertDialog(
            onDismissRequest = { descuentoModal = false },
            shape = RoundedCornerShape(16.dp),
            title = { Text("Aplicar Descuento a Pedido", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Selecciona porcentaje:", style = MaterialTheme.typography.bodySmall)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("5", "10", "15", "20", "100").forEach { p ->
                            FilterChip(
                                selected = porcentajeDesc == p,
                                onClick = { porcentajeDesc = p },
                                label = { Text(if (p == "100") "Cortesía" else "$p%", maxLines = 1, softWrap = false) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = motivoDesc,
                        onValueChange = { motivoDesc = it },
                        label = { Text("Motivo del Descuento") },
                        placeholder = { Text("Ej. Cliente frecuente / Promoción") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        s.pedido?.idPedido?.let { idPed ->
                            vm.aplicarDescuento(idPed, porcentajeDesc.toDoubleOrNull() ?: 10.0, motivoDesc)
                        }
                        descuentoModal = false
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("Aplicar") }
            },
            dismissButton = { TextButton(onClick = { descuentoModal = false }) { Text("Cancelar") } }
        )
    }

    if (anularModal) {
        AlertDialog(
            onDismissRequest = { anularModal = false },
            shape = RoundedCornerShape(16.dp),
            title = { Text("Anular Pedido (Administrador)", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Esta acción anulará la comanda y liberará la mesa si no quedan órdenes activas.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = motivoAnular,
                        onValueChange = { motivoAnular = it },
                        label = { Text("Motivo de anulación *") },
                        singleLine = false,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        s.pedido?.idPedido?.let { idPed ->
                            vm.anularPedido(idPed, motivoAnular)
                        }
                        motivoAnular = ""
                        anularModal = false
                    },
                    enabled = motivoAnular.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RojoCoral)
                ) { Text("Confirmar Anulación") }
            },
            dismissButton = { TextButton(onClick = { anularModal = false }) { Text("Cancelar") } }
        )
    }

    // Modal de Comprobante Electrónico SUNAT Beta emitido
    s.comprobanteEmitido?.let { comp ->
        val aceptado = comp.estadoFinal == "ACEPTADO"
        val rechazado = comp.estadoFinal == "RECHAZADO"
        val colorEstado = if (aceptado) c.verde else if (rechazado) RojoCoral else if (s.modoOscuro) AmbarDorado else AmbarProfundo
        var telReenvio by rememberSaveable(comp.idComprobante) { mutableStateOf(telefonoWhatsApp) }
        var selloVisible by remember(comp.idComprobante) { mutableStateOf(false) }
        LaunchedEffect(comp.idComprobante) { selloVisible = true }
        val escalaSello by animateFloatAsState(
            targetValue = if (selloVisible) 1f else 0.7f,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 360f),
            label = "selloSunat"
        )

        AlertDialog(
            onDismissRequest = { vm.cerrarComprobanteModal() },
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Comprobante Electrónico", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(comp.numeroFinal, style = MaterialTheme.typography.bodySmall.tabular(), color = c.textoSecundario, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    EstadoPill(
                        texto = if (aceptado) "ACEPTADO" else if (rechazado) "RECHAZADO" else "PENDIENTE",
                        colorContenido = colorEstado,
                        colorFondo = if (aceptado) c.verdeBg else if (rechazado) c.rojoBg else c.ambarBg,
                        icono = if (aceptado) Icons.Default.Verified else Icons.Default.Info,
                        modifier = Modifier.graphicsLayer { scaleX = escalaSello; scaleY = escalaSello }
                    )
                }
            },
            text = {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(comp.razonSocialEmisor ?: "Wendy’s Food", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = c.textoPrincipal)
                            Text("RUC: ${comp.rucEmisor.orEmpty()}", style = MaterialTheme.typography.labelSmall.tabular(), color = c.textoSecundario)
                            Text("Ambiente de pruebas SUNAT Beta", fontSize = 10.sp, color = c.textoSecundario)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Tipo:", fontSize = 12.sp, color = c.textoSecundario)
                                Spacer(Modifier.width(8.dp))
                                Text(comp.tipoComprobante, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.textoPrincipal)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Serie y Número:", fontSize = 12.sp, color = c.textoSecundario)
                                Spacer(Modifier.width(8.dp))
                                Text(comp.numeroFinal, style = MaterialTheme.typography.bodySmall.tabular(), fontWeight = FontWeight.Bold, color = c.textoPrincipal)
                            }
                            comp.receptorFinal?.let { rec ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                                    Text("Cliente:", fontSize = 12.sp, color = c.textoSecundario)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "${comp.numeroDocumentoReceptor.orEmpty()} $rec".trim(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = c.textoPrincipal,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Fecha de Emisión:", fontSize = 12.sp, color = c.textoSecundario)
                                Spacer(Modifier.width(8.dp))
                                Text(formatearHoraLima(comp.fechaEmision), style = MaterialTheme.typography.labelSmall.tabular(), color = c.textoPrincipal)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Estado SUNAT:", fontSize = 12.sp, color = c.textoSecundario)
                                Spacer(Modifier.width(8.dp))
                                Text(comp.estadoFinal.replace("_", " "), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (comp.estadoFinal == "ACEPTADO") c.verde else if (comp.estadoFinal == "RECHAZADO") RojoCoral else if (s.modoOscuro) AmbarDorado else AmbarProfundo)
                            }
                        }
                    }

                    // Representación del Código QR para Tiquetera
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(if (comp.estadoFinal == "ACEPTADO") Icons.Default.Verified else Icons.AutoMirrored.Filled.ReceiptLong, null, modifier = Modifier.size(44.dp), tint = c.acento)
                            Text(comp.descripcionFinal, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.textoPrincipal, textAlign = TextAlign.Center)
                            Text(if (comp.hashFinal.isBlank()) "Firma digital pendiente" else "Hash: ${comp.hashFinal.take(20)}…", fontSize = 10.sp, color = c.textoSecundario)
                        }
                    }

                    s.pendientesEmision.firstOrNull { it.idPago == comp.idPago }?.let { solicitud ->
                        OutlinedButton(onClick = { vm.recuperarComprobante(solicitud) }, enabled = !s.busy, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) { Text("Reintentar emisión SUNAT", maxLines = 1, softWrap = false) }
                    }

                    // Cuadro de Totales
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Op. Gravada:", fontSize = 12.sp, color = c.textoSecundario)
                                Text(dineroD(comp.totalGravado), style = MaterialTheme.typography.bodySmall.tabular(), fontWeight = FontWeight.SemiBold, color = c.textoPrincipal)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("I.G.V. (18%):", fontSize = 12.sp, color = c.textoSecundario)
                                Text(dineroD(comp.totalIgv), style = MaterialTheme.typography.bodySmall.tabular(), fontWeight = FontWeight.SemiBold, color = c.textoPrincipal)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("TOTAL A PAGAR:", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = c.textoPrincipal)
                                Text(dineroD(comp.totalImporte), style = MaterialTheme.typography.titleSmall.tabular(), fontWeight = FontWeight.ExtraBold, color = c.verde)
                            }
                        }
                    }

                    // Sección de envío real PDF 80mm por WhatsApp (Green-API)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Envío de PDF por WhatsApp", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = c.textoPrincipal)
                            s.whatsappResultado?.let { wa ->
                                val esOk = wa.estado == "ENVIADO"
                                val esPend = wa.estado == "PENDIENTE_CONFIGURACION"
                                val bg = when {
                                    esOk -> c.verdeBg
                                    esPend -> c.ambarBg
                                    else -> c.rojoBg
                                }
                                val fg = when {
                                    esOk -> c.verde
                                    esPend -> if (s.modoOscuro) AmbarDorado else AmbarProfundo
                                    else -> RojoCoral
                                }
                                Surface(shape = RoundedCornerShape(10.dp), color = bg, modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(
                                            when {
                                                esOk -> Icons.Default.MarkChatRead
                                                esPend -> Icons.Default.Info
                                                else -> Icons.Default.ErrorOutline
                                            },
                                            null,
                                            tint = fg,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            if (esOk) "PDF ${wa.archivoPdf ?: "${comp.numeroFinal}.pdf"} enviado a WhatsApp"
                                            else wa.detalle?.takeIf { it.isNotBlank() } ?: wa.mensaje,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = fg
                                        )
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = telReenvio,
                                    onValueChange = { telReenvio = it.filter { ch -> ch.isDigit() || ch == '+' }.take(15) },
                                    label = { Text("N° Celular", maxLines = 1, softWrap = false) },
                                    placeholder = { Text("987654321") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = { vm.ticketWhatsApp(comp.idPedido, telReenvio) },
                                    enabled = !s.busy && telReenvio.count(Char::isDigit) >= 9,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda, contentColor = BlancoPuro),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("PDF", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }

                    s.error?.let { err ->
                        Notice(text = err, error = true, onClose = vm::limpiarAvisos)
                    }
                    s.mensaje?.takeIf { it.contains("PDF", ignoreCase = true) }?.let { msg ->
                        Notice(text = msg, error = false, onClose = vm::limpiarAvisos)
                    }
                    // Acciones PDF 80mm reales
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { vm.abrirPdfComprobante(comp.idComprobante, comp.numeroFinal, compartir = false) },
                            enabled = !s.busy && comp.idComprobante > 0L,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Print, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Ver PDF", fontSize = 11.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = { vm.abrirPdfComprobante(comp.idComprobante, comp.numeroFinal, compartir = true) },
                            enabled = !s.busy && comp.idComprobante > 0L,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Compartir", fontSize = 11.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.limpiarTransaccionTarjeta()
                        vm.cerrarComprobanteModal()
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Finalizar Venta")
                }
            }
        )
    }
    s.pdfVistaPrevia?.let { pdf ->
        VisorPdfComprobante(
            nombreArchivo = pdf.nombre,
            bytes = pdf.bytes,
            onCerrar = vm::cerrarVistaPreviaPdf,
            onAbrirExterno = { vm.abrirPdfExterno(compartir = false) },
            onCompartir = { vm.abrirPdfExterno(compartir = true) }
        )
    }
}

// -------------------------------------------------------------
// 6. ALMACEN, INVENTARIO Y STOCK (HU-18 A HU-21)
// -------------------------------------------------------------

@Composable
private fun AlmacenScreen(s: PosUiState, vm: PosViewModel) {
    val c = LocalColores.current
    var buscar by rememberSaveable { mutableStateOf("") }
    var nuevoInsumoModal by remember { mutableStateOf(false) }
    var compraModal by remember { mutableStateOf(false) }
    var mermaModal by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(4.dp))
        Heading("Inventario & Almacén", "Materias primas, compras a proveedor y registro de mermas")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { nuevoInsumoModal = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Nuevo Insumo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = { compraModal = true },
                enabled = s.insumos.isNotEmpty() && !s.busy,
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.ShoppingBag, null, modifier = Modifier.size(16.dp), tint = c.verde)
                Spacer(Modifier.width(4.dp))
                Text("Registrar Compra", fontSize = 12.sp, color = c.verde, maxLines = 1, softWrap = false)
            }
            OutlinedButton(
                onClick = { mermaModal = true },
                enabled = s.insumos.isNotEmpty() && !s.busy,
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.TrendingDown, null, modifier = Modifier.size(16.dp), tint = RojoCoral)
                Spacer(Modifier.width(4.dp))
                Text("Registrar Merma", fontSize = 12.sp, color = RojoCoral, maxLines = 1, softWrap = false)
            }
        }

        // Ticker de Alertas de Stock Crítico
        val bajoStock = s.insumos.count { it.stockActual <= it.stockMinimo }
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (bajoStock > 0) c.rojoBg else c.verdeBg,
            border = BorderStroke(1.dp, if (bajoStock > 0) RojoCoral.copy(alpha = 0.35f) else c.verde.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (bajoStock > 0) Icons.Default.Warning else Icons.Default.CheckCircle,
                            null,
                            tint = if (bajoStock > 0) RojoCoral else c.verde,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column {
                    Text(
                        if (bajoStock > 0) "$bajoStock insumos en nivel crítico o bajo" else "Stock en niveles óptimos",
                        style = MaterialTheme.typography.titleSmall.tabular(),
                        fontWeight = FontWeight.Bold,
                        color = if (bajoStock > 0) RojoCoral else c.verde
                    )
                    Text("Total monitoreado: ${s.insumos.size} materias primas", style = MaterialTheme.typography.bodySmall.tabular(), color = c.textoSecundario)
                }
            }
        }

        OutlinedTextField(
            value = buscar,
            onValueChange = { buscar = it },
            label = { Text("Buscar insumo (ej. Pollo, Papa huayro, Aceite)") },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = c.acento) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )

        val insumosFiltrados = s.insumos.filter {
            it.nombre.contains(buscar, ignoreCase = true) || it.codigo.contains(buscar, ignoreCase = true)
        }

        if (insumosFiltrados.isEmpty()) {
            Empty("Sin insumos para mostrar", "No se encontraron insumos con ese nombre o código. Presiona '+ Nuevo Insumo' para registrar uno.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(insumosFiltrados, key = { it.idInsumo }) { ins ->
                    val esCritico = ins.stockActual <= ins.stockMinimo
                    val ratio = if (ins.stockMinimo > 0) (ins.stockActual / (ins.stockMinimo * 2.0)).coerceIn(0.0, 1.0).toFloat() else 1f

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (esCritico) RojoCoral.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant),
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(ins.nombre, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = c.textoPrincipal)
                                    Text("Cód: ${ins.codigo} · Costo Prom: S/ ${ins.costoUnitario}", style = MaterialTheme.typography.bodySmall.tabular(), color = c.textoSecundario)
                                }
                                Spacer(Modifier.width(8.dp))
                                EstadoPill(
                                    texto = "${ins.stockActual} ${ins.unidadMedida}",
                                    colorContenido = if (esCritico) RojoCoral else c.verde,
                                    colorFondo = if (esCritico) c.rojoBg else c.verdeBg,
                                    dotColor = if (esCritico) RojoCoral else c.verde
                                )
                            }

                            LinearProgressIndicator(
                                progress = { ratio },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = if (esCritico) RojoCoral else c.verde,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Mínimo: ${ins.stockMinimo} ${ins.unidadMedida}", style = MaterialTheme.typography.labelSmall.tabular(), color = c.textoSecundario)
                                Text(if (esCritico) "¡STOCK CRÍTICO!" else "OK", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (esCritico) RojoCoral else c.verde)
                            }
                        }
                    }
                }
            }
        }
    }

    if (nuevoInsumoModal) {
        NuevoInsumoDialog({ nuevoInsumoModal = false }) { cod, nom, uni, st, min, cost ->
            vm.crearInsumo(cod, nom, uni, st, min, cost)
            nuevoInsumoModal = false
        }
    }
    if (compraModal && s.insumos.isNotEmpty()) {
        CompraInsumoDialog(s.insumos, { compraModal = false }) { prov, doc, idIns, cant, costo, obs ->
            vm.registrarCompra(prov, doc, listOf(pe.edu.upeu.nullpointerpos.data.remote.dto.ItemCompraDto(idIns, cant, costo)), obs)
            compraModal = false
        }
    }
    if (mermaModal && s.insumos.isNotEmpty()) {
        MermaInsumoDialog(s.insumos, { mermaModal = false }) { idIns, cant, motivo, obs ->
            vm.registrarMerma(idIns, cant, motivo, obs)
            mermaModal = false
        }
    }
}

@Composable
private fun CompraInsumoDialog(
    insumos: List<Insumo>,
    cerrar: () -> Unit,
    guardar: (String, String, Long, Double, Double, String) -> Unit
) {
    var seleccionado by remember { mutableStateOf(insumos.first()) }
    var proveedor by remember { mutableStateOf("") }
    var doc by remember { mutableStateOf("") }
    var cantidad by remember { mutableStateOf("10.0") }
    var costo by remember { mutableStateOf(seleccionado.costoUnitario.toString()) }
    var obs by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = cerrar,
        shape = RoundedCornerShape(16.dp),
        title = { Text("Ingreso por Compra", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Selecciona insumo (${insumos.size}):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Column(Modifier.fillMaxWidth().heightIn(max = 150.dp).verticalScroll(rememberScrollState())) {
                    insumos.forEach { ins ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                seleccionado = ins
                                costo = ins.costoUnitario.toString()
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = seleccionado.idInsumo == ins.idInsumo, onClick = {
                                seleccionado = ins
                                costo = ins.costoUnitario.toString()
                            })
                            Text("${ins.nombre} (${ins.stockActual} ${ins.unidadMedida})", fontSize = 13.sp)
                        }
                    }
                }
                OutlinedTextField(proveedor, { proveedor = it }, label = { Text("Proveedor *") }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(doc, { doc = it }, label = { Text("N° Factura / Guía") }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(cantidad, { cantidad = it }, label = { Text("Cantidad (${seleccionado.unidadMedida}) *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(costo, { costo = it }, label = { Text("Costo Unitario S/. *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(obs, { obs = it }, label = { Text("Observación (opcional)") }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            val cantD = cantidad.toDoubleOrNull() ?: 0.0
            val costD = costo.toDoubleOrNull() ?: 0.0
            Button(
                onClick = { guardar(proveedor, doc, seleccionado.idInsumo, cantD, costD, obs) },
                enabled = proveedor.isNotBlank() && cantD > 0 && costD >= 0,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text("Registrar Ingreso") }
        },
        dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } }
    )
}

@Composable
private fun MermaInsumoDialog(
    insumos: List<Insumo>,
    cerrar: () -> Unit,
    guardar: (Long, Double, String, String) -> Unit
) {
    var seleccionado by remember { mutableStateOf(insumos.first()) }
    var cantidad by remember { mutableStateOf("1.0") }
    var motivo by remember { mutableStateOf("") }
    var obs by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = cerrar,
        shape = RoundedCornerShape(16.dp),
        title = { Text("Registro de Merma", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Selecciona insumo (${insumos.size}):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Column(Modifier.fillMaxWidth().heightIn(max = 150.dp).verticalScroll(rememberScrollState())) {
                    insumos.forEach { ins ->
                        Row(
                            Modifier.fillMaxWidth().clickable { seleccionado = ins },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = seleccionado.idInsumo == ins.idInsumo, onClick = { seleccionado = ins })
                            Text("${ins.nombre} (${ins.stockActual} ${ins.unidadMedida})", fontSize = 13.sp)
                        }
                    }
                }
                OutlinedTextField(cantidad, { cantidad = it }, label = { Text("Cantidad mermada *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(motivo, { motivo = it }, label = { Text("Motivo (ej. Vencido, Derrame) *") }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(obs, { obs = it }, label = { Text("Detalle adicional") }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            val cantD = cantidad.toDoubleOrNull() ?: 0.0
            Button(
                onClick = { guardar(seleccionado.idInsumo, cantD, motivo, obs) },
                enabled = motivo.isNotBlank() && cantD > 0,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RojoCoral)
            ) { Text("Descontar Stock") }
        },
        dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } }
    )
}

@Composable
private fun NuevoInsumoDialog(cerrar: () -> Unit, crear: (String, String, String, Double, Double, Double) -> Unit) {
    var codigo by remember { mutableStateOf("") }
    var nombre by remember { mutableStateOf("") }
    var unidad by remember { mutableStateOf("kg") }
    var stock by remember { mutableStateOf("50.0") }
    var minimo by remember { mutableStateOf("10.0") }
    var costo by remember { mutableStateOf("8.50") }

    AlertDialog(
        onDismissRequest = cerrar,
        shape = RoundedCornerShape(16.dp),
        title = { Text("Registrar Insumo de Cocina", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(codigo, { codigo = it }, label = { Text("Código (ej. INS-POL-01)") }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre del insumo") }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(unidad, { unidad = it }, label = { Text("Unidad de Medida (kg, lt, und)") }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(stock, { stock = it }, label = { Text("Stock Inicial") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(minimo, { minimo = it }, label = { Text("Stock Mínimo de Alerta") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(costo, { costo = it }, label = { Text("Costo Unitario (S/.)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    crear(codigo, nombre, unidad, stock.toDoubleOrNull() ?: 0.0, minimo.toDoubleOrNull() ?: 0.0, costo.toDoubleOrNull() ?: 0.0)
                },
                enabled = codigo.isNotBlank() && nombre.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text("Guardar Insumo") }
        },
        dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } }
    )
}

// -------------------------------------------------------------
// 7. REPORTES, AUDITORIA Y ANALITICA (HU-22 A HU-25)
// -------------------------------------------------------------

@Composable
private fun ReportesScreen(s: PosUiState, vm: PosViewModel) {
    val c = LocalColores.current
    var tabReporte by rememberSaveable { mutableStateOf("Horas") }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(4.dp))
        Heading("Analítica & Reportes de Venta", "Métricas de facturación, horas punta y platos populares")

        PrimaryTabRow(
            selectedTabIndex = if (tabReporte == "Horas") 0 else if (tabReporte == "Platos") 1 else 2,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = c.acento
        ) {
            Tab(selected = tabReporte == "Horas", onClick = { tabReporte = "Horas" }, text = { Text("Ventas x Hora") })
            Tab(selected = tabReporte == "Platos", onClick = { tabReporte = "Platos" }, text = { Text("Top Platos") })
            Tab(selected = tabReporte == "Auditoría", onClick = { tabReporte = "Auditoría" }, text = { Text("Auditoría") })
        }

        when (tabReporte) {
            "Horas" -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    // 1. Tarjeta visual "Curva de Flujo Operativo" (Bistro & Gourmet M3)
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shadowElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = c.acentoSuave,
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.AutoMirrored.Filled.ShowChart, null, tint = c.acento, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        Text("Curva de Flujo Operativo", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = c.textoPrincipal)
                                    }
                                    Text("Hora Perú (UTC-5)", style = MaterialTheme.typography.labelSmall, color = c.acento, fontWeight = FontWeight.Bold)
                                }

                                val ventasPorHoraMap = s.ventasPorHora.associateBy { it.hora }
                                val maxVentaCent = s.ventasPorHora.maxOfOrNull { it.totalVendidoCentimos }?.coerceAtLeast(1L) ?: 1L
                                val horaPico = s.ventasPorHora.maxByOrNull { it.totalVendidoCentimos }?.hora
                                val horasMostrar = (s.ventasPorHora.map { it.hora } + listOf(12, 13, 14, 18, 19, 20, 21)).distinct().sorted()

                                Row(
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    horasMostrar.forEach { hora ->
                                        val vh = ventasPorHoraMap[hora]
                                        val esPicoReal = horaPico != null && hora == horaPico && (vh?.totalVendidoCentimos ?: 0L) > 0L
                                        val alturaDpObjetiva = if (vh != null && vh.totalVendidoCentimos > 0L) {
                                            (14 + (54f * vh.totalVendidoCentimos / maxVentaCent).toInt()).dp
                                        } else 6.dp
                                        val alturaAnimada by animateDpAsState(
                                            targetValue = alturaDpObjetiva,
                                            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                                            label = "barHora$hora"
                                        )
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (esPicoReal) {
                                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(Icons.Default.Whatshot, null, tint = BlancoPuro, modifier = Modifier.size(13.dp))
                                                    }
                                                }
                                            } else if (vh != null) {
                                                Text("${vh.cantidadPedidos}", style = MaterialTheme.typography.labelSmall.tabular(), fontWeight = FontWeight.Bold, color = c.textoSecundario)
                                            } else {
                                                Spacer(Modifier.height(14.dp))
                                            }
                                            Box(
                                                Modifier
                                                    .width(18.dp)
                                                    .height(alturaAnimada)
                                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                                    .background(
                                                        when {
                                                            esPicoReal -> MaterialTheme.colorScheme.primary
                                                            vh != null -> if (s.modoOscuro) AmbarDorado else AmbarProfundo
                                                            else -> MaterialTheme.colorScheme.outlineVariant
                                                        }
                                                    )
                                            )
                                            Text(
                                                "${hora.toString().padStart(2, '0')}h",
                                                style = MaterialTheme.typography.labelSmall.tabular(),
                                                fontWeight = if (esPicoReal || vh != null) FontWeight.ExtraBold else FontWeight.Medium,
                                                color = if (esPicoReal) c.acento else c.textoSecundario
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                val totalDia = s.ventasPorHora.sumOf { it.totalVentas }
                                val pedidosDia = s.ventasPorHora.sumOf { it.cantidadPedidos }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Pedidos hoy: $pedidosDia", style = MaterialTheme.typography.labelSmall.tabular(), fontWeight = FontWeight.Bold, color = c.textoSecundario)
                                    Text("Facturado hoy: ${dineroD(totalDia)}", style = MaterialTheme.typography.labelSmall.tabular(), fontWeight = FontWeight.ExtraBold, color = c.verde)
                                }
                            }
                        }
                    }

                    // 2. Detalle de ventas por hora
                    if (s.ventasPorHora.isEmpty()) {
                        item {
                            Empty("Sin transacciones registradas", "A medida que se cobren comandas en caja se acumularán las métricas por cada hora.")
                        }
                    } else {
                        items(s.ventasPorHora) { vh ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.Schedule, null, tint = c.acento, modifier = Modifier.size(20.dp))
                                        Text("${vh.hora.toString().padStart(2, '0')}:00 hrs", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge.tabular(), color = c.textoPrincipal)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(dineroD(vh.totalVentas), style = MaterialTheme.typography.titleSmall.tabular(), fontWeight = FontWeight.ExtraBold, color = c.acento)
                                        Text("${vh.cantidadPedidos} pedidos", style = MaterialTheme.typography.labelSmall.tabular(), color = c.textoSecundario)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "Platos" -> {
                if (s.platosPopulares.isEmpty()) {
                    Empty("Sin platos populares", "Los platos más vendidos aparecerán aquí tras registrar ventas.")
                } else {
                    val maxCantidad = s.platosPopulares.maxOfOrNull { it.cantidadVendida }?.coerceAtLeast(1) ?: 1
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                        itemsIndexed(s.platosPopulares) { index, pp ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("#${index + 1} ${pp.nombrePlato}", style = MaterialTheme.typography.titleSmall.tabular(), fontWeight = FontWeight.Bold, color = c.textoPrincipal, modifier = Modifier.weight(1f))
                                        EstadoPill(
                                            texto = "${pp.cantidadVendida} unid.",
                                            colorContenido = c.acento,
                                            colorFondo = c.acentoSuave,
                                            borde = c.acento.copy(alpha = 0.25f)
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { (pp.cantidadVendida.toFloat() / maxCantidad).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                        color = c.acento,
                                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                    Text("Facturado: ${dineroD(pp.totalRecaudado)}", style = MaterialTheme.typography.labelSmall.tabular(), color = c.textoSecundario)
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                // Auditoría de Descuentos y Anulaciones (HU-25)
                if (s.auditoria.isEmpty()) {
                    Empty("Sin registros de auditoría", "Las anulaciones y descuentos aplicados se registran de forma inmutable.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(s.auditoria) { aud ->
                            val actorResuelto = s.usuarios.find { it.idUsuario.toString() == aud.usuario }
                                ?.let { "${it.codigoTrabajador} · ${it.nombreCompleto}" } ?: aud.usuario
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(aud.accion, fontWeight = FontWeight.Bold, color = c.acento)
                                        Text(formatearHoraLima(aud.fecha), style = MaterialTheme.typography.labelSmall.tabular(), color = c.textoSecundario)
                                    }
                                    Text("${aud.codigoPedido} · Por: $actorResuelto", style = MaterialTheme.typography.bodySmall, color = c.textoPrincipal)
                                    if (aud.motivo.isNotBlank()) {
                                        Text("Motivo: ${aud.motivo}", style = MaterialTheme.typography.bodyMedium, color = c.textoSecundario)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 8. ADMINISTRACION DE USUARIOS Y ROLES (HU-26)
// -------------------------------------------------------------

@Composable
private fun Usuarios(s: PosUiState, vm: PosViewModel) {
    val c = LocalColores.current
    var crear by remember { mutableStateOf(false) }
    var confirmar by remember { mutableStateOf<Cuenta?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Heading("Personal del Restaurante", "Gestión de mozos, cajeros, cocineros y almaceneros", Modifier.weight(1f))
            Button(
                onClick = { crear = true },
                enabled = !s.busy,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Nuevo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(s.usuarios, key = { it.idUsuario }) { u ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(u.nombreCompleto, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.textoPrincipal)
                                Text("${u.codigoTrabajador} · ${u.rol}", style = MaterialTheme.typography.bodySmall, color = c.textoSecundario)
                            }
                            EstadoPill(
                                texto = if (u.activo) "ACTIVO" else "INACTIVO",
                                colorContenido = if (u.activo) c.verde else RojoCoral,
                                colorFondo = if (u.activo) c.verdeBg else c.rojoBg,
                                dotColor = if (u.activo) c.verde else RojoCoral
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (u.activo) "Cuenta habilitada para turno" else "Acceso revocado",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textoSecundario
                            )
                            TextButton(
                                onClick = { confirmar = u },
                                enabled = !s.busy && u.idUsuario != s.sesion?.usuario?.idUsuario
                            ) {
                                Text(if (u.activo) "Desactivar" else "Activar", color = if (u.activo) RojoCoral else c.verde, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    confirmar?.let { u ->
        Confirmar(
            if (u.activo) "Desactivar usuario" else "Activar usuario",
            "¿Deseas cambiar el estado de acceso de '${u.nombreCompleto}'?",
            { confirmar = null }
        ) {
            confirmar = null
            vm.activar(u)
        }
    }

    if (crear) {
        UsuarioDialog({ crear = false }) { cod, nom, cor, pin, rol ->
            crear = false
            vm.crearUsuario(cod, nom, cor, pin, rol)
        }
    }
}

@Composable
private fun UsuarioDialog(cerrar: () -> Unit, crear: (String, String, String, String, String) -> Unit) {
    var codigo by remember { mutableStateOf("") }
    var nombre by remember { mutableStateOf("") }
    var correo by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var rol by remember { mutableStateOf("MOZO") }

    AlertDialog(
        onDismissRequest = cerrar,
        shape = RoundedCornerShape(16.dp),
        title = { Text("Registrar Colaborador", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(codigo, { codigo = it.uppercase().take(30) }, label = { Text("Código (ej. MOZ003, CAJ002)") }, singleLine = true, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(nombre, { nombre = it.take(120) }, label = { Text("Nombre Completo") }, singleLine = true, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(correo, { correo = it.take(160) }, label = { Text("Correo (opcional)") }, singleLine = true, shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(4) }, label = { Text("PIN de 4 dígitos") }, singleLine = true, shape = RoundedCornerShape(10.dp), visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
                Text("Rol en el restaurante:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                listOf("MOZO", "COCINERO", "CAJERO", "ALMACENERO", "ADMINISTRADOR").forEach { r ->
                    Row(Modifier.fillMaxWidth().clickable { rol = r }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = rol == r, onClick = { rol = r })
                        Text(r, fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { crear(codigo, nombre, correo, pin, rol) },
                enabled = codigo.isNotBlank() && nombre.isNotBlank() && pin.length == 4,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text("Crear Usuario") }
        },
        dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } }
    )
}

// -------------------------------------------------------------
// 9. CUENTA DEL USUARIO Y PERFIL
// -------------------------------------------------------------

@Composable
private fun Cuenta(s: PosUiState, vm: PosViewModel) {
    val c = LocalColores.current
    var salir by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Heading("Mi Perfil", s.sesion?.usuario?.nombreCompleto.orEmpty())

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(shape = CircleShape, color = Color.Transparent, shadowElevation = 3.dp, modifier = Modifier.size(56.dp)) {
                        WendysLogo(Modifier.fillMaxSize())
                    }
                    Column {
                        Text(s.sesion?.usuario?.nombreCompleto.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Cód: ${s.sesion?.usuario?.codigoTrabajador} · Rol: ${s.rol}", style = MaterialTheme.typography.bodySmall, color = c.textoSecundario)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Ajuste rápido de Modo Oscuro en perfil
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().clickable { vm.toggleModoOscuro() }
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = c.acentoSuave,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (s.modoOscuro) Icons.Default.DarkMode else Icons.Default.LightMode,
                                    contentDescription = null,
                                    tint = c.acento,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Tema Visual", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text(if (s.modoOscuro) "Modo Oscuro (KDS & Noche)" else "Modo Claro Bistro", style = MaterialTheme.typography.labelSmall, color = c.textoSecundario)
                        }
                        Switch(
                            checked = s.modoOscuro,
                            onCheckedChange = { vm.toggleModoOscuro() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AmbarDorado,
                                checkedTrackColor = Borgona,
                                uncheckedThumbColor = BlancoPuro,
                                uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }
                }

                Text("Servidor Gateway: ${s.servidor.ifBlank { "http://10.40.79.37:8080" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                Text("Sistema NullPointer POS con arquitectura limpia para Sprints 1, 2 y 3.", style = MaterialTheme.typography.bodySmall, color = c.textoSecundario)
            }
        }

        OutlinedButton(
            onClick = { salir = true },
            enabled = !s.busy,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, RojoCoral.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = RojoCoral)
            Spacer(Modifier.width(8.dp))
            Text("Cerrar Sesión del Dispositivo", color = RojoCoral, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(6.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Créditos & Autoría", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, color = c.acento)
                Text("Randy Huamán (Célula G5 - UPeU)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                Text("NullPointer POS · Wendy's Food", style = MaterialTheme.typography.bodySmall, color = c.textoSecundario)
            }
        }
    }

    if (salir) {
        Confirmar(
            "Cerrar Sesión",
            "¿Deseas cerrar tu sesión? Los pedidos en borrador se mantendrán guardados localmente.",
            { salir = false }
        ) {
            salir = false
            vm.cerrarSesion()
        }
    }
}
