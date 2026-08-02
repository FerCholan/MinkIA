package com.moviles.minkia.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseActivity
import com.moviles.minkia.core.animarPopFab
import com.moviles.minkia.core.animarSeleccion
import com.moviles.minkia.core.aplicarPulsacion
import com.moviles.minkia.core.vibrar
import com.moviles.minkia.data.local.PreferenciasRepository
import com.moviles.minkia.databinding.ActivityMainBinding
import com.moviles.minkia.ui.common.CoachMarks
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Activity contenedora del ciudadano Y del administrador (FASE 2 de la
 * migración de 18 Activities a 3 con Navigation Component; AdminMainActivity
 * desaparece). Aloja en un único NavHostFragment uno de dos grafos posibles
 * según el rol de la sesión activa: nav_ciudadano o nav_admin (ver
 * [configurarGrafoSegunRol]). El menú del BottomNavigationView y, para el
 * ciudadano, el FAB de "Reportar" y sus coach marks se resuelven ahí mismo,
 * porque todos dependen de ese rol.
 */
class MainActivity : BaseActivity<ActivityMainBinding>() {

    private val prefs by lazy { PreferenciasRepository.create(this) }

    // Franja inferior que el NavHostFragment debe dejar libre para no quedar bajo
    // el BottomNavigationView. Se arma con dos piezas que llegan en momentos
    // distintos (el alto nominal del nav, al medir; el inset de la barra de
    // gestos, cuando el sistema lo entrega) y el flujo de reporte las anula a las
    // dos. Por eso se guardan sueltas y el margen se recalcula en un solo lugar.
    private var altoNav = 0
    private var insetInferior = 0
    private var enFlujoReporte = false

    // Grafo que se resolvió para el rol de la sesión (0 = todavía sin resolver).
    // Se guarda en el estado de la instancia para que una recreación no tenga que
    // volver a esperar a DataStore para armarlo: ver configurarGrafoSegunRol.
    private var grafoActivo = 0

    override fun inflateBinding(inflater: LayoutInflater) = ActivityMainBinding.inflate(inflater)

    override fun onViewReady(savedInstanceState: Bundle?) {
        // Headers verdes oscuros arriba, nav clara abajo: mismos flags que antes
        // ponía esta Activity a mano (ver BaseActivity.barrasCabeceraVerde).
        barrasCabeceraVerde()

        // La transición compartida de Reportes a Detalle ya no se configura acá:
        // desde que ambas pantallas son fragments del mismo host, la resuelven
        // ellas con FragmentNavigatorExtras y sus propias sharedElementTransition
        // (ver ReportesFragment.abrirDetalle y DetalleFragment). Los callbacks a
        // nivel Activity solo intervienen entre Activities distintas.

        val densidad = resources.displayMetrics.density
        // Lado del FAB = fabCustomSize (68dp). Lo usamos en vez de medir su height,
        // que puede valer 0 cuando el nav hace su primer layout.
        val ladoFab = (68 * densidad).toInt()

        // Edge-to-edge real: el contenido se dibuja DETRÁS de las barras. El root
        // NO padea ni arriba ni abajo: el inset superior fluye a cada fragment (su
        // header de color sube tras la status bar y baja solo el contenido vía
        // aplicarInsetSuperior); el inferior lo absorbe el nav. Solo left/right por
        // si hay gestos laterales. Las pantallas de ambos roles tienen header verde
        // oscuro arriba, así que los íconos de la status bar van en CLARO.
        altoNav = (56 * densidad).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(left = bars.left, right = bars.right)
            insetInferior = bars.bottom
            ajustarMargenDelHost()
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
        // posiciona, para que el ojo vaya derecho al corazón de la app. Corre igual
        // para los dos roles (no se condiciona por rol, para no tocar este código
        // calibrado); en el admin el FAB queda GONE apenas se resuelve la sesión,
        // ver configurarGrafoSegunRol.
        binding.fabReportar.post {
            binding.fabReportar.scaleX = 0.5f
            binding.fabReportar.scaleY = 0.5f
            binding.fabReportar.animate()
                .scaleX(1f).scaleY(1f)
                .setInterpolator(android.view.animation.OvershootInterpolator(2.5f))
                .setDuration(420)
                .start()
        }

        binding.fabReportar.aplicarPulsacion()

        configurarGrafoSegunRol(savedInstanceState?.getInt(ESTADO_GRAFO, 0) ?: 0)
    }

