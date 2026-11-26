// Autor: Randy Huaman (Sistema de Diseno Bistro & Gourmet Contemporaneo - Modo Claro & Oscuro)

package pe.edu.upeu.nullpointerpos.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Paleta de Colores Gastronomica "Bistro & Gourmet"
val Borgona = Color(0xFF82171E)
val BorgonaOscuro = Color(0xFF60000C)
val BorgonaVibrante = Color(0xFFA3242C)
val AmbarDorado = Color(0xFFF59E0B)
val AmbarProfundo = Color(0xFFD97706)
val AmbarSuave = Color(0xFFFEA619)

val CremaFondo = Color(0xFFF7F4EE)
val BlancoPuro = Color(0xFFFFFFFF)
val SuperficieContenedor = Color(0xFFF6ECE8)
val SuperficieBorde = Color(0xFFE5DDD2)
val TintaTexto = Color(0xFF231B19)
val TintaSecundaria = Color(0xFF624E4B)

// Modo Oscuro (KDS & Chef Night Mode — capas slate-espresso profundas, sin negros planos)
val FondoOscuro = Color(0xFF0F1014)
val SuperficieOscura = Color(0xFF181A21)
val ContenedorOscuro = Color(0xFF222530)
val BordeOscuro = Color(0xFF323646)
val TextoOscuroPrincipal = Color(0xFFF4F4F7)
val TextoOscuroSecundario = Color(0xFFA5AAB8)

// Semantica Operativa POS y KDS
val VerdeEsmeralda = Color(0xFF2E7D4F)
val VerdeEsmeraldaOscuro = Color(0xFF5BB974)
val VerdeEsmeraldaBg = Color(0xFFE7F3EB)
val VerdeEsmeraldaDarkBg = Color(0xFF172E20)

val RojoCoral = Color(0xFFDC3E3E)
val RojoCoralBg = Color(0xFFFEE2E2)
val RojoCoralDarkBg = Color(0xFF4C171B)

val AmbarAlerta = Color(0xFFF59E0B)
val AmbarAlertaBg = Color(0xFFFEF3C7)
val AmbarAlertaDarkBg = Color(0xFF4A2A0A)

val AzulListo = Color(0xFF1D63D8)
val AzulListoOscuro = Color(0xFF60A5FA)
val AzulIndigo = AzulListo
val AzulIndigoBg = Color(0xFFE8F1FE)
val AzulIndigoDarkBg = Color(0xFF162947)

// Alias para compatibilidad
val Vino = Borgona
val Ambar = AmbarDorado
val Tinta = TintaTexto
val Libre = VerdeEsmeralda
val Ocupada = RojoCoral

data class ColoresGastronomicos(
    val fondo: Color,
    val superficie: Color,
    val contenedor: Color,
    val borde: Color,
    val textoPrincipal: Color,
    val textoSecundario: Color,
    val verdeBg: Color,
    val rojoBg: Color,
    val ambarBg: Color,
    val azulBg: Color,
    val acento: Color = Borgona,
    val acentoSuave: Color = Borgona.copy(alpha = 0.10f),
    val superficieElevada: Color = BlancoPuro,
    val esOscuro: Boolean = false,
    val verde: Color = VerdeEsmeralda,
    val azul: Color = AzulListo
)

val LocalColores = staticCompositionLocalOf {
    ColoresGastronomicos(
        fondo = CremaFondo,
        superficie = BlancoPuro,
        contenedor = SuperficieContenedor,
        borde = SuperficieBorde,
        textoPrincipal = TintaTexto,
        textoSecundario = TintaSecundaria,
        verdeBg = VerdeEsmeraldaBg,
        rojoBg = RojoCoralBg,
        ambarBg = AmbarAlertaBg,
        azulBg = AzulIndigoBg,
        acento = Borgona,
        acentoSuave = Borgona.copy(alpha = 0.10f),
        superficieElevada = Color(0xFFFAF7F2),
        esOscuro = false,
        verde = VerdeEsmeralda,
        azul = AzulListo
    )
}

val WendyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp)
)

fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

val WendysTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.4).sp
    ),
    headlineSmall = TextStyle(
        fontSize = 21.sp,
        lineHeight = 27.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.25).sp
    ),
    titleLarge = TextStyle(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.15).sp
    ),
    titleMedium = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Bold
    ),
    titleSmall = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Normal
    ),
    labelLarge = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.2.sp
    )
)

