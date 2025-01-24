package uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data

import jakarta.validation.ValidationException
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.ProductDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.SingleDashboardProductDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.SingleReportProductDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.service.SyncDataApiService.Companion.INVALID_REPORT_ID_MESSAGE

abstract class AbstractProductDefinitionRepository(
  private val identifiedHelper: IdentifiedHelper,
) : ProductDefinitionRepository {

  override fun getSingleReportProductDefinition(
    definitionId: String,
    reportId: String,
    dataProductDefinitionsPath: String?,
  ): SingleReportProductDefinition {
    val productDefinition: ProductDefinition = getProductDefinition(definitionId, dataProductDefinitionsPath)
    val reportDefinition = identifiedHelper.findOrFail(productDefinition.report, reportId)
    val dataSet = identifiedHelper.findOrFail(productDefinition.dataset, reportDefinition.dataset)

    return SingleReportProductDefinition(
      id = definitionId,
      name = productDefinition.name,
      description = productDefinition.description,
      metadata = productDefinition.metadata,
      datasource = productDefinition.datasource.first(),
      reportDataset = dataSet,
      allDatasets = productDefinition.dataset,
      report = reportDefinition,
      policy = productDefinition.policy,
      allReports = productDefinition.report,
    )
  }

  override fun getSingleDashboardProductDefinition(
    definitionId: String,
    dashboardId: String,
    dataProductDefinitionsPath: String?,
  ): SingleDashboardProductDefinition {
    val productDefinition: ProductDefinition = getProductDefinition(definitionId, dataProductDefinitionsPath)
    val dashboard = productDefinition.dashboards?.firstOrNull { it.id == dashboardId }
      ?: throw ValidationException("Invalid report dashboard id provided: $dashboardId")

    val dataSet = identifiedHelper.findOrFail(productDefinition.dataset, dashboard.dataset)

    return SingleDashboardProductDefinition(
      id = definitionId,
      name = productDefinition.name,
      description = productDefinition.description,
      metadata = productDefinition.metadata,
      datasource = productDefinition.datasource.first(),
      policy = productDefinition.policy,
      dashboardDataset = dataSet,
      dashboard = dashboard,
      allDatasets = productDefinition.dataset,
    )
  }

  override fun getProductDefinition(definitionId: String, dataProductDefinitionsPath: String?): ProductDefinition = getProductDefinitions(dataProductDefinitionsPath)
    .filter { it.id == definitionId }
    .ifEmpty { throw ValidationException("$INVALID_REPORT_ID_MESSAGE $definitionId") }
    .first()
}
