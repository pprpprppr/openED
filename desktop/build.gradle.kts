import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.sqlite.jdbc)
    implementation(libs.androidx.datastore.preferences.core)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

kotlin {
    jvmToolchain(21)
}

compose.desktop {
    application {
        mainClass = "org.opened.desktop.DesktopMainKt"
        jvmArgs("-Xmx1024m", "-Dfile.encoding=UTF-8")

        nativeDistributions {
            targetFormats(
                TargetFormat.Deb,
                TargetFormat.Exe
            )
            packageName = "openED"
            packageVersion = "1.9.0"
            description = "openED — Self-Learning & Knowledge Studio"
            vendor = "openED"

            // Complete JRE module set (verified with jdeps + TLS/SQLite/Swing requirements)
            modules(
                "java.desktop",
                "java.instrument",
                "java.logging",
                "java.management",
                "java.naming",
                "java.net.http",
                "java.security.jgss",
                "java.sql",
                "jdk.crypto.cryptoki",
                "jdk.crypto.ec",
                "jdk.unsupported",
                "jdk.zipfs"
            )

            linux {
                packageName = "opened"
                debMaintainer = "pprpprppr@users.noreply.github.com"
                menuGroup = "Education"
                shortcut = true
                iconFile.set(project.file("src/main/resources/icon.png"))
            }

            windows {
                menuGroup = "openED"
                shortcut = true
                menu = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "8d3e4f9a-5b2c-41a0-9e7d-3f8c2a1b6e40"
                iconFile.set(project.file("src/main/resources/icon.ico"))
            }
        }
    }
}
