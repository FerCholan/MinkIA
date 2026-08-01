package com.moviles.minkia.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import androidx.navigation.fragment.NavHostFragment
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseActivity
import com.moviles.minkia.databinding.ActivityAuthBinding

/**
 * Activity contenedora del flujo de ACCESO (FASE 1 de la migración de 18
 * Activities a 3 con Navigation Component; mismo modelo que Eventify). Aloja
 * en un único NavHostFragment el grafo nav_auth: onboarding, login, registro,
 * recuperar contraseña, términos y permisos. Ya no hay una Activity por
 * pantalla: activity_auth.xml es solo un FragmentContainerView que va
 * cambiando de fragment (ver res/navigation/nav_auth.xml).
 */
class AuthActivity : BaseActivity<ActivityAuthBinding>() {

    override fun inflateBinding(inflater: LayoutInflater) = ActivityAuthBinding.inflate(inflater)

    override fun onViewReady(savedInstanceState: Bundle?) {
        configurarDestinoInicial()
    }

    /**
     * Elige el destino inicial del grafo según CÓMO se llegó a esta Activity.
     * Hoy existen DOS caminos, que antes resolvían Activities distintas:
     *
     * 1) Desde SplashActivity, sin sesión guardada: antes abría directo
     *    OnboardingActivity. SplashActivity.destinoSegunSesion NO usa ninguna
     *    bandera de "onboarding ya visto" para decidir esto (revisado en el
     *    código: solo mira si hay sesión). Ojo con no confundir esto con
     *    `PreferenciasRepository.onboardingVisto`: ese flag es de OTRO feature
     *    (los coach marks de MainActivity, ver
     *    MainActivity.mostrarCoachMarksSiCorresponde) y reusarlo acá acoplaría
     *    dos comportamientos que hoy son independientes, cambiando conducta
     *    visible. Por eso, sin el extra de abajo, el onboarding se muestra
     *    siempre (replica el comportamiento actual 1 a 1).
     * 2) Desde un logout (PerfilFragment, ConfiguracionFragment,
     *    AdminCuentaFragment): esas pantallas hoy relanzan esta misma Activity
     *    DIRECTO (Intent a AuthActivity), sin pasar por onboarding. Por eso
     *    marcan [EXTRA_OMITIR_ONBOARDING] al construirlo.
     *
     * El grafo se infla acá y NO en el layout: activity_auth.xml omite a
     * propósito el `app:navGraph`, porque declararlo en los dos lados lo
     * inflaría dos veces (se crearía el fragment del destino "de fábrica" para
     * descartarlo enseguida, y en cada rotación este inflado pisaría el back
     * stack ya restaurado por el NavHostFragment). Con el grafo puesto solo por
     * código, es `NavController.setGraph` quien restaura el back stack guardado
     * si lo hay, de modo que rotar la pantalla no devuelve al destino inicial.
     * Corre dentro de onCreate (vía onViewReady), antes del primer frame.
     */
    private fun configurarDestinoInicial() {
        val navHost = supportFragmentManager.findFragmentById(R.id.navHostAuth) as NavHostFragment
        val nav = navHost.navController
        val grafo = nav.navInflater.inflate(R.navigation.nav_auth)
        val omitirOnboarding = intent.getBooleanExtra(EXTRA_OMITIR_ONBOARDING, false)
        grafo.setStartDestination(if (omitirOnboarding) R.id.loginFragment else R.id.onboardingFragment)
        nav.graph = grafo
    }

    companion object {
        /** Extra para saltar el carrusel de onboarding y abrir directo en Login (logout). */
        const val EXTRA_OMITIR_ONBOARDING = "extra_omitir_onboarding"
    }
}
