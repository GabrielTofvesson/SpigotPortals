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
            description.permissions.first { it.name == "portals.create" },
            description.permissions.first { it.name == "portals.modify.remove" },
            description.permissions.first { it.name == "portals.modify.other" },
            description.permissions.first { it.name == "portals.invite" },
            description.permissions.first { it.name == "portals.invite.other" },
            description.permissions.first { it.name == "portals.list.other" },
            description.permissions.first { it.name == "portals.tp" },
            description.permissions.first { it.name == "portals.tp.other" },
            description.permissions.first { it.name == "portals.info" },
            description.permissions.first { it.name == "portals.info.other" },
            description.permissions.first { it.name == "portals.modify.edit" },
            description.permissions.first { it.name == "portals.modify.publish" }
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