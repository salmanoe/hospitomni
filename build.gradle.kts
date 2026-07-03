/*
 * HospitOmni root build — Gradle Kotlin DSL multi-module.
 *
 * Java 25 LTS toolchain · Spring Boot 4.1.0 BOM · JVM (not native) ·
 * no Lombok/MapStruct (records + JSpecify @NullMarked instead).
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */

import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension

plugins {
    java
    id("org.springframework.boot") version "4.1.0" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "io.spring.dependency-management")

    group = "id.co.hospitomni"
    version = "0.0.1-SNAPSHOT"

    repositories { mavenCentral() }

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    // Import the Spring Boot BOM so Spring-managed artifacts need no versions.
    configure<DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.0")
        }
    }

    dependencies {
        // Nullness contract for every module — @NullMarked per package,
        // matching Spring Framework 7's own nullability model.
        "api"("org.jspecify:jspecify:1.0.0")

        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        // Gradle 9 no longer bundles the JUnit Platform launcher on the test
        // runtime classpath — it must be declared explicitly (version via BOM).
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<JavaCompile>().configureEach {
        // -parameters: retain method parameter names so Spring MVC can infer
        // @PathVariable/@RequestParam names without explicit value=. The Boot
        // Gradle plugin sets this, but only `bootstrap` applies that plugin —
        // the library modules need it set here.
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
