package dev.salavatov.multifs.vfs

interface VFS {
    val root: RootFolder

    fun AbsolutePath.represent(): String
}