package com.anhourtec.camgrid.tv

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-wide scope for work that must finish even when a screen goes away, such as telling
 * the NVR to stop streaming. Without it, a closed screen leaves streams running on the NVR.
 */
object AppScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO)
