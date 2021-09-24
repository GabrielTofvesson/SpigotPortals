import org.bukkit.Location
import org.bukkit.OfflinePlayer
import java.util.*
import kotlin.Comparator

typealias Comparison<V> = (V) -> Int
typealias Cooldown = Pair<OfflinePlayer, Long>

fun Cooldown.isExpired(currentTime: Long) = second < currentTime

val COMPARATOR_COOLDOWN_PLAYER = Comparator<Cooldown> { a, b -> a.first.uniqueId.compareTo(b.first.uniqueId) }
val COMPARATOR_COOLDOWN_EXPIRY = Comparator<Cooldown> { a, b -> a.second.compareTo(b.second) }

val OfflinePlayer.COMPARISON_COOLDOWN: Comparison<Cooldown>
    get() = { uniqueId.compareTo(it.first.uniqueId) }

val Long.COMPARISON_COOLDOWN: Comparison<Cooldown>
    get() = { compareTo(it.second) }

val COMPARATOR_PLAYER = Comparator<OfflinePlayer> { a, b -> a.uniqueId.compareTo(b.uniqueId) }
val COMPARATOR_LOCATION = Comparator<Location> { a, b -> a.compareByOrder(b, { world!!.uid }, Location::getBlockX, Location::getBlockY, Location::getBlockZ) }

val COMPARATOR_UUID = Comparator<UUID?> { a, b ->
    val aUnlinked = a == null
    val bUnlinked = a == null

    if (aUnlinked || bUnlinked) {
        return@Comparator if (aUnlinked == bUnlinked) 0 else if (aUnlinked) -1 else 1
    }

    a!!.compareTo(b!!)
}

// An owner cannot place two portals on the same block, implying that this comparator defines a partial order
val COMPARATOR_PORTAL_LOCATION_OWNER = Comparator<Portal> { a, b -> a.compareByOrder(b, { world.uid }, Portal::x, Portal::y, Portal::z, { owner.uniqueId }) }
val COMPARATOR_PORTAL_LOCATION = Comparator<Portal> { a, b -> a.compareByOrder(b, { world.uid }, Portal::x, Portal::y, Portal::z) }
val COMPARATOR_PORTAL_OWNER_NAME = Comparator<Portal> { a, b -> a.compareByOrder(b, { owner.uniqueId }, Portal::name) }
val COMPARATOR_PORTAL_LINKS = Comparator<Portal> { a, b -> COMPARATOR_UUID.compare(a.link, b.link) }

val Location.COMPARISON_PORTAL: Comparison<Portal>
    get() = {
        compareValues(
            world!!::getUID to it.world::getUID,
            this::getBlockX to it::x,
            this::getBlockY to it::y,
            this::getBlockZ to it::z
        )
    }

val OfflinePlayer.COMPARISON_PORTAL: Comparison<Portal>
    get() = { uniqueId.compareTo(it.owner.uniqueId) }

val UUID.COMPARISON_PORTAL_ID: Comparison<Portal>
    get() = { compareTo(it.id) }

val Portal.COMPARISON_PORTAL_LINKEDTO: Comparison<Portal>
    get() = { COMPARATOR_UUID.compare(id, it.link) }

// IDs are unique, so this comparator inherently defines a partial order
val COMPARATOR_PORTAL_UID = Comparator<Portal> { a, b -> a.id.compareTo(b.id) }

val COMPARATOR_INVITE_RECIPIENT = Comparator<Invite> { a, b ->
    a.compareByOrder(b, { recipient.uniqueId }, Invite::portalID)
}

val COMPARATOR_INVITE_PORTAL = Comparator<Invite> { a, b ->
    a.compareByOrder(b, Invite::portalID, { recipient.uniqueId })
}

val OfflinePlayer.COMPARISON_INVITE: Comparison<Invite>
    get() = { uniqueId.compareTo(it.recipient.uniqueId) }

val Portal.COMPARISON_INVITE: Comparison<Invite>
    get() = { id.compareTo(it.portalID) }