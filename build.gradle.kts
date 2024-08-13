plugins {
    kotlin("jvm") version "1.9.23"
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