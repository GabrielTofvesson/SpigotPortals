import org.bukkit.Location
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.plugin.Plugin
import java.util.*
import kotlin.collections.ArrayList

private const val PATH_PLAYERS = "players"
private const val PATH_WORLDS = "worlds"
private const val PATH_PORTALS = "portals"


class PortalManager(private val data: ConfigurationSection, private val config: () -> ConfigurationSection): Listener {
    private val players = PlayerMapper(data, PATH_PLAYERS)
    private val worlds = WorldMapper(data, PATH_WORLDS)
    private var portals = MultiSortedList(ArrayList(), PORTAL_LOCATION_COMPARATOR, PORTAL_UID_COMPARATOR)

    // Make UUIDs as "sequential" as possible
    private var nextUUIDUsed = false
    private var nextUUID = UUID(0, 0)
        get() {
            // If currently held value guaranteed to be unused, just return it
            if (!nextUUIDUsed) {
                nextUUIDUsed = true
                return field
            }

            // Compute next available uuid
            var lsb = field.leastSignificantBits.toULong()
            var msb = field.mostSignificantBits.toULong()

            // Start sequential search at the resulting index if it is populated
            val index = portals.binSearch(PORTAL_UID_COMPARATOR) {
                compareValues(
                    { msb } to { it.id.mostSignificantBits.toULong() },
                    { lsb } to { it.id.leastSignificantBits.toULong() }
                )
            }

            if (index >= 0) {
                // Increment 128-bit value
                if (++lsb == 0UL)
                    ++msb

                for (i in index until portals.size) {
                    val find = portals.get(index, PORTAL_UID_COMPARATOR).id

                    // Found a gap in the UUIDs
                    if (find.mostSignificantBits.toULong() != msb || find.leastSignificantBits.toULong() != lsb)
                        break
                    else if (++lsb == 0UL)
                        ++msb
                }
            }

            // Save result and mark as used
            field = UUID(msb.toLong(), lsb.toLong())
            nextUUIDUsed = true

            return field
        }

    fun reload() {
        players.reload()
        worlds.reload()

        val portalList = ArrayList<Portal>()
        data.getStringList(PATH_PORTALS).forEach {
            val portal = readCompressedPortal(it, worlds::getValue, players::getValue) ?: return@forEach
            portalList += portal

            if (portal.id >= nextUUID)
                nextUUID = portal.id + 1UL
        }
        portals = MultiSortedList(portalList, PORTAL_LOCATION_COMPARATOR, PORTAL_UID_COMPARATOR)

        if(portals.isEmpty()) nextUUID = UUID(0, 0)
        else {
            nextUUID = portals.get(0, PORTAL_UID_COMPARATOR).id + 1UL

            // Compute next UUID
            nextUUID
            nextUUIDUsed = false
        }
    }

    fun save() {
        players.save()
        worlds.save()
        data.set(PATH_PORTALS, portals.map { it.toCompressedString(worlds::getIndex, players::getIndex) })
    }

    fun onEnable(plugin: Plugin) {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    fun onDisable() {
        HandlerList.unregisterAll(this)
    }

    @EventHandler
    fun onPlayerMove(moveEvent: PlayerMoveEvent) {
        fun UUID.portalMapper() = portals.firstOrNull { it.id == this }
        val to = moveEvent.to

        if (!moveEvent.isCancelled && to != null) {
            val found = getPortalsAt(to)

            found?.firstOrNull { it.owner.uniqueId == moveEvent.player.uniqueId }
                ?.enterPortal(moveEvent.player, UUID::portalMapper)
                ?: found?.firstOrNull { it.enterPortal(moveEvent.player, UUID::portalMapper) == PortalResult.SUCCESS }
        }
    }

    
    // This is a very hot function: allocate with extreme care!
    fun getPortalsAt(location: Location): LinkedList<Portal>? {
        fun portalFinder(portal: Portal) =
            compareValues(
                location.world!!::getUID to portal.world::getUID,
                location::getBlockX to portal::x,
                location::getBlockY to portal::y,
                location::getBlockZ to portal::z
            )

        // Don't allocate list unless there is data
        var index = portals.binarySearch(comparison = ::portalFinder)
        if (index < 0) return null

        val result = LinkedList<Portal>()

        do result += portals[index]
        while (++index < portals.size && portalFinder(portals[index]) == 0)

        return result
    }
}