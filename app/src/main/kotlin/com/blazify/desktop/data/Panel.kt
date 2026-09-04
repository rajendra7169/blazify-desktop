package com.blazify.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blazify.desktop.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.URLDecoder
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The desktop's own set of controls, wired to this player.
 *
 * A media key on a keyboard is not sent to whichever window has focus — it is
 * sent to the desktop, and the desktop hands it to whatever has said "I am
 * playing something". So has the panel applet, the lock screen, the widget on
 * a phone paired over bluetooth, and the volume rocker on a pair of
 * headphones. All of them ask the same question over the session bus, and an
 * application that never answers it is one where the play button on somebody's
 * keyboard does nothing at all.
 *
 * Answering costs one connection and a handful of properties. Nothing is
 * published beyond what any music player publishes: what is playing, whether
 * it is playing, and how to stop it.
 */
object Panel {

    private const val NAME = "org.mpris.MediaPlayer2.blazify"
    private const val PATH = "/org/mpris/MediaPlayer2"

    private var connection: org.freedesktop.dbus.connections.impl.DBusConnection? = null

    /**
     * Start answering, if there is anybody to answer.
     *
     * Only where a session bus exists. There is no equivalent to fail at on a
     * desktop that has none — the absence is ordinary, not an error, and a
     * player that refused to start over it would be worse than one whose media
     * keys are quiet.
     */
    /**
     * Whether to answer the desktop at all.
     *
     * On, because the media keys on a keyboard go through the same door: an
     * application that does not answer is one where that key does nothing.
     * Off is offered because the controls appear in the panel and the calendar
     * whether or not somebody wants them there, and a music player is not
     * entitled to a permanent seat on somebody's screen.
     */
    var on by mutableStateOf(
        runCatching { settings.readText().trim() != "false" }.getOrDefault(true),
    )
        private set

    private val settings: java.io.File get() = java.io.File(Store.folder, "panel")

    fun choose(value: Boolean) {
        on = value
        runCatching { settings.writeText(on.toString()) }
        if (on) start() else stop()
    }

    /** Whether the desktop is being answered at all. */
    var answering = false
        private set

    /**
     * Why it is not, when it is not.
     *
     * A failure here is silent by design — the media keys not working is not a
     * reason to refuse to play music — but silent to the person is not the
     * same as silent to whoever has to find out why, and the first version of
     * this threw the reason away.
     */
    var trouble: String? = null
        private set

    fun start() {
        if (!on) return
        if (connection != null) return
        if (System.getenv("DBUS_SESSION_BUS_ADDRESS").isNullOrBlank()) {
            trouble = "no session bus on this desktop"
            return
        }
        runCatching {
            val bus = DBusConnectionBuilder.forSessionBus().build()
            bus.requestBusName(NAME)
            bus.exportObject(PATH, Player())
            connection = bus
            answering = true
            trouble = null
        }.onFailure {
            trouble = "${it.javaClass.simpleName}: ${it.message}"
            // Said out loud, once. The media keys not working is not a reason
            // to refuse to play music, so this stays a warning — but a failure
            // nobody can see is a failure nobody can fix, and the first
            // version of this threw the reason away entirely.
            System.err.println("Blazify: the desktop's media controls are unavailable — $trouble")
        }
    }

    /**
     * Tell the desktop something changed.
     *
     * Nothing polls this — a panel applet asks once and then waits to be told,
     * which is the right way round for something that sits on screen all day.
     * Without the telling it would show whatever was playing when it first
     * looked, forever, and look broken rather than quiet.
     */
    fun changed() {
        val bus = connection ?: return
        runCatching {
            bus.sendMessage(
                Properties.PropertiesChanged(
                    PATH,
                    "org.mpris.MediaPlayer2.Player",
                    Player().GetAll("org.mpris.MediaPlayer2.Player"),
                    emptyList(),
                ),
            )
        }
    }

