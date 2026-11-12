package com.webclass.plus.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object ImageUtils {

    /**
     * Safely decode a bitmap from a file, downsampling it to fit the requested dimensions.
     */
    fun decodeSampledBitmapFromFile(filePath: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val file = File(filePath)
        if (!file.exists()) return null

        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(filePath, options)

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false

            val decoded = BitmapFactory.decodeFile(filePath, options) ?: return null

            // Read EXIF orientation to correct the rotation
            val orientation = try {
                val exif = ExifInterface(filePath)
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } catch (e: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            return rotateBitmapIfNeeded(decoded, orientation)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Helper to calculate standard inSampleSize.
     */
    fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Helper to rotate a bitmap based on EXIF orientation.
     */
    private fun rotateBitmapIfNeeded(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap // No rotation needed
        }

        return try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) {
                bitmap.recycle()
            }
            rotated
        } catch (e: Exception) {
            e.printStackTrace()
            bitmap
        }
    }

    /**
     * Safely reads an image from a Uri, resizes it so its longest dimension doesn't exceed [maxDimension],
     * and saves it to [destFile] as a compressed JPEG.
     */
    fun saveResizedImage(context: Context, uri: Uri, destFile: File, maxDimension: Int = 1920): Boolean {
        try {
            // First step: get dimensions of the source image without loading it into memory
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri).use { input ->
                if (input != null) {
                    BitmapFactory.decodeStream(input, null, options)
                }
            }

            // Calculate a safe sample size to avoid loading huge images into memory.
            options.inSampleSize = calculateInSampleSize(options, maxDimension, maxDimension)
            options.inJustDecodeBounds = false

            // Decode the sampled image into memory
            var bitmap = context.contentResolver.openInputStream(uri).use { input ->
                if (input != null) {
                    BitmapFactory.decodeStream(input, null, options)
                } else null
            } ?: return false

            // Read orientation from EXIF
            val orientation = try {
                context.contentResolver.openInputStream(uri).use { input ->
                    if (input != null) {
                        val exif = ExifInterface(input)
                        exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    } else {
                        ExifInterface.ORIENTATION_NORMAL
                    }
                }
            } catch (e: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            // Rotate if needed
            bitmap = rotateBitmapIfNeeded(bitmap, orientation)

            // Second step: scale to the exact desired maxDimension if necessary
            val width = bitmap.width
            val height = bitmap.height
            val longest = maxOf(width, height)

            if (longest > maxDimension) {
                val ratio = maxDimension.toFloat() / longest
                val targetWidth = (width * ratio).toInt()
                val targetHeight = (height * ratio).toInt()

                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
                if (scaledBitmap != bitmap) {
                    bitmap.recycle()
                    bitmap = scaledBitmap
                }
            }

            // Third step: save as JPEG with compression (quality 85)
            FileOutputStream(destFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }

            bitmap.recycle()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
