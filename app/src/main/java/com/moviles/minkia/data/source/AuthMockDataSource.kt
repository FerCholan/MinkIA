package com.moviles.minkia.data.source

import com.moviles.minkia.data.model.Usuario
import kotlinx.coroutines.delay

/**
 * Fuente de datos de autenticación de prueba. Simula la latencia de una llamada
 * remota y valida contra una única credencial fija. Cuando exista el backend
 * (Firebase Auth/REST), se reemplaza esta clase sin tocar el repositorio ni el
 * ViewModel.
 */
class AuthMockDataSource {

    suspend fun iniciarSesion(email: String, password: String): Usuario {
        delay(900) // simula la red

        if (email.equals(EMAIL_DEMO, ignoreCase = true) && password == PASSWORD_DEMO) {
            return Usuario(id = "U-001", nombre = "Vecino de Chimbote", email = email)
        }
        if (email.equals(EMAIL_ADMIN, ignoreCase = true) && password == PASSWORD_DEMO) {
            return Usuario(id = "A-001", nombre = "Municipalidad", email = email, esAdmin = true)
        }
        throw IllegalArgumentException("Correo o contraseña incorrectos. Probá de nuevo.")
    }

    suspend fun registrar(
        nombre: String,
        email: String,
        dni: String,
        password: String
    ): Usuario {
        delay(900) // simula la red

        // Simula que el correo de prueba ya está tomado, para ejercitar el error.
        if (email.equals(EMAIL_DEMO, ignoreCase = true)) {
            throw IllegalArgumentException("Ese correo ya tiene una cuenta. Iniciá sesión.")
        }
        return Usuario(id = "U-${dni.ifBlank { "NEW" }}", nombre = nombre, email = email)
    }

    suspend fun recuperarPassword(email: String) {
        delay(900) // simula la red
        // Siempre confirma: no revelamos si el correo existe (evita enumeración
        // de usuarios). El backend real enviaría el enlace solo si corresponde.
    }

    companion object {
        /** Credenciales de prueba mientras no hay backend. */
        const val EMAIL_DEMO = "vecino@chimbote.pe"
        const val EMAIL_ADMIN = "admin@minkia.pe"
        const val PASSWORD_DEMO = "123456"
    }
}
