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

package org.gradle.api.internal.provider

import org.gradle.api.Task
import org.gradle.api.Transformer
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.provider.Provider
import org.gradle.internal.state.Managed
import spock.lang.Specification

import java.util.concurrent.Callable

abstract class PropertySpec<T> extends ProviderSpec<T> {
    @Override
    abstract PropertyInternal<T> providerWithValue(T value)

    @Override
    PropertyInternal<T> providerWithNoValue() {
        return propertyWithNoValue()
    }

    /**
     * Returns a property with _no_ value.
     */
    abstract PropertyInternal<T> propertyWithNoValue()

    /**
     * Returns a property with its default value.
     */
    abstract PropertyInternal<T> propertyWithDefaultValue()

    abstract T someValue()

    abstract T someOtherValue()

    abstract Class<T> type()

    protected void setToNull(def property) {
        property.set(null)
    }

    def "cannot get value when it has none"() {
        given:
        def property = propertyWithNoValue()

        when:
        property.get()

        then:
        def t = thrown(IllegalStateException)
        t.message == "No value has been specified for this provider."

        when:
        property.set(someValue())
        property.get()

        then:
        noExceptionThrown()
    }

    def "can set value"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())

        expect:
        property.present
        property.get() == someValue()
        property.getOrNull() == someValue()
        property.getOrElse(someOtherValue()) == someValue()
        property.getOrElse(null) == someValue()
    }

    def "can set value using chaining method"() {
        given:
        def property = propertyWithNoValue()
        property.value(someValue())

        expect:
        property.get() == someValue()
    }

    def "can set value using provider"() {
        def provider = Mock(ProviderInternal)

        given:
        provider.type >> type()

        def property = propertyWithNoValue()
        property.set(provider)

        when:
        def r = property.present

        then:
        r
        1 * provider.present >> true
        0 * Specification._

        when:
        def r2 = property.get()

        then:
        r2 == someValue()
        1 * provider.get() >> someValue()
        0 * Specification._

        when:
        def r3 = property.getOrNull()

        then:
        r3 == someOtherValue()
        1 * provider.getOrNull() >> someOtherValue()
        0 * Specification._

        when:
        def r4 = property.getOrElse(someOtherValue())

        then:
        r4 == someValue()
        1 * provider.getOrNull() >> someValue()
        0 * Specification._
    }

    def "can set value using provider and chaining method"() {
        given:
        def property = propertyWithNoValue()
        property.value(Providers.of(someValue()))

        expect:
        property.get() == someValue()
    }

    def "does not allow a null provider"() {
        given:
        def property = propertyWithNoValue()

        when:
        property.set((Provider) null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Cannot set the value of a property using a null provider.'
    }

    def "can set untyped using null"() {
        given:
        def property = propertyWithNoValue()
        property.setFromAnyValue(null)

        expect:
        !property.present
        property.getOrNull() == null
        property.getOrElse(someOtherValue()) == someOtherValue()
    }

    def "can set untyped using value"() {
        given:
        def property = propertyWithNoValue()
        property.setFromAnyValue(someValue())

        expect:
        property.present
        property.get() == someValue()
        property.getOrNull() == someValue()
        property.getOrElse(someOtherValue()) == someValue()
        property.getOrElse(null) == someValue()
    }

    def "fails when untyped value is set using incompatible type"() {
        def property = propertyWithNoValue()

        when:
        property.setFromAnyValue(new Thing())

        then:
        IllegalArgumentException e = thrown()
        e.message == "Cannot set the value of a property of type ${type().name} using an instance of type ${Thing.name}."
    }

    def "can set untyped using provider"() {
        def provider = Stub(ProviderInternal)

        given:
        provider.type >> type()
        provider.get() >> someValue()
        provider.present >> true

        def property = propertyWithNoValue()
        property.setFromAnyValue(provider)

        when:
        def r = property.present
        def r2 = property.get()

        then:
        r
        r2 == someValue()
    }

    def "convention value is used before value has been set"() {
        def property = propertyWithDefaultValue()
        assert property.getOrNull() != someValue()

        expect:
        property.convention(someValue())
        property.present
        property.get() == someValue()

        property.set(someOtherValue())
        property.present
        property.get() == someOtherValue()
    }

    def "convention provider is used before value has been set"() {
        def provider = Mock(ProviderInternal)
        def property = propertyWithDefaultValue()

        when:
        property.convention(provider)
        def r = property.present
        def r2 = property.get()

        then:
        r
        r2 == someValue()

        and:
        1 * provider.present >> true
        1 * provider.get() >> someValue()
        0 * provider._

        when:
        property.set(someOtherValue())
        property.present
        property.get()

        then:
        0 * provider._
    }

    def "can replace convention value before value has been set"() {
        def provider = Mock(ProviderInternal)
        def property = propertyWithDefaultValue()

        when:
        property.convention(someValue())

        then:
        property.get() == someValue()

        when:
        property.convention(provider)
        def r = property.get()

        then:
        r == someOtherValue()

        and:
        1 * provider.get() >> someOtherValue()
        0 * provider._

        when:
        property.convention(someValue())

        then:
        property.get() == someValue()
        0 * provider._

        when:
        property.set(someOtherValue())
        def r2 = property.get()

        then:
        r2 == someOtherValue()
        0 * provider._
    }

    def "convention value ignored after value has been set"() {
        def property = propertyWithDefaultValue()
        property.set(someValue())

        expect:
        property.convention(someOtherValue())
        property.get() == someValue()
    }

    def "convention provider ignored after value has been set"() {
        def provider = Mock(PropertyInternal)
        0 * provider._

        def property = propertyWithDefaultValue()
        property.set(someValue())

        expect:
        property.convention(provider)
        property.get() == someValue()
    }

    def "convention value is used after value has been set to null"() {
        def property = propertyWithDefaultValue()

        property.convention(someOtherValue())
        setToNull(property)

        expect:
        property.present
        property.get() == someOtherValue()

        property.convention(someValue())
        property.present
        property.get() == someValue()
    }

    def "convention provider is used after value has been set to null"() {
        def provider = Mock(PropertyInternal)

        def property = propertyWithDefaultValue()
        property.convention(provider)
        setToNull(property)

        when:
        def r = property.present
        def r2 = property.get()

        then:
        r
        r2 == someOtherValue()

        and:
        1 * provider.isPresent() >> true
        1 * provider.get() >> someOtherValue()
        0 * provider._
    }

    def "convention value ignored after value has been set using provider with no value"() {
        def property = propertyWithDefaultValue()
        property.set(Providers.notDefined())

        expect:
        property.convention(someOtherValue())
        !property.present
        property.getOrNull() == null
    }

    def "convention provider ignored after value has been set using provider with no value"() {
        def provider = Mock(PropertyInternal)
        0 * provider._

        def property = propertyWithDefaultValue()
        property.set(Providers.notDefined())

        expect:
        property.convention(provider)
        !property.present
        property.getOrNull() == null
    }

    def "can map value using a transformation"() {
        def transformer = Mock(Transformer)
        def property = propertyWithNoValue()

        when:
        def provider = property.map(transformer)

        then:
        0 * Specification._

        when:
        property.set(someValue())
        def r1 = provider.get()

        then:
        r1 == someOtherValue()
        1 * transformer.transform(someValue()) >> someOtherValue()
        0 * Specification._

        when:
        def r2 = provider.get()

        then:
        r2 == someValue()
        1 * transformer.transform(someValue()) >> someValue()
        0 * Specification._
    }

    def "transformation is provided with the current value of the property each time the value is queried"() {
        def transformer = Mock(Transformer)
        def property = propertyWithNoValue()

        when:
        def provider = property.map(transformer)

        then:
        0 * Specification._

        when:
        property.set(someValue())
        def r1 = provider.get()

        then:
        r1 == 123
        1 * transformer.transform(someValue()) >> 123
        0 * Specification._

        when:
        property.set(someOtherValue())
        def r2 = provider.get()

        then:
        r2 == 456
        1 * transformer.transform(someOtherValue()) >> 456
        0 * Specification._
    }

    def "can map value to some other type"() {
        def transformer = Mock(Transformer)
        def property = propertyWithNoValue()

        when:
        def provider = property.map(transformer)

        then:
        0 * Specification._

        when:
        property.set(someValue())
        def r1 = provider.get()

        then:
        r1 == 12
        1 * transformer.transform(someValue()) >> 12
        0 * Specification._

        when:
        def r2 = provider.get()

        then:
        r2 == 10
        1 * transformer.transform(someValue()) >> 10
        0 * Specification._
    }

    def "mapped provider has no value and transformer is not invoked when property has no value"() {
        def transformer = Mock(Transformer)
        def property = propertyWithNoValue()

        when:
        def provider = property.map(transformer)

        then:
        !provider.present
        provider.getOrNull() == null
        provider.getOrElse(someOtherValue()) == someOtherValue()
        0 * Specification._

        when:
        provider.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'No value has been specified for this provider.'
    }

    def "can finalize value when no value defined"() {
        def property = propertyWithNoValue()

        when:
        property."$method"()

        then:
        !property.present
        property.getOrNull() == null

        where:
        method << ["finalizeValue", "implicitFinalizeValue"]
    }

    def "can finalize value when value set"() {
        def property = propertyWithNoValue()

        when:
        property.set(someValue())
        property."$method"()

        then:
        property.present
        property.getOrNull() == someValue()

        where:
        method << ["finalizeValue", "implicitFinalizeValue"]
    }

    def "can finalize value when using convention"() {
        def property = propertyWithDefaultValue()

        when:
        property.convention(someValue())
        property."$method"()

        then:
        property.present
        property.getOrNull() == someValue()

        where:
        method << ["finalizeValue", "implicitFinalizeValue"]
    }

    def "replaces provider with fixed value when value finalized"() {
        def property = propertyWithNoValue()
        def function = Mock(Callable)
        def provider = new DefaultProvider<T>(function)

        given:
        property.set(provider)

        when:
        property.finalizeValue()

        then:
        1 * function.call() >> someValue()
        0 * _

        when:
        def present = property.present
        def result = property.getOrNull()

        then:
        present
        result == someValue()
        0 * _
    }

    def "replaces provider with fixed value when value finalized on next read of value"() {
        def property = propertyWithNoValue()
        def function = Mock(Callable)
        def provider = new DefaultProvider<T>(function)

        given:
        property.set(provider)

        when:
        property.implicitFinalizeValue()

        then:
        0 * _

        when:
        def result = property.get()
        def present = property.present

        then:
        1 * function.call() >> someValue()
        0 * _

        and:
        result == someValue()
        present
    }

    def "replaces provider with fixed value when value finalized on next read of nullable value"() {
        def property = propertyWithNoValue()
        def function = Mock(Callable)
        def provider = new DefaultProvider<T>(function)

        given:
        property.set(provider)

        when:
        property.implicitFinalizeValue()

        then:
        0 * _

        when:
        def result = property.getOrNull()
        def present = property.present

        then:
        1 * function.call() >> someValue()
        0 * _

        and:
        result == someValue()
        present
    }

    def "replaces provider with fixed value when value finalized on next read of `present` property"() {
        def property = propertyWithNoValue()
        def function = Mock(Callable)
        def provider = new DefaultProvider<T>(function)

        given:
        property.set(provider)

        when:
        property.implicitFinalizeValue()

        then:
        0 * _

        when:
        def present = property.present
        def value = property.get()

        then:
        1 * function.call() >> someValue()
        0 * _

        and:
        present
        value == someValue()
    }

    def "replaces provider with fixed value when value finalized after finalize on next read"() {
        def property = propertyWithNoValue()
        def function = Mock(Callable)
        def provider = new DefaultProvider<T>(function)

        given:
        property.set(provider)
        property.implicitFinalizeValue()

        when:
        property.finalizeValue()

        then:
        1 * function.call() >> someValue()
        0 * Specification._

        when:
        def present = property.present
        def result = property.getOrNull()

        then:
        0 * Specification._

        and:
        present
        result == someValue()
    }

    def "replaces provider with no value with fixed missing value when value finalized"() {
        def property = propertyWithNoValue()
        def function = Mock(Callable)
        def provider = new DefaultProvider<T>(function)

        given:
        property.set(provider)

        when:
        property.finalizeValue()

        then:
        1 * function.call() >> null
        0 * Specification._

        when:
        def present = property.present
        def result = property.getOrNull()

        then:
        !present
        result == null
        0 * Specification._
    }

    def "replaces provider with no value with fixed missing value when value finalized on next read"() {
        def property = propertyWithNoValue()
        def function = Mock(Callable)
        def provider = new DefaultProvider<T>(function)

        given:
        property.set(provider)

        when:
        property.implicitFinalizeValue()

        then:
        0 * Specification._

        when:
        def present = property.present
        def result = property.getOrNull()

        then:
        1 * function.call() >> null
        0 * Specification._

        and:
        !present
        result == null
    }

    def "can finalize value when already finalized"() {
        def property = propertyWithNoValue()
        def function = Mock(Callable)
        def provider = new DefaultProvider<T>(function)

        when:
        property.set(provider)
        property.finalizeValue()

        then:
        1 * function.call() >> someValue()
        0 * Specification._

        when:
        property.finalizeValue()
        property.implicitFinalizeValue()
        property.implicitFinalizeValue()
        property.disallowChanges()

        then:
        0 * Specification._
    }

    def "can finalize after changes disallowed"() {
        def property = propertyWithNoValue()
        def function = Mock(Callable)
        def provider = new DefaultProvider<T>(function)

        when:
        property.set(provider)
        property.disallowChanges()

        then:
        0 * Specification._

        when:
        property.finalizeValue()

        then:
        1 * function.call() >> someValue()
        0 * Specification._
    }

    def "uses value from provider after changes disallowed"() {
        def property = propertyWithNoValue()
        def function = Mock(Callable)
        def provider = new DefaultProvider<T>(function)

        given:
        property.set(provider)

        when:
        property.disallowChanges()

        then:
        0 * function._

        when:
        def result = property.getOrNull()

        then:
        result == someValue()
        1 * function.call() >> someValue()
        0 * Specification._
    }

    def "cannot set value after value finalized"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.finalizeValue()

        when:
        property.set(someValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property is final and cannot be changed any further.'

        when:
        setToNull(property)

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.value(someValue())

        then:
        def e3 = thrown(IllegalStateException)
        e3.message == 'The value for this property is final and cannot be changed any further.'
    }

    def "ignores set value after value finalized leniently"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.implicitFinalizeValue()
        property.get()

        when:
        property.set(someOtherValue())

        then:
        property.get() == someValue()

        when:
        setToNull(property)

        then:
        property.get() == someValue()
    }

    def "cannot set value after value finalized after value finalized leniently"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.implicitFinalizeValue()
        property.set(someOtherValue())
        property.finalizeValue()

        when:
        property.set(someOtherValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property is final and cannot be changed any further.'

        when:
        setToNull(property)

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.value(someOtherValue())

        then:
        def e3 = thrown(IllegalStateException)
        e3.message == 'The value for this property is final and cannot be changed any further.'
    }

    def "cannot set value after changes disallowed"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.disallowChanges()

        when:
        property.set(someValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property cannot be changed any further.'

        when:
        setToNull(property)

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property cannot be changed any further.'

        when:
        property.value(someValue())

        then:
        def e3 = thrown(IllegalStateException)
        e3.message == 'The value for this property cannot be changed any further.'
    }

    def "cannot set value after changes disallowed and implicitly finalized"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.disallowChanges()
        property.implicitFinalizeValue()

        when:
        property.set(someValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property cannot be changed any further.'
    }

    def "cannot set value after changes disallowed and finalized"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.disallowChanges()
        property.finalizeValue()

        when:
        property.set(someValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property is final and cannot be changed any further.'
    }

    def "cannot set value using provider after value finalized"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.finalizeValue()

        when:
        property.set(Mock(ProviderInternal))

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.value(Mock(ProviderInternal))

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property is final and cannot be changed any further.'
    }

    def "ignores set value using provider after value finalized leniently"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.implicitFinalizeValue()
        property.get()

        when:
        property.set(Mock(ProviderInternal))

        then:
        property.get() == someValue()
    }

    def "cannot set value using provider after changes disallowed"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.disallowChanges()

        when:
        property.set(Mock(ProviderInternal))

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property cannot be changed any further.'

        when:
        property.value(Mock(ProviderInternal))

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property cannot be changed any further.'
    }

    def "cannot set value using any type after value finalized"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.finalizeValue()

        when:
        property.setFromAnyValue(someOtherValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.setFromAnyValue(Stub(ProviderInternal))

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property is final and cannot be changed any further.'
    }

    def "ignores set value using any type after value finalized leniently"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.implicitFinalizeValue()
        property.get()

        when:
        property.setFromAnyValue(someOtherValue())

        then:
        property.get() == someValue()

        when:
        property.setFromAnyValue(Stub(ProviderInternal))

        then:
        property.get() == someValue()
    }

    def "cannot set value using any type after changes disallowed"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.disallowChanges()

        when:
        property.setFromAnyValue(someOtherValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property cannot be changed any further.'

        when:
        property.setFromAnyValue(Stub(ProviderInternal))

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property cannot be changed any further.'
    }

    def "cannot set convention value after value finalized"() {
        given:
        def property = propertyWithDefaultValue()
        property.finalizeValue()

        when:
        property.convention(someValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property is final and cannot be changed any further.'
    }

    def "ignores set convention value after value finalized leniently"() {
        given:
        def property = propertyWithDefaultValue()
        property.set(someValue())
        property.implicitFinalizeValue()
        property.get()

        when:
        property.convention(someOtherValue())

        then:
        property.get() == someValue()
    }

    def "cannot set convention value after changes disallowed"() {
        given:
        def property = propertyWithDefaultValue()
        property.disallowChanges()

        when:
        property.convention(someValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property cannot be changed any further.'
    }

    def "cannot set convention value using provider after value finalized"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.finalizeValue()

        when:
        property.convention(Mock(ProviderInternal))

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property is final and cannot be changed any further.'
    }

    def "ignores set convention value using provider after value finalized leniently"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.implicitFinalizeValue()
        property.get()

        when:
        property.convention(Mock(ProviderInternal))

        then:
        property.get() == someValue()
    }

    def "cannot set convention value using provider after changes disallowed"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.disallowChanges()

        when:
        property.convention(Mock(ProviderInternal))

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property cannot be changed any further.'
    }

    def "producer task for a property is not known by default"() {
        def context = Mock(TaskDependencyResolveContext)
        def property = propertyWithNoValue()
        property.set(someValue())

        when:
        def known = property.maybeVisitBuildDependencies(context)

        then:
        !known
        0 * context._
    }

    def "can define producer task for a property"() {
        def task = Mock(Task)
        def context = Mock(TaskDependencyResolveContext)
        def property = propertyWithNoValue()
        property.set(someValue())
        property.attachProducer(task)

        when:
        def known = property.maybeVisitBuildDependencies(context)

        then:
        known
        1 * context.add(task)
        0 * context._
    }

    def "has build dependencies when value is provider with producer task"() {
        def provider = Mock(ProviderInternal)
        _ * provider.type >> type()
        def context = Mock(TaskDependencyResolveContext)
        def property = propertyWithNoValue()
        property.set(provider)

        when:
        def known = property.maybeVisitBuildDependencies(context)

        then:
        known
        1 * provider.maybeVisitBuildDependencies(context) >> true
        0 * context._
    }

    def "has content producer when producer task attached"() {
        def task = Mock(Task)
        def property = propertyWithDefaultValue()

        expect:
        !property.contentProducedByTask
        !property.valueProducedByTask

        property.attachProducer(task)

        property.contentProducedByTask
        !property.valueProducedByTask
    }

    def "has content producer when value is provider with content producer"() {
        def provider = Mock(ProviderInternal)
        _ * provider.type >> type()
        _ * provider.contentProducedByTask >> true

        def property = propertyWithNoValue()
        property.set(provider)

        expect:
        property.contentProducedByTask
        !property.valueProducedByTask
    }

    def "mapped value has value producer when producer task attached"() {
        def task = Mock(Task)
        def property = propertyWithDefaultValue()
        def mapped = property.map { it }

        expect:
        !mapped.contentProducedByTask
        !mapped.valueProducedByTask

        property.attachProducer(task)

        mapped.contentProducedByTask
        mapped.valueProducedByTask
    }

    def "mapped value has value producer when value is provider with content producer"() {
        def provider = Mock(ProviderInternal)
        _ * provider.type >> type()
        _ * provider.contentProducedByTask >> true

        def property = propertyWithNoValue()
        property.set(provider)
        def mapped = property.map { it }

        expect:
        mapped.contentProducedByTask
        mapped.valueProducedByTask
    }

    def "can unpack state and recreate instance"() {
        given:
        def property = propertyWithNoValue()

        expect:
        property instanceof Managed
        !property.immutable()
        def state = property.unpackState()
        def copy = managedFactory().fromState(property.publicType(), state)
        !copy.is(property)
        !copy.present
        copy.getOrNull() == null

        property.set(someValue())
        copy.getOrNull() == null

        def state2 = property.unpackState()
        def copy2 = managedFactory().fromState(property.publicType(), state2)
        !copy2.is(property)
        copy2.get() == someValue()

        property.set(someOtherValue())
        copy.getOrNull() == null
        copy2.get() == someValue()
    }

    static class Thing {}
}
