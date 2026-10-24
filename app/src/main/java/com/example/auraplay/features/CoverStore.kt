package com.example.auraplay.features

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale

object CoverStore {
    suspend fun save(context: Context, uri: Uri): String {
        // Coil honors EXIF orientation and samples large camera images before decoding.
        val result = context.imageLoader.execute(ImageRequest.Builder(context).data(uri).size(512).scale(Scale.FIT).allowHardware(false).build())
        require(result is SuccessResult) { "Could not read the selected image." }
        val bitmap = result.drawable.toBitmap()
        val bytes = ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out); out.toByteArray() }
        return storeBytes(context, bytes)
    }

    fun storeBytes(context: Context, bytes: ByteArray): String {
        require(bytes.size <= 2_000_000) { "Cover image is too large." }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 1..4096 && bounds.outHeight in 1..4096) { "The cover image is invalid or too large." }
        val name = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } + ".jpg"
        val folder = File(context.filesDir, "covers").apply { mkdirs() }
        val file = File(folder, name)
        if (!file.exists()) file.writeBytes(bytes)
        return Uri.fromFile(file).toString()
    }
}
