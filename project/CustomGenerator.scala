import com.raquo.domtypes.codegen.DefType.LazyVal
import com.raquo.domtypes.codegen.{
  CanonicalCache,
  CanonicalDefGroups,
  CanonicalGenerator,
  CodeFormatting,
  DefType,
  SourceRepr
}
import com.raquo.domtypes.codegen.generators.PropsTraitGenerator
import com.raquo.domtypes.common.{HtmlTagType, PropDef, SvgTagType}
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

  override def codecsImport: String = s"import $basePackagePath.codecs._"

  override def transformCodecName(codecName: String): String =
    codecName + "Codec"

  override def tagKeysPackagePath: String = basePackagePath

  // domtypes 19.0.0's CanonicalGenerator emits only one type parameter in
  // the shared property factory, while ff4s.HtmlProp keeps both the Scala and
  // DOM value types in its public API.
  override def generatePropsTrait(
      defGroups: List[(String, List[PropDef])],
      printDefGroupComments: Boolean,
      traitCommentLines: List[String],
      traitModifiers: List[String],
      traitName: String,
      keyKind: String,
      useDomVTypeParam: Boolean,
      implNameSuffix: String,
      baseImplDefComments: List[String],
      baseImplName: String,
      keyImplReflectedAttrNameArgName: Option[String],
      defType: DefType
  ): String = {
    val (defs, defGroupComments) =
      defsAndGroupComments(defGroups, printDefGroupComments)

    val baseImplDef = List(
      List(
        s"def ${baseImplName}[V, _DomV](",
        s"$keyImplNameArgName: String, ",
        keyImplReflectedAttrNameArgName
          .map(argName => argName + ": Option[String], ")
          .getOrElse(""),
        "codec: Codec[V, _DomV]",
        s"): ${keyKind}[V, _DomV] = ",
        s"${keyKind}(",
        keyImplNameArgName + ", ",
        keyImplReflectedAttrNameArgName
          .map(argName => argName + ", ")
          .getOrElse(""),
        "codec",
        ")"
      ).mkString
    )

    val headerLines = List(
      s"package $propDefsPackagePath",
      "",
      keyTypeImport(keyKind),
      codecsImport,
      ""
    ) ++ standardTraitCommentLines.map("// " + _)

    new PropsTraitGenerator(
      defs = defs,
      defGroupComments = defGroupComments,
      headerLines = headerLines,
      traitCommentLines = traitCommentLines,
      traitModifiers = traitModifiers,
      traitName = traitName,
      traitExtends = Nil,
      traitThisType = None,
      defType = _ => defType,
      keyKind = keyKind,
      useDomVTypeParam = useDomVTypeParam,
      keyImplName = prop => propImplName(prop.codec, implNameSuffix),
      keyImplNameArgName = keyImplNameArgName,
      keyImplReflectedAttrNameArgName = keyImplReflectedAttrNameArgName,
      baseImplDefComments = baseImplDefComments,
      baseImplName = baseImplName,
      baseImplDef = baseImplDef,
      transformCodecName = transformCodecName,
      outputImplDefs = true,
      format = format
    ).printTrait().getOutput()
  }

}
