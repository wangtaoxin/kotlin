/*
 * Copyright 2010-2017 JetBrains s.r.o.
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

package org.jetbrains.kotlin.cli.jvm.plugins

import java.net.URL
import java.net.URLClassLoader
import java.util.*

internal class PluginURLClassLoader(urls: Array<URL>, parent: ClassLoader) : ClassLoader(Thread.currentThread().contextClassLoader) {
    private val childClassLoader: SelfThenParentURLClassLoader = SelfThenParentURLClassLoader(urls, parent)

    @Synchronized
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        return try {
            childClassLoader.findClass(name)
        }
        catch (e: ClassNotFoundException) {
            super.loadClass(name, resolve)
        }
    }

    override fun getResource(name: String): URL? = childClassLoader.getResource(name)

    override fun getResources(name: String): Enumeration<URL> = childClassLoader.getResources(name)

    private class SelfThenParentURLClassLoader(urls: Array<URL>, private val onFail: ClassLoader) : URLClassLoader(urls, null) {
        public override fun findClass(name: String): Class<*> {
            return findLoadedClass(name) ?: try {
                super.findClass(name)
            }
            catch (e: ClassNotFoundException) {
                onFail.loadClass(name)
            }
        }

        override fun findResource(name: String): URL? =
                super.findResource(name) ?: onFail.getResource(name)

        override fun findResources(name: String): Enumeration<URL> =
                CompoundEnumeration(super.findResources(name), onFail.getResources(name))
    }

    private class CompoundEnumeration<E>(private vararg val enumerations: Enumeration<E>) : Enumeration<E> {
        private var index = 0

        override fun hasMoreElements(): Boolean {
            while (index < enumerations.size) {
                if (enumerations[index].hasMoreElements()) return true
                index++
            }
            return false
        }

        override fun nextElement(): E {
            if (hasMoreElements()) return enumerations[index].nextElement()
            throw NoSuchElementException()
        }
    }
}
