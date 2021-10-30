import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.World
import org.bukkit.configuration.ConfigurationSection
import java.nio.ByteBuffer
import java.util.*

typealias MapFunction<K, V> = K.() -> V

private val threadLocalBuffer = ThreadLocal.withInitial { ByteBuffer.allocate(16) }

private val STRING_TO_UUID: MapFunction<String, UUID> = {
    val buffer = threadLocalBuffer.get().position(0)

    Base64.getDecoder().decode(toByteArray(Charsets.ISO_8859_1), buffer.array())

    UUID(buffer.long, buffer.long)
}

private val UUID_TO_STRING: MapFunction<UUID, String> = {
    Base64.getEncoder()
        .withoutPadding()
        .encodeToString(
            threadLocalBuffer.get()
                .position(0)
                .putLong(mostSignificantBits)
                .putLong(leastSignificantBits)
                .array()
        )
}

open class UUIDMapper<T>(
    private val fromUUID: MapFunction<UUID, T?>,
    private val toUUID: MapFunction<T, UUID>,
    private val dataStore: ConfigurationSection,
    private val dataStorePath: String,
    private var underlying: MutableList<String> = ArrayList<String>()
) {
    init {
        reload()
    }

    private fun T.toSavedString() = toUUID().UUID_TO_STRING()
    private fun String.toMappedType() = STRING_TO_UUID().fromUUID()

    fun reload() {
        underlying = dataStore.getStringList(dataStorePath)
    }

    fun save() {
        dataStore.set(dataStorePath, underlying)
    }

    fun getValue(index: UInt) = underlying[index.toInt()].toMappedType()
    fun getIndex(value: T): UInt {
        val saved = value.toSavedString()
        val index = underlying.indexOf(saved)

        return if (index < 0) {
            underlying.add(saved)
            (underlying.size - 1).toUInt()
        } else {
            index.toUInt()
        }
    }
}

class PlayerMapper(dataStore: ConfigurationSection, dataStorePath: String): UUIDMapper<OfflinePlayer>(
    { Bukkit.getServer().getOfflinePlayer(this) },
    OfflinePlayer::getUniqueId,
    dataStore,
    dataStorePath
)

class WorldMapper(dataStore: ConfigurationSection, dataStorePath: String): UUIDMapper<World>(
    { Bukkit.getServer().getWorld(this) },
    World::getUID,
    dataStore,
    dataStorePath
)