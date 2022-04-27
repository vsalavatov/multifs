package dev.salavatov.multifs.systemfs

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.*
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.*
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
}
