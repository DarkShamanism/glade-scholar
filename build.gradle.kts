import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("net.fabricmc.fabric-loom-remap")
	`maven-publish`
	id("org.jetbrains.kotlin.jvm")
	id("org.jetbrains.kotlin.plugin.serialization")
}

base {
	archivesName.set("glade-scribe")
}

loom {
	mods {
		register("glade_scribe") {
			sourceSet(sourceSets.main.get())
		}
	}
}

sourceSets {
	named("main") {
		java.srcDir("src/client/java")
		resources.srcDir("src/client/resources")
	}
}

dependencies {
	minecraft(libs.minecraft)
	mappings(variantOf(libs.yarn) { classifier("v2") })
	modImplementation(libs.fabric.loader)
	modImplementation(libs.fabric.api)
	modImplementation(libs.fabric.kotlin)
	implementation(project(":glade-core"))
}

kotlin {
	compilerOptions {
		jvmTarget = JvmTarget.JVM_21
	}
	sourceSets.getByName("main").kotlin.srcDir("src/client/kotlin")
}

java {
	withSourcesJar()
	sourceCompatibility = JavaVersion.VERSION_21
	targetCompatibility = JavaVersion.VERSION_21
}

publishing {
	publications {
		register<MavenPublication>("mavenJava") {
			artifactId = base.archivesName.get()
			from(components["java"])
		}
	}
	repositories {}
}
