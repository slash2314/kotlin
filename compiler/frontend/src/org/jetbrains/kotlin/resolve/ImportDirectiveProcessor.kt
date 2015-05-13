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

package org.jetbrains.kotlin.resolve

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PackageViewDescriptor
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.resolve.scopes.JetScope
import org.jetbrains.kotlin.utils.sure
import kotlin.platform.platformStatic

public class ImportDirectiveProcessor(
        private val qualifiedExpressionResolver: QualifiedExpressionResolver
) {
    public fun processImportReference(
            importDirective: JetImportDirective,
            moduleDescriptor: ModuleDescriptor,
            trace: BindingTrace,
            lookupMode: QualifiedExpressionResolver.LookupMode,
            allowClassesFromDefaultPackage: Boolean
    ): JetScope {
        if (importDirective.isAbsoluteInRootPackage()) {
            trace.report(Errors.UNSUPPORTED.on(importDirective, "TypeHierarchyResolver")) // TODO
            return JetScope.Empty
        }

        val importedReference = importDirective.getImportedReference() ?: return JetScope.Empty
        //TODO_R: not valid import
        val (packageViewDescriptor, selectorsToLookUp) = tryResolvePackagesFromRightToLeft(moduleDescriptor, trace, importDirective.getImportPath()!!.fqnPart(), importedReference, emptyList())
        val descriptors = lookUpMembersFromLeftToRight(moduleDescriptor, trace, listOf(packageViewDescriptor), selectorsToLookUp, lookupMode)

        val referenceExpression = JetPsiUtil.getLastReference(importedReference)
        if (importDirective.isAllUnder()) {
            if (!canAllUnderImportFrom(descriptors) && referenceExpression != null) {
                val toReportOn = descriptors.filterIsInstance<ClassDescriptor>().first()
                trace.report(Errors.CANNOT_IMPORT_ON_DEMAND_FROM_SINGLETON.on(referenceExpression, toReportOn))
            }

            if (referenceExpression == null || !canImportMembersFrom(descriptors, referenceExpression, trace, lookupMode)) {
                return JetScope.Empty
            }

            val importsScope = AllUnderImportsScope()
            for (descriptor in descriptors) {
                importsScope.addAllUnderImport(descriptor)
            }
            return importsScope
        }
        else {
            val aliasName = JetPsiUtil.getAliasName(importDirective) ?: return JetScope.Empty
            return SingleImportScope(aliasName, descriptors)
        }
    }

    private fun tryResolvePackagesFromRightToLeft(
            moduleDescriptor: ModuleDescriptor,
            trace: BindingTrace,
            fqName: FqName,
            jetExpression: JetExpression?,
            selectorsToLookUp: List<JetSimpleNameExpression>
    ): Pair<PackageViewDescriptor, List<JetSimpleNameExpression>> {
        //TODO_R: verify
        val packageView = moduleDescriptor.getPackage(fqName)
        if (jetExpression == null) {
            assert(fqName.isRoot())
            return Pair(packageView.sure { "Root package does not exist in module $moduleDescriptor" }, selectorsToLookUp)
        }
        return when {
            packageView != null -> {
                recordPackageViews(jetExpression, packageView, trace)
                Pair(packageView, selectorsToLookUp)
            }
            else -> {
                assert(!fqName.isRoot())
                val (expressionRest, selector) = jetExpression.getReceiverAndSelector()
                tryResolvePackagesFromRightToLeft(moduleDescriptor, trace, fqName.parent(), expressionRest, listOf(selector) + selectorsToLookUp)
            }
        }
    }

    private fun recordPackageViews(jetExpression: JetExpression, packageView: PackageViewDescriptor, trace: BindingTrace) {
        trace.record(BindingContext.REFERENCE_TARGET, JetPsiUtil.getLastReference(jetExpression), packageView)
        val containingView = packageView.getContainingDeclaration()
        val (receiver, _) = jetExpression.getReceiverAndSelector()
        if (containingView != null && receiver != null) {
            recordPackageViews(receiver, containingView, trace)
        }
    }

    private fun lookUpMembersFromLeftToRight(
            moduleDescriptor: ModuleDescriptor,
            trace: BindingTrace,
            descriptors: Collection<DeclarationDescriptor>,
            selectorsToLookUp: List<JetSimpleNameExpression>,
            lookupMode: QualifiedExpressionResolver.LookupMode
    ): Collection<DeclarationDescriptor> {
        if (selectorsToLookUp.isEmpty()) {
            return descriptors
        }
        val nameReference = selectorsToLookUp.first()
        val selectorsRest = selectorsToLookUp.subList(1, selectorsToLookUp.size())
        val descriptorsBySelector = qualifiedExpressionResolver.lookupSelectorDescriptors(
                nameReference, descriptors, trace, moduleDescriptor, lookupMode, lookupMode.isEverything()
        )
        if (selectorsRest.isNotEmpty() && !canImportMembersFrom(descriptorsBySelector, nameReference, trace, lookupMode)) {
            return emptyList()
        }
        return lookUpMembersFromLeftToRight(
                moduleDescriptor, trace, descriptorsBySelector, selectorsRest, lookupMode
        )
    }

    public companion object {
        public fun canAllUnderImportFrom(descriptors: Collection<DeclarationDescriptor>): Boolean {
            if (descriptors.isEmpty()) {
                return true
            }
            return descriptors.any { it !is ClassDescriptor || canAllUnderImportFromClass(it) }
        }

        public fun canAllUnderImportFromClass(descriptor: ClassDescriptor): Boolean = !descriptor.getKind().isSingleton()

        platformStatic public fun canImportMembersFrom(
                descriptors: Collection<DeclarationDescriptor>,
                reference: JetSimpleNameExpression,
                trace: BindingTrace,
                lookupMode: QualifiedExpressionResolver.LookupMode
        ): Boolean {
            if (lookupMode.isOnlyClassesAndPackages()) {
                return true
            }

            descriptors.singleOrNull()?.let { return canImportMembersFrom(it, reference, trace, lookupMode) }

            val temporaryTrace = TemporaryBindingTrace.create(trace, "trace to find out if members can be imported from", reference)
            var canImport = false
            for (descriptor in descriptors) {
                canImport = canImport or canImportMembersFrom(descriptor, reference, temporaryTrace, lookupMode)
            }
            if (!canImport) {
                temporaryTrace.commit()
            }
            return canImport
        }

        private fun canImportMembersFrom(
                descriptor: DeclarationDescriptor,
                reference: JetSimpleNameExpression,
                trace: BindingTrace,
                lookupMode: QualifiedExpressionResolver.LookupMode
        ): Boolean {
            assert(lookupMode.isEverything())
            if (descriptor is PackageViewDescriptor || descriptor is ClassDescriptor) {
                return true
            }
            trace.report(Errors.CANNOT_IMPORT_FROM_ELEMENT.on(reference, descriptor))
            return false
        }
    }

    private fun JetExpression.getReceiverAndSelector(): Pair<JetExpression?, JetSimpleNameExpression> {
        when (this) {
            is JetDotQualifiedExpression -> {
                return Pair(this.getReceiverExpression(), this.getSelectorExpression() as JetSimpleNameExpression)
            }
            is JetSimpleNameExpression -> {
                return Pair(null, this)
            }
            else -> {
                throw AssertionError("Invalid expession in import $this of class ${this.javaClass}")
            }
        }
    }
}

private fun QualifiedExpressionResolver.LookupMode.isEverything() = this == QualifiedExpressionResolver.LookupMode.EVERYTHING
private fun QualifiedExpressionResolver.LookupMode.isOnlyClassesAndPackages() = this == QualifiedExpressionResolver.LookupMode.ONLY_CLASSES_AND_PACKAGES