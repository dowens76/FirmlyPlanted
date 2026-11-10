package com.firmlyplanted.app.data.remote

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.zlib.ZLIB_VERSION
import platform.zlib.Z_FINISH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream

@OptIn(ExperimentalForeignApi::class)
internal actual fun inflateRaw(data: ByteArray, uncompressedSize: Int): ByteArray {
    val out = ByteArray(uncompressedSize)
    if (uncompressedSize == 0) return out
    // Kotlin/Native rejects pinning an empty array's address, so give zlib at least one byte.
    val input = if (data.isEmpty()) ByteArray(1) else data
    memScoped {
        val stream = alloc<z_stream>()
        // Negative window bits = raw DEFLATE (zip entries have no zlib header).
        val init = inflateInit2_(stream.ptr, -15, ZLIB_VERSION, sizeOf<z_stream>().toInt())
        require(init == Z_OK) { "zlib inflateInit2 failed ($init)" }
        try {
            input.usePinned { inPinned ->
                out.usePinned { outPinned ->
                    stream.next_in = inPinned.addressOf(0).reinterpret<UByteVar>()
                    stream.avail_in = data.size.toUInt()
                    stream.next_out = outPinned.addressOf(0).reinterpret<UByteVar>()
                    stream.avail_out = uncompressedSize.toUInt()
                    val result = inflate(stream.ptr, Z_FINISH)
                    require(result == Z_STREAM_END || (result == Z_OK && stream.avail_out == 0u)) {
                        "zlib inflate failed ($result)"
                    }
                    require(stream.total_out.toInt() == uncompressedSize) {
                        "Zip entry inflated to ${stream.total_out} of $uncompressedSize bytes"
                    }
                }
            }
        } finally {
            inflateEnd(stream.ptr)
        }
    }
    return out
}
