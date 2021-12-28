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

suspend inline fun <reified T: VFSNode> Folder.find(path: PathPart): T? {
    listFolder().forEach {
        if (it.name == path && it is T) {
            return it
        }
    }
    return null
}

interface RootFolder : Folder {
    override val parent: Folder
        get() = this
    override val name: String
        get() = ""
    override val absolutePath: AbsolutePath
        get() = emptyList()
}
