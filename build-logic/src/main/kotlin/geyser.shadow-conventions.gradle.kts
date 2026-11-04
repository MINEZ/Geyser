import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("geyser.base-conventions")
    id("com.gradleup.shadow")
}

shadow {
    addShadowVariantIntoJavaComponent = false
}

tasks {
    named<Jar>("jar") {
        from(project.rootProject.file("LICENSE"))
    }

    val shadowJar = named<ShadowJar>("shadowJar") {
        archiveBaseName.set(project.name)
        // MINEZ：文件名带上版本号，例如 Geyser-Velocity-2.12.1-b1280.1.jar。
        archiveVersion.set(minezVersion(project))
        archiveClassifier.set("")
        
        val currentProjectName = project.name // Capture project name at config time

        dependencies {
            providedDependencies[currentProjectName]?.forEach { string ->
                println("Excluding $string from $currentProjectName") 
                exclude(dependency(string))
            }

            exclude(dependency("org.checkerframework:checker-qual:.*"))
            exclude(dependency("org.jetbrains:annotations:.*"))
        }
    }
    
    named("build") {
        dependsOn(shadowJar)
    }
}
