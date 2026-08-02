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
            copyright = "Blazify Project (C) 2026"

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
                appCategory = "Audio"
                debMaintainer = "rajendrapandey199971@gmail.com"
                shortcut = true
            }
        }
    }
}


/**
 * Declare the native audio library the finished package needs.
 *
 * The packager works out dependencies by looking at what the bundled files link
 * against, and the audio library is opened by name at runtime rather than
 * linked, so it looks at the package and sees nothing. Installing without it
 * gives someone an application that starts, browses, and then refuses to make a
 * sound — so it is written into the control file after the fact.
 */
val declareAudioDependency by tasks.registering {
    val debDir = layout.buildDirectory.dir("compose/binaries/main/deb")
    val workDir = layout.buildDirectory.dir("repack")
    outputs.upToDateWhen { false }
    doLast {
        val deb = debDir.get().asFile.listFiles()?.firstOrNull { it.extension == "deb" } ?: return@doLast
        val work = workDir.get().asFile.apply { deleteRecursively(); mkdirs() }

        // Everything here is plain process work rather than the build tool's
        // own helpers, which can't be carried in a cached configuration.
        fun shell(vararg command: String) {
            val process = ProcessBuilder(*command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor() == 0) { "${command.first()} failed: $output" }
        }

        shell("dpkg-deb", "-R", deb.absolutePath, work.absolutePath)
        val control = File(work, "DEBIAN/control")
        control.writeText(
            control.readLines().joinToString("\n") { line ->
                if (line.startsWith("Depends:")) "$line, libvlc5, vlc-plugin-base" else line
            } + "\n",
        )
        shell("dpkg-deb", "-b", work.absolutePath, deb.absolutePath)
        println("declared libvlc5 and vlc-plugin-base in ${deb.name}")
    }
}

// Matched rather than named: the packaging tasks are registered by the
// packaging plugin after this file is read, so asking for one by name here
// finds nothing.
tasks.matching { it.name == "packageDeb" }.configureEach { finalizedBy(declareAudioDependency) }

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

tasks.register<JavaExec>("colourProbe") {
    group = "verification"
    mainClass.set("com.blazify.desktop.tools.ColourProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("exploreProbe") {
    group = "verification"
    mainClass.set("com.blazify.desktop.tools.ExploreProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("moodProbe") {
    group = "verification"
    mainClass.set("com.blazify.desktop.tools.MoodProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("searchProbe") {
    group = "verification"
    mainClass.set("com.blazify.desktop.tools.SearchProbeKt")
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
