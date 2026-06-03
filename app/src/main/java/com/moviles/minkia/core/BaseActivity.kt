package com.moviles.minkia.core

import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.view.WindowInsetsControllerCompat
import androidx.viewbinding.ViewBinding

/**
 * Activity base con ViewBinding y utilidades de ventana. Cada Activity concreta
 * solo provee cómo inflar su binding y qué hacer en [onViewReady]; el inflado,
 * el setContentView y los helpers de edge-to-edge (insets y contraste de la
 * barra de estado) quedan resueltos acá una sola vez, sin repetirlos pantalla
 * por pantalla.
 */
abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {

    protected lateinit var binding: VB
        private set

    /** Cómo inflar el binding concreto, p. ej. ActivityLoginBinding::inflate. */
    protected abstract fun inflateBinding(inflater: LayoutInflater): VB

    /** Punto de entrada de la vista: corre tras inflar el binding y setContentView. */
    protected abstract fun onViewReady(savedInstanceState: Bundle?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge para TODA la app, una sola vez (antes de inflar): el contenido
        // se dibuja DETRÁS de las barras del sistema y ninguna pantalla queda con la
        // franja del color por defecto cortando el fondo. Cada Activity solo decide
        // el color de los iconos de las barras según su fondo, y esquiva las barras
        // con los helpers de inset donde su contenido lo necesite.
        enableEdgeToEdge()
        binding = inflateBinding(layoutInflater)
        setContentView(binding.root)
        forzarBarrasTransparentes()
        onViewReady(savedInstanceState)
    }

    /**
     * Tocar FUERA del campo de texto enfocado baja el teclado y suelta el foco, como
     * en cualquier app pulida. Resuelto acá una vez para TODA la app: si el toque cae
     * fuera del [EditText] activo, oculta el IME y deja seguir el evento normal (para
     * que botones y otros campos respondan igual).
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val foco = currentFocus
            if (foco is EditText) {
                val recorte = Rect()
                foco.getGlobalVisibleRect(recorte)
                if (!recorte.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    foco.clearFocus()
                    WindowInsetsControllerCompat(window, foco)
                        .hide(WindowInsetsCompat.Type.ime())
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    /**
     * Fuerza las barras del sistema a transparente y desactiva el "scrim" de
     * contraste que Android 10+ les agrega. `enableEdgeToEdge()` solo NO basta en
     * algunos fabricantes (Samsung One UI): sin esto la barra de navegación
     * transparente se ve como una franja blanca sólida que corta el fondo. El
     * emulador Pixel lo disimula, el dispositivo real no. Corre tras setContentView.
     */
    private fun forzarBarrasTransparentes() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
    }

    /**
     * Iconos OSCUROS en ambas barras (estado y navegación), para pantallas de fondo
     * claro (si no, quedan blancos sobre crema y no se leen).
     */
    protected fun barraEstadoOscura() {
        WindowCompat.getInsetsController(window, binding.root).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    /**
     * Para pantallas con cabecera verde arriba y fondo claro abajo (Registro,
     * Recuperar): iconos CLAROS en la barra de estado (van sobre el verde) y OSCUROS
     * en la de navegación (va sobre el fondo claro).
     */
    protected fun barrasCabeceraVerde() {
        WindowCompat.getInsetsController(window, binding.root).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = true
        }
    }

    /**
     * Iconos CLAROS en AMBAS barras, para pantallas de fondo OSCURO de punta a punta
     * (preview de cámara negro, análisis verde profundo).
     */
    protected fun barrasClaras() {
        WindowCompat.getInsetsController(window, binding.root).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    /**
     * Padea [view] (típicamente una toolbar) con el inset superior real del
     * sistema, para que con edge-to-edge no quede bajo el notch / barra de estado.
     */
    protected fun aplicarInsetSuperior(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = barras.top)
            insets
        }
    }

    /**
     * Padea [view] (un contenedor raíz) con los insets superior e inferior, para
     * que el contenido no quede bajo la barra de estado ni la barra de gestos.
     */
    protected fun aplicarInsetsVerticales(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = barras.top, bottom = barras.bottom)
            insets
        }
    }

    /**
     * Padea SOLO el inset inferior (barra de gestos) sobre el paddingBottom original.
     * Para pantallas cuya cabecera maneja aparte el inset superior (un toolbar con
     * [aplicarInsetSuperior]) pero cuyo contenido inferior sí debe esquivar la barra.
     */
    protected fun aplicarInsetInferior(view: View) {
        val padBottomInicial = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = padBottomInicial + barras.bottom)
            insets
        }
    }
}
