package com.moviles.minkia.data.source

import com.moviles.minkia.data.model.Usuario

/**
 * Contrato de autenticación. Abstrae el origen (mock o Firebase) para que el
 * repositorio dependa de la interfaz y no de una implementación concreta. Cambiar
 * de backend es cambiar la implementación inyectada, sin tocar repo, ViewModel ni UI.
 */
interface AuthDataSource {
    suspend fun iniciarSesion(email: String, password: String): Usuario
    suspend fun registrar(nombre: String, email: String, dni: String, password: String): Usuario
    suspend fun recuperarPassword(email: String)

    /** Intercambia el ID token de Google por una sesión. La PRIMERA vez crea la cuenta. */
    suspend fun iniciarSesionConGoogle(idToken: String): Usuario
}
