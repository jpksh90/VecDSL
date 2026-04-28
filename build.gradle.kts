plugins {
    kotlin("jvm") version "2.3.10"
    antlr
    application
}

group = "dk.sdu"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    antlr("org.antlr:antlr4:4.13.2")
    implementation("org.antlr:antlr4-runtime:4.13.2")
    testImplementation(kotlin("test"))
}

sourceSets {
    main {
        java.srcDir("build/generated-src/antlr/main")
        kotlin.srcDir("build/generated-src/antlr/main")
    }
    test {
        java.srcDir("build/generated-src/antlr/main")
        kotlin.srcDir("build/generated-src/antlr/main")
    }
}

tasks.generateGrammarSource {
    maxHeapSize = "64m"
    arguments = arguments + listOf("-visitor", "-long-messages")
}

tasks.compileTestKotlin {
    dependsOn(tasks.generateTestGrammarSource)
}

kotlin {
    jvmToolchain(25)
}


tasks.compileKotlin {
    dependsOn(tasks.generateGrammarSource)
}

tasks.build {
    dependsOn(tasks.generateGrammarSource)
}

tasks.test {
    dependsOn(tasks.generateGrammarSource)
}


tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("MainKt")
}


val armadilloHome = System.getenv("ARMADILLO_HOME") ?: "/usr/local"

val buildArmadillo by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds C++ code with Armadillo if present."
    commandLine = listOf("g++", "-O2", "-std=c++17", "-I$armadilloHome/include", "-L$armadilloHome/lib", "-larmadillo", "-o", "build/armadillo_test", "src/main/cpp/armadillo_test.cpp")
    // Only run if the C++ source exists
    onlyIf { file("src/main/cpp/armadillo_test.cpp").exists() }
}

tasks.named("build") {
    dependsOn(buildArmadillo)
}

tasks.register("runDemo1", JavaExec::class) {
    group = "demo"
    description = "Run VecDSL demo1 and generate Armadillo C++ code."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dk.sdu.MainKt")
    args = listOf("demo/demo1.dsl", "demo/demo1.cpp")
}

tasks.register("runDemo2", JavaExec::class) {
    group = "demo"
    description = "Run VecDSL demo2 and generate Armadillo C++ code."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dk.sdu.MainKt")
    args = listOf("demo/demo2.dsl", "demo/demo2.cpp")
}

tasks.register("runDemos") {
    group = "demo"
    description = "Run all VecDSL demos."
    dependsOn("runDemo1", "runDemo2")
}