package com.moviles.minkia.ui.home

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.model.FocoMapa
import com.moviles.minkia.data.model.PuntoCritico
import com.moviles.minkia.data.model.ResumenCiudadano
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
 * Verifica la pantalla de inicio: el resumen del ciudadano y los focos del mapa
 * de calor son dos lecturas independientes del mismo repositorio, cada una con
 * su propio [UiState], de modo que un fallo en una no afecta a la otra.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val instantTask = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val resumen = ResumenCiudadano(
        puntosActivos = 340,
        tusReportes = 8,
        resueltos = 5,
        focosCerca = 3,
        puntosCercanos = listOf(
            PuntoCritico(
                id = "1",
                direccion = "Jr. Elías Aguirre 450, Chimbote",
                referencia = "Frente al mercado",
                distanciaMetros = 120,
                cantidadReportes = 6,
                severidad = Severidad.ALTA
            )
        ),
        nombre = "Fernando Cholán"
    )

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
    fun `al construirse carga el resumen y lo emite como Success en uiState`() {
        val repository = mockk<CiudadanoRepository>()
        coEvery { repository.obtenerResumen() } returns resumen
        coEvery { repository.focosMapa() } returns focos

        val viewModel = HomeViewModel(repository)

        assertEquals(UiState.Success(resumen), viewModel.uiState.value)
    }

    @Test
    fun `al construirse carga los focos y los emite como Success en focosState`() {
        val repository = mockk<CiudadanoRepository>()
        coEvery { repository.obtenerResumen() } returns resumen
        coEvery { repository.focosMapa() } returns focos

        val viewModel = HomeViewModel(repository)

        assertEquals(UiState.Success(focos), viewModel.focosState.value)
    }

    @Test
    fun `si falla el resumen uiState termina en Error sin afectar focosState`() {
        val repository = mockk<CiudadanoRepository>()
        coEvery { repository.obtenerResumen() } throws RuntimeException("sin conexión")
        coEvery { repository.focosMapa() } returns focos

        val viewModel = HomeViewModel(repository)

        assertEquals(UiState.Error("sin conexión"), viewModel.uiState.value)
        assertEquals(UiState.Success(focos), viewModel.focosState.value)
    }

    @Test
    fun `si fallan los focos focosState termina en Error sin afectar uiState`() {
        val repository = mockk<CiudadanoRepository>()
        coEvery { repository.obtenerResumen() } returns resumen
        coEvery { repository.focosMapa() } throws RuntimeException("focos caídos")

        val viewModel = HomeViewModel(repository)

        assertEquals(UiState.Success(resumen), viewModel.uiState.value)
        assertEquals(UiState.Error("focos caídos"), viewModel.focosState.value)
    }
}
