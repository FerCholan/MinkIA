package com.moviles.minkia.ui.misdatos

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.moviles.minkia.R
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.repository.PerfilRepository
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
 * Verifica "Mis datos" (mockup C17b): la carga progresiva del perfil (cada campo
 * cae a su propio valor por defecto si Firestore falla, sin arrastrar a los
 * demás), la validación con corte secuencial y el guardado, incluido el caso de
 * apodo ocupado, que viaja dentro de Success en vez de como Error.
 *
 * El constructor dispara cargar() en el init. Como cada campo se resuelve con
 * runCatching + valor por defecto, una llamada al repositorio sin stub cae en su
 * default sin romper el test; por eso los tests centrados en guardar()/validar()
 * no stubean obtenerDni/obtenerNickname/obtenerUsarApodo salvo que el propio test
 * verifique el resultado de cargar().
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MisDatosViewModelTest {

    @get:Rule
    val instantTask = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    // ---- cargar() ----

    @Test
    fun `al construirse carga dni apodo y usarApodo y los emite como Success`() {
        val repository = mockk<PerfilRepository>()
        coEvery { repository.obtenerDni() } returns "12345678"
        coEvery { repository.obtenerNickname() } returns "vecino1"
        coEvery { repository.obtenerUsarApodo() } returns true

        val viewModel = MisDatosViewModel(repository)

        assertEquals(
            UiState.Success(DatosPersonales(dni = "12345678", apodo = "vecino1", usarApodo = true)),
            viewModel.datos.value
        )
    }

    @Test
    fun `si un campo falla al cargar cae a su valor por defecto y no afecta a los demas`() {
        val repository = mockk<PerfilRepository>()
        coEvery { repository.obtenerDni() } throws RuntimeException("sin conexión")
        coEvery { repository.obtenerNickname() } returns "vecino1"
        coEvery { repository.obtenerUsarApodo() } returns true

        val viewModel = MisDatosViewModel(repository)

        assertEquals(
            UiState.Success(DatosPersonales(dni = "", apodo = "vecino1", usarApodo = true)),
            viewModel.datos.value
        )
    }

    @Test
    fun `si los tres campos fallan al cargar el estado igual termina en Success con los defaults`() {
        val repository = mockk<PerfilRepository>()
        coEvery { repository.obtenerDni() } throws RuntimeException("sin conexión")
        coEvery { repository.obtenerNickname() } throws RuntimeException("sin conexión")
        coEvery { repository.obtenerUsarApodo() } throws RuntimeException("sin conexión")

        val viewModel = MisDatosViewModel(repository)

        assertEquals(
            UiState.Success(DatosPersonales(dni = "", apodo = "", usarApodo = false)),
            viewModel.datos.value
        )
    }

    // ---- validar() a través de guardar() ----

    @Test
    fun `dni con formato invalido publica el error de dni`() {
        val viewModel = MisDatosViewModel(mockk<PerfilRepository>())

        viewModel.guardar(dni = "123", apodo = "", usarApodo = false)

        assertEquals(R.string.registro_error_dni_invalido, viewModel.errores.value?.dni)
        assertNull(viewModel.errores.value?.apodo)
    }

    @Test
    fun `dni vacio es valido por ser opcional`() {
        val repository = mockk<PerfilRepository>()
        coEvery { repository.nicknameDisponible(any()) } returns true
        coEvery { repository.guardarDni(any()) } returns Unit
        coEvery { repository.guardarNickname(any()) } returns Unit
        coEvery { repository.guardarUsarApodo(any()) } returns Unit
        val viewModel = MisDatosViewModel(repository)

        viewModel.guardar(dni = "", apodo = "vecino1", usarApodo = true)

        assertNull(viewModel.errores.value?.dni)
    }

    @Test
    fun `apodo con formato invalido publica el error de apodo`() {
        val viewModel = MisDatosViewModel(mockk<PerfilRepository>())

        viewModel.guardar(dni = "", apodo = "ab", usarApodo = false)

        assertEquals(R.string.editar_nickname_invalido, viewModel.errores.value?.apodo)
    }

    @Test
    fun `activar usar apodo sin apodo cargado publica el error de apodo faltante`() {
        val viewModel = MisDatosViewModel(mockk<PerfilRepository>())

        viewModel.guardar(dni = "", apodo = "", usarApodo = true)

        assertEquals(R.string.mis_datos_apodo_falta, viewModel.errores.value?.apodo)
    }

    @Test
    fun `dni y apodo invalidos a la vez solo reportan el error de dni por el corte secuencial`() {
        val repository = mockk<PerfilRepository>()
        val viewModel = MisDatosViewModel(repository)

        viewModel.guardar(dni = "123", apodo = "ab", usarApodo = false)

        assertEquals(R.string.registro_error_dni_invalido, viewModel.errores.value?.dni)
        assertNull(viewModel.errores.value?.apodo)
        coVerify(exactly = 0) { repository.nicknameDisponible(any()) }
    }

    @Test
    fun `dni invalido junto con usarApodo sin apodo solo reporta el error de dni`() {
        val viewModel = MisDatosViewModel(mockk<PerfilRepository>())

        viewModel.guardar(dni = "abc", apodo = "", usarApodo = true)

        assertEquals(R.string.registro_error_dni_invalido, viewModel.errores.value?.dni)
        assertNull(viewModel.errores.value?.apodo)
    }

    @Test
    fun `formulario invalido no toca el repositorio`() {
        val repository = mockk<PerfilRepository>()
        val viewModel = MisDatosViewModel(repository)

        viewModel.guardar(dni = "123", apodo = "", usarApodo = false)

        coVerify(exactly = 0) { repository.nicknameDisponible(any()) }
        coVerify(exactly = 0) { repository.guardarDni(any()) }
    }

    // ---- guardar() ----

    @Test
    fun `guardar valido con apodo libre guarda los tres campos y emite Guardado`() {
        val repository = mockk<PerfilRepository>()
        coEvery { repository.nicknameDisponible("vecino1") } returns true
        coEvery { repository.guardarDni("12345678") } returns Unit
        coEvery { repository.guardarNickname("vecino1") } returns Unit
        coEvery { repository.guardarUsarApodo(true) } returns Unit
        val viewModel = MisDatosViewModel(repository)

        viewModel.guardar(dni = "12345678", apodo = "vecino1", usarApodo = true)

        assertEquals(UiState.Success(ResultadoGuardar.Guardado), viewModel.estado.value)
        coVerify(exactly = 1) { repository.guardarDni("12345678") }
        coVerify(exactly = 1) { repository.guardarNickname("vecino1") }
        coVerify(exactly = 1) { repository.guardarUsarApodo(true) }
    }

    @Test
    fun `guardar con apodo vacio no consulta disponibilidad y desactiva usarApodo`() {
        val repository = mockk<PerfilRepository>()
        coEvery { repository.guardarDni("12345678") } returns Unit
        coEvery { repository.guardarNickname("") } returns Unit
        coEvery { repository.guardarUsarApodo(false) } returns Unit
        val viewModel = MisDatosViewModel(repository)

        viewModel.guardar(dni = "12345678", apodo = "", usarApodo = false)

        assertEquals(UiState.Success(ResultadoGuardar.Guardado), viewModel.estado.value)
        coVerify(exactly = 0) { repository.nicknameDisponible(any()) }
        coVerify(exactly = 1) { repository.guardarUsarApodo(false) }
    }

    @Test
    fun `guardar con apodo ocupado emite ApodoOcupado dentro de Success sin guardar nada`() {
        val repository = mockk<PerfilRepository>()
        coEvery { repository.nicknameDisponible("vecino1") } returns false
        val viewModel = MisDatosViewModel(repository)

        viewModel.guardar(dni = "", apodo = "vecino1", usarApodo = false)

        assertEquals(UiState.Success(ResultadoGuardar.ApodoOcupado), viewModel.estado.value)
        coVerify(exactly = 0) { repository.guardarDni(any()) }
        coVerify(exactly = 0) { repository.guardarNickname(any()) }
        coVerify(exactly = 0) { repository.guardarUsarApodo(any()) }
    }

    @Test
    fun `si el repositorio falla al guardar el estado termina en Error`() {
        val repository = mockk<PerfilRepository>()
        coEvery { repository.nicknameDisponible("vecino1") } returns true
        coEvery { repository.guardarDni(any()) } throws RuntimeException("sin conexión")
        val viewModel = MisDatosViewModel(repository)

        viewModel.guardar(dni = "12345678", apodo = "vecino1", usarApodo = true)

        assertEquals(UiState.Error("sin conexión"), viewModel.estado.value)
    }
}
