package dev.salavatov.multifs.cloud.googledrive

import dev.salavatov.multifs.vfs.*

open class GoogleDriveFS(protected val api: GoogleDriveAPI) : VFS<GDriveFile, GDriveFolder> {
    override val root = GDriveRoot(api)

    override suspend fun move(file: GDriveFile, newParent: GDriveFolder, newName: PathPart?, overwrite: Boolean): GDriveFile {
        TODO("Not yet implemented")
    }
    override suspend fun copy(file: GDriveFile, newParent: GDriveFolder, newName: PathPart?, overwrite: Boolean): GDriveFile {
        TODO("Not yet implemented")
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
        val rawEntries = api.list(id)
        return rawEntries.map { it.convert() }
    }

    override suspend fun createFolder(name: PathPart): GDriveFolder {
        val rawEntry = api.createFolder(name, id)
        return rawEntry.convert() as GDriveFolder
    }

    override suspend fun remove(recursively: Boolean) {
        if (recursively) {
            return api.delete(id)
        }
        val children = listFolder()
        if (children.isEmpty()) {
            return api.delete(id)
        }
        throw GoogleDriveFSException("cannot delete folder $id non-recursively as it contains children")
    }

    override suspend fun createFile(name: PathPart): GDriveFile {
        val rawEntry = api.createFile(name, id)
        return rawEntry.convert() as GDriveFile
    }

    override suspend fun div(path: PathPart): GDriveFolder { // TODO: optimize
        val entries = listFolder()
        return entries.find { it.name == path && it is GDriveFolder } as? GDriveFolder
            ?: throw GoogleDriveFolderNotFoundException("$path wasn't found in $absolutePath")
    }

    override suspend fun rem(path: PathPart): GDriveFile { // TODO: optimize
        val entries = listFolder()
        return entries.find { it.name == path && it is GDriveFile } as? GDriveFile
            ?: throw GoogleDriveFileNotFoundException("$path wasn't found in $absolutePath")
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
        return api.delete(id)
    }

    override suspend fun read(): ByteArray {
        return api.download(id)
    }

    override suspend fun write(data: ByteArray) {
        return api.upload(id, data)
    }
}