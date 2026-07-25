package com.moviles.minkia.ui.mapa

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.model.FocoMapa
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.data.repository.CiudadanoRepository
import com.moviles.minkia.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Verifica el mapa de focos (C08): al construirse pide al repositorio los focos
 * activos de la comunidad y publica el ciclo Loading/Success/Error.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapaViewModelTest {

    @get:Rule
    val instantTask = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val focos = listOf(
        FocoMapa(
            id = "1",
            latitud = -9.0745,
            longitud = -78.5936,
            severidad = Severidad.ALTA,
            direccion = "Jr. Elías Aguirre 450, Chimbote",
            tipo = "Basura"
        )
    )

    @Test
    fun `al construirse carga los focos y los emite como Success`() {
        val repository = mockk<CiudadanoRepository>()
        coEvery { repository.focosMapa() } returns focos

        val viewModel = MapaViewModel(repository)

        assertEquals(UiState.Success(focos), viewModel.uiState.value)
    }

    @Test
    fun `si el repositorio falla el estado termina en Error`() {
        val repository = mockk<CiudadanoRepository>()
        coEvery { repository.focosMapa() } throws RuntimeException("sin conexión")

        val viewModel = MapaViewModel(repository)

        assertEquals(UiState.Error("sin conexión"), viewModel.uiState.value)
    }
}
