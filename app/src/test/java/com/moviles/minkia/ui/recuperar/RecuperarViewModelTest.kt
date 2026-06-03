package com.moviles.minkia.ui.recuperar

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.moviles.minkia.R
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.repository.AuthRepository
import com.moviles.minkia.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Tests de "Recuperar contraseña" (C05): validación del correo y disparo del
 * envío del enlace.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecuperarViewModelTest {

    @get:Rule
    val instantTask = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = mockk<AuthRepository>()
    private val viewModel = RecuperarViewModel(repository)

    @Test
    fun `email vacio publica el error de email vacio`() {
        viewModel.enviarEnlace("")
        assertEquals(R.string.recuperar_error_email_vacio, viewModel.errorEmail.value)
    }

    @Test
    fun `email con formato invalido publica el error de email invalido`() {
        viewModel.enviarEnlace("no-es-mail")
        assertEquals(R.string.recuperar_error_email_invalido, viewModel.errorEmail.value)
    }

    @Test
    fun `email invalido no toca el repositorio`() {
        viewModel.enviarEnlace("no-es-mail")
        coVerify(exactly = 0) { repository.recuperarPassword(any()) }
    }

    @Test
    fun `email valido envia el enlace y emite Success`() {
        coEvery { repository.recuperarPassword(any()) } returns Unit

        viewModel.enviarEnlace("vecino@chimbote.pe")

        assertNull(viewModel.errorEmail.value)
        assertEquals(UiState.Success(Unit), viewModel.estado.value)
        coVerify(exactly = 1) { repository.recuperarPassword("vecino@chimbote.pe") }
    }
}
