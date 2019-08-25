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
package org.gradle.api.internal.file;

import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.collections.DefaultDirectoryFileTreeFactory;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.api.tasks.util.internal.PatternSets;
import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.DefaultExecutorFactory;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.file.impl.DefaultDeleter;
import org.gradle.internal.fingerprint.impl.DefaultFileCollectionSnapshotter;
import org.gradle.internal.hash.DefaultFileHasher;
import org.gradle.internal.hash.DefaultStreamHasher;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.resource.BasicTextResourceLoader;
import org.gradle.internal.resource.TextResourceLoader;
import org.gradle.internal.resource.local.FileResourceConnector;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.snapshot.FileSystemMirror;
import org.gradle.internal.snapshot.impl.DefaultFileSystemMirror;
import org.gradle.internal.snapshot.impl.DefaultFileSystemSnapshotter;
import org.gradle.internal.time.Time;
import org.gradle.process.internal.DefaultExecActionFactory;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.process.internal.ExecFactory;
import org.gradle.process.internal.ExecHandleFactory;
import org.gradle.process.internal.JavaExecHandleFactory;
import org.gradle.testfixtures.internal.NativeServicesTestFixture;
import org.gradle.util.TestUtil;

import javax.annotation.Nullable;
import java.io.File;

public class TestFiles {
    private static final FileSystem FILE_SYSTEM = NativeServicesTestFixture.getInstance().get(FileSystem.class);
    private static final DefaultFileLookup FILE_LOOKUP = new DefaultFileLookup(PatternSets.getNonCachingPatternSetFactory());
    private static final DefaultExecActionFactory EXEC_FACTORY = DefaultExecActionFactory.of(resolver(), fileCollectionFactory(), new DefaultExecutorFactory());

    public static FileLookup fileLookup() {
        return FILE_LOOKUP;
    }

    public static FileSystem fileSystem() {
        return FILE_SYSTEM;
    }

    public static FileResourceRepository fileRepository() {
        return new FileResourceConnector(FILE_SYSTEM);
    }

    /**
     * Returns a resolver with no base directory.
     */
    public static FileResolver resolver() {
        return FILE_LOOKUP.getFileResolver();
    }

    /**
     * Returns a resolver with no base directory.
     */
    public static PathToFileResolver pathToFileResolver() {
        return FILE_LOOKUP.getPathToFileResolver();
    }

    /**
     * Returns a resolver with the given base directory.
     */
    public static FileResolver resolver(File baseDir) {
        return FILE_LOOKUP.getFileResolver(baseDir);
    }

    /**
     * Returns a resolver with the given base directory.
     */
    public static PathToFileResolver pathToFileResolver(File baseDir) {
        return FILE_LOOKUP.getPathToFileResolver(baseDir);
    }

    public static DirectoryFileTreeFactory directoryFileTreeFactory() {
        return new DefaultDirectoryFileTreeFactory(getPatternSetFactory(), fileSystem());
    }

    public static Deleter deleter() {
        return new DefaultDeleter(Time.clock()::getCurrentTime, fileSystem()::isSymlink, false);
    }

    public static FileOperations fileOperations(File basedDir) {
        return fileOperations(basedDir, null);
    }

    public static FileOperations fileOperations(File basedDir, @Nullable TemporaryFileProvider temporaryFileProvider) {
        return new DefaultFileOperations(
            resolver(basedDir),
            temporaryFileProvider,
            TestUtil.instantiatorFactory().inject(),
            directoryFileTreeFactory(),
            streamHasher(),
            fileHasher(),
            textResourceLoader(),
            fileCollectionFactory(basedDir),
            fileSystem(),
            deleter()
        );
    }

    public static TextResourceLoader textResourceLoader() {
        return new BasicTextResourceLoader();
    }

    public static DefaultStreamHasher streamHasher() {
        return new DefaultStreamHasher();
    }

    public static DefaultFileHasher fileHasher() {
        return new DefaultFileHasher(streamHasher());
    }

    public static DefaultFileCollectionSnapshotter fileCollectionSnapshotter() {
        return new DefaultFileCollectionSnapshotter(fileSystemSnapshotter(), fileSystem());
    }

    public static DefaultFileSystemSnapshotter fileSystemSnapshotter() {
        return fileSystemSnapshotter(new DefaultFileSystemMirror(file -> false), new StringInterner());
    }

    public static DefaultFileSystemSnapshotter fileSystemSnapshotter(FileSystemMirror fileSystemMirror, StringInterner stringInterner) {
        return new DefaultFileSystemSnapshotter(
            fileHasher(),
            stringInterner,
            fileSystem(),
            fileSystemMirror
        );
    }

    public static FileCollectionFactory fileCollectionFactory() {
        return new DefaultFileCollectionFactory(pathToFileResolver(), DefaultTaskDependencyFactory.withNoAssociatedProject(), directoryFileTreeFactory(), getPatternSetFactory());
    }

    public static FileCollectionFactory fileCollectionFactory(File baseDir) {
        return new DefaultFileCollectionFactory(pathToFileResolver(baseDir), DefaultTaskDependencyFactory.withNoAssociatedProject(), directoryFileTreeFactory(), getPatternSetFactory());
    }

    public static ExecFactory execFactory() {
        return EXEC_FACTORY;
    }

    public static ExecFactory execFactory(File baseDir) {
        return execFactory().forContext(resolver(baseDir), fileCollectionFactory(baseDir), TestUtil.instantiatorFactory().inject(), TestUtil.objectFactory());
    }

    public static ExecActionFactory execActionFactory() {
        return execFactory();
    }

    public static ExecHandleFactory execHandleFactory() {
        return execFactory();
    }

    public static ExecHandleFactory execHandleFactory(File baseDir) {
        return execFactory(baseDir);
    }

    public static JavaExecHandleFactory javaExecHandleFactory(File baseDir) {
        return execFactory(baseDir);
    }

    public static Factory<PatternSet> getPatternSetFactory() {
        return resolver().getPatternSetFactory();
    }

    public static String systemSpecificAbsolutePath(String path) {
        return new File(path).getAbsolutePath();
    }
}
