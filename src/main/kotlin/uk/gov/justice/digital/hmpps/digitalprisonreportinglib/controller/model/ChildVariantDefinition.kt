package uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model

data class ChildVariantDefinition(
  val id: String,
  val name: String,
  val resourceName: String,
  val specification: Specification? = null,
  val joinFields: List<String>,
)
