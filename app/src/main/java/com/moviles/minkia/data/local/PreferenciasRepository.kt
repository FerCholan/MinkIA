package com.moviles.minkia.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.moviles.minkia.data.model.Usuario
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** DataStore único del proceso, atado al Context de aplicación. */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "minkia_prefs")

/**
 * Preferencias persistentes de la app sobre DataStore. Envuelve el acceso para
 * que la UI hable con Flows y suspend, sin conocer las claves ni el origen.
 * Recibe el DataStore por constructor para poder testearlo con un store
 * temporal (sin Android). En producción se usa [create].
 */
class PreferenciasRepository(private val dataStore: DataStore<Preferences>) {

    // Preferencias de Configuración (C18). Defaults espejan los del layout.
    val notificacionesEstado: Flow<Boolean> = dataStore.data.map { it[NOTIF_ESTADO] ?: true }
    val notificacionesNovedades: Flow<Boolean> = dataStore.data.map { it[NOTIF_NOVEDADES] ?: true }
    val descargaSoloWifi: Flow<Boolean> = dataStore.data.map { it[DESCARGA_WIFI] ?: false }

    // Flags de primer arranque (usados por el ruteo de Permisos y el onboarding).
    val onboardingVisto: Flow<Boolean> = dataStore.data.map { it[ONBOARDING_VISTO] ?: false }
    val permisosVistos: Flow<Boolean> = dataStore.data.map { it[PERMISOS_VISTOS] ?: false }

    /** Sesión persistida: el usuario logueado, o null si no hay sesión activa. */
    val sesion: Flow<Usuario?> = dataStore.data.map { prefs ->
        val id = prefs[SESION_ID]
        if (id.isNullOrBlank()) null
        else Usuario(
            id = id,
            nombre = prefs[SESION_NOMBRE].orEmpty(),
            email = prefs[SESION_EMAIL].orEmpty(),
            esAdmin = prefs[SESION_ES_ADMIN] ?: false
        )
    }

    suspend fun setNotificacionesEstado(activo: Boolean) = guardar(NOTIF_ESTADO, activo)
    suspend fun setNotificacionesNovedades(activo: Boolean) = guardar(NOTIF_NOVEDADES, activo)
    suspend fun setDescargaSoloWifi(activo: Boolean) = guardar(DESCARGA_WIFI, activo)
    suspend fun marcarOnboardingVisto() = guardar(ONBOARDING_VISTO, true)
    suspend fun marcarPermisosVistos() = guardar(PERMISOS_VISTOS, true)

    /** Persiste la sesión del usuario logueado, para no volver a pedir login. */
    suspend fun guardarSesion(usuario: Usuario) {
        dataStore.edit {
            it[SESION_ID] = usuario.id
            it[SESION_NOMBRE] = usuario.nombre
            it[SESION_EMAIL] = usuario.email
            it[SESION_ES_ADMIN] = usuario.esAdmin
        }
    }

    /** Borra la sesión: el próximo arranque vuelve al login. */
    suspend fun cerrarSesion() {
        dataStore.edit {
            it.remove(SESION_ID)
            it.remove(SESION_NOMBRE)
            it.remove(SESION_EMAIL)
            it.remove(SESION_ES_ADMIN)
        }
    }

    private suspend fun guardar(clave: Preferences.Key<Boolean>, valor: Boolean) {
        dataStore.edit { it[clave] = valor }
    }

    companion object {
        private val NOTIF_ESTADO = booleanPreferencesKey("notif_estado")
        private val NOTIF_NOVEDADES = booleanPreferencesKey("notif_novedades")
        private val DESCARGA_WIFI = booleanPreferencesKey("descarga_solo_wifi")
        private val ONBOARDING_VISTO = booleanPreferencesKey("onboarding_visto")
        private val PERMISOS_VISTOS = booleanPreferencesKey("permisos_vistos")
        private val SESION_ID = stringPreferencesKey("sesion_id")
        private val SESION_NOMBRE = stringPreferencesKey("sesion_nombre")
        private val SESION_EMAIL = stringPreferencesKey("sesion_email")
        private val SESION_ES_ADMIN = booleanPreferencesKey("sesion_es_admin")

        /** Instancia de producción atada al DataStore del proceso. */
        fun create(context: Context): PreferenciasRepository =
            PreferenciasRepository(context.applicationContext.dataStore)
    }
}
