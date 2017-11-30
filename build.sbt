organization in ThisBuild := "com.example"
version in ThisBuild := "1.0-SNAPSHOT"

// the Scala version that will be used for cross-compiled libraries
scalaVersion in ThisBuild := "2.11.8"

val macwire = "com.softwaremill.macwire" %% "macros" % "2.2.5" % "provided"
val scalaTest = "org.scalatest" %% "scalatest" % "3.0.1" % Test
val bcrypt =  "com.github.t3hnar" %% "scala-bcrypt" % "3.0"

lazy val `password-cop` = (project in file("."))
  .aggregate(`password-cop-api`, `password-cop-impl`, `password-cop-stream-api`, `password-cop-stream-impl`)

lazy val `password-cop-api` = (project in file("password-cop-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi
    )
  )

lazy val `password-cop-impl` = (project in file("password-cop-impl"))
  .enablePlugins(LagomScala)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslPersistenceCassandra,
      lagomScaladslKafkaBroker,
      lagomScaladslTestKit,
      macwire,
      bcrypt,
      scalaTest
    )
  )
  .settings(lagomForkedTestSettings: _*)
  .dependsOn(`password-cop-api`)

lazy val `password-cop-stream-api` = (project in file("password-cop-stream-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi
    )
  )

lazy val `password-cop-stream-impl` = (project in file("password-cop-stream-impl"))
  .enablePlugins(LagomScala)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslTestKit,
      macwire,
      scalaTest
    )
  )
  .dependsOn(`password-cop-stream-api`, `password-cop-api`)
