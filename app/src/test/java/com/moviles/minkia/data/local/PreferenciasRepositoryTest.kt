package com.moviles.minkia.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.moviles.minkia.data.model.Usuario
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests del repositorio de preferencias sobre un DataStore temporal en disco
 * (DataStore core es JVM puro, no necesita Android). Verifica defaults y
 * persistencia de cada preferencia.
 */
class PreferenciasRepositoryTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: PreferenciasRepository

    @Before
    fun setup() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.Unconfined + Job()),
            produceFile = { File(tmpFolder.root, "test.preferences_pb") }
        )
        repo = PreferenciasRepository(dataStore)
    }

    @Test
    fun `notificaciones de estado por defecto estan activas`() = runBlocking {
        assertTrue(repo.notificacionesEstado.first())
    }

    @Test
    fun `desactivar notificaciones de estado persiste el valor`() = runBlocking {
        repo.setNotificacionesEstado(false)
        assertFalse(repo.notificacionesEstado.first())
    }

    @Test
    fun `novedades por defecto estan activas y se pueden desactivar`() = runBlocking {
        assertTrue(repo.notificacionesNovedades.first())
        repo.setNotificacionesNovedades(false)
        assertFalse(repo.notificacionesNovedades.first())
    }

    @Test
    fun `descarga solo por wifi por defecto esta desactivada`() = runBlocking {
        assertFalse(repo.descargaSoloWifi.first())
        repo.setDescargaSoloWifi(true)
        assertTrue(repo.descargaSoloWifi.first())
    }

    @Test
    fun `onboarding visto por defecto es false y se puede marcar`() = runBlocking {
        assertFalse(repo.onboardingVisto.first())
        repo.marcarOnboardingVisto()
        assertTrue(repo.onboardingVisto.first())
    }

    @Test
    fun `permisos vistos por defecto es false y se puede marcar`() = runBlocking {
        assertFalse(repo.permisosVistos.first())
        repo.marcarPermisosVistos()
        assertTrue(repo.permisosVistos.first())
    }

    @Test
    fun `sin sesion guardada la sesion es null`() = runBlocking {
        assertNull(repo.sesion.first())
    }

    @Test
    fun `guardar sesion persiste el usuario y se puede leer`() = runBlocking {
        val usuario = Usuario(id = "U-001", nombre = "Vecino", email = "vecino@chimbote.pe")
        repo.guardarSesion(usuario)
        assertEquals(usuario, repo.sesion.first())
    }

    @Test
    fun `guardar sesion conserva el rol de administrador`() = runBlocking {
        repo.guardarSesion(Usuario("A-001", "Municipalidad", "admin@minkia.pe", esAdmin = true))
        assertTrue(repo.sesion.first()?.esAdmin == true)
    }

    @Test
    fun `cerrar sesion borra el usuario guardado`() = runBlocking {
        repo.guardarSesion(Usuario("U-001", "Vecino", "vecino@chimbote.pe"))
        repo.cerrarSesion()
        assertNull(repo.sesion.first())
    }
}
