plugins {
    java
}

repositories {
    gradlePluginPortal()

    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/Gamula/pvpwars-libraries")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
        }
    }
}

dependencies {
    compileOnly("net.pvpwars:pvpwars-libs:1.1.3-SNAPSHOT")
}
