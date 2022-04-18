package dev.salavatov.multifs.cloud.googledrive

import dev.salavatov.multifs.vfs.VFSException
import dev.salavatov.multifs.vfs.VFSFileExistsException
import dev.salavatov.multifs.vfs.VFSFileNotFoundException
import dev.salavatov.multifs.vfs.VFSFolderNotFoundException

open class GoogleDriveFSException(message: String? = null, cause: Throwable? = null) : VFSException(message, cause)

open class GoogleDriveFSNodeNotFoundException(message: String? = null, cause: Throwable? = null) :
    GoogleDriveFSException(message, cause)

open class GoogleDriveFSFolderNotFoundException(message: String? = null, cause: Throwable? = null) :
    GoogleDriveFSNodeNotFoundException(message, cause), VFSFileNotFoundException

open class GoogleDriveFSFileNotFoundException(message: String? = null, cause: Throwable? = null) :
    GoogleDriveFSNodeNotFoundException(message, cause), VFSFolderNotFoundException

open class GoogleDriveFSFileExistsException(message: String? = null, cause: Throwable? = null) :
    GoogleDriveFSException(message, cause), VFSFileExistsException
