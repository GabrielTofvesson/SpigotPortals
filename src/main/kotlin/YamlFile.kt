import org.bukkit.configuration.Configuration
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class YamlFile private constructor(
    private val file: File,
    private val yamlConfiguration: YamlConfiguration = YamlConfiguration.loadConfiguration(file)
): ConfigurationSection, Configuration by yamlConfiguration {
    companion object {
        fun loadFile(file: File) = YamlFile(file)
    }

    fun load() {
        if (file.isFile) yamlConfiguration.load(file)
    }

    fun save() = yamlConfiguration.save(file)
}