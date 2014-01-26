name := "iStoreApp"

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  jdbc,
  anorm,
  cache,
  "org.reactivecouchbase" %% "reactivecouchbase-play" % "0.2-SNAPSHOT"
)

resolvers += "ReactiveCouchbase repository" at "https://raw.github.com/ReactiveCouchbase/repository/master/snapshots"

play.Project.playScalaSettings
