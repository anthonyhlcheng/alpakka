lazy val alpakka = project
  .in(file("."))
  .enablePlugins(ScalaUnidocPlugin)
  .disablePlugins(MimaPlugin, SitePlugin)
  .aggregate(
    amqp,
    avroparquet,
    awslambda,
    azureStorageQueue,
    cassandra,
    couchbase,
    csv,
    dynamodb,
    elasticsearch,
    eventbridge,
    files,
    ftp,
    geode,
    googleCommon,
    googleCloudBigQuery,
    googleCloudBigQueryStorage,
    googleCloudPubSub,
    googleCloudPubSubGrpc,
    googleCloudStorage,
    googleFcm,
    hbase,
    hdfs,
    huaweiPushKit,
    influxdb,
    ironmq,
    jms,
    jsonStreaming,
    kinesis,
    kudu,
    mongodb,
    mqtt,
    mqttStreaming,
    orientdb,
    pravega,
    reference,
    s3,
    springWeb,
    simpleCodecs,
    slick,
    sns,
    solr,
    sqs,
    sse,
    text,
    udp,
    unixdomainsocket,
    xml
  )
  .aggregate(`doc-examples`)
  .settings(
    onLoadMessage :=
      """
        |** Welcome to the sbt build definition for Alpakka! **
        |
        |Useful sbt tasks:
        |
        |  docs/previewSite - builds Paradox and Scaladoc documentation,
        |    starts a webserver and opens a new browser window
        |
        |  test - runs all the tests for all of the connectors.
        |    Make sure to run `docker-compose up` first.
        |
        |  mqtt/testOnly *.MqttSourceSpec - runs a single test
        |
        |  mimaReportBinaryIssues - checks whether this current API
        |    is binary compatible with the released version
      """.stripMargin,
    // unidoc combines sources and jars from all connectors and that
    // might include some incompatible ones. Depending on the
    // classpath order that might lead to scaladoc compilation errors.
    // Therefore some versions are excluded here.
    ScalaUnidoc / unidoc / fullClasspath := {
      (ScalaUnidoc / unidoc / fullClasspath).value
        .filterNot(_.data.getAbsolutePath.contains("protobuf-java-2.5.0.jar"))
        .filterNot(_.data.getAbsolutePath.contains("guava-28.1-android.jar"))
        .filterNot(_.data.getAbsolutePath.contains("commons-net-3.1.jar"))
        .filterNot(_.data.getAbsolutePath.contains("protobuf-java-2.6.1.jar"))
    },
    ScalaUnidoc / unidoc / unidocProjectFilter := inAnyProject
      -- inProjects(
        `doc-examples`,
        csvBench,
        mqttStreamingBench,
        // googleCloudPubSubGrpc and googleCloudBigQueryStorage contain the same gRPC generated classes
        // don't include ScalaDocs for googleCloudBigQueryStorage to make it work
        googleCloudBigQueryStorage,
        // springWeb triggers an esoteric ScalaDoc bug (from Java code)
        springWeb
      ),
    crossScalaVersions := List() // workaround for https://github.com/sbt/sbt/issues/3465
  )

TaskKey[Unit]("verifyCodeFmt") := {
  javafmtCheckAll.all(ScopeFilter(inAnyProject)).result.value.toEither.left.foreach { _ =>
    throw new MessageOnlyException(
      "Unformatted Java code found. Please run 'javafmtAll' and commit the reformatted code"
    )
  }
  scalafmtCheckAll.all(ScopeFilter(inAnyProject)).result.value.toEither.left.foreach { _ =>
    throw new MessageOnlyException(
      "Unformatted Scala code found. Please run 'scalafmtAll' and commit the reformatted code"
    )
  }
  (Compile / scalafmtSbtCheck).result.value.toEither.left.foreach { _ =>
    throw new MessageOnlyException(
      "Unformatted sbt code found. Please run 'scalafmtSbt' and commit the reformatted code"
    )
  }
}

addCommandAlias("verifyCodeStyle", "headerCheck; verifyCodeFmt")

lazy val amqp = alpakkaProject("amqp", "amqp", Dependencies.Amqp)

lazy val avroparquet =
  alpakkaProject("avroparquet", "avroparquet", Dependencies.AvroParquet)

lazy val awslambda = alpakkaProject("awslambda", "aws.lambda", Dependencies.AwsLambda)

lazy val azureStorageQueue = alpakkaProject(
  "azure-storage-queue",
  "azure.storagequeue",
  Dependencies.AzureStorageQueue
)

