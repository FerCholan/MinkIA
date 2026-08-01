package com.moviles.minkia.ui.registro

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
 * Errores de validación del formulario de registro. Cada campo lleva el recurso
 * de texto a mostrar (o null si es válido); [terminos] es true cuando falta
 * aceptar los términos.
 */
data class ErroresRegistro(
    @StringRes val nombre: Int? = null,
    @StringRes val email: Int? = null,
    @StringRes val dni: Int? = null,
    @StringRes val password: Int? = null,
    val terminos: Boolean = false
) {
    val hayErrores: Boolean
        get() = nombre != null || email != null || dni != null ||
            password != null || terminos
}

/**
 * ViewModel de la pantalla de registro (mockup C04). Concentra la validación de
 * formato y el ciclo de creación de cuenta. La Activity solo observa y pinta.
 * Las reglas de validación son Kotlin puro (sin Android) para poder testearlas
 * sin emulador.
 */
class RegistroViewModel(
    private val repository: AuthRepository = AuthRepository()
) : BaseViewModel() {

    private val _estado = MutableLiveData<UiState<Usuario>>()
    val estado: LiveData<UiState<Usuario>> = _estado

    private val _errores = MutableLiveData<ErroresRegistro>()
    val errores: LiveData<ErroresRegistro> = _errores

    /**
     * Valida el formulario y, si está bien, crea la cuenta. Devuelve temprano
     * publicando los errores de campo cuando algo no cumple, sin tocar el
     * repositorio.
     */
    fun registrar(
        nombre: String,
        email: String,
        dni: String,
        password: String,
        aceptaTerminos: Boolean
    ) {
        val nombreLimpio = nombre.trim()
        val emailLimpio = email.trim()
        val dniLimpio = dni.trim()

        val erroresEncontrados = validar(nombreLimpio, emailLimpio, dniLimpio, password, aceptaTerminos)
        _errores.value = erroresEncontrados
        if (erroresEncontrados.hayErrores) return

        loadInto(_estado) {
            repository.registrar(nombreLimpio, emailLimpio, dniLimpio, password)
        }
    }

    /** Registro/login con Google (la primera vez crea la cuenta). */
    fun iniciarSesionConGoogle(idToken: String) =
        loadInto(_estado) { repository.iniciarSesionConGoogle(idToken) }

    private fun validar(
        nombre: String,
        email: String,
        dni: String,
        password: String,
        aceptaTerminos: Boolean
    ) = ErroresRegistro(
        nombre = when {
            nombre.isBlank() -> R.string.registro_error_nombre_vacio
            nombre.length < MIN_NOMBRE -> R.string.registro_error_nombre_corto
            else -> null
        },
        email = when {
            email.isBlank() -> R.string.registro_error_email_vacio
            !EMAIL_REGEX.matches(email) -> R.string.registro_error_email_invalido
            else -> null
        },
        // DNI es opcional: solo se valida si el usuario escribió algo.
        dni = when {
            dni.isBlank() -> null
            !DNI_REGEX.matches(dni) -> R.string.registro_error_dni_invalido
            else -> null
        },
        password = when {
            password.isBlank() -> R.string.registro_error_password_vacio
            password.length < MIN_PASSWORD -> R.string.registro_error_password_corto
            else -> null
        },
        terminos = !aceptaTerminos
    )

    /** Factory para inyectar el repositorio sin librerías de DI. */
    class Factory(
        private val repository: AuthRepository = AuthRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RegistroViewModel(repository) as T
        }
    }

    companion object {
        private const val MIN_NOMBRE = 3
        private const val MIN_PASSWORD = 6
        private val EMAIL_REGEX = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
        private val DNI_REGEX = Regex("\\d{8}")
    }
}
