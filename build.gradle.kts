import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm") version "1.7.10"
  application
}

group = "nl.miruvor"
version = "1.0-SNAPSHOT"

repositories {
  mavenCentral()
}

val versions = mapOf(
  "scala" to "2.13",
  "akka" to "2.6.20",
  "akka-http" to "10.2.10"
)

dependencies {
  implementation(platform("com.typesafe.akka:akka-bom_${versions["scala"]}:2.6.20"))

  implementation("com.typesafe.akka:akka-persistence-typed_2.13")
  implementation("com.typesafe.akka:akka-serialization-jackson_2.13")
  implementation("org.scala-lang:scala-library:2.13.9")
  implementation("ch.qos.logback:logback-classic:1.2.11")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.11.4")
  implementation("org.jetbrains.kotlin:kotlin-reflect")

  testImplementation(kotlin("test"))
  testImplementation("com.typesafe.akka:akka-actor-testkit-typed_2.13")
  testImplementation("org.assertj:assertj-core:3.11.1")
  testImplementation("junit:junit:4.13.1")
}

tasks.test {
  useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
  kotlinOptions.jvmTarget = "11"
}

application {
  mainClass.set("MainKt")
}
