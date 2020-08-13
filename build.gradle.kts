import org.gradle.api.artifacts.maven.MavenDeployment
import org.gradle.api.tasks.bundling.Jar
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.io.File
import java.nio.file.StandardCopyOption
import java.util.regex.Pattern
import java.io.FileWriter
import groovy.text.GStringTemplateEngine

buildscript {
    fun createBuildVersion(projectVersion: String): String {
        var derivedVersion = projectVersion
        val versionWithSnapshot = projectVersion.replace("-SNAPSHOT", "")
        val buildNumber = System.getenv("TRAVIS_BUILD_NUMBER") ?: "0"
        if (project.extra["release"] == "true") {
            derivedVersion = "${versionWithSnapshot}.${buildNumber}"
        } else {
            derivedVersion = "${versionWithSnapshot}.${buildNumber}-SNAPSHOT"
        }
        println("effective project version: ${derivedVersion}")
        return derivedVersion
    }
    project.version = createBuildVersion("${project.version}")
}

plugins {
    idea
    java
    jacoco
    id("org.sonarqube") version "2.8"
    id("maven-publish")
    id("scala")
    maven
    signing
}

repositories {
    mavenCentral()
    jcenter()
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

configurations {
    implementation {
        resolutionStrategy.failOnVersionConflict()
    }
}

val sparkVersion = "3.0.0"
val scalaVersion = "2.12"

dependencies {
    implementation("org.scala-lang:scala-compiler:2.12.11")
    implementation("org.scala-lang:scala-library:2.12.11")

    listOf("spark-core", "spark-sql", "spark-kubernetes", "spark-streaming", "spark-streaming-kafka-0-10").forEach { name ->
        implementation("org.apache.spark:${name}_${scalaVersion}:$sparkVersion")
    }

    implementation("com.datastax.spark:spark-cassandra-connector_${scalaVersion}:3.0.0-beta") {
        exclude(module = "scala-library")
    }
    implementation("com.ibm.db2:jcc:11.5.4.0")
    implementation("org.postgresql:postgresql:42.2.14")
    implementation("com.amazonaws:aws-java-sdk-s3:1.11.839")

    implementation("org.yaml:snakeyaml:1.26")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.11.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.11.2")
    implementation("com.fasterxml.jackson.module:jackson-module-scala_${scalaVersion}:2.11.2")


    testImplementation("org.mockito:mockito-core:3.3.3")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.6.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.6.1")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.6.1")

    testImplementation("org.junit.platform:junit-platform-commons:1.6.1")
    testImplementation("org.junit.platform:junit-platform-runner:1.6.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.6.1")
    testRuntimeOnly("org.junit.platform:junit-platform-engine:1.6.1")
}

tasks.test {
    useJUnitPlatform()
}

sonarqube {
    properties {
        property("sonar.projectName", "mounted-secrets-utils")
        property("sonar.host.url", "http://localhost:9000")
        property("sonar.projectKey", "mounted-secrets-utils")
        property("sonar.projectVersion", "${project.version}")
        property("sonar.junit.reportPaths", "${projectDir}/build/test-results/test")
        property("sonar.coverage.jacoco.xmlReportPaths", "${projectDir}/build/reports/jacoco/test/jacocoTestReport.xml")
        property("sonar.coverage.exclusions", "**/R.java")
    }
}

apply(from = "$rootDir/gradle/includes/codestyle.gradle.kts")
tasks.build {
    dependsOn(arrayOf("checkstyleMain", "checkstyleTest"))
}

tasks.jacocoTestReport {
    reports {
        xml.isEnabled = true
        csv.isEnabled = false
        html.destination = file("${buildDir}/jacocoHtml")
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            enabled = true
            limit {
                minimum = "0.2".toBigDecimal()
            }
        }

        rule {
            enabled = false
            element = "BUNDLE"
            includes = listOf("com.github.starter.*")
            excludes = listOf("**/Application*")
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.1".toBigDecimal()
            }
        }
    }
}

tasks.test {
    extensions.configure(JacocoTaskExtension::class) {
        destinationFile = file("$buildDir/jacoco/jacocoTest.exec")
        classDumpDir = file("$buildDir/jacoco/classpathdumps")
    }
}

