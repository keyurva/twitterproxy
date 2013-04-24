name := "twitterproxy"

version := "0.1.0"

scalaVersion := "2.9.2"

seq(webSettings :_*)

resolvers += "Akka Repo" at "http://repo.akka.io/repository"

libraryDependencies ++= Seq(
  "org.scalatra" %% "scalatra" % "2.2.1",
  "net.databinder.dispatch" %% "dispatch-core" % "0.9.5",
  "oauth.signpost" % "signpost-core" % "1.2.1.2",
  "ch.qos.logback" % "logback-classic" % "1.0.6" % "runtime",
  "javax.servlet" % "javax.servlet-api" % "3.0.1" % "provided",
  "org.eclipse.jetty" % "jetty-webapp" % "8.1.8.v20121106" % "container;provided;test",
  "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container;provided;test" artifacts (Artifact("javax.servlet", "jar", "jar"))
)