import kr.entree.spigradle.annotations.SpigotPlugin
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

@SpigotPlugin
class PortalsPlugin: JavaPlugin() {
    private val data = YamlFile.loadFile(File(dataFolder, "data.yml"))
    private val portalManager = PortalManager(data) { config }

    override fun onEnable() {
        super.onEnable()

        saveDefaultConfig()
        data.load()
    }

    override fun onDisable() {
        super.onDisable()

        data.save()
        saveConfig()
    }
}