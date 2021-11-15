package dev.salavatov.multifs.vfs

fun simpleAbsolutePath(node: VFSNode): AbsolutePath {
    var current = node
    val result = mutableListOf<PathPart>()
    while (current != current.parent) {
        result.add(current.name)
        current = current.parent
    }
    return result.reversed()
}