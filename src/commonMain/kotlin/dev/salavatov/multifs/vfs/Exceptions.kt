package dev.salavatov.multifs.vfs

open class VFSException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)
interface VFSFileNotFoundException
interface VFSFolderNotFoundException
interface VFSFileExistsException