package dev.salavatov.multifs.systemfs

import dev.salavatov.multifs.vfs.*

open class SystemFSException(message: String? = null, cause: Throwable? = null) : VFSException(message, cause)

open class SystemFSFolderNotEmptyException(message: String? = null, cause: Throwable? = null) :
    SystemFSException(message, cause), VFSFolderNotEmptyException

open class SystemFSNodeNotFoundException(message: String? = null, cause: Throwable? = null) :
    SystemFSException(message, cause), VFSNodeNotFoundException

open class SystemFSFolderNotFoundException(message: String? = null, cause: Throwable? = null) :
    SystemFSNodeNotFoundException(message, cause), VFSFolderNotFoundException

open class SystemFSFileNotFoundException(message: String? = null, cause: Throwable? = null) :
    SystemFSNodeNotFoundException(message, cause), VFSFileNotFoundException

open class SystemFSNodeExistsException(message: String? = null, cause: Throwable? = null) :
    SystemFSException(message, cause), VFSNodeExistsException

open class SystemFSFileExistsException(message: String? = null, cause: Throwable? = null) :
    SystemFSNodeExistsException(message, cause), VFSFileExistsException

open class SystemFSFolderExistsException(message: String? = null, cause: Throwable? = null) :
    SystemFSNodeExistsException(message, cause), VFSFolderExistsException