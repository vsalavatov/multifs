package dev.salavatov.multifs.cloud.googledrive

import dev.salavatov.multifs.vfs.*
import dev.salavatov.multifs.vfs.extensions.FileWStreamingIO
import io.ktor.utils.io.*

open class GoogleDriveFS(protected val api: GoogleDriveAPI) : VFS<GDriveFile, GDriveFolder> {
    override val root = GDriveRoot(api)

    /**
     * be aware that `overwrite` doesn't affect anything because Google Drive will create a new file
     * (even if there were already a file with such name)
     */
    override suspend fun copy(
        file: File,
        newParent: Folder,
        newName: PathPart?,
        overwrite: Boolean
    ): GDriveFile {
        if (newParent !is GDriveFolder) throw GoogleDriveFSException("can't operate on folders that don't belong to GoogleDriveFS")
        val targetName = newName ?: file.name
        if (newParent == file.parent && targetName == file.name) {
            // no op
            return file.fromGeneric()
        }
        if (file is GDriveFile) {
            try {
                val nativeFileData = api.copyFile(file.id, newParent.id, targetName)
                return GDriveFile(
                    api,
                    newParent,
                    nativeFileData.id,
                    nativeFileData.name,
                    nativeFileData.size,
                    nativeFileData.mimeType
                )
            } catch (e: Throwable) {
                throw GoogleDriveFSException("couldn't copy ${file.absolutePath} to ${newParent.absolutePath}", e)
            }
        }
        return genericCopy(file, newParent, targetName, overwrite, onExistsThrow = { targetFile ->
            throw GoogleDriveFSFileExistsException("couldn't copy ${file.absolutePath} to ${targetFile.absolutePath}: target file exists")
        })
    }

    override suspend fun move(
        file: File,
        newParent: Folder,
        newName: PathPart?,
        overwrite: Boolean
    ): GDriveFile {
        if (newParent !is GDriveFolder) throw GoogleDriveFSException("can't operate on folders that don't belong to GoogleDriveFS")
        val targetName = newName ?: file.name
        if (newParent == file.parent && targetName == file.name) {
            // no op
            return file.fromGeneric()
        }
        if (file is GDriveFile) {
            try {
                val nativeFileData = api.moveFile(file.id, file.parent.id, newParent.id, targetName)
                return GDriveFile(
                    api,
                    newParent,
                    nativeFileData.id,
                    nativeFileData.name,
                    nativeFileData.size,
                    nativeFileData.mimeType
                )
            } catch (e: Throwable) {
                throw GoogleDriveFSException("couldn't move ${file.absolutePath} to ${newParent.absolutePath}", e)
            }
        }
        return genericMove(file, newParent, targetName, overwrite, onExistsThrow = { targetFile ->
            throw GoogleDriveFSFileExistsException("couldn't move ${file.absolutePath} to ${targetFile.absolutePath}: target file exists")
        })
    }

    override fun representPath(path: AbsolutePath): String = path.joinToString("/", "/") // no native representation ?

    private fun File.fromGeneric(): GDriveFile =
        this as? GDriveFile ?: throw GoogleDriveFSException("expected the file to be a part of the GoogleDriveFS")
}

sealed class GDriveNode(
    protected val api: GoogleDriveAPI,
    val id: String,
    override val name: String
) : VFSNode {
    override val absolutePath: AbsolutePath
        get() = if (id != "root") {
            parent.absolutePath + name
        } else {
            emptyList()
        }
}

