package com.moviles.minkia.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * Datos de una diapositiva del onboarding (mockup C02): un ícono, un título y
 * una descripción. Las páginas se definen de forma estática en el ViewModel.
 */
data class OnboardingPage(
    @DrawableRes val icono: Int,
    @StringRes val titulo: Int,
    @StringRes val descripcion: Int
)
