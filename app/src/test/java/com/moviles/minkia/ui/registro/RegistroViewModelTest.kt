package com.moviles.minkia.ui.registro

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.moviles.minkia.R
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.model.Usuario
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
 * Tests de la validación multi-campo y el alta de cuenta del registro (C04).
 * Foco en las reglas no triviales: DNI opcional y términos obligatorios.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistroViewModelTest {

    @get:Rule
    val instantTask = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = mockk<AuthRepository>()
    private val viewModel = RegistroViewModel(repository)

    @Test
    fun `nombre de menos de 3 caracteres publica el error de nombre corto`() {
        viewModel.registrar("Fe", "f@mail.com", "", "123456", true)
        assertEquals(R.string.registro_error_nombre_corto, viewModel.errores.value?.nombre)
    }

    @Test
    fun `email con formato invalido publica el error de email invalido`() {
        viewModel.registrar("Fernando", "no-mail", "", "123456", true)
        assertEquals(R.string.registro_error_email_invalido, viewModel.errores.value?.email)
    }

    @Test
    fun `dni con menos de 8 digitos publica el error de dni invalido`() {
        viewModel.registrar("Fernando", "f@mail.com", "1234567", "123456", true)
        assertEquals(R.string.registro_error_dni_invalido, viewModel.errores.value?.dni)
    }

    @Test
    fun `password de menos de 6 caracteres publica el error de password corto`() {
        viewModel.registrar("Fernando", "f@mail.com", "", "123", true)
        assertEquals(R.string.registro_error_password_corto, viewModel.errores.value?.password)
    }

    @Test
    fun `no aceptar los terminos marca el error de terminos`() {
        viewModel.registrar("Fernando", "f@mail.com", "", "123456", false)
        assertEquals(true, viewModel.errores.value?.terminos)
    }

    @Test
    fun `dni vacio es valido por ser opcional`() {
        val usuario = Usuario("9", "Fernando", "f@mail.com", esAdmin = false)
        coEvery { repository.registrar(any(), any(), any(), any()) } returns usuario

        viewModel.registrar("Fernando", "f@mail.com", "", "123456", true)

        assertNull(viewModel.errores.value?.dni)
        coVerify(exactly = 1) { repository.registrar("Fernando", "f@mail.com", "", "123456") }
    }

    @Test
    fun `registro valido con dni emite Success`() {
        val usuario = Usuario("9", "Fernando", "f@mail.com", esAdmin = false)
        coEvery { repository.registrar(any(), any(), any(), any()) } returns usuario

        viewModel.registrar("Fernando", "f@mail.com", "12345678", "123456", true)

        assertEquals(UiState.Success(usuario), viewModel.estado.value)
    }
}
