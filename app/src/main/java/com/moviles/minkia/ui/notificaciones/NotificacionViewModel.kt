package com.moviles.minkia.ui.notificaciones

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.moviles.minkia.core.BaseViewModel
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.model.Notificacion
import com.moviles.minkia.data.repository.CiudadanoRepository

/**
 * ViewModel de notificaciones (mockup C16). Carga la lista que la Activity
 * agrupa por sección (HOY / ESTA SEMANA).
 */
class NotificacionViewModel(
    private val repository: CiudadanoRepository = CiudadanoRepository()
) : BaseViewModel() {

    private val _uiState = MutableLiveData<UiState<List<Notificacion>>>()
    val uiState: LiveData<UiState<List<Notificacion>>> = _uiState

    init {
        cargar()
    }

    fun cargar() = loadInto(_uiState) { repository.notificaciones() }

    class Factory(
        private val repository: CiudadanoRepository = CiudadanoRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NotificacionViewModel(repository) as T
        }
    }
}
