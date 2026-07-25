package com.moviles.minkia.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.tabs.TabLayout
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseFragment
import com.moviles.minkia.core.UiState
import com.moviles.minkia.core.aplicarInsetSuperior
import com.moviles.minkia.core.mostrarSkeleton
import com.moviles.minkia.data.model.AlertaAdmin
import com.moviles.minkia.databinding.FragmentAdminAlertasBinding

/**
 * Bandeja de reportes entrantes del administrador (mockup A02). Lista las
 * alertas, filtra por pestaña y abre el detalle/validación al tocar una.
 */
class AlertasFragment : BaseFragment<FragmentAdminAlertasBinding>() {

    private val viewModel: AlertasViewModel by viewModels { AlertasViewModel.Factory() }
    private val adapter = AlertaAdapter { abrirValidacion(it) }

    private var todas: List<AlertaAdmin> = emptyList()
    private var primeraCarga = true

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentAdminAlertasBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.headerAlertasContenido.aplicarInsetSuperior()
        binding.rvAlertas.adapter = adapter

        binding.tabs.apply {
            addTab(newTab().setText(R.string.admin_alertas_tab_nuevos))
            addTab(newTab().setText(R.string.admin_alertas_tab_sin))
            addTab(newTab().setText(R.string.admin_alertas_tab_urgentes))
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) = filtrar(tab.position)
                override fun onTabUnselected(tab: TabLayout.Tab) = Unit
                override fun onTabReselected(tab: TabLayout.Tab) = Unit
            })
        }

        viewModel.uiState.observe(viewLifecycleOwner) { estado ->
            when (estado) {
                is UiState.Loading -> {
                    binding.skeletonAlertas.root.mostrarSkeleton(true)
                    binding.rvAlertas.visibility = View.GONE
                }
                is UiState.Success -> {
                    binding.skeletonAlertas.root.mostrarSkeleton(false)
                    binding.rvAlertas.visibility = View.VISIBLE
                    todas = estado.data
                    filtrar(binding.tabs.selectedTabPosition.coerceAtLeast(0))
                }
                is UiState.Error -> {
                    // Sin datos viejos disfrazados de válidos: la bandeja es el trabajo
                    // del admin, un fallo de red debe avisarse, no moderar a ciegas.
                    binding.skeletonAlertas.root.mostrarSkeleton(false)
                    binding.rvAlertas.visibility = View.GONE
                    binding.tvVacioAlertas.visibility = View.GONE
                    android.widget.Toast.makeText(
                        requireContext(), estado.mensaje, android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /** 0 = Nuevos (RECIBIDO), 1 = En proceso (ya validados), 2 = Urgentes (severidad ALTA). */
    private fun filtrar(posicion: Int) {
        val lista = when (posicion) {
            0 -> todas.filter { it.nuevo }
            1 -> todas.filter { !it.nuevo }
            2 -> todas.filter { it.severidad == com.moviles.minkia.data.model.Severidad.ALTA }
            else -> todas
        }
        binding.tvVacioAlertas.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
        adapter.submitList(lista) { binding.rvAlertas.scheduleLayoutAnimation() }
    }

    /**
     * Abre la Validación de la alerta tocada (A04). Antes viajaban trece extras
     * completos (toda AlertaAdmin); ahora solo su id, que YA es el id real del
     * documento en Firestore (a diferencia del ticket que usa el lado
     * ciudadano). ValidacionFragment le pide el resto a
     * ValidacionViewModel/AdminRepository.
     */
    private fun abrirValidacion(a: AlertaAdmin) {
        findNavController().navigate(R.id.action_alertas_a_validacion, Bundle().apply { putString("reporteId", a.id) })
    }

    /**
     * Al volver de validar/rechazar un reporte, recarga la bandeja para reflejar
     * el cambio (un reporte rechazado desaparece; uno validado cambia de estado).
     * Salta la primera vez porque el ViewModel ya carga en su init.
     */
    override fun onResume() {
        super.onResume()
        if (primeraCarga) {
            primeraCarga = false
            return
        }
        viewModel.cargar()
    }

    override fun onBindingDestroy() {
        binding.rvAlertas.adapter = null
    }
}
