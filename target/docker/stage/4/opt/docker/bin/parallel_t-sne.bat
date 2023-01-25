@REM parallel_t-sne launcher script
@REM
@REM Environment:
@REM JAVA_HOME - location of a JDK home dir (optional if java on path)
@REM CFG_OPTS  - JVM options (optional)
@REM Configuration:
@REM PARALLEL_T_SNE_config.txt found in the PARALLEL_T_SNE_HOME.
@setlocal enabledelayedexpansion
@setlocal enableextensions

@echo off


if "%PARALLEL_T_SNE_HOME%"=="" (
  set "APP_HOME=%~dp0\\.."

  rem Also set the old env name for backwards compatibility
  set "PARALLEL_T_SNE_HOME=%~dp0\\.."
) else (
  set "APP_HOME=%PARALLEL_T_SNE_HOME%"
)

set "APP_LIB_DIR=%APP_HOME%\lib\"

rem Detect if we were double clicked, although theoretically A user could
rem manually run cmd /c
for %%x in (!cmdcmdline!) do if %%~x==/c set DOUBLECLICKED=1

rem FIRST we load the config file of extra options.
set "CFG_FILE=%APP_HOME%\PARALLEL_T_SNE_config.txt"
set CFG_OPTS=
call :parse_config "%CFG_FILE%" CFG_OPTS

rem We use the value of the JAVA_OPTS environment variable if defined, rather than the config.
set _JAVA_OPTS=%JAVA_OPTS%
if "!_JAVA_OPTS!"=="" set _JAVA_OPTS=!CFG_OPTS!

rem We keep in _JAVA_PARAMS all -J-prefixed and -D-prefixed arguments
rem "-J" is stripped, "-D" is left as is, and everything is appended to JAVA_OPTS
set _JAVA_PARAMS=
set _APP_ARGS=

