package flwr.android_client

import androidx.room.Database
import androidx.room.RoomDatabase
import org.eu.fedcampus.train.db.Model
import org.eu.fedcampus.train.db.ModelDao

@Database(entities = [Model::class], exportSchema = false, version = 1)
abstract class Db : RoomDatabase() {
    abstract fun modelDao(): ModelDao
}
