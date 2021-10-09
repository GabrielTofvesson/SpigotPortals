import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.OfflinePlayer
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.permissions.Permission
import java.util.*

private const val RESULT_ERROR_NOPORTAL = "Given portal doesn't exist"
private const val RESULT_ERROR_NOINVITE = "Invitation to given portal does not exist"
private const val RESULT_ERROR_PORTALEXISTS = "Portal with this name already exists"
private const val RESULT_ERROR_NOTINVITED = "%s is not invited to portal"
private const val RESULT_ERROR_SELFREF = "Cannot link portal to itself"
private const val RESULT_ERROR_NOPORTALS = "%s has no portals"
private const val RESULT_ERROR_INVITED = "%s is already invited"
private const val RESULT_ERROR_BANOWNER = "Cannot ban portal owner"
private const val RESULT_ERROR_UNKNOWN = "An unknown error occurred"

private const val RESULT_SUCCESS_NEWPORTAL = "Created portal \"%s\""
private const val RESULT_SUCCESS_DELPORTAL = "Removed portal \"%s\""
private const val RESULT_SUCCESS_DELPORTALOTHER = "Removed portal \"%s\", owned by %s"
private const val RESULT_SUCCESS_BAN = "%s has been banned from using portal \"%s\""
private const val RESULT_SUCCESS_BANOTHER = "%s has been banned from using portal \"%s\", owned by %s"
private const val RESULT_SUCCESS_LINK = "Linked portal \"%s\" to portal \"%s\""
private const val RESULT_SUCCESS_UNLINK = "Unlinked portal \"%s\""
private const val RESULT_SUCCESS_INVITE = "Invited %s to portal \"%s\""
private const val RESULT_SUCCESS_INVITEOTHER = "Invited %s to portal \"%s\", owned by %s"
private const val RESULT_SUCCESS_CANCEL = "Cancelled invitation of %s to portal \"%s\""
private const val RESULT_SUCCESS_ACCEPT = "Accepted invite from %s to portal \"%s\""
private const val RESULT_SUCCESS_DECLINE = "Declined invite from %s to portal \"%s\""
private const val RESULT_SUCCESS_TP = "Teleported to portal \"%s\""
private const val RESULT_SUCCESS_TPO = "Teleported to portal \"%s\", owned by %s"
private const val RESULT_SUCCESS_EDIT_YAW = "Set yaw to %f degrees"
private const val RESULT_SUCCESS_EDIT_PITCH = "Set pitch to %f degrees"

private const val RESULT_INFO_LIST = "List of portals owned by %s:"
private const val RESULT_INFO_PORTAL = "Portal \"%s\" (%s; %d, %d, %d; %f, %f) (%s)"
private const val RESULT_INFO_PORTAL_LINKED = "Linked to \"%s\""
private const val RESULT_INFO_PORTAL_UNLINKED = "Un-linked"


val OfflinePlayer.playerName: String
    get() = name ?: "<Name Missing>"


