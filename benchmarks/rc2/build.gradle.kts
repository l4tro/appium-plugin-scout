import java.text.SimpleDateFormat
import java.util.*

plugins {
    application
    `java-library`
    id("me.champeau.jmh") version "0.7.2" apply true
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
    implementation("org.seleniumhq.selenium:selenium-java:4.20.0")
    implementation("org.openjdk.jmh:jmh-core:1.37")
    implementation("org.openjdk.jmh:jmh-generator-annprocess:1.37")
    testImplementation("org.mockito:mockito-core:5.11.0")
}

// Configure the application
application {
    mainClass.set(mainClassPath)
    executableDir = ""
}

jmh {
    resultFormat.set("JSON")
    val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm")
    val timestamp = sdf.format(Date())
    resultsFile.set(file("build/results/results-$timestamp.json"))

    includes.add(".*Appium.*")
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

tasks.named<Zip>("distZip") {
    finalizedBy("extractDistZip")
}


//tasks.getByName<Test>("test") {
//    useJUnitPlatform()
//}
