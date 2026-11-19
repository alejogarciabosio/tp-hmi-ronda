package ar.edu.uade.ronda.ui.supervision

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.painter.Painter
import ar.edu.uade.ronda.data.ResolvedSnapshot
import ar.edu.uade.ronda.data.SnapshotResolver
import ar.edu.uade.ronda.ui.components.CommandButton
import ar.edu.uade.ronda.ui.components.PreviewSurface
import ar.edu.uade.ronda.ui.model.CommandButtonUi
import ar.edu.uade.ronda.ui.model.CommandKind
import ar.edu.uade.ronda.ui.theme.RondaColors
import ar.edu.uade.ronda.ui.theme.RondaType

@Composable
private fun ResolvedSnapshot.painter(): Painter = when (this) {
    is ResolvedSnapshot.Decoded -> BitmapPainter(bitmap)
    is ResolvedSnapshot.Illustration -> painterResource(resId)
}

/** Foto 16:9 con su pie. Tocarla la abre a pantalla completa; sin imagen lo dice con texto. */
@Composable
fun SnapshotImage(ui: SnapshotUi, modifier: Modifier = Modifier) {
    val resolved = remember(ui.id, ui.data) { SnapshotResolver.resolve(ui.id, ui.data) }
    var enlarged by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val frame = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(RondaColors.ink950, RoundedCornerShape(4.dp))
            .border(1.dp, RondaColors.ink700, RoundedCornerShape(4.dp))
        if (resolved != null) {
            Image(
                resolved.painter(),
                contentDescription = ui.contentDescription,
                contentScale = ContentScale.Crop,
                modifier = frame.clickable(onClickLabel = "Ver en grande", role = Role.Button) { enlarged = true },
            )
        } else {
            Box(frame.semantics(mergeDescendants = true) { contentDescription = "Sin imagen disponible" }, contentAlignment = Alignment.Center) {
                Text("Sin imagen disponible", style = RondaType.textoCuerpo, color = RondaColors.bone400)
            }
        }
        Text(ui.caption, style = RondaType.textoNota, color = RondaColors.bone400)
    }
    if (enlarged && resolved != null) {
        Dialog(onDismissRequest = { enlarged = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(
                Modifier.fillMaxSize().background(RondaColors.ink950).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Image(
                        resolved.painter(), ui.contentDescription,
                        contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    )
                }
                Text(ui.caption, style = RondaType.textoNota, color = RondaColors.bone400)
                CommandButton(CommandButtonUi("Cerrar", CommandKind.SECONDARY), { enlarged = false })
            }
        }
    }
}

private fun sample(id: String, caption: String) = SnapshotUi(id, null, caption, "Foto de la cámara frontal del robot: Obstáculo en el pasillo")

@Preview(showBackground = true, backgroundColor = 0xFF1C1B17, widthDp = 412)
@Composable
private fun SnapshotObstaclePreview() = PreviewSurface {
    SnapshotImage(sample("sim-obstaculo", "Cámara frontal · hace 4 s"), Modifier.padding(16.dp))
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1B17, widthDp = 412)
@Composable
private fun SnapshotPersonPreview() = PreviewSurface {
    SnapshotImage(sample("sim-persona", "Cámara frontal · hace 4 s"), Modifier.padding(16.dp))
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1B17, widthDp = 412)
@Composable
private fun SnapshotFaultPreview() = PreviewSurface {
    SnapshotImage(sample("sim-falla", "Cámara frontal · hace 4 s"), Modifier.padding(16.dp))
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1B17, widthDp = 412)
@Composable
private fun SnapshotStalePreview() = PreviewSurface {
    SnapshotImage(sample("sim-obstaculo", "Imagen del momento del incidente"), Modifier.padding(16.dp))
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1B17, widthDp = 412)
@Composable
private fun SnapshotMissingPreview() = PreviewSurface {
    SnapshotImage(sample("desconocida", "Cámara frontal · hace 4 s"), Modifier.padding(16.dp))
}
