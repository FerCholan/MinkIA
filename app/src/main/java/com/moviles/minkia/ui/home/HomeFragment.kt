package com.moviles.minkia.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.TooltipCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.TileOverlayOptions
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.maps.android.heatmaps.HeatmapTileProvider
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseFragment
import com.moviles.minkia.core.UiState
import com.moviles.minkia.core.animarEntrada
import com.moviles.minkia.core.aplicarInsetSuperior
import com.moviles.minkia.core.aplicarPulsacion
import com.moviles.minkia.core.recortarEsquinasInferiores
import com.moviles.minkia.data.model.FocoMapa
import com.moviles.minkia.data.model.ResumenCiudadano
import com.moviles.minkia.data.sync.CambiosReportes
import com.moviles.minkia.databinding.FragmentHomeBinding

/**
 * Pantalla de inicio del ciudadano (mockup C07). Patrón de referencia para las
 * demás pantallas: extiende [BaseFragment], observa un UiState y solo describe
 * cómo pintar cada estado. Sin lógica de negocio ni acceso a datos.
 */
class HomeFragment : BaseFragment<FragmentHomeBinding>() {

    private val viewModel: HomeViewModel by viewModels { HomeViewModel.Factory() }

    private val adapter = PuntoCriticoAdapter()

    // El mapa de calor necesita dos piezas que llegan por caminos asíncronos
    // distintos: la instancia del mapa (callback de getMapAsync) y los focos (el
    // ViewModel, que los lee de Firestore). Se guarda cada una al llegar y pinta
    // la que llegue segunda; ver [pintarHeatmap].
    private var mapaPreview: GoogleMap? = null
    private var focos: List<FocoMapa>? = null

    // La primera entrega de CambiosReportes.version es el valor que ya está
    // puesto, no un cambio: se ignora para no cargar dos veces junto al onResume.
    private var primerAviso = true

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentHomeBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.headerContenido.aplicarInsetSuperior()
        binding.headerInicio.recortarEsquinasInferiores()
        binding.rvPuntosCercanos.adapter = adapter
        configurarMapaCalor()
        binding.btnReintentar.setOnClickListener { viewModel.cargarResumen() }
        binding.btnNotificaciones.setOnClickListener {
            findNavController().navigate(R.id.action_home_a_notificaciones)
        }
        TooltipCompat.setTooltipText(binding.btnNotificaciones, getString(R.string.tooltip_notificaciones))
        binding.btnNotificaciones.aplicarPulsacion()

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is UiState.Loading -> mostrarCarga()
                is UiState.Success -> mostrarDatos(state.data)
                is UiState.Error -> mostrarError(state.mensaje)
            }
        }

        // Focos del mapa de calor: carga aparte del resumen. Si falla, la tarjeta
        // queda con el mapa sin heatmap y sin aviso, igual que antes del refactor:
        // es un adorno de la pantalla, no su contenido principal.
        viewModel.focosState.observe(viewLifecycleOwner) { estado ->
            if (estado is UiState.Success) {
                focos = estado.data
                pintarHeatmap()
            }
        }

        // Un reporte de la cola puede salir MIENTRAS este Inicio está en pantalla
        // (típico al cambiar de red): sin este aviso, ni el contador ni el mapa de
        // calor se enteraban hasta reiniciar la app. Ver CambiosReportes.
        CambiosReportes.version.observe(viewLifecycleOwner) {
            if (primerAviso) {
                primerAviso = false
            } else {
                viewModel.cargarResumen()
                viewModel.cargarFocos()
            }
        }
    }

    /**
     * Carga el resumen y los focos cada vez que la pantalla vuelve al frente. Es
     * el ÚNICO camino de carga (el ViewModel ya no carga en su init): un reporte
     * recién enviado tiene que sumar en "Tus reportes" y aparecer en el mapa de
     * calor sin obligar a reiniciar la app. Ver ReportesFragment.onResume.
     */
    override fun onResume() {
        super.onResume()
        viewModel.cargarResumen()
        viewModel.cargarFocos()
    }

    /** Mapa de calor del Inicio: Google Map sin gestos con un heatmap de los focos. */
    private fun configurarMapaCalor() {
        val mapaCalor = childFragmentManager
            .findFragmentById(R.id.mapaCalorContainer) as SupportMapFragment
        mapaCalor.getMapAsync { mapa ->
            mapa.uiSettings.setAllGesturesEnabled(false) // es un preview, no se navega acá
            mapa.uiSettings.isMapToolbarEnabled = false
            mapa.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(-9.0745, -78.5936), 12.5f))
            mapa.setOnMapClickListener { irAlMapa() }
            mapaPreview = mapa
            pintarHeatmap()
        }
    }

    /**
     * Superpone el heatmap cuando ya están disponibles el mapa y los focos. Se
     * llama desde los dos orígenes asíncronos y sale sin hacer nada si todavía
     * falta alguno, así no importa cuál termine primero.
     */
    private fun pintarHeatmap() {
        val mapa = mapaPreview ?: return
        val puntos = focos?.takeIf { it.isNotEmpty() } ?: return
        val provider = HeatmapTileProvider.Builder()
            .data(puntos.map { LatLng(it.latitud, it.longitud) })
            .build()
        mapa.addTileOverlay(TileOverlayOptions().tileProvider(provider))
    }

    /** El mapa vive en la vista: soltarlo evita retenerla tras onDestroyView. */
    override fun onDestroyView() {
        mapaPreview = null
        super.onDestroyView()
    }

    /**
     * Tocar el mapa de calor abre el mapa completo (pestaña Mapa). Se asigna el
     * id del destino (no se llama a NavController.navigate directo) para pasar
     * por el mismo listener que instala BottomNavigationView.setupWithNavController
     * en MainActivity: así la pestaña Mapa queda marcada como seleccionada y la
     * navegación aplica el mismo popUpTo/restoreState que un toque directo.
     */
    private fun irAlMapa() {
        requireActivity().findViewById<BottomNavigationView>(R.id.bottomNav)
            .selectedItemId = R.id.mapaFragment
    }

    private fun mostrarCarga() {
        binding.skeleton.visibility = View.VISIBLE
        binding.skeleton.startShimmer()
        binding.contenido.visibility = View.GONE
        binding.grupoError.visibility = View.GONE
    }

    private fun mostrarDatos(resumen: ResumenCiudadano) {
        binding.skeleton.stopShimmer()
        binding.skeleton.visibility = View.GONE
        binding.grupoError.visibility = View.GONE
        binding.contenido.visibility = View.VISIBLE

        if (resumen.nombre.isNotBlank()) binding.tvSaludoNombre.text = resumen.nombre
        binding.tvPuntosActivos.text = resumen.puntosActivos.toString()
        binding.tvTusReportes.text = resumen.tusReportes.toString()
        binding.tvResueltos.text = formatearMiles(resumen.resueltos)
        binding.tvFocosCerca.text = "${resumen.focosCerca} focos cerca de ti"

        adapter.submitList(resumen.puntosCercanos) { binding.rvPuntosCercanos.scheduleLayoutAnimation() }
        binding.contenido.animarEntrada()
    }

    private fun mostrarError(mensaje: String) {
        binding.skeleton.stopShimmer()
        binding.skeleton.visibility = View.GONE
        binding.contenido.visibility = View.GONE
        binding.grupoError.visibility = View.VISIBLE
        binding.tvError.text = mensaje
    }

    private fun formatearMiles(valor: Int): String =
        if (valor >= 1000) "%.1fk".format(valor / 1000.0) else valor.toString()

    override fun onBindingDestroy() {
        binding.rvPuntosCercanos.adapter = null
    }
}
