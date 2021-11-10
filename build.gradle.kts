import kr.entree.spigradle.kotlin.spigot

plugins {
    kotlin("jvm") version "1.5.30"
    id("kr.entree.spigradle") version "2.2.4"
}

group = "dev.w1zzrd"
version = "1.0.2"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(spigot("1.17.1"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.5.30")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks.getByName<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin") {
    kotlinOptions {
        jvmTarget = "16"
    }
}

tasks.getByName<CopySpec>("prepareSpigot") {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.getByName<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileTestKotlin") {
    kotlinOptions {
        jvmTarget = "16"
    }
}

spigot {
    description = "Simple portals plugin"
    depends = listOf("Kotlin")
    load = kr.entree.spigradle.data.Load.STARTUP

    commands {
        create("portals") {
            aliases = listOf("p", "portal")
            description = "Create a portal"
            permission = "portals.create"
            permissionMessage = "You do not have permission to create portals"
        }
    }

    permissions {
        create("portals.create") {
            description = "Allows portal creation"
            defaults = "true"
        }

        create("portals.list") {
            description = "Allows listing portals"
            defaults = "true"
        }

        create("portals.list.other") {
            description = "Allows listing other players' portals"
            defaults = "op"
        }

        create("portals.invite") {
            description = "Allows inviting players to portals"
            defaults = "true"
        }

        create("portals.invite.other") {
            description = "Allows inviting players to another players portal"
            defaults = "true"
        }

        create("portals.tp") {
            description = "Allows teleporting to a portal"
            defaults = "true"
        }

        create("portals.tp.other") {
            description = "Allows teleporting to other players' portals"
            defaults = "op"
        }

        create("portals.info") {
            description = "Get info about a portal"
            defaults = "true"
        }

        create("portals.info.other") {
            description = "Get info about another player's portal"
            defaults = "op"
        }

        create("portals.modify.remove") {
            description = "Allows portal removal"
            defaults = "true"
        }

        create("portals.modify.edit") {
            description = "Allows portal position and orientation editing"
            defaults = "true"
        }

        create("portals.modify.link") {
            description = "Allows targeting/un-targeting a portal as a destination"
            defaults = "true"
        }

        create("portals.modify.allow") {
            description = "Allows another player to edit a portal"
            defaults = "true"
        }

        create("portals.modify.other") {
            description = "Allows modification of other players' portals"
            defaults = "op"
        }

        create("portals.modify.publish") {
            description = "Allows changing the publicity state of portals"
            defaults = "op"
        }

        create("portals.modify.*") {
            description = "Wildcard portal modification"
            defaults = "op"
            children = mapOf(
                "portals.modify.remove" to true,
                "portals.modify.edit" to true,
                "portals.modify.link" to true,
                "portals.modify.allow" to true,
                "portals.modify.other" to true,
                "portals.modify.publish" to true
            )
        }

        create("portals.*") {
            description = "Top-level wildcard"
            defaults = "op"
            children = mapOf(
                "portals.create" to true,
                "portals.list" to true,
                "portals.list.other" to true,
                "portals.tp" to true,
                "portals.tp.other" to true,
                "portals.info" to true,
                "portals.info.other" to true,
                "portals.modify.*" to true,
                "portals.invite" to true,
                "portals.invite.other" to true
            )
        }
    }

    debug {
        jvmArgs("-Xmx4G")
        buildVersion = "1.17.1"
    }
}