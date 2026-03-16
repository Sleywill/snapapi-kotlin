plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    `java-library`
    `maven-publish`
}

group   = "pics.snapapi"
version = "3.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withJavadocJar()
    withSourcesJar()
}

kotlin {
    jvmToolchain(11)
}

repositories {
    mavenCentral()
}

dependencies {
    // HTTP client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("SnapAPI Kotlin SDK")
                description.set("Official Kotlin SDK for SnapAPI.pics — screenshot, scrape, extract, PDF")
                url.set("https://github.com/Sleywill/snapapi-kotlin")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("sleywill")
                        name.set("SnapAPI Team")
                        email.set("hello@snapapi.pics")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/Sleywill/snapapi-kotlin.git")
                    developerConnection.set("scm:git:ssh://github.com/Sleywill/snapapi-kotlin.git")
                    url.set("https://github.com/Sleywill/snapapi-kotlin")
                }
            }
        }
    }
}
