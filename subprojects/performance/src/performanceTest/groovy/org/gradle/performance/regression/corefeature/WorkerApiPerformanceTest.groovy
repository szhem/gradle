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

package org.gradle.performance.regression.corefeature

import org.gradle.performance.AbstractCrossVersionGradleProfilerPerformanceTest
import spock.lang.Unroll

class WorkerApiPerformanceTest extends AbstractCrossVersionGradleProfilerPerformanceTest {
    def setup() {
        runner.minimumVersion = '5.0'
        runner.targetVersions = ["5.7-20190805220111+0000"]
        runner.testProject = "workerApiProject"
    }

    @Unroll
    def "executing tasks with no isolation with work=#workItems / workers=#workers"() {
        given:
        runner.tasksToRun = ['clean', 'noIsolation', "-PoutputSize=$workItems"]
        runner.args = [ "--max-workers=$workers" ]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        workers | workItems
        1       | 1
        1       | 10
        4       | 1
        4       | 4
        4       | 40
        12      | 1
        12      | 12
        12      | 120
    }

    @Unroll
    def "executing tasks with classloader isolation work=#workItems / workers=#workers"() {
        given:
        runner.tasksToRun = ['clean', 'classloaderIsolation', "-PoutputSize=$workItems"]
        runner.args = [ "--max-workers=$workers" ]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        workers | workItems
        1       | 1
        1       | 10
        4       | 1
        4       | 4
        4       | 40
        12      | 1
        12      | 12
        12      | 120
    }

    @Unroll
    def "executing tasks with process isolation work=#workItems / workers=#workers"() {
        given:
        runner.tasksToRun = ['clean', 'classloaderIsolation', "-PoutputSize=$workItems"]
        runner.args = [ "--max-workers=$workers" ]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        workers | workItems
        1       | 1
        1       | 10
        4       | 1
        4       | 4
        4       | 40
        12      | 1
        12      | 12
        12      | 120
    }
}
