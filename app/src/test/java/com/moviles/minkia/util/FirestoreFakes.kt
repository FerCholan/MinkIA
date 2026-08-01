package com.moviles.minkia.util

import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.AggregateQuery
import com.google.firebase.firestore.AggregateQuerySnapshot
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import io.mockk.every
import io.mockk.mockk

/**
 * Dobles de Firestore y Auth para probar en JVM la lógica que hoy vive pegada a la
 * fuente de datos (gamificación, filtros, agrupación de zonas, textos de las
 * notificaciones, CSV). No hay emulador ni red.
 *
 * No es un mock "que devuelve siempre lo mismo": es un mini motor de consultas.
 * `whereEqualTo`, `whereIn`, los rangos, el `orderBy`, el `limit` y el `count()` se
 * resuelven DE VERDAD sobre la lista de documentos del escenario. Eso importa
 * porque desde que los filtros se mudaron al servidor, un test contra un mock
 * ciego pasaría igual aunque el código filtrara por el campo equivocado.
 *
 * Sobre las Task: `await()` de kotlinx-coroutines-play-services devuelve el
 * resultado sin suspender si la Task YA está completa. Por eso alcanza con
 * falsear isComplete/exception/isCanceled/result.
 */

fun <T> tareaLista(valor: T?): Task<T> = mockk<Task<T>>().also {
    every { it.isComplete } returns true
    every { it.isSuccessful } returns true
    every { it.isCanceled } returns false
    every { it.exception } returns null
    every { it.result } returns valor
}

fun <T> tareaFallida(error: Exception): Task<T> = mockk<Task<T>>().also {
    every { it.isComplete } returns true
    every { it.isSuccessful } returns false
    every { it.isCanceled } returns false
    every { it.exception } returns error
}

/**
 * Documento falso: responde a los getters tipados que usa la fuente de datos y
 * devuelve null para los campos ausentes, igual que Firestore cuando el campo no
 * está en el documento. Así se prueban de verdad los defaults del mapeo.
 */
fun documentoFake(id: String = "doc", vararg campos: Pair<String, Any?>): DocumentSnapshot {
    val mapa = campos.toMap()
    val doc = mockk<DocumentSnapshot>()
    every { doc.id } returns id
    every { doc.exists() } returns true
    every { doc.get(any<String>()) } answers { mapa[firstArg<String>()] }
    every { doc.getString(any()) } answers { mapa[firstArg<String>()] as? String }
    every { doc.getDouble(any()) } answers { (mapa[firstArg<String>()] as? Number)?.toDouble() }
    every { doc.getLong(any()) } answers { (mapa[firstArg<String>()] as? Number)?.toLong() }
    every { doc.getBoolean(any()) } answers { mapa[firstArg<String>()] as? Boolean }
    every { doc.getTimestamp(any()) } answers { mapa[firstArg<String>()] as? Timestamp }
    return doc
}

/** Documento inexistente (exists() == false), para los caminos de "no hay dato". */
fun documentoAusente(): DocumentSnapshot = mockk<DocumentSnapshot>().also {
    every { it.exists() } returns false
    every { it.get(any<String>()) } returns null
    every { it.getString(any()) } returns null
    every { it.getBoolean(any()) } returns null
}

fun snapshotDe(docs: List<DocumentSnapshot>): QuerySnapshot =
    mockk<QuerySnapshot>().also { every { it.documents } returns docs }

/**
 * Firestore falso: la colección [nombre] contiene [documentos] y responde a las
 * consultas resolviéndolas sobre esa lista.
 */
fun firestoreCon(nombre: String, documentos: List<DocumentSnapshot>): FirebaseFirestore {
    val db = mockk<FirebaseFirestore>()
    val coleccion = mockk<CollectionReference>()
    // CollectionReference ES una Query: se le enchufa el mismo motor.
    montarConsulta(coleccion, documentos)
    every { db.collection(nombre) } returns coleccion
    return db
}

