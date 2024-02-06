package uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model

data class Feature(
  val type: FeatureType,
)

enum class FeatureType(val type: String) {
  PRINT("print")
}
