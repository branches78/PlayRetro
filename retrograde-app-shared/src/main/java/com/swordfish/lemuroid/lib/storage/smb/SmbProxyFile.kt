package com.swordfish.lemuroid.lib.storage.smb

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.system.ErrnoException
import android.system.OsConstants
import androidx.annotation.RequiresApi
import timber.log.Timber

/**
 * A file of the network share given to a core as a file descriptor, read from the share as the core asks for it
 * instead of copying the whole file first (a PlayStation disc is ~700 MB). Reads go in blocks of [BLOCK_SIZE] and the
 * last [CACHED_BLOCKS] are kept, so the sector by sector reads of a core don't make a request each.
 *
 * The connection may close in the middle of a game (a Wi-Fi drop, the server closing the session): a failed read
 * opens the file again with [open], which connects again when needed, and retries a few times before failing.
 */
@RequiresApi(Build.VERSION_CODES.O)
class SmbProxyFile private constructor(
    private val open: () -> SmbShare.RandomAccessFile,
    private var file: SmbShare.RandomAccessFile,
    private val thread: HandlerThread,
) : ProxyFileDescriptorCallback() {
    private val size = file.size

    private val blocks = object : LinkedHashMap<Long, ByteArray>(CACHED_BLOCKS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>?): Boolean = size > CACHED_BLOCKS
    }

    // Only the first error of a series is logged: a core keeps asking for the same sector many times per second.
    private var failing = false

    override fun onGetSize(): Long = size

    override fun onRead(
        offset: Long,
        size: Int,
        data: ByteArray,
    ): Int {
        try {
            var copied = 0
            while (copied < size && offset + copied < this.size) {
                val position = offset + copied
                val blockIndex = position / BLOCK_SIZE
                val block = blocks.getOrPut(blockIndex) { readBlockWithRetries(blockIndex) }
                val inBlock = (position - blockIndex * BLOCK_SIZE).toInt()
                if (inBlock >= block.size) break
                val count = minOf(size - copied, block.size - inBlock)
                System.arraycopy(block, inBlock, data, copied, count)
                copied += count
            }
            failing = false
            return copied
        } catch (e: Exception) {
            if (!failing) Timber.e(e, "Error while reading from the network share")
            failing = true
            throw ErrnoException("onRead", OsConstants.EIO)
        }
    }

    override fun onRelease() {
        runCatching { file.close() }
        thread.quitSafely()
    }

    private fun readBlockWithRetries(index: Long): ByteArray {
        var lastError: Exception? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                if (attempt > 0) reopen(attempt)
                return readBlock(index)
            } catch (e: Exception) {
                if (!failing) Timber.w(e, "Read from the network share failed (attempt ${attempt + 1})")
                lastError = e
            }
        }
        throw lastError!!
    }

    // A new file of a connection that works: the old one may belong to a closed connection, so it is closed without
    // waiting for the server (that waited for the whole request timeout).
    private fun reopen(attempt: Int) {
        file.closeInBackground()
        // The first retry is immediate (usually just a closed connection), the next ones wait a bit more each.
        Thread.sleep(RETRY_DELAY_MS * (attempt - 1))
        file = open()
        Timber.i("Network share file opened again")
    }

    private fun readBlock(index: Long): ByteArray {
        val start = index * BLOCK_SIZE
        val length = minOf(BLOCK_SIZE.toLong(), size - start).toInt()
        val buffer = ByteArray(length)
        var read = 0
        // The server may return less than asked for (its maximum read size).
        while (read < length) {
            val count = file.read(buffer, start + read, read, length - read)
            if (count <= 0) break
            read += count
        }
        return if (read == length) buffer else buffer.copyOf(read)
    }

    companion object {
        private const val BLOCK_SIZE = 1024 * 1024
        private const val CACHED_BLOCKS = 8
        private const val MAX_ATTEMPTS = 4
        private const val RETRY_DELAY_MS = 500L

        /**
         * File descriptor of a file of the network share, opened (and opened again after an error) with [open]. The
         * file stays open until the descriptor is closed.
         */
        fun open(
            context: Context,
            open: () -> SmbShare.RandomAccessFile,
        ): ParcelFileDescriptor {
            val file = open()
            val thread = HandlerThread("smb-proxy-file").apply { start() }
            return try {
                val storageManager = context.getSystemService(StorageManager::class.java)
                storageManager.openProxyFileDescriptor(
                    ParcelFileDescriptor.MODE_READ_ONLY,
                    SmbProxyFile(open, file, thread),
                    Handler(thread.looper),
                )
            } catch (e: Exception) {
                file.close()
                thread.quitSafely()
                throw e
            }
        }
    }
}
