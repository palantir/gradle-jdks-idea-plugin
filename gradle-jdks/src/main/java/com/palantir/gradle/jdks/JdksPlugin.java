/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.baseline.extensions.BaselineJavaVersionsExtension;
import com.palantir.baseline.plugins.BaselineJavaVersions;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

public final class JdksPlugin implements Plugin<Project> {
    @Override
    public void apply(Project rootProject) {
        if (rootProject.getRootProject() != rootProject) {
            throw new IllegalArgumentException("com.palantir.jdks must be applied to the root project only");
        }

        JdkDistributions jdkDistributions = new JdkDistributions();

        JdksExtension jdksExtension = extension(rootProject, jdkDistributions);

        JdkManager jdkManager = new JdkManager(
                rootProject.getProject(),
                jdksExtension.getJdkStorageLocation(),
                jdkDistributions,
                new JdkDownloaders(rootProject, jdksExtension));

        rootProject.getPluginManager().apply(BaselineJavaVersions.class);

        rootProject
                .getExtensions()
                .getByType(BaselineJavaVersionsExtension.class)
                .getJdks()
                .putAll(rootProject.provider(() -> {
                    Map<JavaLanguageVersion, JavaInstallationMetadata> ret = new HashMap<>();
                    jdksExtension.getJdks().get().forEach((javaLanguageVersion, jdkExtension) -> {
                        ret.put(
                                javaLanguageVersion,
                                javaInstallationForLanguageVersion(
                                        rootProject, jdksExtension, jdkExtension, jdkManager, javaLanguageVersion));
                    });

                    return ret;
                }));
    }

    private JdksExtension extension(Project rootProject, JdkDistributions jdkDistributions) {
        JdksExtension jdksExtension = rootProject.getExtensions().create("jdks", JdksExtension.class);

        jdksExtension
                .getJdkStorageLocation()
                .set(rootProject
                        .getLayout()
                        .dir(rootProject.provider(
                                () -> new File(System.getProperty("user.home"), ".gradle/caches/gradle-jdks"))));

        Arrays.stream(JdkDistributionName.values()).forEach(jdkDistributionName -> {
            JdkDistributionExtension jdkDistributionExtension =
                    rootProject.getObjects().newInstance(JdkDistributionExtension.class);

            jdkDistributionExtension
                    .getBaseUrl()
                    .set(jdkDistributions.get(jdkDistributionName).defaultBaseUrl());

            jdksExtension.getJdkDistributions().put(jdkDistributionName, jdkDistributionExtension);
        });

        return jdksExtension;
    }

    private GradleJdksJavaInstallationMetadata javaInstallationForLanguageVersion(
            Project rootProject,
            JdksExtension jdksExtension,
            JdkExtension jdkExtension,
            JdkManager jdkManager,
            JavaLanguageVersion javaLanguageVersion) {

        String version = jdkExtension.getJdkVersion().get();
        JdkDistributionName jdkDistributionName =
                jdkExtension.getDistributionName().get();

        Path jdk = jdkManager.jdk(JdkSpec.builder()
                .distributionName(jdkDistributionName)
                .release(JdkRelease.builder().version(version).build())
                .caCerts(CaCerts.from(jdksExtension.getCaCerts().get()))
                .build());

        return GradleJdksJavaInstallationMetadata.builder()
                .installationPath(rootProject
                        .getLayout()
                        .dir(rootProject.provider(jdk::toFile))
                        .get())
                .javaRuntimeVersion(version)
                .languageVersion(javaLanguageVersion)
                .jvmVersion(version)
                .vendor(jdkDistributionName.uiName())
                .build();
    }
}