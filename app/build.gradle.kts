import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.process.ExecOperations
import java.net.URI
import javax.inject.Inject
import java.util.zip.ZipFile


/**
 * Turn `proto/together.proto` into Java before anything compiles.
 *
 * The rooms this app joins are shared with other Blazify clients, so the wire
 * format is not ours to invent — it belongs to the server, and the .proto file
 * is the only honest description of it. Generating from that file means a
 * change to the protocol is a one-line change here rather than a hunt through
 * hand-written parsing.
 *
 * protoc is fetched once into the build directory. It is a native binary per
 * platform, which is why it can't simply be a dependency.
 */
abstract class GenerateProtoTask : DefaultTask() {
    @get:InputFile
    abstract val protoSourceFile: RegularFileProperty

    @get:OutputDirectory
    abstract val generatedSourcesDir: DirectoryProperty

    @get:Input
    abstract val protocUrl: Property<String>

    @get:Internal
    abstract val protocExecutable: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun generate() {
        val proto = protoSourceFile.get().asFile
        val out = generatedSourcesDir.get().asFile
        val protoc = protocExecutable.get().asFile
        out.mkdirs()

        if (!protoc.exists() || protoc.length() == 0L) {
            val url = protocUrl.get()
            logger.lifecycle("Fetching ${url.substringAfterLast('/')}")
            protoc.parentFile.mkdirs()
            val connection = URI(url).toURL().openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("User-Agent", "Blazify/1.0")
            if (connection.responseCode !in 200..299) {
                throw GradleException("protoc download failed: HTTP ${connection.responseCode} for $url")
            }
            connection.inputStream.use { input -> protoc.outputStream().use(input::copyTo) }
            protoc.setExecutable(true)
        }

        execOperations.exec {
            executable = protoc.absolutePath
            args("--java_out=lite:$out", "-I=${proto.parentFile}", proto.absolutePath)
        }
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.serialization)
}

val protocVersion = libs.versions.protobuf.get()

val protocDownloadUrl: String = run {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val platform = when {
        os.contains("linux") -> "linux"
        os.contains("mac") || os.contains("darwin") -> "osx"
        os.contains("windows") -> "windows"
        else -> "linux"
    }
    val chip = when {
        arch.contains("x86_64") || arch.contains("amd64") -> "x86_64"
        arch.contains("aarch64") || arch.contains("arm64") -> "aarch_64"
        else -> "x86_64"
    }
    "https://repo1.maven.org/maven2/com/google/protobuf/protoc/" +
        "$protocVersion/protoc-$protocVersion-$platform-$chip.exe"
}

val generatedProto = layout.buildDirectory.dir("generated/proto")

val generateProto = tasks.register<GenerateProtoTask>("generateProto") {
    group = "build"
    description = "Generate the Blaze Together wire format from proto/together.proto"
    protoSourceFile.set(rootProject.file("proto/together.proto"))
    generatedSourcesDir.set(generatedProto)
    protocUrl.set(protocDownloadUrl)
    protocExecutable.set(
        layout.buildDirectory.file("protoc/${protocDownloadUrl.substringAfterLast('/')}"),
    )
}

sourceSets["main"].java.srcDir(generatedProto)

tasks.withType<JavaCompile>().configureEach { dependsOn(generateProto) }
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(generateProto)
}

dependencies {
    implementation(project(":innertube"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.components.resources)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)

    // Reading the cookie store browsers keep, which is an ordinary SQLite file.
    implementation(libs.sqlite.jdbc)

    // Turning any script into the Latin alphabet. Writing this by hand means a
    // table per language and getting every one of them slightly wrong.
    implementation(libs.icu4j)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.websockets)
    implementation(libs.protobuf.javalite)

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

            // Files copied in beside the application. The Windows folder holds
            // the audio library, so a Windows install needs nothing else.
            appResourcesRootDir.set(layout.projectDirectory.dir("resources"))
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
 * Fetch the audio library for Windows and put it beside the application.
 *
 * Linux installs declare a dependency and the package manager handles it.
 * Windows has no such mechanism, so the alternative is telling every person who
 * downloads this to go and install a media player first — which is not an
 * answer. The library is redistributable and licensed compatibly, so it travels
 * with the application instead.
 *
 * Only the parts that decode and play audio are kept. The full download carries
 * video decoders, subtitle renderers and interface skins that a music player
 * will never open, and they are most of its size.
 */
val fetchWindowsAudio by tasks.registering {
    val version = "3.0.21"
    val into = layout.projectDirectory.dir("resources/windows/vlc").asFile
    val cache = layout.buildDirectory.file("vlc-$version-win64.zip").get().asFile

    outputs.dir(into)

    doLast {
        // Made whether or not anything is fetched: the packager copies this
        // folder, and a folder that isn't there fails the copy rather than
        // being treated as empty.
        into.mkdirs()

        if (File(into, "libvlc.dll").exists()) {
            println("the Windows audio library is already here")
            return@doLast
        }

        if (!cache.exists() || cache.length() < 1_000_000) {
            cache.parentFile.mkdirs()
            println("fetching the Windows audio library once (~78 MB)")
            URI("https://get.videolan.org/vlc/$version/win64/vlc-$version-win64.zip")
                .toURL().openStream().use { input ->
                    cache.outputStream().use { output -> input.copyTo(output) }
                }
        }

        // Everything a music player reaches for, and nothing it doesn't.
        val wanted = setOf("access", "audio_filter", "audio_output", "codec", "demux", "misc", "stream_filter")

        // Video encoders, subtitle renderers, screen capture and remote-desktop
        // protocols all ship in those same folders and are most of their size.
        // A music player will never open one.
        val unwanted = listOf(
            "x264", "x265", "vpx", "theora", "daala", "schroedinger", "libass",
            "subsdec", "subsusf", "svcdsub", "cvdsub", "dvbsub", "substx3g", "scte",
            "png", "jpeg", "svg", "bpg", "sdl_image", "zvbi",
            "vnc", "rdp", "screen", "dshow", "decklink", "v4l", "dc1394", "dv1394",
            "srt", "rist", "satip", "shm", "vcd", "dvdnav", "dvdread", "bluray",
            "mkv", "avi", "asf", "ogg", "ts_", "libts", "mjpeg", "rawvid", "y4m",
        )

        ZipFile(cache).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                if (entry.isDirectory) return@forEach
                val path = entry.name.substringAfter("vlc-$version/", "")
                val keep = when {
                    path == "libvlc.dll" || path == "libvlccore.dll" -> path
                    path.startsWith("plugins/") -> {
                        val group = path.removePrefix("plugins/").substringBefore('/')
                        val file = path.substringAfterLast('/')
                        if (group in wanted && unwanted.none { it in file }) path else null
                    }
                    else -> null
                } ?: return@forEach

                val target = File(into, keep)
                target.parentFile.mkdirs()
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
        val size = into.walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1_000_000
        println("bundled the audio library for Windows (${size} MB)")
    }
}

