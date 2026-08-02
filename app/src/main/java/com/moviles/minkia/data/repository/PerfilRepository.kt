package com.moviles.minkia.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
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

    // Ya se comprobó que el perfil existe en esta instancia: no se repite la
    // lectura en cada campo que se guarda. Ver [asegurarPerfil].
    private var perfilAsegurado = false

    /**
     * Garantiza que exista `usuarios/{uid}` antes de escribirle un campo.
     *
     * El documento lo crea el registro, pero ese paso tiene un respaldo que lo
     * saltea si Firestore no responde en ese momento (ver
     * AuthFirebaseDataSource.sincronizarPerfil): esas cuentas quedan sin
     * documento y "Mis datos" no puede guardar NUNCA.
     *
     * Y no alcanza con un set + merge del campo suelto: para las reglas, escribir
     * sobre un documento que no existe es un CREATE, y el create de `usuarios`
     * exige `rol == 'ciudadano'` (ver firestore.rules). Un merge de solo `{dni}`
     * llegaría sin rol y la regla lo rechazaría con PERMISSION_DENIED. Por eso el
     * documento se crea acá completo, con la misma forma que usa el registro, y
     * recién después se mergea el campo editado.
     *
     * El rol se escribe SIEMPRE como ciudadano: es lo único que la regla acepta
     * desde el cliente, y el admin se promueve desde la consola.
     */
    private suspend fun asegurarPerfil(): Boolean {
        if (perfilAsegurado) return true
        val ref = docActual() ?: return false
        val user = auth.currentUser ?: return false
        if (!ref.get().await().exists()) {
            ref.set(
                mapOf(
                    "nombre" to (user.displayName?.takeIf { it.isNotBlank() }
                        ?: user.email?.substringBefore("@") ?: "Vecino"),
                    "email" to user.email.orEmpty(),
                    "dni" to "",
                    "rol" to ROL_CIUDADANO,
                    "creadoEn" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        perfilAsegurado = true
        return true
    }

    /** DNI guardado del usuario actual, o cadena vacía si no tiene o no hay sesión. */
    suspend fun obtenerDni(): String =
        docActual()?.get()?.await()?.getString("dni").orEmpty()

    /**
     * Actualiza solo el DNI: no pisa el resto del perfil ni el rol.
     *
     * set + merge y NO update, y esto vale para todo este archivo: `update` exige
     * que el documento YA exista y, si no está, falla con NOT_FOUND. El doc de
     * `usuarios/{uid}` se crea al registrarse, pero ese paso tiene un respaldo que
     * lo saltea cuando Firestore no responde en ese momento (ver
     * AuthFirebaseDataSource.sincronizarPerfil): esas cuentas quedaban SIN
     * documento, y entonces "Mis datos" no podía guardar nunca. Fallaba con el
     * aviso genérico "No se pudo cargar la información", que encima no dice nada
     * sobre lo que pasó. Con merge, el primer guardado crea el documento y los
     * siguientes solo tocan el campo.
     */
    suspend fun guardarDni(dni: String) {
        if (!asegurarPerfil()) return
        docActual()?.set(mapOf("dni" to dni), SetOptions.merge())?.await()
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
        if (!asegurarPerfil()) return
        val perfil = docActual() ?: return
        val nuevo = nickname.trim()
        val anterior = obtenerNickname().trim()
        if (nuevo.equals(anterior, ignoreCase = true)) return

        val refNuevo = if (nuevo.isNotEmpty()) nicknameDoc(nuevo) else null
        val refAnterior = if (anterior.isNotEmpty()) nicknameDoc(anterior) else null

        db.runTransaction { tx ->
            // Firestore exige TODAS las lecturas antes de cualquier escritura.
            val ocupado = refNuevo?.let { tx.get(it) }
            val existe = ocupado != null && ocupado.exists()
            val yaEsMio = existe && ocupado?.getString("uid") == yo

            if (existe && !yaEsMio) {
                throw FirebaseFirestoreException(
                    "Ese apodo ya está en uso",
                    FirebaseFirestoreException.Code.ABORTED
                )
            }
            refAnterior?.let { tx.delete(it) }
            // Solo se ESCRIBE la reserva si todavía no existe. Reescribir la propia
            // reserva parecía inofensivo, pero para las reglas un set sobre un
            // documento existente es un UPDATE, y `nicknames` no tiene allow update:
            // solo create y delete (ver firestore.rules). O sea que reescribirla
            // volvía con PERMISSION_DENIED y tumbaba TODO el guardado.
            //
            // Le pasa a quien tenga la reserva y el perfil desincronizados, que es
            // justo lo que dejaban las tres operaciones sueltas de antes: esos
            // vecinos no podían volver a guardar sus datos nunca más.
            if (refNuevo != null && !yaEsMio) tx.set(refNuevo, mapOf("uid" to yo))
            // merge, no update: el perfil puede no existir todavía (ver guardarDni).
            tx.set(perfil, mapOf("nickname" to nuevo), SetOptions.merge())
            null
        }.await()
    }

    /** ¿El vecino activó usar su apodo en los reportes? (por defecto no). */
    suspend fun obtenerUsarApodo(): Boolean =
        docActual()?.get()?.await()?.getBoolean("usarApodo") ?: false

    /** Activa o desactiva el uso del apodo en los reportes. */
    suspend fun guardarUsarApodo(activo: Boolean) {
        if (!asegurarPerfil()) return
        docActual()?.set(mapOf("usarApodo" to activo), SetOptions.merge())?.await()
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

    /**
     * Reescribe el campo `autor` de los reportes PROPIOS con el nombre visible de
     * ahora. Se llama después de guardar el apodo o la preferencia de privacidad.
     *
     * Hace falta porque `autor` es una FOTO del nombre en el momento de crear el
     * reporte (ver ReporteRepository.guardar): se guarda dentro del documento y
     * nadie lo vuelve a mirar. Sin esto, el vecino activaba su apodo y sus
     * reportes seguían mostrando su nombre real para siempre, que es exactamente
     * lo contrario de lo que pidió.
     *
     * ¿Y por qué está guardado dentro del reporte en vez de resolverse al leer?
     * Porque la regla de `usuarios` solo deja leer TU PROPIO documento: un vecino
     * no puede consultar el nombre de otro, así que el mapa no podría mostrar el
     * autor de los reportes ajenos. Guardarlo es la única forma de que ese dato
     * viaje; el precio es este re-sincronizado.
     *
     * Solo toca los que de verdad cambiaron, y en un lote: si el nombre ya es el
     * correcto no gasta una escritura. Las reglas lo permiten porque el dueño
     * puede editar su reporte mientras no cambie el userId ni el estado, y acá
     * solo se escribe `autor`.
     */
    suspend fun resincronizarAutorDeMisReportes() {
        val uid = uid() ?: return
        val nombre = nombreVisible()
        val mios = db.collection(REPORTES).whereEqualTo("userId", uid).get().await()

        val lote = db.batch()
        var cambios = 0
        for (doc in mios.documents) {
            if (doc.getString("autor") == nombre) continue
            lote.update(doc.reference, "autor", nombre)
            cambios++
        }
        if (cambios > 0) lote.commit().await()
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
        private const val REPORTES = "reportes"

        /** Único rol que las reglas aceptan al crear un perfil desde la app. */
        private const val ROL_CIUDADANO = "ciudadano"
    }
}
