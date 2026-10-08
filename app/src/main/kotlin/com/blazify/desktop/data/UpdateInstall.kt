package com.blazify.desktop.data

import com.blazify.desktop.PlayerState
import java.io.File
import kotlin.system.exitProcess

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * Puts a downloaded version in place of this one, the way this copy was installed.
 *
 * Blazify closes first: a program cannot be replaced while it runs, and on
 * Windows its files are locked until it stops. What does the replacing is the
 * system's own installer — the package manager for a .deb, which asks for the
 * password in its usual window, or the setup program on Windows — so the result
 * is exactly what installing by hand would have left. A copy run from a single
 * AppImage file is simply swapped for the new file. Afterwards Blazify opens
 * again by itself.
 */
object UpdateInstall {

    /** How this copy came to be on the machine, which decides what replaces it. */
    enum class Kind { Deb, AppImage, WindowsSetup, Unknown }

    private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    /** The program that started this copy. */
    private val launcher: String? by lazy {
        System.getProperty("jpackage.app-path")
            ?: ProcessHandle.current().info().command().orElse(null)
    }

    val kind: Kind by lazy {
        when {
            windows -> Kind.WindowsSetup
            System.getenv("APPIMAGE") != null -> Kind.AppImage
            launcher?.startsWith("/opt/blazify/") == true -> Kind.Deb
            else -> Kind.Unknown
        }
    }

    /** The file of a release that fits this copy, or null when it has to be updated by hand. */
    fun pick(files: List<Updates.Download>): Updates.Download? =
        when (kind) {
            Kind.Deb -> files.firstOrNull { it.name.endsWith(".deb", ignoreCase = true) }
            Kind.AppImage -> files.firstOrNull { it.name.endsWith(".AppImage", ignoreCase = true) }
            Kind.WindowsSetup -> files.firstOrNull { it.name.endsWith(".exe", ignoreCase = true) }
            Kind.Unknown -> null
        }

    /**
     * Starts putting [file] in and closes Blazify. Returns a message only when it could not
     * start, in which case Blazify stays open.
     */
    fun start(file: File): String? {
        val started =
            runCatching {
                when (kind) {
                    Kind.Deb -> afterClosing(file, debSteps(file))
                    Kind.AppImage -> afterClosing(file, appImageSteps(file))
                    Kind.WindowsSetup -> windowsSetup(file)
                    Kind.Unknown -> return "This copy has to be updated by hand: the file is in ${file.parent}."
                }
            }
        started.exceptionOrNull()?.let { return "Couldn't start the installer: ${it.message ?: "no reason given"}." }
        // Written before the process goes, so the new version opens on exactly what this one closed on.
        runCatching { PlayerState.remember() }
        Thread.sleep(300)
        exitProcess(0)
    }

    private fun debSteps(file: File): String {
        if (File("/usr/bin/pkexec").exists().not() && File("/bin/pkexec").exists().not()) {
            error("there is nothing here to ask for the password. Install it with: sudo apt install \"${file.absolutePath}\"")
        }
        // The package manager asks for the password in its own window, installs, and the new
        // version opens. Should the password be refused, the old version opens again instead.
        return """
            pkexec env DEBIAN_FRONTEND=noninteractive apt-get install -y --allow-downgrades ${quote(file.absolutePath)}
            exec ${quote(launcher ?: "/opt/blazify/bin/Blazify")}
        """.trimIndent()
    }

    private fun appImageSteps(file: File): String {
        val current = System.getenv("APPIMAGE") ?: error("this AppImage didn't say where it is")
        return """
            chmod +x ${quote(file.absolutePath)}
            mv -f ${quote(file.absolutePath)} ${quote(current)}
            exec ${quote(current)}
        """.trimIndent()
    }

    /**
     * Runs [steps] in a shell of its own once this process has gone, so it outlives it. What
     * the shell says goes in a log beside the download, for when something does not take.
     */
    private fun afterClosing(file: File, steps: String) {
        val pid = ProcessHandle.current().pid()
        val script = File(file.parentFile, "install.sh")
        script.writeText(
            "#!/bin/sh\n" +
                "while kill -0 $pid 2>/dev/null; do sleep 0.3; done\n" +
                steps + "\n",
        )
        script.setExecutable(true)
        val log = File(file.parentFile, "install.log")
        val shell = if (File("/usr/bin/setsid").exists()) listOf("/usr/bin/setsid", "/bin/sh") else listOf("/bin/sh")
        ProcessBuilder(shell + script.absolutePath)
            .redirectOutput(log)
            .redirectErrorStream(true)
            .start()
    }

    private fun quote(text: String) = "'" + text.replace("'", "'\\''") + "'"

    /**
     * On Windows: once Blazify has closed, the setup runs, and when it has finished Blazify is
     * opened again. A hidden PowerShell does the waiting, so it outlives this process; its steps
     * are given on the command line rather than in a script file, which a locked-down machine
     * may refuse to run.
     */
    private fun windowsSetup(file: File) {
        val pid = ProcessHandle.current().pid()
        val app = launcher?.takeIf { it.endsWith(".exe", ignoreCase = true) }
        fun ps(text: String) = "'" + text.replace("'", "''") + "'"
        val steps = buildString {
            append("while (Get-Process -Id $pid -ErrorAction SilentlyContinue) { Start-Sleep -Milliseconds 300 }; ")
            append("Start-Process -FilePath ${ps(file.absolutePath)} -Wait; ")
            if (app != null) append("if (Test-Path ${ps(app)}) { Start-Process -FilePath ${ps(app)} }")
        }
        ProcessBuilder(
            "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
            "-Command", steps,
        ).directory(file.parentFile).start()
    }
}
