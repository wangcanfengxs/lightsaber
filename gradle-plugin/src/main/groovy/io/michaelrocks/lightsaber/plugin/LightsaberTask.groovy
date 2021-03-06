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

import groovy.io.FileVisitResult
import groovy.transform.CompileStatic
import io.michaelrocks.lightsaber.processor.LightsaberParameters
import io.michaelrocks.lightsaber.processor.LightsaberProcessor
import io.michaelrocks.lightsaber.processor.watermark.WatermarkChecker
import org.gradle.api.DefaultTask
import org.gradle.api.GradleScriptException
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

@CompileStatic
public class LightsaberTask extends DefaultTask {
  @InputDirectory
  File backupDir
  @OutputDirectory
  File classesDir
  @OutputDirectory
  File sourceDir
  @InputFiles
  List<File> classpath
  @InputFiles
  List<File> bootClasspath

  LightsaberTask() {
    logging.captureStandardOutput LogLevel.INFO
  }

  @TaskAction
  void process() {
    final def parameters = new LightsaberParameters()
    parameters.classes = backupDir
    parameters.output = classesDir
    parameters.classpath = classpath
    parameters.bootClasspath = bootClasspath
    parameters.source = sourceDir
    parameters.debug = logger.isDebugEnabled()
    parameters.info = logger.isInfoEnabled()
    logger.info("Starting Lightsaber processor: $parameters")
    final def processor = new LightsaberProcessor(parameters)
    try {
      processor.process()
    } catch (final Exception exception) {
      throw new GradleScriptException('Lightsaber processor failed to process files', exception)
    }
  }

  void clean() {
    logger.info("Removing patched files...")
    logger.info("  from [$classesDir]")

    if (!classesDir.exists()) {
      return
    }

    classesDir.traverse(
        postDir: { final File dir -> FileMethods.deleteDirectoryIfEmpty(dir) } as Object
    ) { final file ->
      if (file.isDirectory()) {
        return FileVisitResult.CONTINUE
      }

      logger.debug("Checking $file...")
      if (WatermarkChecker.isLightsaberClass(file)) {
        logger.debug("File was patched - removing")
        file.delete()
      } else {
        logger.debug("File wasn't patched - skipping")
      }
      return FileVisitResult.CONTINUE
    }
  }
}
