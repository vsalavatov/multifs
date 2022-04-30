package dev.salavatov.multifs.systemfs

import dev.salavatov.multifs.vfs.VFSException
import dev.salavatov.multifs.vfs.VFSFileExistsException
import dev.salavatov.multifs.vfs.VFSFileNotFoundException
import dev.salavatov.multifs.vfs.VFSFolderNotFoundException

open class SystemFSException(message: String? = null, cause: Throwable? = null) : VFSException(message, cause)

open class SystemFSNodeNotFoundException(message: String? = null, cause: Throwable? = null) :
    SystemFSException(message, cause)

open class SystemFSFolderNotFoundException(message: String? = null, cause: Throwable? = null) :
    SystemFSNodeNotFoundException(message, cause), VFSFolderNotFoundException

open class SystemFSFileNotFoundException(message: String? = null, cause: Throwable? = null) :
    SystemFSNodeNotFoundException(message, cause), VFSFileNotFoundException

open class SystemFSFileExistsException(message: String? = null, cause: Throwable? = null) :
    SystemFSException(message, cause), VFSFileExistsException
