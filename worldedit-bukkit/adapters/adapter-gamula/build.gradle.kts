plugins {
    java
    `java-library`
}

applyCommonConfiguration()

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
    compileOnly(fileTree("libs/"))
    implementation("javax.annotation:javax.annotation-api:1.3.2")
    implementation("io.papermc:paperlib:1.0.7")
    implementation(project(":worldedit-bukkit"))
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:-deprecation")
}
