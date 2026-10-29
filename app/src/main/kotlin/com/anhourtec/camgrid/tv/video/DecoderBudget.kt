package com.anhourtec.camgrid.tv.video

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build

/**
 * Hands out HEVC decoder instances within what the device declares, hardware first.
 * Software decoders are capped separately because they compete for the CPU.
 */
class DecoderBudget(
    private val softwareLimit: Int = DEFAULT_SOFTWARE_LIMIT,
) {
    data class Decoder(
        val name: String,
        val hardware: Boolean,
        val maxInstances: Int,
    )

    val decoders: List<Decoder> =
        MediaCodecList(MediaCodecList.REGULAR_CODECS)
            .codecInfos
            .filter { !it.isEncoder && MIME in it.supportedTypes && !it.name.endsWith(".secure") }
            .map { Decoder(it.name, isHardware(it), it.getCapabilitiesForType(MIME).maxSupportedInstances) }
            .sortedByDescending { it.hardware }

    private val inUse = mutableMapOf<String, Int>()

    /** Reserves a decoder, or returns null when the budget is exhausted (show a snapshot instead). */
    @Synchronized
    fun acquire(allowSoftware: Boolean = true): Slot? {
        val softwareInUse = decoders.filter { !it.hardware }.sumOf { inUse[it.name] ?: 0 }
        val decoder =
            decoders.firstOrNull { d ->
                val free = (inUse[d.name] ?: 0) < d.maxInstances
                free && (d.hardware || (allowSoftware && softwareInUse < softwareLimit))
            } ?: return null
        inUse[decoder.name] = (inUse[decoder.name] ?: 0) + 1
        return Slot(decoder)
    }

    inner class Slot internal constructor(
        val decoder: Decoder,
    ) {
        private var released = false

        fun release() {
            synchronized(this@DecoderBudget) {
                if (released) return
                released = true
                inUse[decoder.name] = (inUse[decoder.name] ?: 1) - 1
            }
        }
    }

    companion object {
        const val MIME = "video/hevc"
        const val DEFAULT_SOFTWARE_LIMIT = 2

        private fun isHardware(info: MediaCodecInfo): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                info.isHardwareAccelerated
            } else {
                !info.name.startsWith("OMX.google.") && !info.name.startsWith("c2.android.")
            }
    }
}