lazy val cassandra =
  alpakkaProject("cassandra", "cassandra", Dependencies.Cassandra)

lazy val couchbase =
  alpakkaProject("couchbase", "couchbase", Dependencies.Couchbase)

lazy val csv = alpakkaProject("csv", "csv")

lazy val csvBench = internalProject("csv-bench")
  .dependsOn(csv)
  .enablePlugins(JmhPlugin)

lazy val dynamodb = alpakkaProject("dynamodb", "aws.dynamodb", Dependencies.DynamoDB)

lazy val elasticsearch = alpakkaProject(
  "elasticsearch",
  "elasticsearch",
  Dependencies.Elasticsearch
)

// The name 'file' is taken by `sbt.file`, hence 'files'
lazy val files = alpakkaProject("file", "file", Dependencies.File)

lazy val ftp = alpakkaProject(
  "ftp",
  "ftp",
  Dependencies.Ftp,
  Test / fork := true,
  // To avoid potential blocking in machines with low entropy (default is `/dev/random`)
  Test / javaOptions += "-Djava.security.egd=file:/dev/./urandom"
)

lazy val geode =
  alpakkaProject(
    "geode",
    "geode",
    Dependencies.Geode,
    Test / fork := true,
    // https://github.com/scala/bug/issues/12072
    Test / scalacOptions += "-Xlint:-byname-implicit"
  )

lazy val googleCommon = alpakkaProject(
  "google-common",
  "google.common",
  Dependencies.GoogleCommon,
  Test / fork := true
)

lazy val googleCloudBigQuery = alpakkaProject(
  "google-cloud-bigquery",
  "google.cloud.bigquery",
  Dependencies.GoogleBigQuery,
  Test / fork := true,
  Compile / scalacOptions += "-Wconf:src=src_managed/.+:s"
).dependsOn(googleCommon).enablePlugins(spray.boilerplate.BoilerplatePlugin)

lazy val googleCloudBigQueryStorage = alpakkaProject(
  "google-cloud-bigquery-storage",
  "google.cloud.bigquery.storage",
  Dependencies.GoogleBigQueryStorage,
  akkaGrpcCodeGeneratorSettings ~= { _.filterNot(_ == "flat_package") },
  akkaGrpcCodeGeneratorSettings += "server_power_apis",
  akkaGrpcGeneratedSources := Seq(AkkaGrpc.Client),
  Test / akkaGrpcGeneratedSources := Seq(AkkaGrpc.Server),
  akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Scala, AkkaGrpc.Java),
  Compile / scalacOptions ++= Seq(
      "-Wconf:src=.+/akka-grpc/main/.+:s",
      "-Wconf:src=.+/akka-grpc/test/.+:s"
    ),
  compile / javacOptions := (compile / javacOptions).value.filterNot(_ == "-Xlint:deprecation")
).dependsOn(googleCommon).enablePlugins(AkkaGrpcPlugin)

lazy val googleCloudPubSub = alpakkaProject(
  "google-cloud-pub-sub",
  "google.cloud.pubsub",
  Dependencies.GooglePubSub,
  Test / fork := true,
  // See docker-compose.yml gcloud-pubsub-emulator_prep
  Test / envVars := Map("PUBSUB_EMULATOR_HOST" -> "localhost", "PUBSUB_EMULATOR_PORT" -> "8538")
).dependsOn(googleCommon)

lazy val googleCloudPubSubGrpc = alpakkaProject(
  "google-cloud-pub-sub-grpc",
  "google.cloud.pubsub.grpc",
  Dependencies.GooglePubSubGrpc,
  akkaGrpcCodeGeneratorSettings ~= { _.filterNot(_ == "flat_package") },
  akkaGrpcGeneratedSources := Seq(AkkaGrpc.Client),
  akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Scala, AkkaGrpc.Java),
  Compile / PB.protoSources += (Compile / PB.externalIncludePath).value,
  // for the ExampleApp in the tests
  run / connectInput := true,
  Compile / scalacOptions ++= Seq(
      "-Wconf:src=.+/akka-grpc/main/.+:s",
      "-Wconf:src=.+/akka-grpc/test/.+:s"
    ),
  compile / javacOptions := (compile / javacOptions).value.filterNot(_ == "-Xlint:deprecation")
).enablePlugins(AkkaGrpcPlugin).dependsOn(googleCommon)

lazy val googleCloudStorage = alpakkaProject(
  "google-cloud-storage",
  "google.cloud.storage",
  Test / fork := true,
  Dependencies.GoogleStorage
).dependsOn(googleCommon)

