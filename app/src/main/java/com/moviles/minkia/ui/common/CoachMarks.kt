package com.moviles.minkia.ui.common

import android.app.Activity
import android.view.View
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence
import com.moviles.minkia.R

/**
 * Recorrido guiado con "coach marks" (foco con globo de texto) que se muestra la
 * PRIMERA vez que el ciudadano entra a la app. Resalta el FAB de reportar y la
 * navegación inferior. Usa TapTargetView con la paleta de marca.
 *
 * El llamador decide cuándo mostrarlo (solo si onboarding no fue visto) y qué
 * hacer al terminar (marcar el flag). Acá solo se arma y dispara la secuencia.
 */
object CoachMarks {

    fun mostrar(activity: Activity, fab: View, bottomNav: View, alTerminar: () -> Unit) {
        TapTargetSequence(activity)
            .targets(
                conEstilo(
                    TapTarget.forView(
                        fab,
                        activity.getString(R.string.coach_fab_titulo),
                        activity.getString(R.string.coach_fab_desc)
                    )
                ),
                conEstilo(
                    TapTarget.forView(
                        bottomNav,
                        activity.getString(R.string.coach_nav_titulo),
                        activity.getString(R.string.coach_nav_desc)
                    )
                )
            )
            .continueOnCancel(true)
            .listener(object : TapTargetSequence.Listener {
                override fun onSequenceFinish() = alTerminar()
                override fun onSequenceStep(lastTarget: TapTarget, targetClicked: Boolean) {}
                override fun onSequenceCanceled(lastTarget: TapTarget) = alTerminar()
            })
            .start()
    }

    /** Estilo común de cada coach mark, con la paleta de MinkIA. */
    private fun conEstilo(target: TapTarget): TapTarget = target
        .outerCircleColor(R.color.verde_bosque)
        .outerCircleAlpha(0.96f)
        .targetCircleColor(R.color.white)
        .titleTextColor(R.color.white)
        .descriptionTextColor(R.color.crema)
        .dimColor(R.color.verde_bosque_osc)
        .drawShadow(true)
        .cancelable(true)
        .transparentTarget(true)
        .tintTarget(false)
}
