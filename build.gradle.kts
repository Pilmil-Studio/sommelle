plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.paparazzi) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt) apply false
}

dependencies {
    kover(project(":core:model"))
    kover(project(":core:protocol"))
    kover(project(":core:presentation"))
    kover(project(":core:data"))
    kover(project(":shared"))
    kover(project(":feature:onboarding"))
    kover(project(":feature:home"))
    kover(project(":feature:diary"))
    kover(project(":feature:week"))
    kover(project(":feature:night"))
    kover(project(":feature:reminders"))
    kover(project(":feature:export"))
    kover(project(":feature:cognitive"))
}

subprojects {
    apply(plugin = "org.jetbrains.kotlinx.kover")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        parallel = true
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        setSource(files("src/main/kotlin", "src/commonMain/kotlin", "src/androidMain/kotlin", "src/iosMain/kotlin"))
        include("**/*.kt")
        exclude("**/build/**")
        reports {
            html.required.set(true)
            xml.required.set(false)
            txt.required.set(true)
        }
    }
}
