import org.bukkit.Location
import org.bukkit.OfflinePlayer
import org.bukkit.World
import org.bukkit.entity.Player
import java.nio.ByteBuffer
import java.util.*
import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or


private val threadLocalInputBuffer = ThreadLocal.withInitial { ReallocatingBuffer(ByteBuffer.allocate(96)) }
private val threadLocalOutputBuffer = ThreadLocal.withInitial { ReallocatingBuffer(ByteBuffer.allocate(96)) }

sealed class PortalResult {
    object NO_LINK: PortalResult()
    object DISALLOWED: PortalResult()
    data class SUCCESS(val link: Portal): PortalResult()
}

enum class PortalFlag {
    PUBLIC, LINKED, NO_EXCLUSIONS;

    private val flagBit: Byte
        get() = (1 shl ordinal).toByte()

    fun isFlagSet(flagMap: Byte) = flagMap and flagBit != 0.toByte()
    fun setFlag(flagMap: Byte) = flagMap or flagBit
    fun unSetFlag(flagMap: Byte) = flagMap and flagBit.inv()

    fun setFlagValue(flagMap: Byte, set: Boolean) =
        if (set) setFlag(flagMap)
        else unSetFlag(flagMap)
}

fun applyFlags(flags: Byte, flagMap: Map<PortalFlag, Boolean>): Byte {
    var result = flags
    for ((flag, value) in flagMap)
        result = flag.setFlagValue(result, value)
    return result
}

fun flagMapOf(vararg flags: PortalFlag): Byte {
    var result: Byte = 0

    for (flag in flags)
        result = flag.setFlag(result)

    return result
}

fun readFlags(flagMap: Byte): List<PortalFlag> {
    val list = ArrayList<PortalFlag>()

    for (flag in PortalFlag.values())
        if (flag.isFlagSet(flagMap))
            list += flag

    return list
}


