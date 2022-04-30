package dev.salavatov.multifs.cloud.googledrive

import dev.salavatov.multifs.vfs.*

open class GoogleDriveFSException(message: String? = null, cause: Throwable? = null) : VFSException(message, cause)

open class GoogleDriveFSNodeNotFoundException(message: String? = null, cause: Throwable? = null) :
    GoogleDriveFSException(message, cause), VFSNodeNotFoundException

open class GoogleDriveFSFolderNotFoundException(message: String? = null, cause: Throwable? = null) :
    GoogleDriveFSNodeNotFoundException(message, cause), VFSFolderNotFoundException

open class GoogleDriveFSFileNotFoundException(message: String? = null, cause: Throwable? = null) :
    GoogleDriveFSNodeNotFoundException(message, cause), VFSFileNotFoundException

open class GoogleDriveFSNodeExistsException(message: String? = null, cause: Throwable? = null) :
    GoogleDriveFSException(message, cause), VFSNodeExistsException

open class GoogleDriveFSFileExistsException(message: String? = null, cause: Throwable? = null) :
    GoogleDriveFSNodeExistsException(message, cause), VFSFileExistsException