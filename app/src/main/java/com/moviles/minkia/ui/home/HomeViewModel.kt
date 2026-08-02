package com.moviles.minkia.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.moviles.minkia.core.BaseViewModel
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.model.FocoMapa
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

    /**
     * Focos para el mapa de calor de vista previa. Va aparte del resumen porque
     * son dos lecturas distintas de Firestore y la tarjeta del mapa se pinta
     * cuando el mapa termina de cargar, sin esperar al resto de la pantalla.
     */
    private val _focosState = MutableLiveData<UiState<List<FocoMapa>>>()
    val focosState: LiveData<UiState<List<FocoMapa>>> = _focosState

    // Sin init { cargar() } a propósito. La carga la pide la VISTA en su onResume
    // (ver HomeFragment): así hay UN solo camino de carga, que corre tanto al
    // entrar como al volver, en vez de dos (el init para la primera vez, el
    // onResume para las demás) que había que coordinar con una bandera. Esa
    // bandera era el bug: cuando la pestaña se restauraba con el ViewModel ya
    // creado, el init no volvía a correr y la bandera saltaba el onResume, así
    // que la pantalla se quedaba con los datos de la vez anterior.

    fun cargarResumen() = loadInto(_uiState) { repository.obtenerResumen() }

    fun cargarFocos() = loadInto(_focosState) { repository.focosMapa() }

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
