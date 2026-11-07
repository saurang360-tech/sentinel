package dev.titus.sentinel.monitor

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.math.BigInteger
import java.security.MessageDigest
import java.security.interfaces.ECPublicKey
import java.util.Base64

/** SSH wire formats only. No private-key material is exported or encoded here. */
object SshEncoding {
    const val ALGORITHM = "ecdsa-sha2-nistp256"
    fun strings(vararg values: ByteArray): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out -> values.forEach { out.writeInt(it.size); out.write(it) } }
        return bytes.toByteArray()
    }
    fun publicBlob(key: ECPublicKey): ByteArray {
        fun coordinate(n: BigInteger): ByteArray {
            val raw = n.toByteArray().let { if (it.size == 33 && it[0] == 0.toByte()) it.copyOfRange(1,33) else it }
            require(raw.size <= 32)
            return ByteArray(32 - raw.size) + raw
        }
        return strings(ALGORITHM.toByteArray(), "nistp256".toByteArray(),
            byteArrayOf(4) + coordinate(key.w.affineX) + coordinate(key.w.affineY))
    }
    fun signatureFromDer(der: ByteArray): ByteArray {
        var i = 0
        fun take(): Int { require(i < der.size); return der[i++].toInt() and 255 }
        require(take() == 0x30)
        // P-256 signatures are at most 72 bytes: the DER length is always short form.
        val size = take(); require(size < 128 && size == der.size - i)
        fun integer(): ByteArray {
            require(take() == 2)
            val length = take(); require(length in 1..33 && i + length <= der.size)
            val value = der.copyOfRange(i, i + length); i += length
            require(value[0].toInt() and 0x80 == 0)
            val n = BigInteger(value); require(n.signum() > 0 && n.bitLength() <= 256)
            return n.toByteArray()
        }
        val r = integer(); val s = integer(); require(i == der.size)
        return strings(ALGORITHM.toByteArray(), strings(r,s))
    }
    fun fingerprint(blob: ByteArray): String = "SHA256:" +
        Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(blob))
    fun matchesPin(blob: ByteArray, expected: String): Boolean =
        MessageDigest.isEqual(fingerprint(blob).toByteArray(), expected.toByteArray())
}
