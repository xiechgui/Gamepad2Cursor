@echo off
setlocal
if exist gradlew.bat (
  call gradlew.bat assembleDebug
) else (
  echo This source package does not bundle the Gradle wrapper.
  echo Open it once in Android Studio, or run: gradle wrapper --gradle-version 8.7
  echo Then run gradlew.bat assembleDebug
)
endlocal
