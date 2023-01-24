#FROM openjdk:11-alpine
#
#ENV SCALA_VERSION 2.13.4
#ENV SCALA_HOME /usr/local/scala
#
#RUN wget "https://downloads.lightbend.com/scala/${SCALA_VERSION}/scala-${SCALA_VERSION}.tgz" && \
#    tar xzf "scala-${SCALA_VERSION}.tgz" -C /usr/local && \
#    rm "scala-${SCALA_VERSION}.tgz" && \
#    ln -s /usr/local/scala-${SCALA_VERSION} ${SCALA_HOME} && \
#    ln -s ${SCALA_HOME}/bin/scala /usr/local/bin/scala && \
#    ln -s ${SCALA_HOME}/bin/scalac /usr/local/bin/scalac && \
#    ln -s ${SCALA_HOME}/bin/scaladoc /usr/local/bin/scaladoc && \
#    ln -s ${SCALA_HOME}/bin/scalap /usr/local/bin/scalap
#
#ENV SPARK_VERSION 3.3.1
#ENV HADOOP_VERSION 3.2
#
#RUN apk add --no-cache wget && \
#    wget "https://downloads.apache.org/spark/spark-${SPARK_VERSION}/spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz" && \
#    tar -xzf "spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz" && \
#    mv "spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}" /usr/local/spark && \
#    rm "spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz" && \
#    apk del wget
#
#ENV SPARK_HOME /usr/local/spark
#ENV PATH $PATH:$SPARK_HOME/bin

#######

#FROM openjdk:11-alpine
#
#RUN apk update && apk add bash
#
#ENV SPARK_VERSION 3.3.1
#ENV HADOOP_VERSION 2.7
#
#RUN apk --no-cache add curl && \
#    curl -sL https://www.apache.org/dist/spark/spark-$SPARK_VERSION/spark-$SPARK_VERSION-bin-hadoop$HADOOP_VERSION.tgz | tar xz -C /tmp/ && \
#    mv /tmp/spark-$SPARK_VERSION-bin-hadoop$HADOOP_VERSION /spark
#
#ENV SPARK_HOME /spark
#ENV PATH $PATH:$SPARK_HOME/bin
#
#COPY . /app
#
#CMD ["spark-submit", "--class", "com.example.Main", "/app/target/scala-2.11/example-assembly-1.0.jar"]


## builder step used to download and configure spark environment
#FROM openjdk:11.0.11-jre-slim-buster as builder
#RUN apt-get update && apt-get install -y bash wget
#
#ENV SPARK_VERSION 3.3.1
#ENV SCALA_VERSION 2.13.10
#
#
##RUN apt-get update && apt-get install -y curl && \
##    curl -sL https://www.apache.org/dist/spark/spark-$SPARK_VERSION/spark-$SPARK_VERSION-bin-hadoop2.7.tgz | tar xz -C /tmp/ && \
##    mv /tmp/spark-$SPARK_VERSION-bin-hadoop2.7 /spark
#
#RUN apt-get update && apt-get install -y bash curl&& \
#  curl -fsL http://downloads.typesafe.com/scala/$SCALA_VERSION/scala-$SCALA_VERSION.tgz | tar xfz - -C /usr/local && \
#  ln -s /usr/local/scala-$SCALA_VERSION/bin/* /usr/local/bin/ && \
#  scala -version && \
#  scalac -version
#
#RUN wget --no-verbose -O apache-spark.tgz "https://archive.apache.org/dist/spark/spark-${SPARK_VERSION}/spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz" \
#    && mkdir -p /opt/spark \
#    && tar -xf apache-spark.tgz -C /opt/spark --strip-components=1 \
#    && rm apache-spark.tgz
#
#
#
#ENV SPARK_HOME /spark
#ENV PATH $PATH:$SPARK_HOME/bin
#
#COPY . /app
#
#CMD ["spark-submit", "--class", "com.parallel_t-SNE.Main", "/app/target/scala-2.13/parallel_t-sne_2.13-0.1.0-SNAPSHOT.jar"]
#FROM openjdk:11-alpine
#
## Add Dependencies for PySpark
#RUN apt-get update && apt-get install -y curl vim wget software-properties-common ssh net-tools ca-certificates python3 python3-pip python3-numpy python3-matplotlib python3-scipy python3-pandas python3-simpy
#
#RUN update-alternatives --install "/usr/bin/python" "python" "$(which python3)" 1

