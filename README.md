<p align="right">
<a href="https://autorelease.general.dmz.palantir.tech/palantir/gradle-jdks-idea-plugin"><img src="https://img.shields.io/badge/Perform%20an-Autorelease-success.svg" alt="Autorelease"></a>
</p>

# gradle-jdks-idea-plugin

Intellij plugin that configures the JDKs used by Intellij for a project that has the [Gradle JDK Automanagement](https://github.com/palantir/gradle-jdks/tree/develop/gradle-jdks-setup#gradle-jdk-automanagement) configured. 

If a project has the Gradle JDK Automanagement configured (`palantir.jdk.setup.enabled=true`), the plugin will:
- run the Gradle jdks setup script: ./gradle/gradle-jdks-setup.sh before any Gradle Task and before the idea project is configured.
- set the Project SDK to the Gradle Daemon JDK.
- set the GradleJVM to the Project SDK.


<img width="1407" alt="Screenshot 2024-10-10 at 10 39 39 AM" src="https://github.com/user-attachments/assets/01fabaf2-1a0b-4fb9-8398-8a8586a597eb">

