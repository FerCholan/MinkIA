package com.moviles.minkia.ui.login

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
import com.moviles.minkia.databinding.ActivityLoginBinding
import com.moviles.minkia.ui.permisos.RuteoPermisos
import kotlinx.coroutines.launch

/**
 * Pantalla de login (mockup C03). Observa dos flujos del [LoginViewModel]: los
 * errores de validación de campo y el estado de la autenticación. No contiene
 * reglas de negocio: solo lee los campos, dispara la acción y pinta cada estado.
 */
class LoginActivity : BaseActivity<ActivityLoginBinding>() {

    private val viewModel: LoginViewModel by viewModels { LoginViewModel.Factory() }
    private val prefs by lazy { PreferenciasRepository.create(this) }

    override fun inflateBinding(inflater: LayoutInflater) = ActivityLoginBinding.inflate(inflater)

    override fun onViewReady(savedInstanceState: Bundle?) {
        aplicarInsetsVerticales(binding.loginRoot)
        barraEstadoOscura() // fondo claro (edge-to-edge ya viene de BaseActivity)
        configurarEntradas()
        configurarAcciones()
        observarViewModel()
    }

    private fun configurarEntradas() {
        // Limpia el error del campo (y el banner) apenas el usuario empieza a corregir.
        binding.etEmail.doOnTextChanged { _, _, _, _ -> binding.tilEmail.error = null; ocultarBanner() }
        binding.etPassword.doOnTextChanged { _, _, _, _ -> binding.tilPassword.error = null; ocultarBanner() }
    }

    private fun configurarAcciones() {
        binding.btnIniciar.setOnClickListener {
            viewModel.iniciarSesion(
                email = binding.etEmail.text?.toString().orEmpty(),
                password = binding.etPassword.text?.toString().orEmpty()
            )
        }

        binding.btnOlvidaste.setOnClickListener {
            startActivity(Intent(this, com.moviles.minkia.ui.recuperar.RecuperarActivity::class.java))
            transicionFundido()
        }
        binding.btnRegistrate.setOnClickListener {
            startActivity(Intent(this, com.moviles.minkia.ui.registro.RegistroActivity::class.java))
            transicionFundido()
        }
        // TODO: integrar Google Sign-In cuando haya backend de autenticación.
        binding.btnGoogle.setOnClickListener { avisarProximamente() }
    }

    private fun observarViewModel() {
        viewModel.errores.observe(this) { errores ->
            binding.tilEmail.error = errores.email?.let { getString(it) }
            binding.tilPassword.error = errores.password?.let { getString(it) }
        }

        viewModel.estado.observe(this) { estado ->
            when (estado) {
                is UiState.Loading -> {
                    mostrarCargando(true)
                    ocultarBanner()
                }
                is UiState.Success -> entrar(estado.data)
                is UiState.Error -> {
                    mostrarCargando(false)
                    mostrarErrorBanner(estado.mensaje)
                }
            }
        }
    }

    private fun mostrarCargando(cargando: Boolean) {
        binding.btnIniciar.isEnabled = !cargando
        binding.btnGoogle.isEnabled = !cargando
        binding.btnIniciar.setText(
            if (cargando) R.string.login_ingresando else R.string.login_iniciar
        )
    }

    private fun entrar(usuario: Usuario) {
        // Sin Toast de bienvenida: la app ya saluda en Inicio (UX moderna).
        // Ruteo por rol: el administrador va directo a su panel. El ciudadano va
        // a Permisos solo la primera vez (lo decide RuteoPermisos); si no, a la app.
        if (usuario.esAdmin) {
            irA(com.moviles.minkia.ui.admin.AdminMainActivity::class.java)
        } else {
            lifecycleScope.launch { irA(RuteoPermisos.destinoCiudadano(this@LoginActivity, prefs)) }
        }
    }

    private fun irA(destino: Class<*>) {
        startActivity(Intent(this, destino))
        transicionFundido()
        finishAffinity() // cierra el flujo previo; el destino queda arriba.
    }

    private fun mostrarErrorBanner(mensaje: String) = binding.bannerError.mostrarError(mensaje)

    private fun ocultarBanner() = binding.bannerError.ocultarError()

    private fun avisarProximamente() {
        Toast.makeText(this, R.string.login_proximamente, Toast.LENGTH_SHORT).show()
    }
}
