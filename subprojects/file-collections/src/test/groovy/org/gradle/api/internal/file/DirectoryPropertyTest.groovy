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

package org.gradle.api.internal.file

import org.gradle.api.file.Directory
import org.gradle.api.internal.provider.PropertyInternal
import org.gradle.internal.state.ManagedFactory

class DirectoryPropertyTest extends FileSystemPropertySpec<Directory> {
    @Override
    Class<Directory> type() {
        return Directory.class
    }

    @Override
    Directory someValue() {
        return baseDir.dir("dir1").get()
    }

    @Override
    Directory someOtherValue() {
        return baseDir.dir("dir2").get()
    }

    @Override
    PropertyInternal<Directory> propertyWithNoValue() {
        return factory.newDirectoryProperty()
    }

    @Override
    PropertyInternal<Directory> propertyWithDefaultValue() {
        return factory.newDirectoryProperty()
    }

    @Override
    PropertyInternal<Directory> providerWithValue(Directory value) {
        return factory.newDirectoryProperty().value(value)
    }

    @Override
    ManagedFactory managedFactory() {
        new ManagedFactories.DirectoryPropertyManagedFactory(resolver, fileCollectionFactory)
    }
}
