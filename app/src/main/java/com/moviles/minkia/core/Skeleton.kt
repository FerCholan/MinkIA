package com.moviles.minkia.core

import android.view.View
import com.facebook.shimmer.ShimmerFrameLayout

/**
 * Muestra u oculta un skeleton de carga. Si la vista es un ShimmerFrameLayout,
 * arranca o detiene el brillo automáticamente. Reutilizable en cualquier pantalla.
 */
fun View.mostrarSkeleton(mostrar: Boolean) {
    visibility = if (mostrar) View.VISIBLE else View.GONE
    (this as? ShimmerFrameLayout)?.let { if (mostrar) it.startShimmer() else it.stopShimmer() }
}
