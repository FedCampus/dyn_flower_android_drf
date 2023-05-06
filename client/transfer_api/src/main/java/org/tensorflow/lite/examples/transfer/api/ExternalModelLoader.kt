package org.tensorflow.lite.examples.transfer.api

import android.util.Log
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class ExternalModelLoader constructor(private val directory: File) : ModelLoader {
    @Throws(IOException::class)
    override fun loadInitializeModel(): LiteModelWrapper {
        return LiteModelWrapper(loadMappedFile("initialize.tflite"))
    }

    @Throws(IOException::class)
    override fun loadBaseModel(): LiteModelWrapper {
        return LiteModelWrapper(loadMappedFile("bottleneck.tflite"))
    }

    @Throws(IOException::class)
    override fun loadTrainModel(): LiteModelWrapper {
        return LiteModelWrapper(loadMappedFile("train_head.tflite"))
    }

    @Throws(IOException::class)
    override fun loadInferenceModel(): LiteModelWrapper {
        return LiteModelWrapper(loadMappedFile("inference.tflite"))
    }

    @Throws(IOException::class)
    override fun loadOptimizerModel(): LiteModelWrapper {
        return LiteModelWrapper(loadMappedFile("optimizer.tflite"))
    }

    @Throws(IOException::class)
    fun loadMappedFile(filePath: String): MappedByteBuffer {
        val file = File(directory, filePath)
        Log.i("Loading mapped file", file.toString())
        val accessFile = RandomAccessFile(file, "r")
        val channel = accessFile.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
    }
}
