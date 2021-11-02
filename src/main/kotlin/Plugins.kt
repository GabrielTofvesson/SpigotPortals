import org.bukkit.plugin.java.JavaPlugin

fun JavaPlugin.getPermission(name: String) = description.permissions.firstOrNull { it.name == name }