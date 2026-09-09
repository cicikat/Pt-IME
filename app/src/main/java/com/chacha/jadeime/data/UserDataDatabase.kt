package com.chacha.jadeime.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** A word the engine learned from consecutive single-character picks (M1 "自造词"). */
@Entity(tableName = "learned_word", primaryKeys = ["word", "pinyin_key"])
data class LearnedWordRow(
    val word: String,
    @ColumnInfo(name = "pinyin_key") val pinyinKey: String,
)

/** Per-word usage count driving the score boost in DESIGN.md 3.3 ("打多了自动排前面"). */
@Entity(tableName = "user_freq")
data class UserFreqRow(
    @PrimaryKey val word: String,
    @ColumnInfo(name = "freq") val freq: Long,
)

/** Candidate adaptation without storing typed pinyin or committed Chinese text. */
@Entity(tableName = "candidate_memory")
data class CandidateMemoryRow(
    @PrimaryKey @ColumnInfo(name = "key_hash") val keyHash: String,
    val frequency: Long,
    @ColumnInfo(name = "last_used") val lastUsed: Long,
)

/**
 * V2 memory identity uses only static lexicon row ids. `entryIds` is a comma
 * separated id sequence and never contains pinyin or candidate text.
 */
@Entity(
    tableName = "candidate_memory_v2",
    primaryKeys = ["key_kind", "entry_ids", "lexicon_version"],
)
data class CandidateMemoryV2Row(
    @ColumnInfo(name = "key_kind") val keyKind: Int,
    @ColumnInfo(name = "entry_ids") val entryIds: String,
    val frequency: Long,
    @ColumnInfo(name = "last_used") val lastUsed: Long,
    @ColumnInfo(name = "lexicon_version") val lexiconVersion: String,
)

/** A user-authored snippet from the M2 "自定义短语" settings screen. */
@Entity(tableName = "custom_phrase")
data class CustomPhraseRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
)

/** One clipboard snapshot (PLAN M2 "剪贴板：监听 + 历史面板"). Never stores content
 * copied while a password/email field was focused -- see [com.chacha.jadeime.ime.isSensitiveField]. */
@Entity(tableName = "clipboard_entry")
data class ClipboardEntryRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val pinned: Boolean = false,
    val timestamp: Long,
)

@Entity(tableName = "draft_entry")
data class DraftEntryRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "app_package") val appPackage: String,
    val source: String,
    val content: String,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = createdAt,
    @ColumnInfo(defaultValue = "1") val revision: Long = 1,
)

@Dao
interface UserDictDao {
    @Query("SELECT * FROM learned_word")
    suspend fun getLearnedWords(): List<LearnedWordRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLearnedWords(rows: List<LearnedWordRow>)

    @Query("SELECT * FROM user_freq")
    suspend fun getUserFreq(): List<UserFreqRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserFreq(rows: List<UserFreqRow>)

    @Query("SELECT * FROM candidate_memory")
    suspend fun getCandidateMemory(): List<CandidateMemoryRow>

    @Query(
        "INSERT INTO candidate_memory (key_hash, frequency, last_used) " +
            "VALUES (:keyHash, :frequency, :lastUsed) " +
            "ON CONFLICT(key_hash) DO UPDATE SET " +
            "frequency = MAX(frequency, :frequency), " +
            "last_used = MAX(last_used, :lastUsed)",
    )
    suspend fun upsertCandidateMemory(keyHash: String, frequency: Long, lastUsed: Long)

    @Query("SELECT * FROM candidate_memory_v2")
    suspend fun getCandidateMemoryV2(): List<CandidateMemoryV2Row>

    @Query(
        "INSERT INTO candidate_memory_v2 " +
            "(key_kind, entry_ids, frequency, last_used, lexicon_version) " +
            "VALUES (:keyKind, :entryIds, :frequency, :lastUsed, :lexiconVersion) " +
            "ON CONFLICT(key_kind, entry_ids, lexicon_version) DO UPDATE SET " +
            "frequency = MAX(frequency, :frequency), " +
            "last_used = MAX(last_used, :lastUsed)",
    )
    suspend fun upsertCandidateMemoryV2(
        keyKind: Int,
        entryIds: String,
        frequency: Long,
        lastUsed: Long,
        lexiconVersion: String,
    )
}

