package dev.salavatov.multifs.systemfs

import dev.salavatov.multifs.vfs.*
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*

open class SystemFS(private val rootPath: Path = Paths.get(".").toAbsolutePath().root) : VFS<SystemFSFile, SystemFSFolder> {
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
        if (newParent !is SystemFSFolder) throw SystemFSException("can't operate on folders that don't belong to SqliteFS")
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
                throw SystemFSFileExistsException("couldn't copy ${file.absolutePath} to ${targetPath.absolutePathString()}: target file exists", e)
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
        if (newParent !is SystemFSFolder) throw SystemFSException("can't operate on folders that don't belong to SqliteFS")
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
                throw SystemFSFileExistsException("couldn't move ${file.absolutePath} to ${targetPath.absolutePathString()}: target file exists", e)
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
            ?: throw SystemFSException("expected the file to be a part of the SystemFS (class: ${this.javaClass.kotlin.qualifiedName}")
}

sealed class SystemNode(val nioPath: Path) : VFSNode {
    override val name: String
        get() = nioPath.fileName.name
    override val parent: Folder
        get() = if (nioPath.parent != null) SystemFSFolder(nioPath.parent) else (this as Folder)
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
    override suspend fun listFolder(): List<SystemNode> =
        nioPath.listDirectoryEntries().filter { it.isDirectory() || it.isRegularFile() }.map {
            if (it.isDirectory()) {
                SystemFSFolder(it)
            } else if (it.isRegularFile()) {
                SystemFSFile(it)
            } else {
                throw VFSException("unexpected path: $it")
            }
        }

    override suspend fun createFolder(name: PathPart) = SystemFSFolder((nioPath / name).createDirectory())

    override suspend fun remove(recursively: Boolean) {
        if (recursively) {
            listFolder().forEach {
                when (it) {
                    is SystemFSFolder -> it.remove(true)
                    is SystemFSFile -> it.remove()
                }
            }
        }
        nioPath.deleteExisting()
    }

    override suspend fun createFile(name: PathPart) = SystemFSFile((nioPath / name).createFile())

    override suspend fun div(path: PathPart): SystemFSFolder {
        val nextPath = nioPath / path
        if (nextPath.isDirectory()) {
            return SystemFSFolder(nextPath)
        }
        throw VFSException("expected a directory but it is not: $nextPath")
    }

    override suspend fun rem(path: PathPart): SystemFSFile {
        val nextPath = nioPath / path
        if (nextPath.isRegularFile()) {
            return SystemFSFile(nextPath)
        }
        throw VFSException("expected a file but it is not: $nextPath")
    }
}

open class SystemFSRoot(actualPathToRoot: Path) : SystemFSFolder(actualPathToRoot) {
    override val name: String
        get() = ""
    override val parent: Folder
        get() = this
    override val absolutePath: AbsolutePath
        get() = emptyList()
}

open class SystemFSFile(nioPath: Path) : SystemNode(nioPath), File {
    override suspend fun remove() {
        nioPath.deleteExisting()
    }

    override suspend fun read(): ByteArray {
        return nioPath.readBytes()
    }

    override suspend fun write(data: ByteArray) {
        nioPath.writeBytes(data)
    }
}
