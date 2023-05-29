package flwr.android_client

import android.widget.EditText
import androidx.room.*
import org.eu.fedcampus.train.db.Model
import org.eu.fedcampus.train.db.ModelDao

@Database(
    entities = [Model::class, Input::class],
    version = 2,
    autoMigrations = [AutoMigration(1, 2)]
)
abstract class Db : RoomDatabase() {
    abstract fun modelDao(): ModelDao
    abstract fun inputDao(): InputDao
}

@Entity
data class Input(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo val device_id: String,
    @ColumnInfo val ip: String,
    @ColumnInfo val port: String
)

fun inputFromEditText(deviceIdText: EditText, ipEditText: EditText, portEditText: EditText): Input {
    return Input(
        1,
        deviceIdText.text.toString(),
        ipEditText.text.toString(),
        portEditText.text.toString()
    )
}

@Dao
interface InputDao {
    @Query("SELECT * FROM input WHERE id = 1")
    suspend fun get(): Input?

    @Upsert
    suspend fun upsertAll(vararg inputs: Input)
}
