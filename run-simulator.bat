@echo off
call gradlew.bat :tools:webhook-simulator:bootJar -q && java -jar tools\webhook-simulator\build\libs\webhook-simulator-0.0.1-SNAPSHOT.jar
