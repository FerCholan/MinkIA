package com.moviles.minkia.ui.perfil

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.moviles.minkia.core.BaseViewModel
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.model.PerfilCiudadano
import com.moviles.minkia.data.repository.CiudadanoRepository

/**
 * ViewModel del perfil (mockup C17). Pide el perfil al repositorio y expone el
 * ciclo Loading/Success/Error vía [BaseViewModel.loadInto]. La vista solo pinta.
 */
class PerfilViewModel(
    private val repository: CiudadanoRepository = CiudadanoRepository()
) : BaseViewModel() {

    private val _uiState = MutableLiveData<UiState<PerfilCiudadano>>()
    val uiState: LiveData<UiState<PerfilCiudadano>> = _uiState

    init {
        cargar()
    }

    fun cargar() = loadInto(_uiState) { repository.obtenerPerfil() }

    /** Factory para inyectar el repositorio sin librerías de DI. */
    class Factory(
        private val repository: CiudadanoRepository = CiudadanoRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PerfilViewModel(repository) as T
        }
    }
}
