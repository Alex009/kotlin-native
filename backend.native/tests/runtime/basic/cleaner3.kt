/*
 * Copyright 2010-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

import kotlin.test.*

import kotlin.native.internal.*
import kotlin.native.concurrent.*

val globalInt1 = AtomicInt(11)
val globalInt2 = AtomicInt(30)

// This cleaner won't be run, because it's deinitialized with globals after
// cleaners are disabled.
val globalCleaner = createCleaner(globalInt2) {
    println(it.value + globalInt1.value)
}

fun main() {
    globalInt1.value = 12
    // Make sure cleaner is initialized.
    assertNotNull(globalCleaner)
}