lazy val googleFcm = alpakkaProject("google-fcm", "google.firebase.fcm", Dependencies.GoogleFcm, Test / fork := true)
  .dependsOn(googleCommon)

lazy val hbase = alpakkaProject("hbase", "hbase", Dependencies.HBase, Test / fork := true)

lazy val hdfs = alpakkaProject("hdfs", "hdfs", Dependencies.Hdfs)

lazy val huaweiPushKit =
  alpakkaProject("huawei-push-kit", "huawei.pushkit", Dependencies.HuaweiPushKit)

lazy val influxdb = alpakkaProject(
  "influxdb",
  "influxdb",
  Dependencies.InfluxDB,
  Compile / scalacOptions ++= Seq(
      // JDK 11: method isAccessible in class AccessibleObject is deprecated
      "-Wconf:cat=deprecation:s"
    )
)

lazy val ironmq = alpakkaProject(
  "ironmq",
  "ironmq",
  Dependencies.IronMq,
  Test / fork := true
)

lazy val jms = alpakkaProject("jms", "jms", Dependencies.Jms)

lazy val jsonStreaming = alpakkaProject("json-streaming", "json.streaming", Dependencies.JsonStreaming)

lazy val kinesis = alpakkaProject("kinesis", "aws.kinesis", Dependencies.Kinesis)

lazy val kudu = alpakkaProject("kudu", "kudu", Dependencies.Kudu)

lazy val mongodb = alpakkaProject("mongodb", "mongodb", Dependencies.MongoDb)

lazy val mqtt = alpakkaProject("mqtt", "mqtt", Dependencies.Mqtt)

lazy val mqttStreaming =
  alpakkaProject("mqtt-streaming", "mqttStreaming", Dependencies.MqttStreaming)
lazy val mqttStreamingBench = internalProject("mqtt-streaming-bench")
  .enablePlugins(JmhPlugin)
  .dependsOn(mqtt, mqttStreaming)

lazy val orientdb =
  alpakkaProject(
    "orientdb",
    "orientdb",
    Dependencies.OrientDB,
    Test / fork := true,
    // note: orientdb client needs to be refactored to move off deprecated calls
    fatalWarnings := false
  )

lazy val reference = internalProject("reference", Dependencies.Reference)
  .dependsOn(testkit % Test)

lazy val s3 = alpakkaProject("s3", "aws.s3", Dependencies.S3)

lazy val pravega = alpakkaProject(
  "pravega",
  "pravega",
  Dependencies.Pravega,
  Test / fork := true
)

lazy val springWeb = alpakkaProject(
  "spring-web",
  "spring.web",
  Dependencies.SpringWeb
)

lazy val simpleCodecs = alpakkaProject("simple-codecs", "simplecodecs")

lazy val slick = alpakkaProject("slick", "slick", Dependencies.Slick)

lazy val eventbridge =
  alpakkaProject("aws-event-bridge", "aws.eventbridge", Dependencies.Eventbridge)

lazy val sns = alpakkaProject("sns", "aws.sns", Dependencies.Sns)

lazy val solr = alpakkaProject("solr", "solr", Dependencies.Solr)

lazy val sqs = alpakkaProject("sqs", "aws.sqs", Dependencies.Sqs)

lazy val sse = alpakkaProject("sse", "sse", Dependencies.Sse)

lazy val text = alpakkaProject("text", "text")

lazy val udp = alpakkaProject("udp", "udp")

lazy val unixdomainsocket =
  alpakkaProject("unix-domain-socket", "unixdomainsocket", Dependencies.UnixDomainSocket)

lazy val xml = alpakkaProject("xml", "xml", Dependencies.Xml)

