package com.moviles.minkia.ui.admin

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.model.FocoMapa
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.data.model.ZonaAfectada
import com.moviles.minkia.data.repository.AdminRepository
import com.moviles.minkia.data.repository.CiudadanoRepository
import com.moviles.minkia.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Verifica los puntos críticos del administrador (A03): focos y zonas se piden a
 * repositorios distintos y cada uno publica su propio [UiState] de forma
 * independiente, sin que el resultado de uno dependa del otro.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PuntosCriticosViewModelTest {

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

    private val zonas = listOf(
        ZonaAfectada(nombre = "Casco urbano", focos = 12, severidad = Severidad.ALTA)
    )

    @Test
    fun `al construirse carga los focos y los emite como Success en focosState`() {
        val repository = mockk<CiudadanoRepository>()
        val adminRepository = mockk<AdminRepository>()
        coEvery { repository.focosMapa() } returns focos
        coEvery { adminRepository.zonas() } returns zonas

        val viewModel = PuntosCriticosViewModel(repository, adminRepository)

        assertEquals(UiState.Success(focos), viewModel.focosState.value)
    }

    @Test
    fun `al construirse carga las zonas y las emite como Success en zonasState`() {
        val repository = mockk<CiudadanoRepository>()
        val adminRepository = mockk<AdminRepository>()
        coEvery { repository.focosMapa() } returns focos
        coEvery { adminRepository.zonas() } returns zonas

        val viewModel = PuntosCriticosViewModel(repository, adminRepository)

        assertEquals(UiState.Success(zonas), viewModel.zonasState.value)
    }

    @Test
    fun `si el repositorio de focos falla solo focosState termina en Error`() {
        val repository = mockk<CiudadanoRepository>()
        val adminRepository = mockk<AdminRepository>()
        coEvery { repository.focosMapa() } throws RuntimeException("sin conexión")
        coEvery { adminRepository.zonas() } returns zonas

        val viewModel = PuntosCriticosViewModel(repository, adminRepository)

        assertEquals(UiState.Error("sin conexión"), viewModel.focosState.value)
        assertEquals(UiState.Success(zonas), viewModel.zonasState.value)
    }

    @Test
    fun `si el repositorio de zonas falla solo zonasState termina en Error`() {
        val repository = mockk<CiudadanoRepository>()
        val adminRepository = mockk<AdminRepository>()
        coEvery { repository.focosMapa() } returns focos
        coEvery { adminRepository.zonas() } throws RuntimeException("zonas caídas")

        val viewModel = PuntosCriticosViewModel(repository, adminRepository)

        assertEquals(UiState.Success(focos), viewModel.focosState.value)
        assertEquals(UiState.Error("zonas caídas"), viewModel.zonasState.value)
    }
}
