import org.jetbrains.compose.desktop.application.dsl.TargetFormat

val desktopProductionVersion = providers.gradleProperty("pos.desktop.version").get()

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvm("desktop")
    jvmToolchain(17)

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(project(":sharedLogic"))
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(libs.sqlite.jdbc)
                implementation(libs.jna.platform)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(compose.desktop.uiTestJUnit4)
            }
        }
    }
}

compose.desktop {
    application {
        from(kotlin.targets["desktop"])
        mainClass = "ma.elaroui.pos.desktop.MainKt"

        nativeDistributions {
            // Compose/jpackage builds only the formats supported by the current host. Windows
            // keeps EXE/MSI; Ubuntu builds the DEB from the same shared desktop application.
            targetFormats(TargetFormat.Exe, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "PATISSERIE_POS"
            packageVersion = desktopProductionVersion
            description = "Système de caisse enregistreuse et gestion pour Pâtisserie (Offline-first)"
            vendor = "PATISSERIE_POS"
            // SQLite JDBC is opened during startup. Gradle runs use a full JDK,
            // while native distributions use this trimmed runtime image.
            modules("jdk.accessibility", "java.sql")
            jvmArgs += listOf(
                "-DgeneralPos.version=$desktopProductionVersion",
                "-XX:+UseG1GC",
                "-XX:MaxGCPauseMillis=50",
                "-Xms64m",
                "-Xmx512m"
            )

            windows {
                iconFile.set(project.file("src/desktopMain/resources/pos_app_icon.ico"))
                perUserInstall = true
                menuGroup = "PATISSERIE_POS"
                upgradeUuid = "46788696-3912-4d20-9f7d-220b62935d31"
            }

            linux {
                packageName = "patisserie-pos"
                iconFile.set(project.file("../app/src/main/res/drawable-nodpi/pos_app_icon.png"))
                debMaintainer = "support@elaroui-pos.local"
                menuGroup = "Office"
                appCategory = "Office"
                shortcut = true
            }
        }
    }
}



