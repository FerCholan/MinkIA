package com.moviles.minkia.ui.common

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Encapsula el flujo de Credential Manager para obtener el ID token de Google.
 * La pantalla pide el token y se lo pasa al repositorio, que lo cambia por una
 * sesión Firebase. Así la UI no conoce Firebase ni el token por dentro.
 */
class AccesoGoogle(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)

    /**
     * Muestra el selector de cuentas de Google y devuelve el ID token elegido.
     * Lanza si el usuario cancela o no hay cuentas (lo maneja la Activity).
     */
    suspend fun obtenerIdToken(serverClientId: String): String {
        val opcion = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setFilterByAuthorizedAccounts(false) // ofrece todas las cuentas, no solo las ya usadas
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(opcion)
            .build()

        val respuesta = credentialManager.getCredential(context, request)
        val credencial = respuesta.credential

        if (credencial is CustomCredential &&
            credencial.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credencial.data).idToken
        }
        error("Respuesta de credencial inesperada de Google")
    }
}
