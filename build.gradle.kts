import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    kotlin("jvm") version "2.4.20"
    id("org.jetbrains.intellij.platform") version "2.20.0"
    id("org.jetbrains.intellij.platform.grammarkit") version "2.20.0"
}

group = "dev.nsis.jetbrains"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        val localIde = providers.gradleProperty("localIdePath").orNull
        if (localIde != null) local(localIde) else intellijIdea("2026.2.4")
        testFramework(TestFrameworkType.Platform)
        pluginVerifier()
    }
    testImplementation("junit:junit:4.13.2")
}

kotlin { jvmToolchain(25) }

tasks.generateLexer { sourceFile = file("src/main/grammar/Nsis.flex") }
tasks.generateParser { sourceFile = file("src/main/grammar/Nsis.bnf") }
sourceSets.main {
    java.srcDir(tasks.generateLexer)
    java.srcDir(tasks.generateParser)
}
tasks.compileKotlin { dependsOn(tasks.generateLexer, tasks.generateParser) }

intellijPlatform {
    pluginConfiguration {
        name = "NSIS Language Support"
        ideaVersion {
            sinceBuild = "262"
            untilBuild = "263.*"
        }
    }
    pluginVerification {
        if (providers.gradleProperty("verifierOffline").orNull.toBoolean()) freeArgs = listOf("-offline")
        ides {
            val idea = providers.gradleProperty("verifyIdeaPath").orNull
            val rider = providers.gradleProperty("verifyRiderPath").orNull
            if (idea != null) local(idea) else create(IntelliJPlatformType.IntellijIdea, "2026.3")
            if (rider != null) local(rider) else create(IntelliJPlatformType.Rider, "2026.2.4.1")
        }
    }
}

tasks.test {
    maxHeapSize = "2g"
    systemProperty("java.awt.headless", "true")
}

tasks.buildPlugin {
    from(layout.projectDirectory.file("LICENSE"))
}
