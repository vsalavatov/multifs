package dev.salavatov.multifs.systemfs

import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.*
import java.io.IOException
import java.lang.Integer.min
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.createDirectory
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SystemFSTest {
    val tmpdir = Paths.get(".tmpdir")

    lateinit var fs: SystemFS

    @BeforeAll
    fun `make tmp folder`() = runBlocking {
        fs = SystemFS(tmpdir.createDirectory())
    }

    @AfterAll
    fun `rm tmp folder`() {
        Files.walkFileTree(tmpdir, object : SimpleFileVisitor<Path?>() {
            override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                Files.delete(file!!)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path?, exc: IOException?): FileVisitResult {
                Files.delete(dir!!)
                return FileVisitResult.CONTINUE
            }
        })
    }

    @Test
    fun `root represents as empty list`() = runTest {
        val fs = SystemFS()
        assertEquals("[]", fs.root.absolutePath.toString())
    }

    @Test
    fun `createFile works`() = runTest {
        val f = fs.root.createFile("sample.txt")
        assert(f.nioPath.exists())
        f.remove()
        assert(!f.nioPath.exists())
    }

    @Test
    fun `write and read`() = runTest {
        val f = fs.root.createFile("sample.txt")
        val data = byteArrayOf(1, 2, 3)
        f.write(data)
        assert(f.nioPath.readBytes().contentEquals(data))
        f.remove()
    }

    @Test
    fun `createFolder works`() = runTest {
        val subdir = fs.root.createFolder("subdir")
        assert(subdir.nioPath.exists())
        val subfile = subdir.createFile("sample.txt")
        assert(subfile.nioPath.exists())
        subfile.remove()
        assert(!subfile.nioPath.exists())
        subdir.remove()
        assert(!subdir.nioPath.exists())
    }

    @Test
    fun `copy and move works`() = runTest {
        val f = fs.root.createFile("sample.txt")
        val data = byteArrayOf(1, 2, 3)
        f.write(data)

        val tmpcopy = fs.copy(f, fs.root, "copy.txt")
        assert(tmpcopy.nioPath.exists())
        assert(tmpcopy.read().contentEquals(data))

        val tmpmove = fs.move(tmpcopy, fs.root, "move.txt")
        assert(!tmpcopy.nioPath.exists())
        assert(tmpmove.nioPath.exists())
        assert(tmpmove.read().contentEquals(data))

        tmpmove.remove()
        f.remove()
    }

    @Nested
    @DisplayName("StreamingIO tests")
    inner class StreamingIOTest {
        suspend fun CoroutineScope.dataProducer(length: Int): ByteReadChannel {
            val chan = ByteChannel()
            launch {
                val CHUNK_SIZE = 420
                for (i in 0 until length step CHUNK_SIZE) {
                    val toWrite = min(CHUNK_SIZE, length - i)
                    var chunk = ByteArray(toWrite) {
                        it.toByte()
                    }
                    while (chunk.isNotEmpty()) {
                        chan.write(chunk.size) { buffer ->
                            val toPut = buffer.remaining()
//                            println("writing chunk $i $toPut")
                            buffer.put(chunk.take(toPut).toByteArray())
                            chunk = chunk.drop(toPut).toByteArray()
//                            println("after write")
                        }
                    }
                }
//                println("closing")
                chan.close()
//                println("closed")
            }
            return chan
        }

        val DATA_SIZE = 8 * 1024 * 1024

        @Test
        fun `write read`(): Unit = runTest {
            val f = fs.root.createFile("streaming-test.txt")
            val producer = dataProducer(DATA_SIZE)

            f.writeStream(producer)

//            println("========================")
            val dataStream = dataProducer(DATA_SIZE)
            val inputStream = f.readStream()
            var dataLeft = ByteArray(0)

            inputStream.consumeEachBufferRange { buffer, last ->
//                println("reading chunk ${inputStream.totalBytesRead}")

                val chunk = buffer.moveToByteArray()
                while (chunk.size > dataLeft.size) {
//                    println("chunk: ${chunk.size} have: ${dataLeft.size}")
                    dataStream.read {
                        dataLeft += it.moveToByteArray()
                    }
                }
//                println("! chunk: ${chunk.size} have: ${dataLeft.size}")
                assertContentEquals(dataLeft.take(chunk.size), chunk.toList())
                dataLeft = dataLeft.drop(chunk.size).toByteArray()

                !last
            }
            assert(dataStream.isClosedForRead)
            f.remove()
        }
    }
}