@Dao
interface CustomPhraseDao {
    @Query("SELECT * FROM custom_phrase ORDER BY id DESC")
    suspend fun getAll(): List<CustomPhraseRow>

    @Insert
    suspend fun insert(row: CustomPhraseRow)

    @Delete
    suspend fun delete(row: CustomPhraseRow)
}

@Dao
interface ClipboardDao {
    @Query("SELECT * FROM clipboard_entry ORDER BY pinned DESC, timestamp DESC")
    suspend fun getAll(): List<ClipboardEntryRow>

    @Query("SELECT * FROM clipboard_entry WHERE content = :content LIMIT 1")
    suspend fun findByContent(content: String): ClipboardEntryRow?

    @Insert
    suspend fun insert(row: ClipboardEntryRow)

    @Update
    suspend fun update(row: ClipboardEntryRow)

    @Delete
    suspend fun delete(row: ClipboardEntryRow)

    @Query("DELETE FROM clipboard_entry WHERE pinned = 0")
    suspend fun deleteUnpinned()

    // Oldest unpinned rows beyond PLAN's 50-entry cap (PLAN M2 "剪贴板...50条").
    @Query(
        "DELETE FROM clipboard_entry WHERE id IN (" +
            "SELECT id FROM clipboard_entry WHERE pinned = 0 " +
            "ORDER BY timestamp DESC LIMIT -1 OFFSET :keep)",
    )
    suspend fun trimUnpinnedBeyond(keep: Int)
}

@Dao
interface DraftDao {
    @Query("SELECT * FROM draft_entry WHERE updated_at >= :since ORDER BY updated_at DESC")
    suspend fun recent(since: Long): List<DraftEntryRow>
    @Insert suspend fun insert(row: DraftEntryRow): Long
    @Update suspend fun update(row: DraftEntryRow)
    @Query("SELECT * FROM draft_entry WHERE id = :id") suspend fun find(id: Long): DraftEntryRow?
    @Query("DELETE FROM draft_entry WHERE updated_at < :before") suspend fun deleteBefore(before: Long)
    @Query("DELETE FROM draft_entry") suspend fun deleteAll()
    @Query("SELECT * FROM draft_entry WHERE id > :after ORDER BY id ASC") suspend fun after(after: Long): List<DraftEntryRow>
}

@Database(
    entities = [
        LearnedWordRow::class,
        UserFreqRow::class,
        CustomPhraseRow::class,
        ClipboardEntryRow::class,
        CandidateMemoryRow::class,
        CandidateMemoryV2Row::class,
        DraftEntryRow::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class UserDataDatabase : RoomDatabase() {
    abstract fun userDictDao(): UserDictDao
    abstract fun customPhraseDao(): CustomPhraseDao
    abstract fun clipboardDao(): ClipboardDao
    abstract fun draftDao(): DraftDao

    companion object {
        fun create(context: Context): UserDataDatabase =
            Room.databaseBuilder(context, UserDataDatabase::class.java, "userdata.db")
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `candidate_memory` (" +
                        "`key_hash` TEXT NOT NULL, `frequency` INTEGER NOT NULL, " +
                        "`last_used` INTEGER NOT NULL, PRIMARY KEY(`key_hash`))",
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `candidate_memory_v2` (" +
                        "`key_kind` INTEGER NOT NULL, " +
                        "`entry_ids` TEXT NOT NULL, " +
                        "`frequency` INTEGER NOT NULL, " +
                        "`last_used` INTEGER NOT NULL, " +
                        "`lexicon_version` TEXT NOT NULL, " +
                        "PRIMARY KEY(`key_kind`, `entry_ids`, `lexicon_version`))",
                )
            }
        }
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE draft_entry ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE draft_entry ADD COLUMN revision INTEGER NOT NULL DEFAULT 1")
                db.execSQL("UPDATE draft_entry SET updated_at = created_at")
            }
        }
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `draft_entry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `created_at` INTEGER NOT NULL, `app_package` TEXT NOT NULL, `source` TEXT NOT NULL, `content` TEXT NOT NULL)")
            }
        }
    }
}
