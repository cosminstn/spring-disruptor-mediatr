import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "2.5.3"
    id("io.spring.dependency-management") version "1.0.11.RELEASE"
    kotlin("jvm") version "1.5.21"
    kotlin("plugin.spring") version "1.5.21"
    kotlin("plugin.serialization") version "1.5.21"

    `maven-publish`
}

group = "tech.sharply"
version = "0.0.2"
java.sourceCompatibility = JavaVersion.VERSION_1_8

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/cosmyn9708/spring-disruptor-mediatr/")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_USERNAME")
                password =
                    project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_PACKAGES_REPOSITORY_TOKEN")
            }
        }
    }
    publications {
        register<MavenPublication>("gpr") {
            from(components["java"])
        }
    }
}
repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.2.2")

    // https://mvnrepository.com/artifact/com.lmax/disruptor
    implementation("com.lmax:disruptor:3.4.4")

    testImplementation("org.awaitility:awaitility-kotlin:4.1.0")

    developmentOnly("org.springframework.boot:spring-boot-devtools")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "1.8"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// region Version Increment
data class Version(
    val major: Int,
    val minor: Int,
    val patch: Int
) {

    fun bumpPatch(): Version {
        return Version(this.major, this.minor, patch + 1)
    }

    fun bumpMinor(): Version {
        return Version(this.major, this.minor + 1, 0)
    }

    fun bumpMajor(): Version {
        return Version(this.major + 1, 0, 0)
    }

    override fun toString(): String {
        return "$major.$minor.$patch"
    }

    companion object {
        fun versionFromCode(code: String): Version {
            val versionParts = code.toLowerCase()
                .replace(" ", "")
                .replace("version", "")
                .replace("snapshot", "")
                .replace("-", "")
                .split(".")
            if (versionParts.size != 3) {
                throw IllegalArgumentException(
                    "Invalid version code! " +
                            "Must contain major, minor and patch, separated by dots!"
                )
            }
            return Version(versionParts[0].toInt(), versionParts[1].toInt(), versionParts[2].toInt())
        }
    }
}

tasks.create("bumpPatch") {
    doLast {
        val currentVersion = Version.versionFromCode(version.toString())
        val newVersion = currentVersion.bumpPatch()
        println("New version: $newVersion")
        val buildFileContent = buildFile.readText()
            .replaceFirst("version = \"$currentVersion\"", "version = \"$newVersion\"")
        buildFile.writeText(buildFileContent)
    }
}

tasks.create("bumpMinor") {
    doLast {
        val currentVersion = Version.versionFromCode(version.toString())
        val newVersion = currentVersion.bumpMinor()
        println("New version: $newVersion")
        val buildFileContent = buildFile.readText()
            .replaceFirst("version = \"$currentVersion\"", "version = \"$newVersion\"")
        buildFile.writeText(buildFileContent)
    }
}

tasks.create("bumpMajor") {
    doLast {
        val currentVersion = Version.versionFromCode(version.toString())
        val newVersion = currentVersion.bumpMinor()
        println("New version: $newVersion")
        val buildFileContent = buildFile.readText()
            .replaceFirst("version = \"$currentVersion\"", "version = \"$newVersion\"")
        buildFile.writeText(buildFileContent)
    }
}
// endregion