package com.moviles.minkia.core

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * ViewModel base para pantallas orientadas a datos. Centraliza el patrón
 * "emitir Loading, ejecutar una operación suspendida, emitir Success o Error",
 * para que cada ViewModel concreto solo describa QUÉ pedir, no el cómo.
 */
abstract class BaseViewModel : ViewModel() {

    /**
     * Ejecuta [block] dentro del scope del ViewModel publicando el ciclo de
     * estados en [target]: Loading antes, Success con el resultado, o Error si
     * algo falla.
     */
    protected fun <T> loadInto(
        target: MutableLiveData<UiState<T>>,
        block: suspend () -> T
    ) {
        target.value = UiState.Loading
        viewModelScope.launch {
            target.value = try {
                UiState.Success(block())
            } catch (e: CancellationException) {
                // La cancelación NO es un error de la pantalla: pasa cuando el
                // ViewModel se destruye a mitad de una carga. Hay que relanzarla
                // para respetar la cancelación estructurada; atraparla como una
                // excepción más dejaba la corrutina "terminando" un trabajo ya
                // cancelado y publicando un Error que nunca ocurrió.
                throw e
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Ocurrió un error inesperado. Intenta de nuevo.")
            }
        }
    }
}
