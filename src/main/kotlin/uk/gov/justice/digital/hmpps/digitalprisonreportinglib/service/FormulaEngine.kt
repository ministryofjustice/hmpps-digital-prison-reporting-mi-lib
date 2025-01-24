package uk.gov.justice.digital.hmpps.digitalprisonreportinglib.service

import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.IdentifiedHelper
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.ReportField
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class FormulaEngine(
  private val reportFields: List<ReportField>,
  private val env: String? = null,
  private val identifiedHelper: IdentifiedHelper = IdentifiedHelper(),
) {

  companion object {
    const val MAKE_URL_FORMULA_PREFIX = "make_url("
    const val FORMAT_DATE_FORMULA_PREFIX = "format_date("
  }

  fun applyFormulas(row: Map<String, Any?>): Map<String, Any?> =
    row.entries.associate { e ->
      e.key to constructValueWithFormulaInterpolationIfNeeded(e, row)
    }

  private fun constructValueWithFormulaInterpolationIfNeeded(
    e: Map.Entry<String, Any?>,
    row: Map<String, Any?>,
  ) = (
    findFormula(e.key)
      ?.let {
        interpolate(formula = it, row)
      } ?: e.value
    )

  private fun findFormula(columnName: String) =
    identifiedHelper.findOrNull(reportFields, columnName)?.formula?.ifEmpty { null }

  private fun interpolate(formula: String, row: Map<String, Any?>): String {
    return when {
      formula.startsWith(MAKE_URL_FORMULA_PREFIX) -> interpolateUrlFormula(formula, row)
      formula.startsWith(FORMAT_DATE_FORMULA_PREFIX) -> interpolateFormatDateFormula(formula, row)
      else -> interpolateStandardFormula(formula, row)
    }
  }

  private fun interpolateFormatDateFormula(formula: String, row: Map<String, Any?>): String {
    val (dateColumnNamePlaceholder, datePatternPlaceholder) = formula.substring(FORMAT_DATE_FORMULA_PREFIX.length, formula.indexOf(")"))
      .split(",")
    val dateColumnName = dateColumnNamePlaceholder.removeSurrounding(prefix = "\${", suffix = "}")
    val date = row[dateColumnName] ?: return ""
    return when (date) {
      is LocalDate -> date.format(DateTimeFormatter.ofPattern(removeQuotes(datePatternPlaceholder.trim())))
      is LocalDateTime -> date.format(DateTimeFormatter.ofPattern(removeQuotes(datePatternPlaceholder.trim())))
      is Date -> SimpleDateFormat(removeQuotes(datePatternPlaceholder.trim())).format(date)
      else -> throw IllegalArgumentException("Could not parse date: $date, of type ${date::class}")
    }
  }

  private fun removeQuotes(dateFormat: String) = dateFormat.removeSurrounding("'", "'").removeSurrounding("\"", "\"")

  private fun interpolateStandardFormula(formula: String, row: Map<String, Any?>): String {
    val sb = StringBuilder(formula)
    row.keys.forEach {
      sb.replace(
        0,
        sb.length,
        sb.toString()
          .replace("\${$it}", row.getOrElse(it) { "" }.toString()),
      )
    }
    return sb.toString()
  }

  private fun interpolateUrlFormula(formula: String, row: Map<String, Any?>): String {
    val interpolatedEnv =
      env?.let { formula.replace("\${env}", env) } ?: formula.replace("-\${env}", "")
    val (hrefPlaceholder, linkTextPlaceholder, newTab) = interpolatedEnv.substring(MAKE_URL_FORMULA_PREFIX.length, interpolatedEnv.indexOf(")"))
      .split(",")
    val href = interpolateStandardFormula(hrefPlaceholder, row)
    val linkText = interpolateStandardFormula(linkTextPlaceholder, row)
    return """<a href=$href ${if (newTab.uppercase() == "TRUE") "target=\"_blank\"" else ""}>$linkText</a>"""
  }
}
