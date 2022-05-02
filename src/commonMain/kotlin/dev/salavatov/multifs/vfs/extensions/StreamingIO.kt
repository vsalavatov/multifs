package dev.salavatov.multifs.vfs.extensions

import dev.salavatov.multifs.vfs.File
import io.ktor.utils.io.*

interface StreamingIO {
    suspend fun readStream(): ByteReadChannel
    suspend fun writeStream(data: ByteReadChannel)
}

interface FileWStreamingIO: File, StreamingIO