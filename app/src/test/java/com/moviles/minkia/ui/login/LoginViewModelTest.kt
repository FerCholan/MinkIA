package com.moviles.minkia.ui.login

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
 * Tests de la validación y el ciclo de autenticación del login (C03).
 * El repositorio se mockea: acá probamos la lógica del ViewModel, no la red.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val instantTask = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = mockk<AuthRepository>()
    private val viewModel = LoginViewModel(repository)

    @Test
    fun `email vacio publica el error de email vacio`() {
        viewModel.iniciarSesion("", "123456")
        assertEquals(R.string.login_error_email_vacio, viewModel.errores.value?.email)
    }

    @Test
    fun `email con formato invalido publica el error de email invalido`() {
        viewModel.iniciarSesion("no-es-un-mail", "123456")
        assertEquals(R.string.login_error_email_invalido, viewModel.errores.value?.email)
    }

    @Test
    fun `password vacio publica el error de password vacio`() {
        viewModel.iniciarSesion("vecino@chimbote.pe", "")
        assertEquals(R.string.login_error_password_vacio, viewModel.errores.value?.password)
    }

    @Test
    fun `password de menos de 6 caracteres publica el error de password corto`() {
        viewModel.iniciarSesion("vecino@chimbote.pe", "123")
        assertEquals(R.string.login_error_password_corto, viewModel.errores.value?.password)
    }

    @Test
    fun `formulario invalido no toca el repositorio`() {
        viewModel.iniciarSesion("", "")
        coVerify(exactly = 0) { repository.iniciarSesion(any(), any()) }
    }

    @Test
    fun `login valido emite Success con el usuario y limpia errores`() {
        val usuario = Usuario("1", "Vecino", "vecino@chimbote.pe", esAdmin = false)
        coEvery { repository.iniciarSesion(any(), any()) } returns usuario

        viewModel.iniciarSesion("vecino@chimbote.pe", "123456")

        assertNull(viewModel.errores.value?.email)
        assertNull(viewModel.errores.value?.password)
        assertEquals(UiState.Success(usuario), viewModel.estado.value)
        coVerify(exactly = 1) { repository.iniciarSesion("vecino@chimbote.pe", "123456") }
    }
}
