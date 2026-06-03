package com.moviles.minkia.core

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.moviles.minkia.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Verifica el patrón central de BaseViewModel: Loading -> Success(data) o
 * Loading -> Error(mensaje). Se prueba a través de una subclase mínima porque
 * loadInto es protected (se testea el comportamiento, no el detalle interno).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BaseViewModelTest {

    @get:Rule
    val instantTask = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private class FakeViewModel : BaseViewModel() {
        private val _estado = MutableLiveData<UiState<String>>()
        val estado: LiveData<UiState<String>> = _estado
        fun ejecutar(block: suspend () -> String) = loadInto(_estado, block)
    }

    @Test
    fun `loadInto emite Success con el resultado del bloque`() {
        val vm = FakeViewModel()
        vm.ejecutar { "ok" }
        assertEquals(UiState.Success("ok"), vm.estado.value)
    }

    @Test
    fun `loadInto emite Error con el mensaje de la excepcion`() {
        val vm = FakeViewModel()
        vm.ejecutar { throw RuntimeException("falla de red") }
        assertEquals(UiState.Error("falla de red"), vm.estado.value)
    }
}
