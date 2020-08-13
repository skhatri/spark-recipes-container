FROM gradle:jdk11 as builder
COPY --chown=gradle:gradle . /home/gradle/container
WORKDIR /home/gradle/container
RUN gradle export --no-daemon


FROM kubesailmaker/microservices-java:ubi-java11-1.4
RUN mkdir -p /opt/app/tools && mkdir -p /opt/app/source && mkdir -p /opt/app/libs && mkdir -p /opt/app/out && mkdir -p /opt/app/create

ADD https://downloads.lightbend.com/scala/2.12.11/scala-2.12.11.tgz /opt/app/tools
COPY --from=builder /home/gradle/container/examples/libs/ /opt/app/libs
COPY --from=builder /home/gradle/container/examples/compiler.sh /opt/app/compiler.sh
COPY --from=builder /home/gradle/container/entrypoint.sh /opt/app/entrypoint.sh
COPY --from=builder /home/gradle/container/examples/source /opt/app/source

COPY --from=builder /home/gradle/container/gradle.properties /opt/app/create/gradle.properties
COPY --from=builder /home/gradle/container/build.gradle.kts /opt/app/create/build.gradle.kts
COPY --from=builder /home/gradle/container/examples/source /opt/app/create/src/main/scala
COPY --from=builder /home/gradle/container/gradle /opt/app/create/gradle
COPY --from=builder /home/gradle/container/README.md /opt/app/create/README.md
COPY --from=builder /home/gradle/container/gradlew /opt/app/create/gradlew
COPY --from=builder /home/gradle/container/gradlew.bat /opt/app/create/gradlew.bat

ENV PATH /opt/app/tools/scala-2.12.11/bin:$PATH
ENV SLEEP 0
WORKDIR /opt/app
USER root
RUN chmod a+x /opt/app/*.sh && chmod a+x /opt/app/create/gradlew* && tar zxf /opt/app/tools/scala-2.12.11.tgz -C /opt/app/tools && rm /opt/app/tools/scala-2.12.11.tgz
USER app

ENTRYPOINT ["/opt/app/entrypoint.sh"]
CMD ["run", "demo.Hello"]


