package com.callstudio.app.domain

import java.util.Locale

object TimeFormat {
    fun elapsed(ms: Long): String {
        val total = ms.coerceAtLeast(0L) / 1000L
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, m, s) else "%d:%02d".format(Locale.ROOT, m, s)
    }

    fun countdown(ms: Long): String {
        val s = ms / 1000L
        return when {
            s <= 0L -> "now"
            s < 60L -> "in $s s"
            s < 3600L -> "in ${(s + 59) / 60} min"
            s < 86_400L -> {
                val h = s / 3600
                val m = (s % 3600) / 60
                if (m == 0L) "in $h h" else "in $h h $m min"
            }
            else -> {
                val d = s / 86_400
                val h = (s % 86_400) / 3600
                if (h == 0L) "in $d d" else "in $d d $h h"
            }
        }
    }
}
