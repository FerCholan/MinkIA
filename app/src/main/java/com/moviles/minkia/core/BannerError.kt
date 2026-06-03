package com.moviles.minkia.core

import android.view.View
import com.moviles.minkia.databinding.ViewBannerErrorBinding

/**
 * Muestra el banner de error inline (reemplaza a los Toast del flujo de acceso)
 * con una entrada suave. Reutilizable en login, registro y recuperar.
 */
fun ViewBannerErrorBinding.mostrarError(mensaje: String) {
    tvBannerError.text = mensaje
    if (root.visibility != View.VISIBLE) {
        root.visibility = View.VISIBLE
        root.animarEntrada(desplazamientoY = 16f)
    }
}

/** Oculta el banner de error (al reintentar o corregir). */
fun ViewBannerErrorBinding.ocultarError() {
    root.visibility = View.GONE
}
