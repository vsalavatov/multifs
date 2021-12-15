package dev.salavatov.multifs.cloud.googledrive

import dev.salavatov.multifs.vfs.*

class GoogleDriveFSException(message: String? = null, cause: Throwable? = null) : VFSException(message, cause)

class GoogleDriveFS(private val api: GoogleDriveAPI) : VFS, RootFolder {
    private val root = GDriveRoot(this)

    override suspend fun listFolder(): List<VFSNode> = root.listFolder()

    override suspend fun createFolder(name: PathPart): Folder = root.createFolder(name)

    override suspend fun remove(recursively: Boolean) = root.remove(recursively)

    override suspend fun createFile(name: PathPart): File = root.createFile(name)

    override suspend fun div(path: PathPart): Folder = root.div(path)

    override suspend fun rem(path: PathPart): File = root.rem(path)

    override fun AbsolutePath.represent(): String = TODO()
}

sealed class GDriveNode(protected val fs: GoogleDriveFS, protected val path: AbsolutePath) : VFSNode {
    override val name: String
        get() = if (path.isEmpty()) "" else path.last()
    override val parent: Folder
        get() = GDriveFolder(  // Root if path.isEmpty
            fs, if (path.isEmpty()) {
                path
            } else {
                path.subList(0, path.size - 1)
            }
        )
    override val absolutePath: AbsolutePath
        get() = path
}

open class GDriveFolder(fs: GoogleDriveFS, path: AbsolutePath) : GDriveNode(fs, path), Folder {
    override suspend fun listFolder(): List<VFSNode> {
        TODO("Not yet implemented")
    }

    override suspend fun createFolder(name: PathPart): Folder {
        TODO("Not yet implemented")
    }

    override suspend fun remove(recursively: Boolean) {
        TODO("Not yet implemented")
    }

    override suspend fun createFile(name: PathPart): File {
        TODO("Not yet implemented")
    }

    override suspend fun div(path: PathPart): Folder {
        TODO("Not yet implemented")
    }

    override suspend fun rem(path: PathPart): File {
        TODO("Not yet implemented")
    }
}

class GDriveRoot(fs: GoogleDriveFS) : GDriveFolder(fs, emptyList()), RootFolder {
    override val name: String
        get() = ""
    override val parent: Folder
        get() = this
    override val absolutePath: AbsolutePath
        get() = emptyList()
}

class GDriveFile(fs: GoogleDriveFS, path: AbsolutePath) : GDriveNode(fs, path), File {
    override suspend fun remove() {
        TODO("Not yet implemented")
    }

    override suspend fun read(): ByteArray {
        TODO("Not yet implemented")
    }

    override suspend fun write(data: ByteArray) {
        TODO("Not yet implemented")
    }
}