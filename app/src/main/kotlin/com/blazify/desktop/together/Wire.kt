package com.blazify.desktop.together

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * What the two sides say to each other.
 *
 * Every name here is the server's, not ours — a room can hold a desktop and a
 * different client at once, so the wire is a shared language and none of it is
 * free to be
 * tidied. Kept as plain strings in one place so that reading the protocol means
 * reading this file rather than grepping for quoted words.
 */
object Say {
    // Ours to say.
    const val CREATE_ROOM = "create_room"
    const val JOIN_ROOM = "join_room"
    const val LEAVE_ROOM = "leave_room"
    const val APPROVE_JOIN = "approve_join"
    const val REJECT_JOIN = "reject_join"
    const val PLAYBACK_ACTION = "playback_action"
    const val KICK_USER = "kick_user"
    const val TRANSFER_HOST = "transfer_host"
    const val PING = "ping"
    const val REQUEST_SYNC = "request_sync"
    const val RECONNECT = "reconnect"
    const val SUGGEST_TRACK = "suggest_track"
    const val APPROVE_SUGGESTION = "approve_suggestion"
    const val REJECT_SUGGESTION = "reject_suggestion"

    // Theirs.
    const val ROOM_CREATED = "room_created"
    const val JOIN_REQUEST = "join_request"
    const val JOIN_APPROVED = "join_approved"
    const val JOIN_REJECTED = "join_rejected"
    const val USER_JOINED = "user_joined"
    const val USER_LEFT = "user_left"
    const val SYNC_PLAYBACK = "sync_playback"
    const val ERROR = "error"
    const val PONG = "pong"
    const val HOST_CHANGED = "host_changed"
    const val KICKED = "kicked"
    const val SYNC_STATE = "sync_state"
    const val RECONNECTED = "reconnected"
    const val USER_RECONNECTED = "user_reconnected"
    const val USER_DISCONNECTED = "user_disconnected"
    const val SUGGESTION_RECEIVED = "suggestion_received"
    const val SUGGESTION_APPROVED = "suggestion_approved"
    const val SUGGESTION_REJECTED = "suggestion_rejected"
}

/** The things a host can do that everyone else has to be told about. */
object Did {
    const val PLAY = "play"
    const val PAUSE = "pause"
    const val SEEK = "seek"
    const val SKIP_NEXT = "skip_next"
    const val SKIP_PREV = "skip_prev"
    const val CHANGE_TRACK = "change_track"
    const val SYNC_QUEUE = "sync_queue"
}

/**
 * Where rooms live.
 *
 * A community server rather than one of ours — which is the point of not
 * inventing our own: a room is only worth having if the people you want in it
 * can already reach it.
 */
object Servers {
    /** Where rooms are made unless you say otherwise. */
    const val DEFAULT = "wss://metroserverx.meowery.eu/ws"
}
