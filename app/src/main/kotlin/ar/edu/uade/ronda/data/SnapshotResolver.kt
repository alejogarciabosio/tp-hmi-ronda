package ar.edu.uade.ronda.data

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import ar.edu.uade.ronda.R

/** Imagen lista para mostrar: una foto decodificada o una ilustración simulada incluida en la app. */
sealed interface ResolvedSnapshot {
    data class Decoded(val bitmap: ImageBitmap) : ResolvedSnapshot
    data class Illustration(@DrawableRes val resId: Int) : ResolvedSnapshot
}

/**
 * Convierte la foto adjunta a un incidente en algo dibujable. Si el robot manda `data` (JPEG en
 * base64) se decodifica; si no, los ids `sim-*` del simulador se mapean a ilustraciones locales.
 * Si no se puede resolver devuelve null y la UI dice "Sin imagen disponible".
 */
object SnapshotResolver {
    private val simDrawables = mapOf(
        "sim-obstaculo" to R.drawable.snap_sim_obstaculo,
        "sim-persona" to R.drawable.snap_sim_persona,
        "sim-falla" to R.drawable.snap_sim_falla,
    )

    fun resolve(id: String, data: String?): ResolvedSnapshot? {
        if (data != null) {
            decode(data)?.let { return ResolvedSnapshot.Decoded(it) }
            return null
        }
        return simDrawables[id]?.let { ResolvedSnapshot.Illustration(it) }
    }

    private fun decode(base64: String): ImageBitmap? = try {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (_: IllegalArgumentException) {
        null
    }
}
