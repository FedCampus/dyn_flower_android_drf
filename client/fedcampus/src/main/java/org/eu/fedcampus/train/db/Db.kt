package org.eu.fedcampus.train.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Model::class], version = 1)
abstract class Db : RoomDatabase() {
    abstract fun modelDao(): ModelDao
}
