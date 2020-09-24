/*
 * Copyright 2010-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "Cleaner.h"

#include "Memory.h"

// Defined in Cleaner.kt
extern "C" void Kotlin_CleanerImpl_clean(KRef thiz);

namespace {

bool allowedCleaners = true;
bool cleanerWorkerActive = false;

void disposeCleaner(KRef thiz) {
    if (!atomicGet(&allowedCleaners)) {
        konan::consoleErrorf("Cleaner %p was stored in a global object. This is not allowed\n", thiz);
        RuntimeCheck(false, "Terminating now");
    }
    Kotlin_CleanerImpl_clean(thiz);
}

}  // namespace

RUNTIME_NOTHROW void DisposeCleaner(KRef thiz) {
#if KONAN_NO_EXCEPTIONS
    disposeCleaner(thiz);
#else
    try {
        disposeCleaner(thiz);
    } catch (...) {
        // A trick to terminate with unhandled exception. This will print a stack trace
        // and write to iOS crash log.
        std::terminate();
    }
#endif
}

RUNTIME_NOTHROW void DisallowCleaners() {
  atomicSet(&allowedCleaners, false);
}

RUNTIME_NOTHROW bool CleanerWorkerActive() {
    return atomicGet(&cleanerWorkerActive);
}

extern "C" void Kotlin_CleanerImpl_MarkCleanerWorkerActive() {
    atomicSet(&cleanerWorkerActive, true);
}
