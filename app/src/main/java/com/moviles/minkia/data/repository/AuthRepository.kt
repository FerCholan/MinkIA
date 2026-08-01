package com.moviles.minkia.data.repository

import com.moviles.minkia.data.model.Usuario
import com.moviles.minkia.data.source.AuthDataSource
import com.moviles.minkia.data.source.AuthFirebaseDataSource

/**
 * Punto único de acceso al flujo de autenticación. El ViewModel habla solo con
 * el repositorio, nunca con la fuente de datos directamente. Cambiar el origen
 * (mock -> Firebase/REST) se hace acá adentro, sin afectar al resto de la app.
 */
class AuthRepository(
    private val dataSource: AuthDataSource = AuthFirebaseDataSource()
) {
    suspend fun iniciarSesion(email: String, password: String): Usuario =
        dataSource.iniciarSesion(email, password)

    suspend fun registrar(
        nombre: String,
        email: String,
        dni: String,
        password: String
    ): Usuario = dataSource.registrar(nombre, email, dni, password)

    suspend fun recuperarPassword(email: String) = dataSource.recuperarPassword(email)

    suspend fun iniciarSesionConGoogle(idToken: String): Usuario =
        dataSource.iniciarSesionConGoogle(idToken)
}
