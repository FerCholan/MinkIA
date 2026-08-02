/**
 * Pruebas de las reglas de seguridad de Cloud Firestore (firestore.rules).
 *
 * A diferencia de las pruebas unitarias de la aplicacion, que verifican la logica
 * Kotlin contra fuentes de datos simuladas, estas ejecutan las reglas REALES sobre
 * el emulador de Firestore. Es la unica forma de demostrar que una cuenta de
 * ciudadano no puede ejecutar una operacion reservada al administrador.
 *
 * Ejecucion:
 *   cd tests/reglas && npm install && npm test
 */
import { before, after, describe, test } from 'node:test'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
import {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} from '@firebase/rules-unit-testing'
import { doc, getDoc, setDoc, updateDoc, deleteDoc, Timestamp } from 'firebase/firestore'

const AQUI = dirname(fileURLToPath(import.meta.url))
const RAIZ = resolve(AQUI, '..', '..')

const UID_VECINO = 'vecino_1'
const UID_OTRO_VECINO = 'vecino_2'
const UID_ADMIN = 'admin_1'
const ID_REPORTE = 'reporte_1'

let entorno

/** Reporte valido segun las reglas de creacion. */
function reporteValido(userId = UID_VECINO) {
  return {
    userId,
    estado: 'RECIBIDO',
    severidad: 'MEDIA',
    latitud: -9.0853,
    longitud: -78.5783,
    confianza: 73,
    autorNivel: 1,
    creadoEn: Timestamp.now(),
    descripcion: 'Foco de residuos en la esquina',
    tipo: 'Basura acumulada',
    fotoUrl: 'https://ejemplo/foto.jpg',
  }
}

before(async () => {
  entorno = await initializeTestEnvironment({
    projectId: 'minkia-reglas',
    firestore: {
      rules: readFileSync(resolve(RAIZ, 'firestore.rules'), 'utf8'),
      host: '127.0.0.1',
      port: 8080,
    },
  })

  // Semilla con las reglas desactivadas: el estado de partida no se somete a prueba.
  await entorno.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore()
    await setDoc(doc(db, 'usuarios', UID_VECINO), {
      rol: 'ciudadano', correo: 'vecino@chimbote.pe', nickname: 'vecino', dni: '', usarApodo: false,
    })
    await setDoc(doc(db, 'usuarios', UID_ADMIN), {
      rol: 'admin', correo: 'admin@minkia.pe',
    })
    await setDoc(doc(db, 'reportes', ID_REPORTE), reporteValido())
  })
})

after(async () => {
  await entorno?.cleanup()
})

const comoVecino = () => entorno.authenticatedContext(UID_VECINO).firestore()
const comoOtroVecino = () => entorno.authenticatedContext(UID_OTRO_VECINO).firestore()
const comoAdmin = () => entorno.authenticatedContext(UID_ADMIN).firestore()
const sinAutenticar = () => entorno.unauthenticatedContext().firestore()

describe('Coleccion usuarios', () => {
  test('CS01 el ciudadano NO puede cambiar su propio rol a admin', async () => {
    await assertFails(updateDoc(doc(comoVecino(), 'usuarios', UID_VECINO), { rol: 'admin' }))
  })

  test('CS02 el ciudadano SI puede editar su apodo y su documento', async () => {
    await assertSucceeds(
      updateDoc(doc(comoVecino(), 'usuarios', UID_VECINO), { nickname: 'vecino_nuevo', dni: '12345678' })
    )
  })

  test('CS03 el ciudadano NO puede editar el correo ni otros campos no declarados', async () => {
    await assertFails(
      updateDoc(doc(comoVecino(), 'usuarios', UID_VECINO), { correo: 'otro@chimbote.pe' })
    )
  })

  test('CS04 un vecino NO puede leer el documento de otro vecino', async () => {
    await assertFails(getDoc(doc(comoOtroVecino(), 'usuarios', UID_VECINO)))
  })

  test('CS05 el registro NO puede crear una cuenta con rol admin', async () => {
    await assertFails(
      setDoc(doc(comoOtroVecino(), 'usuarios', UID_OTRO_VECINO), { rol: 'admin', correo: 'x@y.pe' })
    )
  })
})

