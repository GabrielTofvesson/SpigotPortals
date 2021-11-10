import kr.entree.spigradle.annotations.SpigotPlugin
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.io.File

private const val PATH_AUTOSAVE = "autoSaveMinutesInterval"
private const val AUTOSAVE_DEFAULT = 240L


@SpigotPlugin
class PortalsPlugin: JavaPlugin() {
    private val data = YamlFile.loadFile(File(dataFolder, "data.yml"))
    private val portalManager = PortalManager(data) { config }
    private var autoSaveTask: BukkitTask? = null

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

        startAutoSaver()
    }

    override fun reloadConfig() {
        super.reloadConfig()
        saveDefaultConfig()
        data.load()

        portalManager.reload()
    }

    private fun cancelAutoSaver() {
        val task = autoSaveTask
        if (task != null) {
            Bukkit.getScheduler().cancelTask(task.taskId)
            autoSaveTask = null
        }
    }

    private fun startAutoSaver() {
        if (autoSaveTask != null)
            cancelAutoSaver()

        val interval = config.getLong(PATH_AUTOSAVE, AUTOSAVE_DEFAULT)
        autoSaveTask = Bukkit.getScheduler().runTaskTimer(
            this,
            Runnable {
                Bukkit.getLogger().info("Triggered auto-save")
                portalManager.save()
                data.save()
                Bukkit.getLogger().info("Auto-save complete")
            },
            interval,
            interval
        )
    }

    override fun onDisable() {
        super.onDisable()

        cancelAutoSaver()

        portalManager.onDisable()

        data.save()
        saveConfig()
    }
}