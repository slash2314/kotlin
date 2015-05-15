/*
 * Copyright 2010-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments;
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments;
import org.jetbrains.kotlin.cli.js.K2JSCompiler;
import org.jetbrains.kotlin.utils.LibraryUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts Kotlin to JavaScript code
 *
 * @goal js-test
 * @phase test-compile
 * @requiresDependencyResolution test
 * @noinspection UnusedDeclaration
 */
public class KotlinTestJSCompilerMojo extends K2JSCompilerMojo {

    /**
     * Flag to allow test compilation to be skipped.
     *
     * @parameter expression="${maven.test.skip}" default-value="false"
     * @noinspection UnusedDeclaration
     */
    private boolean skip;

    /**
     * The default source directories containing the sources to be compiled.
     *
     * @parameter default-value="${project.testCompileSourceRoots}"
     * @required
     */
    private List<String> defaultSourceDirs;

    /**
     * The source directories containing the sources to be compiled.
     *
     * @parameter
     */
    private List<String> sourceDirs;

    @Override
    public List<String> getSources() {
        if (sourceDirs != null && !sourceDirs.isEmpty()) return sourceDirs;
        return defaultSourceDirs;
    }

    /**
     * The output JS file name
     *
     * @required
     * @parameter default-value="${project.build.directory}/test-js/${project.artifactId}-tests.js"
     */
    private String outputFile;

    /**
     * The output metafile name
     *
     * @parameter default-value="${project.build.directory}/test-js/${project.artifactId}-tests.meta.js"
     */
    private String metaFile;

    @Override
    protected void configureSpecificCompilerArguments(@NotNull K2JSCompilerArguments arguments) throws MojoExecutionException {
        module = testModule;
        output = testOutput;

        super.configureSpecificCompilerArguments(arguments);

        arguments.outputFile = outputFile;
        arguments.metaInfo = metaFile;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Test compilation is skipped");
        }
        else {
            super.execute();
        }
    }
}
