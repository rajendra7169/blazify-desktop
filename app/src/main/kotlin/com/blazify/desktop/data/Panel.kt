package com.blazify.desktop.data

import com.blazify.desktop.PlayerState
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
    fun start() {
        if (connection != null) return
        if (System.getenv("DBUS_SESSION_BUS_ADDRESS").isNullOrBlank()) return
        runCatching {
            val bus = DBusConnectionBuilder.forSessionBus().build()
            bus.requestBusName(NAME)
            bus.exportObject(PATH, Player())
            connection = bus
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
    }

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
            "DesktopEntry" to Variant("blazify"),
            "CanQuit" to Variant(false),
            "CanRaise" to Variant(false),
            "HasTrackList" to Variant(false),
            "SupportedUriSchemes" to Variant(arrayOf<String>()),
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
