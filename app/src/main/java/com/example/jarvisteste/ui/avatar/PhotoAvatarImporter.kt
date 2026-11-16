package com.example.jarvisteste.ui.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min

/**
 * Prepara um retrato escolhido pelo usuário:
 *
 *  1. decodifica e corrige a rotação do EXIF;
 *  2. reduz para no máximo 1600 px no maior lado (economia de memória);
 *  3. localiza o maior rosto com o ML Kit (modelo embutido, funciona offline);
 *  4. extrai boca e olhos para o avatar animado;
 *  5. gera uma paleta de fundo a partir da própria foto.
 *
 * Tudo acontece no aparelho; a imagem nunca é enviada para fora.
 */
class PhotoAvatarImporter(context: Context) {

    private val appContext = context.applicationContext

    suspend fun import(uri: Uri): Result<ImportedPhoto> = runCatching {
        withContext(Dispatchers.IO) {
            val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Não consegui abrir essa imagem.")
            analyze(bytes)
        }
    }

    /** Variante usada pelos testes via ADB, lendo um arquivo local do app. */
    suspend fun importFile(file: File): Result<ImportedPhoto> = runCatching {
        withContext(Dispatchers.IO) {
            if (!file.isFile) error("Arquivo de teste não encontrado: ${file.name}")
            analyze(file.readBytes())
        }
    }

