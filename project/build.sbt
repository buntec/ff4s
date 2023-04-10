lazy val scalaDomTypesVersion = "17.0.0"

lazy val root = (project in file("."))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](
      BuildInfoKey.action("scalaDomTypesVersion")(scalaDomTypesVersion)
    ),
    buildInfoPackage := "metaProject",
    // Compile-time dependencies
    libraryDependencies ++= Seq(
      "com.raquo" %% "domtypes" % scalaDomTypesVersion
    )
  )
