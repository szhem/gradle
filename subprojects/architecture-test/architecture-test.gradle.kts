import org.gradle.gradlebuild.ProjectGroups.publicProjects
import org.gradle.gradlebuild.unittestandcompile.ModuleType
import org.gradle.gradlebuild.PublicApi

plugins {
    `java-library`
}

gradlebuildJava {
    moduleType = ModuleType.INTERNAL
}

dependencies {
    testImplementation(project(":baseServices"))
    testImplementation(project(":modelCore"))
    
    testImplementation(testLibrary("archunit_junit4"))
    testImplementation(library("jsr305"))
    testImplementation(library("guava"))

    publicProjects.forEach {
        testRuntimeOnly(it)
    }
}

tasks.withType<Test> {
    systemProperty("org.gradle.public.api.includes", PublicApi.includes.joinToString(":"))
    systemProperty("org.gradle.public.api.excludes", PublicApi.excludes.joinToString(":"))
}
