package com.moviles.minkia.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import com.moviles.minkia.R
import com.moviles.minkia.core.animarPopFab
import com.moviles.minkia.core.animarSeleccion
import com.moviles.minkia.core.aplicarPulsacion
import com.moviles.minkia.core.vibrar
import com.moviles.minkia.data.local.PreferenciasRepository
import com.moviles.minkia.databinding.ActivityMainBinding
import com.moviles.minkia.ui.captura.CapturaActivity
import com.moviles.minkia.ui.common.CoachMarks
import com.moviles.minkia.ui.home.HomeFragment
import com.moviles.minkia.ui.mapa.MapaFragment
import com.moviles.minkia.ui.perfil.PerfilFragment
import com.moviles.minkia.ui.reportes.ReportesFragment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Actividad principal. Orquesta la navegación inferior entre las secciones del
 * ciudadano y el FAB central de "Reportar". Cada sección es un fragment; se
 * intercambian en el contenedor según la pestaña elegida.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy { PreferenciasRepository.create(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Container transform: esta Activity es el origen del elemento compartido
        // (la tarjeta de reporte que se expande hacia el detalle).
        setExitSharedElementCallback(MaterialContainerTransformSharedElementCallback())
        window.sharedElementsUseOverlay = false

        val densidad = resources.displayMetrics.density
        // Lado del FAB = fabCustomSize (68dp). Lo usamos en vez de medir su height,
        // que puede valer 0 cuando el nav hace su primer layout.
        val ladoFab = (68 * densidad).toInt()

        // Edge-to-edge real: el contenido se dibuja DETRÁS de las barras. El root
        // NO padea ni arriba ni abajo: el inset superior fluye a cada fragment (su
        // header de color sube tras la status bar y baja solo el contenido vía
        // aplicarInsetSuperior); el inferior lo absorbe el nav. Solo left/right por
        // si hay gestos laterales. Las 4 pantallas tienen header verde oscuro arriba,
        // así que los íconos de la status bar van en CLARO.
        WindowCompat.getInsetsController(window, binding.root).apply {
            isAppearanceLightStatusBars = false // headers verdes oscuros arriba
            isAppearanceLightNavigationBars = true // nav claro abajo: iconos oscuros
        }
        // Mismo fix que BaseActivity: forzar barras transparentes y matar el scrim de
        // contraste que Samsung One UI pinta como franja blanca bajo el nav.
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(left = bars.left, right = bars.right)
            binding.fragmentContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = (56 * densidad).toInt() + bars.bottom
            }
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav) { v, insets ->
            v.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom)
            insets
        }

        // El FAB sobresale medio cuerpo por encima del borde superior del nav. La
        // altura REAL del nav no es fija: depende del inset (gestos vs 3 botones) y
        // del alto del label, así que un margen fijo dejaba al FAB casi tapado. Se
        // recalcula cada vez que el nav se re-mide y se lo trae al frente para que
        // su elevación gane al fondo opaco del nav (compatElevation, no elevation
        // directo: el StateListAnimator del FAB pisa este último).
        // FAB protagonista: elevación más honda (sombra dramática) para que el
        // squircle XL flote claramente sobre el nav. compatElevation, NO elevation:
        // el StateListAnimator del FAB pisa este último restaurándolo a app:elevation.
        binding.bottomNav.elevation = 6f * densidad
        binding.fabReportar.compatElevation = 18f * densidad
        // Cuánto baja el FAB respecto a sobresalir medio cuerpo: lo integra más al
        // nav sin que su cuerpo llegue a tapar los labels de las pestañas.
        val bajadaFab = (16 * densidad).toInt()
        binding.bottomNav.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            val margen = (bottom - top) - ladoFab / 2 - bajadaFab
            val lp = binding.fabReportar.layoutParams as ViewGroup.MarginLayoutParams
            if (lp.bottomMargin != margen) {
                lp.bottomMargin = margen
                binding.fabReportar.layoutParams = lp
            }
            binding.fabReportar.bringToFront()
        }

        // Pop de entrada: el FAB aparece creciendo con rebote la primera vez que se
        // posiciona, para que el ojo vaya derecho al corazón de la app.
        binding.fabReportar.post {
            binding.fabReportar.scaleX = 0.5f
            binding.fabReportar.scaleY = 0.5f
            binding.fabReportar.animate()
                .scaleX(1f).scaleY(1f)
                .setInterpolator(android.view.animation.OvershootInterpolator(2.5f))
                .setDuration(420)
                .start()
        }

        if (savedInstanceState == null) {
            mostrarFragment(HomeFragment())
            binding.bottomNav.selectedItemId = R.id.nav_inicio
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment? = when (item.itemId) {
                R.id.nav_inicio -> HomeFragment()
                R.id.nav_mapa -> MapaFragment()
                R.id.nav_reportes -> ReportesFragment()
                R.id.nav_perfil -> PerfilFragment()
                else -> null
            }
            fragment?.let {
                mostrarFragment(it)
                // El ícono de la pestaña elegida rebota + haptic: navegar se siente vivo.
                binding.bottomNav.animarSeleccion(item.itemId)
                true
            } ?: false
        }

        // La confirmación de reporte puede pedir abrir una pestaña puntual.
        if (intent.getIntExtra(EXTRA_TAB, TAB_INICIO) == TAB_MAPA) {
            binding.bottomNav.selectedItemId = R.id.nav_mapa
        }

        binding.fabReportar.aplicarPulsacion()
        binding.fabReportar.setOnClickListener {
            it.vibrar() // feedback háptico en la acción núcleo
            it.animarPopFab() // rebote que confirma el toque al corazón de la app
            // Núcleo del producto: abre el flujo de reporte por la cámara (C09). Se
            // pospone apenas para que el pop alcance a verse antes de la transición.
            it.postDelayed({
                startActivity(Intent(this, CapturaActivity::class.java))
            }, 150)
        }

        mostrarCoachMarksSiCorresponde()
    }

    /**
     * En el primer arranque guía al ciudadano con coach marks sobre el FAB y la
     * navegación. Espera a que el FAB esté posicionado (post) y al terminar marca
     * el onboarding como visto para no repetirlo.
     */
    private fun mostrarCoachMarksSiCorresponde() {
        lifecycleScope.launch {
            if (prefs.onboardingVisto.first()) return@launch
            binding.fabReportar.post {
                CoachMarks.mostrar(this@MainActivity, binding.fabReportar, binding.bottomNav) {
                    lifecycleScope.launch { prefs.marcarOnboardingVisto() }
                }
            }
        }
    }

    companion object {
        const val EXTRA_TAB = "extra_tab"
        const val TAB_INICIO = 0
        const val TAB_MAPA = 1
    }

    private fun mostrarFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
