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

package org.jetbrains.kotlin.util

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import java.lang.ref.WeakReference
import kotlin.platform.platformStatic

public open class MappedExtensionProvider<T, R> protected (private val epName: ExtensionPointName<T>, private val map: (List<T>) -> R) {
    private var cached = WeakReference<Pair<Application, R>>(null)

    public fun get(): R {
        val cached = cached.get() ?: return update()
        val (app, extensions) = cached
        if (app == ApplicationManager.getApplication()) {
            return extensions
        }
        else {
            return update()
        }
    }

    private fun update(): R {
        val newVal = ApplicationManager.getApplication().let { app ->
            Pair(app, map(app.getExtensions(epName).toList()))
        }
        cached = WeakReference(newVal)
        return newVal.second
    }

    companion object {
        platformStatic public fun <T, R> create(epName: ExtensionPointName<T>, map: (List<T>) -> R): MappedExtensionProvider<T, R>
                = MappedExtensionProvider(epName, map)
    }
}

public class ExtensionProvider<T>(epName: ExtensionPointName<T>) : MappedExtensionProvider<T, List<T>>(epName, { it }) {
    companion object {
        platformStatic public fun <T> create(epName: ExtensionPointName<T>): ExtensionProvider<T> = ExtensionProvider(epName)
    }
}