import java.nio.ByteBuffer
import kotlin.math.min
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0
import kotlin.reflect.KProperty1

fun ByteBuffer.writePackedRange(value: Double, min: Double, max: Double) {
    packedULong = ((value - min)/max).toULong()
}

fun ByteBuffer.writePackedRange(value: Float, min: Float, max: Float) {
    packedULong = ((value - min)/max).toULong()
}

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

fun ByteBuffer.readPackedRangeDouble(min: Double, max: Double) = (packedLong * max) + min
fun ByteBuffer.readPackedRangeFloat(min: Float, max: Float) = (packedLong * max) + min


class ReallocatingBuffer(buffer: ByteBuffer, val growthFactor: Float = 1.0f) {
    // Handles reads/writes
    private abstract inner class ReallocatingAccessor<T>(
        private val getter: () -> T,
        private val setter: (T) -> Unit
    ) {
        protected abstract fun sizeOf(value: T): Int

        constructor(property: KMutableProperty0<T>): this(property::get, property::set)

        operator fun getValue(thisRef: Any?, property: KProperty<*>) = getter()
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            ensureSize(size)
            setter(value)
        }
    }

    private inner class StaticReallocatingAccessor<T>(
        getter: () -> T,
        setter: (T) -> Unit,
        private val size: Int
    ): ReallocatingAccessor<T>(getter, setter) {
        constructor(property: KMutableProperty0<T>, size: Int): this(property::get, property::set, size)

        override fun sizeOf(value: T) = size
    }

    private inner class VarIntReallocatingAccessor<T>(
        getter: () -> T,
        setter: (T) -> Unit,
        private val sizeGetter: T.() -> Int
    ): ReallocatingAccessor<T>(getter, setter) {
        constructor(getter: () -> T, setter: (T) -> Unit, sizeGetter: KProperty1<T, Int>): this(getter, setter, sizeGetter::get)
        constructor(property: KMutableProperty0<T>, sizeGetter: T.() -> Int): this(property::get, property::set, sizeGetter)
        constructor(property: KMutableProperty0<T>, sizeGetter: KProperty1<T, Int>): this(property::get, property::set, sizeGetter::get)

        override fun sizeOf(value: T) = sizeGetter(value)
    }

    var buffer = buffer
        private set

    var byte: Byte by StaticReallocatingAccessor(buffer::get, buffer::put, 1)
    var char: Char by StaticReallocatingAccessor(buffer::getChar, buffer::putChar, 2)
    var short: Short by StaticReallocatingAccessor(buffer::getShort, buffer::putShort, 2)
    var int: Int by StaticReallocatingAccessor(buffer::getInt, buffer::putInt, 4)
    var long: Long by StaticReallocatingAccessor(buffer::getLong, buffer::putLong, 8)
    var uShort: UShort by StaticReallocatingAccessor({ buffer.short.toUShort() }, { buffer.putShort(it.toShort()) }, 2)
    var uInt: UInt by StaticReallocatingAccessor({ buffer.int.toUInt() }, { buffer.putInt(it.toInt()) }, 4)
    var uLong: ULong by StaticReallocatingAccessor({ buffer.long.toULong() }, { buffer.putLong(it.toLong()) }, 8)
    var float: Float by StaticReallocatingAccessor(buffer::getFloat, buffer::putFloat, 4)
    var double: Double by StaticReallocatingAccessor(buffer::getDouble, buffer::putDouble, 4)

    var packedChar: Char by VarIntReallocatingAccessor(buffer::packedChar, Char::varIntSize)
    var packedShort: Short by VarIntReallocatingAccessor(buffer::packedShort, Short::varIntSize)
    var packedInt: Int by VarIntReallocatingAccessor(buffer::packedInt, Int::varIntSize)
    var packedLong: Long by VarIntReallocatingAccessor(buffer::packedLong, Long::varIntSize)
    var packedUShort: UShort by VarIntReallocatingAccessor(buffer::packedUShort, UShort::varIntSize)
    var packedUInt: UInt by VarIntReallocatingAccessor(buffer::packedUInt, UInt::varIntSize)
    var packedULong: ULong by VarIntReallocatingAccessor(buffer::packedULong, ULong::varIntSize)

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
                newBuffer.put(buffer)
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

    fun getPackedFloat(min: Float, max: Float) = buffer.readPackedRangeFloat(min, max)
    fun getPackedDouble(min: Double, max: Double) = buffer.readPackedRangeDouble(min, max)

    fun ensureAtLeast(minSize: Int) {
        if (minSize > size)
            size = minSize
    }
}