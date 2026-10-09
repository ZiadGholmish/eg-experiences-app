plugins {
    id("bahr.kmp.library")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.kotlinx.datetime)
        implementation(libs.kotlinx.serialization.json)
        api(libs.kermit)
    }
}
