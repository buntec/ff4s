import com.raquo.domtypes.codegen.DefType.LazyVal
import com.raquo.domtypes.codegen.{
  CanonicalCache,
  CanonicalDefGroups,
  CanonicalGenerator,
  CodeFormatting,
  SourceRepr
}
import com.raquo.domtypes.common.{HtmlTagType, SvgTagType}
import com.raquo.domtypes.defs.styles.StyleTraitDefs

import cats.effect.IO
import cats.syntax.all._

import java.io.File

class CustomGenerator(srcManaged: File)
    extends CanonicalGenerator(
      baseOutputDirectoryPath = srcManaged.getPath,
      basePackagePath = "ff4s",
      standardTraitCommentLines = List(
        "#NOTE: GENERATED CODE",
        s" - This file is generated at compile time from the data in Scala DOM Types",
        " - See `project/DomDefsGenerator.scala` for code generation params",
        " - Contribute to https://github.com/raquo/scala-dom-types to add missing tags / attrs / props / etc."
      ),
      format = CodeFormatting()
    ) {

  override def settersPackagePath: String =
    basePackagePath + ".modifiers.KeySetter"

  override def scalaJsElementTypeParam: String = "Ref"

  override def defsPackagePath: String = basePackagePath

  override def tagDefsPackagePath: String = defsPackagePath

  override def attrDefsPackagePath: String = defsPackagePath

  override def propDefsPackagePath: String = defsPackagePath

  override def eventPropDefsPackagePath: String = defsPackagePath

  override def stylePropDefsPackagePath: String = defsPackagePath

  override def keysPackagePath: String = basePackagePath

  override def tagKeysPackagePath: String = basePackagePath

}
