# MinkIA — Arquitectura (Android, MVVM)

App Android nativa (Kotlin), **Material 3 sin Jetpack Compose** (layouts XML + ViewBinding), patrón **MVVM**. El diseño (mockups, wireframes, marca, Word) vive aparte en `design/`.

## Capas

```
com.moviles.minkia
├── core/                 Piezas reutilizables por TODA pantalla
│   ├── UiState<T>        Loading | Success(data) | Error(mensaje)
│   ├── BaseViewModel     loadInto(target) { ... }  → emite el ciclo de estados
│   └── BaseFragment<VB>  ViewBinding + ciclo de vida resueltos
├── data/                 Origen de datos (hoy mock; mañana Firebase/REST)
│   ├── model/            Modelos de dominio (data class)
│   ├── source/           DataSources (de dónde salen los datos)
│   └── repository/       Único punto de acceso a datos para la UI
└── ui/                   Vista
    ├── MainActivity      Contenedor de fragments
    └── <pantalla>/       Fragment + ViewModel (+ Adapter si hay lista)
```

**Regla de oro:** la vista (Fragment) NUNCA toca un DataSource ni contiene lógica de negocio. Solo habla con su ViewModel y observa `UiState`. El ViewModel habla solo con el Repository. Cambiar el origen de datos (mock → Firebase) se hace **únicamente** dentro de `data/`, sin tocar `ui/`.

## Flujo de datos

```
Fragment  --(evento)-->  ViewModel  --(suspend)-->  Repository  -->  DataSource
Fragment  <--observa--   LiveData<UiState<T>>  <--emite--  ViewModel
```

## Cómo agregar una pantalla nueva (ej. "Perfil")

1. **Modelo** (si hace falta): `data/model/Perfil.kt`.
2. **DataSource + Repository**: agregá el método en el datasource mock y exponelo en un repository (`data/repository/PerfilRepository.kt`).
3. **ViewModel**: extiende `BaseViewModel`, expone `LiveData<UiState<Perfil>>` y carga con `loadInto`.
   ```kotlin
   class PerfilViewModel(private val repo: PerfilRepository = PerfilRepository()) : BaseViewModel() {
       private val _uiState = MutableLiveData<UiState<Perfil>>()
       val uiState: LiveData<UiState<Perfil>> = _uiState
       init { cargar() }
       fun cargar() = loadInto(_uiState) { repo.obtener() }
       class Factory(...) : ViewModelProvider.Factory { ... }
   }
   ```
4. **Layout**: `res/layout/fragment_perfil.xml` (genera `FragmentPerfilBinding`).
5. **Fragment**: extiende `BaseFragment<FragmentPerfilBinding>`, implementá `inflateBinding` y `onViewReady`, observá `uiState` con un `when` sobre `UiState`.
6. **Navegación**: cargalo desde `MainActivity` (o el contenedor que corresponda).

La pantalla **Home** (`ui/home/`) es el ejemplo completo a copiar.

## Stack

Kotlin 2.0.21 · AGP 9.0.1 · Gradle 9.2.1 · Material 3 · minSdk 24 / target 36 · ViewBinding · lifecycle-viewmodel/livedata · coroutines · fragment-ktx · recyclerview. Dependencias en `gradle/libs.versions.toml`.

## Convenciones

- Paquete base `com.moviles.minkia`. Código y UI en **español** (tuteo peruano).
- Paleta de marca en `res/values/colors.xml` (verde bosque `#1B4228`, verde hoja `#69802D`, naranja terracota `#BD4C18`).
- Listas: `ListAdapter` + `DiffUtil`, nunca `notifyDataSetChanged`.
- Sin guion largo (—) en textos visibles.
