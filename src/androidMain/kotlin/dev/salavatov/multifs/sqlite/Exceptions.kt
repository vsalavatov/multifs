package dev.salavatov.multifs.sqlite

import dev.salavatov.multifs.vfs.VFSException

open class SqliteFSException(message: String? = null, cause: Throwable? = null) : VFSException(message, cause)
open class SqliteFSNodeNotFoundException(message: String? = null, cause: Throwable? = null) : SqliteFSException(message, cause)
open class SqliteFSFolderNotFoundException(message: String? = null, cause: Throwable? = null) : SqliteFSNodeNotFoundException(message, cause)
open class SqliteFSFileNotFoundException(message: String? = null, cause: Throwable? = null) : SqliteFSNodeNotFoundException(message, cause)
