package ch.schreibwerkstatt.mobile.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BookEntity::class,
        PageEntity::class,
        SyncCursorEntity::class,
        PendingWriteEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun pageDao(): PageDao
    abstract fun syncCursorDao(): SyncCursorDao
    abstract fun pendingWriteDao(): PendingWriteDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "schreibwerkstatt.db",
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
