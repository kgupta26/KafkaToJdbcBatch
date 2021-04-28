resolvers += Resolver.typesafeRepo("releases")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.7.5")
resolvers += "Artima Maven Repository" at "http://repo.artima.com/releases"
addSbtPlugin("com.artima.supersafe" % "sbtplugin" % "1.1.10")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.3")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.1")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.10")
addSbtPlugin("com.julianpeeters" % "sbt-avrohugger" % "2.0.0-RC22" excludeAll "io.spray")
addSbtPlugin("com.github.gseitz" % "sbt-protobuf" % "0.6.5")

//addSbtPlugin("io.kamon" % "sbt-kanela-runner" % "2.0.6") todo find a akka monitoring tool
libraryDependencies += "io.spray" %% "spray-json" % "1.3.5"