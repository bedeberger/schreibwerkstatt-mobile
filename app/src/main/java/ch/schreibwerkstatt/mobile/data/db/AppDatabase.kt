package ch.schreibwerkstatt.mobile.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BookEntity::class,
        PageEntity::class,
        PageFtsEntity::class,
        SyncCursorEntity::class,
        PendingWriteEntity::class,
    ],
    version = 3,
    exportSchema = true,
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
                )
                    // KEIN destruktiver Fallback für Upgrades: Ein Schema-Bump OHNE
                    // passende Migration soll laut hier lieber laut scheitern als still
                    // die Offline-Schreib-Queue (pending_writes) und den Cache zu löschen
                    // (Offline-first-Regel). Für jeden künftigen versionsanstieg ein
                    // Migration-Objekt via .addMigrations(...) ergänzen (Schemas liegen
                    // exportiert unter app/schemas/ → mit MigrationTestHelper testbar).
                    // Nur ein Downgrade (Dev/Rollback) darf destruktiv neu aufsetzen.
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build().also { instance = it }
            }
    }
}
