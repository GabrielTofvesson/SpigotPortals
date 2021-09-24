import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.permissions.Permission

class PortalCommand(
    permissionCreate: Permission,
    permissionRemove: Permission,
    permissionRemoveOther: Permission,
    permissionInvite: Permission,
    permissionInviteOther: Permission,
    permissionListOther: Permission
): CommandExecutor {
    private val portalParse = ParseTree()
        .branch(PermissionParseBranch(permissionCreate, constantParseNode("create"), PARSE_NODE_STRING, PARSE_NODE_STRING))                         //  portal create [name] [linkName]
        .branch(PermissionParseBranch(permissionCreate, constantParseNode("create"), PARSE_NODE_STRING))                                            //  portal create [name]
        .branch(PermissionParseBranch(permissionRemove, constantParseNode("remove"), PARSE_NODE_STRING))                                            //  portal remove [name]
        .branch(PermissionParseBranch(permissionRemoveOther, constantParseNode("remove"), PARSE_NODE_STRING, PARSE_NODE_PLAYER))                    //  portal remove [name] [player]
        .branch(PermissionParseBranch(permissionInvite, constantParseNode("invite"), PARSE_NODE_STRING, PARSE_NODE_PLAYER))                         //  portal invite [name] [player]
        .branch(PermissionParseBranch(permissionInviteOther, constantParseNode("invite"), PARSE_NODE_PLAYER, PARSE_NODE_STRING, PARSE_NODE_PLAYER)) //  portal invite [owner] [name] [player]
        .branch(constantParseNode("invite"), constantParseNode("cancel"), PARSE_NODE_PLAYER, PARSE_NODE_STRING)                                     //  portal invite cancel [player] [name]
        .branch(constantParseNode("invite"), constantParseNode("accept"), PARSE_NODE_PLAYER, PARSE_NODE_STRING)                                     //  portal invite accept [player] [name]
        .branch(constantParseNode("invite"), constantParseNode("decline"), PARSE_NODE_PLAYER, PARSE_NODE_STRING)                                    //  portal invite decline [player] [name]
        .branch(constantParseNode("uninvite"), PARSE_NODE_PLAYER, PARSE_NODE_STRING)                                                                //  portal uninvite [player] [name]
        .branch(constantParseNode("link"), PARSE_NODE_STRING, PARSE_NODE_STRING)                                                                    //  portal link [name] [linkName]
        .branch(constantParseNode("unlink"), PARSE_NODE_STRING)                                                                                     //  portal unlink [name]
        .branch(constantParseNode("list"))                                                                                                          //  portal list
        .branch(PermissionParseBranch(permissionListOther, constantParseNode("list"), PARSE_NODE_PLAYER))                                           //  portal list [player]

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        TODO("Not yet implemented")
    }
}