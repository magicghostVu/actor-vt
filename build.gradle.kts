import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    `java-library`
    id("maven-publish")
    id("net.ltgt.errorprone") version "4.1.0"
}

group = "org.magicghostvu"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val log4jVersion = "2.17.2"

dependencies {
    api("org.slf4j:slf4j-api:1.7.25")

    // log4j2 as the slf4j implementation (runtimeOnly — not exposed to library consumers)
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j-impl:$log4jVersion") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    runtimeOnly("org.apache.logging.log4j:log4j-core:$log4jVersion")

    compileOnly("org.jspecify:jspecify:1.0.0")
    testCompileOnly("org.jspecify:jspecify:1.0.0")

    errorprone("com.google.errorprone:error_prone_core:2.49.0")
    errorprone("com.uber.nullaway:nullaway:0.13.4")

    testImplementation(platform("org.junit:junit-bom:5.11.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("log4j.configurationFile", "${projectDir}/log4j2.xml")
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        showExceptions = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-XDaddTypeAnnotationsToSymbol=true")
    options.errorprone {
        check("NullAway", CheckSeverity.ERROR)
        option("NullAway:AnnotatedPackages", "org.magicghostvu.actorvt.*")
    }
}



publishing {
    /*repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/magicghostVu/actor-vt")
            credentials() {
                username = "magicghostVu"
                password = project.properties.getValue("deploy.token") as String
            }
        }
    }*/

    repositories {
        maven {
            credentials(HttpHeaderCredentials::class) {
                name = "Private-Token"
                value = project.properties.getValue("deploy.token") as String
            }
            url = uri("https://gitlab.zingplay.com/api/v4/projects/178/packages/maven")
            authentication {
                create<HttpHeaderAuthentication>("header")
            }
        }
    }

    publications {
        create<MavenPublication>("maven") {
            groupId = "org.magicghostvu"
            artifactId = "actor-vt"
            version = "0.2"
            from(components["java"])
        }
    }
}