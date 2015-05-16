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

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithVisibility
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.imports.canBeReferencedViaImport
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.quickfix.moveCaret
import org.jetbrains.kotlin.idea.util.ShortenReferences
import org.jetbrains.kotlin.idea.util.isUnit
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.isExtension
import org.jetbrains.kotlin.utils.addIfNotNull
import java.util.ArrayList

//TODO: inspection
//TODO: descriptions for intention and inspection
public class DeprecatedCallableAddReplaceWithIntention : JetSelfTargetingRangeIntention<JetCallableDeclaration>(
        javaClass(), "Add 'replaceWith' argument to specify replacement pattern", "Add 'replaceWith' argument to 'deprecated' annotation"
) {
    //TODO: use ReplaceWith from package kotlin
    private class ReplaceWith(val expression: String, vararg val imports: String)

    override fun applicabilityRange(element: JetCallableDeclaration): TextRange? {
        if (element.replaceWithFromBody() == null) return null
        return element.deprecatedAnnotationWithNoReplaceWith()?.getTextRange()
    }

    override fun applyTo(element: JetCallableDeclaration, editor: Editor) {
        val replaceWith = element.replaceWithFromBody()!!

        assert('\n' !in replaceWith.expression && '\r' !in replaceWith.expression, "Formatted expression text should not contain \\n or \\r")

        val annotationEntry = element.deprecatedAnnotationWithNoReplaceWith()!!
        val psiFactory = JetPsiFactory(element)

        val escapedText = replaceWith.expression
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")

        val argumentText = StringBuilder {
            append("kotlin.ReplaceWith(\"")
            append(escapedText)
            append("\"")
            replaceWith.imports.forEach { append(",\"").append(it).append("\"") }
        }.toString()

        var argument = psiFactory.createArgument(psiFactory.createExpression(argumentText))
        argument = annotationEntry.getValueArgumentList().addArgument(argument)
        argument = ShortenReferences.DEFAULT.process(argument) as JetValueArgument

        PsiDocumentManager.getInstance(argument.getProject()).doPostponedOperationsAndUnblockDocument(editor.getDocument())
        editor.moveCaret(argument.getTextOffset())
    }

    private fun JetCallableDeclaration.deprecatedAnnotationWithNoReplaceWith(): JetAnnotationEntry? {
        val bindingContext = this.analyze()
        val deprecatedConstructor = KotlinBuiltIns.getInstance().getDeprecatedAnnotation().getUnsubstitutedPrimaryConstructor()
        for (entry in getAnnotationEntries()) {
            val resolvedCall = entry.getCalleeExpression().getResolvedCall(bindingContext) ?: continue
            if (!resolvedCall.getStatus().isSuccess()) continue
            if (resolvedCall.getResultingDescriptor() != deprecatedConstructor) continue
            val replaceWithArguments = resolvedCall.getValueArguments().entrySet()
                    .single { it.key.getName().asString() == "replaceWith"/*TODO*/ }.value
            return if (replaceWithArguments.getArguments().isEmpty()) entry else null
        }
        return null
    }

    private fun JetCallableDeclaration.replaceWithFromBody(): ReplaceWith? {
        val replacementExpression = when (this) {
            is JetNamedFunction -> {
                val body = getBodyExpression() ?: return null
                if (hasBlockBody()) {
                    val block = body as? JetBlockExpression ?: return null
                    val statement = block.getStatements().singleOrNull() as? JetExpression ?: return null
                    val returnsUnit = (analyze()[BindingContext.DECLARATION_TO_DESCRIPTOR, this] as? FunctionDescriptor)?.getReturnType()?.isUnit() ?: true
                    when (statement) {
                        is JetReturnExpression -> statement.getReturnedExpression()
                        else -> if (returnsUnit) statement else null
                    }
                }
                else {
                    body
                }
            }

        //TODO: properties
            else -> null
        } ?: return null

        var isGood = true
        replacementExpression.accept(object: JetVisitorVoid(){
            override fun visitReturnExpression(expression: JetReturnExpression) {
                isGood = false
            }

            override fun visitDeclaration(dcl: JetDeclaration) {
                isGood = false
            }

            override fun visitSimpleNameExpression(expression: JetSimpleNameExpression) {
                val target = expression.analyze()[BindingContext.REFERENCE_TARGET, expression] as? DeclarationDescriptorWithVisibility ?: return
                if (Visibilities.isPrivate((target.getVisibility()))) {
                    isGood = false
                }
            }

            override fun visitJetElement(element: JetElement) {
                element.acceptChildren(this)
            }
        })
        if (!isGood) return null

        val text = replacementExpression.getText()
        var expression = JetPsiFactory(this).createExpression(text.replace('\n', ' '))
        expression = CodeStyleManager.getInstance(getProject()).reformat(expression, true) as JetExpression

        return ReplaceWith(expression.getText(), *extractImports(replacementExpression).toTypedArray())
    }

    private fun extractImports(expression: JetExpression): Collection<String> {
        val currentPackageFqName = expression.getContainingJetFile().getPackageFqName()
        val result = ArrayList<String>()
        expression.accept(object : JetVisitorVoid(){
            override fun visitSimpleNameExpression(expression: JetSimpleNameExpression) {
                val target = expression.analyze()[BindingContext.REFERENCE_TARGET, expression] ?: return
                if (target.canBeReferencedViaImport()) {
                    if (target.isExtension || expression.getReceiverExpression() == null) {
                        if ((target.getContainingDeclaration() as? PackageFragmentDescriptor)?.fqName == currentPackageFqName) return
                        result.addIfNotNull(target.importableFqName?.asString())
                    }
                }
            }

            override fun visitJetElement(element: JetElement) {
                element.acceptChildren(this)
            }
        })
        return result
    }
}