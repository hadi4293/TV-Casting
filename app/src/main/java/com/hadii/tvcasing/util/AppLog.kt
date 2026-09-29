package com.hadii.tvcasing.util

import android.util.Log

/** Thin wrapper so every log line is tagged consistently and easy to filter. */
object AppLog {
    private const val TAG = "TVCasting"

    fun d(msg: String) = Log.d(TAG, msg)
    fun i(msg: String) = Log.i(TAG, msg)
    fun w(msg: String, t: Throwable? = null) = if (t == null) Log.w(TAG, msg) else Log.w(TAG, msg, t)
    fun e(msg: String, t: Throwable? = null) = if (t == null) Log.e(TAG, msg) else Log.e(TAG, msg, t)
}
