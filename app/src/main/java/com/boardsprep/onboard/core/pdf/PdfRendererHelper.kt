package com.boardsprep.onboard.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

import java.util.concurrent.TimeUnit

class PdfRendererHelper(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun getPdfCacheDirectory(): File {
        val dir = File(context.filesDir, "cached_pdfs")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun getOrDownloadPdfFile(urlOrPath: String): File? = withContext(Dispatchers.IO) {
        if (urlOrPath.isBlank()) return@withContext null

        // If it's already a local file path
        val localCandidate = File(urlOrPath)
        if (localCandidate.exists() && localCandidate.length() > 0) return@withContext localCandidate

        // Otherwise download and cache using MD5 or safe hash
        val cleanHash = Math.abs(urlOrPath.hashCode()).toString()
        val originalName = urlOrPath.substringAfterLast("/").substringBefore("?").ifEmpty { "document.pdf" }
        val fileName = "${cleanHash}_${originalName}"
        val cachedFile = File(getPdfCacheDirectory(), fileName)

        if (cachedFile.exists() && cachedFile.length() > 100) {
            return@withContext cachedFile
        }

        val tempFile = File(getPdfCacheDirectory(), "${fileName}.tmp")

        try {
            val request = Request.Builder()
                .url(urlOrPath)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) BoardsPrep/1.0")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext null
            }

            val body = response.body ?: return@withContext null
            FileOutputStream(tempFile).use { output ->
                body.byteStream().copyTo(output)
            }

            if (tempFile.exists() && tempFile.length() > 100) {
                if (cachedFile.exists()) cachedFile.delete()
                if (tempFile.renameTo(cachedFile)) {
                    cachedFile
                } else {
                    tempFile
                }
            } else {
                tempFile.delete()
                null
            }
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            null
        }
    }

    suspend fun renderPageToBitmap(pdfFile: File, pageIndex: Int, targetWidth: Int = 1080): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
                renderer.close()
                pfd.close()
                return@withContext null
            }

            val page = renderer.openPage(pageIndex)
            val aspectRatio = page.height.toFloat() / page.width.toFloat()
            val targetHeight = (targetWidth * aspectRatio).toInt()

            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            page.close()
            renderer.close()
            pfd.close()
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getPageCount(pdfFile: File): Int = withContext(Dispatchers.IO) {
        try {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val count = renderer.pageCount
            renderer.close()
            pfd.close()
            count
        } catch (e: Exception) {
            0
        }
    }
}
