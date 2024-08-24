/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.jdks;

import com.palantir.gradle.jdks.setup.CaResources;
import java.io.File;
import java.nio.file.Path;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.OutputDirectory;

public abstract class GenerateGradleJdksConfigsTask extends GradleJdksConfigs {

    private static final Logger log = Logging.getLogger(GenerateGradleJdksConfigsTask.class);

    @OutputDirectory
    public abstract DirectoryProperty getOutputGradleDirectory();

    @Override
    protected final Directory gradleDirectory() {
        return getOutputGradleDirectory().get();
    }

    @Override
    protected final void applyGradleJdkFileAction(
            Path downloadUrlPath, Path localUrlPath, JdkDistributionConfig jdkDistribution) {
        GradleJdksConfigsUtils.createDirectories(downloadUrlPath.getParent());
        GradleJdksConfigsUtils.writeConfigurationFile(
                downloadUrlPath, jdkDistribution.getDownloadUrl().get());
        GradleJdksConfigsUtils.writeConfigurationFile(
                localUrlPath, jdkDistribution.getLocalPath().get());
    }

    @Override
    protected final void applyGradleJdkDaemonVersionAction(Path gradleJdkDaemonVersion) {
        GradleJdksConfigsUtils.writeConfigurationFile(
                gradleJdkDaemonVersion, getDaemonJavaVersion().get().toString());
    }

    @Override
    protected final void applyGradleJdkJarAction(File gradleJdkJarFile, String resourceName) {
        GradleJdksConfigsUtils.writeResourceAsStreamToFile(resourceName, gradleJdkJarFile);
    }

    @Override
    protected final void applyGradleJdkScriptAction(File gradleJdkScriptFile, String resourceName) {
        GradleJdksConfigsUtils.writeResourceAsStreamToFile(resourceName, gradleJdkScriptFile);
        GradleJdksConfigsUtils.setExecuteFilePermissions(gradleJdkScriptFile.toPath());
    }

    @Override
    protected final void applyCertAction(File certFile, String alias, String content) {
        GradleJdksConfigsUtils.createDirectories(certFile.getParentFile().toPath());
        GradleJdksConfigsUtils.writeConfigurationFile(certFile.toPath(), CaResources.getSerialNumber(content));
    }
}