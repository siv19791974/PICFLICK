package com.picflick.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

/**
 * Utility for resizing images to create thumbnails.
 * Generates 256px and 512px JPEG thumbnails with configurable quality.
 */
object ImageResizer {

    const val THUMBNAIL_SIZE_512 = 512
    const val THUMBNAIL_SIZE_1080 = 1080
    const val THUMBNAIL_QUALITY = 85

    /**
     * Resize an image from a Uri to a square-ish max-dimension thumbnail.
     *
     * @param context Android context for ContentResolver
     * @param photoUri Source image URI
     * @param maxDimension Max width/height in pixels
     * @param quality JPEG compression quality (0-100)
     * @return JPEG-encoded ByteArray or null if failed
     */
    fun resizeToThumbnail(
        context: Context,
        photoUri: Uri,
        maxDimension: Int,
        quality: Int = THUMBNAIL_QUALITY
    ): ByteArray? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(photoUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }

            // Calculate sample size to load a smaller bitmap first
            options.inSampleSize = calculateInSampleSize(options, maxDimension, maxDimension)
            options.inJustDecodeBounds = false

            val bitmap = context.contentResolver.openInputStream(photoUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: return null

            val scaledBitmap = scaleBitmap(bitmap, maxDimension)
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)

            // Clean up bitmaps
            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }
            bitmap.recycle()

            outputStream.toByteArray()
        } catch (e: Exception) {
            android.util.Log.w("ImageResizer", "Failed to resize image to $maxDimension", e)
            null
        }
    }

    /**
     * Resize a ByteArray image to a thumbnail.
     * Useful when image is already in memory as bytes.
     */
    fun resizeBytesToThumbnail(
        imageBytes: ByteArray,
        maxDimension: Int,
        quality: Int = THUMBNAIL_QUALITY
    ): ByteArray? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

            options.inSampleSize = calculateInSampleSize(options, maxDimension, maxDimension)
            options.inJustDecodeBounds = false

            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
                ?: return null

            val scaledBitmap = scaleBitmap(bitmap, maxDimension)
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)

            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }
            bitmap.recycle()

            outputStream.toByteArray()
        } catch (e: Exception) {
            android.util.Log.w("ImageResizer", "Failed to resize bytes to $maxDimension", e)
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }

        val ratio = width.toFloat() / height.toFloat()
        val (newWidth, newHeight) = if (width > height) {
            maxDimension to (maxDimension / ratio).toInt()
        } else {
            (maxDimension * ratio).toInt() to maxDimension
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
