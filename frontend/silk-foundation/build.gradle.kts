import com.varabyte.kobweb.gradle.publish.FILTER_OUT_MULTIPLATFORM_PUBLICATIONS
import com.varabyte.kobweb.gradle.publish.set

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("kobweb-compose")
    id("com.varabyte.kobweb.internal.publish")
}

group = "com.varabyte.kobweb"
version = libs.versions.kobweb.libs.get()

kotlin {
    js {
        browser()
    }

    sourceSets {
        jsMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.html.core)

            implementation(projects.frontend.composeHtmlExt)
            api(projects.frontend.kobwebCompose)
        }
        jsTest.dependencies {
            implementation(kotlin("test"))
            implementation(projects.frontend.test.composeTestUtils)
            implementation(libs.truthish)
        }
    }
}

kobwebPublication {
    artifactId.set("silk-foundation")
    description.set("The foundational layer of Silk that provides general purpose styling functionality like component styles, keyframes, and .")
    filter.set(FILTER_OUT_MULTIPLATFORM_PUBLICATIONS)
}
