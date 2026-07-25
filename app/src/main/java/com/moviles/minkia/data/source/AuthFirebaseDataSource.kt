package com.moviles.minkia.data.source

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.moviles.minkia.data.model.Usuario
import kotlinx.coroutines.tasks.await

/**
 * Autenticación REAL contra Firebase Auth (email + contraseña). Implementa el
 * mismo contrato que el mock, así el repositorio, el ViewModel y la UI no cambian.
 * Traduce las excepciones de Firebase (vienen en inglés y técnicas) a mensajes
 * claros en castellano para el vecino.
 */
class AuthFirebaseDataSource(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : AuthDataSource {

    override suspend fun iniciarSesion(email: String, password: String): Usuario = try {
        val resultado = auth.signInWithEmailAndPassword(email.trim(), password).await()
        sincronizarPerfil(resultado.user ?: error("Firebase no devolvió usuario"))
    } catch (e: FirebaseAuthInvalidUserException) {
        throw IllegalArgumentException("Correo o contraseña incorrectos. Prueba de nuevo.")
    } catch (e: FirebaseAuthInvalidCredentialsException) {
        throw IllegalArgumentException("Correo o contraseña incorrectos. Prueba de nuevo.")
    } catch (e: FirebaseNetworkException) {
        throw IllegalStateException("Sin conexión. Revisa tu internet e intenta de nuevo.")
    }

    override suspend fun registrar(
        nombre: String,
        email: String,
        dni: String,
        password: String
    ): Usuario = try {
        val resultado = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = resultado.user ?: error("Firebase no devolvió usuario")
        // Guarda el nombre visible en el perfil de Firebase (el DNI se persistirá
        // en Firestore cuando llegue ese paso; Auth no tiene un campo para eso).
        user.updateProfile(
            UserProfileChangeRequest.Builder().setDisplayName(nombre).build()
        ).await()
        sincronizarPerfil(user, nombreOverride = nombre, dni = dni)
    } catch (e: FirebaseAuthUserCollisionException) {
        throw IllegalArgumentException("Ese correo ya tiene una cuenta. Inicia sesión.")
    } catch (e: FirebaseAuthWeakPasswordException) {
        throw IllegalArgumentException("La contraseña es muy débil. Usa al menos 6 caracteres.")
    } catch (e: FirebaseNetworkException) {
        throw IllegalStateException("Sin conexión. Revisa tu internet e intenta de nuevo.")
    }

    override suspend fun iniciarSesionConGoogle(idToken: String): Usuario = try {
        val credencial = GoogleAuthProvider.getCredential(idToken, null)
        val resultado = auth.signInWithCredential(credencial).await()
        sincronizarPerfil(resultado.user ?: error("Firebase no devolvió usuario"))
    } catch (e: FirebaseNetworkException) {
        throw IllegalStateException("Sin conexión. Revisa tu internet e intenta de nuevo.")
    }

    override suspend fun recuperarPassword(email: String) {
        // Firebase envía el enlace solo si el correo existe; no revela si existe o
        // no (igual que el mock: evita enumeración de usuarios).
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    /**
     * Lee (o crea, en el primer ingreso) el perfil del usuario en Firestore y trae
     * su ROL real desde el servidor. Reemplaza el viejo hack de derivar el rol del
     * correo: en producción el rol NUNCA se decide en el cliente.
     *
     * Si Firestore no responde (sin red, base no creada todavía), NO rompe el login:
     * cae a un respaldo que SIEMPRE entra como ciudadano. El rol admin JAMÁS se
     * concede en el cliente: si el usuario es admin, se resolverá desde el servidor
     * en el próximo arranque con Firestore disponible.
     */
    private suspend fun sincronizarPerfil(
        user: FirebaseUser,
        nombreOverride: String? = null,
        dni: String? = null
    ): Usuario {
        val nombre = nombreOverride ?: user.displayName ?: user.email?.substringBefore("@") ?: "Vecino"
        val email = user.email.orEmpty()
        return try {
            val ref = db.collection(COLECCION_USUARIOS).document(user.uid)
            val snap = ref.get().await()
            if (!snap.exists()) {
                // Primer ingreso: crea el perfil del vecino con rol ciudadano.
                ref.set(
                    mapOf(
                        "nombre" to nombre,
                        "email" to email,
                        "dni" to dni.orEmpty(), // vacío si entró con Google o no lo cargó
                        "rol" to ROL_CIUDADANO,
                        "creadoEn" to FieldValue.serverTimestamp()
                    )
                ).await()
                Usuario(id = user.uid, nombre = nombre, email = email, esAdmin = false)
            } else {
                val rol = snap.getString("rol") ?: ROL_CIUDADANO
                val nombreGuardado = snap.getString("nombre") ?: nombre
                Usuario(id = user.uid, nombre = nombreGuardado, email = email, esAdmin = rol == ROL_ADMIN)
            }
        } catch (e: Exception) {
            // Respaldo: el login no debe caerse por Firestore. Degrada SIEMPRE a
            // ciudadano; el rol nunca se decide en el cliente (evita escalada de
            // privilegios si Firestore está degradado).
            Usuario(
                id = user.uid,
                nombre = nombre,
                email = email,
                esAdmin = false
            )
        }
    }

    companion object {
        private const val COLECCION_USUARIOS = "usuarios"
        private const val ROL_CIUDADANO = "ciudadano"
        private const val ROL_ADMIN = "admin"
    }
}
