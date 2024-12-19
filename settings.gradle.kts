rootProject.name = "hmpps-digital-prison-reporting-lib"

pluginManagement {
  val jvmPluginVersion: String by settings

  plugins {
    kotlin("jvm") version jvmPluginVersion
    kotlin("plugin.spring") version jvmPluginVersion
    kotlin("plugin.jpa") version jvmPluginVersion
  }
}