@Composable
fun WendysTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = BorgonaVibrante,
            onPrimary = BlancoPuro,
            primaryContainer = Color(0xFF4F1218),
            onPrimaryContainer = Color(0xFFFFD9D9),
            secondary = AmbarDorado,
            onSecondary = Color(0xFF1A1205),
            secondaryContainer = Color(0xFF47300A),
            onSecondaryContainer = Color(0xFFFDE68A),
            tertiary = AzulIndigo,
            onTertiary = BlancoPuro,
            background = FondoOscuro,
            onBackground = TextoOscuroPrincipal,
            surface = SuperficieOscura,
            onSurface = TextoOscuroPrincipal,
            surfaceVariant = ContenedorOscuro,
            onSurfaceVariant = TextoOscuroSecundario,
            surfaceContainerLowest = Color(0xFF0A0B0E),
            surfaceContainerLow = Color(0xFF14161D),
            surfaceContainer = Color(0xFF1D1F28),
            surfaceContainerHigh = Color(0xFF252834),
            surfaceContainerHighest = Color(0xFF2F3342),
            outline = Color(0xFF474C61),
            outlineVariant = BordeOscuro,
            error = RojoCoral,
            onError = BlancoPuro,
            errorContainer = RojoCoralDarkBg,
            onErrorContainer = Color(0xFFFECACA)
        )
    } else {
        lightColorScheme(
            primary = Borgona,
            onPrimary = BlancoPuro,
            primaryContainer = SuperficieContenedor,
            onPrimaryContainer = BorgonaOscuro,
            secondary = AmbarProfundo,
            onSecondary = BlancoPuro,
            secondaryContainer = Color(0xFFFEF0D6),
            onSecondaryContainer = Color(0xFF452B00),
            tertiary = AzulIndigo,
            onTertiary = BlancoPuro,
            background = CremaFondo,
            onBackground = TintaTexto,
            surface = BlancoPuro,
            onSurface = TintaTexto,
            surfaceVariant = SuperficieContenedor,
            onSurfaceVariant = TintaSecundaria,
            surfaceContainerLowest = BlancoPuro,
            surfaceContainerLow = Color(0xFFFAF7F2),
            surfaceContainer = Color(0xFFF3EEE7),
            surfaceContainerHigh = Color(0xFFECE5DC),
            surfaceContainerHighest = Color(0xFFE5DDD2),
            outline = Color(0xFFCBBFB2),
            outlineVariant = SuperficieBorde,
            error = RojoCoral,
            onError = BlancoPuro,
            errorContainer = RojoCoralBg,
            onErrorContainer = Color(0xFF7F1D1D)
        )
    }

    val coloresGourmet = if (darkTheme) {
        ColoresGastronomicos(
            fondo = FondoOscuro,
            superficie = SuperficieOscura,
            contenedor = ContenedorOscuro,
            borde = BordeOscuro,
            textoPrincipal = TextoOscuroPrincipal,
            textoSecundario = TextoOscuroSecundario,
            verdeBg = VerdeEsmeraldaDarkBg,
            rojoBg = RojoCoralDarkBg,
            ambarBg = AmbarAlertaDarkBg,
            azulBg = AzulIndigoDarkBg,
            acento = AmbarDorado,
            acentoSuave = AmbarDorado.copy(alpha = 0.16f),
            superficieElevada = Color(0xFF252834),
            esOscuro = true,
            verde = VerdeEsmeraldaOscuro,
            azul = AzulListoOscuro
        )
    } else {
        ColoresGastronomicos(
            fondo = CremaFondo,
            superficie = BlancoPuro,
            contenedor = SuperficieContenedor,
            borde = SuperficieBorde,
            textoPrincipal = TintaTexto,
            textoSecundario = TintaSecundaria,
            verdeBg = VerdeEsmeraldaBg,
            rojoBg = RojoCoralBg,
            ambarBg = AmbarAlertaBg,
            azulBg = AzulIndigoBg,
            acento = Borgona,
            acentoSuave = Borgona.copy(alpha = 0.10f),
            superficieElevada = Color(0xFFFAF7F2),
            esOscuro = false,
            verde = VerdeEsmeralda,
            azul = AzulListo
        )
    }

    CompositionLocalProvider(LocalColores provides coloresGourmet) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = WendysTypography,
            shapes = WendyShapes,
            content = content
        )
    }
}
