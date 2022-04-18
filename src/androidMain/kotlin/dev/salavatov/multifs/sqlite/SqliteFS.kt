package dev.salavatov.multifs.sqlite

import android.content.ContentValues
import dev.salavatov.multifs.vfs.*

open class SqliteFS(protected val dbHelper: SqliteFSDatabaseHelper) : VFS<SqliteFSFile, SqliteFSFolder> {
    override val root: SqliteFSFolder
        get() = SqliteFSRoot(dbHelper)

    override suspend fun copy(
        file: SqliteFSFile,
        newParent: SqliteFSFolder,
        newName: PathPart?,
        overwrite: Boolean
    ): SqliteFSFile {
        val targetName = newName ?: file.name
        if (newParent == file.parent && targetName == file.name) {
            // no op
            return file
        }
        return try {
            val targetFile = newParent % targetName
            // targetFile exists
            if (overwrite) {
                targetFile.write(file.read())
                targetFile
            } else {
                throw SqliteFSFileExistsException("couldn't copy ${file.absolutePath} to ${targetFile.absolutePath}: target file exists")
            }
        } catch (_: SqliteFSFileNotFoundException) {
            val targetFile = newParent.createFile(targetName)
            targetFile.write(file.read())
            targetFile
        }
    }

    override suspend fun move(
        file: SqliteFSFile,
        newParent: SqliteFSFolder,
        newName: PathPart?,
        overwrite: Boolean
    ): SqliteFSFile {
        val targetName = newName ?: file.name
        if (newParent == file.parent && targetName == file.name) {
            // no op
            return file
        }
        return try {
            val targetFile = newParent % targetName
            // targetFile exists
            if (overwrite) {
                targetFile.write(file.read())
                file.remove()
                targetFile
            } else {
                throw SqliteFSFileExistsException("couldn't move ${file.absolutePath} to ${targetFile.absolutePath}: target file exists")
            }
        } catch (_: SqliteFSFileNotFoundException) {
            val targetFile = newParent.createFile(targetName)
            targetFile.write(file.read())
            file.remove()
            targetFile
        }
    }

    override fun representPath(path: AbsolutePath): String {
        return path.joinToString("/", "/")
    }
}

sealed class SqliteFSNode(
    protected val dbHelper: SqliteFSDatabaseHelper,
    protected val id: Int,
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
    }

    override suspend fun createFile(name: PathPart): SqliteFSFile {
        val db = dbHelper.writableDatabase
        val fileId = db.insert(
            SQLContract.Files.TABLE_NAME,
            null,
            ContentValues().apply {
                put(SQLContract.Files.COLUMN_NAME, name)
                put(SQLContract.Files.COLUMN_DATA, ByteArray(0))
                put(SQLContract.Files.COLUMN_PARENT, id)
            }
        ).toInt()
        if (fileId == -1)
            throw SqliteFSException("failed to create new file $name at $absolutePath")

        return SqliteFSFile(dbHelper, fileId, name, this)
    }

    override suspend fun createFolder(name: PathPart): SqliteFSFolder {
        val db = dbHelper.writableDatabase

        val folderId = db.insert(
            SQLContract.Folders.TABLE_NAME,
            null,
            ContentValues().apply {
                put(SQLContract.Folders.COLUMN_NAME, name)
                put(SQLContract.Folders.COLUMN_PARENT, id)
            }
        ).toInt()
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
                throw SqliteFSException("couldn't remove folder $absolutePath as it has children")
        }

        val db = dbHelper.writableDatabase
        val deleted = db.delete(
            SQLContract.Folders.TABLE_NAME,
            "${SQLContract.Folders.COLUMN_ID} = $id",
            arrayOf()
        )
        if (deleted == 0)
            throw SqliteFSFolderNotFoundException("couldn't remove folder $absolutePath")
    }

    override suspend fun div(path: PathPart): SqliteFSFolder {
        val db = dbHelper.readableDatabase
        db.query(
            SQLContract.Folders.TABLE_NAME,
            arrayOf(SQLContract.Folders.COLUMN_ID),
            "${SQLContract.Folders.COLUMN_NAME} = ? AND ${SQLContract.Folders.COLUMN_PARENT} = $id",
            arrayOf(path.toString()),
            null,
            null,
            null
        ).use {
            if (it.count == 0)
                throw SqliteFSFolderNotFoundException("folder $path not found at $absolutePath")
            if (it.count > 1)
                throw SqliteFSException("internal invariant failure (more than one child folder with specified name")
            it.moveToFirst()
            return SqliteFSFolder(dbHelper, it.getInt(0), path, this)
        }
    }

    override suspend fun rem(path: PathPart): SqliteFSFile {
        val db = dbHelper.readableDatabase
        db.query(
            SQLContract.Files.TABLE_NAME,
            arrayOf(SQLContract.Files.COLUMN_ID),
            "${SQLContract.Files.COLUMN_NAME} = ? AND ${SQLContract.Files.COLUMN_PARENT} = $id",
            arrayOf(path.toString()),
            null,
            null,
            null
        ).use {
            if (it.count == 0)
                throw SqliteFSFolderNotFoundException("file $path not found at $absolutePath")
            if (it.count > 1)
                throw SqliteFSException("internal invariant failure (more than one child file with specified name")
            it.moveToFirst()
            return SqliteFSFile(dbHelper, it.getInt(0), path, this)
        }
    }

}

open class SqliteFSRoot(dbHelper: SqliteFSDatabaseHelper) : SqliteFSFolder(dbHelper, 0, "", null) {
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

    override suspend fun read(): ByteArray {
        val db = dbHelper.readableDatabase

        db.query(
            SQLContract.Files.TABLE_NAME,
            arrayOf(SQLContract.Files.COLUMN_DATA),
            "${SQLContract.Files.COLUMN_ID} = $id",
            arrayOf(),
            null,
            null,
            null
        ).use { cursor ->
            if (cursor.count == 0)
                throw SqliteFSFileNotFoundException("couldn't read $absolutePath")
            cursor.moveToFirst()
            return cursor.getBlob(0)
        }
    }

    override suspend fun remove() {
        val db = dbHelper.writableDatabase

        val deleted = db.delete(
            SQLContract.Files.TABLE_NAME,
            "${SQLContract.Files.COLUMN_ID} = $id",
            arrayOf()
        )
        if (deleted == 0)
            throw SqliteFSFileNotFoundException("couldn't delete $absolutePath")
    }

    override suspend fun write(data: ByteArray) {
        val db = dbHelper.writableDatabase

        val updated = db.update(
            SQLContract.Files.TABLE_NAME,
            ContentValues().apply {
                put(SQLContract.Files.COLUMN_DATA, data)
            },
            "${SQLContract.Files.COLUMN_ID} = ?",
            arrayOf(id.toString())
        )
        if (updated != 1)
            throw SqliteFSFileNotFoundException("couldn't update $absolutePath")
    }
}


