plugins {
    id("com.android.library")
}

android {
    namespace = "com.scloud.httpdns.sdk"
    compileSdk = 34

    defaultConfig {
        minSdk = 19
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

}

dependencies {
    implementation("com.google.android.gms:play-services-cronet:18.1.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
