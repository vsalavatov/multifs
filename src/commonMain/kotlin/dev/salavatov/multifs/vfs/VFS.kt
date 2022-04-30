package dev.salavatov.multifs.vfs

typealias GenericFS = VFS<out File, out Folder>

interface VFS<FileClass : File, FolderClass : Folder> {
    val root: FolderClass

    suspend fun move(
        file: File,
        newParent: Folder,
        newName: PathPart? = null,
        overwrite: Boolean = false
    ): FileClass

    suspend fun copy(
        file: File,
        newParent: Folder,
        newName: PathPart? = null,
        overwrite: Boolean = false
    ): FileClass

    fun representPath(path: AbsolutePath): String
}