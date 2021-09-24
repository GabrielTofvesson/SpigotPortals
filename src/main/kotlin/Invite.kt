import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import java.nio.ByteBuffer
import java.util.*

private val threadLocalBuffer = ThreadLocal.withInitial { ByteBuffer.allocate(32) }

data class Invite(val recipient: OfflinePlayer, val portalID: UUID) {
    private constructor(data: Pair<OfflinePlayer, UUID>): this(data.first, data.second)
    constructor(data: String): this(parseData(data))
    constructor(recipient: OfflinePlayer, portal: Portal): this(recipient, portal.id)

    override fun toString(): String {
        val buffer = threadLocalBuffer.get().position(0)
        buffer.putLong(recipient.uniqueId.mostSignificantBits)
        buffer.putLong(recipient.uniqueId.leastSignificantBits)
        buffer.putLong(portalID.mostSignificantBits)
        buffer.putLong(portalID.leastSignificantBits)

        return Base64.getEncoder().withoutPadding().encodeToString(buffer.array())
    }


    companion object {
        private fun parseData(data: String): Pair<OfflinePlayer, UUID> {
            val buffer = threadLocalBuffer.get().position(0)

            Base64.getDecoder().decode(data.toByteArray(Charsets.ISO_8859_1), buffer.array())

            return Bukkit.getOfflinePlayer(UUID(buffer.long, buffer.long)) to UUID(buffer.long, buffer.long)
        }
    }
}