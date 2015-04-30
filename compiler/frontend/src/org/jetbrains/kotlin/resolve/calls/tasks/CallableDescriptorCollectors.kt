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

package org.jetbrains.kotlin.resolve.calls.tasks.collectors

import org.jetbrains.kotlin.builtins.functions.FunctionClassDescriptor
import org.jetbrains.kotlin.builtins.functions.FunctionInvokeDescriptor
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.DescriptorUtils.isStaticNestedClass
import org.jetbrains.kotlin.resolve.LibrarySourceHacks
import org.jetbrains.kotlin.resolve.calls.util.FakeCallableDescriptorForObject
import org.jetbrains.kotlin.resolve.descriptorUtil.hasClassObjectType
import org.jetbrains.kotlin.resolve.scopes.JetScope
import org.jetbrains.kotlin.types.ErrorUtils
import org.jetbrains.kotlin.types.JetType
import org.jetbrains.kotlin.types.TypeSubstitutor
import org.jetbrains.kotlin.types.expressions.OperatorConventions

public trait CallableDescriptorCollector<D : CallableDescriptor> {

    public fun getNonExtensionsByName(scope: JetScope, name: Name, bindingTrace: BindingTrace): Collection<D>

    public fun getMembersByName(receiver: JetType, name: Name, bindingTrace: BindingTrace): Collection<D>

    public fun getStaticMembersByName(receiver: JetType, name: Name, bindingTrace: BindingTrace): Collection<D>

    public fun getExtensionsByName(scope: JetScope, name: Name, bindingTrace: BindingTrace): Collection<D>
}

private fun <D : CallableDescriptor> CallableDescriptorCollector<D>.withDefaultFilter() = filtered { !LibrarySourceHacks.shouldSkip(it) }

private val FUNCTIONS_COLLECTOR = FunctionCollector.withDefaultFilter()
private val VARIABLES_COLLECTOR = VariableCollector.withDefaultFilter()
private val PROPERTIES_COLLECTOR = PropertyCollector.withDefaultFilter()

public class CallableDescriptorCollectors<D : CallableDescriptor>(val collectors: List<CallableDescriptorCollector<D>>) :
        Iterable<CallableDescriptorCollector<D>> {
    override fun iterator(): Iterator<CallableDescriptorCollector<D>> = collectors.iterator()

    [suppress("UNCHECKED_CAST")] companion
    object {
        public val FUNCTIONS_AND_VARIABLES: CallableDescriptorCollectors<CallableDescriptor> =
                CallableDescriptorCollectors(listOf(
                        FUNCTIONS_COLLECTOR as CallableDescriptorCollector<CallableDescriptor>,
                        VARIABLES_COLLECTOR as CallableDescriptorCollector<CallableDescriptor>
                ))
        public val FUNCTIONS: CallableDescriptorCollectors<CallableDescriptor> =
                CallableDescriptorCollectors(listOf(FUNCTIONS_COLLECTOR as CallableDescriptorCollector<CallableDescriptor>))
        public val VARIABLES: CallableDescriptorCollectors<VariableDescriptor> = CallableDescriptorCollectors(listOf(VARIABLES_COLLECTOR))
        public val PROPERTIES: CallableDescriptorCollectors<VariableDescriptor> = CallableDescriptorCollectors(listOf(PROPERTIES_COLLECTOR))
    }
}

public fun <D : CallableDescriptor> CallableDescriptorCollectors<D>.filtered(filter: (D) -> Boolean): CallableDescriptorCollectors<D> =
        CallableDescriptorCollectors(this.collectors.map { it.filtered(filter) })

private object FunctionCollector : CallableDescriptorCollector<FunctionDescriptor> {

    override fun getNonExtensionsByName(scope: JetScope, name: Name, bindingTrace: BindingTrace): Collection<FunctionDescriptor> {
        return scope.getFunctions(name).filter { it.getExtensionReceiverParameter() == null } + getConstructors(scope, name)
    }

    override fun getMembersByName(receiver: JetType, name: Name, bindingTrace: BindingTrace): Collection<FunctionDescriptor> {
        val receiverScope = receiver.getMemberScope()
        return receiverScope.getFunctions(name) + getConstructors(receiverScope, name, { !isStaticNestedClass(it) })
    }

