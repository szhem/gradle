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
package org.gradle.integtests.resolve.rules

import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest

class VariantFilesMetadataRulesIntegrationTest extends AbstractModuleDependencyResolveTest {

    def setup() {

    }

    private String ivyRuntimeVariantDerivation() {
        if (!useIvy() && !isGradleMetadataPublished()) { return '' }
        // For ivy, there is no built-in derivation strategy.
        """
            context.details.allVariants {
                attributes { 
                    attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.JAR)) 
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.LIBRARY)) 
                }
            }
            context.details.withVariant('api') {
                attributes { 
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_API)) 
                }
            }
            context.details.withVariant('runtime') {
                attributes { 
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                }
            }
        """
    }

    def "missing variant can be added"() {
        given:
        repository {
            'org.test:moduleA:1.0' {
                withModule { undeclaredArtifact(classifier: 'jdk8') }
                dependsOn 'org.test:moduleB:1.0'
            }
            'org.test:moduleB:1.0'()
        }

        when:
        buildFile << """
            class MissingVariantRule implements ComponentMetadataRule {
                @javax.inject.Inject
                ObjectFactory getObjects() { }

                void execute(ComponentMetadataContext context) {
                    ${ivyRuntimeVariantDerivation()}
                    context.details.addVariant('jdk8Runtime', 'runtime') {
                        attributes { attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8) }
                        withFiles {
                            addFile("\${context.details.id.name}-\${context.details.id.version}-jdk8.jar")
                        }
                    }
                }
            }

            configurations.conf {
                attributes { attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8) }
            }

            dependencies {
                conf 'org.test:moduleA:1.0'
                components {
                    withModule('org.test:moduleA', MissingVariantRule)
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact(classifier: 'jdk8')
            }
            'org.test:moduleB:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        then:
        succeeds 'checkDep'
        def expectedStatus = useIvy() ? 'integration' : 'release'
        resolve.expectGraph {
            root(':', ':test:') {
                module('org.test:moduleA:1.0') {
                    variant('jdk8Runtime', ['org.gradle.jvm.version': 8, 'org.gradle.status': expectedStatus, 'org.gradle.usage': 'java-runtime', 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library'])
                    artifact(group: 'org.test', name: 'moduleA', version: '1.0', classifier: 'jdk8')
                    module('org.test:moduleB:1.0')
                }
            }
        }
    }
}