set "APP_CLASSPATH=%APP_LIB_DIR%\parallel_t-sne.parallel_t-sne-0.1.0-SNAPSHOT.jar;%APP_LIB_DIR%\org.scala-lang.scala-library-2.13.10.jar;%APP_LIB_DIR%\org.apache.spark.spark-core_2.13-3.3.1.jar;%APP_LIB_DIR%\org.apache.spark.spark-sql_2.13-3.3.1.jar;%APP_LIB_DIR%\org.apache.spark.spark-hive_2.13-3.3.1.jar;%APP_LIB_DIR%\org.apache.spark.spark-mllib_2.13-3.3.1.jar;%APP_LIB_DIR%\org.scala-lang.modules.scala-parallel-collections_2.13-1.0.3.jar;%APP_LIB_DIR%\org.apache.avro.avro-1.11.0.jar;%APP_LIB_DIR%\org.apache.avro.avro-mapred-1.11.0.jar;%APP_LIB_DIR%\com.twitter.chill_2.13-0.10.0.jar;%APP_LIB_DIR%\com.twitter.chill-java-0.10.0.jar;%APP_LIB_DIR%\org.apache.xbean.xbean-asm9-shaded-4.20.jar;%APP_LIB_DIR%\org.apache.hadoop.hadoop-client-api-3.3.2.jar;%APP_LIB_DIR%\org.apache.hadoop.hadoop-client-runtime-3.3.2.jar;%APP_LIB_DIR%\org.apache.spark.spark-launcher_2.13-3.3.1.jar;%APP_LIB_DIR%\org.apache.spark.spark-kvstore_2.13-3.3.1.jar;%APP_LIB_DIR%\org.apache.spark.spark-network-common_2.13-3.3.1.jar;%APP_LIB_DIR%\org.apache.spark.spark-network-shuffle_2.13-3.3.1.jar;%APP_LIB_DIR%\org.apache.spark.spark-unsafe_2.13-3.3.1.jar;%APP_LIB_DIR%\javax.activation.activation-1.1.1.jar;%APP_LIB_DIR%\org.apache.curator.curator-recipes-2.13.0.jar;%APP_LIB_DIR%\org.apache.zookeeper.zookeeper-3.6.2.jar;%APP_LIB_DIR%\jakarta.servlet.jakarta.servlet-api-4.0.3.jar;%APP_LIB_DIR%\commons-codec.commons-codec-1.15.jar;%APP_LIB_DIR%\org.apache.commons.commons-lang3-3.12.0.jar;%APP_LIB_DIR%\org.apache.commons.commons-math3-3.6.1.jar;%APP_LIB_DIR%\org.apache.commons.commons-text-1.9.jar;%APP_LIB_DIR%\commons-io.commons-io-2.11.0.jar;%APP_LIB_DIR%\commons-collections.commons-collections-3.2.2.jar;%APP_LIB_DIR%\org.apache.commons.commons-collections4-4.4.jar;%APP_LIB_DIR%\com.google.code.findbugs.jsr305-3.0.2.jar;%APP_LIB_DIR%\org.slf4j.slf4j-api-1.7.35.jar;%APP_LIB_DIR%\org.slf4j.jul-to-slf4j-1.7.32.jar;%APP_LIB_DIR%\org.slf4j.jcl-over-slf4j-1.7.32.jar;%APP_LIB_DIR%\org.apache.logging.log4j.log4j-slf4j-impl-2.17.2.jar;%APP_LIB_DIR%\org.apache.logging.log4j.log4j-api-2.17.2.jar;%APP_LIB_DIR%\org.apache.logging.log4j.log4j-core-2.17.2.jar;%APP_LIB_DIR%\org.apache.logging.log4j.log4j-1.2-api-2.17.2.jar;%APP_LIB_DIR%\com.ning.compress-lzf-1.1.jar;%APP_LIB_DIR%\org.xerial.snappy.snappy-java-1.1.8.4.jar;%APP_LIB_DIR%\org.lz4.lz4-java-1.8.0.jar;%APP_LIB_DIR%\com.github.luben.zstd-jni-1.5.2-1.jar;%APP_LIB_DIR%\org.roaringbitmap.RoaringBitmap-0.9.25.jar;%APP_LIB_DIR%\org.scala-lang.modules.scala-xml_2.13-1.2.0.jar;%APP_LIB_DIR%\org.scala-lang.scala-reflect-2.13.10.jar;%APP_LIB_DIR%\org.json4s.json4s-jackson_2.13-3.7.0-M11.jar;%APP_LIB_DIR%\org.glassfish.jersey.core.jersey-client-2.36.jar;%APP_LIB_DIR%\org.glassfish.jersey.core.jersey-common-2.36.jar;%APP_LIB_DIR%\org.glassfish.jersey.core.jersey-server-2.36.jar;%APP_LIB_DIR%\org.glassfish.jersey.containers.jersey-container-servlet-2.36.jar;%APP_LIB_DIR%\org.glassfish.jersey.containers.jersey-container-servlet-core-2.36.jar;%APP_LIB_DIR%\org.glassfish.jersey.inject.jersey-hk2-2.36.jar;%APP_LIB_DIR%\io.netty.netty-all-4.1.74.Final.jar;%APP_LIB_DIR%\com.clearspring.analytics.stream-2.9.6.jar;%APP_LIB_DIR%\io.dropwizard.metrics.metrics-core-4.2.7.jar;%APP_LIB_DIR%\io.dropwizard.metrics.metrics-jvm-4.2.7.jar;%APP_LIB_DIR%\io.dropwizard.metrics.metrics-json-4.2.7.jar;%APP_LIB_DIR%\io.dropwizard.metrics.metrics-graphite-4.2.7.jar;%APP_LIB_DIR%\io.dropwizard.metrics.metrics-jmx-4.2.7.jar;%APP_LIB_DIR%\com.fasterxml.jackson.core.jackson-databind-2.13.4.1.jar;%APP_LIB_DIR%\com.fasterxml.jackson.module.jackson-module-scala_2.13-2.13.4.jar;%APP_LIB_DIR%\org.apache.ivy.ivy-2.5.0.jar;%APP_LIB_DIR%\oro.oro-2.0.8.jar;%APP_LIB_DIR%\net.razorvine.pickle-1.2.jar;%APP_LIB_DIR%\net.sf.py4j.py4j-0.10.9.5.jar;%APP_LIB_DIR%\org.apache.spark.spark-tags_2.13-3.3.1.jar;%APP_LIB_DIR%\org.apache.commons.commons-crypto-1.1.0.jar;%APP_LIB_DIR%\org.spark-project.spark.unused-1.0.0.jar;%APP_LIB_DIR%\org.rocksdb.rocksdbjni-6.20.3.jar;%APP_LIB_DIR%\com.univocity.univocity-parsers-2.9.1.jar;%APP_LIB_DIR%\org.apache.spark.spark-sketch_2.13-3.3.1.jar;%APP_LIB_DIR%\org.apache.spark.spark-catalyst_2.13-3.3.1.jar;%APP_LIB_DIR%\org.apache.orc.orc-core-1.7.6.jar;%APP_LIB_DIR%\org.apache.orc.orc-mapreduce-1.7.6.jar;%APP_LIB_DIR%\org.apache.hive.hive-storage-api-2.7.2.jar;%APP_LIB_DIR%\org.apache.parquet.parquet-column-1.12.2.jar;%APP_LIB_DIR%\org.apache.parquet.parquet-hadoop-1.12.2.jar;%APP_LIB_DIR%\org.apache.hive.hive-common-2.3.9.jar;%APP_LIB_DIR%\org.apache.hive.hive-exec-2.3.9-core.jar;%APP_LIB_DIR%\org.apache.hive.hive-metastore-2.3.9.jar;%APP_LIB_DIR%\org.apache.hive.hive-serde-2.3.9.jar;%APP_LIB_DIR%\org.apache.hive.hive-shims-2.3.9.jar;%APP_LIB_DIR%\org.apache.hive.hive-llap-common-2.3.9.jar;%APP_LIB_DIR%\org.apache.hive.hive-llap-client-2.3.9.jar;%APP_LIB_DIR%\org.apache.httpcomponents.httpclient-4.5.13.jar;%APP_LIB_DIR%\org.codehaus.jackson.jackson-mapper-asl-1.9.13.jar;%APP_LIB_DIR%\joda-time.joda-time-2.10.13.jar;%APP_LIB_DIR%\org.jodd.jodd-core-3.5.2.jar;%APP_LIB_DIR%\org.datanucleus.datanucleus-core-4.1.17.jar;%APP_LIB_DIR%\org.apache.thrift.libthrift-0.12.0.jar;%APP_LIB_DIR%\org.apache.thrift.libfb303-0.9.3.jar;%APP_LIB_DIR%\org.apache.derby.derby-10.14.2.0.jar;%APP_LIB_DIR%\org.scala-lang.modules.scala-parser-combinators_2.13-1.1.2.jar;%APP_LIB_DIR%\org.apache.spark.spark-streaming_2.13-3.3.1.jar;%APP_LIB_DIR%\org.apache.spark.spark-graphx_2.13-3.3.1.jar;%APP_LIB_DIR%\org.apache.spark.spark-mllib-local_2.13-3.3.1.jar;%APP_LIB_DIR%\org.scalanlp.breeze_2.13-1.2.jar;%APP_LIB_DIR%\org.glassfish.jaxb.jaxb-runtime-2.3.2.jar;%APP_LIB_DIR%\dev.ludovic.netlib.blas-2.2.1.jar;%APP_LIB_DIR%\dev.ludovic.netlib.lapack-2.2.1.jar;%APP_LIB_DIR%\dev.ludovic.netlib.arpack-2.2.1.jar;%APP_LIB_DIR%\com.fasterxml.jackson.core.jackson-core-2.13.4.jar;%APP_LIB_DIR%\org.apache.commons.commons-compress-1.21.jar;%APP_LIB_DIR%\org.apache.avro.avro-ipc-1.11.0.jar;%APP_LIB_DIR%\com.esotericsoftware.kryo-shaded-4.0.2.jar;%APP_LIB_DIR%\commons-logging.commons-logging-1.2.jar;%APP_LIB_DIR%\org.fusesource.leveldbjni.leveldbjni-all-1.8.jar;%APP_LIB_DIR%\com.fasterxml.jackson.core.jackson-annotations-2.13.4.jar;%APP_LIB_DIR%\com.google.crypto.tink.tink-1.6.1.jar;%APP_LIB_DIR%\org.apache.curator.curator-framework-2.13.0.jar;%APP_LIB_DIR%\commons-lang.commons-lang-2.6.jar;%APP_LIB_DIR%\org.apache.zookeeper.zookeeper-jute-3.6.2.jar;%APP_LIB_DIR%\org.apache.yetus.audience-annotations-0.12.0.jar;%APP_LIB_DIR%\org.roaringbitmap.shims-0.9.25.jar;%APP_LIB_DIR%\org.json4s.json4s-core_2.13-3.7.0-M11.jar;%APP_LIB_DIR%\jakarta.ws.rs.jakarta.ws.rs-api-2.1.6.jar;%APP_LIB_DIR%\org.glassfish.hk2.external.jakarta.inject-2.6.1.jar;%APP_LIB_DIR%\jakarta.annotation.jakarta.annotation-api-1.3.5.jar;%APP_LIB_DIR%\org.glassfish.hk2.osgi-resource-locator-1.0.3.jar;%APP_LIB_DIR%\jakarta.validation.jakarta.validation-api-2.0.2.jar;%APP_LIB_DIR%\org.glassfish.hk2.hk2-locator-2.6.1.jar;%APP_LIB_DIR%\org.javassist.javassist-3.25.0-GA.jar;%APP_LIB_DIR%\io.netty.netty-buffer-4.1.74.Final.jar;%APP_LIB_DIR%\io.netty.netty-codec-4.1.74.Final.jar;%APP_LIB_DIR%\io.netty.netty-common-4.1.74.Final.jar;%APP_LIB_DIR%\io.netty.netty-handler-4.1.74.Final.jar;%APP_LIB_DIR%\io.netty.netty-tcnative-classes-2.0.48.Final.jar;%APP_LIB_DIR%\io.netty.netty-resolver-4.1.74.Final.jar;%APP_LIB_DIR%\io.netty.netty-transport-4.1.74.Final.jar;%APP_LIB_DIR%\io.netty.netty-transport-classes-epoll-4.1.74.Final.jar;%APP_LIB_DIR%\io.netty.netty-transport-native-unix-common-4.1.74.Final.jar;%APP_LIB_DIR%\io.netty.netty-transport-classes-kqueue-4.1.74.Final.jar;%APP_LIB_DIR%\io.netty.netty-transport-native-epoll-4.1.74.Final-linux-x86_64.jar;%APP_LIB_DIR%\io.netty.netty-transport-native-epoll-4.1.74.Final-linux-aarch_64.jar;%APP_LIB_DIR%\io.netty.netty-transport-native-kqueue-4.1.74.Final-osx-x86_64.jar;%APP_LIB_DIR%\io.netty.netty-transport-native-kqueue-4.1.74.Final-osx-aarch_64.jar;%APP_LIB_DIR%\com.thoughtworks.paranamer.paranamer-2.8.jar;%APP_LIB_DIR%\org.codehaus.janino.janino-3.0.16.jar;%APP_LIB_DIR%\org.codehaus.janino.commons-compiler-3.0.16.jar;%APP_LIB_DIR%\org.antlr.antlr4-runtime-4.8.jar;%APP_LIB_DIR%\org.apache.arrow.arrow-vector-7.0.0.jar;%APP_LIB_DIR%\org.apache.arrow.arrow-memory-netty-7.0.0.jar;%APP_LIB_DIR%\org.apache.orc.orc-shims-1.7.6.jar;%APP_LIB_DIR%\com.google.protobuf.protobuf-java-3.14.0.jar;%APP_LIB_DIR%\io.airlift.aircompressor-0.21.jar;%APP_LIB_DIR%\org.jetbrains.annotations-17.0.0.jar;%APP_LIB_DIR%\org.threeten.threeten-extra-1.5.0.jar;%APP_LIB_DIR%\org.apache.parquet.parquet-common-1.12.2.jar;%APP_LIB_DIR%\org.apache.parquet.parquet-encoding-1.12.2.jar;%APP_LIB_DIR%\org.apache.parquet.parquet-format-structures-1.12.2.jar;%APP_LIB_DIR%\org.apache.parquet.parquet-jackson-1.12.2.jar;%APP_LIB_DIR%\commons-cli.commons-cli-1.2.jar;%APP_LIB_DIR%\jline.jline-2.12.jar;%APP_LIB_DIR%\com.tdunning.json-1.8.jar;%APP_LIB_DIR%\com.github.joshelser.dropwizard-metrics-hadoop-metrics2-reporter-0.1.2.jar;%APP_LIB_DIR%\org.apache.hive.hive-vector-code-gen-2.3.9.jar;%APP_LIB_DIR%\org.antlr.antlr-runtime-3.5.2.jar;%APP_LIB_DIR%\org.antlr.ST4-4.0.4.jar;%APP_LIB_DIR%\com.google.code.gson.gson-2.8.6.jar;%APP_LIB_DIR%\stax.stax-api-1.0.1.jar;%APP_LIB_DIR%\javolution.javolution-5.5.1.jar;%APP_LIB_DIR%\com.jolbox.bonecp-0.8.0.RELEASE.jar;%APP_LIB_DIR%\com.zaxxer.HikariCP-2.5.1.jar;%APP_LIB_DIR%\org.datanucleus.datanucleus-api-jdo-4.2.4.jar;%APP_LIB_DIR%\org.datanucleus.datanucleus-rdbms-4.1.19.jar;%APP_LIB_DIR%\commons-pool.commons-pool-1.5.4.jar;%APP_LIB_DIR%\commons-dbcp.commons-dbcp-1.4.jar;%APP_LIB_DIR%\javax.jdo.jdo-api-3.0.1.jar;%APP_LIB_DIR%\org.datanucleus.javax.jdo-3.2.0-m3.jar;%APP_LIB_DIR%\net.sf.opencsv.opencsv-2.3.jar;%APP_LIB_DIR%\org.apache.hive.shims.hive-shims-common-2.3.9.jar;%APP_LIB_DIR%\org.apache.hive.shims.hive-shims-0.23-2.3.9.jar;%APP_LIB_DIR%\org.apache.hive.shims.hive-shims-scheduler-2.3.9.jar;%APP_LIB_DIR%\org.apache.httpcomponents.httpcore-4.4.13.jar;%APP_LIB_DIR%\org.codehaus.jackson.jackson-core-asl-1.9.13.jar;%APP_LIB_DIR%\net.sourceforge.f2j.arpack_combined_all-0.1.jar;%APP_LIB_DIR%\org.scalanlp.breeze-macros_2.13-1.2.jar;%APP_LIB_DIR%\com.github.fommil.netlib.core-1.1.2.jar;%APP_LIB_DIR%\com.github.wendykierp.JTransforms-3.1.jar;%APP_LIB_DIR%\com.chuusai.shapeless_2.13-2.3.3.jar;%APP_LIB_DIR%\org.typelevel.spire_2.13-0.17.0.jar;%APP_LIB_DIR%\org.scala-lang.modules.scala-collection-compat_2.13-2.2.0.jar;%APP_LIB_DIR%\jakarta.xml.bind.jakarta.xml.bind-api-2.3.2.jar;%APP_LIB_DIR%\com.sun.istack.istack-commons-runtime-3.0.8.jar;%APP_LIB_DIR%\org.tukaani.xz-1.9.jar;%APP_LIB_DIR%\com.esotericsoftware.minlog-1.3.0.jar;%APP_LIB_DIR%\org.objenesis.objenesis-2.5.1.jar;%APP_LIB_DIR%\org.apache.curator.curator-client-2.13.0.jar;%APP_LIB_DIR%\org.json4s.json4s-ast_2.13-3.7.0-M11.jar;%APP_LIB_DIR%\org.json4s.json4s-scalap_2.13-3.7.0-M11.jar;%APP_LIB_DIR%\org.glassfish.hk2.external.aopalliance-repackaged-2.6.1.jar;%APP_LIB_DIR%\org.glassfish.hk2.hk2-api-2.6.1.jar;%APP_LIB_DIR%\org.glassfish.hk2.hk2-utils-2.6.1.jar;%APP_LIB_DIR%\org.apache.arrow.arrow-format-7.0.0.jar;%APP_LIB_DIR%\org.apache.arrow.arrow-memory-core-7.0.0.jar;%APP_LIB_DIR%\com.google.flatbuffers.flatbuffers-java-1.12.0.jar;%APP_LIB_DIR%\com.google.guava.guava-16.0.1.jar;%APP_LIB_DIR%\org.apache.velocity.velocity-1.5.jar;%APP_LIB_DIR%\javax.transaction.jta-1.1.jar;%APP_LIB_DIR%\javax.transaction.transaction-api-1.1.jar;%APP_LIB_DIR%\pl.edu.icm.JLargeArrays-1.5.jar;%APP_LIB_DIR%\org.typelevel.spire-macros_2.13-0.17.0.jar;%APP_LIB_DIR%\org.typelevel.spire-platform_2.13-0.17.0.jar;%APP_LIB_DIR%\org.typelevel.spire-util_2.13-0.17.0.jar;%APP_LIB_DIR%\org.typelevel.algebra_2.13-2.0.1.jar;%APP_LIB_DIR%\javax.annotation.javax.annotation-api-1.3.2.jar;%APP_LIB_DIR%\org.typelevel.cats-kernel_2.13-2.1.1.jar;%APP_LIB_DIR%\io.netty.netty-transport-native-epoll-4.1.74.Final.jar"
set "APP_MAIN_CLASS=ScalaImplementation"
set "SCRIPT_CONF_FILE=%APP_HOME%\conf\application.ini"

