/*
 * Copyright 2010-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

package runtime.basic.cleaner1

import kotlin.test.*

import kotlin.native.internal.*
import kotlin.native.concurrent.*
import kotlin.native.ref.WeakReference

class AtomicBoolean(initialValue: Boolean) {
    private val impl = AtomicInt(if (initialValue) 1 else 0)

    init {
        freeze()
    }

    public var value: Boolean
        get() = impl.value != 0
        set(new) { impl.value = if (new) 1 else 0 }
}

class FunBox(private val impl: () -> Unit) {
    fun call() {
        impl()
    }
}

@Test
fun testCleanerDestroyInChild() {
    val worker = Worker.start()

    val called = AtomicBoolean(false);
    var funBoxWeak: WeakReference<FunBox>? = null
    var cleanerWeak: WeakReference<Cleaner>? = null
    worker.execute(TransferMode.SAFE, {
        val funBox = FunBox { called.value = true }.freeze()
        funBoxWeak = WeakReference(funBox)
        val cleaner = createCleaner(funBox) { it.call() }
        cleanerWeak = WeakReference(cleaner)
        Pair(called, cleaner)
    }) { (called, cleaner) ->
        assertFalse(called.value)
    }.result

    GC.collect()
    worker.requestTermination().result
    performGCOnCleanerWorker().result

    assertNull(cleanerWeak!!.value)
    assertTrue(called.value)
    assertNull(funBoxWeak!!.value)
}

@Test
fun testCleanerDestroyFrozenInChild() {
    val worker = Worker.start()

    val called = AtomicBoolean(false);
    var funBoxWeak: WeakReference<FunBox>? = null
    var cleanerWeak: WeakReference<Cleaner>? = null
    {
        worker.execute(TransferMode.SAFE, {
            val funBox = FunBox { called.value = true }.freeze()
            funBoxWeak = WeakReference(funBox)
            val cleaner = createCleaner(funBox) { it.call() }.freeze()
            cleanerWeak = WeakReference(cleaner)
            Pair(called, cleaner)
        }) { (called, cleaner) ->
            assertFalse(called.value)
        }.result
    }()

    GC.collect()
    worker.requestTermination().result
    performGCOnCleanerWorker().result

    assertNull(cleanerWeak!!.value)
    assertTrue(called.value)
    assertNull(funBoxWeak!!.value)
}

@Test
fun testCleanerDestroyInMain() {
    val worker = Worker.start()

    val called = AtomicBoolean(false);
    var funBoxWeak: WeakReference<FunBox>? = null
    var cleanerWeak: WeakReference<Cleaner>? = null
    {
        val result = worker.execute(TransferMode.SAFE, { called }) { called ->
            val funBox = FunBox { called.value = true }.freeze()
            val cleaner = createCleaner(funBox) { it.call() }
            Triple(cleaner, WeakReference(funBox), WeakReference(cleaner))
        }.result
        val cleaner = result.first
        funBoxWeak = result.second
        cleanerWeak = result.third
        assertFalse(called.value)
    }()

    GC.collect()
    worker.requestTermination().result
    performGCOnCleanerWorker().result

    assertNull(cleanerWeak!!.value)
    assertTrue(called.value)
    assertNull(funBoxWeak!!.value)
}

@Test
fun testCleanerDestroyFrozenInMain() {
    val worker = Worker.start()

    val called = AtomicBoolean(false);
    var funBoxWeak: WeakReference<FunBox>? = null
    var cleanerWeak: WeakReference<Cleaner>? = null
    {
        val result = worker.execute(TransferMode.SAFE, { called }) { called ->
            val funBox = FunBox { called.value = true }.freeze()
            val cleaner = createCleaner(funBox) { it.call() }.freeze()
            Triple(cleaner, WeakReference(funBox), WeakReference(cleaner))
        }.result
        val cleaner = result.first
        funBoxWeak = result.second
        cleanerWeak = result.third
        assertFalse(called.value)
    }()

    GC.collect()
    worker.requestTermination().result
    performGCOnCleanerWorker().result

    assertNull(cleanerWeak!!.value)
    assertTrue(called.value)
    assertNull(funBoxWeak!!.value)
}

// TODO: Tests with shared cleaner.
// TODO: Also test when Cleaner is destroyed while worker is still alive.
