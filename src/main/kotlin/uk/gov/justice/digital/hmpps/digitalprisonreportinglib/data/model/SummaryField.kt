package uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model

data class SummaryField(
  val name: String,
  val header: Boolean? = false,
  val mergeRows: Boolean? = false,
  val sortAsc: Boolean? = false,
)
