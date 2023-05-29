package org.eu.fedcampus.train

import android.content.Context
import java.io.File

data class TFLiteModelData(
    val id: Long,
    val name: String,
    val n_layers: Long,
    val tflite_files: List<String>
) {
    @Throws
    fun getModelDir(context: Context): File {
        return context.getExternalFilesDir("models/$name/")!!
    }
}
