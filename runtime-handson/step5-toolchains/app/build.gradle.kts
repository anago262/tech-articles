plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("handson.App")
}

// --- Step 5.2 で以下のコメントを外す ---
// java {
//     toolchain {
//         languageVersion.set(JavaLanguageVersion.of(11))
//     }
// }
