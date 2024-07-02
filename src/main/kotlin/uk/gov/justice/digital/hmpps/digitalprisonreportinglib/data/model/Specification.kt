package uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model

data class Specification(
  val template: String,
  val field: List<ReportField>,
  val section: List<String>?,
)
