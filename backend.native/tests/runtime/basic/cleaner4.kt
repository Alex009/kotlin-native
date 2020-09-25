/*
 * Copyright 2010-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

import kotlin.native.internal.*
import kotlin.native.Platform

fun main() {
    Platform.isCleanersLeakCheckerActive = false
    // This will not get executed at all: after exiting `main` cleaners get disabled before GC is run to claim
    // this cleaner.
    createCleaner(42) {
        println(it)
    }
}
