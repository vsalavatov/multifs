package dev.salavatov.multifs.sqlite

import dev.salavatov.multifs.vfs.VFSException
import dev.salavatov.multifs.vfs.VFSFileExistsException
import dev.salavatov.multifs.vfs.VFSFileNotFoundException
import dev.salavatov.multifs.vfs.VFSFolderNotFoundException

open class SqliteFSException(message: String? = null, cause: Throwable? = null) : VFSException(message, cause)

open class SqliteFSNodeNotFoundException(message: String? = null, cause: Throwable? = null) :
    SqliteFSException(message, cause)

open class SqliteFSFolderNotFoundException(message: String? = null, cause: Throwable? = null) :
    SqliteFSNodeNotFoundException(message, cause), VFSFileNotFoundException

open class SqliteFSFileNotFoundException(message: String? = null, cause: Throwable? = null) :
    SqliteFSNodeNotFoundException(message, cause), VFSFolderNotFoundException

open class SqliteFSFileExistsException(message: String? = null, cause: Throwable? = null) :
        SqliteFSException(message, cause), VFSFileExistsException
