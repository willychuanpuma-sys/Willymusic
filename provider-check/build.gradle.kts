plugins { kotlin("jvm") version "2.4.20" }
kotlin { jvmToolchain(21) }
sourceSets {
    main { kotlin.srcDir("../app/src/main/kotlin/com/metrolist/music/providers") }
    test { kotlin.srcDir("../app/src/test/kotlin/com/metrolist/music/providers") }
}
dependencies {
    implementation("io.ktor:ktor-client-core:3.6.0")
    implementation("io.ktor:ktor-client-okhttp:3.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation("io.ktor:ktor-client-mock:3.6.0")
    testImplementation("junit:junit:4.13.2")
}
tasks.test {
    useJUnit()
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
}
