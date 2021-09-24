import org.bukkit.Location
import org.bukkit.OfflinePlayer
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.plugin.Plugin
import java.lang.Long.max
import java.util.*

private const val PATH_DATA_PLAYERS = "players"
private const val PATH_DATA_WORLDS = "worlds"
private const val PATH_DATA_PORTALS = "portals"
private const val PATH_DATA_INVITES = "invites"

private const val PATH_CONFIG_COOLDOWN = "playerTeleportCooldownTicks"

private const val DEFAULT_COOLDOWN = 100L
private const val DEFAULT_COOLDOWN_MIN = 5L


// TODO: Adapt to proper DBMS because this is depressing garbage
class PortalManager(private val data: ConfigurationSection, private val config: () -> ConfigurationSection): Listener {
    private val players = PlayerMapper(data, PATH_DATA_PLAYERS)
    private val worlds = WorldMapper(data, PATH_DATA_WORLDS)
    private var portals = MultiSortedList(::ArrayList, COMPARATOR_PORTAL_LOCATION_OWNER, COMPARATOR_PORTAL_UID, COMPARATOR_PORTAL_OWNER_NAME, COMPARATOR_PORTAL_LINKS)
    private var invitations = MultiSortedList(::ArrayList, COMPARATOR_INVITE_RECIPIENT, COMPARATOR_INVITE_PORTAL)

    // Player-based list needs to handle random access efficiently, whereas expiry list will always be accessed sequentially
    private val cooldowns = MultiSortedList(ArrayList(), ::LinkedList, COMPARATOR_COOLDOWN_PLAYER, COMPARATOR_COOLDOWN_EXPIRY)

    private var cooldownTime = DEFAULT_COOLDOWN


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
            val index = portals.search(COMPARATOR_PORTAL_UID) {
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
                    val find = portals.get(index, COMPARATOR_PORTAL_UID).id

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
        cooldownTime = max(config().getLong(PATH_CONFIG_COOLDOWN), DEFAULT_COOLDOWN_MIN)

        players.reload()
        worlds.reload()

        val portalList = ArrayList<Portal>()
        data.getStringList(PATH_DATA_PORTALS).forEach {
            val portal = readCompressedPortal(it, worlds::getValue, players::getValue) ?: return@forEach
            portalList += portal

            if (portal.id >= nextUUID)
                nextUUID = portal.id + 1UL
        }
        portals = MultiSortedList(portalList, ::ArrayList, COMPARATOR_PORTAL_LOCATION_OWNER, COMPARATOR_PORTAL_UID)

        if(portals.isEmpty()) nextUUID = UUID(0, 0)
        else {
            nextUUID = portals.get(0, COMPARATOR_PORTAL_UID).id + 1UL

            // Compute next UUID
            nextUUID
            nextUUIDUsed = false
        }

        invitations = MultiSortedList(
            data.getStringList(PATH_DATA_INVITES).mapTo(ArrayList(), ::Invite),
            ::ArrayList,
            COMPARATOR_INVITE_RECIPIENT,
            COMPARATOR_INVITE_PORTAL
        )
    }

    fun save() {
        players.save()
        worlds.save()
        data.set(PATH_DATA_PORTALS, portals.map { it.toCompressedString(worlds::getIndex, players::getIndex) })
    }