lazy val docs = project
  .enablePlugins(AkkaParadoxPlugin, ParadoxSitePlugin, PreprocessPlugin, PublishRsyncPlugin)
  .disablePlugins(MimaPlugin)
  .settings(
    Compile / paradox / name := "Alpakka",
    publish / skip := true,
    makeSite := makeSite.dependsOn(LocalRootProject / ScalaUnidoc / doc).value,
    previewPath := (Paradox / siteSubdirName).value,
    Preprocess / siteSubdirName := s"api/alpakka/${projectInfoVersion.value}",
    Preprocess / sourceDirectory := (LocalRootProject / ScalaUnidoc / unidoc / target).value,
    Preprocess / preprocessRules := Seq(
        ("http://www\\.eclipse\\.org/".r, _ => "https://www\\.eclipse\\.org/"),
        ("http://pravega\\.io/".r, _ => "https://pravega\\.io/"),
        ("http://www\\.scala-lang\\.org/".r, _ => "https://www\\.scala-lang\\.org/"),
        ("https://javadoc\\.io/page/".r, _ => "https://javadoc\\.io/static/"),
        // Add Java module name https://github.com/ThoughtWorksInc/sbt-api-mappings/issues/58
        ("https://docs\\.oracle\\.com/en/java/javase/11/docs/api/".r,
         _ => "https://docs\\.oracle\\.com/en/java/javase/11/docs/api/java.base/")
      ),
    Paradox / siteSubdirName := s"docs/alpakka/${projectInfoVersion.value}",
    paradoxProperties ++= Map(
        "akka.version" -> Dependencies.AkkaVersion,
        "akka-http.version" -> Dependencies.AkkaHttpVersion,
        "hadoop.version" -> Dependencies.HadoopVersion,
        "extref.github.base_url" -> s"https://github.com/akka/alpakka/tree/${if (isSnapshot.value) "master"
        else "v" + version.value}/%s",
        "extref.akka.base_url" -> s"https://doc.akka.io/docs/akka/${Dependencies.AkkaBinaryVersion}/%s",
        "scaladoc.akka.base_url" -> s"https://doc.akka.io/api/akka/${Dependencies.AkkaBinaryVersion}",
        "javadoc.akka.base_url" -> s"https://doc.akka.io/japi/akka/${Dependencies.AkkaBinaryVersion}/",
        "javadoc.akka.link_style" -> "direct",
        "extref.akka-http.base_url" -> s"https://doc.akka.io/docs/akka-http/${Dependencies.AkkaHttpBinaryVersion}/%s",
        "scaladoc.akka.http.base_url" -> s"https://doc.akka.io/api/akka-http/${Dependencies.AkkaHttpBinaryVersion}/",
        "javadoc.akka.http.base_url" -> s"https://doc.akka.io/japi/akka-http/${Dependencies.AkkaHttpBinaryVersion}/",
        // Akka gRPC
        "akka-grpc.version" -> Dependencies.AkkaGrpcBinaryVersion,
        "extref.akka-grpc.base_url" -> s"https://doc.akka.io/docs/akka-grpc/${Dependencies.AkkaGrpcBinaryVersion}/%s",
        // Couchbase
        "couchbase.version" -> Dependencies.CouchbaseVersion,
        "extref.couchbase.base_url" -> s"https://docs.couchbase.com/java-sdk/${Dependencies.CouchbaseVersionForDocs}/%s",
        // Java
        "extref.java-api.base_url" -> "https://docs.oracle.com/javase/8/docs/api/index.html?%s.html",
        "extref.geode.base_url" -> s"https://geode.apache.org/docs/guide/${Dependencies.GeodeVersionForDocs}/%s",
        "extref.javaee-api.base_url" -> "https://docs.oracle.com/javaee/7/api/index.html?%s.html",
        "extref.paho-api.base_url" -> "https://www.eclipse.org/paho/files/javadoc/index.html?%s.html",
        "extref.pravega.base_url" -> s"https://cncf.pravega.io/docs/${Dependencies.PravegaVersionForDocs}/%s",
        "extref.slick.base_url" -> s"https://scala-slick.org/doc/${Dependencies.SlickVersion}/%s",
        // Cassandra
        "extref.cassandra.base_url" -> s"https://cassandra.apache.org/doc/${Dependencies.CassandraVersionInDocs}/%s",
        "extref.cassandra-driver.base_url" -> s"https://docs.datastax.com/en/developer/java-driver/${Dependencies.CassandraDriverVersionInDocs}/%s",
        "javadoc.com.datastax.oss.base_url" -> s"https://docs.datastax.com/en/drivers/java/${Dependencies.CassandraDriverVersionInDocs}/",
        // Solr
        "extref.solr.base_url" -> s"https://lucene.apache.org/solr/guide/${Dependencies.SolrVersionForDocs}/%s",
        "javadoc.org.apache.solr.base_url" -> s"https://lucene.apache.org/solr/${Dependencies.SolrVersionForDocs}_0/solr-solrj/",
        // Java
        "javadoc.base_url" -> "https://docs.oracle.com/javase/8/docs/api/",
        "javadoc.javax.jms.base_url" -> "https://docs.oracle.com/javaee/7/api/",
        "javadoc.com.couchbase.base_url" -> s"https://docs.couchbase.com/sdk-api/couchbase-java-client-${Dependencies.CouchbaseVersion}/",
        "javadoc.io.pravega.base_url" -> s"http://pravega.io/docs/${Dependencies.PravegaVersionForDocs}/javadoc/clients/",
        "javadoc.org.apache.kudu.base_url" -> s"https://kudu.apache.org/releases/${Dependencies.KuduVersion}/apidocs/",
        "javadoc.org.apache.hadoop.base_url" -> s"https://hadoop.apache.org/docs/r${Dependencies.HadoopVersion}/api/",
        "javadoc.software.amazon.awssdk.base_url" -> "https://sdk.amazonaws.com/java/api/latest/",
        "javadoc.com.google.auth.base_url" -> "https://www.javadoc.io/doc/com.google.auth/google-auth-library-credentials/latest/",
        "javadoc.com.google.auth.link_style" -> "direct",
        "javadoc.com.fasterxml.jackson.annotation.base_url" -> "https://javadoc.io/doc/com.fasterxml.jackson.core/jackson-annotations/latest/",
        "javadoc.com.fasterxml.jackson.annotation.link_style" -> "direct",
        // Scala
        "scaladoc.spray.json.base_url" -> s"https://javadoc.io/doc/io.spray/spray-json_${scalaBinaryVersion.value}/latest/",
        // Eclipse Paho client for MQTT
        "javadoc.org.eclipse.paho.client.mqttv3.base_url" -> "https://www.eclipse.org/paho/files/javadoc/",
        "javadoc.org.bson.codecs.configuration.base_url" -> "https://mongodb.github.io/mongo-java-driver/3.7/javadoc/",
        "scaladoc.scala.base_url" -> s"https://www.scala-lang.org/api/${scalaBinaryVersion.value}.x/",
        "scaladoc.akka.stream.alpakka.base_url" -> s"/${(Preprocess / siteSubdirName).value}/",
        "javadoc.akka.stream.alpakka.base_url" -> ""
      ),
    paradoxGroups := Map("Language" -> Seq("Java", "Scala")),
    paradoxRoots := List("examples/elasticsearch-samples.html",
                         "examples/ftp-samples.html",
                         "examples/jms-samples.html",
                         "examples/mqtt-samples.html",
                         "index.html"),
    resolvers += Resolver.jcenterRepo,
    publishRsyncArtifacts += makeSite.value -> "www/",
    publishRsyncHost := "akkarepo@gustav.akka.io",
    apidocRootPackage := "akka"
  )

