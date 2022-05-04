package dev.salavatov.multifs.systemfs

import dev.salavatov.multifs.vfs.*
import dev.salavatov.multifs.vfs.extensions.FileWStreamingIO
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import java.nio.file.*
import kotlin.io.FileAlreadyExistsException
import kotlin.io.path.*

open class SystemFS(protected val rootPath: Path = Paths.get(".").toAbsolutePath().root) :
    VFS<SystemFSFile, SystemFSFolder> {
    companion object {
        fun AbsolutePath.represent(): String = joinToString("/", "/")
    }

    override val root: SystemFSRoot
        get() = SystemFSRoot(rootPath)

    override suspend fun copy(
        file: File,
        newParent: Folder,
        newName: PathPart?,
        overwrite: Boolean
    ): SystemFSFile {
        if (newParent !is SystemFSFolder) throw SystemFSException("can't operate on folders that don't belong to SystemFS")
        val targetName = newName ?: file.name
        if (newParent == file.parent && targetName == file.name) {
            // no op
            return file.fromGeneric()
        }
        if (file is SystemFSFile) {
            val targetPath = newParent.nioPath / targetName
            try {
                val newNioPath = file.nioPath.copyTo(targetPath, overwrite = overwrite)
                return SystemFSFile(newNioPath)
            } catch (e: FileAlreadyExistsException) {
                throw SystemFSFileExistsException(
                    "couldn't copy ${file.absolutePath} to ${targetPath.absolutePathString()}: target file exists",
                    e
                )
            } catch (e: Throwable) {
                throw SystemFSException("couldn't copy ${file.absolutePath} to ${targetPath.absolutePathString()}", e)
            }
        }
        return genericCopy(file, newParent, targetName, overwrite, onExistsThrow = {
            throw SystemFSFileExistsException("couldn't copy ${file.absolutePath} to ${it.absolutePath}: target file exists")
        })
    }

    override suspend fun move(
        file: File,
        newParent: Folder,
        newName: PathPart?,
        overwrite: Boolean
    ): SystemFSFile {
        if (newParent !is SystemFSFolder) throw SystemFSException("can't operate on folders that don't belong to SystemFS")
        val targetName = newName ?: file.name
        if (newParent == file.parent && targetName == file.name) {
            // no op
            return file.fromGeneric()
        }
        if (file is SystemFSFile) {
            val targetPath = newParent.nioPath / targetName
            try {
                val newNioPath = file.nioPath.moveTo(targetPath, overwrite = overwrite)
                return SystemFSFile(newNioPath)
            } catch (e: FileAlreadyExistsException) {
                throw SystemFSFileExistsException(
                    "couldn't move ${file.absolutePath} to ${targetPath.absolutePathString()}: target file exists",
                    e
                )
            } catch (e: Throwable) {
                throw SystemFSException("couldn't move ${file.absolutePath} to ${targetPath.absolutePathString()}", e)
            }
        }
        return genericMove(file, newParent, targetName, overwrite, onExistsThrow = {
            throw SystemFSFileExistsException("couldn't move ${file.absolutePath} to ${it.absolutePath}: target file exists")
        })
    }

    override fun representPath(path: AbsolutePath): String = path.represent()

    private fun File.fromGeneric(): SystemFSFile =
        this as? SystemFSFile
            ?: throw SystemFSException("expected the file to be part of the SystemFS (class: ${this.javaClass.kotlin.qualifiedName}")
}

sealed class SystemNode(val nioPath: Path) : VFSNode {
    override val name: String
        get() = nioPath.fileName.name
    override val absolutePath: AbsolutePath
        get() = computeAbsolutePath(this)

    override fun toString(): String = SystemFS.run { absolutePath.represent() }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is SystemNode) {
            return nioPath == other.nioPath
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return nioPath.hashCode()
    }
}

open class SystemFSFolder(nioPath: Path) : SystemNode(nioPath), Folder {
    override val parent: SystemFSFolder
        get() = if (nioPath.parent != null) SystemFSFolder(nioPath.parent) else this

