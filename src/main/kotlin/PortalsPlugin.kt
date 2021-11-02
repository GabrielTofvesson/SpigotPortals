import kr.entree.spigradle.annotations.SpigotPlugin
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

@SpigotPlugin
class PortalsPlugin: JavaPlugin() {
    private val data = YamlFile.loadFile(File(dataFolder, "data.yml"))
    private val portalManager = PortalManager(data) { config }

    override fun onEnable() {
        super.onEnable()

        reloadConfig()

        portalManager.onEnable(this)


        val command = PortalCommand(
            portalManager,
            getPermission("portals.create")!!,
            getPermission("portals.modify.remove")!!,
            getPermission("portals.modify.other")!!,
            getPermission("portals.invite")!!,
            getPermission("portals.invite.other")!!,
            getPermission("portals.list.other")!!,
            getPermission("portals.tp")!!,
            getPermission("portals.tp.other")!!,
            getPermission("portals.info")!!,
            getPermission("portals.info.other")!!,
            getPermission("portals.modify.edit")!!,
            getPermission("portals.modify.publish")!!,
            getPermission("portals.modify.link")!!,
        )

        val pluginCommand = getCommand("portals")!!
        pluginCommand.tabCompleter = command
        pluginCommand.setExecutor(command)
    }

    override fun reloadConfig() {
        super.reloadConfig()
        saveDefaultConfig()
        data.load()

        portalManager.reload()
    }

    override fun onDisable() {
        super.onDisable()

        portalManager.onDisable()

        data.save()
        saveConfig()
    }
}