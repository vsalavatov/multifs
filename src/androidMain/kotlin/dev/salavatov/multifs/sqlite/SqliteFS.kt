package dev.salavatov.multifs.sqlite

import android.content.ContentValues
import dev.salavatov.multifs.vfs.*

open class SqliteFS(protected val dbHelper: SqliteFSDatabaseHelper) : VFS<SqliteFSFile, SqliteFSFolder> {
    override val root: SqliteFSFolder
        get() = SqliteFSRoot(dbHelper)

    override suspend fun copy(
        file: File,
        newParent: Folder,
        newName: PathPart?,
        overwrite: Boolean
    ): SqliteFSFile {
        @Suppress("NAME_SHADOWING")
        val newParent = newParent as? SqliteFSFolder
            ?: throw SqliteFSException("can't operate on folders that don't belong to SqliteFS")
        val targetName = newName ?: file.name
        if (newParent == file.parent && targetName == file.name) {
            // no op
            return file.fromGeneric()
        }
        if (file is SqliteFSFile) {
            try {
                val db = dbHelper.writableDatabase
                val Files = SQLContract.Files
                db.execSQL(
                    """
                        INSERT INTO ${Files.TABLE_NAME} 
                            (${Files.COLUMN_NAME}, ${Files.COLUMN_PARENT}, ${Files.COLUMN_DATA})
                        SELECT ? ${Files.COLUMN_NAME}, ${newParent.id} ${Files.COLUMN_PARENT}, ${Files.COLUMN_DATA}
                        FROM ${Files.TABLE_NAME}
                        WHERE ${Files.COLUMN_ID} = ${file.id};
                    """.trimIndent(),
                    arrayOf(targetName)
                )
                val fileId = db.rawQuery("SELECT last_insert_rowid();", arrayOf()).use {
                    if (!it.moveToFirst()) throw Exception("empty insert query result")
                    it.getInt(0)
                }
                return SqliteFSFile(dbHelper, fileId, targetName, newParent)
            } catch (e: Throwable) {
                throw SqliteFSException("couldn't copy ${file.absolutePath} to ${newParent.absolutePath}", e)
            }
        }
        return genericCopy(file, newParent, targetName, overwrite, onExistsThrow = { targetFile ->
            throw SqliteFSFileExistsException("couldn't copy ${file.absolutePath} to ${targetFile.absolutePath}: target file exists")
        })
    }

    override suspend fun move(
        file: File,
        newParent: Folder,
        newName: PathPart?,
        overwrite: Boolean
    ): SqliteFSFile {
        if (newParent !is SqliteFSFolder) throw SqliteFSException("can't operate on folders that don't belong to SqliteFS")
        val targetName = newName ?: file.name
        if (newParent == file.parent && targetName == file.name) {
            // no op
            return file.fromGeneric()
        }
        if (file is SqliteFSFile) {
            try {
                val db = dbHelper.writableDatabase
                val Files = SQLContract.Files
                val affected = db.update(Files.TABLE_NAME,
                    ContentValues().apply {
                        put(Files.COLUMN_PARENT, newParent.id)
                        put(Files.COLUMN_NAME, targetName)
                    },
                    "${Files.COLUMN_ID} = ${file.id}",
                    arrayOf()
                )
                if (affected == 0) throw Exception("update didn't affect the file")
                return SqliteFSFile(dbHelper, file.id, targetName, newParent)
            } catch (e: Throwable) {
                throw SqliteFSException("couldn't move ${file.absolutePath} to ${newParent.absolutePath}", e)
            }
        }
        return genericMove(file, newParent, targetName, overwrite, onExistsThrow = { targetFile ->
            throw SqliteFSFileExistsException("couldn't move ${file.absolutePath} to ${targetFile.absolutePath}: target file exists")
        })
    }

    override fun representPath(path: AbsolutePath): String {
        return path.joinToString("/", "/")
    }

    private fun File.fromGeneric(): SqliteFSFile =
        this as? SqliteFSFile
            ?: throw SqliteFSException("expected the file to be a part of the SqliteFS (class: ${this.javaClass.kotlin.qualifiedName}")
}

sealed class SqliteFSNode(
    protected val dbHelper: SqliteFSDatabaseHelper,
    val id: Int,
    override val name: String
) : VFSNode {
    val isRoot = id == SQLContract.Folders.ROOT_ID
    override val absolutePath: AbsolutePath
        get() = if (!isRoot) {
            parent.absolutePath + name
        } else {
            emptyList()
        }
}