    override suspend fun listFolder(): List<SystemNode> =
        try {
            nioPath.listDirectoryEntries().filter { it.isDirectory() || it.isRegularFile() }.map {
                if (it.isDirectory()) {
                    SystemFSFolder(it)
                } else if (it.isRegularFile()) {
                    SystemFSFile(it)
                } else {
                    throw SystemFSException("unexpected path: $it")
                }
            }
        } catch (e: NotDirectoryException) {
            throw SystemFSFolderNotFoundException("couldn't list folder $name: folder not found", e)
        } catch (e: Throwable) {
            throw SystemFSException("couldn't list folder $name", e)
        }

    override suspend fun createFolder(name: PathPart) = try {
        SystemFSFolder((nioPath / name).createDirectory())
    } catch (e: FileAlreadyExistsException) {
        throw SystemFSNodeExistsException(
            "couldn't create folder $name at $absolutePath: node with such name already exists",
            e
        )
    } catch (e: Throwable) {
        throw SystemFSException("couldn't create file $name at $absolutePath", e)
    }

    override suspend fun remove(recursively: Boolean) {
        if (recursively) {
            listFolder().forEach {
                when (it) {
                    is SystemFSFolder -> it.remove(true)
                    is SystemFSFile -> it.remove()
                }
            }
        }
        try {
            nioPath.deleteExisting()
        } catch (e: DirectoryNotEmptyException) {
            throw SystemFSException("couldn't delete $name: folder isn't empty", e)
        } catch (e: Throwable) {
            throw SystemFSException("couldn't delete $name", e)
        }
    }

    override suspend fun createFile(name: PathPart) = try {
        SystemFSFile((nioPath / name).createFile())
    } catch (e: FileAlreadyExistsException) {
        throw SystemFSNodeExistsException(
            "couldn't create file $name at $absolutePath: node with such name already exists",
            e
        )
    } catch (e: Throwable) {
        throw SystemFSException("couldn't create file $name at $absolutePath", e)
    }

    override suspend fun div(path: PathPart): SystemFSFolder {
        val nextPath = nioPath / path
        if (nextPath.notExists())
            throw SystemFSFolderNotFoundException("folder $path not found at $absolutePath")
        if (nextPath.isDirectory()) {
            return SystemFSFolder(nextPath)
        }
        throw SystemFSException("expected a directory but it is not: $nextPath")
    }

    override suspend fun rem(path: PathPart): SystemFSFile {
        val nextPath = nioPath / path
        if (nextPath.notExists())
            throw SystemFSFileNotFoundException("file $path not found at $absolutePath")
        if (nextPath.isRegularFile()) {
            return SystemFSFile(nextPath)
        }
        throw SystemFSException("expected a file but it is not: $nextPath")
    }
}

open class SystemFSRoot(actualPathToRoot: Path) : SystemFSFolder(actualPathToRoot) {
    override val name: String
        get() = ""
    override val parent: SystemFSFolder
        get() = this
    override val absolutePath: AbsolutePath
        get() = emptyList()
}

open class SystemFSFile(nioPath: Path) : SystemNode(nioPath), FileWStreamingIO {
    override val parent: SystemFSFolder
        get() = SystemFSFolder(nioPath.parent)

    override suspend fun getSize(): Long {
        try {
            return nioPath.fileSize()
        } catch (e: NoSuchFileException) {
            throw SystemFSFileNotFoundException("couldn't get size of $name: file doesn't exist", e)
        } catch (e: Throwable) {
            throw SystemFSException("couldn't get size of $name", e)
        }
    }

    override suspend fun remove() {
        try {
            nioPath.deleteExisting()
        } catch (e: NoSuchFileException) {
            throw SystemFSFileNotFoundException("couldn't delete $name: file doesn't exist", e)
        } catch (e: Throwable) {
            throw SystemFSException("couldn't delete $name", e)
        }
    }

    override suspend fun read(): ByteArray {
        try {
            return nioPath.readBytes()
        } catch (e: Throwable) {
            throw SystemFSException("couldn't read $name", e)
        }
    }

    override suspend fun write(data: ByteArray) {
        try {
            nioPath.writeBytes(data)
        } catch (e: Throwable) {
            throw SystemFSException("couldn't write to $name", e)
        }
    }

    override suspend fun readStream(): ByteReadChannel {
        return nioPath.readChannel()
    }

    override suspend fun writeStream(data: ByteReadChannel) {
        try {
            data.copyAndClose(nioPath.toFile().writeChannel())
        } catch (e: Throwable) {
            throw SystemFSException("couldn't write stream to $name", e)
        }
    }
}
