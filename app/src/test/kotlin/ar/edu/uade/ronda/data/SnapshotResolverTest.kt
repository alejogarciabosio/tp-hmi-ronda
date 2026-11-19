package ar.edu.uade.ronda.data

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SnapshotResolverTest {
    private fun jpegBase64(): String {
        val bmp = Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    @Test
    fun `un base64 valido se decodifica a imagen`() {
        val r = SnapshotResolver.resolve("cam-1", jpegBase64())
        val decoded = assertIs<ResolvedSnapshot.Decoded>(r)
        assertEquals(16, decoded.bitmap.width)
    }

    @Test
    fun `un base64 que no es imagen devuelve null`() {
        assertNull(SnapshotResolver.resolve("cam-1", "esto no es una imagen!!"))
        assertNull(SnapshotResolver.resolve("cam-1", Base64.encodeToString("hola".toByteArray(), Base64.NO_WRAP)))
    }

    @Test
    fun `los ids del simulador se mapean a las ilustraciones incluidas`() {
        val r = assertIs<ResolvedSnapshot.Illustration>(SnapshotResolver.resolve("sim-obstaculo", null))
        assertEquals(ar.edu.uade.ronda.R.drawable.snap_sim_obstaculo, r.resId)
        assertIs<ResolvedSnapshot.Illustration>(SnapshotResolver.resolve("sim-persona", null))
        assertIs<ResolvedSnapshot.Illustration>(SnapshotResolver.resolve("sim-falla", null))
    }

    @Test
    fun `un id desconocido sin datos devuelve null`() {
        assertNull(SnapshotResolver.resolve("sim-otra-cosa", null))
    }
}
