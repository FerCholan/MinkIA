package com.moviles.minkia.ui.registro

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseActivity
import com.moviles.minkia.core.UiState
import com.moviles.minkia.core.mostrarError
import com.moviles.minkia.core.ocultarError
import com.moviles.minkia.core.transicionFundido
import com.moviles.minkia.data.local.PreferenciasRepository
import com.moviles.minkia.data.model.Usuario
import com.moviles.minkia.databinding.ActivityRegistroBinding
import com.moviles.minkia.ui.permisos.RuteoPermisos
import kotlinx.coroutines.launch

/**
 * Pantalla de registro (mockup C04). Igual que el login, observa dos flujos del
 * [RegistroViewModel] (errores de campo y estado de la creación de cuenta) y se
 * limita a leer los campos, disparar la acción y pintar cada estado. La barra
 * superior permite volver al login.
 */
class RegistroActivity : BaseActivity<ActivityRegistroBinding>() {

    private val viewModel: RegistroViewModel by viewModels { RegistroViewModel.Factory() }
    private val prefs by lazy { PreferenciasRepository.create(this) }

    override fun inflateBinding(inflater: LayoutInflater) = ActivityRegistroBinding.inflate(inflater)

    override fun onViewReady(savedInstanceState: Bundle?) {
        aplicarInsetSuperior(binding.toolbar) // toolbar verde tras la status bar
        aplicarInsetInferior(binding.registroRoot) // el form esquiva la barra de gestos
        barrasCabeceraVerde()
        configurarEntradas()
        configurarAcciones()
        observarViewModel()
    }

    private fun configurarEntradas() {
        // Limpia el error del campo apenas el usuario empieza a corregirlo.
        binding.etNombre.doOnTextChanged { _, _, _, _ -> binding.tilNombre.error = null }
        binding.etEmail.doOnTextChanged { _, _, _, _ -> binding.tilEmail.error = null }
        binding.etDni.doOnTextChanged { _, _, _, _ -> binding.tilDni.error = null }
        binding.etPassword.doOnTextChanged { _, _, _, _ -> binding.tilPassword.error = null }
        binding.cbTerminos.setOnCheckedChangeListener { _, marcado ->
            if (marcado) binding.tvErrorTerminos.visibility = View.GONE
        }
    }

    private fun configurarAcciones() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnRegistrar.setOnClickListener {
            viewModel.registrar(
                nombre = binding.etNombre.text?.toString().orEmpty(),
                email = binding.etEmail.text?.toString().orEmpty(),
                dni = binding.etDni.text?.toString().orEmpty(),
                password = binding.etPassword.text?.toString().orEmpty(),
                aceptaTerminos = binding.cbTerminos.isChecked
            )
        }

        // Volver al login (de donde venimos) en lugar de apilar otra Activity.
        binding.btnIniciaSesion.setOnClickListener { finish() }
    }

    private fun observarViewModel() {
        viewModel.errores.observe(this) { errores ->
            binding.tilNombre.error = errores.nombre?.let { getString(it) }
            binding.tilEmail.error = errores.email?.let { getString(it) }
            binding.tilDni.error = errores.dni?.let { getString(it) }
            binding.tilPassword.error = errores.password?.let { getString(it) }
            binding.tvErrorTerminos.visibility = if (errores.terminos) View.VISIBLE else View.GONE
        }

        viewModel.estado.observe(this) { estado ->
            when (estado) {
                is UiState.Loading -> {
                    mostrarCargando(true)
                    binding.bannerError.ocultarError()
                }
                is UiState.Success -> entrar(estado.data)
                is UiState.Error -> {
                    mostrarCargando(false)
                    binding.bannerError.mostrarError(estado.mensaje)
                }
            }
        }
    }

    private fun mostrarCargando(cargando: Boolean) {
        binding.btnRegistrar.isEnabled = !cargando
        binding.btnRegistrar.setText(
            if (cargando) R.string.registro_registrando else R.string.registro_boton
        )
    }

    private fun entrar(usuario: Usuario) {
        // Sin Toast de bienvenida (UX moderna): la app saluda en Inicio.
        // Cuenta recién creada: siempre es primer arranque, así que Permisos se
        // mostrará. Igual lo decide RuteoPermisos para mantener una sola regla.
        lifecycleScope.launch { irA(RuteoPermisos.destinoCiudadano(this@RegistroActivity, prefs)) }
    }

    private fun irA(destino: Class<*>) {
        startActivity(Intent(this, destino))
        transicionFundido()
        finishAffinity() // cierra el flujo previo; el destino queda arriba.
    }
}
