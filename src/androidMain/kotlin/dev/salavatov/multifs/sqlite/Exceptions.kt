package dev.salavatov.multifs.sqlite

import dev.salavatov.multifs.vfs.*

open class SqliteFSException(message: String? = null, cause: Throwable? = null) : VFSException(message, cause)

open class SqliteFSFolderNotEmptyException(message: String? = null, cause: Throwable? = null) :
    SqliteFSException(message, cause), VFSFolderNotEmptyException

open class SqliteFSNodeNotFoundException(message: String? = null, cause: Throwable? = null) :
    SqliteFSException(message, cause), VFSNodeNotFoundException

open class SqliteFSFolderNotFoundException(message: String? = null, cause: Throwable? = null) :
    SqliteFSNodeNotFoundException(message, cause), VFSFolderNotFoundException

open class SqliteFSFileNotFoundException(message: String? = null, cause: Throwable? = null) :
    SqliteFSNodeNotFoundException(message, cause), VFSFileNotFoundException

open class SqliteFSNodeExistsException(message: String? = null, cause: Throwable? = null) :
    SqliteFSException(message, cause), VFSNodeExistsException

open class SqliteFSFileExistsException(message: String? = null, cause: Throwable? = null) :
    SqliteFSNodeExistsException(message, cause), VFSFileExistsException
