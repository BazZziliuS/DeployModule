plugins {
    java
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://maven.gravit.pro")
}

val launcherVersion = "5.6.9"

dependencies {
    compileOnly("pro.gravit.launcher:launcher-core:${launcherVersion}")
    compileOnly("pro.gravit.launcher:launcher-ws-api:${launcherVersion}")
    compileOnly("pro.gravit.launcher:launchserver-api:${launcherVersion}")
    compileOnly("io.netty:netty-codec-http:4.1.107.Final")
    compileOnly("com.google.code.gson:gson:2.11.0")
    compileOnly("org.apache.logging.log4j:log4j-api:2.24.3")
}

tasks.jar {
    manifest {
        attributes(
            "Module-Main-Class" to "pro.gravit.launchermodules.deploymodule.DeployModule"
        )
    }
    archiveBaseName.set("DeployModule")
}