    fun onEnable(plugin: Plugin) {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    fun onDisable() {
        HandlerList.unregisterAll(this)
    }

    fun getInvitationsForPlayer(player: OfflinePlayer) =
        invitations.getAll(COMPARATOR_INVITE_RECIPIENT) { player.uniqueId.compareTo(it.recipient.uniqueId) }

    fun getInvitationsForPortal(portalID: UUID) =
        invitations.getAll(COMPARATOR_INVITE_PORTAL) { portalID.compareTo(it.portalID) }

    fun getInvitationsForPortal(portal: Portal) = getInvitationsForPortal(portal.id)

    // This is a perfect example of why mysql is better
    fun getInvitationsForPlayerFromPlayer(recipient: OfflinePlayer, sender: OfflinePlayer) =
        invitations.getAll(COMPARATOR_INVITE_RECIPIENT) {
            compareValues(
                recipient::getUniqueId to it.recipient::getUniqueId,
                portals.get(
                    portals.search(COMPARATOR_PORTAL_UID, it.portalID.COMPARISON_PORTAL_ID), COMPARATOR_PORTAL_UID
                ).owner::getUniqueId to sender::getUniqueId
            )
        }

    fun invitePlayer(player: OfflinePlayer, portal: Portal): Boolean {
        // Player is already invited or already has a pending invitation
        if (player in portal.accessExclusions || invitations.search(COMPARATOR_INVITE_RECIPIENT) {
                compareValues(player::getUniqueId to it.recipient::getUniqueId, portal::id to it::portalID)
        } >= 0)
            return false

        invitations += Invite(player, portal)

        return true
    }

    fun cancelInvite(player: OfflinePlayer, portal: Portal): Boolean {
        val index = invitations.search(COMPARATOR_INVITE_RECIPIENT) {
            compareValues(
                player::getUniqueId to it.recipient::getUniqueId,
                portal::id to it::portalID
            )
        }

        if (index < 0) return false
        invitations.removeAt(index, COMPARATOR_INVITE_RECIPIENT)

        return true
    }

    fun acceptInvite(player: OfflinePlayer, portal: Portal): Boolean {
        if (!cancelInvite(player, portal)) return false

        portal.accessExclusions += player

        return true
    }

    fun declineInvite(player: OfflinePlayer, portal: Portal) = cancelInvite(player, portal)

    // This makes me cry
    fun makePortal(portal: Portal) =
        !portals.contains(portal, COMPARATOR_PORTAL_OWNER_NAME) &&
                !portals.contains(portal, COMPARATOR_PORTAL_LOCATION_OWNER) &&
                portals.add(portal)

    fun clearInvites(portal: Portal) = clearInvites(COMPARATOR_INVITE_PORTAL, portal.COMPARISON_INVITE)
    fun clearInvites(recipient: OfflinePlayer) = clearInvites(COMPARATOR_INVITE_RECIPIENT, recipient.COMPARISON_INVITE)
    private fun clearInvites(comparator: Comparator<Invite>, comparison: Comparison<Invite>) =
        invitations.getAll(comparator, comparison)?.forEach(invitations::remove)

    fun removePortal(owner: OfflinePlayer, name: String): Boolean {
        val index = portals.search(COMPARATOR_PORTAL_OWNER_NAME) {
            compareValues(owner::getUniqueId to it.owner::getUniqueId, { name } to it::name)
        }

        if (index < 0) return false

        // Remove invites linked to this portal
        clearInvites(portals.get(index, COMPARATOR_PORTAL_OWNER_NAME))
        val removed = portals.removeAt(index, COMPARATOR_PORTAL_OWNER_NAME)

        // Unlink portals
        portals.getAll(COMPARATOR_PORTAL_LINKS, removed.COMPARISON_PORTAL_LINKEDTO)?.forEach(Portal::unlink)

        return true
    }

    fun getPortal(owner: OfflinePlayer, name: String): Portal? {
        val index = portals.search(COMPARATOR_PORTAL_OWNER_NAME) {
            compareValues(owner::getUniqueId to it.owner::getUniqueId, { name } to it::name)
        }

        if (index < 0) return null

        return portals.get(index, COMPARATOR_PORTAL_OWNER_NAME)
    }

    fun getPortalsAt(location: Location) =
        portals.getAll(COMPARATOR_PORTAL_LOCATION_OWNER, location.COMPARISON_PORTAL)

    private fun popCooldowns() {
        val time = System.currentTimeMillis()
        while (cooldowns.isNotEmpty()) {
            val front = cooldowns.get(0, COMPARATOR_COOLDOWN_EXPIRY)
            if (front.isExpired(time)) cooldowns.removeAt(0, COMPARATOR_COOLDOWN_EXPIRY)
            else break
        }
    }

    private fun isOnCooldown(player: OfflinePlayer): Boolean {
        popCooldowns()
        return cooldowns.search(COMPARATOR_COOLDOWN_PLAYER, player.COMPARISON_COOLDOWN) >= 0
    }

    private fun triggerCooldown(player: OfflinePlayer) {
        cooldowns.add(Pair(player, System.currentTimeMillis() + cooldownTime), false)
    }

    @EventHandler
    fun onPlayerMove(moveEvent: PlayerMoveEvent) {
        // If we're ignoring player movements for this player, just return immediately
        if (isOnCooldown(moveEvent.player)) return

        fun UUID.portalMapper() = portals.firstOrNull { it.id == this }
        val to = moveEvent.to

        if (!moveEvent.isCancelled && to != null) {
            val found = getPortalsAt(to)

            if ((found?.firstOrNull { it.owner.uniqueId == moveEvent.player.uniqueId }
                    ?.enterPortal(moveEvent.player, UUID::portalMapper)
                    ?: found?.firstOrNull {
                        it.enterPortal(
                            moveEvent.player,
                            UUID::portalMapper
                        ) == PortalResult.SUCCESS
                    }) != null)
                triggerCooldown(moveEvent.player)
        }
    }
}