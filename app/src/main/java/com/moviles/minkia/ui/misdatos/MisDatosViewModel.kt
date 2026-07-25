package com.moviles.minkia.ui.misdatos

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseViewModel
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.repository.PerfilRepository

/** DNI, apodo y preferencia de privacidad tal como están guardados hoy en el perfil. */
data class DatosPersonales(
    val dni: String,
    val apodo: String,
    val usarApodo: Boolean
)

/**
 * Errores de validación del formulario "Mis datos". Cada campo lleva el recurso
 * de texto a mostrar, o null si es válido. [apodo] también se usa para el aviso
 * de apodo ya ocupado, que solo se conoce tras consultar Firestore.
 */
data class ErroresMisDatos(
    @StringRes val dni: Int? = null,
    @StringRes val apodo: Int? = null
) {
    val hayErrores: Boolean get() = dni != null || apodo != null
}

/** Resultado de intentar guardar: el apodo puede estar ocupado por otro vecino. */
sealed interface ResultadoGuardar {
    data object Guardado : ResultadoGuardar
    data object ApodoOcupado : ResultadoGuardar
}

/**
 * ViewModel de "Mis datos" (mockup C17b). Carga el DNI/apodo/preferencia actuales
 * y guarda los cambios: valida el formato localmente antes de tocar Firestore y
 * confirma la unicidad del apodo antes de escribirlo. La Activity solo lee los
 * campos del formulario y observa los tres estados que expone.
 */
class MisDatosViewModel(
    private val repository: PerfilRepository = PerfilRepository()
) : BaseViewModel() {

    private val _datos = MutableLiveData<UiState<DatosPersonales>>()
    val datos: LiveData<UiState<DatosPersonales>> = _datos

    private val _errores = MutableLiveData<ErroresMisDatos>()
    val errores: LiveData<ErroresMisDatos> = _errores

    private val _estado = MutableLiveData<UiState<ResultadoGuardar>>()
    val estado: LiveData<UiState<ResultadoGuardar>> = _estado

    init {
        cargar()
    }

    /**
     * Cada campo se resuelve por separado con su propio valor por defecto: perfil
     * progresivo, ningún dato es indispensable, así que si Firestore falla en uno
     * los demás igual se muestran.
     */
    fun cargar() = loadInto(_datos) {
        DatosPersonales(
            dni = runCatching { repository.obtenerDni() }.getOrDefault(""),
            apodo = runCatching { repository.obtenerNickname() }.getOrDefault(""),
            usarApodo = runCatching { repository.obtenerUsarApodo() }.getOrDefault(false)
        )
    }

    /**
     * Valida formato localmente (sin red) y corta ahí si algo no cumple. Si pasa,
     * confirma que el apodo siga libre y recién ahí guarda los tres campos. Que el
     * apodo esté ocupado no es un error de red: se resuelve como
     * [ResultadoGuardar.ApodoOcupado] para que la vista lo muestre como error de
     * campo, igual que las validaciones locales, y no como aviso genérico.
     */
    fun guardar(dni: String, apodo: String, usarApodo: Boolean) {
        val erroresLocales = validar(dni, apodo, usarApodo)
        _errores.value = erroresLocales
        if (erroresLocales.hayErrores) return

        loadInto(_estado) {
            if (apodo.isNotEmpty() && !repository.nicknameDisponible(apodo)) {
                ResultadoGuardar.ApodoOcupado
            } else {
                repository.guardarDni(dni)
                repository.guardarNickname(apodo)
                repository.guardarUsarApodo(usarApodo && apodo.isNotEmpty())
                ResultadoGuardar.Guardado
            }
        }
    }

    /** Mismo orden y mismos mensajes que la validación original: corta en el primero que falle. */
    private fun validar(dni: String, apodo: String, usarApodo: Boolean): ErroresMisDatos {
        if (dni.isNotEmpty() && !DNI_REGEX.matches(dni)) {
            return ErroresMisDatos(dni = R.string.registro_error_dni_invalido)
        }
        if (apodo.isNotEmpty() && !APODO_REGEX.matches(apodo)) {
            return ErroresMisDatos(apodo = R.string.editar_nickname_invalido)
        }
        // No se puede activar "usar apodo" sin un apodo válido cargado.
        if (usarApodo && apodo.isEmpty()) {
            return ErroresMisDatos(apodo = R.string.mis_datos_apodo_falta)
        }
        return ErroresMisDatos()
    }

    /** Factory para inyectar el repositorio sin librerías de DI. */
    class Factory(
        private val repository: PerfilRepository = PerfilRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MisDatosViewModel(repository) as T
        }
    }

    companion object {
        private val DNI_REGEX = Regex("\\d{8}")
        private val APODO_REGEX = Regex("[A-Za-z0-9_]{3,20}")
    }
}
