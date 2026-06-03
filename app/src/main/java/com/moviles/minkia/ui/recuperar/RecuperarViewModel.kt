package com.moviles.minkia.ui.recuperar

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseViewModel
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.repository.AuthRepository

/**
 * ViewModel de "Recuperar contraseña" (mockup C05). Valida el formato del
 * correo y dispara el envío del enlace. La Activity solo observa y pinta. La
 * regla de validación es Kotlin puro (sin Android) para poder testearla sin
 * emulador.
 */
class RecuperarViewModel(
    private val repository: AuthRepository = AuthRepository()
) : BaseViewModel() {

    private val _estado = MutableLiveData<UiState<Unit>>()
    val estado: LiveData<UiState<Unit>> = _estado

    private val _errorEmail = MutableLiveData<Int?>()
    val errorEmail: LiveData<Int?> = _errorEmail

    /**
     * Valida el correo y, si el formato es correcto, pide el envío del enlace.
     * Devuelve temprano publicando el error de campo cuando no es válido.
     */
    fun enviarEnlace(email: String) {
        val limpio = email.trim()
        val error = validar(limpio)
        _errorEmail.value = error
        if (error != null) return

        loadInto(_estado) { repository.recuperarPassword(limpio) }
    }

    @StringRes
    private fun validar(email: String): Int? = when {
        email.isBlank() -> R.string.recuperar_error_email_vacio
        !EMAIL_REGEX.matches(email) -> R.string.recuperar_error_email_invalido
        else -> null
    }

    /** Factory para inyectar el repositorio sin librerías de DI. */
    class Factory(
        private val repository: AuthRepository = AuthRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RecuperarViewModel(repository) as T
        }
    }

    companion object {
        private val EMAIL_REGEX = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
    }
}
