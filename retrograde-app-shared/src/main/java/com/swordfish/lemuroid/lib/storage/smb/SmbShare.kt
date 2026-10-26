package com.swordfish.lemuroid.lib.storage.smb

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import com.hierynomus.smbj.SmbConfig as SmbjConfig

/** An open connection to the share of a [SmbConfig]. Paths are relative to the share, with "\" separators. */
class SmbShare private constructor(
    private val client: SMBClient,
    private val connection: Connection,
    private val session: Session,
    private val share: DiskShare,
) : Closeable {
    /** A file or folder of the share. */
    data class Entry(
        val name: String,
        val path: String,
        val size: Long,
        val lastModified: Long,
        val isDirectory: Boolean,
    )

    val isConnected: Boolean
        get() = connection.isConnected && share.isConnected

    fun list(directory: String): List<Entry> {
        return share.list(directory)
            .filter { it.fileName != "." && it.fileName != ".." }
            .map {
                val isDirectory = it.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
                Entry(
                    name = it.fileName,
                    path = join(directory, it.fileName),
                    size = it.endOfFile,
                    lastModified = it.lastWriteTime.toEpochMillis(),
                    isDirectory = isDirectory,
                )
            }
    }

    /**
     * Stream of a file of the share; closing it closes the file (the share stays open). It is read in blocks of
     * [READ_BUFFER_SIZE]: in small ones each read is a round trip to the server, which made identifying games slow.
     */
    fun openRead(path: String): InputStream {
        val file =
            share.openFile(
                path,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null,
            )
        val buffered = BufferedInputStream(file.inputStream, READ_BUFFER_SIZE)
        return object : FilterInputStream(buffered) {
            override fun close() {
                runCatching { super.close() }
                file.close()
            }
        }
    }

    fun folderExists(path: String): Boolean = path.isEmpty() || share.folderExists(path)

    /** Creates [path] and the folders above it that don't exist yet. */
    fun makeDirectories(path: String) {
        var current = ""
        path.split(SmbConfig.SMB_SEPARATOR).filter { it.isNotEmpty() }.forEach { part ->
            current = join(current, part)
            if (!share.folderExists(current)) share.mkdir(current)
        }
    }

    /** Size and last change of a file of the share, null when it doesn't exist. */
    fun info(path: String): Entry? {
        if (!share.fileExists(path)) return null
        val information = share.getFileInformation(path)
        return Entry(
            name = path.substringAfterLast(SmbConfig.SMB_SEPARATOR),
            path = path,
            size = information.standardInformation.endOfFile,
            lastModified = information.basicInformation.lastWriteTime.toEpochMillis(),
            isDirectory = false,
        )
    }

    /** Stream that replaces the content of a file of the share (created when missing); closing it closes the file. */
    fun openWrite(path: String): OutputStream {
        val file =
            share.openFile(
                path,
                EnumSet.of(AccessMask.GENERIC_WRITE),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                null,
            )
        return object : FilterOutputStream(BufferedOutputStream(file.outputStream, READ_BUFFER_SIZE)) {
            override fun close() {
                runCatching { super.close() }
                file.close()
            }
        }
    }

    /** A file of the share read at any position, kept open until [close]. */
    class RandomAccessFile(private val file: File) : Closeable {
        val size: Long = file.fileInformation.standardInformation.endOfFile

        fun read(
            buffer: ByteArray,
            fileOffset: Long,
            bufferOffset: Int,
            length: Int,
        ): Int = file.read(buffer, fileOffset, bufferOffset, length)

        override fun close() = file.close()

        /** Closes the file without waiting: on a broken connection the answer of the server never comes. */
        fun closeInBackground() {
            Thread { runCatching { file.close() } }.start()
        }
    }

    fun openRandomAccess(path: String): RandomAccessFile {
        val file =
            share.openFile(
                path,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null,
            )
        return RandomAccessFile(file)
    }

    override fun close() {
        runCatching { share.close() }
        runCatching { session.close() }
        runCatching { connection.close() }
        runCatching { client.close() }
    }

    /** Closes the connection without waiting: on a broken connection the answers of the server never come. */
    fun closeInBackground() {
        Thread { close() }.start()
    }

    companion object {
        private const val TIMEOUT_SECONDS = 30L
        private const val READ_BUFFER_SIZE = 1024 * 1024

        /** Connects to the share of the [config], signing in when it uses authentication. */
        fun open(config: SmbConfig): SmbShare {
            val client =
                SMBClient(
                    // No socket timeout: it closed connections that only had nothing to receive for a while (a
                    // game reading from its memory cache), which is not a network failure. Requests keep theirs.
                    SmbjConfig.builder()
                        .withTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .withReadBufferSize(READ_BUFFER_SIZE)
                        .build(),
                )
            try {
                val connection = client.connect(config.server.trim())
                val session = connection.authenticate(authentication(config))
                val share = session.connectShare(config.shareName) as DiskShare
                return SmbShare(client, connection, session, share)
            } catch (e: Exception) {
                client.close()
                throw e
            }
        }

        // "DOMAIN\user" signs in to that domain.
        private fun authentication(config: SmbConfig): AuthenticationContext {
            if (!config.useAuthentication) return AuthenticationContext.guest()
            val domain = config.user.substringBefore('\\', missingDelimiterValue = "").ifBlank { null }
            val user = config.user.substringAfter('\\')
            return AuthenticationContext(user, config.password.toCharArray(), domain)
        }

        fun join(
            directory: String,
            name: String,
        ): String = if (directory.isEmpty()) name else directory + SmbConfig.SMB_SEPARATOR + name
    }
}
