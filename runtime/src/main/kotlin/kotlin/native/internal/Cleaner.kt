/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

package kotlin.native.internal

import kotlin.native.concurrent.freeze
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef

public interface Cleaner

@ExportTypeInfo("theCleanerImplTypeInfo")
private class CleanerImpl<T>(
    obj: T,
    private val cleanObj: (T) -> Unit,
): Cleaner {

    private val objHolder = StableRef.create(obj as Any).asCPointer()

    @ExportForCppRuntime("Kotlin_CleanerImpl_clean")
    private fun clean() {
        val ref = objHolder.asStableRef<Any>()
        @Suppress("UNCHECKED_CAST")
        cleanObj(ref.get() as T)
        ref.dispose()
    }
}

@SymbolName("Kotlin_Any_isShareable")
external private fun Any?.isShareable(): Boolean

@ExportForCompiler
private fun <T> createCleanerImpl(argument: T, block: (T) -> Unit): Cleaner {
    if (!argument.isShareable())
        throw IllegalArgumentException("$argument must be shareable")

    return CleanerImpl(argument, block.freeze())
}

// TODO: Consider just annotating the lambda argument rather than using intrinsic.
/**
 * If [block] throws an exception, the entire program terminates.
 */
@TypedIntrinsic(IntrinsicType.CREATE_CLEANER)
external fun <T> createCleaner(argument: T, block: (T) -> Unit): Cleaner
