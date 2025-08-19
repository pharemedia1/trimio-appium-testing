@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem
@rem SPDX-License-Identifier: Apache-2.0
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  testing startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%..

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and TESTING_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS=

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\lib\testing-1.0-SNAPSHOT.jar;%APP_HOME%\lib\java-client-9.5.0.jar;%APP_HOME%\lib\testng-7.11.0.jar;%APP_HOME%\lib\gson-2.13.1.jar;%APP_HOME%\lib\slf4j-api-2.0.17.jar;%APP_HOME%\lib\selenium-support-4.34.0.jar;%APP_HOME%\lib\selenium-remote-driver-4.34.0.jar;%APP_HOME%\lib\selenium-http-4.34.0.jar;%APP_HOME%\lib\selenium-manager-4.34.0.jar;%APP_HOME%\lib\selenium-json-4.34.0.jar;%APP_HOME%\lib\selenium-os-4.34.0.jar;%APP_HOME%\lib\selenium-api-4.34.0.jar;%APP_HOME%\lib\guava-33.4.8-jre.jar;%APP_HOME%\lib\jspecify-1.0.0.jar;%APP_HOME%\lib\jcommander-1.83.jar;%APP_HOME%\lib\jquery-3.7.1.jar;%APP_HOME%\lib\error_prone_annotations-2.38.0.jar;%APP_HOME%\lib\auto-service-annotations-1.1.1.jar;%APP_HOME%\lib\opentelemetry-exporter-logging-1.51.0.jar;%APP_HOME%\lib\opentelemetry-sdk-extension-autoconfigure-1.51.0.jar;%APP_HOME%\lib\opentelemetry-sdk-extension-autoconfigure-spi-1.51.0.jar;%APP_HOME%\lib\opentelemetry-sdk-1.51.0.jar;%APP_HOME%\lib\opentelemetry-sdk-trace-1.51.0.jar;%APP_HOME%\lib\opentelemetry-sdk-metrics-1.51.0.jar;%APP_HOME%\lib\opentelemetry-sdk-logs-1.51.0.jar;%APP_HOME%\lib\opentelemetry-sdk-common-1.51.0.jar;%APP_HOME%\lib\opentelemetry-api-1.51.0.jar;%APP_HOME%\lib\opentelemetry-context-1.51.0.jar;%APP_HOME%\lib\byte-buddy-1.17.6.jar;%APP_HOME%\lib\failureaccess-1.0.3.jar;%APP_HOME%\lib\listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar;%APP_HOME%\lib\j2objc-annotations-3.0.0.jar;%APP_HOME%\lib\commons-exec-1.5.0.jar


@rem Execute testing
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %TESTING_OPTS%  -classpath "%CLASSPATH%" com.trimio.tests.TrimioLoginTest %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable TESTING_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%TESTING_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
