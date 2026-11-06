package com.trustarx.pitgo.features.registrationverification.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class RegistrationDocumentCaptureFiles(context: Context) {
    private val app = context.applicationContext
    private val root = File(app.noBackupFilesDir,"registration-documents")
    fun create(user: String): Pair<File,Uri> {
        require(Regex("[0-9a-f-]{36}").matches(user))
        val file = File(File(root,user).apply { mkdirs() },"${UUID.randomUUID()}.jpg")
        return file to FileProvider.getUriForFile(app,"${app.packageName}.registration-files",file)
    }
    suspend fun normalized(file: File): ByteArray = withContext(Dispatchers.IO) {
        try {
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeFile(file.path,bounds)
            require(bounds.outWidth>0 && bounds.outHeight>0)
            val sample=BitmapFactory.Options().apply { inSampleSize=1
                while(bounds.outWidth/inSampleSize>2560 || bounds.outHeight/inSampleSize>2560)inSampleSize*=2 }
            val bitmap=requireNotNull(BitmapFactory.decodeFile(file.path,sample))
            val exif=ExifInterface(file)
            val transform=Matrix().apply {
                if(exif.isFlipped)postScale(-1f,1f)
                postRotate(exif.rotationDegrees.toFloat())
            }
            val oriented=Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,transform,true)
            try { java.io.ByteArrayOutputStream().use { out ->
                check(oriented.compress(Bitmap.CompressFormat.JPEG,90,out))
                out.toByteArray().also { require(it.size in 1..10485760) }
            }} finally { if(oriented!==bitmap)oriented.recycle();bitmap.recycle() }
        } finally { file.delete() }
    }
    fun clear() { root.deleteRecursively() }
}
