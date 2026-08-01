package com.moviles.minkia.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await

/**
 * Acceso al perfil del usuario logueado en Firestore (colección `usuarios`).
 * Maneja el DNI y el APODO (nickname) del perfil progresivo: el vecino los completa
 * cuando quiere. El nickname, si está puesto, aparece en sus reportes en lugar del
 * nombre real (privacidad), y es ÚNICO entre todos los usuarios. El rol NO se toca
 * desde acá: solo se promueve a admin desde la consola/servidor.
 */
class PerfilRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun uid() = auth.currentUser?.uid
    private fun docActual() = uid()?.let { db.collection(USUARIOS).document(it) }

    /** DNI guardado del usuario actual, o cadena vacía si no tiene o no hay sesión. */
    suspend fun obtenerDni(): String =
        docActual()?.get()?.await()?.getString("dni").orEmpty()

    /** Actualiza solo el DNI (merge): no pisa el resto del perfil ni el rol. */
    suspend fun guardarDni(dni: String) {
        docActual()?.update("dni", dni)?.await()
    }

    /** Apodo guardado del usuario actual (vacío si no puso). */
    suspend fun obtenerNickname(): String =
        docActual()?.get()?.await()?.getString("nickname").orEmpty()

    // Reserva de apodos: colección pública `nicknames`, doc id = apodo en minúscula
    // (unicidad case-insensitive), valor { uid }. No guarda datos privados, así las
    // reglas pueden dejar que cualquiera consulte disponibilidad sin exponer perfiles.
    private fun nicknameDoc(nickname: String) =
        db.collection(NICKNAMES).document(nickname.trim().lowercase())

    /**
     * ¿El apodo está libre? Lee su doc de reserva: libre si no existe o ya es mío.
     * Vacío siempre está libre (es opcional).
     */
    suspend fun nicknameDisponible(nickname: String): Boolean {
        val limpio = nickname.trim()
        if (limpio.isEmpty()) return true
        val yo = uid() ?: return false
        val snap = nicknameDoc(limpio).get().await()
        return !snap.exists() || snap.getString("uid") == yo
    }

    /**
     * Guarda el apodo de forma ATÓMICA: libera la reserva anterior, reserva la nueva
     * y actualiza el perfil, todo dentro de una transacción de Firestore. Si viene
     * vacío, solo libera.
     *
     * Antes eran tres operaciones sueltas y en ese orden: borrar la reserva vieja,
     * crear la nueva y escribir el perfil. Si la del medio fallaba (porque otro
     * vecino se había quedado con ese apodo entre el chequeo de disponibilidad y la
     * escritura, que es una condición de carrera real), el usuario terminaba sin su
     * apodo anterior, sin el nuevo y con el perfil apuntando a uno que no tenía
     * reservado. Ahora, o pasan las tres cosas, o no pasa ninguna.
     *
     * La verificación de que el apodo sigue libre se hace DENTRO de la transacción,
     * que es lo único que cierra la ventana de carrera: [nicknameDisponible] sirve
     * para avisar temprano en la UI, no como garantía.
     */
    suspend fun guardarNickname(nickname: String) {
        val yo = uid() ?: return
        val perfil = docActual() ?: return
        val nuevo = nickname.trim()
        val anterior = obtenerNickname().trim()
        if (nuevo.equals(anterior, ignoreCase = true)) return

        val refNuevo = if (nuevo.isNotEmpty()) nicknameDoc(nuevo) else null
        val refAnterior = if (anterior.isNotEmpty()) nicknameDoc(anterior) else null

        db.runTransaction { tx ->
            // Firestore exige TODAS las lecturas antes de cualquier escritura.
            val ocupado = refNuevo?.let { tx.get(it) }
            if (ocupado != null && ocupado.exists() && ocupado.getString("uid") != yo) {
                throw FirebaseFirestoreException(
                    "Ese apodo ya está en uso",
                    FirebaseFirestoreException.Code.ABORTED
                )
            }
            refAnterior?.let { tx.delete(it) }
            refNuevo?.let { tx.set(it, mapOf("uid" to yo)) }
            tx.update(perfil, "nickname", nuevo)
            null
        }.await()
    }

    /** ¿El vecino activó usar su apodo en los reportes? (por defecto no). */
    suspend fun obtenerUsarApodo(): Boolean =
        docActual()?.get()?.await()?.getBoolean("usarApodo") ?: false

    /** Activa o desactiva el uso del apodo en los reportes. */
    suspend fun guardarUsarApodo(activo: Boolean) {
        docActual()?.update("usarApodo", activo)?.await()
    }

    /**
     * Nombre a MOSTRAR en los reportes: el apodo SOLO si el vecino lo activó y lo
     * puso; si no, el nombre real (displayName de Auth) y, en última instancia,
     * "Vecino". Es lo que se guarda con cada reporte como autor.
     */
    suspend fun nombreVisible(): String {
        val doc = docActual()?.get()?.await()
        val apodo = doc?.getString("nickname").orEmpty().trim()
        val usar = doc?.getBoolean("usarApodo") ?: false
        if (usar && apodo.isNotEmpty()) return apodo
        val user = auth.currentUser
        return user?.displayName?.takeIf { it.isNotBlank() }
            ?: user?.email?.substringBefore("@")
            ?: "Vecino"
    }

    /** Nombre real (displayName), ignorando el apodo. Para mostrar en el perfil propio. */
    fun nombreReal(): String {
        val user = auth.currentUser
        return user?.displayName?.takeIf { it.isNotBlank() }
            ?: user?.email?.substringBefore("@")
            ?: "Vecino"
    }

    companion object {
        private const val USUARIOS = "usuarios"
        private const val NICKNAMES = "nicknames"
    }
}
