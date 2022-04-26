package dev.salavatov.multifs.cloud.googledrive

import dev.salavatov.multifs.vfs.*

open class GoogleDriveFS(protected val api: GoogleDriveAPI) : VFS<GDriveFile, GDriveFolder> {
    override val root = GDriveRoot(api)

    override suspend fun copy(
        file: GDriveFile,
        newParent: GDriveFolder,
        newName: PathPart?,
        overwrite: Boolean
    ): GDriveFile {
        // TODO: optimize
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
                throw GoogleDriveFSFileExistsException("couldn't copy ${file.absolutePath} to ${targetFile.absolutePath}: target file exists")
            }
        } catch (_: GoogleDriveFSFileNotFoundException) {
            val targetFile = newParent.createFile(targetName)
            targetFile.write(file.read())
            targetFile
        }
    }

    override suspend fun move(
        file: GDriveFile,
        newParent: GDriveFolder,
        newName: PathPart?,
        overwrite: Boolean
    ): GDriveFile {
        // TODO: optimize
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
                throw GoogleDriveFSFileExistsException("couldn't move ${file.absolutePath} to ${targetFile.absolutePath}: target file exists")
            }
        } catch (_: GoogleDriveFSFileNotFoundException) {
            val targetFile = newParent.createFile(targetName)
            targetFile.write(file.read())
            file.remove()
            targetFile
        }
    }

    override fun representPath(path: AbsolutePath): String = path.joinToString("/", "/") // no native representation ?
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
        } catch (e: GoogleDriveAPIException) {
            throw GoogleDriveFSException("listFolder failed", e)
        }
    }

    override suspend fun createFolder(name: PathPart): GDriveFolder {
        try {
            val rawEntry = api.createFolder(name, id)
            return rawEntry.convert() as GDriveFolder
        } catch (e: GoogleDriveAPIException) {
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
        } catch (e: GoogleDriveAPIException) {
            throw GoogleDriveFSException("remove failed", e)
        }
        throw GoogleDriveFSException("cannot delete folder $id non-recursively as it contains children")
    }

    override suspend fun createFile(name: PathPart): GDriveFile {
        try {
            val rawEntry = api.createFile(name, id)
            return rawEntry.convert() as GDriveFile
        } catch (e: GoogleDriveAPIException) {
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
) : GDriveNode(api, id, name), File {
    override suspend fun remove() {
        try {
            return api.delete(id)
        } catch (e: GoogleDriveAPIException) {
            throw GoogleDriveFSException("delete failed", e)
        }
    }

    override suspend fun read(): ByteArray {
        try {
            return api.download(id)
        } catch (e: GoogleDriveFSException) {
            throw GoogleDriveFSException("download failed", e)
        }
    }

    override suspend fun write(data: ByteArray) {
        try {
            return api.upload(id, data)
        } catch (e: GoogleDriveAPIException) {
            throw GoogleDriveFSException("upload failed", e)
        }
    }
}