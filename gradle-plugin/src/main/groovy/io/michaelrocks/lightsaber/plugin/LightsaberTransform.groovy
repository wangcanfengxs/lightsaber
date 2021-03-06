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

package io.michaelrocks.lightsaber.plugin

import com.android.build.api.transform.*
import io.michaelrocks.lightsaber.processor.LightsaberParameters
import io.michaelrocks.lightsaber.processor.LightsaberProcessor
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

public class LightsaberTransform extends Transform {
  private final Project project
  private final Logger logger = Logging.getLogger(LightsaberTransform)

  LightsaberTransform(final Project project) {
    this.project = project
  }

  @Override
  void transform(final TransformInvocation invocation) throws IOException, TransformException, InterruptedException {
    final def parameters = new LightsaberParameters()
    final DirectoryInput directoryInput = invocation.inputs.first().directoryInputs.first()
    final File input = directoryInput.file
    final File output = invocation.outputProvider.getContentLocation(
        directoryInput.name, EnumSet.of(QualifiedContent.DefaultContentType.CLASSES),
        EnumSet.of(QualifiedContent.Scope.PROJECT), Format.DIRECTORY)

    // For now just skip tests.
    if (invocation.context.path.endsWith("Test")) {
      logger.info("Found a test project. Skipping...")
      FileMethods.copyDirectoryTo(input, output, true)
      return
    }

    parameters.classes = input
    parameters.output = output
    parameters.source = new File(invocation.context.temporaryDir, "src")
    parameters.classpath = invocation.referencedInputs.collectMany {
      it.directoryInputs.collect { it.file } + it.jarInputs.collect { it.file }
    }
    parameters.bootClasspath = project.android.bootClasspath.toList()
    parameters.debug = logger.isDebugEnabled()
    parameters.info = logger.isInfoEnabled()
    logger.info("Starting Lightsaber processor: $parameters")
    final def processor = new LightsaberProcessor(parameters)
    try {
      processor.process()
      logger.info("Lightsaber finished processing")
    } catch (final IOException exception) {
      logger.error("Lightsaber failed", exception)
      throw exception
    } catch (final Exception exception) {
      logger.error("Lightsaber failed", exception)
      throw new TransformException(exception)
    }
  }

  @Override
  String getName() {
    return "lightsaber"
  }

  @Override
  Set<QualifiedContent.ContentType> getInputTypes() {
    return EnumSet.of(QualifiedContent.DefaultContentType.CLASSES)
  }

  @Override
  Set<QualifiedContent.Scope> getScopes() {
    return EnumSet.of(QualifiedContent.Scope.PROJECT)
  }

  @Override
  Set<QualifiedContent.Scope> getReferencedScopes() {
    return EnumSet.of(
        QualifiedContent.Scope.PROJECT_LOCAL_DEPS,
        QualifiedContent.Scope.SUB_PROJECTS,
        QualifiedContent.Scope.SUB_PROJECTS_LOCAL_DEPS,
        QualifiedContent.Scope.EXTERNAL_LIBRARIES,
        QualifiedContent.Scope.TESTED_CODE,
        QualifiedContent.Scope.PROVIDED_ONLY
    )
  }

  @Override
  boolean isIncremental() {
    return false
  }
}
