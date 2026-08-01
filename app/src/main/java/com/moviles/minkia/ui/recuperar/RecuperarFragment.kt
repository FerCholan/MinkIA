package com.moviles.minkia.ui.recuperar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseFragment
import com.moviles.minkia.core.UiState
import com.moviles.minkia.core.aplicarInsetInferior
import com.moviles.minkia.core.aplicarInsetSuperior
import com.moviles.minkia.core.barrasCabeceraVerde
import com.moviles.minkia.core.mostrarError
import com.moviles.minkia.core.ocultarError
import com.moviles.minkia.databinding.FragmentRecuperarBinding

/**
 * Pantalla "Recuperar contraseña" (mockup C05), destino del grafo nav_auth.
 * Valida el correo, pide el envío del enlace y, al confirmarse, reemplaza el
 * formulario por un estado de éxito. Igual que el resto del flujo de acceso,
 * el fragment solo observa y pinta.
 */
class RecuperarFragment : BaseFragment<FragmentRecuperarBinding>() {

    private val viewModel: RecuperarViewModel by viewModels { RecuperarViewModel.Factory() }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentRecuperarBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.toolbar.aplicarInsetSuperior() // toolbar verde tras la status bar
        binding.recuperarRoot.aplicarInsetInferior() // el form esquiva la barra de gestos
        requireActivity().barrasCabeceraVerde()
        configurarAcciones()
        observarViewModel()
    }

    private fun configurarAcciones() {
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        binding.etEmail.doOnTextChanged { _, _, _, _ -> binding.tilEmail.error = null }

        binding.btnEnviar.setOnClickListener {
            viewModel.enviarEnlace(binding.etEmail.text?.toString().orEmpty())
        }

        // Cualquier "volver" regresa al login (de donde venimos): antes era
        // finish() de la Activity, ahora popBackStack() dentro del mismo grafo
        // (Login sigue apilado debajo, tal cual quedaba la Activity debajo).
        binding.btnVolverLogin.setOnClickListener { findNavController().popBackStack() }
        binding.btnVolverExito.setOnClickListener { findNavController().popBackStack() }
    }

    private fun observarViewModel() {
        // Se observa con viewLifecycleOwner (no con "this"/el fragment): el
        // fragment puede sobrevivir más que su vista, y observar con su propio
        // lifecycle arriesgaría tocar `binding` ya liberado (ver BaseFragment).
        viewModel.errorEmail.observe(viewLifecycleOwner) { error ->
            binding.tilEmail.error = error?.let { getString(it) }
        }

        viewModel.estado.observe(viewLifecycleOwner) { estado ->
            when (estado) {
                is UiState.Loading -> {
                    mostrarCargando(true)
                    binding.bannerError.ocultarError()
                }
                is UiState.Success -> mostrarExito()
                is UiState.Error -> {
                    mostrarCargando(false)
                    binding.bannerError.mostrarError(estado.mensaje)
                }
            }
        }
    }

    private fun mostrarCargando(cargando: Boolean) {
        binding.btnEnviar.isEnabled = !cargando
        binding.btnEnviar.setText(
            if (cargando) R.string.recuperar_enviando else R.string.recuperar_boton
        )
    }

    private fun mostrarExito() {
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        binding.tvExitoMensaje.text = getString(R.string.recuperar_exito_mensaje, email)
        binding.grupoForm.visibility = View.GONE
        binding.grupoExito.visibility = View.VISIBLE
    }
}
