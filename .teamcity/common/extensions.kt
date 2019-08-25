/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package common

import configurations.m2CleanScriptUnixLike
import configurations.m2CleanScriptWindows
import jetbrains.buildServer.configs.kotlin.v2018_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildSteps
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2018_2.CheckoutMode
import jetbrains.buildServer.configs.kotlin.v2018_2.Dependencies
import jetbrains.buildServer.configs.kotlin.v2018_2.FailureAction
import jetbrains.buildServer.configs.kotlin.v2018_2.Requirements
import jetbrains.buildServer.configs.kotlin.v2018_2.VcsSettings
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.GradleBuildStep
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.script

fun BuildSteps.customGradle(init: GradleBuildStep.() -> Unit, custom: GradleBuildStep.() -> Unit): GradleBuildStep =
    GradleBuildStep(init)
        .apply(custom)
        .also { step(it) }

/**
 * Adds a [Gradle build step](https://confluence.jetbrains.com/display/TCDL/Gradle)
 * that runs with the Gradle wrapper.
 *
 * @see GradleBuildStep
 */
fun BuildSteps.gradleWrapper(init: GradleBuildStep.() -> Unit): GradleBuildStep =
    customGradle(init) {
        useGradleWrapper = true
        if (buildFile == null) {
            buildFile = "" // Let Gradle detect the build script
        }
    }

fun Requirements.requiresOs(os: Os) {
    contains("teamcity.agent.jvm.os.name", os.agentRequirement)
}

fun VcsSettings.filterDefaultBranch() {
    branchFilter = """
                +:*
                -:<default>
            """.trimIndent()
}

fun BuildType.applyDefaultSettings(os: Os = Os.linux, timeout: Int = 30, vcsRoot: String = "Gradle_Branches_GradlePersonalBranches") {
    artifactRules = """
        build/report-* => .
        buildSrc/build/report-* => .
        subprojects/*/build/tmp/test files/** => test-files
        build/errorLogs/** => errorLogs
        build/reports/incubation/** => incubation-reports
    """.trimIndent()

    vcs {
        root(AbsoluteId(vcsRoot))
        checkoutMode = CheckoutMode.ON_AGENT
        if (vcsRoot.contains("Branches")) {
            filterDefaultBranch()
        }
    }

    requirements {
        requiresOs(os)
    }

    failureConditions {
        executionTimeoutMin = timeout
    }

    if (os == Os.linux || os == Os.macos) {
        params {
            param("env.LC_ALL", "en_US.UTF-8")
        }
    }
}

fun BuildSteps.checkCleanM2(os: Os = Os.linux) {
    script {
        name = "CHECK_CLEAN_M2"
        executionMode = BuildStep.ExecutionMode.ALWAYS
        scriptContent = if (os == Os.windows) m2CleanScriptWindows else m2CleanScriptUnixLike
    }
}

fun buildToolGradleParameters(daemon: Boolean = true, isContinue: Boolean = true): List<String> =
    listOf(
        "-PmaxParallelForks=%maxParallelForks%",
        "-s",
        if (daemon) "--daemon" else "--no-daemon",
        if (isContinue) "--continue" else "",
        """-I "%teamcity.build.checkoutDir%/gradle/init-scripts/build-scan.init.gradle.kts"""",
        "-Dorg.gradle.internal.tasks.createops",
        "-Dorg.gradle.internal.plugins.portal.url.override=%gradle.plugins.portal.url%"
    )

fun buildToolParametersString(daemon: Boolean = true) = buildToolGradleParameters(daemon).joinToString(separator = " ")

fun Dependencies.compileAllDependency(compileAllId: String = "Gradle_Check_CompileAll") {
    // Compile All has to succeed before anything else is started
    dependency(AbsoluteId(compileAllId)) {
        snapshot {
            onDependencyFailure = FailureAction.CANCEL
            onDependencyCancel = FailureAction.CANCEL
        }
    }
    // Get the build receipt from sanity check to reuse the timestamp
    artifacts(AbsoluteId(compileAllId)) {
        id = "ARTIFACT_DEPENDENCY_$compileAllId"
        cleanDestination = true
        artifactRules = "build-receipt.properties => incoming-distributions"
    }
}

fun BuildSteps.verifyTestFilesCleanup(daemon: Boolean = true) {
    gradleWrapper {
        name = "VERIFY_TEST_FILES_CLEANUP"
        tasks = "verifyTestFilesCleanup"
        gradleParams = buildToolParametersString(daemon)
    }
}