open class GDriveFolder(
    api: GoogleDriveAPI,
    private val parent_: GDriveFolder?,
    id: String,
    name: String
) : GDriveNode(api, id, name), Folder {

    private fun GDriveNativeNodeData.convert(): GDriveNode {
        val folder = this@GDriveFolder
        return when (this) {
            is GDriveNativeFileData -> GDriveFile(api, folder, this.id, this.name, this.size, this.mimeType)
            is GDriveNativeFolderData -> GDriveFolder(api, folder, this.id, this.name)
        }
    }

    override suspend fun listFolder(): List<GDriveNode> {
        try {
            val rawEntries = api.list(id)
            return rawEntries.map { it.convert() }
        } catch (e: Throwable) {
            throw GoogleDriveFSException("listFolder failed", e)
        }
    }

    override suspend fun createFolder(name: PathPart): GDriveFolder {
        try {
            val rawEntry = api.createFolder(name, id)
            return rawEntry.convert() as GDriveFolder
        } catch (e: Throwable) {
            throw GoogleDriveFSException("createFolder failed", e)
        }
    }

    override suspend fun remove(recursively: Boolean) {
        try {
            if (recursively) {
                return api.delete(id)
            }
            val children = listFolder()
            if (children.isEmpty()) {
                return api.delete(id)
            }
        } catch (e: Throwable) {
            throw GoogleDriveFSException("remove failed", e)
        }
        throw GoogleDriveFSException("cannot delete folder $id non-recursively as it contains children")
    }

    override suspend fun createFile(name: PathPart): GDriveFile {
        try {
            val rawEntry = api.createFile(name, id)
            return rawEntry.convert() as GDriveFile
        } catch (e: Throwable) {
            throw GoogleDriveFSException("createFile failed", e)
        }
    }

    override suspend fun div(path: PathPart): GDriveFolder { // TODO: optimize
        val entries = listFolder()
        return entries.find { it.name == path && it is GDriveFolder } as? GDriveFolder
            ?: throw GoogleDriveFSFolderNotFoundException("folder $path not found at $absolutePath")
    }

    override suspend fun rem(path: PathPart): GDriveFile { // TODO: optimize
        val entries = listFolder()
        return entries.find { it.name == path && it is GDriveFile } as? GDriveFile
            ?: throw GoogleDriveFSFileNotFoundException("file $path not found at $absolutePath")
    }

    override val parent: GDriveFolder
        get() = parent_ ?: this

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is GDriveFolder) {
            return id == other.id && name == other.name && parent_ == other.parent_
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        var result = parent_?.hashCode() ?: 0
        result = 31 * result + id.hashCode()
        result = 37 * result + name.hashCode()
        return result
    }
}

open class GDriveRoot(api: GoogleDriveAPI) : GDriveFolder(api, null, "root", "") {
    override val name: String
        get() = ""
    override val parent: GDriveFolder
        get() = this
    override val absolutePath: AbsolutePath
        get() = emptyList()
}

open class GDriveFile(
    api: GoogleDriveAPI,
    override val parent: GDriveFolder,
    id: String,
    name: String,
    val size: Long,
    val mimeType: String
) : GDriveNode(api, id, name), FileWStreamingIO {
    override suspend fun getSize(): Long {
        try {
            val data = api.getFileMeta(id)
            return data.size
        } catch (e: Throwable) {
            throw GoogleDriveFSException("getSize failed", e)
        }
    }

    override suspend fun remove() {
        try {
            return api.delete(id)
        } catch (e: Throwable) {
            throw GoogleDriveFSException("delete failed", e)
        }
    }

    override suspend fun read(): ByteArray {
        try {
            return api.download(id)
        } catch (e: Throwable) {
            throw GoogleDriveFSException("download failed", e)
        }
    }

    override suspend fun write(data: ByteArray) {
        try {
            return api.upload(id, data)
        } catch (e: Throwable) {
            throw GoogleDriveFSException("upload failed", e)
        }
    }

    override suspend fun readStream(): ByteReadChannel {
        try {
            return api.downloadStream(id)
        } catch (e: Throwable) {
            throw GoogleDriveFSException("download failed", e)
        }
    }

    override suspend fun writeStream(data: ByteReadChannel) {
        try {
            api.uploadStream(id, data)
        } catch (e: Throwable) {
            throw GoogleDriveFSException("upload failed", e)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is GDriveFile) {
            return id == other.id && name == other.name && parent == other.parent
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        var result = parent.hashCode()
        result = 31 * result + id.hashCode()
        result = 37 * result + name.hashCode()
        return result
    }
}