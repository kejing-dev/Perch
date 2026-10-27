package io.embodit.perch

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import io.embodit.perch.ui.focus.FocusScreen
import io.embodit.perch.ui.theme.PerchTheme

/**
 * Perch: one Planar WindowContainer in Shared Space. First-open size comes from the manifest
 * (720×480 dp); windowConstraints only bounds interactive resizing (560×400 – 960×720).
 */
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultWindowContainer {
            PerchTheme {
                FocusScreen(
                    modifier = Modifier.windowConstraints(
                        minWidth = 560.dp,
                        minHeight = 400.dp,
                        maxWidth = 960.dp,
                        maxHeight = 720.dp,
                    ),
                )
            }
        }
    }
