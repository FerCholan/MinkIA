package com.moviles.minkia.data.source

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.moviles.minkia.util.documentoAusente
import com.moviles.minkia.util.documentoFake
import com.moviles.minkia.util.tareaFallida
import com.moviles.minkia.util.tareaLista
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Autenticación real y, sobre todo, la resolución del ROL.
 *
 * Es la única frontera de privilegios de la app: quien resulte `esAdmin = true`
 * entra al panel de moderación. La regla es que el rol se lee del SERVIDOR y que
 * cualquier camino degradado (Firestore caído, documento sin rol, primer ingreso)
 * cae a ciudadano. Un test que falle acá es una escalada de privilegios.
 */
class AuthFirebaseDataSourceTest {

    private val auth = mockk<FirebaseAuth>()
    private val db = mockk<FirebaseFirestore>()
    private val docRef = mockk<DocumentReference>()

    private fun conUsuario(uid: String = "u1", email: String? = "vecino@chimbote.pe"): FirebaseUser =
        mockk<FirebaseUser>().also {
            every { it.uid } returns uid
            every { it.email } returns email
            every { it.displayName } returns "Fernando"
        }

    /** Login que resuelve bien en Auth y encuentra (o no) [perfil] en Firestore. */
    private fun fuenteCon(perfil: DocumentSnapshot): AuthFirebaseDataSource {
        val user = conUsuario()
        val resultado = mockk<AuthResult>().also { every { it.user } returns user }
        every { auth.signInWithEmailAndPassword(any(), any()) } returns tareaLista(resultado)

        every { docRef.get() } returns tareaLista(perfil)
        every { docRef.set(any()) } returns tareaLista(null)
        val col = mockk<CollectionReference>().also { every { it.document("u1") } returns docRef }
        every { db.collection("usuarios") } returns col

        return AuthFirebaseDataSource(auth, db)
    }

    // ---------- resolución del rol ----------

    @Test
    fun `el rol admin se toma del servidor`() = runBlocking {
        val fuente = fuenteCon(documentoFake("u1", "rol" to "admin", "nombre" to "Municipalidad"))

        val usuario = fuente.iniciarSesion("admin@minkia.pe", "123456")

        assertTrue(usuario.esAdmin)
        assertEquals("Municipalidad", usuario.nombre)
    }

    @Test
    fun `un vecino con rol ciudadano no entra al panel admin`() = runBlocking {
        val fuente = fuenteCon(documentoFake("u1", "rol" to "ciudadano"))

        assertFalse(fuente.iniciarSesion("vecino@chimbote.pe", "123456").esAdmin)
    }

    @Test
    fun `un perfil sin campo rol se trata como ciudadano`() = runBlocking {
        val fuente = fuenteCon(documentoFake("u1", "nombre" to "Fernando"))

        assertFalse(fuente.iniciarSesion("vecino@chimbote.pe", "123456").esAdmin)
    }

    @Test
    fun `un rol desconocido no concede privilegios`() = runBlocking {
        val fuente = fuenteCon(documentoFake("u1", "rol" to "superadmin"))

        assertFalse(fuente.iniciarSesion("x@y.pe", "123456").esAdmin)
    }

    @Test
    fun `si Firestore esta caido el login entra pero SIEMPRE como ciudadano`() = runBlocking {
        val user = conUsuario()
        val resultado = mockk<AuthResult>().also { every { it.user } returns user }
        every { auth.signInWithEmailAndPassword(any(), any()) } returns tareaLista(resultado)
        every { docRef.get() } returns tareaFallida(RuntimeException("firestore caído"))
        val col = mockk<CollectionReference>().also { every { it.document("u1") } returns docRef }
        every { db.collection("usuarios") } returns col

        val usuario = AuthFirebaseDataSource(auth, db).iniciarSesion("admin@minkia.pe", "123456")

        // El login no se cae...
        assertEquals("u1", usuario.id)
        // ...pero el rol NUNCA se decide en el cliente: degradar, jamás escalar.
        assertFalse(usuario.esAdmin)
    }

