package dev.salavatov.multifs.vfs

open class VFSException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

interface VFSFolderNotEmptyException

interface VFSNodeNotFoundException
interface VFSFileNotFoundException : VFSNodeNotFoundException
interface VFSFolderNotFoundException : VFSNodeNotFoundException

interface VFSNodeExistsException
interface VFSFileExistsException : VFSNodeExistsException
interface VFSFolderExistsException : VFSNodeExistsException
