package dev.yuki.terminaldial

import java.io.InputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class BridgeInputStream : InputStream() {
    private val queue = LinkedBlockingQueue<ByteArray>(256)
    private var current: ByteArray? = null
    private var offset = 0
    @Volatile private var closed = false

    fun offer(data: ByteArray) {
        if (data.isEmpty() || closed) return
        try {
            queue.put(data)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    override fun read(): Int {
        while (true) {
            val cur = current
            if (cur != null && offset < cur.size) {
                val b = cur[offset].toInt() and 0xFF
                offset++
                if (offset >= cur.size) {
                    current = null
                    offset = 0
                }
                return b
            }
            if (closed) return -1
            val next = try {
                queue.poll(200, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return -1
            }
            if (next == null) {
                if (closed) return -1
                continue
            }
            current = next
            offset = 0
        }
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        val first = read()
        if (first == -1) return -1
        b[off] = first.toByte()
        var count = 1
        while (count < len) {
            val cur = current
            if (cur == null || offset >= cur.size) break
            val avail = minOf(len - count, cur.size - offset)
            System.arraycopy(cur, offset, b, off + count, avail)
            offset += avail
            count += avail
            if (offset >= cur.size) {
                current = null
                offset = 0
            }
        }
        return count
    }

    override fun close() {
        closed = true
        queue.clear()
        current = null
    }
}