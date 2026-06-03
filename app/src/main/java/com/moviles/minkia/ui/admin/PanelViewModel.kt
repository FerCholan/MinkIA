package com.moviles.minkia.ui.admin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.moviles.minkia.core.BaseViewModel
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.model.PanelAdmin
import com.moviles.minkia.data.repository.AdminRepository

/**
 * ViewModel del panel del administrador (mockup A01). Pide los KPIs y zonas al
 * repositorio y expone el ciclo Loading/Success/Error. La vista solo pinta.
 */
class PanelViewModel(
    private val repository: AdminRepository = AdminRepository()
) : BaseViewModel() {

    private val _uiState = MutableLiveData<UiState<PanelAdmin>>()
    val uiState: LiveData<UiState<PanelAdmin>> = _uiState

    init {
        cargar()
    }

    fun cargar() = loadInto(_uiState) { repository.panel() }

    /** Factory para inyectar el repositorio sin librerías de DI. */
    class Factory(
        private val repository: AdminRepository = AdminRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PanelViewModel(repository) as T
        }
    }
}
