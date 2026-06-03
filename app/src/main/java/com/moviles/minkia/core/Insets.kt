package com.moviles.minkia.core

import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import androidx.core.view.ViewCompat
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
