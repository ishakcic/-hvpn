plugins {
    id("com.android.application") version "8.6.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
    // DÜZELTME: Bu eklenti hiç yoktu. @Serializable'ın serializer() üretebilmesi,
    // ve encodeToString/decodeFromString'in tipi çıkarabilmesi için zorunlu.
    // Eksikliği BackupManager.kt'deki "Unresolved reference: serializer" ve
    // ardından gelen tüm tip çıkarım hatalarının (forEach belirsizliği dahil) sebebiydi.
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