    // ---------- primer ingreso ----------

    @Test
    fun `el primer ingreso crea el perfil con rol ciudadano`() = runBlocking {
        val fuente = fuenteCon(documentoAusente())
        val guardado = slot<Map<String, Any>>()
        every { docRef.set(capture(guardado)) } returns tareaLista(null)

        val usuario = fuente.iniciarSesion("nuevo@chimbote.pe", "123456")

        assertFalse(usuario.esAdmin)
        assertEquals("ciudadano", guardado.captured["rol"])
        assertEquals("vecino@chimbote.pe", guardado.captured["email"])
        verify(exactly = 1) { docRef.set(any()) }
    }

    @Test
    fun `un perfil que ya existe no se pisa`() = runBlocking {
        val fuente = fuenteCon(documentoFake("u1", "rol" to "admin", "nombre" to "Municipalidad"))

        fuente.iniciarSesion("admin@minkia.pe", "123456")

        verify(exactly = 0) { docRef.set(any()) } // si lo pisara, degradaría al admin
    }

    // ---------- traducción de errores al vecino ----------

    @Test
    fun `una contrasena incorrecta no revela detalles tecnicos`() {
        every { auth.signInWithEmailAndPassword(any(), any()) } returns
            tareaFallida(mockk<FirebaseAuthInvalidCredentialsException>())

        val e = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { AuthFirebaseDataSource(auth, db).iniciarSesion("a@b.pe", "mala") }
        }
        assertEquals("Correo o contraseña incorrectos. Prueba de nuevo.", e.message)
    }

    @Test
    fun `un usuario inexistente da el MISMO mensaje que una contrasena mala`() {
        // Mismo texto a propósito: no filtra qué correos están registrados.
        every { auth.signInWithEmailAndPassword(any(), any()) } returns
            tareaFallida(mockk<FirebaseAuthInvalidUserException>())

        val e = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { AuthFirebaseDataSource(auth, db).iniciarSesion("nadie@b.pe", "x") }
        }
        assertEquals("Correo o contraseña incorrectos. Prueba de nuevo.", e.message)
    }

    @Test
    fun `sin conexion el login lo dice en castellano`() {
        every { auth.signInWithEmailAndPassword(any(), any()) } returns
            tareaFallida(mockk<FirebaseNetworkException>())

        val e = assertThrows(IllegalStateException::class.java) {
            runBlocking { AuthFirebaseDataSource(auth, db).iniciarSesion("a@b.pe", "x") }
        }
        assertEquals("Sin conexión. Revisa tu internet e intenta de nuevo.", e.message)
    }

    @Test
    fun `registrarse con un correo ya usado invita a iniciar sesion`() {
        every { auth.createUserWithEmailAndPassword(any(), any()) } returns
            tareaFallida(mockk<FirebaseAuthUserCollisionException>())

        val e = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { AuthFirebaseDataSource(auth, db).registrar("Ana", "a@b.pe", "12345678", "123456") }
        }
        assertEquals("Ese correo ya tiene una cuenta. Inicia sesión.", e.message)
    }

    @Test
    fun `una contrasena debil explica el minimo`() {
        every { auth.createUserWithEmailAndPassword(any(), any()) } returns
            tareaFallida(mockk<FirebaseAuthWeakPasswordException>())

        val e = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { AuthFirebaseDataSource(auth, db).registrar("Ana", "a@b.pe", "12345678", "123") }
        }
        assertTrue(e.message!!.contains("6 caracteres"))
    }

    @Test
    fun `recuperar contrasena limpia el correo antes de mandarlo`() = runBlocking {
        every { auth.sendPasswordResetEmail(any()) } returns tareaLista(null)

        AuthFirebaseDataSource(auth, db).recuperarPassword("  vecino@chimbote.pe  ")

        verify(exactly = 1) { auth.sendPasswordResetEmail("vecino@chimbote.pe") }
    }
}
