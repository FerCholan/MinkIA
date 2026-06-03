package com.moviles.minkia.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.moviles.minkia.core.BaseViewModel
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.model.ResumenCiudadano
import com.moviles.minkia.data.repository.CiudadanoRepository

/**
 * ViewModel de la pantalla de inicio. Gracias a [BaseViewModel.loadInto] solo
 * declara qué dato pedir; el ciclo Loading/Success/Error lo resuelve la base.
 */
class HomeViewModel(
    private val repository: CiudadanoRepository = CiudadanoRepository()
) : BaseViewModel() {

    private val _uiState = MutableLiveData<UiState<ResumenCiudadano>>()
    val uiState: LiveData<UiState<ResumenCiudadano>> = _uiState

    init {
        cargarResumen()
    }

    fun cargarResumen() = loadInto(_uiState) { repository.obtenerResumen() }

    /** Factory para inyectar el repositorio sin librerías de DI. */
    class Factory(
        private val repository: CiudadanoRepository = CiudadanoRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository) as T
        }
    }
}
