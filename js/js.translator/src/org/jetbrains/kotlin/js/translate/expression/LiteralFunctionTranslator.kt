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

package org.jetbrains.kotlin.js.translate.expression

import com.google.dart.compiler.backend.js.ast.*
import com.google.dart.compiler.backend.js.ast.metadata.isLocal
import com.google.dart.compiler.backend.js.ast.metadata.staticRef
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.js.inline.util.getInnerFunction
import org.jetbrains.kotlin.js.translate.context.TranslationContext
import org.jetbrains.kotlin.js.translate.context.getNameForCapturedDescriptor
import org.jetbrains.kotlin.js.translate.context.hasCapturedExceptContaining
import org.jetbrains.kotlin.js.translate.context.isCaptured
import org.jetbrains.kotlin.js.translate.general.AbstractTranslator
import org.jetbrains.kotlin.js.translate.utils.BindingUtils.getFunctionDescriptor
import org.jetbrains.kotlin.js.translate.utils.FunctionBodyTranslator.translateFunctionBody
import org.jetbrains.kotlin.js.translate.utils.JsAstUtils
import org.jetbrains.kotlin.js.translate.utils.TranslationUtils.simpleReturnFunction
import org.jetbrains.kotlin.psi.JetDeclarationWithBody
import org.jetbrains.kotlin.resolve.inline.InlineUtil

class LiteralFunctionTranslator(context: TranslationContext) : AbstractTranslator(context) {
    fun translate(declaration: JetDeclarationWithBody): JsExpression {
        val invokingContext = context()
        val descriptor = getFunctionDescriptor(invokingContext.bindingContext(), declaration)

        val lambda = invokingContext.getFunctionObject(descriptor)
        val functionContext = invokingContext.newFunctionBodyWithUsageTracker(lambda, descriptor)

        FunctionTranslator.addParameters(lambda.getParameters(), descriptor, functionContext)
        val functionBody = translateFunctionBody(descriptor, declaration, functionContext)
        lambda.getBody().getStatements().addAll(functionBody.getStatements())

        val tracker = functionContext.usageTracker()!!

        val isRecursive = tracker.isCaptured(descriptor)

        if (isRecursive) {
            lambda.setName(tracker.getNameForCapturedDescriptor(descriptor))
        }

        if (tracker.hasCapturedExceptContaining()) {
            val lambdaCreator = simpleReturnFunction(invokingContext.scope(), lambda)
            lambdaCreator.isLocal = true
            return lambdaCreator.withCapturedParameters(functionContext, invokingContext, descriptor)
        }

        lambda.isLocal = true
        return invokingContext.define(descriptor, lambda)
    }
}

fun JsFunction.withCapturedParameters(context: TranslationContext, invokingContext: TranslationContext, descriptor: MemberDescriptor): JsExpression {

    fun getParameterNameRefForInvocation(descriptor: DeclarationDescriptor): JsExpression {
        val alias = invokingContext.getAliasForDescriptor(descriptor)
        if (alias != null) return alias

        if (descriptor is ReceiverParameterDescriptor) return JsLiteral.THIS

        return invokingContext.getNameForDescriptor(descriptor).makeRef()
    }

    val ref = invokingContext.define(descriptor, this)
    val invocation = JsInvocation(ref)

    val invocationArguments = invocation.getArguments()
    val functionParameters = this.getParameters()

    val tracker = context.usageTracker()!!

    for ((capturedDescriptor, name) in tracker.capturedDescriptorToJsName) {
        if (capturedDescriptor == tracker.containingDescriptor) continue

        val capturedRef = getParameterNameRefForInvocation(capturedDescriptor)
        var additionalArgs = listOf(capturedRef)
        var additionalParams = listOf(JsParameter(name))

        if (capturedDescriptor is CallableDescriptor && isLocalInlineDeclaration(capturedDescriptor)) {
            val aliasRef = capturedRef as? JsNameRef
            val localFunAlias = aliasRef?.getStaticRef() as? JsExpression

            if (localFunAlias != null) {
                val (args, params) = moveCapturedLocalInside(this, name, localFunAlias)
                additionalArgs = args
                additionalParams = params
            }
        }

        functionParameters.addAll(additionalParams)
        invocationArguments.addAll(additionalArgs)
    }

    return invocation
}

private data class CapturedArgsParams(val arguments: List<JsExpression> = listOf(), val parameters: List<JsParameter> = listOf())

/**
 * Moves captured local inline function inside capturing function.
 *
 * For example:
 *  var inc = _.foo.inc(closure) // local fun that captures closure
 *  capturingFunction(inc)
 *
 * Is transformed to:
 *  capturingFunction(closure) // var inc = _.foo.inc(closure) is moved inside capturingFunction
 */
private fun moveCapturedLocalInside(capturingFunction: JsFunction, capturedName: JsName, localFunAlias: JsExpression): CapturedArgsParams =
    when (localFunAlias) {
        is JsNameRef -> {
            /** Local inline function does not capture anything, so just move alias inside */
            declareAliasInsideFunction(capturingFunction, capturedName, localFunAlias)
            CapturedArgsParams()
        }
        is JsInvocation ->
            moveCapturedLocalInside(capturingFunction, capturedName, localFunAlias)
        else ->
            throw AssertionError("Local function reference has wrong alias $localFunAlias")
    }

/**
 * Processes case when local inline function with capture
 * is captured by capturingFunction.
 *
 * In this case, capturingFunction should
 * capture arguments captured by localFunAlias,
 * and localFunAlias declaration is moved inside.
 *
 * For example:
 *  val x = 0
 *  [inline] fun id() = x
 *  val lambda = {println(id())}
 *
 * `lambda` should capture x in this case
 */
private fun moveCapturedLocalInside(capturingFunction: JsFunction, capturedName: JsName, localFunAlias: JsInvocation): CapturedArgsParams {
    val capturedArgs = localFunAlias.getArguments()

    val scope = capturingFunction.getInnerFunction()?.getScope()!!
    val freshNames = getFreshNamesInScope(scope, capturedArgs)

    val aliasCallArguments = freshNames.map { it.makeRef() }
    val alias = JsInvocation(localFunAlias.getQualifier(), aliasCallArguments)
    declareAliasInsideFunction(capturingFunction, capturedName, alias)

    val capturedParameters = freshNames.map {JsParameter(it)}
    return CapturedArgsParams(capturedArgs, capturedParameters)
}

private fun declareAliasInsideFunction(function: JsFunction, name: JsName, alias: JsExpression) {
    name.staticRef = alias
    function.getInnerFunction()?.addDeclaration(name, alias)
}

private fun getFreshNamesInScope(scope: JsScope, suggested: List<JsExpression>): List<JsName> {
    val freshNames = arrayListOf<JsName>()

    for (suggestion in suggested) {
        if (suggestion !is JsNameRef) {
            throw AssertionError("Expected suggestion to be JsNameRef")
        }

        val ident = suggestion.getIdent()
        val name = scope.declareFreshName(ident)
        freshNames.add(name)
    }

    return freshNames
}

private fun JsFunction.addDeclaration(name: JsName, value: JsExpression?) {
    val declaration = JsAstUtils.newVar(name, value)
    this.getBody().getStatements().add(0, declaration)
}

private fun HasName.getStaticRef(): JsNode? {
    return this.getName()?.staticRef
}

private fun isLocalInlineDeclaration(descriptor: CallableDescriptor): Boolean {
    return descriptor is FunctionDescriptor
           && descriptor.getVisibility() == Visibilities.LOCAL
           && InlineUtil.isInline(descriptor)
}
