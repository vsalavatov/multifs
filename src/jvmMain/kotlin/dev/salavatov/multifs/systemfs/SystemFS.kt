package dev.salavatov.multifs.systemfs

import dev.salavatov.multifs.vfs.*
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*

class SystemFS : VFS, RootFolder by SystemRoot {
    companion object {
        fun AbsolutePath.represent(): String = joinToString("/", "/")
    }

    override fun AbsolutePath.represent(): String = SystemFS.Companion.run { represent() }
}

sealed class SystemNode(protected val nioPath: Path): VFSNode {
    override val name: String
        get() = nioPath.fileName.name
    override val parent: Folder
        get() = if (nioPath.parent != null) SystemFolder(nioPath.parent) else (this as Folder)
    override val absolutePath: AbsolutePath
        get() = simpleAbsolutePath(this)

    override fun toString(): String = SystemFS.run { absolutePath.represent() }
}

open class SystemFolder(nioPath: Path) : SystemNode(nioPath), Folder {
    override suspend fun listFolder(): List<SystemNode> =
        nioPath.listDirectoryEntries().map {
            if (it.isDirectory()) {
                SystemFolder(it)
            } else if (it.isRegularFile()) {
                SystemFile(it)
            } else {
                throw VFSException("unexpected path: $it")
            }
        }

    override suspend fun createFolder(name: PathPart) = SystemFolder((nioPath / name).createDirectory())

    override suspend fun remove(recursively: Boolean) {
        if (recursively) {
            listFolder().forEach {
                when (it) {
                    is SystemFolder -> it.remove(true)
                    is SystemFile -> it.remove()
                }
            }
        }
        nioPath.deleteExisting()
    }

    override suspend fun createFile(name: PathPart) = SystemFile((nioPath / name).createFile())

    override suspend fun div(path: PathPart): SystemFolder {
        val nextPath = nioPath / path
        if (nextPath.isDirectory()) {
            return SystemFolder(nextPath)
        }
        throw VFSException("expected a directory but it is not: $nextPath")
    }

    override suspend fun rem(path: PathPart): SystemFile {
        val nextPath = nioPath / path
        if (nextPath.isRegularFile()) {
            return SystemFile(nextPath)
        }
        throw VFSException("expected a file but it is not: $nextPath")
    }
}

object SystemRoot : SystemFolder(Paths.get(".").toAbsolutePath().root), RootFolder {
    override val name: String
        get() = super<RootFolder>.name
    override val parent: Folder
        get() = super<RootFolder>.parent
    override val absolutePath: AbsolutePath
        get() = super<RootFolder>.absolutePath
}

class SystemFile(nioPath: Path): SystemNode(nioPath), File {
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