    override fun getStaticMembersByName(receiver: JetType, name: Name, bindingTrace: BindingTrace): Collection<FunctionDescriptor> {
        return getConstructors(receiver.getMemberScope(), name, { isStaticNestedClass(it) })
    }

    override fun getExtensionsByName(scope: JetScope, name: Name, bindingTrace: BindingTrace): Collection<FunctionDescriptor> {
        return scope.getFunctions(name).filter { it.getExtensionReceiverParameter() != null }
    }

    private fun getConstructors(
            scope: JetScope, name: Name, filterClassPredicate: (ClassDescriptor) -> Boolean = { true }
    ): Collection<FunctionDescriptor> {
        val classifier = scope.getClassifier(name)
        if (classifier !is ClassDescriptor || ErrorUtils.isError(classifier) || !filterClassPredicate(classifier)
            // Constructors of singletons shouldn't be callable from the code
            || classifier.getKind().isSingleton()) {
            return listOf()
        }
        return classifier.getConstructors()
    }

    override fun toString() = "FUNCTIONS"
}

private object VariableCollector : CallableDescriptorCollector<VariableDescriptor> {

    private fun getFakeDescriptorForObject(scope: JetScope, name: Name): VariableDescriptor? {
        val classifier = scope.getClassifier(name)
        if (classifier !is ClassDescriptor || !classifier.hasClassObjectType) return null

        return FakeCallableDescriptorForObject(classifier)
    }

    override fun getNonExtensionsByName(scope: JetScope, name: Name, bindingTrace: BindingTrace): Collection<VariableDescriptor> {
        val localVariable = scope.getLocalVariable(name)
        if (localVariable != null) {
            return setOf(localVariable)
        }
        return (scope.getProperties(name).filter { it.getExtensionReceiverParameter() == null } + getFakeDescriptorForObject(scope, name))
                .filterNotNull()
    }

    override fun getMembersByName(receiver: JetType, name: Name, bindingTrace: BindingTrace): Collection<VariableDescriptor> {
        val memberScope = receiver.getMemberScope()
        return (memberScope.getProperties(name) + getFakeDescriptorForObject(memberScope, name)).filterNotNull()
    }

    override fun getStaticMembersByName(receiver: JetType, name: Name, bindingTrace: BindingTrace): Collection<VariableDescriptor> {
        return listOf()
    }

    override fun getExtensionsByName(scope: JetScope, name: Name, bindingTrace: BindingTrace): Collection<VariableDescriptor> {
        // property may have an extension function type, we check the applicability later to avoid an early computing of deferred types
        return (listOf(scope.getLocalVariable(name)) + scope.getProperties(name)).filterNotNull()
    }

    override fun toString() = "VARIABLES"
}

private object PropertyCollector : CallableDescriptorCollector<VariableDescriptor> {
    private fun filterProperties(variableDescriptors: Collection<VariableDescriptor>) =
            variableDescriptors.filter { it is PropertyDescriptor }

    override fun getNonExtensionsByName(scope: JetScope, name: Name, bindingTrace: BindingTrace): Collection<VariableDescriptor> {
        return filterProperties(VARIABLES_COLLECTOR.getNonExtensionsByName(scope, name, bindingTrace))
    }

    override fun getMembersByName(receiver: JetType, name: Name, bindingTrace: BindingTrace): Collection<VariableDescriptor> {
        return filterProperties(VARIABLES_COLLECTOR.getMembersByName(receiver, name, bindingTrace))
    }

    override fun getStaticMembersByName(receiver: JetType, name: Name, bindingTrace: BindingTrace): Collection<VariableDescriptor> {
        return filterProperties(VARIABLES_COLLECTOR.getStaticMembersByName(receiver, name, bindingTrace))
    }

    override fun getExtensionsByName(scope: JetScope, name: Name, bindingTrace: BindingTrace): Collection<VariableDescriptor> {
        return filterProperties(VARIABLES_COLLECTOR.getExtensionsByName(scope, name, bindingTrace))
    }

