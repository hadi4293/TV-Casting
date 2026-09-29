package com.hadii.tvcasing.streaming

/**
 * Extension points for future casting protocols.
 *
 * AirPlay 2 and Matter Casting are advertised in the README but are not
 * implemented. These hooks give a single place to wire them in later
 * without scattering protocol-specific code through the service.
 */
object CastingHooks {

    /**
     * Called when a new TV socket connects. Implement to advertise
     * AirPlay/Matter capabilities or register the receiver with a hub.
     */
    fun onReceiverConnected(socketId: String) {
        // TODO: AirPlay 2 — mDNS _airplay._tcp advertisement
        // TODO: Matter — commissionable node announcement
    }

    /**
     * Called when streaming starts. Could push a Now Playing update to
     * a smart-home bridge (e.g. update a Matter light or TV input).
     */
    fun onStreamStarted(url: String) {
        // TODO: Matter — set TV input to HDMI/Casting
    }

    /** Called when streaming stops. */
    fun onStreamStopped() {
        // TODO: AirPlay 2 — tear down session
        // TODO: Matter — revert input
    }

    /** Feature flags so UI can grey-out unsupported options. */
    val airPlaySupported: Boolean = false
    val matterSupported: Boolean = false
}
