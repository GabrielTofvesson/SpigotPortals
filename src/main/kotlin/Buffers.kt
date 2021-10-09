import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.nio.ByteBuffer
import java.nio.charset.Charset
import kotlin.math.min
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty

private val threadLocalBuffer = ThreadLocal.withInitial { ByteBuffer.allocate(9) }

internal val PRECISION_1024 = MathContext(1024)

fun ByteBuffer.writePackedRange(_value: BigDecimal, min: BigDecimal, max: BigDecimal) {
    val actualMax = max - min
    packedULong = _value
        .subtract(min)
        .coerceIn(min .. actualMax)
        .multiply(ULONG_MAX_FLOAT.divide(actualMax, PRECISION_1024))
        .toBigInteger()
        .toULong()
}

fun ByteBuffer.writePackedRange(_value: Double, min: Double, max: Double) = writePackedRange(BigDecimal(_value), BigDecimal(min), BigDecimal(max))
fun ByteBuffer.writePackedRange(value: Float, min: Float, max: Float) = writePackedRange(value.toDouble(), min.toDouble(), max.toDouble())

var ByteBuffer.packedULong: ULong
    get() = readPacked().first
    set(value) { value.toPacked(this) }

var ByteBuffer.packedUInt: UInt
    get() = packedULong.toUInt()
    set(value) { packedULong = value.toULong() }

var ByteBuffer.packedUShort: UShort
    get() = packedULong.toUShort()
    set(value) { packedULong = value.toULong() }

var ByteBuffer.packedLong: Long
    get() = readPacked().first.deInterlace()
    set(value) { value.interlace().toPacked(this) }

var ByteBuffer.packedInt: Int
    get() = packedLong.toInt()
    set(value) { packedLong = value.toLong() }

var ByteBuffer.packedShort: Short
    get() = packedLong.toShort()
    set(value) { packedLong = value.toLong() }

var ByteBuffer.packedChar: Char
    get() = packedInt.toChar()
    set(value) { packedInt = value.code }

fun ByteBuffer.readPackedRangeDouble(min: Double, max: Double): Double {
    val buffer = threadLocalBuffer.get()
    return ((BigInteger(buffer.position(0).put(0).putLong(packedULong.toLong()).array(), 0, 9).toBigDecimal(mathContext = MathContext.UNLIMITED) *
            (BigDecimal(max).divide(BigInteger(buffer.position(1).putLong(-1L).array(), 0, 9).toBigDecimal(mathContext = MathContext.UNLIMITED), PRECISION_1024))) + BigDecimal(min)).toDouble()
}

fun ByteBuffer.readPackedRangeFloat(min: Float, max: Float) = readPackedRangeDouble(min.toDouble(), max.toDouble())


