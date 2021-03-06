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
package org.jetbrains.idl2k

val urls = listOf(
        "https://raw.githubusercontent.com/whatwg/html-mirror/master/source" to "org.w3c.dom",
        "https://html.spec.whatwg.org/" to "org.w3c.dom",
        "https://raw.githubusercontent.com/whatwg/dom/master/dom.html" to "org.w3c.dom",
        "https://dvcs.w3.org/hg/editing/raw-file/tip/editing.html" to "org.w3c.dom",
        "http://www.w3.org/TR/animation-timing/" to "org.w3c.dom",
        "http://www.w3.org/TR/uievents/" to "org.w3c.dom.events",
        "http://dev.w3.org/csswg/cssom/" to "org.w3c.dom.css",
        "http://www.w3.org/TR/DOM-Parsing/" to "org.w3c.dom.parsing",

        "http://web.archive.org/web/20150317051602/http://www.w3.org/TR/SVG11/single-page.html" to "org.w3c.dom.svg",
        "https://www.khronos.org/registry/webgl/specs/latest/1.0/webgl.idl" to "org.khronos.webgl",
        "https://www.khronos.org/registry/typedarray/specs/latest/typedarray.idl" to "org.khronos.webgl",

        "https://raw.githubusercontent.com/whatwg/xhr/master/Overview.src.html" to "org.w3c.xhr",
        "https://raw.githubusercontent.com/whatwg/fetch/master/Overview.src.html" to "org.w3c.fetch",
        "https://raw.githubusercontent.com/w3c/FileAPI/gh-pages/index.html" to "org.w3c.files",

        "https://raw.githubusercontent.com/whatwg/notifications/master/notifications.html" to "org.w3c.notifications",
        "https://raw.githubusercontent.com/whatwg/fullscreen/master/Overview.src.html" to "org.w3c.fullscreen",
        "http://www.w3.org/TR/vibration/" to "org.w3c.vibration",

        "http://www.w3.org/TR/hr-time/" to "org.w3c.performance",
        "http://www.w3.org/TR/2012/REC-navigation-timing-20121217/" to "org.w3c.performance",

        "http://slightlyoff.github.io/ServiceWorker/spec/service_worker/index.html" to "org.w3c.workers"
)

val relocations = mapOf(
        "Event" to "org.w3c.dom.events",
        "EventTarget" to "org.w3c.dom.events",
        "EventListener" to "org.w3c.dom.events"
)
