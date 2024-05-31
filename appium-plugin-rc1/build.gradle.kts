plugins {
    application
    `java-library`
}

val mainClassPath: String by rootProject.extra
val pluginClassDir = sourceSets.main.get().output.classesDirs.asPath.plus("/plugin/")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":Multilocator"))
    api(files("external-libs/BrainLic24.jar"))
    compileOnly("com.googlecode.json-simple:json-simple:1.1.1")
    implementation("io.appium:java-client:9.2.2")
    implementation("org.seleniumhq.selenium:selenium-java:4.19.0")
}

// Configure the application
application {
    mainClass.set(mainClassPath)
    executableDir = ""
}

tasks.startScripts{
    applicationName = "startScout"

}

//tasks.createStar createStartScripts(type: CreateStartScripts) {
//    outputDir = file('build/sample')
//    mainClass = 'org.gradle.test.Main'
//    applicationName = 'myApp'
//    classpath = files('path/to/some.jar')
//}



tasks.distZip{
    // Strategy for copy/overriding files
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // Copy the plugins
    into("${project.name}/plugin"){
        from(pluginClassDir)
        exclude("license/**")
    }
}

tasks.register<Copy>("extractDistZip") {
    dependsOn("distZip")
    val distZipTask = tasks.named<Zip>("distZip").get()

    from(zipTree(distZipTask.archiveFile))
    into("${buildDir}/distributions/")
}

tasks.installDist {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    into("plugin"){
        from(pluginClassDir)
        exclude("license/**")
    }
}

tasks.clean{
    doFirst {
        delete (projectDir.path.plus("/plugin/"))
        delete (projectDir.path.plus("/settings/"))
        delete (projectDir.path.plus("/client_log.txt"))
    }
}

tasks.distTar{
    enabled = false
}
tasks.distTar{
    enabled = false
}

// *** DEBUGGING, copy plugins (class files) into exec dir
tasks.register<Copy>("buildForDebugging") {
    dependsOn(tasks.build)
    dependsOn(tasks.installDist)
    mkdir("plugin")
    copy {
        //from(tasks.processResources)
        from(pluginClassDir)
        into(projectDir.path.plus("/plugin/"))
    }
}

sourceSets {
    main {
        java {
            exclude("plugin/LegacyAppiumPlugin.java")
        }
    }
}

tasks.named<Zip>("distZip") {
    finalizedBy("extractDistZip")
}
//tasks.getByName<Test>("test") {
//    useJUnitPlatform()
//}
