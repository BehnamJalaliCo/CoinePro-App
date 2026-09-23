@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.coinepro.web.jvm

/*
 * The cryptography the phone's code asks the JVM for, written out for a page that cannot wait on
 * WebCrypto's promises from a synchronous call: SHA-256, HMAC-SHA256 and AES-GCM, each to its
 * FIPS/NIST specification, with randomness from `crypto.getRandomValues`.
 */

private fun randomByteJs(): Int = js("(function () { var a = new Uint8Array(1); crypto.getRandomValues(a); return a[0]; })()")

fun secureRandomBytes(count: Int): ByteArray = ByteArray(count) { randomByteJs().toByte() }

object Sha256 {
    private val K = intArrayOf(
        0x428a2f98, 0x71374491, -0x4a3f0431, -0x164a245b, 0x3956c25b, 0x59f111f1, -0x6dc07d5c, -0x54e3a12b,
        -0x27f85568, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, -0x7f214e02, -0x6423f959, -0x3e640e8c,
        -0x1b64963f, -0x1041b87a, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        -0x67c1aeae, -0x57ce3993, -0x4ffcd838, -0x40a68039, -0x391ff40d, -0x2a586eb9, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, -0x7e3d36d2, -0x6d8dd37b,
        -0x5d40175f, -0x57e599b5, -0x3db47490, -0x3893ae5d, -0x2e6d17e7, -0x2966f9dc, -0xbf1ca7b, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, -0x7b3787ec, -0x7338fdf8, -0x6f410006, -0x5baf9315, -0x41065c09, -0x398e870e,
    )

    fun digest(input: ByteArray): ByteArray {
        val h = intArrayOf(0x6a09e667, -0x4498517b, 0x3c6ef372, -0x5ab00ac6, 0x510e527f, -0x64fa9774, 0x1f83d9ab, 0x5be0cd19)
        val bitLength = input.size.toLong() * 8
        val padded = ByteArray(((input.size + 9 + 63) / 64) * 64)
        input.copyInto(padded)
        padded[input.size] = 0x80.toByte()
        for (i in 0 until 8) padded[padded.size - 1 - i] = (bitLength ushr (8 * i)).toByte()
        val w = IntArray(64)
        for (chunk in padded.indices step 64) {
            for (i in 0 until 16) {
                val o = chunk + i * 4
                w[i] = (padded[o].toInt() and 0xff shl 24) or (padded[o + 1].toInt() and 0xff shl 16) or
                    (padded[o + 2].toInt() and 0xff shl 8) or (padded[o + 3].toInt() and 0xff)
            }
            for (i in 16 until 64) {
                val s0 = w[i - 15].rotateRight(7) xor w[i - 15].rotateRight(18) xor (w[i - 15] ushr 3)
                val s1 = w[i - 2].rotateRight(17) xor w[i - 2].rotateRight(19) xor (w[i - 2] ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }
            var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
            var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
            for (i in 0 until 64) {
                val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = hh + s1 + ch + K[i] + w[i]
                val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val t2 = s0 + maj
                hh = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
            }
            h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e; h[5] += f; h[6] += g; h[7] += hh
        }
        val out = ByteArray(32)
        for (i in 0 until 8) for (j in 0 until 4) out[i * 4 + j] = (h[i] ushr (24 - 8 * j)).toByte()
        return out
    }
}

fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray {
    val block = 64
    val k = (if (key.size > block) Sha256.digest(key) else key).copyOf(block)
    val inner = ByteArray(block) { (k[it].toInt() xor 0x36).toByte() }
    val outer = ByteArray(block) { (k[it].toInt() xor 0x5c).toByte() }
    return Sha256.digest(outer + Sha256.digest(inner + message))
}

/** AES (FIPS-197), encryption direction only — all that counter mode, and so GCM, needs. */
class Aes(key: ByteArray) {
    private val rounds = when (key.size) { 16 -> 10; 24 -> 12; 32 -> 14; else -> throw IllegalArgumentException("Invalid AES key length: ${key.size}") }
    private val w = expand(key)

