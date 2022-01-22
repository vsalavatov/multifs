package dev.salavatov.multifs.vfs

typealias GenericFS = VFS<out File, out Folder>

interface VFS<FileClass : File, FolderClass : Folder> {
    val root: FolderClass

    suspend fun move(file: FileClass, newParent: FolderClass, overwrite: Boolean = false): FileClass
    suspend fun copy(file: FileClass, folder: FolderClass, overwrite: Boolean = false): FileClass

    fun AbsolutePath.represent(): String
}