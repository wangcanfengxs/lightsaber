/*
 * Copyright 2016 Michael Rozumyanskiy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.michaelrocks.lightsaber.processor

import io.michaelrocks.grip.Grip
import io.michaelrocks.grip.GripFactory
import io.michaelrocks.lightsaber.processor.analysis.Analyzer
import io.michaelrocks.lightsaber.processor.commons.StandaloneClassWriter
import io.michaelrocks.lightsaber.processor.compiler.JavaToolsCompiler
import io.michaelrocks.lightsaber.processor.generation.Generator
import io.michaelrocks.lightsaber.processor.injection.Patcher
import io.michaelrocks.lightsaber.processor.io.DirectoryFileSink
import io.michaelrocks.lightsaber.processor.io.FileSource
import io.michaelrocks.lightsaber.processor.io.IoFactory
import io.michaelrocks.lightsaber.processor.logging.getLogger
import io.michaelrocks.lightsaber.processor.model.Component
import io.michaelrocks.lightsaber.processor.model.InjectionContext
import io.michaelrocks.lightsaber.processor.model.InjectionPoint
import io.michaelrocks.lightsaber.processor.model.InjectionTarget
import io.michaelrocks.lightsaber.processor.model.ProvisionPoint
import io.michaelrocks.lightsaber.processor.validation.Validator
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import java.io.File

class ClassProcessor(
    private val inputPath: File,
    private val sourcePath: File,
    private val outputPath: File,
    classpath: List<File>,
    bootClasspath: List<File>
) {
  private val logger = getLogger()

  private val grip: Grip = GripFactory.create(setOf(inputPath) + classpath + bootClasspath)
  private val errorReporter = ErrorReporter()

  private val fileSource = IoFactory.createFileSource(inputPath)
  private val fileSink = IoFactory.createFileSink(inputPath, outputPath)
  private val sourceSink = DirectoryFileSink(sourcePath)

  private val compiler = JavaToolsCompiler(classpath, bootClasspath, errorReporter)

  fun processClasses() {
    val context = performAnalysisAndValidation()
    context.dump()
    copyAndPatchClasses(context)
    performGeneration(context)
    performCompilation()
  }

  private fun performAnalysisAndValidation(): InjectionContext {
    val analyzer = Analyzer(grip, errorReporter)
    val context = analyzer.analyze(listOf(inputPath))
    Validator(grip.classRegistry, errorReporter).validate(context)
    checkErrors()
    return context
  }

  private fun copyAndPatchClasses(context: InjectionContext) {
    fileSource.listFiles { path, type ->
      when (type) {
        FileSource.EntryType.CLASS -> {
          val classReader = ClassReader(fileSource.readFile(path))
          val classWriter = StandaloneClassWriter(
              classReader, ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES, grip.classRegistry)
          val classVisitor = Patcher(classWriter, context)
          classReader.accept(classVisitor, ClassReader.SKIP_FRAMES)
          fileSink.createFile(path, classWriter.toByteArray())
        }
        FileSource.EntryType.FILE -> fileSink.createFile(path, fileSource.readFile(path))
        FileSource.EntryType.DIRECTORY -> fileSink.createDirectory(path)
      }
    }

    checkErrors()
  }

  private fun performGeneration(context: InjectionContext) {
    val generator = Generator(grip.classRegistry, errorReporter, fileSink, sourceSink)
    generator.generate(context)
    checkErrors()
  }

  private fun performCompilation() {
    compiler.compile(sourcePath, outputPath)
    checkErrors()
  }

  private fun checkErrors() {
    if (errorReporter.hasErrors()) {
      throw ProcessingException(composeErrorMessage())
    }
  }

  private fun composeErrorMessage(): String {
    return errorReporter.getErrors().joinToString("\n") { it.message.orEmpty() }
  }

  private fun InjectionContext.dump() {
    packageComponent.dump()
    components.forEach { it.dump() }
    injectableTargets.forEach { it.dump("Injectable") }
    providableTargets.forEach { it.dump("Providable") }
  }

  private fun Component.dump() {
    logger.debug("Component: {}", type)
    for (module in modules) {
      logger.debug("  Module: {}", module.type)
      for (provider in module.providers) {
        if (provider.provisionPoint is ProvisionPoint.AbstractMethod) {
          logger.debug("   Provides: {}", provider.provisionPoint.method)
        } else if (provider.provisionPoint is ProvisionPoint.Field) {
          logger.debug("   Provides: {}", provider.provisionPoint.field)
        } else {
          logger.debug("   Provides: {}", provider.provisionPoint)
        }
      }
    }

    for (subcomponent in subcomponents) {
      logger.debug("  Subcomponent: {}", subcomponent)
    }
  }

  private fun InjectionTarget.dump(name: String) {
    logger.debug("{}: {}", name, type)
    for (injectionPoint in injectionPoints) {
      when (injectionPoint) {
        is InjectionPoint.Field -> logger.debug("  Field: {}", injectionPoint.field)
        is InjectionPoint.Method -> logger.debug("  Method: {}", injectionPoint.method)
      }
    }
  }
}
