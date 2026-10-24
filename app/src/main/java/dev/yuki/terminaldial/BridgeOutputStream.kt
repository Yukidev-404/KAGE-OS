package dev.yuki.terminaldial

import java.io.OutputStream

class BridgeOutputStream(private val onInput: (String) -> Unit) : OutputStream() {
    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()), 0, 1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (len <= 0) return
        val chunk = b.copyOfRange(off, off + len)
        val text = String(chunk, Charsets.UTF_8)
        onInput(text)
    }

    override fun flush() = Unit
}