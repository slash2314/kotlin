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

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.codegen.ClassBuilder
import org.jetbrains.kotlin.codegen.ClassBuilderFactory
import org.jetbrains.kotlin.codegen.DelegatingClassBuilder
import org.jetbrains.kotlin.codegen.extensions.ClassBuilderFactoryInterceptExtension
import org.jetbrains.kotlin.diagnostics.DiagnosticSink
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.jvm.diagnostics.JvmDeclarationOrigin
import org.jetbrains.org.objectweb.asm.*
import java.io.File
import java.io.IOException
import java.io.StringWriter
import java.io.Writer
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import kotlin.properties.Delegates

public class AnnotationCollectorExtension(
        override val annotationFilterList: List<String>? = null,
        val outputFilename: String? = null
) : AnnotationCollectorExtensionBase() {

    override val collectAnnotations = outputFilename != null

    override fun obtainWriter(diagnostic: DiagnosticSink): Writer? {
        if (collectAnnotations) {
            try {
                return with (File(outputFilename)) {
                    val parent = getParentFile()
                    if (!parent.exists()) parent.mkdirs()
                    bufferedWriter()
                }
            }
            catch (e: IOException) {
                e.printStackTrace()
                return null
            }
        }
        else {
            return StringWriter()
        }
    }
}

public abstract class AnnotationCollectorExtensionBase() : ClassBuilderFactoryInterceptExtension {

    private companion object {
        val ANNOTATED_CLASS = "c"
        val ANNOTATED_METHOD = "m"
        val ANNOTATED_FIELD = "f"

        val SHORTENED_ANNOTATION = "a"
        val SHORTENED_PACKAGE_NAME = "p"
    }

    protected abstract val annotationFilterList: List<String>?

    protected open val collectAnnotations: Boolean = true

    private val classBuilderFactories = arrayListOf<AnnotationCollectorClassBuilderFactory>()

    private val shortenedAnnotationCache = hashMapOf<String, String>()
    private var shortenedAnnotationCacheCounter: Int = 0

    private val shortenedPackageNameCache = hashMapOf<String, String>()
    private var shortenedPackageNameCacheCounter: Int = 0

    override fun interceptClassBuilderFactory(
            parentFactory: ClassBuilderFactory,
            bindingContext: BindingContext,
            diagnostics: DiagnosticSink
    ): ClassBuilderFactory {
        val factory = AnnotationCollectorClassBuilderFactory(parentFactory, obtainWriter(diagnostics), diagnostics)
        classBuilderFactories.add(factory)
        return factory
    }

    protected abstract fun obtainWriter(diagnostic: DiagnosticSink): Writer?

    override fun onDestroy() {
        for (factory in classBuilderFactories) {
            factory.writer?.close()
        }
        classBuilderFactories.clear()
    }

    private inner class AnnotationCollectorClassBuilderFactory(
            private val delegateFactory: ClassBuilderFactory,
            val writer: Writer?,
            val diagnostics: DiagnosticSink
    ) : ClassBuilderFactory {

        override fun newClassBuilder(origin: JvmDeclarationOrigin): ClassBuilder {
            return AnnotationCollectorClassBuilder(delegateFactory.newClassBuilder(origin), writer, diagnostics)
        }

        override fun getClassBuilderMode() = delegateFactory.getClassBuilderMode()

        override fun asText(builder: ClassBuilder?): String? {
            return delegateFactory.asText((builder as AnnotationCollectorClassBuilder).delegateClassBuilder)
        }

        override fun asBytes(builder: ClassBuilder?): ByteArray? {
            return delegateFactory.asBytes((builder as AnnotationCollectorClassBuilder).delegateClassBuilder)
        }
    }

