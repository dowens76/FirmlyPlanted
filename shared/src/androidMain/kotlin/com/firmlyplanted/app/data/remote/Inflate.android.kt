package com.firmlyplanted.app.data.remote

import java.util.zip.Inflater

internal actual fun inflateRaw(data: ByteArray, uncompressedSize: Int): ByteArray {
    val inflater = Inflater(true)
    try {
        inflater.setInput(data)
        val out = ByteArray(uncompressedSize)
        var written = 0
        while (written < uncompressedSize && !inflater.finished()) {
            val n = inflater.inflate(out, written, uncompressedSize - written)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            written += n
        }
        require(written == uncompressedSize) { "Zip entry inflated to $written of $uncompressedSize bytes" }
        return out
    } finally {
        inflater.end()
    }
}
