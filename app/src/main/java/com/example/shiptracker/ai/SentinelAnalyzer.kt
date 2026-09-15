package com.example.shiptracker.ai

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class SentinelAnalyzer(
    private val sentinelEngine: SentinelEngine,
    private val onShipsDetected: (List<BoundingBox>) -> Unit
) : ImageAnalysis.Analyzer {

    // 🚨 FIX: Pre-allocate the memory ONCE when the camera starts
    private val tensorBuffer = FloatArray(3 * 640 * 640)

    private var lastBoxes: List<BoundingBox> = emptyList()
    private var emptyFrameCount = 0
    private val isProcessing = AtomicBoolean(false)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        try {
            val planes = imageProxy.planes
            if (planes.isNotEmpty()) {
                val buffer: ByteBuffer = planes[0].buffer
                val rowStride = planes[0].rowStride
                val pixelStride = planes[0].pixelStride
                val width = imageProxy.width
                val height = imageProxy.height

                // 1. Convert the RGBA frame with proper letterboxing into [3, 640, 640]
                val tensorData = convertRgbaToFloatArray(buffer, width, height, rowStride, pixelStride)

                // 2. Fire the frame into the ONNX NPU engine
                val rawOutput = sentinelEngine.analyzeFrame(tensorData)

                // 3. Parse output with direct 640x640 normalization to [0..1]
                val boundingBoxes = parseYoloOutput(rawOutput)

                // 4. Temporal smoothing (persistence for 4 frames to eliminate flicker)
                val finalBoxes = if (boundingBoxes.isNotEmpty()) {
                    emptyFrameCount = 0
                    lastBoxes = boundingBoxes
                    boundingBoxes
                } else {
                    emptyFrameCount++
                    if (emptyFrameCount < 4) lastBoxes else emptyList()
                }

                // 5. Send the boxes back to the UI to be drawn on the glass
                onShipsDetected(finalBoxes)
            }
        } finally {
            isProcessing.set(false)
            // IMPORTANT: You must close the image, or CameraX will freeze after one frame!
            imageProxy.close()
        }
    }

    private fun convertRgbaToFloatArray(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int
    ): FloatArray {
        val targetSize = 640
        
        // Use pre-allocated buffer and fill with padding color
        tensorBuffer.fill(114f / 255f)

        buffer.rewind()

        val scale = minOf(targetSize.toFloat() / width, targetSize.toFloat() / height)
        val scaledWidth = (width * scale).toInt()
        val scaledHeight = (height * scale).toInt()
        val padX = (targetSize - scaledWidth) / 2
        val padY = (targetSize - scaledHeight) / 2

        val rOffset = 0
        val gOffset = targetSize * targetSize
        val bOffset = 2 * targetSize * targetSize

        for (y in 0 until scaledHeight) {
            for (x in 0 until scaledWidth) {
                val srcX = (x / scale).toInt().coerceIn(0, width - 1)
                val srcY = (y / scale).toInt().coerceIn(0, height - 1)
                val pixelIndex = srcY * rowStride + srcX * pixelStride

                val r = if (pixelIndex < buffer.capacity()) (buffer.get(pixelIndex).toInt() and 0xFF) / 255.0f else 0f
                val g = if (pixelIndex + 1 < buffer.capacity()) (buffer.get(pixelIndex + 1).toInt() and 0xFF) / 255.0f else 0f
                val b = if (pixelIndex + 2 < buffer.capacity()) (buffer.get(pixelIndex + 2).toInt() and 0xFF) / 255.0f else 0f

                val destX = x + padX
                val destY = y + padY
                val destIdx = destY * targetSize + destX

                tensorBuffer[rOffset + destIdx] = r
                tensorBuffer[gOffset + destIdx] = g
                tensorBuffer[bOffset + destIdx] = b
            }
        }

        return tensorBuffer
    }

    private fun parseYoloOutput(output: FloatArray?): List<BoundingBox> {
        if (output == null || output.isEmpty()) {
            return emptyList()
        }

        val boxes = mutableListOf<BoundingBox>()
        
        // Ultralytics YOLOv8 shape is typically [1, 84, 8400] 
        val numColumns = 8400
        val numRows = output.size / numColumns

        if (numRows < 5) return emptyList()

        for (c in 0 until numColumns) {
            var maxClassScore = 0f
            var classId = -1

            // Find the class with highest probability for this anchor
            for (r in 4 until numRows) {
                val score = output[r * numColumns + c]
                if (score > maxClassScore) {
                    maxClassScore = score
                    classId = r - 4
                }
            }

            // 🚨 FIX: If it's less than 50%, skip this box INSTANTLY.
            // This stops Kotlin from doing complex math on 8,390 useless boxes per frame.
            if (maxClassScore < 0.50f) continue

            val xc = output[0 * numColumns + c]
            val yc = output[1 * numColumns + c]
            val w = output[2 * numColumns + c]
            val h = output[3 * numColumns + c]

            // Convert center coordinates to x1, y1, x2, y2 in 640 space
            val x1 = xc - w / 2
            val y1 = yc - h / 2
            val x2 = xc + w / 2
            val y2 = yc + h / 2

            // Normalize directly from 640x640 model coordinate space to [0..1]
            val normX1 = (x1 / 640f).coerceIn(0f, 1f)
            val normY1 = (y1 / 640f).coerceIn(0f, 1f)
            val normX2 = (x2 / 640f).coerceIn(0f, 1f)
            val normY2 = (y2 / 640f).coerceIn(0f, 1f)

            val vesselClass = when (classId) {
                0 -> "Cargo"
                1 -> "Tanker"
                2 -> "Yacht"
                8 -> "Vessel / Boat"
                else -> "Vessel ($classId)"
            }
            boxes.add(BoundingBox(normX1, normY1, normX2, normY2, maxClassScore, vesselClass))
        }

        return applyNms(boxes, 0.45f)
    }

    private fun applyNms(boxes: List<BoundingBox>, iouThreshold: Float): List<BoundingBox> {
        if (boxes.isEmpty()) return emptyList()
        val sorted = boxes.sortedByDescending { it.confidence }.toMutableList()
        val selected = mutableListOf<BoundingBox>()

        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            selected.add(best)

            val iterator = sorted.iterator()
            while (iterator.hasNext()) {
                val next = iterator.next()
                if (calculateIoU(best, next) > iouThreshold) {
                    iterator.remove()
                }
            }
        }
        return selected
    }

    private fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {
        val x1 = maxOf(box1.x1, box2.x1)
        val y1 = maxOf(box1.y1, box2.y1)
        val x2 = minOf(box1.x2, box2.x2)
        val y2 = minOf(box1.y2, box2.y2)

        val intersection = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val area1 = (box1.x2 - box1.x1) * (box1.y2 - box1.y1)
        val area2 = (box2.x2 - box2.x1) * (box2.y2 - box2.y1)
        val union = area1 + area2 - intersection

        return if (union > 0f) intersection / union else 0f
    }
}
