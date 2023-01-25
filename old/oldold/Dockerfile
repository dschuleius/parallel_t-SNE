FROM openjdk:8-alpine

ARG SPARK_VERSION
#ARG HADOOP_VERSION
ARG SCALA_VERSION
ARG SBT_VERSION

ENV SPARK_VERSION=${SPARK_VERSION:-3.3.1}
#ENV HADOOP_VERSION=${HADOOP_VERSION:-2.7}
ENV SCALA_VERSION ${SCALA_VERSION:-2.13.10}
ENV SBT_VERSION ${SBT_VERSION:-1.8}


RUN apk --update add wget tar bash

#RUN \
#  echo "$SPARK_VERSION $HADOOP_VERSION" && \
#  echo http://apache.mirror.anlx.net/spark/spark-${SPARK_VERSION}/spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz && \
#  wget http://apache.mirror.anlx.net/spark/spark-${SPARK_VERSION}/spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz
#
#
#RUN tar -xzf spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz && \
#    mv spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION} /spark && \
#    rm spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz

RUN \
  echo "$SCALA_VERSION $SBT_VERSION" && \
  mkdir -p /usr/lib/jvm/java-1.8-openjdk/jre && \
  touch /usr/lib/jvm/java-1.8-openjdk/jre/release && \
  apk add -U --no-cache bash curl && \
  curl -fsL http://downloads.typesafe.com/scala/$SCALA_VERSION/scala-$SCALA_VERSION.tgz | tar xfz - -C /usr/local && \
  ln -s /usr/local/scala-$SCALA_VERSION/bin/* /usr/local/bin/ && \
  scala -version && \
  scalac -version

RUN \
  curl -fsL https://github.com/sbt/sbt/releases/download/v$SBT_VERSION/sbt-$SBT_VERSION.tgz | tar xfz - -C /usr/local && \
  $(mv /usr/local/sbt-launcher-packaging-$SBT_VERSION /usr/local/sbt || true) \
  ln -s /usr/local/sbt/bin/* /usr/local/bin/ && \
  sbt sbt-version || sbt sbtVersion || true