plugins {
    id("bahr.kmp.library")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(projects.core.common)

        api(libs.datastore.preferences)
        implementation(libs.okio)
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.koin.core)
    }
}
