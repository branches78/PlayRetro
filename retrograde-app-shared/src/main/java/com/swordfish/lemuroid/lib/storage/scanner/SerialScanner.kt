package com.swordfish.lemuroid.lib.storage.scanner

import com.swordfish.lemuroid.common.files.FileUtils
import com.swordfish.lemuroid.common.kotlin.indexOf
import com.swordfish.lemuroid.common.kotlin.kiloBytes
import com.swordfish.lemuroid.common.kotlin.megaBytes
import com.swordfish.lemuroid.common.kotlin.startsWithAny
import com.swordfish.lemuroid.lib.library.SystemID
import timber.log.Timber
import java.io.InputStream
import java.nio.charset.Charset
import kotlin.math.ceil
import kotlin.math.roundToInt

object SerialScanner {
    private val READ_BUFFER_SIZE = 64.kiloBytes()

    // Where the root folder of a disc and its SYSTEM.CNF are looked for: they are near the start of the disc.
    private val PS2_CHECK_LIMIT = 4.megaBytes()
    private const val ISO_SECTOR_SIZE = 2048
    private const val ROOT_RECORD_OFFSET = 156
    private const val ROOT_RECORD_SIZE = 34

    /** [unsupported]: a disc of a console that the app doesn't support (PS2), which must not become a game. */
    data class DiskInfo(val serial: String?, val systemID: SystemID?, val unsupported: Boolean = false)

    @ExperimentalUnsignedTypes
    private val MAGIC_NUMBERS =
        listOf(
            MagicNumber(
                0x0010,
                ubyteArrayOf(
                    0x53U,
                    0x45U,
                    0x47U,
                    0x41U,
                    0x44U,
                    0x49U,
                    0x53U,
                    0x43U,
                    0x53U,
                    0x59U,
                    0x53U,
                    0x54U,
                    0x45U,
                    0x4dU,
                ).toByteArray(),
                SystemID.SEGACD,
            ),
            MagicNumber(
                0x8008,
                ubyteArrayOf(
                    0x50U,
                    0x4cU,
                    0x41U,
                    0x59U,
                    0x53U,
                    0x54U,
                    0x41U,
                    0x54U,
                    0x49U,
                    0x4fU,
                    0x4eU,
                ).toByteArray(),
                SystemID.PSX,
            ),
            MagicNumber(
                0x9320,
                ubyteArrayOf(
                    0x50U,
                    0x4cU,
                    0x41U,
                    0x59U,
                    0x53U,
                    0x54U,
                    0x41U,
                    0x54U,
                    0x49U,
                    0x4fU,
                    0x4eU,
                ).toByteArray(),
                SystemID.PSX,
            ),
            MagicNumber(
                0x8008,
                ubyteArrayOf(0x50U, 0x53U, 0x50U, 0x20U, 0x47U, 0x41U, 0x4dU, 0x45U).toByteArray(),
                SystemID.PSP,
            ),
        )

    private val SEGA_CD_REGEX = Regex("([A-Z]+)?-?([0-9]+) ?-?([0-9]*)")

    private val PS_SERIAL_REGEX = Regex("^([A-Z]+)-?([0-9]+)")
    private val PS_SERIAL_REGEX2 = Regex("^([A-Z]+)_?([0-9]{3})\\.([0-9]{2})")

    private const val PS_SERIAL_MAX_SIZE = 12

    private val PSX_BASE_SERIALS =
        listOf(
            "CPCS",
            "SCES",
            "SIPS",
            "SLKA",
            "SLPS",
            "SLUS",
            "ESPM",
            "SLED",
            "SCPS",
            "SCAJ",
            "PAPX",
            "SLES",
            "HPS",
            "LSP",
            "SLPM",
            "SCUS",
            "SCED",
        )

    private val PSP_BASE_SERIALS =
        listOf(
            "ULES",
            "ULUS",
            "ULJS",
            "ULEM",
            "ULUM",
            "ULJM",
            "ULKS",
            "ULAS",
            "UCES",
            "UCUS",
            "UCJS",
            "UCAS",
            "NPEH",
            "NPUH",
            "NPJH",
            "NPEG",
            "NPEX",
            "NPUG",
            "NPJG",
            "NPJJ",
            "NPHG",
            "NPEZ",
            "NPUZ",
            "NPJZ",
            "NPUF",
            "NPUZ",
            "NPUG",
            "NPUX",
        )

    fun extractInfo(
        fileName: String,
        inputStream: InputStream,
    ): DiskInfo {
        Timber.d("Extracting disk info for $fileName")
        inputStream.buffered(READ_BUFFER_SIZE).use {
            return when (FileUtils.extractExtension(fileName)) {
                "pbp" -> extractInfoForPBP(it)
                "iso", "bin" -> standardExtractInfo(it)
                "3ds" -> extractInfoFor3DS(it)
                else -> DiskInfo(null, null)
            }
        }
    }