tasks.test {
    finalizedBy("jacocoTestReport")
}

tasks.check {
    dependsOn(arrayOf("jacocoTestReport", "jacocoTestCoverageVerification"))
}

val sourcesJar by tasks.registering(Jar::class) {
    dependsOn(JavaPlugin.CLASSES_TASK_NAME)
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

val javadocJar by tasks.registering(Jar::class) {
    dependsOn(JavaPlugin.CLASSES_TASK_NAME)
    from(tasks.javadoc)
    archiveClassifier.set("javadoc")
}


artifacts {
    add("archives", sourcesJar)
    add("archives", javadocJar)
}

publishing {
    repositories {
        maven {
            var uploadUrl: String = if (project.extra["release"] == "true") {
                "${project.extra["upload.release.url"]}"
            } else {
                "${project.extra["upload.snapshot.url"]}"
            }
            url = uri(uploadUrl)
            credentials {
                username = "${project.extra["maven.username"]}"
                password = "${project.extra["maven.password"]}"
            }
        }
    }
    publications {
        register("mavenJava", MavenPublication::class) {
            from(components["java"])
            artifact(sourcesJar.get())
            artifact(javadocJar.get())
        }
    }
}

val scmUrl = project.extra["scm.url"]
project.publishing.publications.withType(MavenPublication::class.java).forEach { publication ->
    with(publication.pom) {
        withXml {
            val root = asNode()
            root.appendNode("name", project.name)
            root.appendNode("description", "Library to read and integrate secrets into your app")
            root.appendNode("url", scmUrl)
        }
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("${project.extra["author.handle"]}")
                name.set("${project.extra["author.name"]}")
                email.set("${project.extra["author.email"]}")
            }
        }
        scm {
            connection.set("scm:git:$scmUrl")
            developerConnection.set("scm:git:$scmUrl")
            url.set("${scmUrl}")
        }
    }
}

gradle.taskGraph.whenReady {
    if (allTasks.any { it is Sign }) {
        allprojects {
            extra["signing.keyId"] = "${project.extra["signing.keyId"]}"
            extra["signing.secretKeyRingFile"] = "${project.extra["signing.secretKeyRingFile"]}"
            extra["signing.password"] = "${project.extra["signing.password"]}"
        }
    }
}

signing {
    sign(configurations.archives.get())
    sign(publishing.publications["mavenJava"])
}

tasks.withType<Sign>().configureEach {
    onlyIf { project.extra["release"] == "true" }
}
tasks.create("export") {
    doLast {
        file("examples/libs").mkdirs()
        var items = mapOf<String, MutableList<String>>()
        val pathRegex = Pattern.compile("^.*/caches/modules-2/files-2.1/(?<group>[a-zA-Z0-9\\._\\-]+)/(?<artifact>[a-zA-Z0-9\\._\\-]+)/(?<version>[a-zA-Z0-9\\._\\-]+)/(?<hash>[a-zA-Z0-9\\._\\-]+)/(?<filename>[a-zA-Z0-9\\._\\-]+)$")
        configurations.forEach { c ->
            val cfg = project.configurations[c.name]
            if ((cfg.name == "runtimeClasspath" || cfg.name == "zinc") && cfg.isCanBeResolved) {
                val key = if (cfg.name == "runtimeClasspath") "implementation" else cfg.name
                cfg.files.forEach { f ->
                    val matcher = pathRegex.matcher("${f}")
                    if (matcher.matches()) {
                        val groupName = matcher.group("group")
                        val artifactName = matcher.group("artifact")
                        val artifactVersion = matcher.group("version")
                        val current = items.getOrDefault(key, mutableListOf<String>())
                        current.add("libs/${f.name}")
                        items = items.plus(key to current)
                    }
                    Files.copy(Paths.get(f.absolutePath), Paths.get("examples/libs/${f.name}"), StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
        val engine = GStringTemplateEngine()
        val text = engine.createTemplate(file("compiler.sh.groovy")).make(mapOf(
                "items" to items
        ))
        val fw = FileWriter(file("examples/compiler.sh"))
        fw.write(text.toString())
        fw.flush()
        fw.close()
    }
}
