package dev.salavatov.multifs.systemfs

import dev.salavatov.multifs.vfs.VFSException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SystemFSTest {
    @Test
    fun `basic SystemFS sanity check`() = runBlockingTest {
        val fs = SystemFS()
        assertEquals("[]", fs.root.absolutePath.toString())
        val projectDir = fs.root / "home" / "vsalavatov" / "Projects" / "bsse-diploma" / "multifs"
        val gitignoreFile = projectDir % ".gitignore"
        assertTrue(gitignoreFile.read().decodeToString().startsWith(".idea/**"))
        val buildDir = projectDir / "build"
        val tmpfile = buildDir.createFile("tmpfile")
        val data = byteArrayOf(1, 2, 3)
        tmpfile.write(data)
        assertContentEquals(tmpfile.read(), data)
        val tmpcopy = fs.copy(tmpfile, buildDir, "tmpcopy")
        tmpfile.remove()
        assertContentEquals(tmpcopy.read(), data)
        val tmpmove = fs.move(tmpcopy, buildDir, "tmpmove")
        assertContentEquals(tmpmove.read(), data)
        tmpmove.remove()
        for (filename in listOf("tmpfile", "tmpcopy", "tmpmove")) {
            assertThrows<VFSException> { runBlocking { buildDir % filename } }
        }
    }
}
