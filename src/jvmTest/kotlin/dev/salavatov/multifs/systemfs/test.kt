package dev.salavatov.multifs.systemfs

import dev.salavatov.multifs.vfs.VFSException
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        assertEquals("[]", fs.absolutePath.toString())
        val projectDir = fs / "home" / "vsalavatov" / "Projects" / "bsse-diploma" / "multifs"
        val gitignoreFile = projectDir % ".gitignore"
        assertTrue(gitignoreFile.read().decodeToString().startsWith(".idea/**"))
        val tmpfile = (projectDir / "build").createFile("tmpfile")
        val data = byteArrayOf(1, 2, 3)
        tmpfile.write(data)
        assertContentEquals(tmpfile.read(), data)
        tmpfile.remove()
        assertThrows<VFSException> { runBlocking { projectDir / "build" % "tmpfile" } }
    }
}