    private suspend fun analyze(bytes: ByteArray): ImportedPhoto {
        val bitmap = decode(bytes) ?: error("Não consegui abrir essa imagem.")
        try {
            val face = detectFace(bitmap) ?: error(
                "Não encontrei um rosto nessa foto. Escolha uma imagem com o rosto " +
                    "de frente e bem iluminado."
            )

            val width = bitmap.width
            val height = bitmap.height
            val faceBox = RectF(face.boundingBox)
            if (faceBox.width() < width * MIN_FACE_RATIO || faceBox.height() < height * MIN_FACE_RATIO) {
                error(
                    "O rosto ocupa uma parte muito pequena da foto. Escolha uma imagem " +
                        "em que ele apareça maior."
                )
            }

            val mouth = mouthRect(face, faceBox)
            val leftEye = eyeRect(face, faceBox, contourType = FaceContour.LEFT_EYE, landmarkType = FaceLandmark.LEFT_EYE)
            val rightEye = eyeRect(face, faceBox, contourType = FaceContour.RIGHT_EYE, landmarkType = FaceLandmark.RIGHT_EYE)

            val jpeg = ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
                output.toByteArray()
            }
            val palette = paletteOf(bitmap)

            return ImportedPhoto(
                jpeg = jpeg,
                width = width,
                height = height,
                face = faceBox.normalized(width, height),
                mouth = mouth.normalized(width, height),
                leftEye = (leftEye ?: fallbackEye(faceBox, true)).normalized(width, height),
                rightEye = (rightEye ?: fallbackEye(faceBox, false)).normalized(width, height),
                accent = palette[0],
                glow = palette[1],
                backdropTop = palette[2],
                backdropBottom = palette[3]
            )
        } finally {
            bitmap.recycle()
        }
    }

    // --------------------------------------------------------------- imagem

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        val largest = max(bounds.outWidth, bounds.outHeight)
        while (largest / (sample * 2) >= MAX_IMAGE_SIDE) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null

        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        return rotate(decoded, orientation)
    }

    private fun rotate(source: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return source
        }
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (rotated !== source) source.recycle()
        return rotated
    }

    // ---------------------------------------------------------------- rosto

    private suspend fun detectFace(bitmap: Bitmap): Face? {
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .setMinFaceSize(0.05f)
                .build()
        )
        return try {
            detector.process(InputImage.fromBitmap(bitmap, 0))
                .awaitTask()
                .filter { it.boundingBox.width() > 0 && it.boundingBox.height() > 0 }
                .maxByOrNull { it.boundingBox.width().toLong() * it.boundingBox.height() }
        } finally {
            detector.close()
        }
    }

    private fun mouthRect(face: Face, faceBox: RectF): RectF {
        val points = mutableListOf<PointF>()
        face.allContours
            .filter { it.faceContourType in MOUTH_CONTOURS }
            .forEach { points.addAll(it.points) }

        if (points.isEmpty()) {
            face.getLandmark(FaceLandmark.MOUTH_LEFT)?.let { points.add(it.position) }
            face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.let { points.add(it.position) }
            face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.let { points.add(it.position) }
        }

        val bounds = boundsOf(points)
        if (bounds == null || bounds.width() < 2f || bounds.height() < 1f) {
            return RectF(
                faceBox.centerX() - faceBox.width() * 0.17f,
                faceBox.top + faceBox.height() * 0.66f,
                faceBox.centerX() + faceBox.width() * 0.17f,
                faceBox.top + faceBox.height() * 0.79f
            )
        }

        val padX = bounds.width() * 0.12f
        val padTop = bounds.height() * 0.25f
        val padBottom = bounds.height() * 0.35f
        return RectF(
            bounds.left - padX,
            bounds.top - padTop,
            bounds.right + padX,
            bounds.bottom + padBottom
        )
    }

    private fun eyeRect(face: Face, faceBox: RectF, contourType: Int, landmarkType: Int): RectF? {
        val contour = face.getContour(contourType)
        val bounds = contour?.points?.takeIf { it.size >= 3 }?.let { boundsOf(it) }
        if (bounds != null && bounds.width() > 1f && bounds.height() > 0.5f) {
            val padX = bounds.width() * 0.22f
            val padY = max(bounds.height() * 0.55f, faceBox.height() * 0.006f)
            return RectF(
                bounds.left - padX,
                bounds.top - padY,
                bounds.right + padX,
                bounds.bottom + padY
            )
        }

        val landmark = face.getLandmark(landmarkType)?.position ?: return null
        val eyeW = faceBox.width() * 0.17f
        val eyeH = faceBox.height() * 0.055f
        return RectF(
            landmark.x - eyeW / 2f,
            landmark.y - eyeH / 2f,
            landmark.x + eyeW / 2f,
            landmark.y + eyeH / 2f
        )
    }

    private fun fallbackEye(faceBox: RectF, left: Boolean): RectF {
        val eyeW = faceBox.width() * 0.17f
        val eyeH = faceBox.height() * 0.055f
        val centerX = faceBox.centerX() + if (left) -faceBox.width() * 0.19f else faceBox.width() * 0.19f
        val centerY = faceBox.top + faceBox.height() * 0.42f
        return RectF(
            centerX - eyeW / 2f,
            centerY - eyeH / 2f,
            centerX + eyeW / 2f,
            centerY + eyeH / 2f
        )
    }

    private fun boundsOf(points: List<PointF>): RectF? {
        if (points.isEmpty()) return null
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        points.forEach { point ->
            left = min(left, point.x)
            top = min(top, point.y)
            right = max(right, point.x)
            bottom = max(bottom, point.y)
        }
        return RectF(left, top, right, bottom)
    }

    // --------------------------------------------------------------- paleta

    /**
     * Usa a cor média das bordas da foto para o fundo combinar com o retrato.
     * Fotos em preto e branco ganham um tom azulado discreto, igual ao tema.
     */
    private fun paletteOf(bitmap: Bitmap): IntArray {
        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var weight = 0.0

        fun sample(x: Int, y: Int) {
            val color = bitmap.getPixel(
                x.coerceIn(0, bitmap.width - 1),
                y.coerceIn(0, bitmap.height - 1)
            )
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            val value = max(r, max(g, b)) / 255f
            val w = 0.1 + value
            sumR += r * w
            sumG += g * w
            sumB += b * w
            weight += w
        }

        val steps = 48
        for (i in 0 until steps) {
            val x = (bitmap.width - 1) * i / (steps - 1)
            sample(x, 0)
            sample(x, bitmap.height - 1)
        }
        for (i in 0 until steps) {
            val y = (bitmap.height - 1) * i / (steps - 1)
            sample(0, y)
            sample(bitmap.width - 1, y)
        }

        if (weight <= 0.0) return DEFAULT_PALETTE
        val r = (sumR / weight / 255.0).toFloat()
        val g = (sumG / weight / 255.0).toFloat()
        val b = (sumB / weight / 255.0).toFloat()
        val hsv = FloatArray(3)
        Color.RGBToHSV(
            (r * 255).toInt().coerceIn(0, 255),
            (g * 255).toInt().coerceIn(0, 255),
            (b * 255).toInt().coerceIn(0, 255),
            hsv
        )

        val hue = if (hsv[1] < 0.12f) DEFAULT_HUE else hsv[0]
        val saturation = max(hsv[1], 0.34f)
        val accent = Color.HSVToColor(floatArrayOf(hue, min(saturation, 0.78f), 0.94f))
        val glow = Color.HSVToColor(floatArrayOf(hue, min(saturation * 0.85f, 0.70f), 0.72f))
        val top = Color.HSVToColor(floatArrayOf(hue, min(saturation * 0.62f, 0.55f), 0.17f))
        val bottom = Color.HSVToColor(floatArrayOf(hue, min(saturation * 0.55f, 0.50f), 0.055f))
        return intArrayOf(accent, glow, top, bottom)
    }

    private fun RectF.normalized(width: Int, height: Int): RectF = RectF(
        (left / width).coerceIn(0f, 1f),
        (top / height).coerceIn(0f, 1f),
        (right / width).coerceIn(0f, 1f),
        (bottom / height).coerceIn(0f, 1f)
    )

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> continuation.resume(result) }
        addOnFailureListener { error -> continuation.resumeWithException(error) }
        addOnCanceledListener { continuation.cancel() }
    }

    companion object {
        private const val MAX_IMAGE_SIDE = 1600
        private const val JPEG_QUALITY = 92
        private const val MIN_FACE_RATIO = 0.06f
        private const val DEFAULT_HUE = 212f

        private val MOUTH_CONTOURS = setOf(
            FaceContour.UPPER_LIP_TOP,
            FaceContour.UPPER_LIP_BOTTOM,
            FaceContour.LOWER_LIP_TOP,
            FaceContour.LOWER_LIP_BOTTOM
        )

        private val DEFAULT_PALETTE = intArrayOf(
            0xFFB8D9FF.toInt(),
            0xFF7FB7E8.toInt(),
            0xFF101A24.toInt(),
            0xFF05080C.toInt()
        )
    }
}
