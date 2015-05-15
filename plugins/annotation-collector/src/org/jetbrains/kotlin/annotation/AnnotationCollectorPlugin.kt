/*
 * Copyright 2010-2015 JetBrains s.r.o.
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

package org.jetbrains.kotlin.annotation

import com.intellij.mock.MockProject
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.codegen.extensions.ClassBuilderFactoryInterceptExtension
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CliOptionProcessingException
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.MemberDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.diagnostics.DiagnosticSink
import org.jetbrains.kotlin.diagnostics.DiagnosticWithParameters1
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.JetCallableDeclaration
import org.jetbrains.kotlin.psi.JetDeclaration
import org.jetbrains.kotlin.resolve.DescriptorUtils
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.io.Writer
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

public object AnnotationCollectorConfigurationKeys {
    public val ANNOTATION_LIST: CompilerConfigurationKey<List<String>> =
            CompilerConfigurationKey.create<List<String>>("annotation qualifiers to collect")
    public val OUTPUT_FILENAME: CompilerConfigurationKey<String> =
            CompilerConfigurationKey.create<String>("output file")
}

public class AnnotationCollectorCommandLineProcessor : CommandLineProcessor {
    companion object {
        public val ANNOTATION_COLLECTOR_COMPILER_PLUGIN_ID: String = "org.jetbrains.kotlin.annotation"

        public val ANNOTATION_LIST_OPTION: CliOption =
                CliOption("annotations", "<path>", "Qualified names of annotation classes, separated by commas", required = false)

        public val OUTPUT_FILENAME_OPTION: CliOption =
                CliOption("output", "<path>", "File in which annotated item declarations will be placed", required = false)
    }

    override val pluginId: String = ANNOTATION_COLLECTOR_COMPILER_PLUGIN_ID

    override val pluginOptions: Collection<CliOption> = listOf(ANNOTATION_LIST_OPTION, OUTPUT_FILENAME_OPTION)

    override fun processOption(option: CliOption, value: String, configuration: CompilerConfiguration) {
        when (option) {
            ANNOTATION_LIST_OPTION -> {
                val annotations = value.split(',').filter { !it.isEmpty() }.toList()
                configuration.put(AnnotationCollectorConfigurationKeys.ANNOTATION_LIST, annotations)
            }
            OUTPUT_FILENAME_OPTION -> configuration.put(AnnotationCollectorConfigurationKeys.OUTPUT_FILENAME, value)
            else -> throw CliOptionProcessingException("Unknown option: ${option.name}")
        }
    }
}

public class AnnotationCollectorComponentRegistrar : ComponentRegistrar {
    public override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
        val annotationFilterList = configuration.get(AnnotationCollectorConfigurationKeys.ANNOTATION_LIST)
        val outputFilename = configuration.get(AnnotationCollectorConfigurationKeys.OUTPUT_FILENAME)

        if (outputFilename != null) {
            val collectorExtension = AnnotationCollectorExtension(annotationFilterList, outputFilename)
            ClassBuilderFactoryInterceptExtension.registerExtension(project, collectorExtension)
        }
    }
}

