package me.cpele.compotube

import android.util.Log
import androidx.compose.foundation.focusable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.NativeKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalFocusManager

object ModifierX {

    private val TAG = javaClass.simpleName

    fun Modifier.focusableWithArrowKeys(): Modifier = composed {
        val focusManager = LocalFocusManager.current
        focusable()
            .onKeyEvent { event: KeyEvent ->
                when (event.nativeKeyEvent.keyCode) {
                    NativeKeyEvent.KEYCODE_DPAD_DOWN,
                    NativeKeyEvent.KEYCODE_DPAD_RIGHT -> {
                        focusManager.moveFocus(FocusDirection.Next)
                        true
                    }
                    NativeKeyEvent.KEYCODE_DPAD_UP,
                    NativeKeyEvent.KEYCODE_DPAD_LEFT -> {
                        focusManager.moveFocus(FocusDirection.Previous)
                        true
                    }
                    else -> {
                        Log.d(TAG, "View: Unknown key code: ${event.nativeKeyEvent.keyCode}")
                        false
                    }
                }
            }
    }
}
