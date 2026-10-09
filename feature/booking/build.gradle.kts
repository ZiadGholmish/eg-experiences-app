plugins {
    id("bahr.kmp.feature")
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(projects.core.datastore)
    }
}