    /**
     * Guarda QUÉ grafo se resolvió, no el rol: es lo único que [onViewReady]
     * necesita para rearmar la pantalla sin volver a preguntarle a DataStore.
     * Ver [configurarGrafoSegunRol] para por qué esa diferencia importa.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(ESTADO_GRAFO, grafoActivo)
    }

    /**
     * Lee la sesión persistida y arma, según el rol, el resto de la pantalla: el
     * grafo del NavHostFragment, el menú del BottomNavigationView y (solo
     * ciudadano) la reacción a EXTRA_TAB y los coach marks; (solo admin) ocultar
     * el FAB. [PreferenciasRepository.sesion] es la única fuente de verdad sobre
     * quién está logueado (a diferencia de un extra del Intent, sobrevive a que
     * el sistema mate y recree el proceso), pero leerla es asincrónico
     * (DataStore), así que en el arranque en frío esto corre dentro de
     * [lifecycleScope]. Durante ese instante breve el NavHostFragment queda sin
     * grafo asignado y no dibuja nada, pero el fondo (@color/fondo del root, ver
     * activity_main.xml) ya cubre toda la pantalla, así que no se ve un frame en
     * blanco.
     *
     * El NavHostFragment lo retiene el FragmentManager entre rotaciones (mismo
     * mecanismo que usa AuthActivity.configurarDestinoInicial, ver su KDoc), así
     * que en cada recreación de la Activity este método vuelve a correr y es el
     * propio NavController quien restaura el back stack guardado en vez de
     * reiniciar en el destino de partida: rotar la pantalla no devuelve a la
     * pestaña inicial.
     *
     * Pero en esa recreación el grafo NO puede llegar tarde. Leer la sesión
     * suspende (DataStore), y mientras tanto el FragmentManager ya restauró los
     * fragments del NavHostFragment y los pone en pantalla en el onStart de esta
     * misma Activity: si todavía no hay grafo, esos fragments se crean contra un
     * NavController sin destino actual, y el primero que pida su ViewModel de
     * grafo (navGraphViewModels, los cuatro pasos del flujo de reporte) muere
     * con "No destination with ID ... is on the NavController's back stack. The
     * current destination is null". Rotar el teléfono dentro del flujo de
     * reporte cerraba la app por esto, no por la cámara.
     *
     * Por eso hay dos caminos. Con estado guardado ([grafoGuardado] != 0) el rol
     * ya se resolvió antes de rotar: el grafo se aplica ACÁ MISMO, sincrónico,
     * dentro de onCreate, antes de que se restaure un solo fragment. Sin estado
     * guardado (arranque en frío) no hay nada que restaurar todavía, así que el
     * camino asincrónico es seguro.
     */
    private fun configurarGrafoSegunRol(grafoGuardado: Int) {
        if (grafoGuardado != 0) {
            aplicarGrafo(grafoGuardado)
            return
        }
        lifecycleScope.launch {
            val usuario = prefs.sesion.first()
            aplicarGrafo(
                if (usuario?.esAdmin == true) R.navigation.nav_admin else R.navigation.nav_ciudadano
            )
        }
    }

    /**
     * Arma el NavHostFragment, el menú del nav y lo que cuelgue de ellos para el
     * [grafo] ya resuelto. Se llama UNA sola vez por instancia de la Activity
     * (ver [configurarGrafoSegunRol]): volver a inflar el grafo sobre un
     * NavController que ya tiene uno reemplazaría el back stack restaurado por
     * el destino inicial, que es justo lo que rotar no debe hacer.
     */
    private fun aplicarGrafo(grafo: Int) {
        grafoActivo = grafo
        val navHost = supportFragmentManager.findFragmentById(R.id.navHostMain) as NavHostFragment
        val nav = navHost.navController
        nav.graph = nav.navInflater.inflate(grafo)
        binding.bottomNav.menu.clear()

        if (grafo == R.navigation.nav_admin) {
            binding.bottomNav.inflateMenu(R.menu.admin_nav_menu)
            binding.bottomNav.setupWithNavController(nav)
            // El FAB de "Reportar" es una acción del ciudadano (C09): el admin
            // modera lo que otros reportan, no reporta. El resto de la lógica
            // del FAB (posicionamiento, pop, elevación), arriba, corre igual
            // para los dos roles; acá solo se lo oculta.
            binding.fabReportar.visibility = View.GONE
        } else {
            binding.bottomNav.inflateMenu(R.menu.bottom_nav_menu)
            binding.bottomNav.setupWithNavController(nav)
            animarSeleccionEnCadaCambioDeDestino(nav)
            ocultarNavegacionEnFlujoReporte(nav)
            cablearFab(nav)
            mostrarCoachMarksSiCorresponde()
        }
    }

