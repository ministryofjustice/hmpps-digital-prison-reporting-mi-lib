package uk.gov.justice.digital.hmpps.digitalprisonreportinglib.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.Count
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.ConfiguredApiRepository
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.IdentifiedHelper
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.ProductDefinitionRepository
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Dataset
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.SchemaField
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.SingleReportProductDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.policyengine.Policy
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.policyengine.WithPolicy
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.exception.UserAuthorisationException
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.security.DprAuthAwareAuthenticationToken

@Service
class SyncDataApiService(
  private val productDefinitionRepository: ProductDefinitionRepository,
  private val configuredApiRepository: ConfiguredApiRepository,
  private val productDefinitionTokenPolicyChecker: ProductDefinitionTokenPolicyChecker,
  identifiedHelper: IdentifiedHelper,
  @Value("\${URL_ENV_SUFFIX:#{null}}") val env: String? = null,
) : CommonDataApiService(identifiedHelper) {

  companion object {
    const val INVALID_REPORT_ID_MESSAGE = "Invalid report id provided:"
    const val INVALID_REPORT_VARIANT_ID_MESSAGE = "Invalid Report ID:"
    const val INVALID_FILTERS_MESSAGE = "Invalid filters provided."
    const val INVALID_STATIC_OPTIONS_MESSAGE = "Invalid static options provided."
    const val INVALID_DYNAMIC_OPTIONS_MESSAGE = "Invalid dynamic options length provided."
    const val INVALID_DYNAMIC_FILTER_MESSAGE = "Error. This filter is not a dynamic filter."
    const val MISSING_MANDATORY_FILTER_MESSAGE = "Mandatory filter value not provided:"
    const val FILTER_VALUE_DOES_NOT_MATCH_PATTERN_MESSAGE = "Filter value does not match pattern:"
  }

  fun validateAndFetchData(
    reportId: String,
    reportVariantId: String,
    filters: Map<String, String>,
    selectedPage: Long,
    pageSize: Long,
    sortColumn: String?,
    sortedAsc: Boolean,
    userToken: DprAuthAwareAuthenticationToken?,
    reportFieldId: Set<String>? = null,
    prefix: String? = null,
    dataProductDefinitionsPath: String? = null,
    datasetForFilter: Dataset? = null,
  ): List<Map<String, Any?>> {
    val productDefinition = productDefinitionRepository
      .getSingleReportProductDefinition(reportId, reportVariantId, dataProductDefinitionsPath)
    checkAuth(productDefinition, userToken)
    val dynamicFilter = buildAndValidateDynamicFilter(reportFieldId?.first(), prefix, productDefinition)
    val policyEngine = PolicyEngine(productDefinition.policy, userToken)
    val formulaEngine = FormulaEngine(productDefinition.report.specification?.field ?: emptyList(), env, identifiedHelper)
    return configuredApiRepository
      .executeQuery(
        query = datasetForFilter?.query ?: productDefinition.reportDataset.query,
        filters = validateAndMapFilters(productDefinition, filters, null, reportFieldId) + dynamicFilter,
        selectedPage = selectedPage,
        pageSize = pageSize,
        sortColumn = datasetForFilter?.let { findSortColumn(sortColumn, it) } ?: sortColumnFromQueryOrGetDefault(productDefinition, sortColumn),
        sortedAsc = sortedAsc,
        policyEngineResult = datasetForFilter?.let { Policy.PolicyResult.POLICY_PERMIT } ?: policyEngine.execute(),
        dynamicFilterFieldId = reportFieldId,
        dataSourceName = productDefinition.datasource.name,
        reportFilter = productDefinition.report.filter,
      )
      .let { records ->
        applyFormulasSelectivelyAndFormatColumns(
          records,
          productDefinition,
          formulaEngine,
          datasetForFilter,
        )
      }
  }

  fun validateAndFetchDataForFilterWithDataset(
    pageSize: Long,
    sortColumn: String,
    dataset: Dataset,
  ): List<Map<String, Any?>> {
    val formulaEngine = FormulaEngine(emptyList(), env, identifiedHelper)
    return configuredApiRepository
      .executeQuery(
        query = dataset.query,
        filters = emptyList(),
        selectedPage = 1,
        pageSize = pageSize,
        sortColumn = sortColumn,
        sortedAsc = true,
        policyEngineResult = dataset.let { Policy.PolicyResult.POLICY_PERMIT },
        dataSourceName = dataset.datasource,
      )
      .let { records ->
        formatColumnsAndApplyFormulas(
          records,
          dataset.schema.field,
          formulaEngine,
        )
      }
  }

  fun validateAndCount(
    reportId: String,
    reportVariantId: String,
    filters: Map<String, String>,
    userToken: DprAuthAwareAuthenticationToken?,
    dataProductDefinitionsPath: String? = null,
  ): Count {
    val productDefinition = productDefinitionRepository.getSingleReportProductDefinition(
      reportId,
      reportVariantId,
      dataProductDefinitionsPath,
    )
    checkAuth(productDefinition, userToken)
    val policyEngine = PolicyEngine(productDefinition.policy, userToken)
    return Count(
      configuredApiRepository.count(
        filters = validateAndMapFilters(productDefinition, filters, null),
        query = productDefinition.reportDataset.query,
        reportId = reportId,
        policyEngineResult = policyEngine.execute(),
        dataSourceName = productDefinition.datasource.name,
        productDefinition = productDefinition,
      ),
    )
  }

  private fun checkAuth(
    productDefinition: WithPolicy,
    userToken: DprAuthAwareAuthenticationToken?,
  ): Boolean {
    if (!productDefinitionTokenPolicyChecker.determineAuth(productDefinition, userToken)) {
      throw UserAuthorisationException("User does not have correct authorisation")
    }
    return true
  }

  private fun applyFormulasSelectivelyAndFormatColumns(
    records: List<Map<String, Any?>>,
    productDefinition: SingleReportProductDefinition,
    formulaEngine: FormulaEngine,
    datasetForFilter: Dataset?,
  ) = datasetForFilter?.let {
    records.map { row ->
      formatColumnNamesToSourceFieldNamesCasing(row, datasetForFilter.schema.field.map(SchemaField::name))
    }
  } ?: formatColumnsAndApplyFormulas(records, productDefinition.reportDataset.schema.field, formulaEngine)

  private fun formatColumnsAndApplyFormulas(
    records: List<Map<String, Any?>>,
    schemaFields: List<SchemaField>,
    formulaEngine: FormulaEngine,
  ) = records
    .map { row -> formatColumnNamesToSourceFieldNamesCasing(row, schemaFields.map(SchemaField::name)) }
    .map(formulaEngine::applyFormulas)
}
