package com.wineapp.data.file

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Нормализация ориентации фото по EXIF перед сжатием/отправкой на бэк.
 *
 * Проблема: BitmapFactory.decodeFile() игнорирует EXIF-ориентацию, а
 * Bitmap.compress() срезает EXIF. Если исходник (камера/галерея) имел
 * Orientation = 90/180/270, на бэк уходил повёрнутый JPEG без EXIF,
 * хотя Coil в UI применял EXIF и показывал фото нормально.
 *
 * Хелпер читает EXIF каждого файла и применяет поворот/отражение
 * к пикселям. Фиксированный поворот на 90° НЕ используется.
 */
object ImageOrientationHelper {

    private const val TAG = "ImageOrientationHelper"
    private const val MAX_DIMENSION = 2048

    /**
     * Декодирует файл в Bitmap с уже применённой EXIF-ориентацией.
     * Возвращает null, если файл не декодируется.
     */
    fun decodeNormalizedBitmap(imagePath: String, maxDimension: Int = MAX_DIMENSION): Bitmap? {
        return try {
            val file = File(imagePath)
            if (!file.exists()) return null

            // 1. Габариты без загрузки в память (защита от OOM на больших фото).
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(imagePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            // 2. Подбираем inSampleSize чтобы длинная сторона <= maxDimension.
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            var sampleSize = 1
            while (longest / sampleSize > maxDimension) sampleSize *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val raw = BitmapFactory.decodeFile(imagePath, opts) ?: return null

            // 3. Читаем EXIF именно исходного файла (до даунскейла это не важно —
            // ориентация не зависит от размера).
            val orientation = readExifOrientation(imagePath)
            if (orientation == ExifInterface.ORIENTATION_NORMAL ||
                orientation == ExifInterface.ORIENTATION_UNDEFINED
            ) {
                return raw
            }

            val matrix = orientationToMatrix(orientation) ?: return raw
            val rotated = try {
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "OOM while rotating bitmap", e)
                return raw
            }
            if (rotated !== raw) raw.recycle()
            rotated
        } catch (e: Exception) {
            Log.e(TAG, "decodeNormalizedBitmap failed: $imagePath", e)
            null
        }
    }

    /**
     * Кодирует файл в JPEG-байты с нормализованной ориентацией.
     * Используется перед Base64 для отправки на бэк.
     */
    fun encodeNormalizedJpeg(imagePath: String, quality: Int = 85): ByteArray? {
        val bitmap = decodeNormalizedBitmap(imagePath) ?: return null
        return try {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "encodeNormalizedJpeg failed", e)
            null
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Перезаписывает файл нормализованным JPEG (пиксели уже повёрнуты,
     * EXIF-ориентация сбрасывается). Нужно чтобы photoPath, показываемый
     * в Detail/SavedScans, совпадал с тем, что увидел бэк.
     * Возвращает true при успехе. При неудаче исходный файл не трогается.
     */
    fun normalizeFileInPlace(imageFile: File, quality: Int = 92): Boolean {
        return try {
            if (!imageFile.exists()) return false
            val orientation = readExifOrientation(imageFile.absolutePath)
            if (orientation == ExifInterface.ORIENTATION_NORMAL ||
                orientation == ExifInterface.ORIENTATION_UNDEFINED
            ) {
                return true // нормализовывать нечего
            }
            val bitmap = decodeNormalizedBitmap(imageFile.absolutePath) ?: return false
            try {
                // Пишем во временный файл рядом, затем атомарно заменяем.
                val tmp = File(imageFile.parent, "${imageFile.nameWithoutExtension}_norm.tmp")
                FileOutputStream(tmp).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)
                    fos.flush()
                }
                if (!tmp.renameTo(imageFile)) {
                    // renameTo может не сработать через разные FS — копируем вручную.
                    tmp.copyTo(imageFile, overwrite = true)
                    tmp.delete()
                }
                true
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "normalizeFileInPlace failed: ${imageFile.absolutePath}", e)
            false
        }
    }

    private fun readExifOrientation(imagePath: String): Int {
        return try {
            ExifInterface(imagePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (e: Exception) {
            Log.e(TAG, "readExifOrientation failed", e)
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    /**
     * Маппинг всех 8 значений EXIF-ориентации в Matrix.
     * Никаких фиксированных 90° — угол берётся из EXIF каждого фото.
     */
    private fun orientationToMatrix(orientation: Int): Matrix? {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_NORMAL,
            ExifInterface.ORIENTATION_UNDEFINED -> return null
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return null
        }
        return matrix
    }
}
