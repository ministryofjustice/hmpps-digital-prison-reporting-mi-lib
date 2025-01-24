package uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model

import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.policyengine.Policy
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.policyengine.WithPolicy

data class SingleReportProductDefinition(
  val id: String,
  val name: String,
  val description: String? = null,
  val metadata: MetaData,
  val datasource: Datasource,
  val reportDataset: Dataset,
  val report: Report,
  override val policy: List<Policy>,
  val allDatasets: List<Dataset>,
  val allReports: List<Report>,
) : WithPolicy
