package dev.salavatov.multifs.vfs

fun computeAbsolutePath(node: VFSNode): AbsolutePath {
    val result = mutableListOf<PathPart>()
    var current: Folder = if (node is File) {
        result.add(node.name)
        node.parent
    } else {
        node as Folder
    }
    while (!current.isRoot) {
        result.add(current.name)
        current = current.parent
    }
    return result.reversed()
}