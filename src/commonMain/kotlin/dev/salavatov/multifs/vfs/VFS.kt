package dev.salavatov.multifs.vfs

interface VFS : RootFolder {
    fun AbsolutePath.represent(): String
}