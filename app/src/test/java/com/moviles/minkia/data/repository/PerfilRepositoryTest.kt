package com.moviles.minkia.data.repository

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.moviles.minkia.util.authFake
import com.moviles.minkia.util.documentoAusente
import com.moviles.minkia.util.documentoFake
import com.moviles.minkia.util.tareaLista
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Perfil progresivo: DNI y apodo. Lo importante acá es la regla de PRIVACIDAD:
 * qué nombre queda sellado en cada reporte que el vecino publica. Si esa regla se
 * equivoca, se expone el nombre real de alguien que pidió explícitamente no
 * mostrarlo. Y la reserva de apodos, que tiene que ser única sin importar
 * mayúsculas.
 */
class PerfilRepositoryTest {

    private val db = mockk<FirebaseFirestore>()

    /** Arma el repositorio con el doc de perfil y (opcional) el de reserva del apodo. */
    private fun repo(
        perfil: DocumentSnapshot,
        uid: String? = "u1",
        displayName: String? = "Fernando Cholán",
        email: String? = null,
        reserva: DocumentSnapshot? = null
    ): PerfilRepository {
        val perfilRef = mockk<DocumentReference>(relaxed = true).also {
            every { it.get() } returns tareaLista(perfil)
        }
        val usuarios = mockk<CollectionReference>().also { every { it.document(any()) } returns perfilRef }
        every { db.collection("usuarios") } returns usuarios

        if (reserva != null) {
            val reservaRef = mockk<DocumentReference>(relaxed = true).also {
                every { it.get() } returns tareaLista(reserva)
            }
            val nicknames = mockk<CollectionReference>().also { every { it.document(any()) } returns reservaRef }
            every { db.collection("nicknames") } returns nicknames
        }
        return PerfilRepository(authFake(uid, displayName, email), db)
    }

    // ---------- privacidad: qué nombre viaja en el reporte ----------

    @Test
    fun `con el apodo activado el reporte lleva el apodo y no el nombre real`() = runBlocking {
        val r = repo(documentoFake("u1", "nickname" to "ElVecinoDeAlLado", "usarApodo" to true))

        assertEquals("ElVecinoDeAlLado", r.nombreVisible())
    }

    @Test
    fun `con el apodo desactivado el reporte lleva el nombre real`() = runBlocking {
        val r = repo(documentoFake("u1", "nickname" to "ElVecinoDeAlLado", "usarApodo" to false))

        assertEquals("Fernando Cholán", r.nombreVisible())
    }

    @Test
    fun `activar el apodo sin haber puesto ninguno no deja el autor vacio`() = runBlocking {
        val r = repo(documentoFake("u1", "nickname" to "", "usarApodo" to true))

        assertEquals("Fernando Cholán", r.nombreVisible())
    }

    @Test
    fun `un apodo de solo espacios no cuenta como apodo`() = runBlocking {
        val r = repo(documentoFake("u1", "nickname" to "   ", "usarApodo" to true))

        assertEquals("Fernando Cholán", r.nombreVisible())
    }

    @Test
    fun `sin nombre real el autor cae al usuario del correo y por ultimo a Vecino`() = runBlocking {
        assertEquals(
            "fernando",
            repo(documentoAusente(), displayName = null, email = "fernando@mail.com").nombreVisible()
        )
        assertEquals(
            "Vecino",
            repo(documentoAusente(), displayName = null, email = null).nombreVisible()
        )
    }

    @Test
    fun `el nombre real ignora el apodo aunque este activado`() {
        // Es lo que se muestra en TU propio perfil: ahí sí va tu nombre.
        val r = repo(documentoFake("u1", "nickname" to "Apodo", "usarApodo" to true))

        assertEquals("Fernando Cholán", r.nombreReal())
    }

    // ---------- por defecto no se usa el apodo ----------

    @Test
    fun `por defecto el vecino NO usa apodo en sus reportes`() = runBlocking {
        assertFalse(repo(documentoAusente()).obtenerUsarApodo())
    }

    @Test
    fun `un perfil sin DNI devuelve cadena vacia y no null`() = runBlocking {
        assertEquals("", repo(documentoAusente()).obtenerDni())
    }

    @Test
    fun `el DNI guardado se lee tal cual`() = runBlocking {
        assertEquals("12345678", repo(documentoFake("u1", "dni" to "12345678")).obtenerDni())
    }

    // ---------- unicidad del apodo ----------

    @Test
    fun `un apodo vacio siempre esta disponible porque es opcional`() = runBlocking {
        assertTrue(repo(documentoAusente()).nicknameDisponible(""))
        assertTrue(repo(documentoAusente()).nicknameDisponible("   "))
    }

    @Test
    fun `un apodo sin reservar esta disponible`() = runBlocking {
        val r = repo(documentoAusente(), reserva = documentoAusente())

        assertTrue(r.nicknameDisponible("Nuevo"))
    }

    @Test
    fun `un apodo reservado por otro vecino NO esta disponible`() = runBlocking {
        val r = repo(documentoAusente(), reserva = documentoFake("apodo", "uid" to "otro"))

        assertFalse(r.nicknameDisponible("Tomado"))
    }

    @Test
    fun `mi propio apodo me sigue estando disponible`() = runBlocking {
        val r = repo(documentoAusente(), reserva = documentoFake("apodo", "uid" to "u1"))

        assertTrue(r.nicknameDisponible("ElMio"))
    }

    @Test
    fun `sin sesion ningun apodo esta disponible`() = runBlocking {
        val r = repo(documentoAusente(), uid = null, reserva = documentoAusente())

        assertFalse(r.nicknameDisponible("Cualquiera"))
    }

    @Test
    fun `la reserva del apodo se guarda en minuscula para que sea unica sin importar mayusculas`() = runBlocking {
        val reservaRef = mockk<DocumentReference>(relaxed = true).also {
            every { it.get() } returns tareaLista(documentoAusente())
        }
        val nicknames = mockk<CollectionReference>().also { every { it.document(any()) } returns reservaRef }
        val perfilRef = mockk<DocumentReference>(relaxed = true).also {
            every { it.get() } returns tareaLista(documentoAusente())
        }
        val usuarios = mockk<CollectionReference>().also { every { it.document(any()) } returns perfilRef }
        every { db.collection("usuarios") } returns usuarios
        every { db.collection("nicknames") } returns nicknames

        PerfilRepository(authFake("u1"), db).nicknameDisponible("  ElVecino  ")

        // "ElVecino" y "elvecino" deben chocar: por eso el doc id va en minúscula
        // y recortado. Si no, dos vecinos podrían quedarse con "el mismo" apodo.
        verify { nicknames.document("elvecino") }
    }
}
