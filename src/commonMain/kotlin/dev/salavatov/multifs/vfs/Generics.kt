package dev.salavatov.multifs.vfs

suspend inline fun <
        reified FileClass : File,
        reified FolderClass : Folder,
        > genericCopy(
    file: File,
    newParent: FolderClass,
    newName: PathPart,
    overwrite: Boolean,
    onExistsThrow: (FileClass) -> Nothing
): FileClass {
    try {
        val targetFile: FileClass = (newParent % newName) as FileClass
        // targetFile exists
        if (overwrite) {
            targetFile.write(file.read())
            return targetFile
        } else {
            onExistsThrow(targetFile)
        }
    } catch (e: Throwable) {
        // got Type mismatch: inferred type is VFSFileNotFoundException but Throwable was expected
        // when tried to catch e: VFSFileNotFoundException
        if (e is VFSFileNotFoundException) {
            val targetFile = newParent.createFile(newName) as FileClass
            targetFile.write(file.read())
            return targetFile
        }
        throw e
    }
}

suspend inline fun <
        reified FileClass : File,
        reified FolderClass : Folder,
        > genericMove(
    file: File,
    newParent: FolderClass,
    newName: PathPart,
    overwrite: Boolean,
    onExistsThrow: (FileClass) -> Nothing
): FileClass {
    try {
        val targetFile = (newParent % newName) as FileClass
        // targetFile exists
        if (overwrite) {
            targetFile.write(file.read())
            file.remove()
            return targetFile
        } else {
            onExistsThrow(targetFile)
        }
    } catch (e: Throwable) {
        if (e is VFSFileNotFoundException) {
            val targetFile = newParent.createFile(newName) as FileClass
            targetFile.write(file.read())
            file.remove()
            return targetFile
        }
        throw e
    }
}

