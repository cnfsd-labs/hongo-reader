package app.honmani.reader.data.sync

import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * KOReader's document id for progress sync ("binary" checksum, `util.partialMD5`): the MD5 of
 * 1 KB samples at 0, 1 KB, 4 KB, 16 KB … 1 GB into the file, as far as the file goes. The same
 * book file gives the same id on every device, whatever its name.
 *
 * KOReader computes the first offset as `lshift(1024, -2)`, which LuaJIT's 32-bit shift makes 0.
 */
object PartialMd5 {
    private const val SAMPLE = 1024

    fun of(file: File): String = RandomAccessFile(file, "r").use { input ->
        val digest = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(SAMPLE)
        val length = input.length()
        for (i in -1..10) {
            val offset = if (i < 0) 0L else SAMPLE.toLong() shl (2 * i)
            if (offset >= length) break
            input.seek(offset)
            var read = 0
            while (read < SAMPLE) {
                val n = input.read(buffer, read, SAMPLE - read)
                if (n < 0) break
                read += n
            }
            digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}
