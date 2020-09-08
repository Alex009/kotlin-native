/*
 * Copyright 2010-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

import kotlin.native.internal.*
import kotlin.native.concurrent.*

val globalInt = AtomicInt(11)

fun main() {
    globalInt.value = 12
    createCleaner(30) {
        println(it + globalInt.value)
    }
}
