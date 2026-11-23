package com.example.sortmate.util

import android.os.SystemClock
import android.util.Log
import com.example.sortmate.BuildConfig

/** Debug-build timing markers. Only pass event types, row IDs and state, never user input. */
internal fun evaluationMark(event: String) {
    if (BuildConfig.DEBUG) {
        Log.d("SortMateMetrics", "$event elapsed_ms=${SystemClock.elapsedRealtime()} wall_ms=${System.currentTimeMillis()}")
    }
}
