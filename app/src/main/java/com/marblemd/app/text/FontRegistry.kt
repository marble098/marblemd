package com.marblemd.app.text

import android.content.Context
import android.graphics.Typeface

internal class FontRegistry(context: Context) {
    val vazirmatn: Typeface = load(context, "fonts/Vazirmatn.ttf", Typeface.SANS_SERIF)
    val notoSans: Typeface = load(context, "fonts/NotoSans.ttf", Typeface.SANS_SERIF)
    val lalezar: Typeface = load(context, "fonts/Lalezar.ttf", vazirmatn)

    private fun load(context: Context, asset: String, fallback: Typeface): Typeface =
        runCatching { Typeface.createFromAsset(context.assets, asset) }.getOrDefault(fallback)
}
