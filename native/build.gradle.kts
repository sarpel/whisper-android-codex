plugins {
    id("com.android.library")
}

android {
    compileSdk = 34
    namespace = "com.app.whisper.nativelib"

    defaultConfig {
        minSdk = 24
        ndk {
            abiFilters.add("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                arguments(
                    "-DANDROID_ARM_NEON=ON",
                    "-DANDROID_STL=c++_shared"
                )
            }
        }
    }
    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
