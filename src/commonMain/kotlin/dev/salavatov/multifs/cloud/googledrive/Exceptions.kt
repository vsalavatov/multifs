package dev.salavatov.multifs.cloud.googledrive

import dev.salavatov.multifs.vfs.VFSException

open class GoogleDriveFSException(message: String? = null, cause: Throwable? = null) : VFSException(message, cause)
open class GoogleDriveNodeNotFoundException(message: String? = null, cause: Throwable? = null) : GoogleDriveFSException(message, cause)
open class GoogleDriveFolderNotFoundException(message: String? = null, cause: Throwable? = null) : GoogleDriveNodeNotFoundException(message, cause)
open class GoogleDriveFileNotFoundException(message: String? = null, cause: Throwable? = null) : GoogleDriveNodeNotFoundException(message, cause)

open class GoogleDriveAPIException(message: String? = null, cause: Throwable? = null) :
    GoogleDriveFSException(message, cause)