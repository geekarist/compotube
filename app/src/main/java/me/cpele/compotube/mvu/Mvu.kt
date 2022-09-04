package me.cpele.compotube.mvu

import android.content.Intent

class Change<M>(val model: M, vararg val effects: Effect) {
    override fun toString(): String =
        "Change(model=$model, effects=${effects.contentToString()})"
}

sealed class Effect {
    data class LoadPref(val name: String, val defValue: String?) : Effect()
    data class Toast(val text: String) : Effect()
    data class Log(val text: String, val throwable: Throwable? = null, val tag: String) : Effect()
    data class ActForResult(val intent: Intent) : Effect()
    data class SavePref(val name: String, val value: String) : Effect()
    object GetAppContext : Effect()
}


