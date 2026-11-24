package com.metrolist.music.playback.audio

/**
 * A read-only audio-level signal for the optional player visualization.
 * The audio thread publishes a sampled peak; it never waits on the UI.
 */
object BrainAudioLevel {
    @Volatile var active: Boolean = false
    @Volatile var peak: Float = 0f
}
