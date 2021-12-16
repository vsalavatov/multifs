package dev.salavatov.multifs.cloud.googledrive

import dev.salavatov.multifs.vfs.*

class GoogleDriveFS(internal val api: GoogleDriveAPI) : VFS {
    override val root = GDriveRoot(this)

    override fun AbsolutePath.represent(): String = this.joinToString("/", "/") // no native representation ?
}

sealed class GDriveNode(
    protected val fs: GoogleDriveFS,
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
    fs: GoogleDriveFS,
    private val parent_: GDriveFolder?,
    id: String,
    name: String
) : GDriveNode(fs, id, name), Folder {

    private fun GDriveNativeNodeData.convert(): GDriveNode {
        val folder = this@GDriveFolder
        return when (this) {
            is GDriveNativeFileData -> GDriveFile(fs, folder, this.id, this.name, this.size, this.mimeType)
            is GDriveNativeFolderData -> GDriveFolder(fs, folder, this.id, this.name)
        }
    }

    override suspend fun listFolder(): List<GDriveNode> {
        val rawEntries = fs.api.list(id)
        return rawEntries.map { it.convert() }
    }

    override suspend fun createFolder(name: PathPart): GDriveFolder {
        val rawEntry = fs.api.createFolder(name, id)
        return rawEntry.convert() as GDriveFolder
    }

    override suspend fun remove(recursively: Boolean) {
        TODO("Not yet implemented")
    }

    override suspend fun createFile(name: PathPart): GDriveFile {
        val rawEntry = fs.api.createFile(name, id)
        return rawEntry.convert() as GDriveFile
    }

    override suspend fun div(path: PathPart): GDriveFolder { // TODO: optimize
        val entries = listFolder()
        return entries.find { it.name == path && it is GDriveFolder } as? GDriveFolder
            ?: throw GoogleDriveDirectoryNotFoundException("$path wasn't found in $absolutePath")
    }

    override suspend fun rem(path: PathPart): GDriveFile { // TODO: optimize
        val entries = listFolder()
        return entries.find { it.name == path && it is GDriveFile } as? GDriveFile
            ?: throw GoogleDriveDirectoryNotFoundException("$path wasn't found in $absolutePath")
    }

    override val parent: GDriveFolder
        get() = parent_ ?: this
}

class GDriveRoot(fs: GoogleDriveFS) : GDriveFolder(fs, null, "root", ""), RootFolder {
    override val name: String
        get() = ""
    override val parent: GDriveFolder
        get() = this
    override val absolutePath: AbsolutePath
        get() = emptyList()
}

class GDriveFile(
    fs: GoogleDriveFS,
    override val parent: Folder,
    id: String,
    name: String,
    val size: Long,
    val mimeType: String
) : GDriveNode(fs, id, name), File {
    override suspend fun remove() {
        TODO("Not yet implemented")
    }

    override suspend fun read(): ByteArray {
        return fs.api.download(id)
    }

    override suspend fun write(data: ByteArray) {
        return fs.api.upload(id, data)
    }
}