    private fun standardExtractInfo(openedStream: InputStream): DiskInfo {
        openedStream.mark(READ_BUFFER_SIZE)
        val header = readByteArray(openedStream, ByteArray(READ_BUFFER_SIZE))

        val detectedSystem =
            MAGIC_NUMBERS
                .firstOrNull {
                    header.copyOfRange(it.offset, it.offset + it.numbers.size).contentEquals(it.numbers)
                }
                ?.systemID

        Timber.d("SystemID detected via magic numbers: $detectedSystem")

        openedStream.reset()

        return when (detectedSystem) {
            SystemID.SEGACD ->
                runCatching { extractInfoForSegaCD(openedStream) }
                    .getOrDefault(DiskInfo(null, SystemID.SEGACD))

            SystemID.PSX ->
                if (runCatching { isPS2Disc(header, openedStream) }.getOrDefault(false)) {
                    Timber.i("PlayStation 2 disc, not supported")
                    DiskInfo(null, null, unsupported = true)
                } else {
                    runCatching { extractInfoForPSX(openedStream) }
                        .getOrDefault(DiskInfo(null, SystemID.PSX))
                }

            SystemID.PSP ->
                runCatching { extractInfoForPSP(openedStream) }
                    .getOrDefault(DiskInfo(null, SystemID.PSP))

            else -> DiskInfo(null, null)
        }
    }

    private fun extractInfoFor3DS(openedStream: InputStream): DiskInfo {
        Timber.d("Parsing 3DS game")
        openedStream.mark(0x2000)
        openedStream.skip(0x1150)

        val rawSerial = String(readByteArray(openedStream, ByteArray(10)), Charsets.US_ASCII)

        openedStream.reset()

        Timber.d("Found 3DS serial: $rawSerial")
        return DiskInfo(rawSerial, SystemID.NINTENDO_3DS)
    }

    private fun extractInfoForSegaCD(openedStream: InputStream): DiskInfo {
        Timber.d("Parsing SegaCD game")
        openedStream.mark(20000)
        openedStream.skip(0x193)

        val rawSerial = String(readByteArray(openedStream, ByteArray(16)), Charsets.US_ASCII)

        Timber.d("Detected SegaCD raw serial read: $rawSerial")

        openedStream.reset()
        openedStream.skip(0x200)

        val regionID = String(readByteArray(openedStream, ByteArray(1)), Charsets.US_ASCII)

        Timber.d("Detected SegaCD region: $regionID")

        val groups = SEGA_CD_REGEX.find(rawSerial)?.groupValues

        // The following rules come from here: https://github.com/libretro/RetroArch/pull/11719/files
        // and some guess work. They are by no means complete.
        val prefix = groups?.get(1)
        val num = groups?.get(2)
        var postfix = groups?.get(3)

        if (regionID == "E") {
            postfix = "50"
        }

        if (postfix == "00") {
            postfix = null
        }

        val finalSerial =
            sequenceOf(prefix, num, postfix)
                .filterNotNull()
                .filter { it.isNotBlank() }
                .joinToString("-") { it.trim() }

        Timber.i("SegaCD final serial: $finalSerial")
        return DiskInfo(finalSerial, SystemID.SEGACD)
    }

    /**
     * PS2 discs say "PLAYSTATION" like PS1 ones, and use the same serials ("SLUS"). Their SYSTEM.CNF, found in the root
     * folder of the disc, starts the game with "BOOT2" instead of "BOOT". Images with 2048 or 2352 bytes sectors; the
     * stream is left at the start.
     */
    private fun isPS2Disc(
        header: ByteArray,
        openedStream: InputStream,
    ): Boolean {
        val (sectorSize, dataOffset) = if (header.size > 0x8013 && header.hasText(0x8008, "PLAYSTATION")) 2048 to 0 else 2352 to 24
        val volumeDescriptor = 16 * sectorSize + dataOffset
        val rootRecord = volumeDescriptor + ROOT_RECORD_OFFSET
        if (header.size < rootRecord + ROOT_RECORD_SIZE) return false
        val rootLba = header.readInt32(rootRecord + 2)

        openedStream.mark(PS2_CHECK_LIMIT)
        try {
            val rootFolder = readAt(openedStream, rootLba.toLong() * sectorSize + dataOffset, ISO_SECTOR_SIZE) ?: return false
            var position = 0
            while (position < rootFolder.size) {
                val length = rootFolder[position].toInt() and 0xFF
                if (length == 0 || position + length > rootFolder.size) break
                val nameLength = rootFolder[position + 32].toInt() and 0xFF
                val name = String(rootFolder, position + 33, nameLength.coerceAtMost(length - 33), Charsets.US_ASCII)
                if (name.uppercase().startsWith("SYSTEM.CNF")) {
                    val fileLba = rootFolder.readInt32(position + 2)
                    val content = readAt(openedStream, fileLba.toLong() * sectorSize + dataOffset, 64) ?: return false
                    return String(content, Charsets.US_ASCII).trimStart().startsWith("BOOT2")
                }
                position += length
            }
            return false
        } finally {
            openedStream.reset()
        }
    }

