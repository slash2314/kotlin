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

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.DirectoryIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.analyzer.PackageViewManagerProvider
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PackageViewDescriptor
import org.jetbrains.kotlin.descriptors.PackageViewManager
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.PackageViewManagerImpl
import org.jetbrains.kotlin.idea.stubindex.PackageIndexUtil
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.storage.StorageManager

public class IdePackageViewManagerProvider(private val project: Project) : PackageViewManagerProvider {
    override fun createPVM(moduleDescriptor: ModuleDescriptor, info: ModuleInfo, storageManager: StorageManager): PackageViewManager {
        val delegate = PackageViewManagerImpl(moduleDescriptor as ModuleDescriptorImpl)
        val ideaModuleInfo = info as IdeaModuleInfo
        if (ideaModuleInfo !is ModuleSourceInfo) return delegate

        val ideaModule = ideaModuleInfo.module
        val moduleWithDependenciesScope = ideaModule.getModuleWithDependenciesAndLibrariesScope(ideaModuleInfo.isTests())

        return object : PackageViewManager {
            private val packages = storageManager.createMemoizedFunctionWithNullableValues { fqName: FqName ->
                if (!packageExistsInJavaOrKotlin(project, moduleWithDependenciesScope, fqName)) null else delegate.getPackage(fqName)
            }

            override fun getPackage(fqName: FqName): PackageViewDescriptor? = packages(fqName)

            override fun getSubPackagesOf(fqName: FqName, nameFilter: (Name) -> Boolean): Collection<FqName> {
                return delegate.getSubPackagesOf(fqName, nameFilter)
            }

        }
    }
}

private fun packageExistsInJavaOrKotlin(project: Project, scope: GlobalSearchScope, fqName: FqName): Boolean {
    val directoriesQuery = DirectoryIndex.getInstance(project).getDirectoriesByPackageName(fqName.asString(), false)
    val notFound = directoriesQuery.forEach(Processor { vFile ->
        if (vFile in scope) false else true
    })
    if (notFound) {
        return PackageIndexUtil.packageExists(fqName, scope, project)
    }
    return true
}