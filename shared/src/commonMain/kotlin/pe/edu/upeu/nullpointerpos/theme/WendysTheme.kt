// Autor: Randy Huaman (Sistema de Diseno Bistro & Gourmet Contemporaneo - Modo Claro & Oscuro)

package pe.edu.upeu.nullpointerpos.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Paleta de Colores Gastronomica "Bistro & Gourmet"
val Borgona = Color(0xFF82171E)
val BorgonaOscuro = Color(0xFF60000C)
val BorgonaVibrante = Color(0xFFA3242C)
val AmbarDorado = Color(0xFFF59E0B)
val AmbarProfundo = Color(0xFFD97706)
val AmbarSuave = Color(0xFFFEA619)

val CremaFondo = Color(0xFFFAF8F5)
val BlancoPuro = Color(0xFFFFFFFF)
val SuperficieContenedor = Color(0xFFFBEAE7)
val SuperficieBorde = Color(0xFFE7E1D8)
val TintaTexto = Color(0xFF2B2220)
val TintaSecundaria = Color(0xFF584140)

// Modo Oscuro (KDS & Chef Night Mode)
val FondoOscuro = Color(0xFF121214)
val SuperficieOscura = Color(0xFF1C1C21)
val ContenedorOscuro = Color(0xFF26262E)
val BordeOscuro = Color(0xFF353540)
val TextoOscuroPrincipal = Color(0xFFF4F4F6)
val TextoOscuroSecundario = Color(0xFFA1A1AA)

// Semantica Operativa POS y KDS
val VerdeEsmeralda = Color(0xFF10B981)
val VerdeEsmeraldaBg = Color(0xFFD1FAE5)
val VerdeEsmeraldaDarkBg = Color(0xFF064E3B)

val RojoCoral = Color(0xFFEF4444)
val RojoCoralBg = Color(0xFFFEE2E2)
val RojoCoralDarkBg = Color(0xFF7F1D1D)

val AmbarAlerta = Color(0xFFF59E0B)
val AmbarAlertaBg = Color(0xFFFEF3C7)
val AmbarAlertaDarkBg = Color(0xFF78350F)

val AzulIndigo = Color(0xFF6366F1)
val AzulIndigoBg = Color(0xFFEEF2FF)
val AzulIndigoDarkBg = Color(0xFF312E81)

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
    val azulBg: Color
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
        azulBg = AzulIndigoBg
    )
}

val WendyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun WendysTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = BorgonaVibrante,
            onPrimary = BlancoPuro,
            primaryContainer = Borgona,
            onPrimaryContainer = BlancoPuro,
            secondary = AmbarDorado,
            onSecondary = Color(0xFF18181B),
            secondaryContainer = AmbarProfundo,
            onSecondaryContainer = BlancoPuro,
            tertiary = AzulIndigo,
            onTertiary = BlancoPuro,
            background = FondoOscuro,
            onBackground = TextoOscuroPrincipal,
            surface = SuperficieOscura,
            onSurface = TextoOscuroPrincipal,
            surfaceVariant = ContenedorOscuro,
            onSurfaceVariant = TextoOscuroSecundario,
            outline = BordeOscuro,
            outlineVariant = Color(0xFF4A4A55),
            error = RojoCoral,
            onError = BlancoPuro,
            errorContainer = RojoCoralDarkBg,
            onErrorContainer = Color(0xFFFECACA)
        )
    } else {
        lightColorScheme(
            primary = Borgona,
            onPrimary = BlancoPuro,
            primaryContainer = BorgonaVibrante,
            onPrimaryContainer = BlancoPuro,
            secondary = AmbarProfundo,
            onSecondary = BlancoPuro,
            secondaryContainer = AmbarSuave,
            onSecondaryContainer = Color(0xFF452B00),
            tertiary = AzulIndigo,
            onTertiary = BlancoPuro,
            background = CremaFondo,
            onBackground = TintaTexto,
            surface = BlancoPuro,
            onSurface = TintaTexto,
            surfaceVariant = SuperficieContenedor,
            onSurfaceVariant = TintaSecundaria,
            outline = SuperficieBorde,
            outlineVariant = Color(0xFFDFBFBD),
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
            azulBg = AzulIndigoDarkBg
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
            azulBg = AzulIndigoBg
        )
    }

    CompositionLocalProvider(LocalColores provides coloresGourmet) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = WendyShapes,
            content = content
        )
    }
}
