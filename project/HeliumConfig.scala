import sbt._
import laika.ast.LengthUnit
import laika.ast.Styles
import laika.theme.config._
import laika.helium.Helium
import laika.helium.config.IconLink
import laika.helium.config.HeliumIcon
import laika.helium.config.ColorQuintet
import laika.helium.config.AnchorPlacement
import laika.helium.Helium

import LengthUnit._

object HeliumConfig {

  def customize(helium: Helium): Helium = {

    // tailwindcss colors - MIT License
    val `sky-800` = Color.hex("075985")
    val `sky-600` = Color.hex("0284c7")
    val `sky-400` = Color.hex("38bdf8")
    val `fuchsia-800` = Color.hex("86198f")
    val `fuchsia-700` = Color.hex("a21caf")
    val `fuchsia-600` = Color.hex("c026d3")
    val `fuchsia-300` = Color.hex("f0abfc")
    val `fuchsia-100` = Color.hex("fae8ff")
    val `pink-500` = Color.hex("ec4899")
    val `pink-800` = Color.hex("9d174d")
    val `teal-600` = Color.hex("0d9488")
    val `teal-700` = Color.hex("0f766e")
    val `teal-800` = Color.hex("115e59")
    val `gray-50` = Color.hex("f9fafb")
    val `gray-100` = Color.hex("f3f4f6")
    val `gray-200` = Color.hex("e5e7eb")
    val `gray-300` = Color.hex("d1d5db")
    val `gray-400` = Color.hex("9ca3af")
    val `gray-500` = Color.hex("6b7280")
    val `gray-600` = Color.hex("4b5563")
    val `gray-700` = Color.hex("374151")
    val `red-600` = Color.hex("dc2626")
    val `red-400` = Color.hex("f87171")
    val `orange-600` = Color.hex("ea580c")
    val `orange-400` = Color.hex("fb923c")
    val `yellow-600` = Color.hex("ca8a04")

    helium.all
      .fontResources(
        FontDefinition(
          Font.webCSS("https://fonts.googleapis.com/css?family=Lato:300"),
          "Lato",
          FontWeight.Normal,
          FontStyle.Normal
        ),
        FontDefinition(
          Font.webCSS(
            "https://cdn.jsdelivr.net/npm/firacode@6.2.0/distr/fira_code.css"
          ),
          "Fira Code",
          FontWeight.Normal,
          FontStyle.Normal
        )
      )
      .all
      .fontFamilies(
        "Lato",
        "Lato",
        "Fira Code"
      )
      .all
      .fontSizes(
        body = px(18),
        code = px(14),
        title = px(34),
        header2 = px(30),
        header3 = px(22),
        header4 = px(18),
        small = px(12)
      )
      .all
      .themeColors(
        primary = `sky-800`,
        secondary = `pink-800`,
        primaryMedium = `gray-200`,
        primaryLight = `gray-100`,
        text = `gray-700`,
        background = `gray-50`,
        bgGradient = (`sky-800`, `sky-400`)
      )
      .all
      .messageColors(
        info = `sky-600`,
        infoLight = `sky-400`,
        warning = `orange-600`,
        warningLight = `orange-400`,
        error = `red-600`,
        errorLight = `red-400`
      )
      .all
      .syntaxHighlightingColors(
        base = ColorQuintet(
          `gray-100`, // background
          `gray-400`, // comments
          `gray-300`, // ???
          `fuchsia-800`, // variables
          `gray-600` // parens, braces, brackets, commas, etc.
        ),
        wheel = ColorQuintet(
          `pink-500`, // string interpolation
          `sky-800`, // language keywords
          `teal-600`, // method names
          `yellow-600`, // literals: numbers, strings, etc.
          `sky-600` // class, type, object names
        )
      )
      .site
      .layout(
        contentWidth = px(860),
        navigationWidth = px(250), // default = px(275),
        topBarHeight = px(35),
        defaultBlockSpacing = px(10),
        defaultLineHeight = 1.5,
        anchorPlacement = AnchorPlacement.Left
      )
      .site
      .autoLinkJS() // Actually, this *disables* auto-linking, to avoid duplicates with mdoc
      .site
      .topNavigationBar(navLinks =
        List(
          IconLink.external(
            "https://github.com/buntec/ff4s",
            HeliumIcon.github,
            options = Styles("svg-link")
          )
        )
      )
  }

}
