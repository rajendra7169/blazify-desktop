import org.jetbrains.compose.desktop.application.dsl.TargetFormat


plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":innertube"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.components.resources)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.okhttp)

    // Audio. The catalogue serves fragmented MP4, which the lighter JVM media
    // libraries can't open at all — this one plays it without complaint, along
    // with every other container we're ever likely to meet.
    implementation(libs.vlcj)

    // Artwork, fetched and cached on both sides of the window.
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
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

tasks.register<JavaExec>("homeProbe") {
    group = "verification"
    mainClass.set("com.blazify.desktop.tools.HomeProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("openProbe") {
    group = "verification"
    mainClass.set("com.blazify.desktop.tools.OpenProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("feedProbe") {
    group = "verification"
    mainClass.set("com.blazify.desktop.tools.FeedProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("downloadProbe") {
    group = "verification"
    mainClass.set("com.blazify.desktop.tools.DownloadProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("localScanProbe") {
    group = "verification"
    mainClass.set("com.blazify.desktop.tools.LocalScanProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("lyricsProbe") {
    group = "verification"
    mainClass.set("com.blazify.desktop.tools.LyricsProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("discoverProbe") {
    group = "verification"
    mainClass.set("com.blazify.desktop.tools.DiscoverProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}
