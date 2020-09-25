/*
 * Copyright 2010-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

package org.jetbrains.kotlin.testing.native

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.bitcode.CompileToBitcodeExtension
import org.jetbrains.kotlin.bitcode.CompileToBitcodePlugin
import org.jetbrains.kotlin.resolve
import java.io.File
import java.net.URL
import javax.inject.Inject

@Suppress("UnstableApiUsage")
open class RuntimeTestingPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        val extension = extensions.create("googletest", GoogleTestExtension::class.java, target)
        val downloadTask = registerDownloadTask(extension)

        val googleTestRoot = project.provider {
            extension.localSourceRoot ?: extension.cloneTo
        }

        createBitcodeTasks(googleTestRoot, listOf(downloadTask))
    }

    private fun Project.registerDownloadTask(extension: GoogleTestExtension): TaskProvider<GitDownloadTask> {
        val task = tasks.register(
                "downloadGoogleTest",
                GitDownloadTask::class.java,
                provider { URL(extension.repository) },
                provider { extension.revision },
                provider { extension.cloneTo }
        )
        task.configure {
            it.force = provider { extension.force }
            it.onlyIf { extension.localSourceRoot == null }
            it.description = "Retrieves GoogleTest from the given repository"
            it.group = "Google Test"
        }
        return task
    }

    private fun Project.createBitcodeTasks(
            googleTestRoot: Provider<File>,
            dependencies: Iterable<TaskProvider<*>>
    ) {
        pluginManager.withPlugin("compile-to-bitcode") {
            val bitcodeExtension =
                    project.extensions.getByName(CompileToBitcodePlugin.EXTENSION_NAME) as CompileToBitcodeExtension

            bitcodeExtension.create("googletest") {
                srcDirs = project.files(
                        googleTestRoot.resolve("googletest/src")
                )
                headersDirs = project.files(
                        googleTestRoot.resolve("googletest/include"),
                        googleTestRoot.resolve("googletest")
                )
                includeFiles = listOf("*.cc")
                excludeFiles = listOf("gtest-all.cc", "gtest_main.cc")
                dependsOn(dependencies)
            }

            bitcodeExtension.create("googlemock") {
                srcDirs = project.files(
                        googleTestRoot.resolve("googlemock/src")
                )
                headersDirs = project.files(
                        googleTestRoot.resolve("googlemock"),
                        googleTestRoot.resolve("googlemock/include"),
                        googleTestRoot.resolve("googletest/include")
                )
                includeFiles = listOf("*.cc")
                excludeFiles = listOf("gmock-all.cc", "gmock_main.cc")
                dependsOn(dependencies)
            }
        }
    }
}

/**
 * A project extension to configure from where we get the GoogleTest framework.
 */
open class GoogleTestExtension @Inject constructor(project: Project) {
    var repository: String =  "https://github.com/google/googletest.git"
    var revision: String = "master"
    var force: Boolean = false

    var cloneTo: File = project.file("googletest")

    internal var localSourceRoot: File? = null

    fun useLocalSources(directory: File) {
        localSourceRoot = directory
    }
}

