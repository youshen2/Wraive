package moye.wear.wraive.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class WraiveDatabase(context: Context) : SQLiteOpenHelper(
    context,
    "wraive.db",
    null,
    DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE entities (
                kind TEXT NOT NULL,
                id TEXT NOT NULL,
                parent_id TEXT,
                sort_order INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL,
                payload TEXT NOT NULL,
                PRIMARY KEY (kind, id)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX entities_parent_index ON entities(kind, parent_id, sort_order, updated_at)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun put(
        kind: String,
        id: String,
        payload: String,
        parentId: String? = null,
        sortOrder: Int = 0,
        updatedAt: Long = System.currentTimeMillis()
    ) {
        val values = ContentValues().apply {
            put("kind", kind)
            put("id", id)
            put("parent_id", parentId)
            put("sort_order", sortOrder)
            put("updated_at", updatedAt)
            put("payload", payload)
        }
        writableDatabase.insertWithOnConflict(
            "entities",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun query(kind: String, parentId: String? = null): List<String> {
        val selection = if (parentId == null) "kind = ?" else "kind = ? AND parent_id = ?"
        val args = if (parentId == null) arrayOf(kind) else arrayOf(kind, parentId)
        val order = if (kind == KIND_MESSAGE) "updated_at ASC" else "sort_order ASC, updated_at DESC"
        val db = readableDatabase
        return db.query(
            "entities",
            // Keep oversized JSON out of CursorWindow, including rows written by older versions.
            arrayOf("id", "CASE WHEN length(payload) <= $INLINE_PAYLOAD_LIMIT THEN payload END"),
            selection,
            args,
            null,
            null,
            order
        ).use { cursor ->
            db.compileStatement("SELECT payload FROM entities WHERE kind = ? AND id = ?").use { statement ->
                statement.bindString(1, kind)
                buildList {
                    while (cursor.moveToNext()) {
                        add(if (cursor.isNull(1)) {
                            statement.bindString(2, cursor.getString(0))
                            // A scalar query returns the string directly, without a CursorWindow.
                            statement.simpleQueryForString()
                        } else {
                            cursor.getString(1)
                        })
                    }
                }
            }
        }
    }

    fun delete(kind: String, id: String) {
        writableDatabase.delete("entities", "kind = ? AND id = ?", arrayOf(kind, id))
    }

    fun deleteKind(kind: String) {
        writableDatabase.delete("entities", "kind = ?", arrayOf(kind))
    }

    fun deleteChildren(kind: String, parentId: String) {
        writableDatabase.delete(
            "entities",
            "kind = ? AND parent_id = ?",
            arrayOf(kind, parentId)
        )
    }

    fun deleteChildrenFrom(kind: String, parentId: String, timestamp: Long, inclusive: Boolean) {
        val operator = if (inclusive) ">=" else ">"
        writableDatabase.delete(
            "entities",
            "kind = ? AND parent_id = ? AND updated_at $operator ?",
            arrayOf(kind, parentId, timestamp.toString())
        )
    }

    fun clearAll() {
        writableDatabase.delete("entities", null, null)
    }

    companion object {
        const val KIND_PROVIDER = "provider"
        const val KIND_MODEL = "model"
        const val KIND_ASSISTANT = "assistant"
        const val KIND_CONVERSATION = "conversation"
        const val KIND_MESSAGE = "message"
        const val KIND_MEMORY = "memory"
        const val KIND_WORLD_BOOK = "world_book"
        const val KIND_QUICK_PHRASE = "quick_phrase"
        const val KIND_SEARCH_PROVIDER = "search_provider"
        const val KIND_MCP_SERVER = "mcp_server"
        const val KIND_PROMPT_TRANSFORM = "prompt_transform"
        const val KIND_SPEECH_CONFIG = "speech_config"
        const val KIND_REQUEST_LOG = "request_log"
        const val KIND_CLOUD_BACKUP = "cloud_backup"
        const val KIND_NETWORK_PROXY = "network_proxy"
        const val KIND_TRANSCRIPTION_CONFIG = "transcription_config"
        private const val DATABASE_VERSION = 1
        private const val INLINE_PAYLOAD_LIMIT = 64 * 1024
    }
}