rem Bundled JRE has priority over standard environment variables
if defined BUNDLED_JVM (
  set "_JAVACMD=%BUNDLED_JVM%\bin\java.exe"
) else (
  if "%JAVACMD%" neq "" (
    set "_JAVACMD=%JAVACMD%"
  ) else (
    if "%JAVA_HOME%" neq "" (
      if exist "%JAVA_HOME%\bin\java.exe" set "_JAVACMD=%JAVA_HOME%\bin\java.exe"
    )
  )
)

if "%_JAVACMD%"=="" set _JAVACMD=java

rem Detect if this java is ok to use.
for /F %%j in ('"%_JAVACMD%" -version  2^>^&1') do (
  if %%~j==java set JAVAINSTALLED=1
  if %%~j==openjdk set JAVAINSTALLED=1
)

rem BAT has no logical or, so we do it OLD SCHOOL! Oppan Redmond Style
set JAVAOK=true
if not defined JAVAINSTALLED set JAVAOK=false

if "%JAVAOK%"=="false" (
  echo.
  echo A Java JDK is not installed or can't be found.
  if not "%JAVA_HOME%"=="" (
    echo JAVA_HOME = "%JAVA_HOME%"
  )
  echo.
  echo Please go to
  echo   http://www.oracle.com/technetwork/java/javase/downloads/index.html
  echo and download a valid Java JDK and install before running parallel_t-sne.
  echo.
  echo If you think this message is in error, please check
  echo your environment variables to see if "java.exe" and "javac.exe" are
  echo available via JAVA_HOME or PATH.
  echo.
  if defined DOUBLECLICKED pause
  exit /B 1
)

