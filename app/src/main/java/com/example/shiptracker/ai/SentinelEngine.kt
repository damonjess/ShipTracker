package com.example.shiptracker.ai

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.providers.NNAPIFlags
import android.util.Log
import java.nio.FloatBuffer
import java.util.Collections
import java.util.EnumSet

class SentinelEngine(context: Context) {
    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    init {
        try {
            // 1. Initialize the ONNX Environment
            ortEnv = OrtEnvironment.getEnvironment()

            // 2. Configure Hardware Acceleration (Crucial for live camera feeds)
            val sessionOptions = OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
                // Push processing to the NPU/GPU using Android's NNAPI
                try {
                    addNnapi(EnumSet.of(NNAPIFlags.USE_FP16, NNAPIFlags.CPU_DISABLED))
                } catch (e: Exception) {
                    // Fallback to default CPU if NNAPI is not supported on device/emulator
                }
            }

            // Make sure the file is exactly in app/src/main/assets/ship_detector.onnx
            val modelBytes = context.assets.open("ship_detector.onnx").readBytes()
            ortSession = ortEnv?.createSession(modelBytes, sessionOptions)
        } catch (e: Exception) {
            Log.e("SentinelEngine", "FAILED TO LOAD ONNX MODEL", e)
        }
    }

    fun analyzeFrame(tensorInput: FloatArray): FloatArray? {
        val session = ortSession ?: return null
        val env = ortEnv ?: return null

        try {
            // Create tensor from input FloatArray (shape [1, 3, 640, 640] for YOLO)
            val shape = longArrayOf(1, 3, 640, 640)
            val floatBuffer = FloatBuffer.wrap(tensorInput)
            val tensor = OnnxTensor.createTensor(env, floatBuffer, shape)

            val inputName = session.inputNames.iterator().next()
            val inputs = Collections.singletonMap(inputName, tensor)

            session.run(inputs).use { result ->
                val outputTensor = result.get(0) as? OnnxTensor
                val outputValue = outputTensor?.value
                tensor.close()

                return when (outputValue) {
                    is Array<*> -> extractFloatArray(outputValue)
                    is FloatArray -> outputValue
                    else -> null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun extractFloatArray(array: Array<*>): FloatArray {
        val list = mutableListOf<Float>()
        fun flatten(item: Any?) {
            when (item) {
                is FloatArray -> list.addAll(item.toList())
                is Array<*> -> item.forEach { flatten(it) }
                is Float -> list.add(item)
            }
        }
        array.forEach { flatten(it) }
        return list.toFloatArray()
    }

    fun shutdown() {
        try {
            ortSession?.close()
            ortEnv?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        ortSession = null
        ortEnv = null
    }
}
