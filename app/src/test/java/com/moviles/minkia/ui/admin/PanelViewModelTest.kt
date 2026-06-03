package com.moviles.minkia.ui.admin

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.model.KpiAdmin
import com.moviles.minkia.data.model.PanelAdmin
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.data.model.ZonaAfectada
import com.moviles.minkia.data.repository.AdminRepository
import com.moviles.minkia.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Verifica que el panel del administrador se cargue desde el repositorio y se
 * exponga como Success al construir el ViewModel (carga en init).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PanelViewModelTest {

    @get:Rule
    val instantTask = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val panel = PanelAdmin(
        kpis = listOf(KpiAdmin("47", "Reportes hoy", "+12%")),
        zonas = listOf(ZonaAfectada("P. J. La Unión", 18, Severidad.ALTA))
    )

    @Test
    fun `al construirse carga el panel y lo emite como Success`() {
        val repository = mockk<AdminRepository>()
        coEvery { repository.panel() } returns panel

        val viewModel = PanelViewModel(repository)

        assertEquals(UiState.Success(panel), viewModel.uiState.value)
    }
}