    /** [size] bytes at [offset] from the mark of the stream, or null when they are beyond [PS2_CHECK_LIMIT]. */
    private fun readAt(
        openedStream: InputStream,
        offset: Long,
        size: Int,
    ): ByteArray? {
        if (offset < 0 || offset + size > PS2_CHECK_LIMIT) return null
        openedStream.reset()
        var remaining = offset
        while (remaining > 0) {
            val skipped = openedStream.skip(remaining)
            if (skipped <= 0) return null
            remaining -= skipped
        }
        val bytes = readByteArray(openedStream, ByteArray(size))
        return bytes.takeIf { it.size == size }
    }

    private fun ByteArray.hasText(
        offset: Int,
        text: String,
    ): Boolean = String(this, offset, text.length, Charsets.US_ASCII) == text

    // Little endian, as in the ISO 9660 records.
    private fun ByteArray.readInt32(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)

    private fun extractInfoForPSX(openedStream: InputStream): DiskInfo {
        val headerSize = 64.kiloBytes()
        if (openedStream.available() < headerSize) {
            return DiskInfo(null, null)
        }

        return textSearch(PSX_BASE_SERIALS, openedStream, PS_SERIAL_MAX_SIZE, headerSize)
            .mapNotNull { serial -> parsePSXSerial(serial) }
            .mapNotNull { serial -> DiskInfo(serial, SystemID.PSX) }
            .firstOrNull() ?: DiskInfo(null, SystemID.PSX)
    }

    private fun extractInfoForPSP(openedStream: InputStream): DiskInfo {
        val headerSize = 64.kiloBytes()
        if (openedStream.available() < headerSize) {
            return DiskInfo(null, null)
        }

        return textSearch(PSP_BASE_SERIALS, openedStream, PS_SERIAL_MAX_SIZE, headerSize)
            .mapNotNull { serial -> parsePSXSerial(serial) }
            .mapNotNull { serial -> DiskInfo(serial, SystemID.PSP) }
            .firstOrNull() ?: DiskInfo(null, SystemID.PSP)
    }

    private fun extractInfoForPBP(openedStream: InputStream): DiskInfo {
        val headerSize = 2.megaBytes()
        if (openedStream.available() < headerSize) {
            return DiskInfo(null, null)
        }

        val queries = (PSP_BASE_SERIALS + PSX_BASE_SERIALS)

        return textSearch(queries, openedStream, PS_SERIAL_MAX_SIZE, headerSize)
            .mapNotNull { serial -> parsePSXSerial(serial) }
            .mapNotNull { serial ->
                when {
                    serial.startsWithAny(PSX_BASE_SERIALS) -> DiskInfo(serial, SystemID.PSX)
                    serial.startsWithAny(PSP_BASE_SERIALS) -> DiskInfo(serial, SystemID.PSP)
                    else -> DiskInfo(serial, null)
                }
            }
            .firstOrNull() ?: DiskInfo(null, null)
    }

    private fun parsePSXSerial(serial: String): String? {
        return sequenceOf(
            PS_SERIAL_REGEX.find(serial)?.groupValues?.let { "${it[1]}-${it[2]}" },
            PS_SERIAL_REGEX2.find(serial)?.groupValues?.let { "${it[1]}-${it[2]}${it[3]}" },
        ).filter { it != null }
            .firstOrNull()
    }

    private fun textSearch(
        queries: List<String>,
        openedStream: InputStream,
        resultSize: Int,
        streamSize: Int,
        windowSize: Int = 8.kiloBytes(),
        skipSize: Int = windowSize - resultSize,
        charset: Charset = Charsets.US_ASCII,
    ): Sequence<String> {
        val byteQueries = queries.map { it.toByteArray(charset) }
        return movingWidnowSequence(openedStream, windowSize, (skipSize).toLong())
            .take(ceil(streamSize.toDouble() / skipSize.toDouble()).roundToInt())
            .flatMap { serial ->
                byteQueries.asSequence()
                    .map { serial.indexOf(it) }
                    .filter { it >= 0 }
                    .map { serial to it }
            }
            .map { (bytes, index) ->
                val serialBytes = bytes.copyOfRange(index, index + resultSize)
                String(serialBytes, charset)
            }
            .filterNotNull()
    }

    private fun movingWidnowSequence(
        inputStream: InputStream,
        windowSize: Int,
        windowSkip: Long,
    ) = sequence {
        val buffer = ByteArray(windowSize)
        do {
            inputStream.mark(windowSize)
            yield(readByteArray(inputStream, buffer))
            inputStream.reset()
        } while (inputStream.skip(windowSkip) != 0L)
    }

    private fun readByteArray(
        inputStream: InputStream,
        byteArray: ByteArray,
    ): ByteArray {
        val readBytes = inputStream.read(byteArray)
        return if (readBytes < byteArray.size) {
            byteArray.copyOf(readBytes)
        } else {
            byteArray
        }
    }
}
