package io.embodit.perch.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import io.embodit.perch.mainApp

class SpatialApplication : Application() {
    lateinit var graph: PerchGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = PerchGraph(this)
        launch(::mainApp)
    }
}
