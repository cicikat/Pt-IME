package com.chacha.jadeime.data

import android.content.Context

/** CRUD over the user's custom phrases (PLAN M2 "自定义短语页，设置页可增删"). */
class PhraseRepository(context: Context) {
    private val db = UserDataDatabase.create(context.applicationContext)
    private val dao = db.customPhraseDao()

    suspend fun getAll(): List<CustomPhraseRow> = dao.getAll()

    suspend fun add(content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        dao.insert(CustomPhraseRow(content = trimmed))
    }

    suspend fun delete(row: CustomPhraseRow) = dao.delete(row)
}
