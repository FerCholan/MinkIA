package com.moviles.minkia.ui.mapa

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseFragment
import com.moviles.minkia.core.UiState
import com.moviles.minkia.core.aplicarInsetSuperior
import com.moviles.minkia.data.model.FocoMapa
import com.moviles.minkia.ui.detalle.DetalleFragment
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.databinding.FragmentMapaBinding

/**
 * Explorar focos en el mapa (mockup C08). Google Map real con un marcador por
 * cada foco activo de la comunidad (de Firestore), coloreado por severidad. Los
 * chips de arriba filtran los marcadores por severidad (Todos/Alta/Media/Baja).
 */
class MapaFragment : BaseFragment<FragmentMapaBinding>() {

    private val viewModel: MapaViewModel by viewModels { MapaViewModel.Factory() }

    private var mapa: GoogleMap? = null
    private var focos: List<FocoMapa> = emptyList()
    private var filtro: Severidad? = null // null = Todos

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentMapaBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.headerMapaContenido.aplicarInsetSuperior()

        binding.chipTodos.setOnClickListener { aplicarFiltro(null, binding.chipTodos) }
        binding.chipAlta.setOnClickListener { aplicarFiltro(Severidad.ALTA, binding.chipAlta) }
        binding.chipMedia.setOnClickListener { aplicarFiltro(Severidad.MEDIA, binding.chipMedia) }
        binding.chipBaja.setOnClickListener { aplicarFiltro(Severidad.BAJA, binding.chipBaja) }

        val mapaFragment = childFragmentManager
            .findFragmentById(R.id.mapaContainer) as SupportMapFragment

        mapaFragment.getMapAsync { googleMap ->
            mapa = googleMap
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(CHIMBOTE, 13f))
            // Tocar la ventana de un marcador abre el Detalle del foco (con su foto).
            googleMap.setOnInfoWindowClickListener { marker ->
                (marker.tag as? FocoMapa)?.let { abrirDetalle(it) }
            }
            // El mapa puede quedar listo antes o después que lleguen los focos desde
            // el ViewModel: repinta con lo que ya haya, pintarMarcadores no hace nada
            // si la lista todavía está vacía.
            pintarMarcadores()
        }

        viewModel.uiState.observe(viewLifecycleOwner) { estado ->
            when (estado) {
                // Sin indicador propio: el mapa ya se ve mientras cargan los focos.
                is UiState.Loading -> Unit
                is UiState.Success -> {
                    focos = estado.data
                    pintarMarcadores()
                }
                // Mismo criterio que antes: sin foco no hay marcadores, sin aviso visible.
                is UiState.Error -> Unit
            }
        }
    }

    /** Cambia el filtro de severidad, resalta el chip elegido y repinta el mapa. */
    private fun aplicarFiltro(severidad: Severidad?, chip: TextView) {
        filtro = severidad
        resaltarChip(chip)
        pintarMarcadores()
    }

    /** Limpia y vuelve a poner solo los marcadores que pasan el filtro actual. */
    private fun pintarMarcadores() {
        val m = mapa ?: return
        m.clear()
        focos.filter { filtro == null || it.severidad == filtro }
            .forEach { foco ->
                val marcador = m.addMarker(
                    MarkerOptions()
                        .position(LatLng(foco.latitud, foco.longitud))
                        .title(foco.direccion.ifBlank { "Foco de basura" })
                        .snippet(foco.tipo)
                        .icon(BitmapDescriptorFactory.defaultMarker(hueSeveridad(foco.severidad)))
                )
                marcador?.tag = foco
            }
    }

    /** Pinta el chip seleccionado en verde y deja los otros en su estilo normal. */
    private fun resaltarChip(seleccionado: TextView) {
        listOf(binding.chipTodos, binding.chipAlta, binding.chipMedia, binding.chipBaja).forEach { chip ->
            val activo = chip === seleccionado
            chip.setBackgroundResource(if (activo) R.drawable.bg_chip_mapa_sel else R.drawable.bg_chip_mapa)
            chip.setTextColor(ContextCompat.getColor(requireContext(), if (activo) R.color.verde_bosque else R.color.white))
        }
    }

    /**
     * Abre el Detalle del foco tocado (C15). Antes mandaba diez extras (todo
     * FocoMapa salvo "vecinos", que el Mapa nunca tuvo); ahora solo su ticket,
     * que DetalleFragment usa para pedirle el reporte completo al repositorio.
     * Sin elemento compartido: un marcador de mapa no tiene tarjeta que expandir
     * (mismo comportamiento que antes, ver action_mapa_a_detalle en
     * nav_ciudadano.xml).
     */
    private fun abrirDetalle(foco: FocoMapa) {
        findNavController().navigate(
            R.id.action_mapa_a_detalle,
            Bundle().apply { putString(DetalleFragment.ARG_REPORTE_ID, foco.id) }
        )
    }

    private fun hueSeveridad(severidad: Severidad) = when (severidad) {
        Severidad.ALTA -> BitmapDescriptorFactory.HUE_RED
        Severidad.MEDIA -> BitmapDescriptorFactory.HUE_ORANGE
        Severidad.BAJA -> BitmapDescriptorFactory.HUE_YELLOW
    }

    companion object {
        // Centro de Chimbote como vista inicial.
        private val CHIMBOTE = LatLng(-9.0745, -78.5936)
    }
}