# Fix the value of PYTHONHASHSEED
# Note: this is needed when you use Python 3.3 or greater
#ENV SPARK_VERSION=3.0.2 \
#HADOOP_VERSION=3.2 \
#SPARK_HOME=/opt/spark \
#PYTHONHASHSEED=1
#
#ARG SPARK_VERSION
##ARG HADOOP_VERSION
#ARG SCALA_VERSION
#ARG SBT_VERSION
#ARG SCALA_VERSION
#
#ENV SPARK_VERSION=${SPARK_VERSION:-3.3.1}
##ENV HADOOP_VERSION=${HADOOP_VERSION:-2.7}
#ENV SCALA_VERSION ${SCALA_VERSION:-2.13.10}
#ENV SBT_VERSION ${SBT_VERSION:-1.8}

#RUN \
#  echo "$SCALA_VERSION $SBT_VERSION" && \
#  mkdir -p /usr/lib/jvm/java-1.8-openjdk/jre && \
#  touch /usr/lib/jvm/java-1.8-openjdk/jre/release && \
##  apt-get add -U --no-cache bash curl  \
#  apt-get update && apt-get install -y bash curl&& \
#  curl -fsL http://downloads.typesafe.com/scala/$SCALA_VERSION/scala-$SCALA_VERSION.tgz | tar xfz - -C /usr/local && \
#  ln -s /usr/local/scala-$SCALA_VERSION/bin/* /usr/local/bin/ && \
#  scala -version && \
#  scalac -version
#
#RUN \
#  curl -fsL https://github.com/sbt/sbt/releases/download/v$SBT_VERSION/sbt-$SBT_VERSION.tgz | tar xfz - -C /usr/local && \
#  $(mv /usr/local/sbt-launcher-packaging-$SBT_VERSION /usr/local/sbt || true) \
#  ln -s /usr/local/sbt/bin/* /usr/local/bin/ && \
#  sbt sbt-version || sbt sbtVersion || true
#
#
## Download and uncompress spark from the apache archive
#RUN wget --no-verbose -O apache-spark.tgz "https://archive.apache.org/dist/spark/spark-${SPARK_VERSION}/spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz" \
#&& mkdir -p /opt/spark \
#&& tar -xf apache-spark.tgz -C /opt/spark --strip-components=1 \
#&& rm apache-spark.tgz
#
#
## Apache spark environment
#FROM builder as apache-spark
#
#WORKDIR /opt/spark
#
#ENV SPARK_MASTER_PORT=7077 \
#SPARK_MASTER_WEBUI_PORT=8080 \
#SPARK_LOG_DIR=/opt/spark/logs \
#SPARK_MASTER_LOG=/opt/spark/logs/spark-master.out \
#SPARK_WORKER_LOG=/opt/spark/logs/spark-worker.out \
#SPARK_WORKER_WEBUI_PORT=8080 \
#SPARK_WORKER_PORT=7000 \
#SPARK_MASTER="spark://spark-master:7077" \
#SPARK_WORKLOAD="master"
#
#EXPOSE 8080 7077 6066
#
#RUN mkdir -p $SPARK_LOG_DIR && \
#touch $SPARK_MASTER_LOG && \
#touch $SPARK_WORKER_LOG && \
#ln -sf /dev/stdout $SPARK_MASTER_LOG && \
#ln -sf /dev/stdout $SPARK_WORKER_LOG
#
#COPY old/start-spark.sh /
#
#CMD ["/bin/bash", "/start-spark.sh"]