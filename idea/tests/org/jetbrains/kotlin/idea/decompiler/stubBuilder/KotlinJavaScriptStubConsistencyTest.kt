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

package org.jetbrains.kotlin.idea.decompiler.stubBuilder

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileContentImpl
import org.jetbrains.kotlin.idea.decompiler.textBuilder.buildDecompiledTextFromJsMetadata
import org.jetbrains.kotlin.idea.js.KotlinJavaScriptLibraryManager
import org.jetbrains.kotlin.idea.test.JetLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinStdJSProjectDescriptor
import org.jetbrains.kotlin.load.kotlin.PackageClassUtils
import org.jetbrains.kotlin.load.kotlin.VirtualFileFinderFactory
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.JetPsiFactory
import org.jetbrains.kotlin.psi.stubs.elements.JetFileStubBuilder
import org.junit.Assert

public class KotlinJavaScriptStubConsistencyTest : JetLightCodeInsightFixtureTestCase() {

    private val PACKAGES = hashSetOf(
            "java.util", "jquery", "jquery.ui", "kotlin", "kotlin.browser", "kotlin.dom", "kotlin.js"
    ).map { FqName(it) }

    public fun testConsistency() {
        PACKAGES.forEach { doTest(it) }
    }

    private fun doTest(packageFqName: FqName) {
        val project = getProject()

        KotlinJavaScriptLibraryManager.getInstance(project).syncUpdateProjectLibrary()

        val virtualFileFinder = VirtualFileFinderFactory.SERVICE.getInstance(project).create(GlobalSearchScope.allScope(project))
        val packageFile = virtualFileFinder.findVirtualFileWithKotlinJsMetadata(PackageClassUtils.getPackageClassId(packageFqName))!!

        val decompiledText = buildDecompiledTextFromJsMetadata(packageFile).text
        val jsMetaFileStub = KotlinJavaScriptStubBuilder().buildFileStub(FileContentImpl.createByFile(packageFile))!!

        val fileWithDecompiledText = JetPsiFactory(project).createFile(decompiledText)
        val stubTreeFromDecompiledText = JetFileStubBuilder().buildStubTree(fileWithDecompiledText)

        val expectedText = stubTreeFromDecompiledText.serializeToString()
        Assert.assertEquals(expectedText, jsMetaFileStub.serializeToString())
    }

    override fun getProjectDescriptor() = KotlinStdJSProjectDescriptor.instance
}