    fun encryptBlock(input: ByteArray): ByteArray {
        val s = IntArray(16) { input[it].toInt() and 0xff }
        addRoundKey(s, 0)
        for (round in 1 until rounds) {
            subBytes(s); shiftRows(s); mixColumns(s); addRoundKey(s, round)
        }
        subBytes(s); shiftRows(s); addRoundKey(s, rounds)
        return ByteArray(16) { s[it].toByte() }
    }

    private fun addRoundKey(s: IntArray, round: Int) {
        for (c in 0 until 4) {
            val word = w[round * 4 + c]
            for (r in 0 until 4) s[c * 4 + r] = s[c * 4 + r] xor ((word ushr (24 - 8 * r)) and 0xff)
        }
    }

    private fun subBytes(s: IntArray) { for (i in 0 until 16) s[i] = SBOX[s[i]] }

    private fun shiftRows(s: IntArray) {
        val t = s.copyOf()
        for (c in 0 until 4) for (r in 0 until 4) s[c * 4 + r] = t[((c + r) % 4) * 4 + r]
    }

    private fun mixColumns(s: IntArray) {
        for (c in 0 until 4) {
            val a0 = s[c * 4]; val a1 = s[c * 4 + 1]; val a2 = s[c * 4 + 2]; val a3 = s[c * 4 + 3]
            s[c * 4] = xt(a0) xor (xt(a1) xor a1) xor a2 xor a3
            s[c * 4 + 1] = a0 xor xt(a1) xor (xt(a2) xor a2) xor a3
            s[c * 4 + 2] = a0 xor a1 xor xt(a2) xor (xt(a3) xor a3)
            s[c * 4 + 3] = (xt(a0) xor a0) xor a1 xor a2 xor xt(a3)
        }
    }

    private fun xt(b: Int): Int = ((b shl 1) xor (if (b and 0x80 != 0) 0x1b else 0)) and 0xff

    private fun expand(key: ByteArray): IntArray {
        val nk = key.size / 4
        val out = IntArray(4 * (rounds + 1))
        for (i in 0 until nk) {
            out[i] = (key[4 * i].toInt() and 0xff shl 24) or (key[4 * i + 1].toInt() and 0xff shl 16) or
                (key[4 * i + 2].toInt() and 0xff shl 8) or (key[4 * i + 3].toInt() and 0xff)
        }
        var rcon = 1
        for (i in nk until out.size) {
            var t = out[i - 1]
            if (i % nk == 0) {
                t = sub((t shl 8) or (t ushr 24)) xor (rcon shl 24)
                rcon = xt(rcon)
            } else if (nk > 6 && i % nk == 4) {
                t = sub(t)
            }
            out[i] = out[i - nk] xor t
        }
        return out
    }

    private fun sub(word: Int): Int =
        (SBOX[(word ushr 24) and 0xff] shl 24) or (SBOX[(word ushr 16) and 0xff] shl 16) or
            (SBOX[(word ushr 8) and 0xff] shl 8) or SBOX[word and 0xff]

    companion object {
        private val SBOX: IntArray = run {
            val sbox = IntArray(256)
            var p = 1
            var q = 1
            do {
                p = p xor ((p shl 1) and 0xff) xor (if (p and 0x80 != 0) 0x1b else 0)
                q = q xor (q shl 1); q = q xor (q shl 2); q = q xor (q shl 4); q = q and 0xff
                if (q and 0x80 != 0) q = q xor 0x09
                val x = q xor (q.rotl8(1)) xor (q.rotl8(2)) xor (q.rotl8(3)) xor (q.rotl8(4))
                sbox[p] = (x xor 0x63) and 0xff
            } while (p != 1)
            sbox[0] = 0x63
            sbox
        }
        private fun Int.rotl8(n: Int): Int = ((this shl n) or (this ushr (8 - n))) and 0xff
    }
}

/** AES-GCM (NIST SP 800-38D) with a 96-bit IV and a 128-bit tag — the phone's `AES/GCM/NoPadding`. */
object AesGcm {
    fun encrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray, tagBits: Int = 128): ByteArray {
        val aes = Aes(key)
        val h = aes.encryptBlock(ByteArray(16))
        val j0 = j0(aes, h, iv)
        val ct = ctr(aes, inc32(j0), plaintext)
        val tag = xor(aes.encryptBlock(j0), ghash(h, ByteArray(0), ct)).copyOf(tagBits / 8)
        return ct + tag
    }

