import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.CommandSender
import org.bukkit.permissions.Permission
import java.util.*
import kotlin.collections.LinkedHashMap

const val RESULT_ERROR_NOMATCH = "Unknown command"
const val RESULT_ERROR_NOPERMS = "You don't have permission to use this command"
const val RESULT_ERROR_PLAYER = "Player does not exist"
const val RESULT_ERROR_NOTPLAYER = "Command can only be run by players"
const val RESULT_ERROR_NOTDECIMAL = "\"%s\" is not a number"
const val RESULT_ERROR_NOTINT = "\"%s\" is not an integer"
const val RESULT_ERROR_NOTENUM = "Value is not one of following: %s"

typealias Suggestor = (args: List<*>, sender: CommandSender, current: String) -> List<String>?
typealias ArgParser<T> = (parsed: List<*>, current: String, sender: CommandSender) -> NodeParseResult<T>
typealias ArgNode<T> = Pair<ArgParser<out T>, Suggestor>

inline fun <reified T> constantParseNode(value: T, crossinline toStringFunc: T.() -> String = { this.toString() }): ArgNode<T> = value.toStringFunc().let {
    { _: List<*>, current: String, _: CommandSender ->
        if (current == it) NodeParseResult.SuccessResult(value)
        else NodeParseResult.FailResult(RESULT_ERROR_NOMATCH)
    } to { _, _, current ->
        if (it.startsWith(current)) listOf(it)
        else null
    }
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

val PARSE_NODE_DECIMAL: ArgNode<Double> =
    { _: List<*>, current: String, _: CommandSender ->
        val result = current.toDoubleOrNull()
        if (result == null) NodeParseResult.FailResult(RESULT_ERROR_NOTDECIMAL.format(current))
        else NodeParseResult.SuccessResult(result)
    } to { _, _, current ->
        if (current.toDoubleOrNull() == null) null
        else listOf("0.0", "90.0", "180.0", "270.0", "360.0")
    }

val PARSE_NODE_INTEGER: ArgNode<Int> =
    { _: List<*>, current: String, _: CommandSender ->
        val result = current.toIntOrNull()
        if (result == null) NodeParseResult.FailResult(RESULT_ERROR_NOTINT.format(current))
        else NodeParseResult.SuccessResult(result)
    } to { _, _, current ->
        if (current.toIntOrNull() == null) null
        else emptyList()
    }

inline fun <reified T: Enum<T>> enumParseNode(): ArgNode<T> =
    { _: List<*>, current: String, _: CommandSender ->
        val parsed = T::class.java.enumConstants.firstOrNull { it.name.equals(current, ignoreCase = true) }
        if (parsed == null) NodeParseResult.FailResult(RESULT_ERROR_NOTENUM.format(T::class.java.enumConstants.joinToString { it.name }))
        else NodeParseResult.SuccessResult(parsed)
    } to { _, _, current: String ->
        T::class.java.enumConstants
            .filter { it.name.startsWith(current, ignoreCase = true) }
            .map { it.name }
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

const val PRIORITY_MAX = Int.MIN_VALUE
const val PRIORITY_MED = 0
const val PRIORITY_MIN = Int.MAX_VALUE

class ParseTree {
    private val branches = LinkedHashMap<ParseBranch, Int>()

    fun branch(vararg nodes: ArgNode<*>) = branch(PRIORITY_MED, *nodes)
    fun branch(parseBranch: ParseBranch) = branch(PRIORITY_MED, parseBranch)
    fun branch(priority: Int, vararg nodes: ArgNode<*>) = branch(priority, ParseBranch(*nodes))
    fun branch(priority: Int, parseBranch: ParseBranch): ParseTree {
        branches[parseBranch] = priority
        return this
    }

    fun getSuggestions(args: Array<out String>, sender: CommandSender) =
        branches.filter { (k, _) -> k.isEligible(sender) }
            .mapNotNull { (k, v) -> (k.getSuggestions(args, sender) ?: return@mapNotNull null) to v }
            .sortedBy { (_, v) -> v }
            .map(Pair<List<String>, *>::first)
            .flatten()
            .toHashSet()

    fun getMatch(args: Array<out String>, sender: CommandSender): ParseResult {
        branches.keys.forEach {
            if (it.isEligible(sender)) {
                val match = it.match(args, sender)
                if (match != null) return ParseResult.SuccessResult(match, it)
            }
        }

        return ParseResult.FailResult(RESULT_ERROR_NOMATCH, 0, true)
    }
}