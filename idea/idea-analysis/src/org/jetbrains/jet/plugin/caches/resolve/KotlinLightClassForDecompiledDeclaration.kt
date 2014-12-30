/*
 * Copyright 2010-2014 JetBrains s.r.o.
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

package org.jetbrains.jet.plugin.caches.resolve

import org.jetbrains.jet.asJava.KotlinWrappingLightClass
import com.intellij.psi.PsiClass
import com.intellij.psi.impl.compiled.ClsClassImpl
import org.jetbrains.jet.plugin.JetLanguage
import org.jetbrains.jet.lang.psi.JetDeclaration
import org.jetbrains.jet.lang.psi.JetClassOrObject
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement

class KotlinLightClassForDecompiledDeclaration(
        private val clsClass: ClsClassImpl,
        override val origin: JetClassOrObject?
) : KotlinWrappingLightClass(clsClass.getManager()) {
    override fun copy() = this

    override fun getOwnInnerClasses(): List<PsiClass> {
        //TODO (light classes for decompiled files): correct origin
        return clsClass.getOwnInnerClasses().map { KotlinLightClassForDecompiledDeclaration(it as ClsClassImpl, null) }
    }

    //TODO (light classes for decompiled files): correct navigation element
    override fun getNavigationElement() = origin?.getNavigationElement() ?: super.getNavigationElement()

    override fun getDelegate() = clsClass
}