// prepareAppResources is the task that actually copies the folder, and it runs
// well before packaging — depending only on the packaging tasks let it run
// first and fail on a folder that didn't exist yet.
tasks.matching {
    it.name in setOf("prepareAppResources", "packageMsi", "packageExe", "createDistributable")
}.configureEach { dependsOn(fetchWindowsAudio) }

/**
 * Declare what the finished package needs but doesn't link against.
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
                if (line.startsWith("Depends:")) "$line, libvlc5, vlc-plugin-base, libsecret-tools" else line
            } + "\n",
        )
        shell("dpkg-deb", "-b", work.absolutePath, deb.absolutePath)
        println("declared libvlc5, vlc-plugin-base and libsecret-tools in ${deb.name}")
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
    mainClass.set("com.blazify.desktop.tools.stream.ProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

// Ask every lyrics source for one song and print what each answered.
//   ./gradlew :app:sourceProbe --args="Kesariya|Arijit Singh|268"
tasks.register<JavaExec>("sourceProbe") {
    group = "verification"
    description = "Ask every lyrics source for one song, from the terminal"
    mainClass.set("com.blazify.desktop.tools.lyrics.SourceProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("sessionProbe") {
    group = "verification"
    description = "Show which cookies each browser gives, and whether the catalogue accepts them"
    mainClass.set("com.blazify.desktop.tools.session.SessionProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("resumeCheck") {
    group = "verification"
    mainClass.set("com.blazify.desktop.tools.resume.ResumeCheckKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("podcastProbe") {
    group = "verification"
    description = "Show what the catalogue gives back for podcasts and whether an episode plays"
    mainClass.set("com.blazify.desktop.tools.podcast.PodcastProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("rawProbe") {
    group = "verification"
    description = "Ask the catalogue by hand and report the shape of what comes back"
    mainClass.set("com.blazify.desktop.tools.session.RawProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("soundProbe") {
    group = "verification"
    description = "Resolve one song and play it, printing what the engine reports"
    mainClass.set("com.blazify.desktop.tools.sound.SoundProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("longProbe") {
    group = "verification"
    description = "Show what each source offers for one recording, and how fast"
    mainClass.set("com.blazify.desktop.tools.longsong.LongProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("roomProbe") {
    group = "verification"
    description = "Open a Blaze Together room from the terminal and print the traffic"
    mainClass.set("com.blazify.desktop.tools.room.RoomProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("libraryProbe") {
    group = "verification"
    description = "Print what the signed-in account says its library holds"
    mainClass.set("com.blazify.desktop.tools.library.LibraryProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("playProbe") {
    group = "verification"
    description = "Resolve a stream and try to play it, printing what the engine reports"
    mainClass.set("com.blazify.desktop.tools.stream.PlayProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("localPlayProbe") {
    group = "verification"
    mainClass.set("com.blazify.desktop.tools.localplay.LocalPlayProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("homeProbe") {
    group = "verification"
    mainClass.set("com.blazify.desktop.tools.home.HomeProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("openProbe") {
    group = "verification"
    mainClass.set("com.blazify.desktop.tools.open.OpenProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("feedProbe") {
    group = "verification"
    mainClass.set("com.blazify.desktop.tools.feed.FeedProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("likedProbe") {
    group = "verification"
    mainClass.set("com.blazify.desktop.tools.liked.LikedProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("accountProbe") {
    group = "verification"
    mainClass.set("com.blazify.desktop.tools.account.AccountProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("picksProbe") {
    group = "verification"
    mainClass.set("com.blazify.desktop.tools.picks.PicksProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("colourProbe") {
    group = "verification"
    mainClass.set("com.blazify.desktop.tools.colour.ColourProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("exploreProbe") {
    group = "verification"
    mainClass.set("com.blazify.desktop.tools.explore.ExploreProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("moodProbe") {
    group = "verification"
    mainClass.set("com.blazify.desktop.tools.mood.MoodProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("searchProbe") {
    group = "verification"
    mainClass.set("com.blazify.desktop.tools.search.SearchProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("downloadProbe") {
    group = "verification"
    mainClass.set("com.blazify.desktop.tools.download.DownloadProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("localScanProbe") {
    group = "verification"
    mainClass.set("com.blazify.desktop.tools.localscan.LocalScanProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("lyricsProbe") {
    group = "verification"
    mainClass.set("com.blazify.desktop.tools.lyrics.LyricsProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("discoverProbe") {
    group = "verification"
    mainClass.set("com.blazify.desktop.tools.discover.DiscoverProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}
