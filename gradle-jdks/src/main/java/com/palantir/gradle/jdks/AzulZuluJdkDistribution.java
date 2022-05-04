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
import org.immutables.value.Value;

final class AzulZuluJdkDistribution implements JdkDistribution {
    @Override
    public String defaultBaseUrl() {
        return "https://cdn.azul.com/zulu/bin";
    }

    @Override
    public JdkPath path(JdkRelease jdkRelease) {
        ZuluVersionSplit zuluVersionSplit = splitCombinedVersion(jdkRelease.version());

        String filename = String.format(
                "zulu%s-ca-jdk%s-%s_%s",
                zuluVersionSplit.zuluVersion(),
                zuluVersionSplit.javaVersion(),
                os(jdkRelease.os()),
                arch(jdkRelease.arch()));

        return JdkPath.builder().filename(filename).extension(Extension.ZIP).build();
    }

    private static String os(Os os) {
        switch (os) {
            case MACOS:
                return "macosx";
            case LINUX:
                return "linux";
            case WINDOWS:
                return "win";
        }

        throw new UnsupportedOperationException("Case " + os + " not implemented");
    }

    private static String arch(Arch arch) {
        switch (arch) {
            case X86:
                return "i686";
            case X86_64:
                return "x64";
            case AARCH64:
                return "aarch64";
        }

        throw new UnsupportedOperationException("Case " + arch + " not implemented");
    }

    static ZuluVersionSplit splitCombinedVersion(String combinedVersion) {
        String[] split = combinedVersion.split("-", -1);

        if (split.length != 2) {
            throw new IllegalArgumentException(
                    String.format("Expected %s to split into two parts, split into %d", combinedVersion, split.length));
        }

        return ZuluVersionSplit.builder()
                .zuluVersion(split[0])
                .javaVersion(split[1])
                .build();
    }

    @Value.Immutable
    interface ZuluVersionSplit {
        String zuluVersion();

        String javaVersion();

        class Builder extends ImmutableZuluVersionSplit.Builder {}

        static Builder builder() {
            return new Builder();
        }
    }
}