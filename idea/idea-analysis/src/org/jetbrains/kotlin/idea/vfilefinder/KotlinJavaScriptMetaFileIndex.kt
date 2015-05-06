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

package org.jetbrains.kotlin.idea.vfilefinder

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.ID
import com.intellij.util.indexing.ScalarIndexExtension
import org.jetbrains.kotlin.idea.decompiler.KotlinJavaScriptMetaFileType
import org.jetbrains.kotlin.idea.decompiler.navigation.JsMetaFileUtils
import org.jetbrains.kotlin.name.FqName

public class KotlinJavaScriptMetaFileIndex : ScalarIndexExtension<FqName>() {
    override fun getName() = KEY

    override fun getIndexer() = INDEXER

    override fun getKeyDescriptor() = KEY_DESCRIPTOR

    override fun getInputFilter() = INPUT_FILTER

    override fun dependsOnFileContent() = true

    override fun getVersion() = VERSION

    companion object {
        public val KEY: ID<FqName, Void> = ID.create(javaClass<KotlinJavaScriptMetaFileIndex>().getCanonicalName())

        private val LOG = Logger.getInstance(javaClass<KotlinJavaScriptMetaFileIndex>())

        private val VERSION = 1

        private val INPUT_FILTER = FileBasedIndex.InputFilter { file -> file.getFileType() == KotlinJavaScriptMetaFileType }

        private val INDEXER = indexer(LOG) {
            file -> if (file.getFileType() == KotlinJavaScriptMetaFileType) JsMetaFileUtils.getClassFqName(file) else null
        }
    }
}

