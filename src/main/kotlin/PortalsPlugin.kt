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