/** Agrega al Firestore falso un documento puntual: `collection(col).document(id).get()`. */
fun conDocumento(
    db: FirebaseFirestore,
    coleccion: String,
    id: String,
    doc: DocumentSnapshot
): FirebaseFirestore {
    val ref = mockk<DocumentReference>().also { every { it.get() } returns tareaLista(doc) }
    val col = mockk<CollectionReference>().also { every { it.document(id) } returns ref }
    every { db.collection(coleccion) } returns col
    return db
}

// ---------- motor de consultas ----------

private fun consultaCon(docs: List<DocumentSnapshot>): Query =
    mockk<Query>().also { montarConsulta(it, docs) }

/**
 * Enchufa el motor sobre [q]: cada filtro devuelve una consulta NUEVA con los
 * documentos que sobreviven, así las cadenas (`whereX().whereY().orderBy()`) se
 * componen igual que en Firestore.
 */
private fun montarConsulta(q: Query, docs: List<DocumentSnapshot>) {
    every { q.get() } returns tareaLista(snapshotDe(docs))

    every { q.whereEqualTo(any<String>(), any()) } answers {
        val campo = firstArg<String>()
        val valor = secondArg<Any?>()
        consultaCon(docs.filter { it.get(campo) == valor })
    }

    every { q.whereIn(any<String>(), any()) } answers {
        val campo = firstArg<String>()
        val valores = secondArg<List<Any>>()
        consultaCon(docs.filter { it.get(campo) in valores })
    }

    // Los rangos descartan los documentos SIN el campo, igual que Firestore: un
    // documento sin `creadoEn` no entra en ninguna consulta por fecha.
    every { q.whereGreaterThanOrEqualTo(any<String>(), any()) } answers {
        val campo = firstArg<String>()
        val limite = secondArg<Any>()
        consultaCon(docs.filter { comparar(it.get(campo), limite)?.let { c -> c >= 0 } == true })
    }
    every { q.whereLessThanOrEqualTo(any<String>(), any()) } answers {
        val campo = firstArg<String>()
        val limite = secondArg<Any>()
        consultaCon(docs.filter { comparar(it.get(campo), limite)?.let { c -> c <= 0 } == true })
    }

    every { q.orderBy(any<String>(), any()) } answers {
        val campo = firstArg<String>()
        val direccion = secondArg<Query.Direction>()
        val ordenados = docs.sortedWith { a, b -> comparar(a.get(campo), b.get(campo)) ?: 0 }
        consultaCon(if (direccion == Query.Direction.DESCENDING) ordenados.reversed() else ordenados)
    }

    every { q.limit(any()) } answers { consultaCon(docs.take(firstArg<Long>().toInt())) }

    // count() agregado: el servidor devuelve solo el número.
    val agregado = mockk<AggregateQuery>()
    val snapshot = mockk<AggregateQuerySnapshot>().also { every { it.count } returns docs.size.toLong() }
    every { agregado.get(any()) } returns tareaLista(snapshot)
    every { q.count() } returns agregado
}

/** Compara dos valores del mismo tipo (Timestamp, número, texto). null si falta alguno. */
@Suppress("UNCHECKED_CAST")
private fun comparar(a: Any?, b: Any?): Int? {
    if (a == null || b == null) return null
    return (a as? Comparable<Any>)?.compareTo(b)
}

/** Auth falso. [uid] null = no hay sesión activa. */
fun authFake(
    uid: String? = "u1",
    displayName: String? = null,
    email: String? = null
): FirebaseAuth {
    val auth = mockk<FirebaseAuth>()
    if (uid == null) {
        every { auth.currentUser } returns null
    } else {
        val user = mockk<FirebaseUser>()
        every { user.uid } returns uid
        every { user.displayName } returns displayName
        every { user.email } returns email
        every { auth.currentUser } returns user
    }
    return auth
}