class PortalCommand(
    private val portalManager: PortalManager,
    permissionCreate: Permission,
    permissionRemove: Permission,
    permissionRemoveOther: Permission,
    permissionInvite: Permission,
    permissionInviteOther: Permission,
    permissionListOther: Permission,
    permissionTp: Permission,
    permissionTpOther: Permission,
    permissionInfo: Permission,
    permissionInfoOther: Permission,
    permissionEdit: Permission
): CommandExecutor, TabCompleter {
    // Arg parse node for targeting a portal owned by the sender
    private val senderPortalParseNode: ArgNode<Portal> =
        { _: List<*>, current: String, sender: CommandSender ->
            val portal = portalManager.getPortal(sender as OfflinePlayer, current)
            if (portal == null) NodeParseResult.FailResult(RESULT_ERROR_NOPORTAL)
            else NodeParseResult.SuccessResult(portal)
        } to { _: List<*>, sender: CommandSender, current: String ->
            portalManager.getPortalsByPartialName(sender as OfflinePlayer, current)?.mapTo(ArrayList(), Portal::name)
        }

    // Arg parse node for targeting a portal owned by a named player
    private val otherPortalParseNode: ArgNode<Portal> =
        { parsed: List<*>, current: String, _: CommandSender ->
            val portal = portalManager.getPortal(parsed.last() as OfflinePlayer, current)
            if (portal == null) NodeParseResult.FailResult(RESULT_ERROR_NOPORTAL)
            else NodeParseResult.SuccessResult(portal)
        } to { parsed: List<*>, _: CommandSender, current: String ->
            portalManager.getPortalsByPartialName(parsed.last() as OfflinePlayer, current)?.mapTo(ArrayList(), Portal::name)
        }

    private val recipientInviteParseNode: ArgNode<Invite> =
        parse@{ parsed: List<*>, current: String, sender: CommandSender ->
            NodeParseResult.SuccessResult(
                portalManager.getInvitationsForPlayerFromPlayer(sender as OfflinePlayer, parsed.last() as OfflinePlayer)
                    ?.firstOrNull { portalManager.getPortal(it.portalID)?.name?.equals(current) == true }
                    ?: return@parse NodeParseResult.FailResult<Invite>(RESULT_ERROR_NOINVITE)
            )
        } to { parsed: List<*>, sender: CommandSender, current: String ->
            portalManager.getInvitationsForPlayerFromPlayer(sender as OfflinePlayer, parsed.last() as OfflinePlayer)
                ?.mapNotNull {
                    val name = portalManager.getPortal(it.portalID)?.name ?: return@mapNotNull null
                    if (name.startsWith(current)) name
                    else null
                }
        }

    private val senderInviteParseNode: ArgNode<Invite> =
        parse@{ parsed: List<*>, current: String, sender: CommandSender ->
            NodeParseResult.SuccessResult(
                portalManager.getInvitationsForPortal(
                    portalManager.getPortal(sender as OfflinePlayer, current)
                        ?: return@parse NodeParseResult.FailResult<Invite>(RESULT_ERROR_NOPORTAL)
                )
                    ?.firstOrNull { it.recipient.uniqueId == (parsed.last() as OfflinePlayer).uniqueId }
                    ?: return@parse NodeParseResult.FailResult<Invite>(RESULT_ERROR_NOINVITE)
            )
        } to { parsed: List<*>, sender: CommandSender, current: String ->
            portalManager.getInvitationsForPlayerFromPlayer(parsed.last() as OfflinePlayer, sender as OfflinePlayer)
                ?.mapNotNull {
                    val name = portalManager.getPortal(it.portalID)?.name ?: return@mapNotNull null
                    if (name.startsWith(current)) name
                    else null
                }
        }

    private val portalParse = ParseTree()
        .branch(PermissionParseBranch(permissionCreate, false, constantParseNode("create"), PARSE_NODE_STRING, senderPortalParseNode))                          //  portals create [name] [linkName]
        .branch(PermissionParseBranch(permissionCreate, false, constantParseNode("create"), PARSE_NODE_STRING))                                                 //  portals create [name]
        .branch(PermissionParseBranch(permissionRemove, false, constantParseNode("remove"), senderPortalParseNode))                                             //  portals remove [name]
        .branch(PermissionParseBranch(permissionRemoveOther, constantParseNode("remove"), PARSE_NODE_PLAYER, otherPortalParseNode))                             //  portals remove [player] [name]
        .branch(PlayerParseBranch(constantParseNode("uninvite"), senderPortalParseNode, PARSE_NODE_PLAYER))                                                     //  portals uninvite [name] [player]
        .branch(PermissionParseBranch(permissionInviteOther, false, constantParseNode("uninvite"), PARSE_NODE_PLAYER, otherPortalParseNode, PARSE_NODE_PLAYER)) //  portals uninvite [owner] [name] [player]
        .branch(PlayerParseBranch(constantParseNode("link"), senderPortalParseNode, senderPortalParseNode))                                                     //  portals link [name] [linkName]
        .branch(PlayerParseBranch(constantParseNode("unlink"), senderPortalParseNode))                                                                          //  portals unlink [name]
        .branch(PlayerParseBranch(constantParseNode("list")))                                                                                                   //  portals list
        .branch(PermissionParseBranch(permissionListOther, constantParseNode("list"), PARSE_NODE_PLAYER))                                                       //  portals list [player]
        .branch(PermissionParseBranch(permissionInvite, constantParseNode("invite"), senderPortalParseNode, PARSE_NODE_PLAYER))                                 //  portals invite [name] [player]
        .branch(PermissionParseBranch(permissionInviteOther, constantParseNode("invite"), PARSE_NODE_PLAYER, otherPortalParseNode, PARSE_NODE_PLAYER))          //  portals invite [owner] [name] [player]
        .branch(PlayerParseBranch(constantParseNode("invite"), constantParseNode("cancel"), PARSE_NODE_PLAYER, senderInviteParseNode))                          //  portals invite cancel [player] [name]
        .branch(PlayerParseBranch(constantParseNode("invite"), constantParseNode("accept"), PARSE_NODE_PLAYER, recipientInviteParseNode))                       //  portals invite accept [player] [name]
        .branch(PlayerParseBranch(constantParseNode("invite"), constantParseNode("decline"), PARSE_NODE_PLAYER, recipientInviteParseNode))                      //  portals invite decline [player] [name]
        .branch(PermissionParseBranch(permissionTp, false, constantParseNode("tp"), senderPortalParseNode))                                                     //  portals tp [name]
        .branch(PermissionParseBranch(permissionTpOther, false, constantParseNode("tp"), PARSE_NODE_PLAYER, otherPortalParseNode))                              //  portals tp [owner] [name]
        .branch(PermissionParseBranch(permissionInfo, false, constantParseNode("info"), senderPortalParseNode))                                                 //  portals info [name]
        .branch(PermissionParseBranch(permissionInfoOther, constantParseNode("info"), PARSE_NODE_PLAYER, otherPortalParseNode))                                 //  portals info [owner] [name]
        .branch(PermissionParseBranch(permissionEdit, false, constantParseNode("edit"), senderPortalParseNode, constantParseNode("yaw"), PARSE_NODE_DECIMAL))   //  portals edit [name] yaw [number]
        .branch(PermissionParseBranch(permissionEdit, false, constantParseNode("edit"), senderPortalParseNode, constantParseNode("pitch"), PARSE_NODE_DECIMAL)) //  portals edit [name] pitch [number]

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        when (val result = portalParse.getMatch(args, sender)) {
            is ParseResult.FailResult -> sender.spigot().sendMessage(TextComponent(result.reason))
            is ParseResult.SuccessResult -> {
                val message = when (result.match[0] as String) {
                    "create" -> {
                        val portal = portalManager.makePortal(
                            sender as Player,
                            result.match[1] as String,
                            sender.location,
                            if (result.match.size == 2) null else result.match[2] as Portal
                        )
                        if (portal == null) RESULT_ERROR_PORTALEXISTS else RESULT_SUCCESS_NEWPORTAL.format(
                            Locale.ROOT,
                            result.match[1] as String
                        )
                    }

                    "remove" -> {
                        val portal = result.match.last() as Portal
                        portalManager.removePortal(portal)
                        if (result.match.size == 2) RESULT_SUCCESS_DELPORTAL.format(Locale.ROOT, portal.name)
                        else RESULT_SUCCESS_DELPORTALOTHER.format(
                            Locale.ROOT,
                            portal.name,
                            portal.owner.playerName
                        )
                    }

                    "uninvite" -> {
                        val toRemove = result.match.last() as OfflinePlayer
                        val portal = result.match[result.match.size - 2] as Portal

                        if (toRemove.uniqueId == portal.owner.uniqueId) RESULT_ERROR_BANOWNER
                        else if (!portal.public) {
                            portal.removeAccessExclusion(toRemove)
                            RESULT_SUCCESS_BAN.format(toRemove.playerName, portal.name)
                        } else {
                            portal.addAccessExclusion(toRemove)
                            RESULT_SUCCESS_BANOTHER.format(
                                toRemove.playerName,
                                portal.name,
                                portal.owner.playerName
                            )
                        }
                    }

                    "link" -> {
                        val portal = result.match[result.match.size - 2] as Portal
                        val link = result.match.last() as Portal

                        if (portal.link(link)) RESULT_SUCCESS_LINK.format(portal.name, link.name)
                        else RESULT_ERROR_SELFREF
                    }

                    "unlink" -> {
                        val portal = result.match.last() as Portal
                        portal.unlink()
                        RESULT_SUCCESS_UNLINK.format(portal.name)
                    }

                    "list" -> {
                        val owner = sender as? OfflinePlayer ?: result.match.last() as OfflinePlayer
                        val portals = portalManager.getPortals(owner)

                        if (portals == null || !portals.iterator().hasNext()) RESULT_ERROR_NOPORTALS.format(owner.playerName)
                        else {
                            sender.spigot().sendMessage(TextComponent(RESULT_INFO_LIST.format(owner.playerName)))
                            for ((counter, portal) in portals.withIndex()) {
                                val portalLink = portal.getPortalLink(portalManager::getPortal)
                                sender.spigot().sendMessage(TextComponent("${counter}. ${portal.name}" + if (portalLink == null) "" else " -> ${portalLink.name}"))
                            }
                            null
                        }
                    }

                    "invite" ->
                        when (result.match[1]) {
                            is Portal, is OfflinePlayer -> {
                                val recipient = result.match.last() as OfflinePlayer
                                val portal = result.match[result.match.size - 2] as Portal

                                if (recipient.uniqueId == portal.owner.uniqueId || !portalManager.invitePlayer(
                                        recipient,
                                        portal
                                    )
                                ) RESULT_ERROR_INVITED.format(recipient.playerName)
                                else if (sender !is OfflinePlayer || portal.owner.uniqueId != (sender as OfflinePlayer).uniqueId)
                                    RESULT_SUCCESS_INVITEOTHER.format(
                                        recipient.playerName,
                                        portal.name,
                                        portal.owner.playerName
                                    )
                                else RESULT_SUCCESS_INVITE.format(recipient.playerName, portal.name)
                            }

                            "cancel" -> {
                                val invite = result.match.last() as Invite
                                val portal = portalManager.getPortal(invite.portalID)

                                if (portal == null || !portalManager.cancelInvite(invite)) RESULT_ERROR_UNKNOWN
                                else RESULT_SUCCESS_CANCEL.format(invite.recipient.playerName, portal.name)
                            }

                            "accept" -> {
                                val invite = result.match.last() as Invite
                                val portal = portalManager.getPortal(invite.portalID)

                                if (portal == null || !portalManager.acceptInvite(invite)) RESULT_ERROR_UNKNOWN
                                else RESULT_SUCCESS_ACCEPT.format(portal.owner.playerName, portal.name)
                            }

                            "decline" -> {
                                val invite = result.match.last() as Invite
                                val portal = portalManager.getPortal(invite.portalID)

                                if (portal == null || !portalManager.declineInvite(invite)) RESULT_ERROR_UNKNOWN
                                else RESULT_SUCCESS_DECLINE.format(portal.owner.playerName, portal.name)
                            }

                            else -> RESULT_ERROR_UNKNOWN
                        }

                    "tp" -> {
                        portalManager.teleportPlayerTo(sender as Player, result.match.last() as Portal)
                        null
                    }

                    "info" -> {
                        val portal = result.match.last() as Portal
                        val link = portal.getPortalLink(portalManager::getPortal)

                        sender.spigot().sendMessage(TextComponent(RESULT_INFO_PORTAL.format(
                            portal.name,
                            portal.world.name,
                            portal.x,
                            portal.y,
                            portal.z,
                            portal.yaw,
                            portal.pitch,
                            if (link == null) RESULT_INFO_PORTAL_UNLINKED else RESULT_INFO_PORTAL_LINKED.format(link.name)
                        )))
                        null
                    }

                    "edit" -> {
                        val value = result.match.last() as Double
                        val portal = result.match[result.match.size - 3] as Portal

                        when(result.match[result.match.size - 2] as String) {
                            "yaw" -> {
                                portal.yaw = value.toFloat()
                                RESULT_SUCCESS_EDIT_YAW.format(value)
                            }
                            "pitch" -> {
                                portal.pitch = value.toFloat()
                                RESULT_SUCCESS_EDIT_PITCH.format(value)
                            }
                            else -> RESULT_ERROR_UNKNOWN
                        }
                    }

                    else -> RESULT_ERROR_UNKNOWN
                }

                if (message != null)
                    sender.spigot().sendMessage(TextComponent(message))
            }
        }

        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ) = portalParse.getSuggestions(args, sender).toMutableList()
}