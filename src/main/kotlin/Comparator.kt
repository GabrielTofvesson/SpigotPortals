import org.bukkit.Location
import org.bukkit.OfflinePlayer
import org.bukkit.World
import java.util.*

typealias Comparison<V> = (V) -> Int
typealias Cooldown = Pair<OfflinePlayer, Long>

fun Cooldown.isExpired(currentTime: Long) = second < currentTime

val COMPARATOR_COOLDOWN_PLAYER = Comparator<Cooldown> { a, b -> a.first.uniqueId.compareTo(b.first.uniqueId) }
val COMPARATOR_COOLDOWN_EXPIRY = Comparator<Cooldown> { a, b -> a.second.compareTo(b.second) }

val OfflinePlayer.COMPARISON_COOLDOWN: Comparison<Cooldown>
    get() = { it.first.uniqueId.compareTo(uniqueId) }

val COMPARATOR_UUID = Comparator<UUID?> { a, b ->
    val aUnlinked = a == null
    val bUnlinked = b == null

    if (aUnlinked || bUnlinked) {
        return@Comparator if (aUnlinked == bUnlinked) 0 else if (aUnlinked) -1 else 1
    }

    a!!.compareTo(b!!)
}

// An owner cannot place two portals on the same block, implying that this comparator defines a partial order
val COMPARATOR_PORTAL_LOCATION_OWNER = Comparator<Portal> { a, b -> a.compareByOrder(b, Portal::worldIndex, Portal::x, Portal::y, Portal::z, Portal::ownerIndex) }
val COMPARATOR_PORTAL_LOCATION = Comparator<Portal> { a, b -> a.compareByOrder(b, Portal::worldIndex, Portal::x, Portal::y, Portal::z) }
val COMPARATOR_PORTAL_OWNER_NAME = Comparator<Portal> { a, b -> a.compareByOrder(b, Portal::ownerIndex, Portal::name) }
val COMPARATOR_PORTAL_LINKS = Comparator<Portal> { a, b -> COMPARATOR_UUID.compare(a.link, b.link) }

fun Location.portalComparison(fromWorldMapper: MapFunction<World, UInt>): Comparison<Portal> = {
    compareValues(
        it::worldIndex to { fromWorldMapper(world!!) },
        it::x to this::getBlockX,
        it::y to this::getBlockY,
        it::z to this::getBlockZ,
    )
}

val UUID.COMPARISON_PORTAL_ID: Comparison<Portal>
    get() = { it.id.compareTo(this) }

val Portal.COMPARISON_PORTAL_LINKEDTO: Comparison<Portal>
    get() = { COMPARATOR_UUID.compare(it.link, id) }

// IDs are unique, so this comparator inherently defines a partial order
val COMPARATOR_PORTAL_UID = Comparator<Portal> { a, b -> a.id.compareTo(b.id) }

val COMPARATOR_INVITE_RECIPIENT = Comparator<Invite> { a, b ->
    a.compareByOrder(b, { recipient.uniqueId }, Invite::portalID)
}

val COMPARATOR_INVITE_PORTAL = Comparator<Invite> { a, b ->
    a.compareByOrder(b, Invite::portalID, { recipient.uniqueId })
}

val OfflinePlayer.COMPARISON_INVITE: Comparison<Invite>
    get() = { it.recipient.uniqueId.compareTo(uniqueId) }

val Portal.COMPARISON_INVITE: Comparison<Invite>
    get() = { it.portalID.compareTo(id) }