package com.example.shiptracker.ai

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

class SentinelAnalyzer(
    private val sentinelEngine: SentinelEngine,
    private val onShipsDetected: (List<BoundingBox>) -> Unit
) : ImageAnalysis.Analyzer {

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val planes = imageProxy.planes
        if (planes.isNotEmpty()) {
            val buffer: ByteBuffer = planes[0].buffer
            val rowStride = planes[0].rowStride
            val pixelStride = planes[0].pixelStride
            val width = imageProxy.width
            val height = imageProxy.height

            // 1. Convert the RGBA frame directly to a normalized FloatArray [3, 640, 640]
            val tensorData = convertRgbaToFloatArray(buffer, width, height, rowStride, pixelStride)

            // 2. Fire the frame into the ONNX NPU engine
            val rawOutput = sentinelEngine.analyzeFrame(tensorData)

            // 3. Parse the output into coordinates (Top, Left, Bottom, Right, Confidence, Class) with NMS
            val boundingBoxes = parseYoloOutput(rawOutput)

            // 4. Send the boxes back to the UI to be drawn on the glass
            onShipsDetected(boundingBoxes)
        }

        // IMPORTANT: You must close the image, or CameraX will freeze after one frame!
        imageProxy.close()
    }

    private fun convertRgbaToFloatArray(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int
    ): FloatArray {
        val targetSize = 640
        val floatArray = FloatArray(3 * targetSize * targetSize)

        buffer.rewind()

        val rOffset = 0
        val gOffset = targetSize * targetSize
        val bOffset = 2 * targetSize * targetSize

        for (y in 0 until targetSize) {
            for (x in 0 until targetSize) {
                val srcX = (x * width) / targetSize
                val srcY = (y * height) / targetSize
                val pixelIndex = srcY * rowStride + srcX * pixelStride

                val r = if (pixelIndex < buffer.capacity()) (buffer.get(pixelIndex).toInt() and 0xFF) / 255.0f else 0f
                val g = if (pixelIndex + 1 < buffer.capacity()) (buffer.get(pixelIndex + 1).toInt() and 0xFF) / 255.0f else 0f
                val b = if (pixelIndex + 2 < buffer.capacity()) (buffer.get(pixelIndex + 2).toInt() and 0xFF) / 255.0f else 0f

                val destIdx = y * targetSize + x
                floatArray[rOffset + destIdx] = r
                floatArray[gOffset + destIdx] = g
                floatArray[bOffset + destIdx] = b
            }
        }

        return floatArray
    }

    private fun parseYoloOutput(output: FloatArray?): List<BoundingBox> {
        if (output == null || output.isEmpty()) {
            return emptyList()
        }

        val boxes = mutableListOf<BoundingBox>()
        
        // Ultralytics YOLOv8 shape is typically [1, 84, 8400] 
        // which flattens to [84 rows x 8400 columns]
        val numColumns = 8400
        val numRows = output.size / numColumns

        if (numRows < 5) return emptyList()

        for (c in 0 until numColumns) {
            var maxClassScore = 0f
            var classId = -1

            // Find the class with the highest probability for this column (anchor)
            for (r in 4 until numRows) {
                val score = output[r * numColumns + c]
                if (score > maxClassScore) {
                    maxClassScore = score
                    classId = r - 4
                }
            }

            // Confidence threshold
            if (maxClassScore > 0.5f) {
                val xc = output[0 * numColumns + c]
                val yc = output[1 * numColumns + c]
                val w = output[2 * numColumns + c]
                val h = output[3 * numColumns + c]

                // Convert from center coordinates to Top, Left, Bottom, Right
                val x1 = xc - w / 2
                val y1 = yc - h / 2
                val x2 = xc + w / 2
                val y2 = yc + h / 2

                val vesselClass = when (classId) {
                    0 -> "Cargo"
                    1 -> "Tanker"
                    2 -> "Yacht"
                    8 -> "Vessel / Boat" // Standard COCO class index for boats
                    else -> "Vessel ($classId)"
                }
                boxes.add(BoundingBox(x1, y1, x2, y2, maxClassScore, vesselClass))
            }
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
