
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

val resourcePackVersion = providers.gradleProperty("resourcePackVersion").orElse(project.version.toString())

val modernityResourcePackZip by tasks.registering(Zip::class) {
    group = "build"
    description = "Packages the Modernity adapter resource pack."
    archiveBaseName.set("Modernity-TomsStorage")
    archiveVersion.set(resourcePackVersion)
    destinationDirectory.set(layout.buildDirectory.dir("resourcepacks"))
    from("resourcepacks/Modernity-Tom'sStorage")
    include("pack.mcmeta", "pack.png", "assets/**", "README*", "LICENSE*")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val packageResourcePacks by tasks.registering {
    group = "build"
    description = "Builds the Modernity resource pack ZIP for distribution."
    dependsOn(modernityResourcePackZip)
}

tasks.assemble {
    dependsOn(packageResourcePacks)
}