    /**
     * Rebote + haptic del ítem recién elegido: mismo gesto que antes daba
     * BottomNavigationView.setOnItemSelectedListener a mano (ver
     * core/Interacciones.kt#animarSeleccion). BottomNavigationView solo admite
     * UN listener de tap, y setupWithNavController ya lo ocupa; por eso el gesto
     * se engancha del lado del NavController, que sí admite más de un
     * observador. Se ignora la primera notificación (la del arranque del grafo
     * o la restauración tras rotar): esa selección no la disparó un toque, y el
     * listener manual anterior tampoco la animaba en ese caso.
     */
    /**
     * Deja libre para el host la franja que ocupa el BottomNavigationView. Dentro
     * del flujo de reporte no hay nav que esquivar (la cámara y el análisis van a
     * pantalla completa), así que el margen cae a cero. Se centraliza acá porque
     * las dos piezas que lo componen llegan por caminos distintos.
     */
    private fun ajustarMargenDelHost() {
        binding.navHostMain.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = if (enFlujoReporte) 0 else altoNav + insetInferior
        }
    }

    /**
     * El flujo de reporte se dibuja a pantalla completa: mientras se está dentro
     * de él se ocultan el BottomNavigationView y el FAB, y el host recupera la
     * franja inferior. Se reconoce por el grafo PADRE del destino y no por una
     * lista de ids, así sumar o quitar un paso al flujo no obliga a tocar esto.
     */
    private fun ocultarNavegacionEnFlujoReporte(nav: NavController) {
        nav.addOnDestinationChangedListener { _, destino, _ ->
            enFlujoReporte = destino.parent?.id == R.id.nav_reporte
            binding.bottomNav.isVisible = !enFlujoReporte
            binding.fabReportar.isVisible = !enFlujoReporte
            ajustarMargenDelHost()
        }
    }

    /**
     * Acción núcleo del producto (C09): el FAB entra al grafo anidado del flujo
     * de reporte. Se cablea acá, y no junto al resto de la configuración del FAB,
     * porque necesita el [NavController], que recién existe una vez resuelto el
     * grafo del rol. La navegación se pospone un instante para que el rebote del
     * botón alcance a verse antes de que la pantalla cambie.
     */
    private fun cablearFab(nav: NavController) {
        binding.fabReportar.setOnClickListener {
            it.vibrar() // feedback háptico en la acción núcleo
            it.animarPopFab() // rebote que confirma el toque al corazón de la app
            it.postDelayed({ nav.navigate(R.id.nav_reporte) }, 150)
        }
    }

    private fun animarSeleccionEnCadaCambioDeDestino(nav: NavController) {
        var esPrimeraSincronizacion = true
        nav.addOnDestinationChangedListener { _, destino, _ ->
            if (esPrimeraSincronizacion) esPrimeraSincronizacion = false
            else binding.bottomNav.animarSeleccion(destino.id)
        }
    }

    /**
     * En el primer arranque guía al ciudadano con coach marks sobre el FAB y la
     * navegación. Exclusivo del ciudadano (AdminMainActivity nunca los mostró),
     * por eso se llama solo desde la rama ciudadano de
     * [configurarGrafoSegunRol]. Espera a que el FAB esté posicionado (post) y
     * al terminar marca el onboarding como visto para no repetirlo.
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

    private companion object {
        /** Clave del grafo resuelto dentro del estado guardado de la instancia. */
        const val ESTADO_GRAFO = "estado_grafo_rol"
    }
}