open class SqliteFSFolder(
    dbHelper: SqliteFSDatabaseHelper,
    id: Int,
    name: String,
    private val parent_: SqliteFSFolder?,
) : SqliteFSNode(dbHelper, id, name), Folder {
    override val parent: Folder
        get() = parent_ ?: this

    override suspend fun listFolder(): List<SqliteFSNode> {
        val db = dbHelper.readableDatabase
        val result = mutableListOf<SqliteFSNode>()
        try {
            db.query(
                SQLContract.Files.TABLE_NAME,
                arrayOf(SQLContract.Files.COLUMN_ID, SQLContract.Files.COLUMN_NAME),
                "${SQLContract.Files.COLUMN_PARENT} = $id",
                arrayOf(),
                null,
                null,
                null
            ).use {
                while (it.moveToNext()) {
                    result += SqliteFSFile(
                        dbHelper,
                        it.getInt(it.getColumnIndex(SQLContract.Files.COLUMN_ID)),
                        it.getString(it.getColumnIndex(SQLContract.Files.COLUMN_NAME)),
                        this
                    )
                }
            }

            db.query(
                SQLContract.Folders.TABLE_NAME,
                arrayOf(SQLContract.Folders.COLUMN_ID, SQLContract.Folders.COLUMN_NAME),
                "${SQLContract.Folders.COLUMN_PARENT} = $id",
                arrayOf(),
                null,
                null,
                null
            ).use {
                while (it.moveToNext()) {
                    result += SqliteFSFolder(
                        dbHelper, it.getInt(it.getColumnIndex(SQLContract.Files.COLUMN_ID)),
                        it.getString(it.getColumnIndex(SQLContract.Files.COLUMN_NAME)), this
                    )
                }
            }
            return result
        } catch (e: Throwable) {
            throw SqliteFSException("couldn't list folder $name", e)
        }
    }

    override suspend fun createFile(name: PathPart): SqliteFSFile {
        val db = dbHelper.writableDatabase
        val fileId = try {
            db.insert(
                SQLContract.Files.TABLE_NAME,
                null,
                ContentValues().apply {
                    put(SQLContract.Files.COLUMN_NAME, name)
                    put(SQLContract.Files.COLUMN_DATA, ByteArray(0))
                    put(SQLContract.Files.COLUMN_PARENT, id)
                }
            ).toInt()
        } catch (e: Throwable) {
            throw SqliteFSException("failed to create new file $name at $absolutePath", e)
        }
        if (fileId == -1)
            throw SqliteFSException("failed to create new file $name at $absolutePath")

        return SqliteFSFile(dbHelper, fileId, name, this)
    }

    override suspend fun createFolder(name: PathPart): SqliteFSFolder {
        val db = dbHelper.writableDatabase

        val folderId = try {
            db.insert(
                SQLContract.Folders.TABLE_NAME,
                null,
                ContentValues().apply {
                    put(SQLContract.Folders.COLUMN_NAME, name)
                    put(SQLContract.Folders.COLUMN_PARENT, id)
                }
            ).toInt()
        } catch (e: Throwable) {
            throw SqliteFSException("failed to create new folder $name at $absolutePath", e)
        }
        if (folderId == -1)
            throw SqliteFSException("failed to create new folder $name at $absolutePath")

        return SqliteFSFolder(dbHelper, folderId, name, this)
    }

    override suspend fun remove(recursively: Boolean) {
        if (isRoot)
            throw SqliteFSException("couldn't remove root folder")
        if (!recursively) {
            val children = listFolder()
            if (children.isNotEmpty())
                throw SqliteFSFolderNotEmptyException("couldn't remove folder $absolutePath as it has children")
        }

        val db = dbHelper.writableDatabase
        val deleted = try {
            db.delete(
                SQLContract.Folders.TABLE_NAME,
                "${SQLContract.Folders.COLUMN_ID} = $id",
                arrayOf()
            )
        } catch (e: Throwable) {
            throw SqliteFSException("couldn't remove folder $absolutePath", e)
        }
        if (deleted == 0)
            throw SqliteFSFolderNotFoundException("couldn't remove folder $absolutePath")
    }

    override suspend fun div(path: PathPart): SqliteFSFolder {
        try {
            val db = dbHelper.readableDatabase
            db.query(
                SQLContract.Folders.TABLE_NAME,
                arrayOf(SQLContract.Folders.COLUMN_ID),
                "${SQLContract.Folders.COLUMN_NAME} = ? AND ${SQLContract.Folders.COLUMN_PARENT} = $id",
                arrayOf(path.toString()),
                null,
                null,
                null
            )
        } catch (e: Throwable) {
            throw SqliteFSException("db query failed", e)
        }.use {
            if (it.count == 0)
                throw SqliteFSFolderNotFoundException("folder $path not found at $absolutePath")
            if (it.count > 1)
                throw SqliteFSException("internal invariant failure (more than one child folder with specified name")
            it.moveToFirst()
            return SqliteFSFolder(dbHelper, it.getInt(0), path, this)
        }
    }

    override suspend fun rem(path: PathPart): SqliteFSFile {
        try {
            val db = dbHelper.readableDatabase
            db.query(
                SQLContract.Files.TABLE_NAME,
                arrayOf(SQLContract.Files.COLUMN_ID),
                "${SQLContract.Files.COLUMN_NAME} = ? AND ${SQLContract.Files.COLUMN_PARENT} = $id",
                arrayOf(path.toString()),
                null,
                null,
                null
            )
        } catch (e: Throwable) {
            throw SqliteFSException("db query failed", e)
        }.use {
            if (it.count == 0)
                throw SqliteFSFileNotFoundException("file $path not found at $absolutePath")
            if (it.count > 1)
                throw SqliteFSException("internal invariant failure (more than one child file with specified name")
            it.moveToFirst()
            return SqliteFSFile(dbHelper, it.getInt(0), path, this)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is SqliteFSFolder) {
            return id == other.id && name == other.name && parent_ == other.parent_
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        var result = parent_?.hashCode() ?: 0
        result = 31 * result + id
        result = 37 * result + name.hashCode()
        return result
    }
}

open class SqliteFSRoot(dbHelper: SqliteFSDatabaseHelper) :
    SqliteFSFolder(dbHelper, SQLContract.Folders.ROOT_ID, "", null) {
    override val name: String
        get() = ""
    override val parent: SqliteFSFolder
        get() = this
    override val absolutePath: AbsolutePath
        get() = emptyList()
}

open class SqliteFSFile(
    dbHelper: SqliteFSDatabaseHelper,
    id: Int,
    name: String,
    override val parent: SqliteFSFolder
) : SqliteFSNode(dbHelper, id, name), File {

    override suspend fun getSize(): Long {
        try {
            val db = dbHelper.readableDatabase
            db.query(
                SQLContract.Files.TABLE_NAME,
                arrayOf("length(${SQLContract.Files.COLUMN_DATA})"),
                "${SQLContract.Files.COLUMN_ID} = $id",
                arrayOf(),
                null,
                null,
                null
            )
        } catch (e: Throwable) {
            throw SqliteFSException("db query failed", e)
        }.use { cursor ->
            if (cursor.count == 0)
                throw SqliteFSFileNotFoundException("couldn't get size of $absolutePath")
            cursor.moveToFirst()
            return cursor.getLong(0)
        }
    }

    override suspend fun read(): ByteArray {
        try {
            val db = dbHelper.readableDatabase
            db.query(
                SQLContract.Files.TABLE_NAME,
                arrayOf(SQLContract.Files.COLUMN_DATA),
                "${SQLContract.Files.COLUMN_ID} = $id",
                arrayOf(),
                null,
                null,
                null
            )
        } catch (e: Throwable) {
            throw SqliteFSException("db query failed", e)
        }.use { cursor ->
            if (cursor.count == 0)
                throw SqliteFSFileNotFoundException("couldn't read $absolutePath")
            cursor.moveToFirst()
            return cursor.getBlob(0)
        }
    }

    override suspend fun remove() {
        val deleted = try {
            val db = dbHelper.writableDatabase
            db.delete(
                SQLContract.Files.TABLE_NAME,
                "${SQLContract.Files.COLUMN_ID} = $id",
                arrayOf()
            )
        } catch (e: Throwable) {
            throw SqliteFSException("db query failed", e)
        }
        if (deleted == 0)
            throw SqliteFSFileNotFoundException("couldn't delete $absolutePath")
    }

    override suspend fun write(data: ByteArray) {
        val updated = try {
            val db = dbHelper.writableDatabase
            db.update(
                SQLContract.Files.TABLE_NAME,
                ContentValues().apply {
                    put(SQLContract.Files.COLUMN_DATA, data)
                },
                "${SQLContract.Files.COLUMN_ID} = ?",
                arrayOf(id.toString())
            )
        } catch (e: Throwable) {
            throw SqliteFSException("db query failed", e)
        }
        if (updated != 1)
            throw SqliteFSFileNotFoundException("couldn't update $absolutePath")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is SqliteFSFile) {
            return id == other.id && name == other.name && parent == other.parent
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        var result = parent.hashCode()
        result = 31 * result + id
        result = 37 * result + name.hashCode()
        return result
    }
}


