package com.moviles.minkia.ui.mapa

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.moviles.minkia.core.BaseViewModel
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.model.FocoMapa
import com.moviles.minkia.data.repository.CiudadanoRepository

/**
 * ViewModel del mapa de focos (mockup C08). Pide al repositorio los focos activos
 * de la comunidad (Firestore) y publica el ciclo Loading/Success/Error; la vista
 * solo pinta los marcadores cuando el mapa y los datos ya están listos.
 */
class MapaViewModel(
    private val repository: CiudadanoRepository = CiudadanoRepository()
) : BaseViewModel() {

    private val _uiState = MutableLiveData<UiState<List<FocoMapa>>>()
    val uiState: LiveData<UiState<List<FocoMapa>>> = _uiState

    // Sin init { cargar() }: la carga la pide la vista en su onResume, un solo
    // camino para entrar y para volver. El porqué está en HomeViewModel.

    fun cargar() = loadInto(_uiState) { repository.focosMapa() }

    /** Factory para inyectar el repositorio sin librerías de DI. */
    class Factory(
        private val repository: CiudadanoRepository = CiudadanoRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MapaViewModel(repository) as T
        }
    }
}
