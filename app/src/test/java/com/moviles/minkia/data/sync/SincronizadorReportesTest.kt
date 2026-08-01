package com.moviles.minkia.data.sync

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moviles.minkia.data.local.ColaReportes
import com.moviles.minkia.data.model.ReportePendiente
import com.moviles.minkia.data.source.ReporteFirestoreDataSource
import com.moviles.minkia.data.source.ResultadoEnvio
import com.moviles.minkia.util.authFake
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * La política de reintentos de la cola. Decide, reporte por reporte, si vuelve a
 * intentarse o se abandona; equivocarse acá significa o bien perder el reporte de
 * un vecino, o bien reintentar para siempre uno que jamás va a entrar.
 *
 * Nota de diseño: la fuente de envío de [SincronizadorReportes] es `internal var`
 * justamente para que estos tests puedan sustituirla; como `private val` de un
 * `object` compilaba a un campo static final y la política quedaba fuera de
 * cualquier prueba.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SincronizadorReportesTest {

    private lateinit var fuente: ReporteFirestoreDataSource

    @Before
    fun setup() {
        // Las fábricas de Firebase primero: la inicialización del object las llama.
        mockkStatic(FirebaseAuth::class)
        every { FirebaseAuth.getInstance() } returns authFake()
        mockkStatic(FirebaseFirestore::class)
        every { FirebaseFirestore.getInstance() } returns mockk(relaxed = true)

        mockkObject(SincronizadorReportes)
        mockkObject(ColaReportes)
        coEvery { ColaReportes.remover(any()) } just Runs

        fuente = mockk()
        SincronizadorReportes.dataSource = fuente
    }

    @After
    fun teardown() = unmockkAll()

    private fun pendiente(id: String) = ReportePendiente(
        id = id, userId = "u1", ticket = "#MK-$id", tipo = "Basura", severidad = "ALTA",
        descripcion = "", direccion = "", zona = "", latitud = 0.0, longitud = 0.0,
        areaM2 = 0.0, confianza = 0, fotoPath = null, creadoEn = 1L
    )

    @Test
    fun `sin internet no intenta nada y no toca la cola`() = runBlocking {
        every { SincronizadorReportes.hayInternet() } returns false
        coEvery { ColaReportes.listar() } returns listOf(pendiente("a"))

        assertEquals(0, SincronizadorReportes.sincronizar())

        coVerify(exactly = 0) { fuente.enviarReporte(any()) }
        coVerify(exactly = 0) { ColaReportes.remover(any()) }
    }

    @Test
    fun `con la cola vacia no hay nada que enviar`() = runBlocking {
        every { SincronizadorReportes.hayInternet() } returns true
        coEvery { ColaReportes.listar() } returns emptyList()

        assertEquals(0, SincronizadorReportes.sincronizar())
    }

    @Test
    fun `lo que se envia bien sale de la cola y se cuenta`() = runBlocking {
        every { SincronizadorReportes.hayInternet() } returns true
        coEvery { ColaReportes.listar() } returns listOf(pendiente("a"), pendiente("b"))
        coEvery { fuente.enviarReporte(any()) } returns ResultadoEnvio.ENVIADO

        assertEquals(2, SincronizadorReportes.sincronizar())

        coVerify(exactly = 1) { ColaReportes.remover("a") }
        coVerify(exactly = 1) { ColaReportes.remover("b") }
    }

    @Test
    fun `un fallo transitorio conserva el reporte en la cola y no lo cuenta`() = runBlocking {
        every { SincronizadorReportes.hayInternet() } returns true
        coEvery { ColaReportes.listar() } returns listOf(pendiente("a"))
        coEvery { fuente.enviarReporte(any()) } returns ResultadoEnvio.REINTENTAR

        assertEquals(0, SincronizadorReportes.sincronizar())

        coVerify(exactly = 0) { ColaReportes.remover(any()) }
    }

    @Test
    fun `un fallo permanente saca el reporte de la cola pero no cuenta como enviado`() = runBlocking {
        every { SincronizadorReportes.hayInternet() } returns true
        coEvery { ColaReportes.listar() } returns listOf(pendiente("a"))
        coEvery { fuente.enviarReporte(any()) } returns ResultadoEnvio.DESCARTAR

        assertEquals(0, SincronizadorReportes.sincronizar())

        coVerify(exactly = 1) { ColaReportes.remover("a") } // no se reintenta por siempre
    }

    @Test
    fun `cada reporte se decide por separado`() = runBlocking {
        every { SincronizadorReportes.hayInternet() } returns true
        coEvery { ColaReportes.listar() } returns listOf(pendiente("ok"), pendiente("retry"), pendiente("drop"))
        coEvery { fuente.enviarReporte(match { it.id == "ok" }) } returns ResultadoEnvio.ENVIADO
        coEvery { fuente.enviarReporte(match { it.id == "retry" }) } returns ResultadoEnvio.REINTENTAR
        coEvery { fuente.enviarReporte(match { it.id == "drop" }) } returns ResultadoEnvio.DESCARTAR

        assertEquals(1, SincronizadorReportes.sincronizar())

        coVerify(exactly = 1) { ColaReportes.remover("ok") }
        coVerify(exactly = 0) { ColaReportes.remover("retry") }
        coVerify(exactly = 1) { ColaReportes.remover("drop") }
    }

    @Test
    fun `si la conexion se cae a mitad corta y conserva lo que falta`() = runBlocking {
        // true al entrar, true para el primero, false antes del segundo.
        every { SincronizadorReportes.hayInternet() } returnsMany listOf(true, true, false)
        coEvery { ColaReportes.listar() } returns listOf(pendiente("a"), pendiente("b"))
        coEvery { fuente.enviarReporte(any()) } returns ResultadoEnvio.ENVIADO

        assertEquals(1, SincronizadorReportes.sincronizar())

        coVerify(exactly = 1) { ColaReportes.remover("a") }
        coVerify(exactly = 0) { ColaReportes.remover("b") } // sigue en la cola para el próximo intento
    }

    @Test
    fun `un envio colgado vence por timeout y el reporte NO se pierde`() = runTest {
        every { SincronizadorReportes.hayInternet() } returns true
        coEvery { ColaReportes.listar() } returns listOf(pendiente("a"))
        coEvery { fuente.enviarReporte(any()) } coAnswers {
            delay(Long.MAX_VALUE) // el servidor nunca responde
            ResultadoEnvio.ENVIADO
        }

        assertEquals(0, SincronizadorReportes.sincronizar())

        coVerify(exactly = 0) { ColaReportes.remover(any()) } // se conserva para reintentar
    }
}
