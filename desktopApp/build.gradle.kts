import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}

kotlin {
    jvm()
    sourceSets {
        val jvmMain by getting  {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(project(":shared"))
                // Potřebné pro Dispatchers.Main na Desktop — mapuje Main na Swing EDT
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0-RC2")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"

        // Oprava češtiny v terminálu: vynutí UTF-8 kódování pro stdout/stderr na Windows
        jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ZakazkyDesktop"
            packageVersion = "1.0.0"
        }
    }
}