    fun decrypt(key: ByteArray, iv: ByteArray, input: ByteArray, tagBits: Int = 128): ByteArray {
        val tagBytes = tagBits / 8
        if (input.size < tagBytes) throw javax.crypto.AEADBadTagException("Input too short")
        val aes = Aes(key)
        val h = aes.encryptBlock(ByteArray(16))
        val j0 = j0(aes, h, iv)
        val ct = input.copyOf(input.size - tagBytes)
        val tag = input.copyOfRange(input.size - tagBytes, input.size)
        val expected = xor(aes.encryptBlock(j0), ghash(h, ByteArray(0), ct)).copyOf(tagBytes)
        var diff = 0
        for (i in 0 until tagBytes) diff = diff or (expected[i].toInt() xor tag[i].toInt())
        if (diff != 0) throw javax.crypto.AEADBadTagException("Tag mismatch!")
        return ctr(aes, inc32(j0), ct)
    }

    private fun j0(aes: Aes, h: ByteArray, iv: ByteArray): ByteArray =
        if (iv.size == 12) iv + byteArrayOf(0, 0, 0, 1) else ghash(h, ByteArray(0), iv)

    private fun inc32(block: ByteArray): ByteArray {
        val out = block.copyOf()
        for (i in 15 downTo 12) { out[i] = (out[i] + 1).toByte(); if (out[i].toInt() != 0) break }
        return out
    }

    private fun ctr(aes: Aes, start: ByteArray, input: ByteArray): ByteArray {
        val out = ByteArray(input.size)
        var counter = start
        var offset = 0
        while (offset < input.size) {
            val stream = aes.encryptBlock(counter)
            for (i in 0 until minOf(16, input.size - offset)) out[offset + i] = (input[offset + i].toInt() xor stream[i].toInt()).toByte()
            offset += 16
            counter = inc32(counter)
        }
        return out
    }

    private fun xor(a: ByteArray, b: ByteArray): ByteArray = ByteArray(a.size) { (a[it].toInt() xor b[it].toInt()).toByte() }

    private fun ghash(h: ByteArray, aad: ByteArray, c: ByteArray): ByteArray {
        var y = ByteArray(16)
        fun absorb(data: ByteArray) {
            var offset = 0
            while (offset < data.size) {
                val block = data.copyOfRange(offset, minOf(offset + 16, data.size)).copyOf(16)
                y = mul(xor(y, block), h)
                offset += 16
            }
        }
        absorb(aad); absorb(c)
        val lengths = ByteArray(16)
        val aBits = aad.size.toLong() * 8
        val cBits = c.size.toLong() * 8
        for (i in 0 until 8) { lengths[7 - i] = (aBits ushr (8 * i)).toByte(); lengths[15 - i] = (cBits ushr (8 * i)).toByte() }
        return mul(xor(y, lengths), h)
    }

    private fun mul(x: ByteArray, y: ByteArray): ByteArray {
        val z = ByteArray(16)
        val v = y.copyOf()
        for (i in 0 until 128) {
            if ((x[i / 8].toInt() shr (7 - i % 8)) and 1 == 1) for (k in 0 until 16) z[k] = (z[k].toInt() xor v[k].toInt()).toByte()
            val lsb = v[15].toInt() and 1
            for (k in 15 downTo 1) v[k] = (((v[k].toInt() and 0xff) ushr 1) or ((v[k - 1].toInt() and 1) shl 7)).toByte()
            v[0] = ((v[0].toInt() and 0xff) ushr 1).toByte()
            if (lsb == 1) v[0] = (v[0].toInt() xor 0xe1).toByte()
        }
        return z
    }
}
