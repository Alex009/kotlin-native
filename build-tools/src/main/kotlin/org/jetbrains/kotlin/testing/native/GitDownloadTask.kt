/*
 * Copyright 2010-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

package org.jetbrains.kotlin.testing.native

import org.gradle.api.DefaultTask
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.jetbrains.kotlin.isNotEmpty
import java.io.File
import java.net.URL
import java.util.*
import javax.inject.Inject

// TODO: Support option here and in the buildscript
// TODO: Add javadoc for extension.
/**
 * Clones the given revision of the given Git repository to the given directory.
 */
@Suppress("UnstableApiUsage")
open class GitDownloadTask @Inject constructor(
        val repositoryProvider: Provider<URL>,
        val revisionProvider: Provider<String>,
        val outputDirectoryProvider: Provider<File>
) : DefaultTask() {

    private val repository: URL
        get() = repositoryProvider.get()

    private val revision: String
        get() = revisionProvider.get()

    private val outputDirectory: File
        get() = outputDirectoryProvider.get()

    var force: Provider<Boolean> = project.provider { false }

    private val upToDateChecker = UpToDateChecker()

    init {
        outputs.upToDateWhen { upToDateChecker.isUpToDate() }
    }

    private fun git(
            vararg args: String,
            ignoreExitValue: Boolean = false,
            execConfiguration: ExecSpec.() -> Unit = {}
    ): ExecResult =
            project.exec {
                it.executable = "git"
                it.args(*args)
                it.isIgnoreExitValue = ignoreExitValue
                it.execConfiguration()
            }

    private fun tryCloneBranch(): Boolean {
        val execResult = git(
                "clone", repository.toString(),
                outputDirectory.absolutePath,
                "--depth", "1",
                "--branch", revision,
                ignoreExitValue = true
        )
        return execResult.exitValue == 0
    }

    private fun fetchByHash() {
        git("init", outputDirectory.absolutePath)
        git("fetch", repository.toString(), "--depth", "1", revision) {
            workingDir(outputDirectory)
        }
        git("reset", "--hard", revision) {
            workingDir(outputDirectory)
        }
    }

    @TaskAction
    fun clone() {
        // Gradle ignores outputs.upToDateWhen { ... } and reruns a task if classpath of the task was changed
        // So we have to check up-to-dateness manually.
        if (upToDateChecker.isUpToDate()) {
            logger.info("Skip cloning to avoid rewriting possible debug changes in ${outputDirectory.absolutePath}.")
            return
        }

        project.delete {
            it.delete(outputDirectory)
        }

        if (!tryCloneBranch()) {
            logger.info("Cannot use the revision '$revision' to clone the repository. Trying to use init && fetch instead.")
            fetchByHash()
        }

        // Store info about used revision for the manual up-to-date check.
        upToDateChecker.storeRevisionInfo()
    }


    /**
     * This class performs manual UP-TO-DATE checking.
     *
     * We want to be able to edit downloaded sources for debug purposes. Thus this task should not rewrite changes in
     * the output directory.
     *
     * Gradle does allow us to provide a custom logic to determine if task outputs are UP-TO-DATE or not (see
     * Task.outputs.upToDateWhen). But Gradle still reruns a task if its classpath was changed. This may lead
     * to rewriting our manual debug changes in downloaded sources if there are some changes in the
     * `build-tools` project.
     *
     * So we have to manually check up-to-dateness in upToDateWhen and as a first step of the task action.
     */
    private inner class UpToDateChecker() {

        private val revisionInfoFile: File
            get() = outputDirectory.resolve(".revision")

        /**
         * The download task should be executed in the following cases:
         *
         *  - The output directory doesn't exist or is empty;
         *  - Repository or revision were changed since last execution;
         *  - A user forced rerunning this tasks manually (see [GitDownloadTask.force]).
         *
         * In all other case we consider the task UP-TO-DATE.
         */
        fun isUpToDate(): Boolean {
            return !force.get() &&
                    outputDirectory.let { it.exists() && project.fileTree(it).isNotEmpty } &&
                    noRevisionChanges()
        }

        private fun noRevisionChanges(): Boolean =
                revisionInfoFile.exists() && loadRevisionInfo() == RevisionInfo(repository, revision)

        fun storeRevisionInfo() {
            val properties = Properties()
            properties["repository"] = repository.toString()
            properties["revision"] = revision
            revisionInfoFile.bufferedWriter().use {
                properties.store(it, null)
            }
        }

        private fun loadRevisionInfo(): RevisionInfo? {
            return try {
                val properties = Properties()
                revisionInfoFile.bufferedReader().use {
                    properties.load(it)
                }
                RevisionInfo(properties.getProperty("repository"), properties.getProperty("revision"))
            } catch (_ : Exception) {
                null
            }
        }
    }

    private data class RevisionInfo(val repository: String, val revision: String) {
        constructor(repository: URL, revision: String): this(repository.toString(), revision)
    }
}
