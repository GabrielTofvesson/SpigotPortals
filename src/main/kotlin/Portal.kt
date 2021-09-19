import org.bukkit.Location
import org.bukkit.OfflinePlayer
import org.bukkit.World
import org.bukkit.entity.Player
import java.nio.ByteBuffer
import java.util.*
import kotlin.Comparator
import kotlin.collections.ArrayList
import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or

private val PLAYER_COMPARATOR = Comparator<OfflinePlayer> { a, b -> a.uniqueId.compareTo(b.uniqueId) }
val LOCATION_COMPARATOR = Comparator<Location> { a, b -> a.compareByOrder(b, { world!!.uid }, Location::getBlockX, Location::getBlockY, Location::getBlockZ) }
val PORTAL_COMPARATOR = Comparator<Portal> { a, b -> a.compareByOrder(b, { world.uid }, Portal::x, Portal::y, Portal::z, Portal::id) }


private val threadLocalInputBuffer = ThreadLocal.withInitial { ReallocatingBuffer(ByteBuffer.allocate(96)) }
private val threadLocalOutputBuffer = ThreadLocal.withInitial { ReallocatingBuffer(ByteBuffer.allocate(96)) }

private val method_encode0 = run {
    val method = Base64::class.java.getDeclaredMethod(
        "encode0",
        ByteArray::class.java,
        Int::class.java,
        Int::class.java,
        ByteArray::class.java
    )
    method.isAccessible = true
    return@run method
}

private val method_encodedOutLength = run {
    val method = Base64::class.java.getDeclaredMethod("encodedOutLength", Int::class.java, Boolean::class.java)
    method.isAccessible = true
    return@run method
}

enum class PortalResult {
    NO_LINK,
    DISALLOWED,
    SUCCESS
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


class Portal(
    val id: UUID,
    val owner: OfflinePlayer,
    val world: World,
    val x: Int,
    val y: Int,
    val z: Int,
    var yaw: Float,
    var pitch: Float,
    private var flags: Byte,
    link: UUID?,
    val accessExclusions: SortedList<OfflinePlayer>
) {
    init {
        flags = applyFlags(
            flags,
            mapOf(
                PortalFlag.NO_EXCLUSIONS to accessExclusions.isEmpty(),
                PortalFlag.LINKED to (link != null)
            )
        )
    }

    var public: Boolean
        get() = PortalFlag.PUBLIC.isFlagSet(flags)
        set(value) {
            accessExclusions.clear()
            flags = PortalFlag.PUBLIC.setFlagValue(flags, value)
        }

    var link = link
        private set(value) {
            flags = PortalFlag.LINKED.setFlagValue(flags, value != null)
            field = value
        }

    internal val location: Location
        get() = Location(world, x.toDouble(), y.toDouble(), z.toDouble(), yaw, pitch)

    fun canEnter(player: OfflinePlayer) =
        player.uniqueId == owner.uniqueId || (accessExclusions.contains(player) != public)

    fun enterPortal(player: Player, portalMapper: MapFunction<UUID, Portal?>): PortalResult {
        val remoteLink = link

        return if (remoteLink == null) PortalResult.NO_LINK
        else if (!canEnter(player)) PortalResult.DISALLOWED
        else {
            val portal = portalMapper(remoteLink)
            if (portal == null) {
                link = null
                return PortalResult.NO_LINK
            }

            player.teleport(portal.location)
            PortalResult.SUCCESS
        }
    }

    fun toCompressedString(worldMapper: MapFunction<World, UInt>, playerMapper: MapFunction<OfflinePlayer, UInt>): String {
        val buffer = threadLocalInputBuffer.get()
        buffer.position = 0

        buffer.long = id.mostSignificantBits
        buffer.long = id.leastSignificantBits
        buffer.packedUInt = playerMapper(owner)
        buffer.packedUInt = worldMapper(world)
        buffer.packedInt = x
        buffer.packedInt = y
        buffer.packedInt = z
        buffer.putPackedFloat(yaw, 0f, 360f)
        buffer.putPackedFloat(pitch, 0f, 360f)
        buffer.byte = flags
        if (PortalFlag.LINKED.isFlagSet(flags)) {
            val link = link!!
            buffer.long = link.mostSignificantBits
            buffer.long = link.leastSignificantBits
        }
        if (accessExclusions.size > 0) {
            for (player in accessExclusions)
                buffer.packedUInt = playerMapper(player)
        }

        val outputBuffer = threadLocalOutputBuffer.get()
        outputBuffer.position = 0
        val encoder = Base64.getEncoder().withoutPadding()

        outputBuffer.ensureAtLeast(method_encodedOutLength.invoke(encoder, buffer.position, true) as Int)
        val len = method_encode0.invoke(encoder, buffer.buffer.array(), 0, outputBuffer.buffer.array()) as Int


        return buffer.position.toString(16).padStart(8, '0') + String(outputBuffer.buffer.array(), 0, len)
    }
}

fun readCompressedPortal(
    data: String,
    worldMapper: MapFunction<UInt, World?>,
    playerMapper: MapFunction<UInt, OfflinePlayer?>
): Portal? {
    val inputBuffer = threadLocalInputBuffer.get()
    inputBuffer.position = 0

    val dataLen = data.substring(0 until 8).toInt(16)

    inputBuffer.ensureAtLeast(dataLen)

    Base64.getDecoder().decode(data.substring(8).toByteArray(Charsets.ISO_8859_1), inputBuffer.buffer.array())

    val flags: Byte
    return Portal(
        UUID(inputBuffer.long, inputBuffer.long),
        playerMapper(inputBuffer.packedUInt) ?: return null,
        worldMapper(inputBuffer.packedUInt) ?: return null,
        inputBuffer.packedInt,
        inputBuffer.packedInt,
        inputBuffer.packedInt,
        inputBuffer.getPackedFloat(0f, 360f),
        inputBuffer.getPackedFloat(0f, 360f),
        run {
            flags = inputBuffer.byte
            return@run flags
        },
        if (PortalFlag.LINKED.isFlagSet(flags)) UUID(inputBuffer.long, inputBuffer.long)
        else null,
        if (PortalFlag.NO_EXCLUSIONS.isFlagSet(flags)) SortedList.create(comparator = PLAYER_COMPARATOR)
        else run {
            val collect = SortedList.create(comparator = PLAYER_COMPARATOR)
            while (inputBuffer.position < dataLen)
                collect += playerMapper(inputBuffer.packedUInt) ?: continue
            return@run collect
        }
    )
}