class ReallocatingBuffer(buffer: ByteBuffer, val growthFactor: Float = 1.0f) {
    // Handles reads/writes
    private abstract inner class ReallocatingAccessor<T>(
        private val getter: () -> () -> T,
        private val setter: () -> (T) -> Unit
    ) {
        protected abstract fun sizeOf(value: T): Int

        constructor(property: () -> KMutableProperty0<T>): this({ property()::get }, { property()::set })

        operator fun getValue(thisRef: Any?, property: KProperty<*>) = getter()()
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            ensureSize(sizeOf(value))
            setter()(value)
        }
    }

    private inner class StaticReallocatingAccessor<T>(
        getter: () -> T,
        setter: (T) -> Unit,
        private val size: Int
    ): ReallocatingAccessor<T>({ getter }, { setter }) {
        override fun sizeOf(value: T) = size
    }

    private inner class VarIntReallocatingAccessor<T>(
        getter: () -> () -> T,
        setter: () -> (T) -> Unit,
        private val sizeGetter: T.() -> Int
    ): ReallocatingAccessor<T>(getter, setter) {
        constructor(property: () -> KMutableProperty0<T>, sizeGetter: T.() -> Int): this({ property()::get }, { property()::set }, sizeGetter)

        override fun sizeOf(value: T) = sizeGetter(value)
    }

    var buffer = buffer
        private set

    var byte: Byte by StaticReallocatingAccessor({ this.buffer.get() }, { this.buffer.put(it) }, 1)
    var char: Char by StaticReallocatingAccessor({ this.buffer.char }, { this.buffer.putChar(it) }, 2)
    var short: Short by StaticReallocatingAccessor({ this.buffer.short }, { this.buffer.putShort(it) }, 2)
    var int: Int by StaticReallocatingAccessor({ this.buffer.int }, { this.buffer.putInt(it) }, 4)
    var long: Long by StaticReallocatingAccessor({ this.buffer.long }, { this.buffer.putLong(it) }, 8)
    var uShort: UShort by StaticReallocatingAccessor({ this.buffer.short.toUShort() }, { this.buffer.putShort(it.toShort()) }, 2)
    var uInt: UInt by StaticReallocatingAccessor({ this.buffer.int.toUInt() }, { this.buffer.putInt(it.toInt()) }, 4)
    var uLong: ULong by StaticReallocatingAccessor({ this.buffer.long.toULong() }, { this.buffer.putLong(it.toLong()) }, 8)
    var float: Float by StaticReallocatingAccessor({ this.buffer.float }, { this.buffer.putFloat(it) }, 4)
    var double: Double by StaticReallocatingAccessor({ this.buffer.double }, { this.buffer.putDouble(it) }, 8)

    var packedChar: Char by VarIntReallocatingAccessor({ this.buffer::packedChar }, Char::varIntSize)
    var packedShort: Short by VarIntReallocatingAccessor({ this.buffer::packedShort }, Short::varIntSize)
    var packedInt: Int by VarIntReallocatingAccessor({ this.buffer::packedInt }, Int::varIntSize)
    var packedLong: Long by VarIntReallocatingAccessor({ this.buffer::packedLong }, Long::varIntSize)
    var packedUShort: UShort by VarIntReallocatingAccessor({ this.buffer::packedUShort }, UShort::varIntSize)
    var packedUInt: UInt by VarIntReallocatingAccessor({ this.buffer::packedUInt }, UInt::varIntSize)
    var packedULong: ULong by VarIntReallocatingAccessor({ this.buffer::packedULong }, ULong::varIntSize)

    var position: Int
        get() = buffer.position()
        set(value) { buffer.position(value) }

    var size: Int
        get() = buffer.capacity()
        set(value) {
            if (buffer.capacity() != value) {
                val oldPosition = position
                val newBuffer = if(buffer.isDirect) ByteBuffer.allocateDirect(value) else ByteBuffer.allocate(value)

                position = 0
                newBuffer.put(0, buffer, 0, min(oldPosition, value))
                position = min(oldPosition, value)

                buffer = newBuffer
            }
        }

    private fun ensureSize(newBytes: Int) {
        if (position + newBytes > size)
            size = (size * (growthFactor + 1f)).toInt()
    }


    fun putPackedFloat(value: Float, min: Float, max: Float) {
        ensureSize(value.varIntSize(min, max))
        buffer.writePackedRange(value, min, max)
    }

    fun putPackedDouble(value: Double, min: Double, max: Double) {
        ensureSize(value.varIntSize(min, max))
        buffer.writePackedRange(value, min, max)
    }

    fun getPackedFloat(min: Float, max: Float) = buffer.readPackedRangeFloat(min, max).toFloat()
    fun getPackedDouble(min: Double, max: Double) = buffer.readPackedRangeDouble(min, max)

    fun putByteArrayDirect(array: ByteArray, off: Int = 0, len: Int = array.size) {
        ensureSize(len)
        buffer.put(array, off, len)
    }

    fun putByteArray(array: ByteArray, off: Int = 0, len: Int = array.size) {
        ensureSize(len.toUInt().varIntSize)
        packedUInt = len.toUInt()
        putByteArrayDirect(array, off, len)
    }
    fun putString(string: String, charset: Charset = Charsets.UTF_8) = putByteArray(string.toByteArray(charset))

    fun getByteArrayDirect(dst: ByteArray, off: Int = 0, len: Int = dst.size) {
        buffer.get(dst, off, len)
    }

    fun getByteArrayDirect(len: Int): ByteArray {
        val dst = ByteArray(len)
        getByteArrayDirect(dst, 0, len)
        return dst
    }

    fun getByteArray(dst: ByteArray) {
        val len = packedUInt
        if (len > dst.size.toUInt())
            throw IndexOutOfBoundsException("Attempt to write past end of array")

        getByteArrayDirect(dst, 0, len.toInt())
    }

    fun getByteArray(): ByteArray {
        val dst = ByteArray(packedUInt.toInt())
        getByteArrayDirect(dst, 0, dst.size)
        return dst
    }

    // TODO: Optimize allocations
    fun getString(charset: Charset = Charsets.UTF_8) = String(getByteArray(), charset)

    fun ensureAtLeast(minSize: Int) {
        if (minSize > size)
            size = minSize
    }
}