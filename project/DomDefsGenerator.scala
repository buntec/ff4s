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

object DomDefsGenerator {

  def generate(srcManaged: File): IO[List[File]] = {

    val generator = new CustomGenerator(srcManaged)
    val defGroups = new CanonicalDefGroups()

    def writeToFile(
        packagePath: String,
        fileName: String,
        fileContent: String
    ): IO[File] =
      IO {
        generator.writeToFile(
          packagePath = packagePath,
          fileName = fileName,
          fileContent = fileContent
        )
      }

    // -- HTML tags --

    val htmlTags = {
      val traitName = "HtmlTags"

      val fileContent = generator.generateTagsTrait(
        tagType = HtmlTagType,
        defGroups = defGroups.htmlTagsDefGroups,
        printDefGroupComments = true,
        traitCommentLines = Nil,
        traitModifiers = Nil,
        traitName = traitName,
        keyKind = "HtmlTag",
        baseImplDefComments = List(
          "Create HTML tag",
          "",
          "Note: this simply creates an instance of HtmlTag.",
          " - This does not create the element (to do that, call .apply() on the returned tag instance)",
          " - This does not register this tag name as a custom element",
          "   - See https://developer.mozilla.org/en-US/docs/Web/Web_Components/Using_custom_elements",
          "",
          "@param name - e.g. \"div\" or \"mwc-input\"",
          "",
          "@tparam Ref - type of elements with this tag, e.g. dom.html.Input for \"input\" tag"
        ),
        keyImplName = "htmlTag",
        defType = LazyVal
      )

      writeToFile(
        packagePath = generator.tagDefsPackagePath,
        fileName = traitName,
        fileContent = fileContent
      )
    }

    // -- SVG tags --

    val svgTags = {
      val traitName = "SvgTags"

      val fileContent = generator.generateTagsTrait(
        tagType = SvgTagType,
        defGroups = defGroups.svgTagsDefGroups,
        printDefGroupComments = false,
        traitCommentLines = Nil,
        traitModifiers = Nil,
        traitName = traitName,
        keyKind = "SvgTag",
        baseImplDefComments = List(
          "Create SVG tag",
          "",
          "Note: this simply creates an instance of HtmlTag.",
          " - This does not create the element (to do that, call .apply() on the returned tag instance)",
          "",
          "@param name - e.g. \"circle\"",
          "",
          "@tparam Ref    - type of elements with this tag, e.g. dom.svg.Circle for \"circle\" tag"
        ),
        keyImplName = "svgTag",
        defType = LazyVal
      )

      writeToFile(
        packagePath = generator.tagDefsPackagePath,
        fileName = traitName,
        fileContent = fileContent
      )
    }

    // -- HTML attributes --

    val htmlAttrs = {
      val traitName = "HtmlAttrs"

      val fileContent = generator.generateAttrsTrait(
        defGroups = defGroups.htmlAttrDefGroups,
        printDefGroupComments = false,
        traitCommentLines = Nil,
        traitModifiers = Nil,
        traitName = traitName,
        keyKind = "HtmlAttr",
        implNameSuffix = "HtmlAttr",
        baseImplDefComments = List(
          "Create HTML attribute (Note: for SVG attrs, use L.svg.svgAttr)",
          "",
          "@param name  - name of the attribute, e.g. \"value\"",
          "@param codec - used to encode V into String, e.g. StringAsIsCodec",
          "",
          "@tparam V    - value type for this attr in Scala"
        ),
        baseImplName = "htmlAttr",
        namespaceImports = Nil,
        namespaceImpl = _ => ???,
        transformAttrDomName = identity,
        defType = LazyVal
      )

      writeToFile(
        packagePath = generator.attrDefsPackagePath,
        fileName = traitName,
        fileContent = fileContent
      )
    }

    // -- SVG attributes --

    val svgAttrs = {
      val traitName = "SvgAttrs"

      val fileContent = generator.generateAttrsTrait(
        defGroups = defGroups.svgAttrDefGroups,
        printDefGroupComments = false,
        traitModifiers = Nil,
        traitName = traitName,
        traitCommentLines = Nil,
        keyKind = "SvgAttr",
        baseImplDefComments = List(
          "Create SVG attribute (Note: for HTML attrs, use L.htmlAttr)",
          "",
          "@param name  - name of the attribute, e.g. \"value\"",
          "@param codec - used to encode V into String, e.g. StringAsIsCodec",
          "",
          "@tparam V    - value type for this attr in Scala"
        ),
        implNameSuffix = "SvgAttr",
        baseImplName = "svgAttr",
        namespaceImports = Nil,
        namespaceImpl = SourceRepr(_),
        transformAttrDomName = identity,
        defType = LazyVal
      )

      writeToFile(
        packagePath = generator.attrDefsPackagePath,
        fileName = traitName,
        fileContent = fileContent
      )
    }

    // -- ARIA attributes --

    val ariaAttrs = {
      val traitName = "AriaAttrs"

      def transformAttrDomName(ariaAttrName: String): String = {
        if (ariaAttrName.startsWith("aria-")) {
          ariaAttrName.substring(5)
        } else {
          throw new Exception(
            s"Aria attribute does not start with `aria-`: $ariaAttrName"
          )
        }
      }

      val fileContent = generator.generateAttrsTrait(
        defGroups = defGroups.ariaAttrDefGroups,
        printDefGroupComments = false,
        traitModifiers = Nil,
        traitName = traitName,
        traitCommentLines = Nil,
        keyKind = "AriaAttr",
        implNameSuffix = "AriaAttr",
        baseImplDefComments = List(
          "Create ARIA attribute (Note: for HTML attrs, use L.htmlAttr)",
          "",
          "@param name  - suffix of the attribute, without \"aria-\" prefix, e.g. \"labelledby\"",
          "@param codec - used to encode V into String, e.g. StringAsIsCodec",
          "",
          "@tparam V    - value type for this attr in Scala"
        ),
        baseImplName = "ariaAttr",
        namespaceImports = Nil,
        namespaceImpl = _ => ???,
        transformAttrDomName = transformAttrDomName,
        defType = LazyVal
      )

      writeToFile(
        packagePath = generator.attrDefsPackagePath,
        fileName = traitName,
        fileContent = fileContent
      )
    }

    // -- HTML props --

    val htmlProps = {
      val traitName = "HtmlProps"

      val fileContent = generator.generatePropsTrait(
        defGroups = defGroups.propDefGroups,
        printDefGroupComments = true,
        traitCommentLines = Nil,
        traitModifiers = Nil,
        traitName = traitName,
        keyKind = "HtmlProp",
        implNameSuffix = "Prop",
        baseImplDefComments = List(
          "Create custom HTML element property",
          "",
          "@param name  - name of the prop in JS, e.g. \"value\"",
          "@param codec - used to encode V into DomV, e.g. StringAsIsCodec,",
          "",
          "@tparam V    - value type for this prop in Scala",
          "@tparam DomV - value type for this prop in the underlying JS DOM."
        ),
        baseImplName = "htmlProp",
        defType = LazyVal
      )

      writeToFile(
        packagePath = generator.propDefsPackagePath,
        fileName = traitName,
        fileContent = fileContent
      )
    }

    // -- Event props --

    val eventProps = {
      val baseTraitName = "GlobalEventProps"

      val subTraits = List(
        "WindowEventProps" -> defGroups.windowEventPropDefGroups,
        "DocumentEventProps" -> defGroups.documentEventPropDefGroups
      )

      val global = {
        val fileContent = generator.generateEventPropsTrait(
          defSources = defGroups.globalEventPropDefGroups,
          printDefGroupComments = true,
          traitCommentLines = Nil,
          traitModifiers = Nil,
          traitName = baseTraitName,
          traitExtends = Nil,
          traitThisType = None,
          baseImplDefComments = List(
            "Create custom event property",
            "",
            "@param name - event type in JS, e.g. \"click\"",
            "",
            "@tparam Ev - event type in JS, e.g. dom.MouseEvent"
          ),
          outputBaseImpl = true,
          keyKind = "EventProp",
          keyImplName = "eventProp",
          defType = LazyVal
        )

        writeToFile(
          packagePath = generator.eventPropDefsPackagePath,
          fileName = baseTraitName,
          fileContent = fileContent
        )
      }

      val local = subTraits.traverse { case (traitName, eventPropsDefGroups) =>
        val fileContent = generator.generateEventPropsTrait(
          defSources = eventPropsDefGroups,
          printDefGroupComments = true,
          traitCommentLines = List(eventPropsDefGroups.head._1),
          traitModifiers = Nil,
          traitName = traitName,
          traitExtends = Nil,
          traitThisType = Some(baseTraitName),
          baseImplDefComments = Nil,
          outputBaseImpl = false,
          keyKind = "EventProp",
          keyImplName = "eventProp",
          defType = LazyVal
        )

        writeToFile(
          packagePath = generator.eventPropDefsPackagePath,
          fileName = traitName,
          fileContent = fileContent
        )
      }

      List(local, global.map(List(_))).parFlatSequence

    }

    // -- Style props --

    val styleProps = {
      val traitName = "StyleProps"

      val fileContent = generator.generateStylePropsTrait(
        defSources = defGroups.stylePropDefGroups,
        printDefGroupComments = true,
        traitCommentLines = Nil,
        traitModifiers = Nil,
        traitName = traitName,
        keyKind = "StyleProp",
        keyKindAlias = "StyleProp",
        setterType = "StyleSetter",
        setterTypeAlias = "SS",
        derivedKeyKind = "DerivedStyleProp",
        derivedKeyKindAlias = "DSP",
        baseImplDefComments = List(
          "Create custom CSS property",
          "",
          "@param name - name of CSS property, e.g. \"font-weight\"",
          "",
          "@tparam V   - type of values recognized by JS for this property, e.g. Int",
          "              Note: String is always allowed regardless of the type you put here.",
          "              If unsure, use String type as V."
        ),
        baseImplName = "styleProp",
        defType = LazyVal,
        lengthUnitsNumType = "Int",
        outputUnitTraits = true
      )

      writeToFile(
        packagePath = generator.stylePropDefsPackagePath,
        fileName = traitName,
        fileContent = fileContent
      )
    }

    // -- Style keyword traits

    val styleTrait = {
      StyleTraitDefs.defs.traverse { styleTrait =>
        val fileContent = generator.generateStyleKeywordsTrait(
          defSources = styleTrait.keywordDefGroups,
          printDefGroupComments = styleTrait.keywordDefGroups.length > 1,
          traitCommentLines = Nil,
          traitModifiers = Nil,
          traitName = styleTrait.scalaName.replace("[_]", ""),
          extendsTraits = styleTrait.extendsTraits.map(_.replace("[_]", "")),
          extendsUnitTraits = styleTrait.extendsUnits,
          propKind = "StyleProp",
          keywordType = "StyleSetter",
          derivedKeyKind = "DerivedStyleProp",
          lengthUnitsNumType = "Int",
          defType = LazyVal,
          outputUnitTypes = true,
          allowSuperCallInOverride = false // can't access lazy val from `super`
        )

        writeToFile(
          packagePath = generator.styleTraitsPackagePath(),
          fileName = styleTrait.scalaName.replace("[_]", ""),
          fileContent = fileContent
        )
      }
    }

    List(
      List(
        htmlTags,
        htmlAttrs,
        svgTags,
        svgAttrs,
        ariaAttrs,
        htmlProps,
        //styleProps
      ).sequence,
      eventProps,
      //styleTrait
    ).parFlatSequence

  }

}
