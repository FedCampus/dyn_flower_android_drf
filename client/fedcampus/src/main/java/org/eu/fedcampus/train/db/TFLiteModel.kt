package org.eu.fedcampus.train.db

import android.content.Context
import androidx.room.*
import java.io.File

@Entity
data class Model(
    @PrimaryKey val id: Long,
    @ColumnInfo val name: String,
    @ColumnInfo val n_layers: Long,
    @field:TypeConverters(TFLiteFilesConverter::class)
    @ColumnInfo val tflite_files: List<String>
) {
    @Throws
    fun getModelDir(context: Context): File {
        return context.getExternalFilesDir("models/$name/")!!
    }
}

class TFLiteFilesConverter {
    @TypeConverter
    fun fromList(list: List<String>): String {
        return list.joinToString(DELIMITER)
    }

    @TypeConverter
    fun toList(string: String): List<String> {
        return string.split(DELIMITER)
    }

    companion object {
        const val DELIMITER = ":"
    }
}

@Dao
interface ModelDao {
    @Query("SELECT * FROM model")
    suspend fun getAll(): List<Model>

    @Query("SELECT * FROM model WHERE id = :id")
    suspend fun findById(id: Long): Model?

    @Query("SELECT * FROM model WHERE name LIKE :name")
    suspend fun findByName(name: String): List<Model>

    @Upsert
    suspend fun upsertAll(vararg models: Model)

    @Delete
    suspend fun deleteAll(vararg models: Model)
}
