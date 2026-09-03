package com.chacha.jadeime.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Read-only lexicon shipped in assets/lexicon.db (built by tools/build_lexicon.py).
 * Room's `createFromAsset` copies it into the app's databases dir on first open,
 * so no manual first-launch copy step is needed.
 *
 * The `indices` here -- names included -- must mirror exactly what
 * tools/build_lexicon.py creates. Room's prepackaged-db schema validator
 * compares the live table's indices against these annotations by name, and
 * `Index("col")` without an explicit `name=` gets Room's own generated name
 * (e.g. "index_lexicon_pinyin_key") instead of the script's "idx_..." name --
 * that mismatch is exactly what threw "Pre-packaged database has an invalid
 * schema" here.
 */
@Entity(
    tableName = "lexicon",
    indices = [
        Index(value = ["pinyin_key"], name = "idx_lexicon_pinyin_key"),
        Index(value = ["initials"], name = "idx_lexicon_initials"),
    ],
)
data class LexiconRow(
    @PrimaryKey val id: Long,
    @androidx.room.ColumnInfo(name = "pinyin_key") val pinyinKey: String,
    val initials: String,
    val word: String,
    @androidx.room.ColumnInfo(name = "base_freq") val baseFreq: Long,
)

@Dao
interface LexiconDao {
    @Query("SELECT * FROM lexicon")
    suspend fun getAll(): List<LexiconRow>
}

@Database(entities = [LexiconRow::class], version = 1, exportSchema = false)
abstract class LexiconDatabase : RoomDatabase() {
    abstract fun lexiconDao(): LexiconDao

    companion object {
        fun create(context: Context): LexiconDatabase =
            Room.databaseBuilder(context, LexiconDatabase::class.java, "lexicon.db")
                .createFromAsset("lexicon.db")
                .build()
    }
}
