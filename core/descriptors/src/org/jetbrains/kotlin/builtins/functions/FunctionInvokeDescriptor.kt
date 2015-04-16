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

package org.jetbrains.kotlin.builtins.functions

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.Variance

// TODO: make parameter names synthetic / non-stable
public class FunctionInvokeDescriptor private(
        private val container: DeclarationDescriptor,
        private val original: FunctionInvokeDescriptor?,
        private val callableKind: CallableMemberDescriptor.Kind
) : SimpleFunctionDescriptorImpl(
        container,
        original,
        Annotations.EMPTY,
        Name.identifier("invoke"),
        callableKind,
        SourceElement.NO_SOURCE
) {
    override fun createSubstitutedCopy(
            newOwner: DeclarationDescriptor,
            original: FunctionDescriptor?,
            kind: CallableMemberDescriptor.Kind
    ): FunctionInvokeDescriptor {
        return FunctionInvokeDescriptor(newOwner, original as? FunctionInvokeDescriptor, kind)
    }

    companion object Factory {
        fun create(functionClass: FunctionClassDescriptor): FunctionInvokeDescriptor {
            val typeParameters = functionClass.getTypeConstructor().getParameters()

            val result = FunctionInvokeDescriptor(functionClass, null, CallableMemberDescriptor.Kind.DECLARATION)
            result.initialize(
                    null,
                    functionClass.getThisAsReceiverParameter(),
                    listOf(),
                    typeParameters.takeWhile { it.getVariance() == Variance.IN_VARIANCE }.withIndex().map {
                        val (index, typeParameter) = it
                        ValueParameterDescriptorImpl(
                                result, null, index, Annotations.EMPTY,
                                // Type parameter "P1" -> value parameter "p1", "P2" -> "p2", etc.
                                Name.identifier(typeParameter.getName().asString().toLowerCase()),
                                typeParameter.getDefaultType(),
                                /* declaresDefaultValue = */ false,
                                /* varargElementType = */ null,
                                SourceElement.NO_SOURCE
                        )
                    },
                    typeParameters.last().getDefaultType(),
                    Modality.ABSTRACT,
                    Visibilities.PUBLIC
            )
            return result
        }
    }
}
