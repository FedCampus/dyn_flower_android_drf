package org.eu.fedcampus.train.db

import android.content.Context
import androidx.room.*
import java.io.File

// Always change together with Python `train.models.TFLiteModel`.
@Entity
data class Model(
    @PrimaryKey val id: Long,
    @ColumnInfo val name: String,
    @ColumnInfo val file_path: String,
    @ColumnInfo val n_layers: Long,
) {
    @Throws
    fun getModelDir(context: Context): File {
        return context.getExternalFilesDir("models/$name/")!!
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