lazy val testkit = internalProject("testkit", Dependencies.testkit)

lazy val `doc-examples` = project
  .enablePlugins(AutomateHeaderPlugin)
  .disablePlugins(MimaPlugin, SitePlugin)
  .settings(
    name := s"akka-stream-alpakka-doc-examples",
    publish / skip := true,
    Dependencies.`Doc-examples`
  )

def alpakkaProject(projectId: String, moduleName: String, additionalSettings: sbt.Def.SettingsDefinition*): Project = {
  import com.typesafe.tools.mima.core.{Problem, ProblemFilters}
  Project(id = projectId, base = file(projectId))
    .enablePlugins(AutomateHeaderPlugin)
    .disablePlugins(SitePlugin)
    .settings(
      name := s"akka-stream-alpakka-$projectId",
      AutomaticModuleName.settings(s"akka.stream.alpakka.$moduleName"),
      mimaPreviousArtifacts := Set(
          organization.value %% name.value % previousStableVersion.value
            .getOrElse(throw new Error("Unable to determine previous version"))
        ),
      mimaBinaryIssueFilters += ProblemFilters.exclude[Problem]("*.impl.*"),
      Test / parallelExecution := false
    )
    .settings(additionalSettings: _*)
    .dependsOn(testkit % Test)
}

def internalProject(projectId: String, additionalSettings: sbt.Def.SettingsDefinition*): Project =
  Project(id = projectId, base = file(projectId))
    .enablePlugins(AutomateHeaderPlugin)
    .disablePlugins(SitePlugin, MimaPlugin)
    .settings(
      name := s"akka-stream-alpakka-$projectId",
      publish / skip := true
    )
    .settings(additionalSettings: _*)

Global / onLoad := (Global / onLoad).value.andThen { s =>
  val v = version.value
  if (dynverGitDescribeOutput.value.hasNoTags)
    throw new MessageOnlyException(
      s"Failed to derive version from git tags. Maybe run `git fetch --unshallow`? Derived version: $v"
    )
  s
}
