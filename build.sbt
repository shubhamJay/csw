import Dependencies._

lazy val csw = project
  .in(file("."))
  .enablePlugins(UnidocSite, PublishGithub, PublishBintray)
  .aggregate(`csw-location`)
  .settings(Settings.mergeSiteWith(docs))

lazy val `csw-location` = project
  .enablePlugins(Coverage, PublishBintray)
  .settings(
    libraryDependencies ++= Seq(
      `akka-stream`,
      `jmdns`,
      `scala-java8-compat`,
      `akka-remote`,
      `scala-async`
    ),
    libraryDependencies ++= Seq(
      `akka-stream-testkit` % Test,
      `scalatest` % Test,
      `scalamock-scalatest-support` % Test
    )
  )

lazy val integration = project
  .enablePlugins(NoPublish)
  .dependsOn(`csw-location` % "compile->compile;test->test")

lazy val docs = project
  .enablePlugins(ParadoxSite, NoPublish)
