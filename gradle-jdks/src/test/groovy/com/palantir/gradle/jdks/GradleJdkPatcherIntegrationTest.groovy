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

package com.palantir.gradle.jdks

import com.google.common.base.Splitter
import com.google.common.collect.Iterables
import com.palantir.gradle.jdks.setup.CommandRunner
import nebula.test.IntegrationSpec
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class GradleJdkPatcherIntegrationTest extends IntegrationSpec {

    private static AmazonCorrettoJdkDistribution CORRETTO_JDK_DISTRIBUTION = new AmazonCorrettoJdkDistribution();
    private static String GRADLE_7VERSION = "7.6.2";
    private static String JDK_17_VERSION = "17.0.9.8.1";
    private static String AMAZON_ROOT_CA_1_SERIAL = "143266978916655856878034712317230054538369994"
    private static String PALANTIR_3RD_GEN_SERIAL = "18126334688741185161";

    @TempDir
    private Path workingDir

    def setup() {

        // language=groovy
        buildFile << """
            buildscript {
                repositories {
                    mavenCentral()
                }
                // we need to inject the classpath of the plugin under test manually. The tests call the `./gradlew` 
                // command directly in the tests (so not using the nebula-test workflow).
                dependencies {
                    classpath files(FILES)
                }
            }
            apply plugin: 'com.palantir.jdks'
        """.replace("FILES", getPluginClasspathInjector().join(",")).stripIndent(true)

        // language=gradle
        addSubproject 'subproject', '''
            apply plugin: 'java-library'
        '''.stripIndent(true)
    }

    private Iterable<File> getPluginClasspathInjector() {
        File propertiesFile = new File("build/pluginUnderTestMetadata/plugin-under-test-metadata.properties")
        Properties properties = new Properties()
        propertiesFile.withInputStream { inputStream ->
            properties.load(inputStream)
        }
        String classpath = properties.getProperty('implementation-classpath')

        return classpath.split(File.pathSeparator).collect { "'" + it + "'" }
    }


    def '#gradleVersionNumber: patches gradleWrapper to set up JDK'() {
        file('gradle.properties') << 'gradle.jdk.setup.enabled=true'
        gradleVersion = gradleVersionNumber
        populateGradleFiles(JDK_17_VERSION)

        when:
        def output = runTasksSuccessfully('wrapper')

        then:
        output.wasExecuted(':wrapperJdkPatcher')
        output.standardOutput.contains("Gradle JDK setup is enabled, patching the gradle wrapper files")
        file("gradlew").text.contains("gradle/gradle-jdks-setup.sh")

        when:
        String wrapperResult1 = upgradeGradleWrapper()
        String wrapperResult2 = upgradeGradleWrapper()

        then:
        file("gradlew").text.contains("gradle/gradle-jdks-setup.sh")
        Path gradleJdksPath = workingDir.resolve("gradle-jdks")
        String expectedLocalPath = gradleJdksPath.resolve(getLocalFilename(JDK_17_VERSION))
        wrapperResult1.contains(String.format("Successfully installed JDK distribution, setting JAVA_HOME to %s", expectedLocalPath))
        String expectedJdkLog = "JVM:          17.0.9 (Amazon.com Inc. 17.0.9+8-LTS)";
        wrapperResult1.contains(expectedJdkLog)
        wrapperResult1.contains("Gradle 7.6.2")
        file('gradle/wrapper/gradle-wrapper.properties').text.contains("gradle-8.4-bin.zip")
        wrapperResult2.contains(String.format("already exists, setting JAVA_HOME to %s", expectedLocalPath))
        wrapperResult2.contains(expectedJdkLog)
        wrapperResult2.contains("Gradle 8.4")


        where:
        gradleVersionNumber << [ GRADLE_7VERSION ]
    }

    def '#gradleVersionNumber: gradlew file is correctly generated'() {
        gradleVersion = gradleVersionNumber

        when:
        def output = runTasksSuccessfully('wrapper')

        then:
        output.wasExecuted(':wrapperJdkPatcher')
        List<String> initialRows = Files.readAllLines(projectDir.toPath().resolve('gradlew'))

        when:
        file('gradle.properties') << 'gradle.jdk.setup.enabled=true'
        def outputWithJdkEnabled = runTasksSuccessfully('wrapper')

        then:
        outputWithJdkEnabled.wasExecuted(':wrapperJdkPatcher')
        List<String> rowsAfterPatching = Files.readAllLines(projectDir.toPath().resolve('gradlew'))

        List<String> gradlewPatchRows = Files.readAllLines(Path.of('src/main/resources/gradlew-patch.sh'))
        rowsAfterPatching.removeAll(initialRows)
        rowsAfterPatching.removeAll(gradlewPatchRows)
        rowsAfterPatching.size() == 0

        where:
        gradleVersionNumber << [ GRADLE_7VERSION ]
    }

    def 'no gradleWrapper patch if gradle.jdk.setup.enabled == false'() {
        populateGradleFiles(JDK_17_VERSION)

        when:
        def output = runTasksSuccessfully('wrapper')

        then:
        output.wasSkipped(':wrapperJdkPatcher')
        !output.standardOutput.contains("Gradle JDK setup is enabled, patching the gradle wrapper files")
        !file("gradlew").text.contains("gradle-jdks-setup.sh")
    }

    def 'fails if the JDK setup files are missing' () {
        file('gradle.properties') << 'gradle.jdk.setup.enabled=true'
        gradleVersion = gradleVersionNumber
        directory('gradle')
        Files.copy(
                Path.of("../gradle-jdks-setup/src/main/resources/gradle-jdks-setup.sh"),
                projectDir.toPath().resolve("gradle/gradle-jdks-setup.sh"));

        when:
        def output = runTasksSuccessfully('wrapper')

        then:
        output.wasExecuted(':wrapperJdkPatcher')
        output.standardOutput.contains("Gradle JDK setup is enabled, patching the gradle wrapper files")
        file("gradlew").text.contains("gradle/gradle-jdks-setup.sh")

        when:
        String wrapperResult1 = upgradeGradleWrapper()

        then:
        file("gradlew").text.contains("gradle/gradle-jdks-setup.sh")
        wrapperResult1.contains("not found, aborting Gradle JDK setup")

        where:
        gradleVersionNumber << [ GRADLE_7VERSION ]
    }

    void populateGradleFiles(String jdkVersion) {
        String jdkMajorVersion = Iterables.get(Splitter.on('.').split(jdkVersion), 0);

        file('gradle/gradle-jdk-major-version') << jdkMajorVersion + "\n"
        directory('gradle/jdks')

        Files.copy(
                Path.of(String.format(
                        "../gradle-jdks-setup/build/libs/gradle-jdks-setup-%s.jar",
                        System.getenv().get("PROJECT_VERSION"))),
                projectDir.toPath().resolve("gradle/jdks/gradle-jdks-setup.jar"));

        Files.copy(
                Path.of("../gradle-jdks-setup/src/main/resources/gradle-jdks-setup.sh"),
                projectDir.toPath().resolve("gradle/gradle-jdks-setup.sh"));

        Os os = CurrentOs.get();
        Arch arch = CurrentArch.get();

        directory(String.format('gradle/jdks/%s/%s/%s', jdkMajorVersion, os, arch))

        JdkPath jdkPath = CORRETTO_JDK_DISTRIBUTION.path(
                JdkRelease.builder().version(jdkVersion).os(os).arch(arch).build());
        String correttoDistributionUrl = Optional.ofNullable(System.getenv("CORRETTO_DISTRIBUTION_URL"))
                .orElseGet(CORRETTO_JDK_DISTRIBUTION::defaultBaseUrl);
        String downloadUrl = String.format(
                String.format("%s/%s.%s\n", correttoDistributionUrl, jdkPath.filename(), jdkPath.extension()))
        String localFilename = getLocalFilename(jdkVersion);
        file(String.format('gradle/jdks/%s/%s/%s/download-url', jdkMajorVersion, CurrentOs.get(), CurrentArch.get())) << downloadUrl
        file(String.format('gradle/jdks/%s/%s/%s/local-path', jdkMajorVersion, CurrentOs.get(), CurrentArch.get())) <<  localFilename

        directory('gradle/certs')
        file('gradle/certs/AmazonRootCA1Test.serial-number') << AMAZON_ROOT_CA_1_SERIAL + "\n"
        directory('gradle/certs')
        file('gradle/certs/Palantir3rdGenRootCa.serial-number') << PALANTIR_3RD_GEN_SERIAL + "\n"
    }

    String getLocalFilename(String jdkVersion) {
       return  String.format("amazon-corretto-%s-jdkPluginIntegrationTest\n", jdkVersion);
    }


    String upgradeGradleWrapper() {
        ProcessBuilder processBuilder = new ProcessBuilder()
                .command(List.of("./gradlew", "wrapper", "--gradle-version", "8.4", "-V", "--stacktrace"))
                .directory(projectDir).redirectErrorStream(true)
        processBuilder.environment().put("GRADLE_USER_HOME", workingDir.toAbsolutePath().toString())
        Process process = processBuilder.start()
        process.waitFor()
        return CommandRunner.readAllInput(process.getInputStream())
    }
}