describe('Coleccion reportes', () => {
  test('CS06 el usuario no autenticado NO puede leer los reportes', async () => {
    await assertFails(getDoc(doc(sinAutenticar(), 'reportes', ID_REPORTE)))
  })

  test('CS07 el ciudadano autenticado SI puede leer los reportes de la comunidad', async () => {
    await assertSucceeds(getDoc(doc(comoVecino(), 'reportes', ID_REPORTE)))
  })

  test('CS08 el ciudadano NO puede crear un reporte a nombre de otro usuario', async () => {
    await assertFails(
      setDoc(doc(comoVecino(), 'reportes', 'reporte_ajeno'), reporteValido(UID_OTRO_VECINO))
    )
  })

  test('CS09 el ciudadano SI puede crear su propio reporte valido', async () => {
    await assertSucceeds(
      setDoc(doc(comoVecino(), 'reportes', 'reporte_propio'), reporteValido())
    )
  })

  test('CS10 el reporte NO puede nacer en un estado distinto de RECIBIDO', async () => {
    await assertFails(
      setDoc(doc(comoVecino(), 'reportes', 'reporte_resuelto'), {
        ...reporteValido(), estado: 'RESUELTO',
      })
    )
  })

  test('CS11 el reporte NO puede crearse con una confianza fuera de rango', async () => {
    await assertFails(
      setDoc(doc(comoVecino(), 'reportes', 'reporte_confianza'), {
        ...reporteValido(), confianza: 150,
      })
    )
  })

  test('CS12 el reporte NO puede crearse con una severidad inventada', async () => {
    await assertFails(
      setDoc(doc(comoVecino(), 'reportes', 'reporte_severidad'), {
        ...reporteValido(), severidad: 'CRITICA',
      })
    )
  })

  test('CS13 el autor NO puede elevar la severidad de su propio reporte', async () => {
    await assertFails(
      updateDoc(doc(comoVecino(), 'reportes', ID_REPORTE), { severidad: 'ALTA' })
    )
  })

  test('CS14 el autor NO puede marcar su reporte como resuelto', async () => {
    await assertFails(
      updateDoc(doc(comoVecino(), 'reportes', ID_REPORTE), { estado: 'RESUELTO' })
    )
  })

  test('CS15 el autor SI puede corregir la descripcion de su reporte', async () => {
    await assertSucceeds(
      updateDoc(doc(comoVecino(), 'reportes', ID_REPORTE), { descripcion: 'Descripcion corregida' })
    )
  })

  test('CS16 un vecino NO puede editar el reporte de otro vecino', async () => {
    await assertFails(
      updateDoc(doc(comoOtroVecino(), 'reportes', ID_REPORTE), { descripcion: 'Intruso' })
    )
  })

  test('CS17 el administrador SI puede cambiar el estado del reporte', async () => {
    await assertSucceeds(
      updateDoc(doc(comoAdmin(), 'reportes', ID_REPORTE), { estado: 'RESUELTO' })
    )
  })

  test('CS18 el administrador SI puede ajustar la severidad del reporte', async () => {
    await assertSucceeds(
      updateDoc(doc(comoAdmin(), 'reportes', ID_REPORTE), { severidad: 'ALTA' })
    )
  })

  test('CS19 el autor NO puede borrar su reporte', async () => {
    await assertFails(deleteDoc(doc(comoVecino(), 'reportes', ID_REPORTE)))
  })

  test('CS20 el administrador SI puede borrar un reporte', async () => {
    await assertSucceeds(deleteDoc(doc(comoAdmin(), 'reportes', 'reporte_propio')))
  })
})

describe('Coleccion nicknames', () => {
  test('CS21 el vecino NO puede reservar un apodo a nombre de otro uid', async () => {
    await assertFails(
      setDoc(doc(comoVecino(), 'nicknames', 'apodo_ajeno'), { uid: UID_OTRO_VECINO })
    )
  })

  test('CS22 el vecino SI puede reservar un apodo a su propio uid', async () => {
    await assertSucceeds(
      setDoc(doc(comoVecino(), 'nicknames', 'apodo_propio'), { uid: UID_VECINO })
    )
  })

  test('CS23 el vecino NO puede liberar el apodo de otro usuario', async () => {
    await entorno.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'nicknames', 'apodo_de_otro'), { uid: UID_OTRO_VECINO })
    })
    await assertFails(deleteDoc(doc(comoVecino(), 'nicknames', 'apodo_de_otro')))
  })
})