class Portal private constructor(
    val id: UUID,
    val ownerIndex: UInt,
    val worldIndex: UInt,
    val x: Int,
    val y: Int,
    val z: Int,
    var yaw: Float,
    var pitch: Float,
    private var flags: Byte,
    link: UUID?,
    var name: String,
    private val _accessExclusions: SortedList<UInt>,
    private val toPlayerMapper: MapFunction<UInt, OfflinePlayer?>,
    private val fromPlayerMapper: MapFunction<OfflinePlayer, UInt>,
    private val toWorldMapper: MapFunction<UInt, World?>,
    private val fromWorldMapper: MapFunction<World, UInt>
) {
    constructor(
        toPlayerMapper: MapFunction<UInt, OfflinePlayer?>,
        fromPlayerMapper: MapFunction<OfflinePlayer, UInt>,
        toWorldMapper: MapFunction<UInt, World?>,
        fromWorldMapper: MapFunction<World, UInt>,
        id: UUID,
        owner: OfflinePlayer,
        world: World,
        x: Int,
        y: Int,
        z: Int,
        yaw: Float,
        pitch: Float,
        name: String,
        link: Portal? = null
    ): this(
        id, fromPlayerMapper(owner),
        fromWorldMapper(world), x, y, z, yaw, pitch,
        0, link?.id, name, SortedList(comparator = UInt::compareTo),
        toPlayerMapper, fromPlayerMapper, toWorldMapper, fromWorldMapper
    )

    init {
        flags = applyFlags(
            flags,
            mapOf(
                PortalFlag.NO_EXCLUSIONS to _accessExclusions.isEmpty(),
                PortalFlag.LINKED to (link != null)
            )
        )
    }

    var public: Boolean
        get() = PortalFlag.PUBLIC.isFlagSet(flags)
        set(value) {
            _accessExclusions.clear()
            flags = PortalFlag.PUBLIC.setFlagValue(flags, value)
        }

    var link = link
        private set(value) {
            flags = PortalFlag.LINKED.setFlagValue(flags, value != null)
            field = value
        }

    fun getAccessExclusionsSize() = _accessExclusions.size
    fun getAccessExclusion(index: Int) = toPlayerMapper(_accessExclusions[index])
    fun addAccessExclusion(player: OfflinePlayer) {
        _accessExclusions.add(fromPlayerMapper(player))
        flags = PortalFlag.NO_EXCLUSIONS.unSetFlag(flags)
    }
    fun removeAccessExclusion(player: OfflinePlayer) {
        _accessExclusions.remove(fromPlayerMapper(player))
        flags = PortalFlag.NO_EXCLUSIONS.setFlag(flags)
    }
    fun containsAccessExclusion(player: OfflinePlayer) = fromPlayerMapper(player) in _accessExclusions

    val owner: OfflinePlayer
        get() = toPlayerMapper(ownerIndex)!!

    val world: World
        get() = toWorldMapper(worldIndex)!!

    val blockLocation: Location
        get() = Location(toWorldMapper(worldIndex), x.toDouble(), y.toDouble(), z.toDouble(), yaw, pitch)

    val portalLocation: Location
        get() = Location(toWorldMapper(worldIndex), x.toDouble() + 0.5, y.toDouble(), z.toDouble() + 0.5, yaw, pitch)

    private fun canEnter(player: OfflinePlayer) =
        player.uniqueId == toPlayerMapper(ownerIndex)!!.uniqueId || (_accessExclusions.contains(fromPlayerMapper(player)) != public)

    fun checkEnter(player: OfflinePlayer, portalMapper: MapFunction<UUID, Portal?>) =
        if (link == null) PortalResult.NO_LINK
        else if (!canEnter(player)) PortalResult.DISALLOWED
        else {
            val lnk = getPortalLink(portalMapper)
            if (lnk == null) PortalResult.NO_LINK
            else PortalResult.SUCCESS(lnk)
        }

    fun enterPortal(player: Player, portalMapper: MapFunction<UUID, Portal?>): PortalResult {
        return if (link == null) PortalResult.NO_LINK
        else if (!canEnter(player)) PortalResult.DISALLOWED
        else {
            val portal = getPortalLink(portalMapper) ?: return PortalResult.NO_LINK

            portal.teleportPlayerTo(player)

            PortalResult.SUCCESS(portal)
        }
    }

    fun teleportPlayerTo(player: Player) {
        player.teleport(portalLocation)
    }

    fun unlink() {
        link = null
        flags = PortalFlag.LINKED.unSetFlag(flags)
    }

    fun link(portal: Portal): Boolean {
        if (this == portal) return false
        link = portal.id
        flags = PortalFlag.LINKED.setFlag(flags)
        return true
    }

    fun getPortalLink(portalMapper: MapFunction<UUID, Portal?>): Portal? {
        val portal = portalMapper(this.link ?: return null)

        if (portal == null) unlink()

        return portal
    }

    fun toCompressedString(): String {
        val buffer = threadLocalInputBuffer.get()
        buffer.position = 0

        // IDs are sequential, starting at 0, so packing the value is worth it
        buffer.packedULong = id.mostSignificantBits.toULong()
        buffer.packedULong = id.leastSignificantBits.toULong()
        buffer.packedUInt = ownerIndex
        buffer.packedUInt = worldIndex
        buffer.packedInt = x
        buffer.packedInt = y
        buffer.packedInt = z
        buffer.packedUInt = yaw.toPackedRotationUInt()
        buffer.packedUInt = pitch.toPackedPitchUInt()
        buffer.byte = flags
        if (PortalFlag.LINKED.isFlagSet(flags)) {
            val link = link!!
            buffer.packedULong = link.mostSignificantBits.toULong()
            buffer.packedULong = link.leastSignificantBits.toULong()
        }

        buffer.putString(name)

        for (player in _accessExclusions)
            buffer.packedUInt = player

        val outputBuffer = threadLocalOutputBuffer.get()
        outputBuffer.position = 0

        outputBuffer.ensureAtLeast(b64OutLen(buffer.position, true))
        val len = b64Encode(buffer.buffer.array(), 0, buffer.position, outputBuffer.buffer.array())


        return buffer.position.toString(16).padStart(8, '0') + String(outputBuffer.buffer.array(), 0, len)
    }

    companion object {
        fun readCompressedPortal(
            data: String,
            toPlayerMapper: MapFunction<UInt, OfflinePlayer?>,
            fromPlayerMapper: MapFunction<OfflinePlayer, UInt>,
            toWorldMapper: MapFunction<UInt, World?>,
            fromWorldMapper: MapFunction<World, UInt>
        ): Portal {
            val inputBuffer = threadLocalInputBuffer.get()

            val dataLen = data.substring(0 until 8).toInt(16)

            inputBuffer.ensureAtLeast(dataLen)

            Base64.getDecoder().decode(data.substring(8).toByteArray(Charsets.ISO_8859_1), inputBuffer.buffer.array())
            inputBuffer.position = 0

            val flags: Byte
            return Portal(
                UUID(inputBuffer.packedULong.toLong(), inputBuffer.packedULong.toLong()),
                inputBuffer.packedUInt,
                inputBuffer.packedUInt,
                inputBuffer.packedInt,
                inputBuffer.packedInt,
                inputBuffer.packedInt,
                inputBuffer.packedUInt.fromPackedRotationFloat(),
                inputBuffer.packedUInt.fromPackedPitchFloat(),
                run {
                    flags = inputBuffer.byte
                    return@run flags
                },
                if (PortalFlag.LINKED.isFlagSet(flags)) UUID(inputBuffer.packedULong.toLong(), inputBuffer.packedULong.toLong())
                else null,
                inputBuffer.getString(),
                if (PortalFlag.NO_EXCLUSIONS.isFlagSet(flags)) SortedList(comparator = UInt::compareTo)
                else run {
                    val collect = SortedList(comparator = UInt::compareTo)
                    while (inputBuffer.position < dataLen)
                        collect += inputBuffer.packedUInt
                    return@run collect
                },
                toPlayerMapper,
                fromPlayerMapper,
                toWorldMapper,
                fromWorldMapper
            )
        }
    }
}