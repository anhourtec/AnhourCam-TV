package com.anhourtec.camgrid.tv.video

import android.graphics.Bitmap
import android.util.LruCache
import com.anhourtec.camgrid.layouts.CameraRef

/** The latest still image per camera (small bitmaps, about 230 KB each; capped in memory). */
class SnapshotCache : LruCache<CameraRef, SnapshotCache.Entry>(MAX_BYTES) {
    class Entry(
        val bitmap: Bitmap,
        val takenAt: Long,
    )

    override fun sizeOf(
        key: CameraRef,
        value: Entry,
    ): Int = value.bitmap.byteCount

    private companion object {
        const val MAX_BYTES = 12 * 1024 * 1024
    }
}
