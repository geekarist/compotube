package me.cpele.compotube.mvu

class Change<M>(val model: M, vararg val effects: Effect) {
    override fun toString(): String =
        "Change(model=$model, effects=${effects.contentToString()})"
}

sealed class Effect {
    data class Toast(val text: String) : Effect()
    data class Log(val text: String, val throwable: Throwable? = null, val tag: String) : Effect()

    data class LoadPref(val name: String, val defValue: String?) : Effect()
    data class SavePref(val name: String, val value: String) : Effect()

    object ChooseAccount : Effect()
    data class HandleAccountName(val accountName: String?) : Effect()

    data class Search(val query: String) : Effect()
}


