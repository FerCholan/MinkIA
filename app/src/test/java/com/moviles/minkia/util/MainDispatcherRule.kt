package com.moviles.minkia.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Regla JUnit que reemplaza el dispatcher Main por uno de test durante cada
 * prueba. Necesaria porque BaseViewModel.loadInto lanza corrutinas en
 * viewModelScope (que usa Dispatchers.Main). Con UnconfinedTestDispatcher las
 * corrutinas corren de forma ansiosa, así el estado queda disponible apenas se
 * dispara la acción, sin tener que avanzar el reloj manualmente.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
