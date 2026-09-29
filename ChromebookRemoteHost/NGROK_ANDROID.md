# ngrok Android runtime

The official ngrok Java SDK documents Android support for ARM64 and ARMv7, but its native runtime must be packaged for the Android ABI. The Java SDK repository also has an open Android-support issue history, so this project deliberately does not pretend that adding the Java JAR alone is sufficient.

This project uses the official ngrok Java SDK and provides `tools/build-ngrok-native.sh` to build the matching native library from the official `ngrok-java` source. The script places:

- `app/libs/ngrok-java-native-classes.jar`
- `app/src/main/jniLibs/arm64-v8a/libngrok_java.so`
- `app/src/main/jniLibs/x86_64/libngrok_java.so`

For a real Chromebook, ARM64 is the important ABI on many models. x86_64 is included for compatible Android environments/emulators.

The native library should be built with a current Android NDK and checked for Android 16 KB page-size alignment before production distribution.
