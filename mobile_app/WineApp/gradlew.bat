@echo off
setlocal
set GRADLE_HOME=%~dp0..\gradle
set JAVA_EXE=java.exe
if not "%JAVA_HOME%"=="" set JAVA_EXE=%JAVA_HOME%\bin\java.exe
"%JAVA_EXE%" -classpath "%GRADLE_HOME%\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*