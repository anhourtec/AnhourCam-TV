package com.anhourtec.camgrid.ipcam

/** Stream addresses built from a brand's usual RTSP path pattern. */
data class PresetUrls(
    val main: String,
    val sub: String?,
    val snapshot: String?,
)

/**
 * Common RTSP path patterns by brand, for cameras without ONVIF. Our own short list of widely
 * documented patterns; ONVIF ("find automatically") is always tried first in the dashboard.
 */
enum class BrandPreset(
    val label: String,
) {
    GENERIC_0_1("Generic (…/0 main, …/1 sub)") {
        override fun urls(
            host: String,
            channel: Int,
        ) = PresetUrls("rtsp://$host:554/0", "rtsp://$host:554/1", "http://$host/snap.jpg")
    },
    HIKVISION("Hikvision / HiLook / Annke") {
        override fun urls(
            host: String,
            channel: Int,
        ) = PresetUrls(
            "rtsp://$host:554/Streaming/Channels/${channel}01",
            "rtsp://$host:554/Streaming/Channels/${channel}02",
            "http://$host/ISAPI/Streaming/channels/${channel}01/picture",
        )
    },
    DAHUA("Dahua / Amcrest / Lorex") {
        override fun urls(
            host: String,
            channel: Int,
        ) = PresetUrls(
            "rtsp://$host:554/cam/realmonitor?channel=$channel&subtype=0",
            "rtsp://$host:554/cam/realmonitor?channel=$channel&subtype=1",
            "http://$host/cgi-bin/snapshot.cgi?channel=$channel",
        )
    },
    REOLINK("Reolink") {
        override fun urls(
            host: String,
            channel: Int,
        ) = "%02d".format(channel).let {
            PresetUrls("rtsp://$host:554/h264Preview_${it}_main", "rtsp://$host:554/h264Preview_${it}_sub", null)
        }
    },
    UNIVIEW("Uniview") {
        override fun urls(
            host: String,
            channel: Int,
        ) = PresetUrls("rtsp://$host:554/unicast/c$channel/s0/live", "rtsp://$host:554/unicast/c$channel/s1/live", null)
    },
    TAPO("TP-Link Tapo / VIGI") {
        override fun urls(
            host: String,
            channel: Int,
        ) = PresetUrls("rtsp://$host:554/stream1", "rtsp://$host:554/stream2", null)
    },
    AXIS("Axis") {
        override fun urls(
            host: String,
            channel: Int,
        ) = PresetUrls(
            "rtsp://$host/axis-media/media.amp?camera=$channel",
            "rtsp://$host/axis-media/media.amp?camera=$channel&resolution=640x360",
            "http://$host/axis-cgi/jpg/image.cgi?camera=$channel",
        )
    },
    ;

    /** [channel] is 1-based (NVR input or camera lens); single cameras use 1. */
    abstract fun urls(
        host: String,
        channel: Int,
    ): PresetUrls

    companion object {
        fun byId(id: String?): BrandPreset? = entries.firstOrNull { it.name == id }
    }
}
