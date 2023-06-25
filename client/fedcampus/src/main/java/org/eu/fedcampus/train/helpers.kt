package org.eu.fedcampus.train

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

@Throws(IOException::class)
fun loadMappedFile(filePath: String): MappedByteBuffer {
    val file = File(filePath)
    Log.i("Loading mapped file", file.toString())
    val accessFile = RandomAccessFile(file, "r")
    val channel = accessFile.channel
    return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
}

@Throws(IOException::class)
fun loadMappedAssetFile(context: Context, filePath: String): MappedByteBuffer {
    val fileDescriptor = context.assets.openFd(filePath)
    val fileChannel = fileDescriptor.createInputStream().channel
    val startOffset = fileDescriptor.startOffset
    val declaredLength = fileDescriptor.declaredLength
    return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
}
