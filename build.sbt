name := "abc-site-play"

version := "0.2"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  "com.foomoo.abc" %% "abc-domain" % "0.3",
  "com.foomoo.abc" %% "abc-parser" % "0.3",
  "com.foomoo.abc" %% "abc-app" % "0.3",
  "com.foomoo.string-store" % "string-store-service-api" % "0.2",
  "com.foomoo.string-store" % "string-store-service-provider-mongo" % "0.2",
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test,
  "commons-cli" % "commons-cli" % "1.2"
)

sources in (Compile, doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false

