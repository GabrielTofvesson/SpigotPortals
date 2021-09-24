import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.permissions.Permission
import java.util.*

typealias Suggestor = (args: List<Any?>, sender: CommandSender, current: String) -> Optional<List<String>>
typealias ArgParser<T> = (String) -> Optional<T>
typealias ArgNode<T> = Pair<ArgParser<T>, Suggestor>

inline fun <reified T> constantParseNode(value: T, crossinline toStringFunc: T.() -> String = { this.toString() }): ArgNode<T> =
    { it: String ->
        if (it == value.toStringFunc()) Optional.of(value!!)
        else Optional.empty()
    } to { _, _, current ->
        if (current.startsWith(value.toStringFunc())) Optional.of(listOf(value.toStringFunc()))
        else Optional.empty<List<String>>()
    }

val PARSE_NODE_STRING: ArgNode<String> = { str: String -> Optional.of(str) } to { _, _, _ -> Optional.of(emptyList()) }
val PARSE_NODE_PLAYER: ArgNode<OfflinePlayer> =
    { str: String ->
        val player = Bukkit.getOfflinePlayers().firstOrNull { it.name?.equals(str) == true }
        if (player == null) Optional.empty<OfflinePlayer>()
        else Optional.of(player)
    } to { _, _, current ->
        Optional.of(Bukkit.getOfflinePlayers().filter { it.name?.startsWith(current) == true }.map { it.name!! })
    }

open class ParseBranch(private vararg val nodes: ArgNode<*>) {
    open fun isEligible(sender: CommandSender): Boolean = true
    fun getSuggestions(args: Array<String>, sender: CommandSender): Optional<List<String>> {
        if (args.size > nodes.size) return Optional.empty()

        return nodes[args.size - 1].second((0 until args.size - 1).map {
            val parsed = nodes[it].first(args[it])

            if (parsed.isEmpty) return Optional.empty()

            parsed.get()
        }, sender, args[args.size - 1])
    }
}

class PermissionParseBranch(private val permissionNode: Permission, private val allowConsole: Boolean, vararg nodes: ArgNode<*>): ParseBranch(*nodes) {
    constructor(permissionNode: Permission, vararg nodes: ArgNode<*>): this(permissionNode, true, *nodes)
    override fun isEligible(sender: CommandSender) =
        (sender is OfflinePlayer && sender.hasPermission(permissionNode)) || (sender !is OfflinePlayer && allowConsole)
}

class ParseTree {
    private val branches = LinkedList<ParseBranch>()

    fun branch(vararg nodes: ArgNode<*>) = branch(ParseBranch(*nodes))
    fun branch(parseBranch: ParseBranch): ParseTree {
        branches += parseBranch
        return this
    }

    fun getSuggestions(args: Array<String>, sender: CommandSender) =
        branches.filter { it.isEligible(sender) }
            .mapNotNull {
                val suggestions = it.getSuggestions(args, sender)
                return@mapNotNull if (suggestions.isEmpty) null else suggestions.get()
            }
            .flatten()
            .toHashSet()
}