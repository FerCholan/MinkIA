package com.moviles.minkia.core

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Microinteracción de pulsación: la vista se hunde apenas al tocarla y vuelve al
 * soltar. Da feedback táctil-visual de app pro, sin consumir el click (el
 * onClickListener sigue funcionando igual). Llamar una vez al configurar la vista.
 */
@SuppressLint("ClickableViewAccessibility")
fun View.aplicarPulsacion(escala: Float = 0.96f) {
    setOnTouchListener { vista, evento ->
        when (evento.action) {
            MotionEvent.ACTION_DOWN ->
                vista.animate().scaleX(escala).scaleY(escala).setDuration(90).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                vista.animate().scaleX(1f).scaleY(1f).setDuration(130).start()
        }
        false // no consume el evento: el click sigue disparándose normalmente
    }
}

/** Vibración corta de feedback, segura desde minSdk 24. Para confirmar acciones. */
fun View.vibrar() {
    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
}

/**
 * Festeja la pestaña recién elegida: su ícono entra desde 0.8 con un rebote
 * (overshoot) y suma un haptic. Da feedback de app viva al navegar, en vez de la
 * transición plana por defecto. Se llama tras seleccionar el ítem.
 */
fun BottomNavigationView.animarSeleccion(itemId: Int) {
    val icono = findViewById<View>(itemId)
        ?.findViewById<View>(com.google.android.material.R.id.navigation_bar_item_icon_view)
        ?: return
    icono.animate().cancel()
    icono.scaleX = 0.8f
    icono.scaleY = 0.8f
    icono.animate()
        .scaleX(1f).scaleY(1f)
        .setInterpolator(OvershootInterpolator(3f))
        .setDuration(300)
        .start()
    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
}

/**
 * "Pop" del FAB al tocarlo: un rebote rápido de escala (overshoot) que lo hace
 * sentir el centro vivo de la app. Complementa la pulsación de hundido. Pensado
 * para llamarse en el onClick, antes de abrir el flujo.
 */
fun View.animarPopFab() {
    animate().cancel()
    scaleX = 1f
    scaleY = 1f
    animate()
        .scaleX(1.14f).scaleY(1.14f)
        .setDuration(110)
        .withEndAction {
            animate()
                .scaleX(1f).scaleY(1f)
                .setInterpolator(OvershootInterpolator(4f))
                .setDuration(220)
                .start()
        }
        .start()
}
