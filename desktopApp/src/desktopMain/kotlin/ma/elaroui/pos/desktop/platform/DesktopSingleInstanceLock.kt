package ma.elaroui.pos.desktop.platform

import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/** Keeps one Linux POS process per user-data directory. */
class DesktopSingleInstanceLock private constructor(
    private val channel: FileChannel,
    private val lock: FileLock
) : AutoCloseable {
    override fun close() {
        runCatching { lock.release() }
        runCatching { channel.close() }
    }

    companion object {
        fun acquire(lockFile: Path): DesktopSingleInstanceLock? {
            lockFile.parent?.let(Files::createDirectories)
            val channel = FileChannel.open(
                lockFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
            )
            val lock = runCatching { channel.tryLock() }.getOrNull()
            if (lock == null) {
                channel.close()
                return null
            }
            return DesktopSingleInstanceLock(channel, lock)
        }
    }
}
