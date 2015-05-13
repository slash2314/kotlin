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

package org.jetbrains.kotlin.idea.intentions.attributeCallReplacements

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.intentions.JetSelfTargetingOffsetIndependentIntention
import org.jetbrains.kotlin.idea.intentions.callExpression
import org.jetbrains.kotlin.idea.intentions.functionName
import org.jetbrains.kotlin.idea.intentions.toResolvedCall
import org.jetbrains.kotlin.psi.JetDotQualifiedExpression
import org.jetbrains.kotlin.psi.JetPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.resolve.calls.model.ArgumentMatch

public class ReplaceBinaryInfixIntention : JetSelfTargetingOffsetIndependentIntention<JetDotQualifiedExpression>(javaClass(), "Replace call with binary operator") {
    override fun isApplicableTo(element: JetDotQualifiedExpression): Boolean {
        val operation = operation(element.functionName) ?: return false
        val resolvedCall = element.toResolvedCall() ?: return false
        if (!resolvedCall.getStatus().isSuccess()) return false
        if (resolvedCall.getCall().getTypeArgumentList() != null) return false
        val argument = resolvedCall.getCall().getValueArguments().singleOrNull() ?: return false
        if ((resolvedCall.getArgumentMapping(argument) as ArgumentMatch).valueParameter.getIndex() != 0) return false
        setText("Replace with '$operation' operator")
        return true
    }

    override fun applyTo(element: JetDotQualifiedExpression, editor: Editor) {
        val operation = operation(element.functionName)!!
        val argument = element.callExpression!!.getValueArguments().single().getArgumentExpression()!!
        val receiver = element.getReceiverExpression()

        element.replace(JetPsiFactory(element).createExpressionByPattern("$0 $operation $1", receiver, argument))
    }

    private fun operation(functionName: String?): String? {
        return when (functionName) {
            "plus" -> "+"
            "minus" -> "-"
            "div" -> "/"
            "times" -> "*"
            "mod" -> "%"
            "rangeTo" -> ".."
            else -> null
        }
    }
}
