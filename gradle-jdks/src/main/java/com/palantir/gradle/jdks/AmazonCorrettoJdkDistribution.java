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

import com.palantir.gradle.jdks.JdkPath.Extension;
import com.palantir.gradle.jdks.JdkRelease.Arch;
import com.palantir.gradle.jdks.JdkRelease.Os;

final class AmazonCorrettoJdkDistribution implements JdkDistribution {
    @Override
    public String defaultBaseUrl() {
        return "https://corretto.aws/downloads";
    }

    @Override
    public JdkPath path(JdkRelease jdkRelease) {
        String filename = String.format(
                "%s/java-%s-amazon-corretto-%s-%s-%s",
                jdkRelease.version(),
                extractMajorVersion(jdkRelease.version()),
                jdkRelease.version(),
                os(jdkRelease.os()),
                arch(jdkRelease.arch()));

        return JdkPath.builder()
                .filename(filename)
                .extension(extension(jdkRelease.os()))
                .build();
    }

    private static String extractMajorVersion(String version) {
        String[] split = version.split("\\.", -1);

        if (split.length == 0) {
            throw new IllegalArgumentException(String.format(
                    "Expected that there was at least one version segment for %s. Was %d", version, split.length));
        }

        return split[0];
    }

    private static String os(Os os) {
        switch (os) {
            case MACOS:
                return "macosx";
            case LINUX:
                return "linux";
            case WINDOWS:
                return "windows";
        }

        throw new UnsupportedOperationException("Case " + os + " not implemented");
    }

    private static Extension extension(Os os) {
        switch (os) {
            case MACOS:
            case LINUX:
                return Extension.TARGZ;
            case WINDOWS:
                return Extension.ZIP;
        }

        throw new UnsupportedOperationException("Case " + os + " not implemented");
    }

    private static String arch(Arch arch) {
        switch (arch) {
            case X86:
                return "i386";
            case X86_64:
                return "x64";
            case AARCH64:
                return "aarch64";
        }

        throw new UnsupportedOperationException("Case " + arch + " not implemented");
    }
}