    private inner class AnnotationCollectorClassBuilder(
            internal val delegateClassBuilder: ClassBuilder,
            val writer: Writer?,
            val diagnostics: DiagnosticSink
    ) : DelegatingClassBuilder() {
        private val annotationFilterEnabled: Boolean
        private val annotationFilters: List<Pattern>

        private val classVisitor: ClassVisitor by Delegates.lazy {
            object : ClassVisitor(Opcodes.ASM5, super.getVisitor()) {
                override fun visitAnnotation(desc: String, visible: Boolean): AnnotationVisitor? {
                    recordAnnotation(null, ANNOTATED_CLASS, desc)
                    return super.visitAnnotation(desc, visible)
                }
            }
        }

        private var currentClassSimpleName: String? = null
        private var currentPackageName: String? = null

        init {
            val nullableAnnotations = annotationFilterList?.map { it.compilePatternOpt() } ?: listOf()
            annotationFilterEnabled = nullableAnnotations.isNotEmpty()
            annotationFilters = nullableAnnotations.filterNotNull()
        }

        override fun getVisitor() = classVisitor

        override fun getDelegate() = delegateClassBuilder

        override fun defineClass(
                origin: PsiElement?,
                version: Int,
                access: Int,
                name: String,
                signature: String?,
                superName: String,
                interfaces: Array<out String>
        ) {
            currentClassSimpleName = name.substringAfterLast('/')
            currentPackageName = name.substringBeforeLast('/', "").replace('/', '.')

            super.defineClass(origin, version, access, name, signature, superName, interfaces)
        }

        override fun newField(
                origin: JvmDeclarationOrigin,
                access: Int,
                name: String,
                desc: String,
                signature: String?,
                value: Any?
        ): FieldVisitor {
            return object : FieldVisitor(Opcodes.ASM5, super.newField(origin, access, name, desc, signature, value)) {
                override fun visitAnnotation(desc: String, visible: Boolean): AnnotationVisitor? {
                    recordAnnotation(name, ANNOTATED_FIELD, desc)
                    return super.visitAnnotation(desc, visible)
                }
            }
        }

        override fun newMethod(
                origin: JvmDeclarationOrigin,
                access: Int,
                name: String,
                desc: String,
                signature: String?,
                exceptions: Array<out String>?
        ): MethodVisitor {
            return object : MethodVisitor(Opcodes.ASM5, super.newMethod(origin, access, name, desc, signature, exceptions)) {
                override fun visitAnnotation(desc: String, visible: Boolean): AnnotationVisitor? {
                    recordAnnotation(name, ANNOTATED_METHOD, desc)
                    return super.visitAnnotation(desc, visible)
                }
            }
        }

        private fun isAnnotationHandled(annotationFqName: String): Boolean {
            return if (annotationFilterEnabled) {
                annotationFilters.any { it.matcher(annotationFqName).matches() }
            }
            else !annotationFqName.startsWith("kotlin.jvm.internal.")
        }

        private synchronized fun recordAnnotation(name: String?, type: String, annotationDesc: String) {
            val annotationFqName = Type.getType(annotationDesc).getClassName()
            if (!isAnnotationHandled(annotationFqName)) return

            try {
                writer?.let { writer ->
                    val annotationId = shortenedAnnotationCache.getOrPut(annotationFqName) {
                        val retId = shortenedAnnotationCacheCounter.toString()
                        writer.write("$SHORTENED_ANNOTATION $annotationFqName $retId\n")
                        shortenedAnnotationCacheCounter += 1
                        retId
                    }

                    val packageName = this.currentPackageName!!
                    val className = this.currentClassSimpleName!!

                    val packageNameId = if (!packageName.isEmpty()) {
                        shortenedPackageNameCache.getOrPut(packageName) {
                            val retId = shortenedPackageNameCacheCounter.toString()
                            writer.write("$SHORTENED_PACKAGE_NAME $packageName $retId\n")
                            shortenedPackageNameCacheCounter += 1
                            retId
                        }
                    } else null

                    val outputClassName = if (packageNameId == null) {
                        if (packageName.isEmpty()) className else "$packageName.$className"
                    }
                    else "$packageNameId/$className"

                    val elementName = if (name != null) " $name" else ""

                    writer.write("$type $annotationId $outputClassName$elementName\n")
                }
            }
            catch (e: IOException) {
                e.printStackTrace()
            }
        }

        private fun String.compilePatternOpt(): Pattern? {
            return try {
                Pattern.compile(this)
            }
            catch (e: PatternSyntaxException) {
                null
            }
        }
    }
}