    override fun toString() = "PROPERTIES"
}

private fun <D : CallableDescriptor> CallableDescriptorCollector<D>.filtered(filter: (D) -> Boolean): CallableDescriptorCollector<D> {
    val delegate = this
    return object : CallableDescriptorCollector<D> {
        override fun getNonExtensionsByName(scope: JetScope, name: Name, bindingTrace: BindingTrace): Collection<D> {
            return delegate.getNonExtensionsByName(scope, name, bindingTrace).filter(filter)
        }

        override fun getMembersByName(receiver: JetType, name: Name, bindingTrace: BindingTrace): Collection<D> {
            return delegate.getMembersByName(receiver, name, bindingTrace).filter(filter)
        }

        override fun getStaticMembersByName(receiver: JetType, name: Name, bindingTrace: BindingTrace): Collection<D> {
            return delegate.getStaticMembersByName(receiver, name, bindingTrace).filter(filter)
        }

        override fun getExtensionsByName(scope: JetScope, name: Name, bindingTrace: BindingTrace): Collection<D> {
            val result = delegate.getExtensionsByName(scope, name, bindingTrace).toArrayList()

            if (name == OperatorConventions.INVOKE &&
                result.none { // TODO: very dirty hack to avoid multiple equivalent synthesized invokes to be added
                    it.getName() == OperatorConventions.INVOKE &&
                    (it as? CallableMemberDescriptor)?.getKind() != CallableMemberDescriptor.Kind.DECLARATION
                }
            ) {
                for (invoke in delegate.getNonExtensionsByName(scope, name, bindingTrace).filterIsInstance<FunctionInvokeDescriptor>()) {
                    if (invoke.getValueParameters().isEmpty()) continue

                    val synthesized = if ((invoke.getContainingDeclaration() as? FunctionClassDescriptor)?.functionKind == FunctionClassDescriptor.Kind.Function) {
                        val unsubstituted = createSynthesizedFunctionWithFirstParameterAsReceiver(invoke)
                        unsubstituted.substitute(TypeSubstitutor.create(invoke.getDispatchReceiverParameter()!!.getType()))
                    }
                    else {
                        val invokeDeclaration = invoke.getOverriddenDescriptors().single()
                        val unsubstituted = createSynthesizedFunctionWithFirstParameterAsReceiver(invokeDeclaration)
                        val fakeOverride = unsubstituted.copy(
                                invoke.getContainingDeclaration(),
                                unsubstituted.getModality(),
                                unsubstituted.getVisibility(),
                                CallableMemberDescriptor.Kind.FAKE_OVERRIDE,
                                true
                        )
                        fakeOverride.addOverriddenDescriptor(unsubstituted)
                        fakeOverride.substitute(TypeSubstitutor.create(invoke.getDispatchReceiverParameter()!!.getType()))
                    }

                    @suppress("UNCHECKED_CAST")
                    result.add(synthesized as D)
                }
            }

            return result.filter(filter)
        }

        override fun toString(): String {
            return delegate.toString()
        }
    }
}

private fun createSynthesizedFunctionWithFirstParameterAsReceiver(descriptor: FunctionDescriptor): FunctionDescriptor {
    val result = SimpleFunctionDescriptorImpl.create(
            descriptor.getContainingDeclaration(),
            descriptor.getAnnotations(),
            descriptor.getName(),
            CallableMemberDescriptor.Kind.SYNTHESIZED,
            descriptor.getSource()
    )

    val original = descriptor.getOriginal()
    result.initialize(
            original.getValueParameters().first().getType(),
            original.getDispatchReceiverParameter(),
            original.getTypeParameters(),
            original.getValueParameters().drop(1).map { p ->
                ValueParameterDescriptorImpl(
                        result, null, p.getIndex() - 1, p.getAnnotations(), Name.identifier("p${p.getIndex() + 1}"), p.getType(),
                        p.declaresDefaultValue(), p.getVarargElementType(), p.getSource()
                )
            },
            original.getReturnType(),
            original.getModality(),
            original.getVisibility()
    )

    return result
}
