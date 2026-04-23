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


tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("dk.sdu.MainKt")
}
