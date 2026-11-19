package ar.edu.uade.ronda.e2e

import android.app.Activity
import android.os.Bundle
import androidx.room.Room
import ar.edu.uade.ronda.AppContainer
import ar.edu.uade.ronda.RondaApp
import ar.edu.uade.ronda.core.Clock
import ar.edu.uade.ronda.data.local.RondaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.setMain

/**
 * La aplicación real (`RondaApp` → `AppContainer` → simulador real, Room y `MainActivity`) con dos
 * cambios para que los tests sean rápidos y deterministas: el tiempo es virtual (un solo
 * [scheduler] para el simulador, los ViewModels y el reloj) y Room vive en memoria con ejecutores
 * sincrónicos.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class E2eApp : RondaApp() {
    val scheduler = TestCoroutineScheduler()
    val dispatcher = StandardTestDispatcher(scheduler)
    val clock = Clock { START_MS + scheduler.currentTime }

    override fun onCreate() {
        super.onCreate()
        // Main pasa al tiempo virtual justo antes de crear la actividad (los ViewModels lo toman al
        // crearse), no antes: si la regla de Compose lo viera al construirse, adoptaría este reloj y
        // al terminar intentaría vaciarlo, y el simulador nunca queda ocioso.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (!mainSet) { Dispatchers.setMain(dispatcher); mainSet = true }
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private var mainSet = false

    override fun createContainer(): AppContainer {
        val db = Room.inMemoryDatabaseBuilder(this, RondaDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        return AppContainer(this, clock, CoroutineScope(SupervisorJob() + dispatcher), db = db, seed = 42)
    }

    companion object { const val START_MS = 1_763_500_000_000L }
}
