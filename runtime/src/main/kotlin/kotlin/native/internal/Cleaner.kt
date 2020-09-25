/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

package kotlin.native.internal

import kotlin.native.concurrent.*
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef

public interface Cleaner

@SharedImmutable
private val cleanerWorker = {
    val worker = Worker.start(errorReporting = false, name = "Cleaner pool")
    // Make sure worker is up and running.
    worker.execute(TransferMode.SAFE, {}) {}.result
    worker
}()

private val cleanerWorkerIsStopped = AtomicInt(0)

@ExportForCppRuntime("Kotlin_CleanerImpl_shutdownCleanerWorker")
private fun shutdownCleanerWorker() {
    cleanerWorkerIsStopped.value = 1
    cleanerWorker.requestTermination().result
}

@ExportTypeInfo("theCleanerImplTypeInfo")
private class CleanerImpl<T>(
    obj: T,
    private val cleanObj: (T) -> Unit,
): Cleaner {

    init {
        // Make sure that Cleaner Worker is initialized.
        cleanerWorker
    }

    private val objHolder = StableRef.create(obj as Any)

    @ExportForCppRuntime("Kotlin_CleanerImpl_clean")
    private fun clean() {
        if (cleanerWorkerIsStopped.value != 0)
            return

        val cleanPackage = Pair(cleanObj, objHolder).freeze()
        cleanerWorker.execute(TransferMode.SAFE, { cleanPackage }) { (cleanObj, objHolder) ->
            @Suppress("UNCHECKED_CAST")
            cleanObj(objHolder.get() as T)
            objHolder.dispose()
        }
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

fun performGCOnCleanerWorker(): Future<Unit> =
    cleanerWorker.execute(TransferMode.SAFE, {}) {
        GC.collect()
    }
