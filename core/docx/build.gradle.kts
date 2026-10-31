plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}

val samplesDir = layout.buildDirectory.dir("samples")

tasks.test {
    // Tests write sample documents here for CI's LibreOffice check. Declaring the folder as an
    // output makes Gradle restore it together with cached test results instead of skipping it.
    systemProperty("smartword.samples", samplesDir.get().asFile.absolutePath)
    outputs.dir(samplesDir)
}
