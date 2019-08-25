/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.util

import org.gradle.api.Task
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.FeaturePreviews
import org.gradle.api.internal.MutationGuards
import org.gradle.api.internal.collections.DefaultDomainObjectCollectionFactory
import org.gradle.api.internal.collections.DomainObjectCollectionFactory
import org.gradle.api.internal.file.DefaultFilePropertyFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.model.DefaultObjectFactory
import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.taskfactory.TaskInstantiator
import org.gradle.api.internal.provider.DefaultProviderFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.instantiation.DefaultInstantiatorFactory
import org.gradle.internal.instantiation.InjectAnnotationHandler
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.state.ManagedFactoryRegistry
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.gradle.testfixtures.internal.ProjectBuilderImpl

class TestUtil {
    public static final Closure TEST_CLOSURE = {}
    private static InstantiatorFactory instantiatorFactory
    private static ManagedFactoryRegistry managedFactoryRegistry
    private static ServiceRegistry services

    private final File rootDir

    private TestUtil(File rootDir) {
        NativeServicesTestFixture.initialize()
        this.rootDir = rootDir
    }

    static InstantiatorFactory instantiatorFactory() {
        if (instantiatorFactory == null) {
            NativeServicesTestFixture.initialize()
            def annotationHandlers = ProjectBuilderImpl.getGlobalServices().getAll(InjectAnnotationHandler.class)
            instantiatorFactory = new DefaultInstantiatorFactory(new TestCrossBuildInMemoryCacheFactory(), annotationHandlers)
        }
        return instantiatorFactory
    }

    static ManagedFactoryRegistry managedFactoryRegistry() {
        if (managedFactoryRegistry == null) {
            NativeServicesTestFixture.initialize()
            managedFactoryRegistry = ProjectBuilderImpl.getGlobalServices().get(ManagedFactoryRegistry.class)
        }
        return managedFactoryRegistry
    }

    static DomainObjectCollectionFactory domainObjectCollectionFactory() {
        return new DefaultDomainObjectCollectionFactory(instantiatorFactory(), services(), CollectionCallbackActionDecorator.NOOP, MutationGuards.identity())
    }

    static ObjectFactory objectFactory() {
        return objFactory(TestFiles.resolver())
    }

    static ObjectFactory objectFactory(TestFile baseDir) {
        def fileResolver = TestFiles.resolver(baseDir)
        def fileCollectionFactory = TestFiles.fileCollectionFactory(baseDir)
        return new DefaultObjectFactory(instantiatorFactory().injectAndDecorate(services()), objectInstantiator(), fileResolver, TestFiles.directoryFileTreeFactory(), new DefaultFilePropertyFactory(fileResolver, fileCollectionFactory), fileCollectionFactory, domainObjectCollectionFactory())
    }

    private static ObjectFactory objFactory(FileResolver fileResolver) {
        def fileCollectionFactory = TestFiles.fileCollectionFactory()
        return new DefaultObjectFactory(instantiatorFactory().injectAndDecorate(services()), objectInstantiator(), fileResolver, TestFiles.directoryFileTreeFactory(), new DefaultFilePropertyFactory(fileResolver, fileCollectionFactory), fileCollectionFactory, domainObjectCollectionFactory())
    }

    private static ServiceRegistry services() {
        if (services == null) {
            services = new DefaultServiceRegistry()
            services.register {
                it.add(DefaultProviderFactory)
                it.add(InstantiatorFactory, instantiatorFactory())
                it.add(TestCrossBuildInMemoryCacheFactory)
                it.add(NamedObjectInstantiator)
            }
        }
        return services
    }

    static NamedObjectInstantiator objectInstantiator() {
        return services().get(NamedObjectInstantiator)
    }

    static FeaturePreviews featurePreviews() {
        return new FeaturePreviews()
    }

    static TestUtil create(File rootDir) {
        return new TestUtil(rootDir)
    }

    static TestUtil create(TestDirectoryProvider testDirectoryProvider) {
        return new TestUtil(testDirectoryProvider.testDirectory)
    }

    public <T extends Task> T task(Class<T> type) {
        return createTask(type, createRootProject(this.rootDir))
    }

    static <T extends Task> T createTask(Class<T> type, ProjectInternal project) {
        return createTask(type, project, 'name')
    }

    static <T extends Task> T createTask(Class<T> type, ProjectInternal project, String name) {
        return project.services.get(TaskInstantiator).create(name, type)
    }

    static ProjectBuilder builder(File rootDir) {
        return ProjectBuilder.builder().withProjectDir(rootDir)
    }

    static ProjectBuilder builder(TestDirectoryProvider temporaryFolder) {
        return builder(temporaryFolder.testDirectory)
    }

    ProjectInternal rootProject() {
        createRootProject(rootDir)
    }

    static ProjectInternal createRootProject(File rootDir) {
        return ProjectBuilder
            .builder()
            .withProjectDir(rootDir)
            .build()
    }

    static ProjectInternal createChildProject(ProjectInternal parent, String name, File projectDir = null) {
        return ProjectBuilder
            .builder()
            .withName(name)
            .withParent(parent)
            .withProjectDir(projectDir)
            .build()
    }

    static groovy.lang.Script createScript(String code) {
        new GroovyShell().parse(code)
    }

    static Object call(String text, Object... params) {
        toClosure(text).call(*params)
    }

    static Closure toClosure(String text) {
        return new GroovyShell().evaluate("return " + text)
    }

    static Closure toClosure(TestClosure closure) {
        return { param -> closure.call(param) }
    }

    static Closure returns(Object value) {
        return { value }
    }
}

interface TestClosure {
    Object call(Object param);
}