rem if configuration files exist, prepend their contents to the script arguments so it can be processed by this runner
call :parse_config "%SCRIPT_CONF_FILE%" SCRIPT_CONF_ARGS

call :process_args %SCRIPT_CONF_ARGS% %%*

set _JAVA_OPTS=!_JAVA_OPTS! !_JAVA_PARAMS!

if defined CUSTOM_MAIN_CLASS (
    set MAIN_CLASS=!CUSTOM_MAIN_CLASS!
) else (
    set MAIN_CLASS=!APP_MAIN_CLASS!
)

rem Call the application and pass all arguments unchanged.
"%_JAVACMD%" !_JAVA_OPTS! !PARALLEL_T_SNE_OPTS! -cp "%APP_CLASSPATH%" %MAIN_CLASS% !_APP_ARGS!

@endlocal

exit /B %ERRORLEVEL%


rem Loads a configuration file full of default command line options for this script.
rem First argument is the path to the config file.
rem Second argument is the name of the environment variable to write to.
:parse_config
  set _PARSE_FILE=%~1
  set _PARSE_OUT=
  if exist "%_PARSE_FILE%" (
    FOR /F "tokens=* eol=# usebackq delims=" %%i IN ("%_PARSE_FILE%") DO (
      set _PARSE_OUT=!_PARSE_OUT! %%i
    )
  )
  set %2=!_PARSE_OUT!
