package it.jarviswake

import android.view.Window
import android.view.WindowInsets

/** Window.getInsetsController dereferences its decor even before setContentView on AOSP. */
internal fun Window.hideMenuKeyboard() {
    // Do not create a decor or ask the Window's unsafe getter during Activity startup.
    peekDecorView()?.windowInsetsController?.hide(WindowInsets.Type.ime())
}
