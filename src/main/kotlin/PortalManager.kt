import org.bukkit.Location
import org.bukkit.OfflinePlayer
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import java.lang.Long.max
import java.util.*
import java.util.logging.Logger

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
    private val touchPortalCooldown = HashMap<UUID, Portal>()

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
                    { it.id.mostSignificantBits.toULong() } to { msb },
                    { it.id.leastSignificantBits.toULong() } to { lsb }
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
            val portal = Portal.readCompressedPortal(it, players::getValue, players::getIndex, worlds::getValue, worlds::getIndex)
            portalList += portal

            if (portal.id >= nextUUID)
                nextUUID = portal.id + 1UL
        }
        portals = MultiSortedList(portalList, ::ArrayList, COMPARATOR_PORTAL_LOCATION_OWNER, COMPARATOR_PORTAL_UID, COMPARATOR_PORTAL_OWNER_NAME, COMPARATOR_PORTAL_LINKS)

        if(portals.isEmpty()) nextUUID = UUID(0, 0)
        else {
            // Compute next UUID
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
        data.set(PATH_DATA_PORTALS, portals.map { it.toCompressedString() })
    }

    fun onEnable(plugin: Plugin) {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    fun onDisable() {
        HandlerList.unregisterAll(this)
        save()
    }

    fun getInvitationsForPlayer(player: OfflinePlayer) =
        invitations.getAll(COMPARATOR_INVITE_RECIPIENT) { it.recipient.uniqueId.compareTo(player.uniqueId) }

    fun getInvitationsForPortal(portalID: UUID) =
        invitations.getAll(COMPARATOR_INVITE_PORTAL) { it.portalID.compareTo(portalID) }

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
        if (portal.containsAccessExclusion(player) || invitations.search(COMPARATOR_INVITE_RECIPIENT) {
                compareValues(it.recipient::getUniqueId to player::getUniqueId, it::portalID to portal::id)
        } >= 0)
            return false

        invitations += Invite(player, portal)

        return true
    }

    fun cancelInvite(player: OfflinePlayer, portal: Portal): Boolean {
        val index = invitations.search(COMPARATOR_INVITE_RECIPIENT) {
            compareValues(
                it.recipient::getUniqueId to player::getUniqueId,
                it::portalID to portal::id
            )
        }

        if (index < 0) return false
        invitations.removeAt(index, COMPARATOR_INVITE_RECIPIENT)

        return true
    }

    fun cancelInvite(invite: Invite) =
        invitations.remove(invite)

    private fun acceptInvite0(player: OfflinePlayer, portal: Portal) {
        if (portal.public) portal.removeAccessExclusion(player)
        else portal.addAccessExclusion(player)
    }

    fun acceptInvite(player: OfflinePlayer, portal: Portal): Boolean {
        if (!cancelInvite(player, portal) || (portal.containsAccessExclusion(player) != portal.public)) return false

        acceptInvite0(player, portal)

        return true
    }

    fun acceptInvite(invite: Invite): Boolean {
        val portal = getPortal(invite.portalID) ?: return false
        if (!cancelInvite(invite) || (portal.containsAccessExclusion(invite.recipient) != portal.public)) return false

        acceptInvite0(invite.recipient, portal)

        return true
    }

    fun declineInvite(player: OfflinePlayer, portal: Portal) = cancelInvite(player, portal)
    fun declineInvite(invite: Invite) = cancelInvite(invite)


    fun makePortal(owner: OfflinePlayer, name: String, location: Location, link: Portal? = null): Portal? {
        val portal = Portal(
            players::getValue, players::getIndex, worlds::getValue, worlds::getIndex,
            nextUUID,
            owner,
            location.world!!,
            location.blockX,
            location.blockY,
            location.blockZ,
            location.yaw,
            location.pitch,
            name,
            link
        )

        return if (makePortal(portal)) portal else null
    }

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
        val ownerIndex = players.getIndex(owner)
        val index = portals.search(COMPARATOR_PORTAL_OWNER_NAME) {
            compareValues(it::ownerIndex to { ownerIndex }, it::name to { name })
        }

        if (index < 0) return false

        // Remove invites linked to this portal
        clearInvites(portals.get(index, COMPARATOR_PORTAL_OWNER_NAME))
        val removed = portals.removeAt(index, COMPARATOR_PORTAL_OWNER_NAME)

        // Unlink portals
        portals.getAll(COMPARATOR_PORTAL_LINKS, removed.COMPARISON_PORTAL_LINKEDTO)?.forEach(Portal::unlink)

        onPortalRemove(removed)

        return true
    }

    fun removePortal(portal: Portal) {
        portals.remove(portal)
        onPortalRemove(portal)
    }

    private fun onPortalRemove(portal: Portal) {
        synchronized(touchPortalCooldown) {
            touchPortalCooldown.values.removeIf(portal::equals)
        }
    }

    fun getPortal(uuid: UUID): Portal? {
        val index = portals.search(COMPARATOR_PORTAL_UID, uuid.COMPARISON_PORTAL_ID)
        if (index < 0) return null
        return portals.get(index, COMPARATOR_PORTAL_UID)
    }

    fun getPortal(owner: OfflinePlayer, name: String): Portal? {
        val index = portals.search(COMPARATOR_PORTAL_OWNER_NAME) {
            compareValues(it.owner::getUniqueId to owner::getUniqueId, it::name to { name })
        }

        if (index < 0) return null

        return portals.get(index, COMPARATOR_PORTAL_OWNER_NAME)
    }

    fun getPortals(owner: OfflinePlayer) =
        portals.getAll(COMPARATOR_PORTAL_OWNER_NAME) { owner.uniqueId.compareTo(it.owner.uniqueId) }

    fun getPortalsByPartialName(owner: OfflinePlayer, namePart: String) =
        portals.getAll(COMPARATOR_PORTAL_OWNER_NAME) {
            compareValues(
                it.owner::getUniqueId to owner::getUniqueId,
                { it.name.substring(0, namePart.length.coerceAtMost(it.name.length)) } to { namePart }
            )
        }

    fun getPortalsAt(location: Location) =
        portals.getAll(COMPARATOR_PORTAL_LOCATION_OWNER, location.portalComparison(worlds::getIndex))

    fun teleportPlayerTo(player: Player, portal: Portal) {
        val result = portal.enterPortal(player, this::getPortal)
        if (result is PortalResult.SUCCESS)
            triggerCooldown(player, result.link)
        else
            Logger.getLogger("SpigotPortals")
                .warning("${player.name} failed to enter portal ${portal.name} (${portal.owner.playerName}; ${portal.world.name}; ${portal.x}, ${portal.y}, ${portal.z})")
    }

    private fun popCooldowns(player: OfflinePlayer, moveTo: Location) {
        val time = System.currentTimeMillis()
        while (cooldowns.isNotEmpty()) {
            val front = cooldowns.get(0, COMPARATOR_COOLDOWN_EXPIRY)
            if (front.isExpired(time)) cooldowns.removeAt(0, COMPARATOR_COOLDOWN_EXPIRY)
            else break
        }

        if (moveTo.portalComparison(worlds::getIndex)(touchPortalCooldown[player.uniqueId] ?: return) != 0) {
            touchPortalCooldown.remove(player.uniqueId)
        }
    }

    private fun isOnCooldown(player: OfflinePlayer, moveTo: Location): Boolean {
        popCooldowns(player, moveTo)
        return cooldowns.search(COMPARATOR_COOLDOWN_PLAYER, player.COMPARISON_COOLDOWN) >= 0 || player.uniqueId in touchPortalCooldown
    }

    private fun triggerCooldown(player: OfflinePlayer, portal: Portal) {
        cooldowns.add(Pair(player, System.currentTimeMillis() + cooldownTime), false)
        touchPortalCooldown[player.uniqueId] = portal
    }

    @EventHandler
    fun onPlayerMove(moveEvent: PlayerMoveEvent) {
        val to = moveEvent.to

        if (!moveEvent.isCancelled && to != null) {
            // If we're ignoring player movements for this player, just return immediately
            if (isOnCooldown(moveEvent.player, to)) return

            val found = getPortalsAt(to)

            val triggered = found?.firstOrNull {
                it.owner.uniqueId == moveEvent.player.uniqueId && it.checkEnter(moveEvent.player, this::getPortal) is PortalResult.SUCCESS
            }
                ?: found?.firstOrNull { it.checkEnter(moveEvent.player, this::getPortal) is PortalResult.SUCCESS }

            if (triggered != null)
                teleportPlayerTo(moveEvent.player, triggered)
        }
    }

    @EventHandler
    fun onPlayerDisconnect(disconnectEvent: PlayerQuitEvent) {
        synchronized(touchPortalCooldown) {
            touchPortalCooldown.remove(disconnectEvent.player.uniqueId)
        }
    }
}