package com.faceunlock.app

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy

object CameraUtil {
    /** Convert a CameraX frame to an upright Bitmap (applying rotation). */
    fun upright(proxy: ImageProxy): Bitmap? {
        val bitmap = try {
            proxy.toBitmap()
        } catch (e: Exception) {
            return null
        }
        val deg = proxy.imageInfo.rotationDegrees
        if (deg == 0) return bitmap
        val m = Matrix().apply { postRotate(deg.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }
}
