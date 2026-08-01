package com.moviles.minkia.ui.perfil

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseFragment
import com.moviles.minkia.core.UiState
import com.moviles.minkia.core.animarEntrada
import com.moviles.minkia.core.aplicarInsetSuperior
import com.moviles.minkia.core.mostrarSkeleton
import com.moviles.minkia.core.recortarEsquinasInferiores
import com.moviles.minkia.data.local.PreferenciasRepository
import com.moviles.minkia.data.model.PerfilCiudadano
import com.moviles.minkia.data.repository.PerfilRepository
import com.moviles.minkia.databinding.FragmentPerfilBinding
import com.moviles.minkia.ui.auth.AuthActivity
import kotlinx.coroutines.launch

/**
 * Perfil y Minka digital del ciudadano (mockup C17). Muestra el avance de
 * gamificación (puntos, nivel, insignias) y el menú de cuenta. Los datos llegan
 * del repositorio vía [PerfilViewModel]; la vista solo observa y pinta.
 */
class PerfilFragment : BaseFragment<FragmentPerfilBinding>() {

    private val viewModel: PerfilViewModel by viewModels { PerfilViewModel.Factory() }
    private val prefs by lazy { PreferenciasRepository.create(requireContext()) }
    private val perfilRepo by lazy { PerfilRepository() }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentPerfilBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.headerPerfilContenido.aplicarInsetSuperior()
        binding.headerPerfil.recortarEsquinasInferiores()
        binding.btnConfig.setOnClickListener {
            findNavController().navigate(R.id.action_perfil_a_configuracion)
        }
        binding.btnCerrar.setOnClickListener { cerrarSesion() }
        binding.btnEditarPerfil.setOnClickListener {
            findNavController().navigate(R.id.action_perfil_a_mis_datos)
        }

        cargarDni()

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
                    // NO mostrar el scroll: el XML trae placeholders (8/5/340) que se
                    // verían como estadísticas reales del vecino. Mejor avisar del fallo.
                    binding.skeletonPerfil.mostrarSkeleton(false)
                    binding.scrollPerfil.visibility = View.GONE
                    android.widget.Toast.makeText(
                        requireContext(), state.mensaje, android.widget.Toast.LENGTH_LONG
                    ).show()
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

        pintarInsignias(perfil.nivel)
        binding.contenidoPerfil.animarEntrada()
    }

    /**
     * Pinta las 5 insignias de nivel: las alcanzadas (<= nivel actual) en full color,
     * las futuras atenuadas (preview de desbloqueo). Muestra cuál es la próxima.
     */
    private fun pintarInsignias(nivel: Int) {
        val insignias = listOf(
            binding.badgeNivel1, binding.badgeNivel2, binding.badgeNivel3,
            binding.badgeNivel4, binding.badgeNivel5
        )
        insignias.forEachIndexed { indice, insignia ->
            insignia.alpha = if (indice + 1 <= nivel) 1f else 0.28f
        }

        val nombres = listOf(
            R.string.nivel_1_nombre, R.string.nivel_2_nombre, R.string.nivel_3_nombre,
            R.string.nivel_4_nombre, R.string.nivel_5_nombre
        )
        binding.tvProximo.text = if (nivel >= nombres.size) {
            getString(R.string.perfil_nivel_max)
        } else {
            // nivel es 1-based; nombres[nivel] es el nombre del nivel siguiente.
            getString(R.string.perfil_proximo_fmt, getString(nombres[nivel]))
        }
    }

    /** Muestra el DNI real en el header (o "sin registrar" si el vecino no lo cargó). */
    private fun cargarDni() {
        viewLifecycleOwner.lifecycleScope.launch {
            val dni = try { perfilRepo.obtenerDni() } catch (e: Exception) { "" }
            binding.tvDni.text = if (dni.isBlank()) getString(R.string.perfil_dni_vacio)
            else getString(R.string.perfil_dni_fmt, dni)
        }
    }

    /** Refresca el DNI del header al volver de "Mis datos". */
    override fun onResume() {
        super.onResume()
        cargarDni()
    }

    private fun cerrarSesion() {
        lifecycleScope.launch {
            prefs.cerrarSesion() // borra la sesión local (DataStore)
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut() // y la de Firebase
            // Va directo a Login dentro del grafo de auth (sin pasar por
            // onboarding): mismo comportamiento que antes al lanzar LoginActivity
            // directamente. Ver AuthActivity.configurarDestinoInicial().
            startActivity(
                Intent(requireContext(), AuthActivity::class.java)
                    .putExtra(AuthActivity.EXTRA_OMITIR_ONBOARDING, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            requireActivity().finish()
        }
    }
}
