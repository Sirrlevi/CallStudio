package com.callstudio.app.ui.theme

enum class CallTheme(val label: String, val blurb: String) {
    PIXEL("Pixel", "Modern Android: swipe pill, soft green-black"),
    ONE_UI("One UI", "Round buttons, answer left, decline right"),
    IOS("iOS", "Full-bleed photo, large buttons with labels"),
    VIVO("Vivo", "Blue gradient with ringed buttons"),
    TRUECALLER("Truecaller style", "Blue caller-ID screen, press-and-grow buttons"),
    MINIMAL_DARK("Minimal dark", "Black, light type, outlined actions"),
    MINIMAL_LIGHT("Minimal light", "Bright, rounded photo, stacked actions");

    companion object {
        fun fromName(name: String?): CallTheme = entries.firstOrNull { it.name == name } ?: PIXEL
    }
}
