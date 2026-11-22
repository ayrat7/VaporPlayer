package com.vapor.player

import android.net.Uri

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val uri: Uri
)

/** Превращает ASCII в полноширинные символы: "VAPOR" -> "ＶＡＰＯＲ" */
fun String.fw(): String = buildString {
    for (c in this@fw) when {
        c == ' ' -> append('\u3000')
        c.code in 0x21..0x7E -> append((c.code + 0xFEE0).toChar())
        else -> append(c)
    }
}

fun Long.fmt(): String {
    val s = (this / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
