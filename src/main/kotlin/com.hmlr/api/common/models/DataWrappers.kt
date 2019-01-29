@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.hmlr.api.common.models

import net.corda.core.contracts.LinearState
import java.time.Instant

data class StateAndInstant<out T : LinearState> constructor(val state: T, val instant: Instant?) {
    //Below is a sneaky way of, when creating a new StateAndInstance, returning null if the state parameter is null.
    //The below invoke operator function will be called if the state is nullable, whereas the constructor will be called if it is not.
    //To call this function use the same syntax as calling the constructor and the compiler will work out which one to use depending on if the state is nullable.
    companion object {
        operator fun <T : LinearState> invoke(state: T?, instant: Instant?) : StateAndInstant<T>? {
            state ?: return null
            return StateAndInstant(state, instant)
        }
    }
}