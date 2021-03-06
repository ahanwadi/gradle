/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.recomp;

import org.gradle.api.Action;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.compile.incremental.SourceToNameConverter;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependencyInfo;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependencyInfoProvider;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarSnapshotter;
import org.gradle.api.internal.tasks.compile.incremental.jar.LocalJarSnapshotCache;
import org.gradle.api.internal.tasks.compile.incremental.model.PreviousCompilation;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.api.tasks.incremental.InputFileDetails;

public class RecompilationSpecProvider {

    private final SourceToNameConverter sourceToNameConverter;
    private final ClassDependencyInfoProvider dependencyInfoProvider;
    private final FileOperations fileOperations;
    private final JarSnapshotter jarSnapshotter;
    private LocalJarSnapshotCache jarSnapshotCache;

    public RecompilationSpecProvider(SourceToNameConverter sourceToNameConverter, ClassDependencyInfoProvider dependencyInfoProvider,
                                     FileOperations fileOperations, JarSnapshotter jarSnapshotter, LocalJarSnapshotCache jarSnapshotCache) {
        this.sourceToNameConverter = sourceToNameConverter;
        this.dependencyInfoProvider = dependencyInfoProvider;
        this.fileOperations = fileOperations;
        this.jarSnapshotter = jarSnapshotter;
        this.jarSnapshotCache = jarSnapshotCache;
    }

    public RecompilationSpec provideRecompilationSpec(IncrementalTaskInputs inputs) {
        //load the dependency info
        ClassDependencyInfo dependencyInfo = dependencyInfoProvider.provideInfo();
        PreviousCompilation previousCompilation = new PreviousCompilation(dependencyInfo, jarSnapshotCache);

        //creating an action that will be executed against all changes
        DefaultRecompilationSpec spec = new DefaultRecompilationSpec(dependencyInfo);
        JavaChangeProcessor javaChangeProcessor = new JavaChangeProcessor(dependencyInfo, sourceToNameConverter);
        JarChangeProcessor jarChangeProcessor = new JarChangeProcessor(fileOperations, jarSnapshotter, previousCompilation);
        InputChangeAction action = new InputChangeAction(spec, javaChangeProcessor, jarChangeProcessor);

        //go!
        inputs.outOfDate(action);
        if (action.spec.fullRebuildCause != null) {
            //short circuit in case we already know that that full rebuild is needed
            return action.spec;
        }
        inputs.removed(action);
        return action.spec;
    }

    private static class InputChangeAction implements Action<InputFileDetails> {
        private final DefaultRecompilationSpec spec;
        private final JavaChangeProcessor javaChangeProcessor;
        private final JarChangeProcessor jarChangeProcessor;

        public InputChangeAction(DefaultRecompilationSpec spec, JavaChangeProcessor javaChangeProcessor, JarChangeProcessor jarChangeProcessor) {
            this.spec = spec;
            this.javaChangeProcessor = javaChangeProcessor;
            this.jarChangeProcessor = jarChangeProcessor;
        }

        public void execute(InputFileDetails input) {
            if (spec.fullRebuildCause != null) {
                return;
            }
            if (input.getFile().getName().endsWith(".java")) {
                javaChangeProcessor.processChange(input, spec);
            }
            if (input.getFile().getName().endsWith(".jar")) {
                jarChangeProcessor.processChange(input, spec);
            }
        }
    }
}