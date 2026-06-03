package com.moviles.minkia.ui.perfil

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.TooltipCompat
import androidx.fragment.app.viewModels
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseFragment
import com.moviles.minkia.core.UiState
import com.moviles.minkia.core.animarEntrada
import com.moviles.minkia.core.aplicarInsetSuperior
import com.moviles.minkia.core.mostrarSkeleton
import com.moviles.minkia.core.recortarEsquinasInferiores
import com.moviles.minkia.data.model.PerfilCiudadano
import com.moviles.minkia.databinding.FragmentPerfilBinding
import com.moviles.minkia.ui.configuracion.ConfiguracionActivity
import com.moviles.minkia.ui.login.LoginActivity

/**
 * Perfil y Minka digital del ciudadano (mockup C17). Muestra el avance de
 * gamificación (puntos, nivel, insignias) y el menú de cuenta. Los datos llegan
 * del repositorio vía [PerfilViewModel]; la vista solo observa y pinta.
 */
class PerfilFragment : BaseFragment<FragmentPerfilBinding>() {

    private val viewModel: PerfilViewModel by viewModels { PerfilViewModel.Factory() }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentPerfilBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.headerPerfilContenido.aplicarInsetSuperior()
        binding.headerPerfil.recortarEsquinasInferiores()
        binding.btnMisReportes.setOnClickListener {
            requireActivity().findViewById<BottomNavigationView>(R.id.bottomNav)
                .selectedItemId = R.id.nav_reportes
        }
        binding.btnConfig.setOnClickListener {
            startActivity(Intent(requireContext(), ConfiguracionActivity::class.java))
        }
        binding.btnCerrar.setOnClickListener { cerrarSesion() }

        TooltipCompat.setTooltipText(binding.btnEditar, getString(R.string.tooltip_editar_perfil))
        binding.btnEditar.setOnClickListener {
            Toast.makeText(requireContext(), R.string.login_proximamente, Toast.LENGTH_SHORT).show()
        }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is UiState.Loading -> {
                    binding.skeletonPerfil.mostrarSkeleton(true)
                    binding.scrollPerfil.visibility = View.GONE
                }
                is UiState.Success -> {
                    binding.skeletonPerfil.mostrarSkeleton(false)
                    binding.scrollPerfil.visibility = View.VISIBLE
                    mostrarPerfil(state.data)
                }
                is UiState.Error -> {
                    binding.skeletonPerfil.mostrarSkeleton(false)
                    binding.scrollPerfil.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun mostrarPerfil(perfil: PerfilCiudadano) {
        binding.tvIniciales.text = perfil.iniciales
        binding.tvNombre.text = perfil.nombre
        binding.tvRol.text = perfil.rol
        binding.tvReportes.text = perfil.reportes.toString()
        binding.tvResueltos.text = perfil.resueltos.toString()
        binding.tvPuntosMinka.text = perfil.puntosMinka.toString()
        binding.tvNivel.text = perfil.nivelTexto
        binding.tvProgreso.text = "${perfil.puntosNivelActual} / ${perfil.puntosNivelObjetivo}"
        binding.progressMinka.max = perfil.puntosNivelObjetivo
        binding.progressMinka.progress = perfil.puntosNivelActual
        binding.tvFaltan.text = perfil.faltanTexto

        binding.contenidoPerfil.animarEntrada()
    }

    private fun cerrarSesion() {
        startActivity(
            Intent(requireContext(), LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        requireActivity().finish()
    }
}
