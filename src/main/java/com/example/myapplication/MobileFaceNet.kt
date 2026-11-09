package com.example.myapplication

import android.content.res.AssetManager
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.sqrt

class MobileFaceNet(assetManager: AssetManager, modelPath: String) {

    private var interpreter: Interpreter

    companion object {
        const val INPUT_SIZE = 112      // Standard MobileFaceNet input
        const val OUTPUT_SIZE = 192     // Updated for your TFLite model
    }

    init {
        // Load TFLite model from assets
        val fileDescriptor = assetManager.openFd(modelPath)
        val inputStream = fileDescriptor.createInputStream()
        val fileChannel = inputStream.channel
        val modelBuffer = fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
        interpreter = Interpreter(modelBuffer)
    }

    /**
     * Extract 192-dimensional embedding from a face Bitmap
     */
    fun extractEmbedding(faceBitmap: Bitmap): FloatArray {
        // 1. Resize the face to 112x112
        val resized = Bitmap.createScaledBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE, true)

        // 2. Prepare ByteBuffer for TFLite input (Float32)
        val buffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        // 3. Normalize pixels to [-1,1]
        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16 and 0xFF) - 127.5f) / 128f) // R
            buffer.putFloat(((pixel shr 8 and 0xFF) - 127.5f) / 128f)  // G
            buffer.putFloat(((pixel and 0xFF) - 127.5f) / 128f)       // B
        }

        // 4. Prepare output array for 192-dim embedding
        val output = Array(1) { FloatArray(OUTPUT_SIZE) }

        // 5. Run inference
        interpreter.run(buffer, output)

        // 6. Return L2-normalized embedding
        return l2Normalize(output[0])
    }

    /**
     * L2-normalize a vector
     */
    private fun l2Normalize(v: FloatArray): FloatArray {
        var sum = 0f
        for (x in v) sum += x * x
        val magnitude = sqrt(sum)
        return FloatArray(v.size) { i -> v[i] / magnitude }
    }
}
