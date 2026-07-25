package com.moviles.minkia.core

import android.app.Activity
import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Suma el inset superior (barra de estado) al paddingTop ORIGINAL de la vista.
 *
 * Pensado para headers de color que se dibujan DETRÁS de la status bar (edge-to-
 * edge): el fondo de color sube hasta el borde de la pantalla, pero el contenido
 * baja para no chocar con la hora y la batería. Guarda el padding inicial del XML
 * para no acumular si el inset se recalcula (rotación, cambio de modo de barras).
 *
 * Requiere que ningún ancestro consuma el inset superior (MainActivity ya no lo
 * padea: lo deja fluir a los fragments).
 */
fun View.aplicarInsetSuperior() {
    val padTopInicial = paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
        v.updatePadding(top = padTopInicial + top)
        insets
    }
    // Los fragments se agregan después del primer reparto de insets: pedimos uno
    // nuevo en cuanto la vista esté attachada.
    if (isAttachedToWindow) {
        requestApplyInsets()
    } else {
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                v.requestApplyInsets()
            }

            override fun onViewDetachedFromWindow(v: View) {}
        })
    }
}

/**
 * Redondea SOLO las esquinas inferiores de la vista (clipToOutline). Pensado para
 * los headers andinos: la textura es un ImageView que llena el header, así que sin
 * recorte desbordaría la silueta redondeada del fondo. El truco del top negativo
 * deja las esquinas superiores rectas (quedan fuera del rectángulo recortado).
 */
fun View.recortarEsquinasInferiores(radioDp: Float = 24f) {
    val radio = radioDp * resources.displayMetrics.density
    clipToOutline = true
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, (-radio).toInt(), view.width, view.height, radio)
        }
    }
}

/**
 * Suma los insets superior e inferior (barra de estado + barra de gestos) al
 * padding ORIGINAL de la vista. Equivalente fragment-friendly de
 * BaseActivity.aplicarInsetsVerticales (esa vive en la Activity y recibe la
 * vista por parámetro). Se necesita como extensión de [View] porque, desde el
 * refactor a Navigation Component (AuthActivity), varias pantallas comparten
 * UNA sola Activity/window: cada fragment debe resolver sus propios insets al
 * entrar, sin depender de un método protegido de la Activity.
 */
fun View.aplicarInsetsVerticales() {
    val padTopInicial = paddingTop
    val padBottomInicial = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.updatePadding(top = padTopInicial + barras.top, bottom = padBottomInicial + barras.bottom)
        insets
    }
    if (isAttachedToWindow) {
        requestApplyInsets()
    } else {
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                v.requestApplyInsets()
            }

            override fun onViewDetachedFromWindow(v: View) {}
        })
    }
}

/**
 * Suma SOLO el inset inferior (barra de gestos) al paddingBottom ORIGINAL.
 * Para pantallas cuya cabecera resuelve aparte el inset superior (un toolbar
 * con [aplicarInsetSuperior]) pero cuyo contenido inferior sí debe esquivar la
 * barra de gestos. Equivalente fragment-friendly de BaseActivity.aplicarInsetInferior.
 */
fun View.aplicarInsetInferior() {
    val padBottomInicial = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
        v.updatePadding(bottom = padBottomInicial + bottom)
        insets
    }
    if (isAttachedToWindow) {
        requestApplyInsets()
    } else {
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                v.requestApplyInsets()
            }

            override fun onViewDetachedFromWindow(v: View) {}
        })
    }
}

/**
 * Iconos OSCUROS en ambas barras (estado y navegación), para pantallas de
 * fondo claro. Equivalente fragment-friendly de BaseActivity.barraEstadoOscura:
 * ahora que varios fragments comparten UNA sola Activity/window (AuthActivity),
 * cada uno debe pedir la apariencia que le corresponde al entrar, en vez de
 * que la Activity la fije una sola vez para toda la pantalla.
 */
fun Activity.barraEstadoOscura() {
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = true
        isAppearanceLightNavigationBars = true
    }
}

/**
 * Para pantallas con cabecera verde arriba y fondo claro abajo (Registro,
 * Recuperar, Términos): iconos CLAROS en la barra de estado (van sobre el
 * verde) y OSCUROS en la de navegación (va sobre el fondo claro). Equivalente
 * fragment-friendly de BaseActivity.barrasCabeceraVerde.
 */
fun Activity.barrasCabeceraVerde() {
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = false
        isAppearanceLightNavigationBars = true
    }
}

/**
 * Iconos CLAROS en AMBAS barras, para pantallas de fondo OSCURO de punta a
 * punta (preview de cámara negro, análisis verde profundo). Equivalente
 * fragment-friendly de BaseActivity.barrasClaras: la incorpora el grafo
 * nav_reporte (FASE 4), cuyos fragments comparten la window de MainActivity.
 */
fun Activity.barrasClaras() {
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = false
        isAppearanceLightNavigationBars = false
    }
}
