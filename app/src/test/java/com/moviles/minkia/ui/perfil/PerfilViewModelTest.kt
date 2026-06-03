package com.moviles.minkia.ui.perfil

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.model.PerfilCiudadano
import com.moviles.minkia.data.repository.CiudadanoRepository
import com.moviles.minkia.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Verifica que el perfil se cargue desde el repositorio y se exponga como
 * Success al construir el ViewModel (carga en init).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PerfilViewModelTest {

    @get:Rule
    val instantTask = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val perfil = PerfilCiudadano(
        iniciales = "FC",
        nombre = "Fernando Cholán",
        rol = "Vecino fiscalizador · Nivel 3",
        reportes = 8,
        resueltos = 5,
        puntosMinka = 340,
        nivelTexto = "Nivel 3 · Guardián del barrio",
        puntosNivelActual = 340,
        puntosNivelObjetivo = 500,
        faltanTexto = "Te faltan 160 puntos para ser Héroe del barrio"
    )

    @Test
    fun `al construirse carga el perfil y lo emite como Success`() {
        val repository = mockk<CiudadanoRepository>()
        coEvery { repository.obtenerPerfil() } returns perfil

        val viewModel = PerfilViewModel(repository)

        assertEquals(UiState.Success(perfil), viewModel.uiState.value)
    }
}