exit /B 0


:add_java
  set _JAVA_PARAMS=!_JAVA_PARAMS! %*
exit /B 0


:add_app
  set _APP_ARGS=!_APP_ARGS! %*
exit /B 0


rem Processes incoming arguments and places them in appropriate global variables
:process_args
  :param_loop
  call set _PARAM1=%%1
  set "_TEST_PARAM=%~1"

  if ["!_PARAM1!"]==[""] goto param_afterloop


  rem ignore arguments that do not start with '-'
  if "%_TEST_PARAM:~0,1%"=="-" goto param_java_check
  set _APP_ARGS=!_APP_ARGS! !_PARAM1!
  shift
  goto param_loop

  :param_java_check
  if "!_TEST_PARAM:~0,2!"=="-J" (
    rem strip -J prefix
    set _JAVA_PARAMS=!_JAVA_PARAMS! !_TEST_PARAM:~2!
    shift
    goto param_loop
  )

  if "!_TEST_PARAM:~0,2!"=="-D" (
    rem test if this was double-quoted property "-Dprop=42"
    for /F "delims== tokens=1,*" %%G in ("!_TEST_PARAM!") DO (
      if not ["%%H"] == [""] (
        set _JAVA_PARAMS=!_JAVA_PARAMS! !_PARAM1!
      ) else if [%2] neq [] (
        rem it was a normal property: -Dprop=42 or -Drop="42"
        call set _PARAM1=%%1=%%2
        set _JAVA_PARAMS=!_JAVA_PARAMS! !_PARAM1!
        shift
      )
    )
  ) else (
    if "!_TEST_PARAM!"=="-main" (
      call set CUSTOM_MAIN_CLASS=%%2
      shift
    ) else (
      set _APP_ARGS=!_APP_ARGS! !_PARAM1!
    )
  )
  shift
  goto param_loop
  :param_afterloop

exit /B 0
