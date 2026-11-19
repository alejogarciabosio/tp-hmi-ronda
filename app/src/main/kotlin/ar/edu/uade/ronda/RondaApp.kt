package ar.edu.uade.ronda

import android.app.Application

open class RondaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
    }

    /** Los tests de punta a punta arman el contenedor con tiempo virtual y Room en memoria. */
    protected open fun createContainer(): AppContainer = AppContainer(this)
}
