import kotlin.math.min

private val base64 = charArrayOf(
    'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
    'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
    'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
    'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/'
)
internal fun b64Encode(src: ByteArray, off: Int, end: Int, dst: ByteArray): Int {
    var sp = off
    val slen = (end - off) / 3 * 3
    val sl = off + slen
    var dp = 0
    while (sp < sl) {
        val sl0 = min(sp + slen, sl)

        var sp0 = sp
        var dp0 = dp
        while (sp0 < sl0) {
            val bits: Int = src[sp0++].toInt() and 0xff shl 16 or (
                    src[sp0++].toInt() and 0xff shl 8) or
                    (src[sp0++].toInt() and 0xff)
            dst[dp0++] = base64[bits ushr 18 and 0x3f].code.toByte()
            dst[dp0++] = base64[bits ushr 12 and 0x3f].code.toByte()
            dst[dp0++] = base64[bits ushr 6 and 0x3f].code.toByte()
            dst[dp0++] = base64[bits and 0x3f].code.toByte()
        }

        val dlen = (sl0 - sp) / 3 * 4
        dp += dlen
        sp = sl0
    }
    if (sp < end) {               // 1 or 2 leftover bytes
        val b0: Int = src[sp++].toInt() and 0xff
        dst[dp++] = base64[b0 shr 2].code.toByte()
        if (sp == end) {
            dst[dp++] = base64[b0 shl 4 and 0x3f].code.toByte()
        } else {
            val b1: Int = src[sp].toInt() and 0xff
            dst[dp++] = base64[b0 shl 4 and 0x3f or (b1 shr 4)].code.toByte()
            dst[dp++] = base64[b1 shl 2 and 0x3f].code.toByte()
        }
    }
    return dp
}

internal fun b64OutLen(srclen: Int, throwOOME: Boolean) =
    try {
        val n = srclen % 3
        Math.addExact(Math.multiplyExact(4, srclen / 3), if (n == 0) 0 else n + 1)
    } catch (ex: ArithmeticException) {
        if (throwOOME) throw OutOfMemoryError("Encoded size is too large") else -1
    }