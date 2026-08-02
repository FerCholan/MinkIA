package com.moviles.minkia.ui.reportes

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.moviles.minkia.core.BaseViewModel
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.model.MiReporte
import com.moviles.minkia.data.repository.CiudadanoRepository

/**
 * ViewModel del historial de reportes (C13). Carga la lista; el filtrado por
 * pestaña lo resuelve la vista sobre los datos ya cargados.
 */
class ReportesViewModel(
    private val repository: CiudadanoRepository = CiudadanoRepository()
) : BaseViewModel() {

    private val _uiState = MutableLiveData<UiState<List<MiReporte>>>()
    val uiState: LiveData<UiState<List<MiReporte>>> = _uiState

    // Sin init { cargar() }: la carga la pide la vista en su onResume, un solo
    // camino para entrar y para volver. El porqué está en HomeViewModel.

    fun cargar() = loadInto(_uiState) { repository.misReportes() }

    class Factory(
        private val repository: CiudadanoRepository = CiudadanoRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReportesViewModel(repository) as T
        }
    }
}
