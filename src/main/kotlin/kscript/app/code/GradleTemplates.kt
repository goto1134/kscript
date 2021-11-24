package kscript.app.code

import kscript.app.creator.JarArtifact
import kscript.app.model.CompilerOpt
import kscript.app.model.Dependency
import kscript.app.model.Repository
import kscript.app.model.Script
import kscript.app.util.ScriptUtils.dropExtension

object GradleTemplates {
    fun createGradleIdeaScript(script: Script): String {
        val kotlinOptions = kotlinOptions(script.compilerOpts)

        val kotlinVersion = KotlinVersion.CURRENT
        val extendedDependencies = setOf(
            Dependency("org.jetbrains.kotlin:kotlin-stdlib"),
            Dependency("org.jetbrains.kotlin:kotlin-script-runtime:$kotlinVersion")
        ) + script.dependencies

       val capsuleApp = script.scriptName.dropExtension()

        return """
        plugins {
            id("org.jetbrains.kotlin.jvm") version "$kotlinVersion"
            id("com.github.ngyewch.capsule") version "0.1.4"
            application
        }

        repositories {
            mavenLocal()
            mavenCentral()
            ${createGradleRepositoriesSection(script.repositories).prependIndent()}
        }
        
        capsule {
            archiveBaseName.set("$capsuleApp")
            archiveClassifier.set("all")
            embedConfiguration.set(configurations.getByName("runtimeClasspath")) 
            manifestAttributes.set(mapOf("Test-Attribute" to "Test-Value"))
            capsuleManifest {
                applicationId.set("$capsuleApp")
            }
        }

        dependencies {
            ${createGradleDependenciesSection(extendedDependencies).prependIndent()}
        }

        sourceSets.getByName("main").java.srcDirs("src")
        sourceSets.getByName("test").java.srcDirs("src")

        $kotlinOptions
        """.trimIndent()
    }

    //Capsule: https://github.com/ngyewch/gradle-capsule-plugin
    fun createGradlePackageScript(script: Script, jarArtifact: JarArtifact): String {
        val kotlinOptions = kotlinOptions(script.compilerOpts)

        val kotlinVersion = KotlinVersion.CURRENT
        val extendedDependencies = setOf(
            Dependency("org.jetbrains.kotlin:kotlin-stdlib"),
            Dependency("org.jetbrains.kotlin:kotlin-script-runtime:$kotlinVersion")
        ) + script.dependencies

        val capsuleApp = script.scriptName.dropExtension()

        return """
        plugins {
            id("org.jetbrains.kotlin.jvm") version "$kotlinVersion"
            id("com.github.ngyewch.capsule") version "0.1.4"
            application
        }

        repositories {
            mavenLocal()
            mavenCentral()
            ${createGradleRepositoriesSection(script.repositories).prependIndent()}
        }
        
        application {
            mainClass.set("$capsuleApp")
        }

        dependencies {
            implementation(files("${jarArtifact.path}"))
            ${createGradleDependenciesSection(extendedDependencies).prependIndent()}
        }

        $kotlinOptions
        """.trimIndent()
    }

    private fun createGradleRepositoryCredentials(repository: Repository): String {
        if (repository.user.isNotBlank() && repository.password.isNotBlank()) {
            return """
                credentials {
                    username = "${repository.user}"
                    password = "${repository.password}"
                }
            """.trimIndent()
        }

        return ""
    }

    private fun createGradleDependenciesSection(dependencies: Set<Dependency>) = dependencies.joinToString("\n") {
        "implementation(\"${it.value}\")"
    }

    private fun createGradleRepositoriesSection(repositories: Set<Repository>) = repositories.joinToString("\n") {
        """ 
        maven {
            url "${it.url}"
            ${createGradleRepositoryCredentials(it).prependIndent()}
        }
        """.trimIndent()
    }

    private fun kotlinOptions(compilerOpts: Set<CompilerOpt>): String {
        val opts = compilerOpts.map { it.value }

        var jvmTargetOption: String? = null
        for (i in opts.indices) {
            if (i > 0 && opts[i - 1] == "-jvm-target") {
                jvmTargetOption = opts[i]
            }
        }

        val kotlinOpts = if (jvmTargetOption != null) {
            """
            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
                kotlinOptions { 
                    jvmTarget = "$jvmTargetOption"
                }
            }
            """.trimIndent()
        } else {
            ""
        }

        return kotlinOpts
    }
}
