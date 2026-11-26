package com.studywithme.app.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.LruCache
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Photos of problems live in app-private storage (and travel inside the backup ZIP). Large photos are shrunk on import. */
class PhotoStore(private val context: Context) {
    val dir = File(context.filesDir, "notes").apply { mkdirs() }
    private val cache = object : LruCache<String, Bitmap>(24 * 1024) { // KB
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    fun file(name: String) = File(dir, name)

    /** A temporary file and a content Uri for the system camera to write into. */
    fun newCapture(): Pair<File, Uri> {
        val tmp = File(context.cacheDir, "capture").apply { mkdirs() }.let { File(it, "${UUID.randomUUID()}.jpg") }
        return tmp to FileProvider.getUriForFile(context, "${context.packageName}.files", tmp)
    }

    /** Decodes [uri] (gallery or camera), shrinks it to at most 1600 px and stores it as JPEG. Returns the file name. */
    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
        val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val big = maxOf(info.size.width, info.size.height)
            if (big > MAX_PX) { val f = MAX_PX.toFloat() / big; decoder.setTargetSize((info.size.width * f).toInt(), (info.size.height * f).toInt()) }
        }
        val name = "${UUID.randomUUID()}.jpg"
        file(name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        name
    }

    fun delete(name: String?) {
        if (name != null) file(name).delete()
        cache.evictAll()
    }

    /** A bitmap no larger than about [maxPx] on its long side, or null if the file is missing. */
    suspend fun load(name: String, maxPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = "$name@$maxPx"
        cache.get(key) ?: run {
            val f = file(name)
            if (!f.exists()) return@run null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(f.path, it) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })?.also { cache.put(key, it) }
        }
    }

    private companion object { const val MAX_PX = 1600 }
}
