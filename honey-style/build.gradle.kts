plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "com.reacthive.honeystyle"
version = providers.gradleProperty("pluginVersion").getOrElse("0.1.0")

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Compile against the locally installed IDE so no IDE distribution is downloaded.
        local(
            providers.gradleProperty("platformLocalPath")
                .orElse(providers.environmentVariable("WEBSTORM_HOME"))
                .orElse("/Applications/WebStorm.app/Contents")
        )
        // JS/TS PSI (JSReferenceExpression, JSStringTemplateExpression, ...).
        bundledPlugin("JavaScript")
    }

    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    // No .form files, so the Java instrumenter is not needed.
    instrumentCode = false

    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild").getOrElse("252")

            val until = providers.gradleProperty("pluginUntilBuild").getOrElse("")
            if (until.isNotBlank()) untilBuild = until else untilBuild.unset()
        }
    }
}

// The IntelliJ Platform ships Java 25 bytecode, so the plugin must target it too.
java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
}

// The unit tests cover the palette/color logic only, so they run as plain JUnit
// without booting the IDE test fixture.
tasks.test {
    useJUnit()
}
