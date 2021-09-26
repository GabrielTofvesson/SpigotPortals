import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.CommandSender
import org.bukkit.permissions.Permission
import java.util.*

const val RESULT_ERROR_NOMATCH = "Unknown command"
const val RESULT_ERROR_NOPERMS = "You don't have permission to use this command"
const val RESULT_ERROR_PLAYER = "Player does not exist"
const val RESULT_ERROR_NOTPLAYER = "Command can only be run by players"

typealias Suggestor = (args: List<*>, sender: CommandSender, current: String) -> List<String>?
typealias ArgParser<T> = (parsed: List<*>, current: String, sender: CommandSender) -> NodeParseResult<T>
typealias ArgNode<T> = Pair<ArgParser<out T>, Suggestor>

inline fun <reified T> constantParseNode(value: T, crossinline toStringFunc: T.() -> String = { this.toString() }): ArgNode<T> =
    { _: List<*>, current: String, _: CommandSender ->
        if (current == value.toStringFunc()) NodeParseResult.SuccessResult(value)
        else NodeParseResult.FailResult(RESULT_ERROR_NOMATCH)
    } to { _, _, current ->
        if (current.startsWith(value.toStringFunc())) listOf(value.toStringFunc())
        else null
    }

val PARSE_NODE_STRING: ArgNode<String> = { _: List<*>, current: String, _: CommandSender -> NodeParseResult.SuccessResult(current) } to { _, _, _ -> emptyList() }
val PARSE_NODE_PLAYER: ArgNode<OfflinePlayer> =
    { _: List<*>, current: String, _: CommandSender ->
        val player = Bukkit.getOfflinePlayers().firstOrNull { it.name?.equals(current) == true }
        if (player == null) NodeParseResult.FailResult(RESULT_ERROR_PLAYER)
        else NodeParseResult.SuccessResult(player)
    } to { _, _, current ->
        Bukkit.getOfflinePlayers()
            .filter { it.name?.startsWith(current) == true }
            .map { it.name!! }
            .ifEmpty { null }
    }

open class ParseBranch(private vararg val nodes: ArgNode<*>) {
    open fun getFailReason(sender: CommandSender): String? = null
    fun isEligible(sender: CommandSender) = getFailReason(sender) == null

    fun getSuggestions(args: Array<out String>, sender: CommandSender): List<String>? {
        if (args.size > nodes.size) return null

        val parseList = ArrayList<Any?>(nodes.size - 1)

        for (index in 0 until args.size - 1)
            when (val parsed = nodes[index].first(parseList, args[index], sender)) {
                is NodeParseResult.FailResult -> return null
                is NodeParseResult.SuccessResult<*> -> parseList += parsed.match
            }

        return nodes[args.size - 1].second(parseList, sender, args[args.size - 1])
    }

    fun match(args: Array<out String>, sender: CommandSender): List<*>? {
        if (args.size != nodes.size) return null

        val parseList = ArrayList<Any?>(nodes.size)

        for (index in args.indices)
            when (val parsed = nodes[index].first(parseList, args[index], sender)) {
                is NodeParseResult.FailResult -> return null
                is NodeParseResult.SuccessResult<*> -> parseList += parsed.match
            }

        return parseList
    }
}

open class PermissionParseBranch(private val permissionNode: Permission, private val allowConsole: Boolean, vararg nodes: ArgNode<*>): ParseBranch(*nodes) {
    constructor(permissionNode: Permission, vararg nodes: ArgNode<*>): this(permissionNode, true, *nodes)
    override fun getFailReason(sender: CommandSender) =
        when {
            !allowConsole && sender !is OfflinePlayer -> RESULT_ERROR_NOTPLAYER
            sender is OfflinePlayer && !sender.hasPermission(permissionNode) -> RESULT_ERROR_NOPERMS
            else -> null
        }
}

class PlayerParseBranch(vararg nodes: ArgNode<*>): ParseBranch(*nodes) {
    override fun getFailReason(sender: CommandSender) =
        if (sender is OfflinePlayer) null
        else RESULT_ERROR_NOTPLAYER
}

sealed class ParseResult {
    data class FailResult(val reason: String, val argIndex: Int, val relevant: Boolean): ParseResult()
    data class SuccessResult(val match: List<*>, val parseBranch: ParseBranch): ParseResult()
}

sealed class NodeParseResult<T> {
    data class FailResult<T>(val reason: String): NodeParseResult<T>()
    data class SuccessResult<T>(val match: T): NodeParseResult<T>()
}

class ParseTree {
    private val branches = LinkedList<ParseBranch>()

    fun branch(vararg nodes: ArgNode<*>) = branch(ParseBranch(*nodes))
    fun branch(parseBranch: ParseBranch): ParseTree {
        branches += parseBranch
        return this
    }

    fun getSuggestions(args: Array<out String>, sender: CommandSender) =
        branches.filter { it.isEligible(sender) }
            .mapNotNull { it.getSuggestions(args, sender) }
            .flatten()
            .toHashSet()

    fun getMatch(args: Array<out String>, sender: CommandSender): ParseResult {
        branches.forEach {
            if (it.isEligible(sender)) {
                val match = it.match(args, sender)
                if (match != null) return ParseResult.SuccessResult(match, it)
            }
        }

        return ParseResult.FailResult(RESULT_ERROR_NOMATCH, 0, true)
    }
}