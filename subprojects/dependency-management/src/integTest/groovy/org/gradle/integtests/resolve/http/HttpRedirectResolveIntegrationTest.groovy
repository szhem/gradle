/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.integtests.resolve.http

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Issue

import static org.gradle.internal.resource.transport.http.JavaSystemPropertiesHttpTimeoutSettings.SOCKET_TIMEOUT_SYSTEM_PROPERTY

class HttpRedirectResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
    @Rule SetSystemProperties systemProperties = new SetSystemProperties()
    @Rule HttpServer backingServer = new HttpServer()

    def module = ivyRepo().module('group', 'projectA').publish()

    def setup() {
        backingServer.start()
    }

    def "resolves module artifacts via HTTP redirect"() {
        given:
        buildFile << configurationWithIvyDependencyAndExpectedArtifact('group:projectA:1.0', 'projectA-1.0.jar')

        when:
        server.expectGetRedirected('/repo/group/projectA/1.0/ivy-1.0.xml', "${backingServer.uri}/redirected/group/projectA/1.0/ivy-1.0.xml")
        backingServer.expectGet('/redirected/group/projectA/1.0/ivy-1.0.xml', module.ivyFile)
        server.expectGetRedirected('/repo/group/projectA/1.0/projectA-1.0.jar', "${backingServer.uri}/redirected/group/projectA/1.0/projectA-1.0.jar")
        backingServer.expectGet('/redirected/group/projectA/1.0/projectA-1.0.jar', module.jarFile)

        then:
        succeeds('listJars')
    }

    @Issue('GRADLE-2196')
    def "resolves artifact-only module via HTTP redirect"() {
        given:
        buildFile << configurationWithIvyDependencyAndExpectedArtifact('group:projectA:1.0@zip', 'projectA-1.0.zip')

        when:
        server.expectGetMissing('/repo/group/projectA/1.0/ivy-1.0.xml')
        server.expectHeadRedirected('/repo/group/projectA/1.0/projectA-1.0.zip', "${backingServer.uri}/redirected/group/projectA/1.0/projectA-1.0.zip")
        backingServer.expectHead('/redirected/group/projectA/1.0/projectA-1.0.zip', module.jarFile)
        server.expectGetRedirected('/repo/group/projectA/1.0/projectA-1.0.zip', "${backingServer.uri}/redirected/group/projectA/1.0/projectA-1.0.zip")
        backingServer.expectGet('/redirected/group/projectA/1.0/projectA-1.0.zip', module.jarFile)

        then:
        succeeds('listJars')
    }

    def "prints last redirect location in case of failure"() {
        given:
        buildFile << configurationWithIvyDependencyAndExpectedArtifact('group:projectA:1.0', 'projectA-1.0.jar')

        when:
        server.expectGetRedirected('/repo/group/projectA/1.0/ivy-1.0.xml', "${backingServer.uri}/redirected/group/projectA/1.0/ivy-1.0.xml")
        backingServer.expectGetBroken('/redirected/group/projectA/1.0/ivy-1.0.xml')

        then:
        fails('listJars')

        and:
        failureCauseContains("Could not get resource '${server.uri}/repo/group/projectA/1.0/ivy-1.0.xml'")
        failureCauseContains("Could not GET '${backingServer.uri}/redirected/group/projectA/1.0/ivy-1.0.xml'")
    }

    def "prints last redirect location in case of timeout"() {
        given:
        buildFile << configurationWithIvyDependencyAndExpectedArtifact('group:projectA:1.0', 'projectA-1.0.jar')

        when:
        server.expectGetRedirected('/repo/group/projectA/1.0/ivy-1.0.xml', "${backingServer.uri}/redirected/group/projectA/1.0/ivy-1.0.xml")
        backingServer.expectGetBlocking('/redirected/group/projectA/1.0/ivy-1.0.xml')

        then:
        executer.beforeExecute { withArgument("-D${SOCKET_TIMEOUT_SYSTEM_PROPERTY}=1000") }
        fails('listJars')

        and:
        failureCauseContains("Could not get resource '${server.uri}/repo/group/projectA/1.0/ivy-1.0.xml'")
        failureCauseContains("Could not GET '${backingServer.uri}/redirected/group/projectA/1.0/ivy-1.0.xml'")
        failureCauseContains("Read timed out")
    }

    def configurationWithIvyDependencyAndExpectedArtifact(String dependency, String expectedArtifact) {
        """
            repositories {
                ivy { 
                    url "http://localhost:${server.port}/repo"
                    metadataSources {
                        ivyDescriptor()
                        artifact()
                    }
                }
            }
            configurations { compile }
            dependencies { compile '$dependency' }
            task listJars {
                doLast {
                    assert configurations.compile.collect { it.name } == ['$expectedArtifact']
                }
            }
        """
    }

}
