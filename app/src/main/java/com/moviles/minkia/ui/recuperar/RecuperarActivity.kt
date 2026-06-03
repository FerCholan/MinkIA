package com.moviles.minkia.ui.recuperar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.widget.doOnTextChanged
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseActivity
import com.moviles.minkia.core.UiState
import com.moviles.minkia.core.mostrarError
import com.moviles.minkia.core.ocultarError
import com.moviles.minkia.databinding.ActivityRecuperarBinding

/**
 * Pantalla "Recuperar contraseña" (mockup C05). Valida el correo, pide el envío
 * del enlace y, al confirmarse, reemplaza el formulario por un estado de éxito.
 * Igual que el resto del flujo de acceso, la Activity solo observa y pinta.
 */
class RecuperarActivity : BaseActivity<ActivityRecuperarBinding>() {

    private val viewModel: RecuperarViewModel by viewModels { RecuperarViewModel.Factory() }

    override fun inflateBinding(inflater: LayoutInflater) = ActivityRecuperarBinding.inflate(inflater)

    override fun onViewReady(savedInstanceState: Bundle?) {
        aplicarInsetSuperior(binding.toolbar) // toolbar verde tras la status bar
        aplicarInsetInferior(binding.recuperarRoot) // el form esquiva la barra de gestos
        barrasCabeceraVerde()
        configurarAcciones()
        observarViewModel()
    }

    private fun configurarAcciones() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.etEmail.doOnTextChanged { _, _, _, _ -> binding.tilEmail.error = null }

        binding.btnEnviar.setOnClickListener {
            viewModel.enviarEnlace(binding.etEmail.text?.toString().orEmpty())
        }

        // Cualquier "volver" cierra esta pantalla y regresa al login (de donde venimos).
        binding.btnVolverLogin.setOnClickListener { finish() }
        binding.btnVolverExito.setOnClickListener { finish() }
    }

    private fun observarViewModel() {
        viewModel.errorEmail.observe(this) { error ->
            binding.tilEmail.error = error?.let { getString(it) }
        }

        viewModel.estado.observe(this) { estado ->
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
