package dev.salavatov.multifs.vfs

interface VFSNode {
    val name: String
    val parent: Folder
    val absolutePath: AbsolutePath
}

interface File : VFSNode {
    fun remove()

    suspend fun read(): ByteArray // InputStreamByteArray ?
    suspend fun write(data: ByteArray) // OutputStreamByteArray ?
}

interface Folder : VFSNode {
    fun listFolder(): List<VFSNode>

    fun createFolder(name: PathPart): Folder
    fun remove(recursively: Boolean = false)

    fun createFile(name: PathPart): File

    operator fun div(path: PathPart): Folder
    operator fun rem(path: PathPart): File
}

inline fun <reified T: VFSNode> Folder.find(path: PathPart): T? {
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
