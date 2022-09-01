package me.cpele.compotube.mvu

import android.content.Intent

class Change<M>(val model: M, vararg val effects: Effect) {
    override fun toString(): String =
        "Change(model=$model, effects=${effects.contentToString()})"
}

sealed class Effect {
    data class Toast(val text: String) : Effect()
    data class Log(val text: String) : Effect()
    data class ActForResult(val intent: Intent) : Effect()
    object GetAppContext : Effect()
}


