package com.smartcooking.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

/** Photos picked for recognition, community posts and avatars all go through loadUploadJpeg. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UploadImageTest {
    private val context = RuntimeEnvironment.getApplication()

    private fun file(name: String, bytes: ByteArray): Uri = Uri.fromFile(File(context.cacheDir, name).apply { writeBytes(bytes) })

    private fun png(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(220, 60, 40)) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun size(jpeg: ByteArray): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, o)
        return o.outWidth to o.outHeight
    }

    @Test fun readsAPickedPhoto() {
        val jpeg = loadUploadJpeg(context, file("pick.png", png(64, 48)))
        assertTrue(jpeg.isNotEmpty())
        assertEquals(64 to 48, size(jpeg))
    }

    @Test fun boundsLargePhotosToTheUploadSize() {
        val (w, h) = size(loadUploadJpeg(context, file("large.png", png(3200, 2400))))
        assertEquals(1600, maxOf(w, h))
        assertEquals(1200, minOf(w, h))
    }

    @Test fun rejectsFilesThatAreNotImages() {
        val e = assertThrows(IllegalStateException::class.java) { loadUploadJpeg(context, file("note.txt", "not an image".toByteArray())) }
        assertEquals("图片格式不受支持", e.message)
    }

    @Test fun reportsAPhotoThatCannotBeOpened() {
        val missing = Uri.fromFile(File(context.cacheDir, "deleted.jpg"))
        val e = assertThrows(IllegalStateException::class.java) { loadUploadJpeg(context, missing) }
        assertEquals("无法读取图片", e.message)
    }
}
