plugins {
    id("com.android.library") version "8.7.3" apply false
}

// TODO: this is custom environment variable that needs to be set in the system
val flutterSdk: String? = System.getenv("FLUTTER_ROOT")

if (flutterSdk != null) {
    subprojects {
        afterEvaluate {
            dependencies.add(
                "compileOnly",
                files("$flutterSdk/bin/cache/artifacts/engine/android-arm/flutter.jar")
            )
        }
    }
//    处理依赖包namespace问题
    subprojects {
        afterEvaluate { project ->
            if (project.hasProperty('android')) {
                project.android {
                    if (namespace == null) {
                        namespace project.group
                    }
                }
            }
        }
    }
}
