package com.moviles.minkia.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseFragment
import com.moviles.minkia.core.UiState
import com.moviles.minkia.core.aplicarInsetSuperior
import com.moviles.minkia.data.model.FocoMapa
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.data.model.ZonaAfectada
import com.moviles.minkia.databinding.FragmentAdminPuntosBinding
import com.moviles.minkia.databinding.ItemZonaBinding

/**
 * Puntos críticos del administrador (A03). Google Map REAL con un marcador por
 * cada foco activo (coloreado por severidad) y la lista de zonas agrupadas, todo
 * desde Firestore. El buscador filtra EN VIVO tanto los marcadores del mapa como
 * la lista de zonas por texto (zona, dirección o tipo).
 */
class PuntosCriticosFragment : BaseFragment<FragmentAdminPuntosBinding>() {

    private val viewModel: PuntosCriticosViewModel by viewModels { PuntosCriticosViewModel.Factory() }

    private var mapa: GoogleMap? = null
    private var focos: List<FocoMapa> = emptyList()
    private var zonas: List<ZonaAfectada> = emptyList()

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentAdminPuntosBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.headerPuntosContenido.aplicarInsetSuperior()

        val mapaFragment = childFragmentManager
            .findFragmentById(R.id.mapaAdminContainer) as SupportMapFragment
        mapaFragment.getMapAsync { m ->
            mapa = m
            m.moveCamera(CameraUpdateFactory.newLatLngZoom(CHIMBOTE, 12.5f))
            // El mapa puede quedar listo antes o después que los focos del ViewModel:
            // repinta con lo que ya haya (aplicarFiltro no hace nada con listas vacías).
            aplicarFiltro(binding.etBuscar.text?.toString().orEmpty())
        }

        // Búsqueda en vivo: filtra marcadores + lista a medida que escribe.
        binding.etBuscar.doAfterTextChanged { texto -> aplicarFiltro(texto?.toString().orEmpty()) }

        observarViewModel()
    }

    /** Focos (marcadores) y zonas (lista) llegan de repositorios distintos, por separado. */
    private fun observarViewModel() {
        viewModel.focosState.observe(viewLifecycleOwner) { estado ->
            when (estado) {
                // Sin indicador propio: el mapa ya se ve mientras cargan los focos.
                is UiState.Loading -> Unit
                is UiState.Success -> {
                    focos = estado.data
                    aplicarFiltro(binding.etBuscar.text?.toString().orEmpty())
                }
                // Mismo criterio que antes: sin foco no hay marcadores, sin aviso visible.
                is UiState.Error -> Unit
            }
        }
        viewModel.zonasState.observe(viewLifecycleOwner) { estado ->
            when (estado) {
                is UiState.Loading -> Unit
                is UiState.Success -> {
                    zonas = estado.data
                    aplicarFiltro(binding.etBuscar.text?.toString().orEmpty())
                }
                is UiState.Error -> Unit
            }
        }
    }

    /**
     * Filtra por texto (case-insensitive) los marcadores y las zonas. Muestra TODAS
     * las zonas (ya ordenadas por más afectadas); el número de arriba siempre coincide
     * con lo que se ve en la lista. La lista scrollea si hay muchas.
     */
    private fun aplicarFiltro(consulta: String) {
        val q = consulta.trim().lowercase()
        val focosVisibles = if (q.isBlank()) focos
        else focos.filter { it.direccion.lowercase().contains(q) || it.tipo.lowercase().contains(q) }
        val zonasVisibles = if (q.isBlank()) zonas else zonas.filter { it.nombre.lowercase().contains(q) }

        pintarMarcadores(focosVisibles)
        pintarZonas(zonasVisibles)
    }

    private fun pintarMarcadores(lista: List<FocoMapa>) {
        val m = mapa ?: return
        m.clear()
        lista.forEach { foco ->
            m.addMarker(
                MarkerOptions()
                    .position(LatLng(foco.latitud, foco.longitud))
                    .title(foco.direccion.ifBlank { "Foco de basura" })
                    .snippet(foco.tipo)
                    .icon(BitmapDescriptorFactory.defaultMarker(hueSeveridad(foco.severidad)))
            )
        }
    }

    private fun pintarZonas(lista: List<ZonaAfectada>) {
        binding.tvTotalPuntos.text = getString(R.string.admin_puntos_total_fmt, lista.size)
        binding.contenedorZonas.removeAllViews()
        lista.forEach { zona -> binding.contenedorZonas.addView(crearFilaZona(zona)) }
    }

    private fun crearFilaZona(zona: ZonaAfectada): android.view.View {
        val fila = ItemZonaBinding.inflate(layoutInflater, binding.contenedorZonas, false)
        fila.ivIcono.setColorFilter(ContextCompat.getColor(requireContext(), colorSev(zona.severidad)))
        fila.tvZonaNombre.text = zona.nombre
        fila.tvZonaReportes.text = getString(R.string.admin_puntos_reportes, zona.focos)
        // Tocar la zona (lo que promete el chevron): enfoca el mapa en ella.
        fila.root.setOnClickListener { enfocarZona(zona) }
        return fila.root
    }

    /**
     * Enfoca el mapa en la zona tocada. Si la zona tiene varios focos (puede abarcar
     * un área grande, ej. un distrito), ajusta el zoom para que ENTREN TODOS en
     * pantalla (bounds); si tiene uno solo, acerca a ese punto.
     */
    private fun enfocarZona(zona: ZonaAfectada) {
        val m = mapa ?: return
        val focosZona = focos.filter { it.zona == zona.nombre }
        when {
            focosZona.size >= 2 -> {
                val bounds = LatLngBounds.builder()
                    .apply { focosZona.forEach { include(LatLng(it.latitud, it.longitud)) } }
                    .build()
                m.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 140))
            }
            focosZona.size == 1 ->
                m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(focosZona[0].latitud, focosZona[0].longitud), 16f))
            zona.latitud != 0.0 || zona.longitud != 0.0 ->
                m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(zona.latitud, zona.longitud), 15f))
        }
    }

    private fun hueSeveridad(severidad: Severidad) = when (severidad) {
        Severidad.ALTA -> BitmapDescriptorFactory.HUE_RED
        Severidad.MEDIA -> BitmapDescriptorFactory.HUE_ORANGE
        Severidad.BAJA -> BitmapDescriptorFactory.HUE_YELLOW
    }

    private fun colorSev(s: Severidad) = when (s) {
        Severidad.ALTA -> R.color.sev_alta
        Severidad.MEDIA -> R.color.sev_media
        Severidad.BAJA -> R.color.sev_baja
    }

    companion object {
        private val CHIMBOTE = LatLng(-9.0745, -78.5936)
    }
}
