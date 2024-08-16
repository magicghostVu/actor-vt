plugins {
    kotlin("jvm") version "1.9.23"
    id("maven-publish")
}

group = "org.magicghostvu"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val log4jVersion = "2.17.2"
dependencies {
    //testImplementation(kotlin("test"))
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:$log4jVersion"){
        exclude("org.slf4j", "slf4j-api")
    }
    api("org.slf4j:slf4j-api:1.7.25")
    implementation("org.apache.logging.log4j:log4j-core:$log4jVersion")
}

/*tasks.test {
    useJUnitPlatform()
}*/
kotlin {
    jvmToolchain(21)
}

java {
    withSourcesJar()
    //withJavadocJar()
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/magicghostVu/actor-vt")
            credentials() {
                username = "magicghostVu"
                password = project.properties.getValue("deploy.token") as String
            }
            /*authentication {
                create<HttpHeaderAuthentication>("header")
            }*/
        }
    }
    publications {
        create<MavenPublication>("maven") {
            groupId = "org.magicghostvu"
            artifactId = "actor-vt"
            version = "0.0.2-test"
            from(components["kotlin"])
            artifact(tasks["sourcesJar"])
        }
    }
}