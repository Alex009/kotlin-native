/*
 * Copyright 2010-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

package runtime.basic.cleaner0

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
fun testCleaner() {
    val called = AtomicBoolean(false);
    var funBoxWeak: WeakReference<FunBox>? = null
    var cleanerWeak: WeakReference<Cleaner>? = null
    {
        val funBox = FunBox { called.value = true }.freeze()
        funBoxWeak = WeakReference(funBox)
        val cleaner = createCleaner(funBox) { it.call() }
        cleanerWeak = WeakReference(cleaner)
        assertFalse(called.value)
    }()

    GC.collect()

    assertNull(cleanerWeak!!.value)
    assertTrue(called.value)
    assertNull(funBoxWeak!!.value)
}

@Test
fun testCleanerFrozen() {
    val called = AtomicBoolean(false);
    var funBoxWeak: WeakReference<FunBox>? = null
    var cleanerWeak: WeakReference<Cleaner>? = null
    {
        val funBox = FunBox { called.value = true }.freeze()
        funBoxWeak = WeakReference(funBox)
        val cleaner = createCleaner(funBox) { it.call() }.freeze()
        cleanerWeak = WeakReference(cleaner)
        assertFalse(called.value)
    }()

    GC.collect()

    assertNull(cleanerWeak!!.value)
    assertTrue(called.value)
    assertNull(funBoxWeak!!.value)
}

var globalInt = 0

@Test
fun testCleanerWithInt() {
    var cleanerWeak: WeakReference<Cleaner>? = null
    {
        val cleaner = createCleaner(42) {
            globalInt = it
        }
        cleanerWeak = WeakReference(cleaner)
        assertEquals(0, globalInt)
    }()

    GC.collect()

    assertNull(cleanerWeak!!.value)
    assertEquals(42, globalInt)
}

val globalAtomicInt = AtomicInt(0)

@Test
fun testCleanerWithIntFrozen() {
    var cleanerWeak: WeakReference<Cleaner>? = null
    {
        val cleaner = createCleaner(42) {
            globalAtomicInt.value = it
        }
        cleanerWeak = WeakReference(cleaner)
        assertEquals(0, globalAtomicInt.value)
    }()

    GC.collect()

    assertNull(cleanerWeak!!.value)
    assertEquals(42, globalAtomicInt.value)
}

var globalPtr = NativePtr.NULL

@Test
fun testCleanerWithNativePtr() {
    var cleanerWeak: WeakReference<Cleaner>? = null
    {
        val cleaner = createCleaner(NativePtr.NULL + 42L) {
            globalPtr = it
        }
        cleanerWeak = WeakReference(cleaner)
        assertEquals(NativePtr.NULL, globalPtr)
    }()

    GC.collect()

    assertNull(cleanerWeak!!.value)
    assertEquals(NativePtr.NULL + 42L, globalPtr)
}

val globalAtomicPtr = AtomicNativePtr(NativePtr.NULL)

@Test
fun testCleanerWithNativePtrFrozen() {
    var cleanerWeak: WeakReference<Cleaner>? = null
    {
        val cleaner = createCleaner(NativePtr.NULL + 42L) {
            globalAtomicPtr.value = it
        }
        cleanerWeak = WeakReference(cleaner)
        assertEquals(NativePtr.NULL, globalAtomicPtr.value)
    }()

    GC.collect()

    assertNull(cleanerWeak!!.value)
    assertEquals(NativePtr.NULL + 42L, globalAtomicPtr.value)
}

@Test
fun testCleanerHoldsReference() {
    val called = AtomicBoolean(false);
    var funBoxWeak: WeakReference<FunBox>? = null
    var cleanerWeak: WeakReference<Cleaner>? = null
    {
        val cleaner = {
            val funBox = FunBox { called.value = true }.freeze()
            funBoxWeak = WeakReference(funBox)
            createCleaner(funBox) { it.call() }
        }()
        cleanerWeak = WeakReference(cleaner)

        GC.collect()

        assertFalse(called.value)
    }()

    GC.collect()

    assertNull(cleanerWeak!!.value)
    assertTrue(called.value)
    assertNull(funBoxWeak!!.value)
}

@Test
fun testCleanerHoldsReferenceFrozen() {
    val called = AtomicBoolean(false);
    var funBoxWeak: WeakReference<FunBox>? = null
    var cleanerWeak: WeakReference<Cleaner>? = null
    {
        val cleaner = {
            val funBox = FunBox { called.value = true }.freeze()
            funBoxWeak = WeakReference(funBox)
            createCleaner(funBox) { it.call() }
        }()
        cleanerWeak = WeakReference(cleaner)

        GC.collect()

        assertFalse(called.value)
    }()

    GC.collect()

    assertNull(cleanerWeak!!.value)
    assertTrue(called.value)
    assertNull(funBoxWeak!!.value)
}
