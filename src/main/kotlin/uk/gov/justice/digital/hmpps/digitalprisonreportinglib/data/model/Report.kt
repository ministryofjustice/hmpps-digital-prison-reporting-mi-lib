package uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model

import java.time.LocalDateTime

data class Report(
  val id: String,
  val name: String,
  val description: String? = null,
  val created: LocalDateTime?,
  val version: String,
  val dataset: String,
  val render: RenderMethod,
  val schedule: String? = null,
  val specification: Specification? = null,
  val destination: List<Map<String, String>> = emptyList(),
  val classification: String? = null,
  val feature: List<Feature>? = emptyList(),
  val summary: List<ReportSummary>? = emptyList(),
  val filter: ReportFilter? = null,
  val metadata: ReportMetadata? = null,
  val child: List<ReportChild>? = null,
) : Identified() {
  override fun getIdentifier() = this.id
}
