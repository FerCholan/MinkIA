package com.moviles.minkia.ui.login

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseViewModel
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.model.Usuario
import com.moviles.minkia.data.repository.AuthRepository

/**
 * Errores de validación de formato del formulario de login. Cada campo lleva el
 * recurso de texto a mostrar, o null si es válido.
 */
data class ErroresLogin(
    @StringRes val email: Int? = null,
    @StringRes val password: Int? = null
) {
    val hayErrores: Boolean get() = email != null || password != null
}

/**
 * ViewModel de la pantalla de login (mockup C03). Concentra la validación de
 * formato y el ciclo de autenticación. La Activity solo observa y pinta: no
 * decide nada. Las reglas de validación son Kotlin puro (sin Android) para que
 * se puedan testear sin emulador.
 */
class LoginViewModel(
    private val repository: AuthRepository = AuthRepository()
) : BaseViewModel() {

    private val _estado = MutableLiveData<UiState<Usuario>>()
    val estado: LiveData<UiState<Usuario>> = _estado

    private val _errores = MutableLiveData<ErroresLogin>()
    val errores: LiveData<ErroresLogin> = _errores

    /**
     * Valida el formulario y, si está bien, dispara la autenticación. Devuelve
     * temprano publicando los errores de campo cuando el formato no es válido,
     * sin llegar a tocar el repositorio.
     */
    fun iniciarSesion(email: String, password: String) {
        val limpio = email.trim()
        val erroresEncontrados = validar(limpio, password)
        _errores.value = erroresEncontrados
        if (erroresEncontrados.hayErrores) return

        loadInto(_estado) { repository.iniciarSesion(limpio, password) }
    }

    /** Cambia el ID token de Google por una sesión (la primera vez crea la cuenta). */
    fun iniciarSesionConGoogle(idToken: String) =
        loadInto(_estado) { repository.iniciarSesionConGoogle(idToken) }

    private fun validar(email: String, password: String): ErroresLogin = ErroresLogin(
        email = when {
            email.isBlank() -> R.string.login_error_email_vacio
            !EMAIL_REGEX.matches(email) -> R.string.login_error_email_invalido
            else -> null
        },
        password = when {
            password.isBlank() -> R.string.login_error_password_vacio
            password.length < MIN_PASSWORD -> R.string.login_error_password_corto
            else -> null
        }
    )

    /** Factory para inyectar el repositorio sin librerías de DI. */
    class Factory(
        private val repository: AuthRepository = AuthRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LoginViewModel(repository) as T
        }
    }

    companion object {
        private const val MIN_PASSWORD = 6
        private val EMAIL_REGEX = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
    }
}
