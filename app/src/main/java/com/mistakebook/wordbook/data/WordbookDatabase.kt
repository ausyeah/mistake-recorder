package com.mistakebook.wordbook.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WordProgress::class,
        WordStudyLog::class
    ],
    version = 1,
    exportSchema = false
)
abstract class WordbookDatabase : RoomDatabase() {

    abstract fun wordbookDao(): WordbookDao

    companion object {
        const val DB_NAME = "wordbook_local.db"

        fun build(context: Context): WordbookDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                WordbookDatabase::class.java,
                DB_NAME
            ).fallbackToDestructiveMigration().build()
    }
}
