import org.bukkit.Location
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerMoveEvent
import java.util.*
import kotlin.collections.ArrayList

private const val PATH_PLAYERS = "players"
private const val PATH_WORLDS = "worlds"
private const val PATH_PORTALS = "portals"


class PortalManager(private val data: ConfigurationSection, private val config: () -> ConfigurationSection) {
    private val players = PlayerMapper(data, PATH_PLAYERS)
    private val worlds = WorldMapper(data, PATH_WORLDS)
    private var portals = SortedList.create(comparator = PORTAL_COMPARATOR)

    fun reload() {
        players.reload()
        worlds.reload()

        val portalList = ArrayList<Portal>()
        data.getStringList(PATH_PORTALS).forEach {
            portalList += readCompressedPortal(it, worlds::getValue, players::getValue) ?: return@forEach
        }
        portals = SortedList.create(PORTAL_COMPARATOR, portalList)
    }

    fun save() {
        players.save()
        worlds.save()
        data.set(PATH_PORTALS, portals.map { it.toCompressedString(worlds::getIndex, players::getIndex) })
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