package uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model

data class VariantDefinition(
  val id: String,
  val name: String,
  val resourceName: String,
  val description: String? = null,
  val specification: Specification? = null,
  val classification: String? = null,
  val printable: Boolean? = true,
  val summaries: List<ReportSummary>? = emptyList(),
  val interactive: Boolean? = null,
  val childVariants: List<ChildVariantDefinition>? = null,
)
