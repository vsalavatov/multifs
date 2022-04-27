package dev.salavatov.multifs.vfs

interface VFSNode {
    val name: String
    val parent: Folder
    val absolutePath: AbsolutePath
}

interface File : VFSNode {
    suspend fun remove()

    suspend fun read(): ByteArray
    suspend fun write(data: ByteArray)
}

interface Folder : VFSNode {
    suspend fun listFolder(): List<VFSNode>

    suspend fun createFolder(name: PathPart): Folder
    suspend fun remove(recursively: Boolean = false)

    suspend fun createFile(name: PathPart): File

    suspend operator fun div(path: PathPart): Folder
    suspend operator fun rem(path: PathPart): File
}

val Folder.isRoot: Boolean
    get() = this == this.parent