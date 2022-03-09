package dev.salavatov.multifs.vfs

typealias GenericFS = VFS<out File, out Folder>

interface VFS<FileClass : File, FolderClass : Folder> {
    val root: FolderClass

    suspend fun move(
        file: FileClass,
        newParent: FolderClass,
        newName: PathPart? = null,
        overwrite: Boolean = false
    ): FileClass

    suspend fun copy(
        file: FileClass,
        newParent: FolderClass,
        newName: PathPart? = null,
        overwrite: Boolean = false
    ): FileClass

    fun representPath(path: AbsolutePath): String
}