    fun stop() {
        runCatching { connection?.close() }
        connection = null
        answering = false
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Find what an address asks for and play it.
     *
     * A song id is pulled out of a link where there is one; everything else
     * becomes a search, and the first result is what plays. First rather than
     * a list, because the caller here is a voice or a script — there is nobody
     * present to choose from five.
     */
    internal fun open(uri: String) {
        val wanted = wanted(uri).takeIf { it.isNotBlank() } ?: return
        scope.launch {
            val id = videoId(uri)
            val found = if (id != null) {
                Catalogue.search(id).getOrNull()?.firstOrNull { it.id == id }
                    ?: Catalogue.search(wanted).getOrNull()?.firstOrNull()
            } else {
                Catalogue.search(wanted).getOrNull()?.firstOrNull()
            }
            found?.let { PlayerState.startRadio(it) }
        }
    }

    /** The words in an address, however it was written. */
    private fun wanted(uri: String): String {
        val text = uri.trim()
        val query = QUERY.find(text)?.groupValues?.get(1)
        val raw = query ?: text
            .removePrefix("blazify://")
            .removePrefix("blazify:")
            .substringBefore('?')
        return runCatching { URLDecoder.decode(raw.replace('+', ' '), "UTF-8") }.getOrDefault(raw)
            .removePrefix("search")
            .trim(' ', '/', ':')
    }

    /** The song a YouTube link points at, when it is one. */
    private fun videoId(uri: String): String? =
        VIDEO.find(uri)?.groupValues?.get(1)?.takeIf { it.length == 11 }

    private val QUERY = Regex("[?&]q=([^&]*)")
    private val VIDEO = Regex("(?:v=|youtu\\.be/|/watch/)([A-Za-z0-9_-]{11})")

    /**
     * What the desktop is told, and what it is allowed to ask for.
     *
     * One object answering to two interface names, which is how this protocol
     * is built: the application itself, and the player inside it.
     */
    // Public deliberately, and it is not an oversight. The bus calls these by
    // reflection from its own classloader, and a class kept private is a class
    // it is refused access to — which shows up as every property on the
    // interface failing rather than as anything about visibility.
    @DBusInterfaceName("org.mpris.MediaPlayer2.Player")
    class Player : DBusInterface, Properties {

        override fun getObjectPath() = PATH

        override fun isRemote() = false

        fun Next() = PlayerState.next()

        fun Previous() = PlayerState.previous()

        fun Pause() { if (PlayerState.playing) PlayerState.toggle() }

        fun Play() { if (!PlayerState.playing) PlayerState.toggle() }

        fun PlayPause() = PlayerState.toggle()

        fun Stop() { if (PlayerState.playing) PlayerState.toggle() }

        /** Asked for in microseconds, because this protocol was written in 2006. */
        fun Seek(by: Long) = PlayerState.nudge(by / 1_000_000.0)

        /**
         * Play something named from outside the app.
         *
         * The rest of this interface can only work the transport — press play,
         * skip, seek. Nothing in it could ever say *what* to play, which is
         * the one thing a voice assistant or a script actually wants: "play
         * this in Blazify" had no way in at all.
         *
         * Two kinds of address are understood. A link to a song is played
         * outright. Anything else is treated as words to search for, so
         * `blazify:search?q=purple%20rain` works, and so does handing over a
         * bare `blazify:purple rain`.
         *
         * Returns immediately: the bus is not kept waiting on the network, and
         * a caller that wants to know what happened can ask what is playing.
         */
        fun OpenUri(uri: String) = Panel.open(uri)

        // Handed back still wrapped. Unwrapping it first leaves the bus to
        // guess how to describe a bare map, which it cannot do — the metadata
        // is the one property here whose type has to be spelled out, and the
        // wrapper is where that spelling lives.
        @Suppress("UNCHECKED_CAST")
        override fun <A : Any?> Get(interfaceName: String, property: String): A =
            (all(interfaceName)[property] ?: Variant("")) as A

        override fun GetAll(interfaceName: String): MutableMap<String, Variant<*>> =
            all(interfaceName).toMutableMap()

        override fun <A : Any?> Set(interfaceName: String, property: String, value: A) = Unit

        private fun all(interfaceName: String): Map<String, Variant<*>> =
            if (interfaceName.endsWith(".Player")) player() else application()

        private fun application(): Map<String, Variant<*>> = mapOf(
            "Identity" to Variant("Blazify"),
            // The name of the file the package installs, which is how the
            // desktop finds the icon and the name to put beside the controls.
            // Not "blazify": the packager writes blazify-Blazify.desktop, and
            // a pointer to a file that is not there is a widget with a blank
            // square on it.
            "DesktopEntry" to Variant("blazify-Blazify"),
            "CanQuit" to Variant(false),
            "CanRaise" to Variant(false),
            "HasTrackList" to Variant(false),
            "SupportedUriSchemes" to Variant(arrayOf("blazify", "http", "https")),
            "SupportedMimeTypes" to Variant(arrayOf<String>()),
        )

        private fun player(): Map<String, Variant<*>> {
            val track = PlayerState.current
            return mapOf(
                "PlaybackStatus" to Variant(
                    when {
                        track == null -> "Stopped"
                        PlayerState.playing -> "Playing"
                        else -> "Paused"
                    },
                ),
                "CanGoNext" to Variant(true),
                "CanGoPrevious" to Variant(true),
                "CanPlay" to Variant(true),
                "CanPause" to Variant(true),
                "CanSeek" to Variant(true),
                "CanControl" to Variant(true),
                "Volume" to Variant(PlayerState.volume.toDouble()),
                "Position" to Variant((PlayerState.positionSeconds * 1_000_000).toLong()),
                "Metadata" to Variant(metadata(track), "a{sv}"),
            )
        }

        /**
         * What is playing, in the names this protocol uses.
         *
         * The artwork goes with it: a notification with a title and a grey
         * square is the desktop telling somebody a song is on without telling
         * them which.
         */
        private fun metadata(track: Track?): MutableMap<String, Variant<*>> {
            if (track == null) return mutableMapOf()
            val fields = mutableMapOf<String, Variant<*>>(
                "mpris:trackid" to Variant(
                    org.freedesktop.dbus.DBusPath("/org/mpris/MediaPlayer2/track/" + track.id.filter { it.isLetterOrDigit() }),
                ),
                "xesam:title" to Variant(track.title),
                "xesam:artist" to Variant(arrayOf(track.artist)),
            )
            track.durationSeconds?.let { fields["mpris:length"] = Variant(it * 1_000_000L) }
            track.thumbnail?.let { fields["mpris:artUrl"] = Variant(it) }
            return fields
        }
    }
}
