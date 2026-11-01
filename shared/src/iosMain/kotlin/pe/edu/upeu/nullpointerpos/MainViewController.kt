// Autor: Randy Huaman
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package pe.edu.upeu.nullpointerpos
import androidx.compose.ui.window.ComposeUIViewController
import pe.edu.upeu.nullpointerpos.di.initApp
fun MainViewController(): platform.UIKit.UIViewController {
    initApp()
    return ComposeUIViewController { App() }
}
