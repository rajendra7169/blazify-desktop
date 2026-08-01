import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// The media libraries ship one native jar per platform. Pick the one for
// whatever machine is building, which is also the machine that will run it —
// installers have to be produced on their own platform anyway.
val fxPlatform = when {
    System.getProperty("os.name").startsWith("Windows") -> "win"
    System.getProperty("os.name").contains("Mac") -> "mac"
    else -> "linux"
}

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}

dependencies {
    implementation(project(":innertube"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.components.resources)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.ktor.client.okhttp)

    // Audio. Chosen over a native player so the installer stays self-contained:
    // these jars carry their own native code, so nothing has to be installed
    // alongside the app.
    implementation(variantOf(libs.javafx.base) { classifier(fxPlatform) })
    implementation(variantOf(libs.javafx.graphics) { classifier(fxPlatform) })
    implementation(variantOf(libs.javafx.media) { classifier(fxPlatform) })
}

compose.desktop {
    application {
        mainClass = "com.blazify.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.AppImage, TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Blazify"
            packageVersion = "1.0.0"
            description = "A music player"
            vendor = "Rajendra Pandey"

            windows {
                menuGroup = "Blazify"     // Start menu entry
                shortcut = true           // desktop shortcut
                dirChooser = true         // let people pick the install folder
                perUserInstall = true     // no administrator password needed
                // Fixed, and it must stay fixed: this is what tells Windows an
                // install is an UPGRADE rather than a second copy alongside the first.
                upgradeUuid = "0d8f2b41-6c3e-4f7a-9b52-3ac1e8d47f60"
            }

            linux {
                menuGroup = "Audio"       // lands under Sound & Video
                packageName = "blazify"
            }
        }
    }
}


// A way to query the catalogue from a terminal without launching the window —
// far quicker than clicking through the UI when a parser needs checking.
//   ./gradlew :app:probe --args="let her go"
tasks.register<JavaExec>("probe") {
    group = "verification"
    description = "Search the catalogue and resolve a stream, from the terminal"
    mainClass.set("com.blazify.desktop.tools.ProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("playProbe") {
    group = "verification"
    description = "Resolve a stream and try to play it, printing what the engine reports"
    mainClass.set("com.blazify.desktop.tools.PlayProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("localPlayProbe") {
    group = "verification"
    mainClass.set("com.blazify.desktop.tools.LocalPlayProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}
