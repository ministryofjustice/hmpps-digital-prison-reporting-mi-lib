package uk.gov.justice.digital.hmpps.digitalprisonreportinglib.service

import jakarta.validation.ValidationException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.security.core.authority.SimpleGrantedAuthority
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.config.DefinitionGsonConfig
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.DataApiSyncController.FiltersPrefix.RANGE_FILTER_END_SUFFIX
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.DataApiSyncController.FiltersPrefix.RANGE_FILTER_START_SUFFIX
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.Count
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.ConfiguredApiRepository
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.ConfiguredApiRepository.Filter
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.IdentifiedHelper
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.IsoLocalDateTimeTypeAdaptor
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.JsonFileProductDefinitionRepository
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.ProductDefinitionRepository
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.RepositoryHelper.Companion.EXTERNAL_MOVEMENTS_PRODUCT_ID
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.RepositoryHelper.FilterType.BOOLEAN
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.RepositoryHelper.FilterType.DATE_RANGE_END
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.RepositoryHelper.FilterType.DATE_RANGE_START
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.RepositoryHelper.FilterType.DYNAMIC
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Dataset
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Datasource
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.MetaData
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.ParameterType
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.ProductDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.RenderMethod
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Report
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.ReportField
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Schema
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.SchemaField
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.SingleReportProductDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Specification
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Template
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Visible
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.policyengine.Effect
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.policyengine.Policy
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.policyengine.Policy.PolicyResult.POLICY_PERMIT
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.policyengine.PolicyType
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.policyengine.Rule
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.security.DprAuthAwareAuthenticationToken
import java.time.LocalDateTime

class SyncDataApiServiceTest {
  private val configuredApiRepository: ConfiguredApiRepository = mock<ConfiguredApiRepository>()
  private val expectedRepositoryResult = listOf(
    mapOf(
      "PRISONNUMBER" to "1",
      "NAME" to "FirstName",
      "DATE" to "2023-05-20",
      "ORIGIN" to "OriginLocation",
      "DESTINATION" to "DestinationLocation",
      "DIRECTION" to "in",
      "TYPE" to "trn",
      "REASON" to "normal transfer",
    ),
  )
  private val expectedServiceResult = listOf(
    mapOf(
      "prisonNumber" to "1",
      "name" to "FirstName",
      "date" to "2023-05-20",
      "origin" to "OriginLocation",
      "destination" to "DestinationLocation",
      "direction" to "in",
      "type" to "trn",
      "reason" to "normal transfer",
    ),
  )
  private val authToken = mock<DprAuthAwareAuthenticationToken>()
  private val reportId = EXTERNAL_MOVEMENTS_PRODUCT_ID
  private val reportVariantId = "last-month"
  private val policyEngineResult = "TRUE AND (origin_code='WWI' AND lower(direction)='out') OR (destination_code='WWI' AND lower(direction)='in')"
  private val productDefinitionTokenPolicyChecker = mock<ProductDefinitionTokenPolicyChecker>()
  private val identifiedHelper: IdentifiedHelper = IdentifiedHelper()
  private val productDefinitionRepository: ProductDefinitionRepository = JsonFileProductDefinitionRepository(
    listOf("productDefinition.json"),
    DefinitionGsonConfig().definitionGson(IsoLocalDateTimeTypeAdaptor()),
    identifiedHelper,
  )
  private val configuredApiService = SyncDataApiService(productDefinitionRepository, configuredApiRepository, productDefinitionTokenPolicyChecker, identifiedHelper)

  @BeforeEach
  fun setup() {
    whenever(authToken.getCaseLoads()).thenReturn(listOf("WWI"))
    whenever(
      productDefinitionTokenPolicyChecker.determineAuth(
        withPolicy = any(),
        userToken = any(),
      ),
    ).thenReturn(true)
  }

  @Test
  fun `should call the repository with the corresponding arguments and get a list of rows when both range and non range filters are provided`() {
    val filters = mapOf("direction" to "in", "date$RANGE_FILTER_START_SUFFIX" to "2023-04-25", "date$RANGE_FILTER_END_SUFFIX" to "2023-09-10")
    val repositoryFilters = listOf(Filter("direction", "in"), Filter("date", "2023-04-25", DATE_RANGE_START), Filter("date", "2023-09-10", DATE_RANGE_END))
    val selectedPage = 1L
    val pageSize = 10L
    val sortColumn = "date"
    val sortedAsc = true
    val dataSet = productDefinitionRepository.getProductDefinitions().first().dataset.first()
    val singleReportProductDefinition =
      productDefinitionRepository.getSingleReportProductDefinition(reportId, reportVariantId)
    val dataSourceName = singleReportProductDefinition.datasource.name

    whenever(
      configuredApiRepository.executeQuery(
        query = dataSet.query,
        filters = repositoryFilters,
        selectedPage = selectedPage,
        pageSize = pageSize,
        sortColumn = sortColumn,
        sortedAsc = sortedAsc,
        policyEngineResult = policyEngineResult,
        dataSourceName = dataSourceName,
        reportFilter = singleReportProductDefinition.report.filter,
      ),
    ).thenReturn(expectedRepositoryResult)

    val actual = configuredApiService.validateAndFetchData(reportId, reportVariantId, filters, selectedPage, pageSize, sortColumn, sortedAsc, authToken)

    verify(configuredApiRepository, times(1)).executeQuery(
      query = dataSet.query,
      filters = repositoryFilters,
      selectedPage = selectedPage,
      pageSize = pageSize,
      sortColumn = sortColumn,
      sortedAsc = sortedAsc,
      policyEngineResult = policyEngineResult,
      dataSourceName = dataSourceName,
      reportFilter = singleReportProductDefinition.report.filter,
    )
    assertEquals(expectedServiceResult, actual)
  }

  @Test
  fun `should call the repository with the corresponding arguments and get a list of rows when a dynamic filter is provided`() {
    val filters = mapOf(
      "direction" to "in",
      "date$RANGE_FILTER_START_SUFFIX" to "2023-04-25",
      "date$RANGE_FILTER_END_SUFFIX" to "2023-09-10",
    )
    val repositoryFilters = listOf(
      Filter("direction", "in"),
      Filter("date", "2023-04-25", DATE_RANGE_START),
      Filter("date", "2023-09-10", DATE_RANGE_END),
      Filter("name", "Ab", DYNAMIC),
    )
    val selectedPage = 1L
    val pageSize = 10L
    val sortColumn = "date"
    val sortedAsc = true
    val dataSet = productDefinitionRepository.getProductDefinitions().first().dataset.first()
    val reportFieldId = "name"
    val prefix = "Ab"
    val dataSourceName = productDefinitionRepository.getSingleReportProductDefinition(reportId, reportVariantId).datasource.name
    val singleReportProductDefinition =
      productDefinitionRepository.getSingleReportProductDefinition(reportId, reportVariantId)

    whenever(
      configuredApiRepository.executeQuery(
        query = dataSet.query,
        filters = repositoryFilters,
        selectedPage = selectedPage,
        pageSize = pageSize,
        sortColumn = sortColumn,
        sortedAsc = sortedAsc,
        policyEngineResult = policyEngineResult,
        dynamicFilterFieldId = setOf(reportFieldId),
        dataSourceName = dataSourceName,
        reportFilter = singleReportProductDefinition.report.filter,
      ),
    ).thenReturn(expectedRepositoryResult)

    val actual = configuredApiService.validateAndFetchData(
      reportId,
      reportVariantId,
      filters,
      selectedPage,
      pageSize,
      sortColumn,
      sortedAsc,
      authToken,
      setOf(reportFieldId),
      prefix,
    )

    verify(configuredApiRepository, times(1)).executeQuery(
      query = dataSet.query,
      filters = repositoryFilters,
      selectedPage = selectedPage,
      pageSize = pageSize,
      sortColumn = sortColumn,
      sortedAsc = sortedAsc,
      policyEngineResult = policyEngineResult,
      dynamicFilterFieldId = setOf(reportFieldId),
      dataSourceName = dataSourceName,
      reportFilter = singleReportProductDefinition.report.filter,
    )
    assertEquals(expectedServiceResult, actual)
  }

  @Test
  fun `should call the repository with the correct dataset query, a permit policy and get a list of rows when a datasetForFilter is provided`() {
    val selectedPage = 1L
    val pageSize = 30L
    val sortedAsc = true
    val dataSourceName = productDefinitionRepository.getSingleReportProductDefinition(reportId, reportVariantId).datasource.name
    val estNameSchemaFieldName = "establishment_name"
    val estCodeSchemaFieldName = "establishment_code"
    val filterDataset = Dataset(
      id = "establishment-dataset",
      name = "establishment-dataset-name",
      query = "select * from table",
      datasource = "redshift",
      schema = Schema(
        listOf(
          SchemaField(estCodeSchemaFieldName, ParameterType.String, "Establishment Code", null),
          SchemaField(estNameSchemaFieldName, ParameterType.String, "Establishment Name", null),
        ),
      ),
    )
    val expectedEstablishmentRepositoryResult = listOf(
      mapOf(
        estNameSchemaFieldName.uppercase() to "establishment name",
        estCodeSchemaFieldName.uppercase() to "establishment code",
      ),
    )
    val expectedServiceResult = listOf(
      mapOf(
        estNameSchemaFieldName to "establishment name",
        estCodeSchemaFieldName to "establishment code",
      ),
    )
    val singleReportProductDefinition =
      productDefinitionRepository.getSingleReportProductDefinition(reportId, reportVariantId)

    whenever(
      configuredApiRepository.executeQuery(
        query = filterDataset.query,
        filters = emptyList(),
        selectedPage = selectedPage,
        pageSize = pageSize,
        sortColumn = estNameSchemaFieldName,
        sortedAsc = sortedAsc,
        policyEngineResult = POLICY_PERMIT,
        dynamicFilterFieldId = linkedSetOf(estNameSchemaFieldName, estCodeSchemaFieldName),
        dataSourceName = dataSourceName,
        reportFilter = singleReportProductDefinition.report.filter,
      ),
    ).thenReturn(expectedEstablishmentRepositoryResult)

    val actual = configuredApiService.validateAndFetchData(
      reportId = reportId,
      reportVariantId = reportVariantId,
      filters = emptyMap(),
      selectedPage = selectedPage,
      pageSize = pageSize,
      sortColumn = estNameSchemaFieldName,
      sortedAsc = sortedAsc,
      userToken = authToken,
      reportFieldId = linkedSetOf(estNameSchemaFieldName, estCodeSchemaFieldName),
      datasetForFilter = filterDataset,
    )

    verify(configuredApiRepository, times(1)).executeQuery(
      query = filterDataset.query,
      filters = emptyList(),
      selectedPage = selectedPage,
      pageSize = pageSize,
      sortColumn = estNameSchemaFieldName,
      sortedAsc = sortedAsc,
      policyEngineResult = POLICY_PERMIT,
      dynamicFilterFieldId = linkedSetOf(estNameSchemaFieldName, estCodeSchemaFieldName),
      dataSourceName = dataSourceName,
      reportFilter = singleReportProductDefinition.report.filter,
    )
    assertEquals(expectedServiceResult, actual)
  }

  @Test
  fun `should call the repository with the corresponding arguments and get a count of rows when both range and non range filters are provided`() {
    val filters = mapOf("direction" to "in", "date$RANGE_FILTER_START_SUFFIX" to "2023-04-25", "date$RANGE_FILTER_END_SUFFIX" to "2023-09-10")
    val repositoryFilters = listOf(Filter("direction", "in"), Filter("date", "2023-04-25", DATE_RANGE_START), Filter("date", "2023-09-10", DATE_RANGE_END))
    val dataSet = productDefinitionRepository.getProductDefinitions().first().dataset.first()
    val dataSourceName = productDefinitionRepository.getSingleReportProductDefinition(reportId, reportVariantId).datasource.name
    val singleReportProductDefinition =
      productDefinitionRepository.getSingleReportProductDefinition(reportId, reportVariantId)

    whenever(
      configuredApiRepository.count(
        filters = repositoryFilters,
        query = dataSet.query,
        reportId = reportId,
        policyEngineResult = policyEngineResult,
        dataSourceName = dataSourceName,
        productDefinition = singleReportProductDefinition,
      ),
    ).thenReturn(4)

    val actual = configuredApiService.validateAndCount(reportId, reportVariantId, filters, authToken)

    verify(configuredApiRepository, times(1)).count(
      filters = repositoryFilters,
      query = dataSet.query,
      reportId = reportId,
      policyEngineResult = policyEngineResult,
      dataSourceName = dataSourceName,
      productDefinition = singleReportProductDefinition,
    )
    assertEquals(Count(4), actual)
  }

  @Test
  fun `should call the repository with the corresponding arguments and get a list of rows when only range filters are provided`() {
    val filters = mapOf("date$RANGE_FILTER_START_SUFFIX" to "2023-04-25", "date$RANGE_FILTER_END_SUFFIX" to "2023-09-10")
    val repositoryFilters = listOf(Filter("date", "2023-04-25", DATE_RANGE_START), Filter("date", "2023-09-10", DATE_RANGE_END))
    val selectedPage = 1L
    val pageSize = 10L
    val sortColumn = "date"
    val sortedAsc = true
    val dataSet = productDefinitionRepository.getProductDefinitions().first().dataset.first()
    val dataSourceName = productDefinitionRepository.getSingleReportProductDefinition(reportId, reportVariantId).datasource.name
    val singleReportProductDefinition =
      productDefinitionRepository.getSingleReportProductDefinition(reportId, reportVariantId)

    whenever(
      configuredApiRepository.executeQuery(
        query = dataSet.query,
        filters = repositoryFilters,
        selectedPage = selectedPage,
        pageSize = pageSize,
        sortColumn = sortColumn,
        sortedAsc = sortedAsc,
        policyEngineResult = policyEngineResult,
        dataSourceName = dataSourceName,
        reportFilter = singleReportProductDefinition.report.filter,
      ),
    ).thenReturn(expectedRepositoryResult)

    val actual = configuredApiService.validateAndFetchData(reportId, reportVariantId, filters, selectedPage, pageSize, sortColumn, sortedAsc, authToken)

    verify(configuredApiRepository, times(1)).executeQuery(
      query = dataSet.query,
      filters = repositoryFilters,
      selectedPage = selectedPage,
      pageSize = pageSize,
      sortColumn = sortColumn,
      sortedAsc = sortedAsc,
      policyEngineResult = policyEngineResult,
      dataSourceName = dataSourceName,
      reportFilter = singleReportProductDefinition.report.filter,
    )
    assertEquals(expectedServiceResult, actual)
  }

  @Test
  fun `should call the repository with the corresponding arguments and get a count of rows when only range filters are provided`() {
    val filters = mapOf("date$RANGE_FILTER_START_SUFFIX" to "2023-04-25", "date$RANGE_FILTER_END_SUFFIX" to "2023-09-10")
    val repositoryFilters = listOf(Filter("date", "2023-04-25", DATE_RANGE_START), Filter("date", "2023-09-10", DATE_RANGE_END))
    val dataSet = productDefinitionRepository.getProductDefinitions().first().dataset.first()
    val dataSourceName = productDefinitionRepository.getSingleReportProductDefinition(reportId, reportVariantId).datasource.name
    val singleReportProductDefinition =
      productDefinitionRepository.getSingleReportProductDefinition(reportId, reportVariantId)

    whenever(
      configuredApiRepository.count(
        filters = repositoryFilters,
        query = dataSet.query,
        reportId = reportId,
        policyEngineResult = policyEngineResult,
        dataSourceName = dataSourceName,
        productDefinition = singleReportProductDefinition,
      ),
    ).thenReturn(4)

    val actual = configuredApiService.validateAndCount(reportId, reportVariantId, filters, authToken)

    verify(configuredApiRepository, times(1)).count(
      filters = repositoryFilters,
      query = dataSet.query,
      reportId = reportId,
      policyEngineResult = policyEngineResult,
      dataSourceName = dataSourceName,
      productDefinition = singleReportProductDefinition,
    )
    assertEquals(Count(4), actual)
  }

  @Test
  fun `should call the repository with the corresponding arguments and get a list of rows when only non range filters are provided`() {
    val filtersExcludingRange = mapOf("direction" to "in")
    val repositoryFilters = listOf(Filter("direction", "in"))

    val selectedPage = 1L
    val pageSize = 10L
    val sortColumn = "date"
    val sortedAsc = true
    val dataSet = productDefinitionRepository.getProductDefinitions().first().dataset.first()
    val dataSourceName = productDefinitionRepository.getSingleReportProductDefinition(reportId, reportVariantId).datasource.name
    val singleReportProductDefinition =
      productDefinitionRepository.getSingleReportProductDefinition(reportId, reportVariantId)

    whenever(
      configuredApiRepository.executeQuery(
        query = dataSet.query,
        filters = repositoryFilters,
        selectedPage = selectedPage,
        pageSize = pageSize,
        sortColumn = sortColumn,
        sortedAsc = sortedAsc,
        policyEngineResult = policyEngineResult,
        dataSourceName = dataSourceName,
        reportFilter = singleReportProductDefinition.report.filter,
      ),
    ).thenReturn(expectedRepositoryResult)

    val actual = configuredApiService.validateAndFetchData(reportId, reportVariantId, filtersExcludingRange, selectedPage, pageSize, sortColumn, sortedAsc, authToken)

    verify(configuredApiRepository, times(1)).executeQuery(
      dataSet.query,
      repositoryFilters,
      selectedPage,
      pageSize,
      sortColumn,
      sortedAsc,
      policyEngineResult = policyEngineResult,
      dataSourceName = dataSourceName,
      reportFilter = singleReportProductDefinition.report.filter,
    )
    assertEquals(expectedServiceResult, actual)
  }

  @Test
  fun `should call the repository with the corresponding arguments and get a count of rows when only non range filters are provided`() {
    val filters = mapOf("direction" to "in")
    val repositoryFilters = listOf(Filter("direction", "in"))
    val dataSet = productDefinitionRepository.getProductDefinitions().first().dataset.first()
    val dataSourceName = productDefinitionRepository.getSingleReportProductDefinition(reportId, reportVariantId).datasource.name
    val singleReportProductDefinition =
      productDefinitionRepository.getSingleReportProductDefinition(reportId, reportVariantId)

    whenever(
      configuredApiRepository.count(
        filters = repositoryFilters,
        query = dataSet.query,
        reportId = reportId,
        policyEngineResult = policyEngineResult,
        dataSourceName = dataSourceName,
        productDefinition = singleReportProductDefinition,
      ),
    ).thenReturn(4)

    val actual = configuredApiService.validateAndCount(reportId, reportVariantId, filters, authToken)

    verify(configuredApiRepository, times(1)).count(
      filters = repositoryFilters,
      query = dataSet.query,
      reportId = reportId,
      policyEngineResult = policyEngineResult,
      dataSourceName = dataSourceName,
      productDefinition = singleReportProductDefinition,
    )
    assertEquals(Count(4), actual)
  }

  @Test
  fun `should call the repository with the corresponding arguments and get a list of rows regardless of the casing of the values of the non range filters`() {
    val filters = mapOf("direction" to "In", "date$RANGE_FILTER_START_SUFFIX" to "2023-04-25", "date$RANGE_FILTER_END_SUFFIX" to "2023-09-10")
    val repositoryFilters = listOf(Filter("direction", "In"), Filter("date", "2023-04-25", DATE_RANGE_START), Filter("date", "2023-09-10", DATE_RANGE_END))
    val selectedPage = 1L
    val pageSize = 10L
    val sortColumn = "date"
    val sortedAsc = true
    val dataSet = productDefinitionRepository.getProductDefinitions().first().dataset.first()
    val dataSourceName = productDefinitionRepository.getSingleReportProductDefinition(reportId, reportVariantId).datasource.name
    val singleReportProductDefinition =
      productDefinitionRepository.getSingleReportProductDefinition(reportId, reportVariantId)

    whenever(
      configuredApiRepository.executeQuery(
        query = dataSet.query,
        filters = repositoryFilters,
        selectedPage = selectedPage,
        pageSize = pageSize,
        sortColumn = sortColumn,
        sortedAsc = sortedAsc,
        policyEngineResult = policyEngineResult,
        dataSourceName = dataSourceName,
        reportFilter = singleReportProductDefinition.report.filter,
      ),
    ).thenReturn(expectedRepositoryResult)

    val actual = configuredApiService.validateAndFetchData(reportId, reportVariantId, filters, selectedPage, pageSize, sortColumn, sortedAsc, authToken)

    verify(configuredApiRepository, times(1)).executeQuery(
      query = dataSet.query,
      filters = repositoryFilters,
      selectedPage = selectedPage,
      pageSize = pageSize,
      sortColumn = sortColumn,
      sortedAsc = sortedAsc,
      policyEngineResult = policyEngineResult,
      dataSourceName = dataSourceName,
      reportFilter = singleReportProductDefinition.report.filter,
    )
    assertEquals(expectedServiceResult, actual)
  }

  @Test
  fun `the policy engine should parse a null action correctly and return TRUE for an access policy with a matching condition`() {
    val productDefinitionRepository: ProductDefinitionRepository = JsonFileProductDefinitionRepository(
      listOf("productDefinitionPolicyNoAction.json"),
      DefinitionGsonConfig().definitionGson(IsoLocalDateTimeTypeAdaptor()),
      identifiedHelper,
    )
    val configuredApiService = SyncDataApiService(productDefinitionRepository, configuredApiRepository, productDefinitionTokenPolicyChecker, identifiedHelper)
    whenever(authToken.authorities).thenReturn(listOf(SimpleGrantedAuthority("USER-ROLE-1")))
    val policyEngineResult = "TRUE"
    val reportId = "definition-policy-no-action"
    val filters = mapOf("direction" to "In", "date$RANGE_FILTER_START_SUFFIX" to "2023-04-25", "date$RANGE_FILTER_END_SUFFIX" to "2023-09-10")
    val repositoryFilters = listOf(Filter("direction", "In"), Filter("date", "2023-04-25", DATE_RANGE_START), Filter("date", "2023-09-10", DATE_RANGE_END))
    val selectedPage = 1L
    val pageSize = 10L
    val sortColumn = "date"
    val sortedAsc = true
    val dataSet = productDefinitionRepository.getProductDefinitions().first().dataset.first()
    val dataSourceName = productDefinitionRepository.getSingleReportProductDefinition(reportId, reportVariantId).datasource.name
    val singleReportProductDefinition =
      productDefinitionRepository.getSingleReportProductDefinition(reportId, reportVariantId)

    whenever(
      configuredApiRepository.executeQuery(
        query = dataSet.query,
        filters = repositoryFilters,
        selectedPage = selectedPage,
        pageSize = pageSize,
        sortColumn = sortColumn,
        sortedAsc = sortedAsc,
        policyEngineResult = policyEngineResult,
        dataSourceName = dataSourceName,
        reportFilter = singleReportProductDefinition.report.filter,
      ),
    ).thenReturn(expectedRepositoryResult)

    val actual = configuredApiService.validateAndFetchData(reportId, reportVariantId, filters, selectedPage, pageSize, sortColumn, sortedAsc, authToken)

    verify(configuredApiRepository, times(1)).executeQuery(
      query = dataSet.query,
      filters = repositoryFilters,
      selectedPage = selectedPage,
      pageSize = pageSize,
      sortColumn = sortColumn,
      sortedAsc = sortedAsc,
      policyEngineResult = policyEngineResult,
      dataSourceName = dataSourceName,
      reportFilter = singleReportProductDefinition.report.filter,
    )
    assertEquals(expectedServiceResult, actual)
  }

  @Test
  fun `should call the repository with the corresponding arguments and get a count of rows regardless of the casing of the values of the non range filters`() {
    val filters = mapOf("direction" to "In", "date$RANGE_FILTER_START_SUFFIX" to "2023-04-25", "date$RANGE_FILTER_END_SUFFIX" to "2023-09-10")
    val repositoryFilters = listOf(Filter("direction", "In"), Filter("date", "2023-04-25", DATE_RANGE_START), Filter("date", "2023-09-10", DATE_RANGE_END))
    val dataSet = productDefinitionRepository.getProductDefinitions().first().dataset.first()
    val dataSourceName = productDefinitionRepository.getSingleReportProductDefinition(reportId, reportVariantId).datasource.name
    val singleReportProductDefinition =
      productDefinitionRepository.getSingleReportProductDefinition(reportId, reportVariantId)

    whenever(
      configuredApiRepository.count(
        filters = repositoryFilters,
        query = dataSet.query,
        reportId = reportId,
        policyEngineResult = policyEngineResult,
        dataSourceName = dataSourceName,
        productDefinition = singleReportProductDefinition,
      ),
    ).thenReturn(4)

    val actual = configuredApiService.validateAndCount(reportId, reportVariantId, filters, authToken)

    verify(configuredApiRepository, times(1)).count(
      filters = repositoryFilters,
      query = dataSet.query,
      reportId = reportId,
      policyEngineResult = policyEngineResult,
      dataSourceName = dataSourceName,
      productDefinition = singleReportProductDefinition,
    )
    assertEquals(Count(4), actual)
  }

  @Test
  fun `the service calls the repository without filters if no filters are provided`() {
    val dataSet = productDefinitionRepository.getProductDefinitions().first().dataset.first()
    val selectedPage = 1L
    val pageSize = 10L
    val sortColumn = "date"
    val sortedAsc = true
    val dataSourceName = productDefinitionRepository.getSingleReportProductDefinition(reportId, reportVariantId).datasource.name
    val singleReportProductDefinition =
      productDefinitionRepository.getSingleReportProductDefinition(reportId, reportVariantId)

    whenever(
      configuredApiRepository.executeQuery(
        query = dataSet.query,
        filters = emptyList(),
        selectedPage = selectedPage,
        pageSize = pageSize,
        sortColumn = sortColumn,
        sortedAsc = sortedAsc,
        policyEngineResult = policyEngineResult,
        dataSourceName = dataSourceName,
        reportFilter = singleReportProductDefinition.report.filter,
      ),
    ).thenReturn(
      listOf(
        mapOf("PRISONNUMBER" to "1"),
      ),
    )

    val actual = configuredApiService.validateAndFetchData(reportId, reportVariantId, emptyMap(), selectedPage, pageSize, sortColumn, sortedAsc, authToken)

    verify(configuredApiRepository, times(1)).executeQuery(
      query = dataSet.query,
      filters = emptyList(),
      selectedPage = 1,
      pageSize = 10,
      sortColumn = "date",
      sortedAsc = true,
      policyEngineResult = policyEngineResult,
      dataSourceName = dataSourceName,
      reportFilter = singleReportProductDefinition.report.filter,
    )
    assertEquals(
      listOf(
        mapOf("prisonNumber" to "1"),
      ),
      actual,
    )
  }

  @Test
  fun `the service count method calls the repository without filters if no filters are provided`() {
    val dataSet = productDefinitionRepository.getProductDefinitions().first().dataset.first()
    val dataSourceName = productDefinitionRepository.getSingleReportProductDefinition(reportId, reportVariantId).datasource.name
    val singleReportProductDefinition =
      productDefinitionRepository.getSingleReportProductDefinition(reportId, reportVariantId)

    whenever(
      configuredApiRepository.count(
        filters = emptyList(),
        query = dataSet.query,
        reportId = reportId,
        policyEngineResult = policyEngineResult,
        dataSourceName = dataSourceName,
        productDefinition = singleReportProductDefinition,
      ),
    ).thenReturn(4)

    val actual = configuredApiService.validateAndCount(reportId, reportVariantId, emptyMap(), authToken)

    verify(configuredApiRepository, times(1)).count(
      filters = emptyList(),
      query = dataSet.query,
      reportId = reportId,
      policyEngineResult = policyEngineResult,
      dataSourceName = dataSourceName,
      productDefinition = singleReportProductDefinition,
    )
    assertEquals(Count(4), actual)
  }

  @Test
  fun `validateAndFetchData should throw an exception for invalid report id`() {
    val reportId = "random report id"

    val filters = mapOf("direction" to "in", "date$RANGE_FILTER_START_SUFFIX" to "2023-04-25", "date$RANGE_FILTER_END_SUFFIX" to "2023-09-10")
    val selectedPage = 1L
    val pageSize = 10L
    val sortColumn = "date"
    val sortedAsc = true

    val e = org.junit.jupiter.api.assertThrows<ValidationException> {
      configuredApiService.validateAndFetchData(reportId, reportVariantId, filters, selectedPage, pageSize, sortColumn, sortedAsc, authToken)
    }
    assertEquals("${SyncDataApiService.INVALID_REPORT_ID_MESSAGE} $reportId", e.message)
    verify(configuredApiRepository, times(0)).executeQuery(
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
    )
  }

  @Test
  fun `validateAndCount should throw an exception for invalid report id`() {
    val reportId = "random report id"

    val filters = mapOf("direction" to "in", "date$RANGE_FILTER_START_SUFFIX" to "2023-04-25", "date$RANGE_FILTER_END_SUFFIX" to "2023-09-10")

    val e = org.junit.jupiter.api.assertThrows<ValidationException> {
      configuredApiService.validateAndCount(reportId, reportVariantId, filters, authToken)
    }
    assertEquals("${SyncDataApiService.INVALID_REPORT_ID_MESSAGE} $reportId", e.message)
    verify(configuredApiRepository, times(0)).count(any(), any(), any(), any(), any(), any())
  }

  @Test
  fun `validateAndFetchData should throw an exception for invalid report variant`() {
    val reportVariantId = "non existent variant"
    val filters = mapOf("direction" to "in", "date$RANGE_FILTER_START_SUFFIX" to "2023-04-25", "date$RANGE_FILTER_END_SUFFIX" to "2023-09-10")
    val selectedPage = 1L
    val pageSize = 10L
    val sortColumn = "date"
    val sortedAsc = true

    val e = org.junit.jupiter.api.assertThrows<java.lang.IllegalArgumentException> {
      configuredApiService.validateAndFetchData(reportId, reportVariantId, filters, selectedPage, pageSize, sortColumn, sortedAsc, authToken)
    }
    assertEquals("${SyncDataApiService.INVALID_REPORT_VARIANT_ID_MESSAGE} $reportVariantId", e.message)
    verify(configuredApiRepository, times(0)).executeQuery(
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
    )
  }

  @Test
  fun `validateAndCount should throw an exception for invalid report variant`() {
    val reportVariantId = "non existent variant"
    val filters = mapOf("direction" to "in", "date$RANGE_FILTER_START_SUFFIX" to "2023-04-25", "date$RANGE_FILTER_END_SUFFIX" to "2023-09-10")

    val e = org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
      configuredApiService.validateAndCount(reportId, reportVariantId, filters, authToken)
    }
    assertEquals("${SyncDataApiService.INVALID_REPORT_VARIANT_ID_MESSAGE} $reportVariantId", e.message)
    verify(configuredApiRepository, times(0)).count(any(), any(), any(), any(), any(), any())
  }

  @Test
  fun `validateAndFetchData should throw an exception for invalid sort column`() {
    val filters = mapOf("direction" to "in", "date$RANGE_FILTER_START_SUFFIX" to "2023-04-25", "date$RANGE_FILTER_END_SUFFIX" to "2023-09-10")
    val selectedPage = 1L
    val pageSize = 10L
    val sortColumn = "abc"
    val sortedAsc = true

    val e = org.junit.jupiter.api.assertThrows<ValidationException> {
      configuredApiService.validateAndFetchData(reportId, reportVariantId, filters, selectedPage, pageSize, sortColumn, sortedAsc, authToken)
    }
    assertEquals("Invalid sortColumn provided: abc", e.message)
    verify(configuredApiRepository, times(0)).executeQuery(
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
    )
  }

  @Test
  fun `validateAndFetchData should throw an exception for invalid filter`() {
    val filters = mapOf("non existent filter" to "blah")
    val selectedPage = 1L
    val pageSize = 10L
    val sortColumn = "date"
    val sortedAsc = true

    val e = org.junit.jupiter.api.assertThrows<ValidationException> {
      configuredApiService.validateAndFetchData(reportId, reportVariantId, filters, selectedPage, pageSize, sortColumn, sortedAsc, authToken)
    }
    assertEquals(SyncDataApiService.INVALID_FILTERS_MESSAGE, e.message)
    verify(configuredApiRepository, times(0)).executeQuery(
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["destination", "invalid field name"])
  fun `validateAndFetchData should throw an exception for invalid dynamic filter`(fieldId: String) {
    val selectedPage = 1L
    val pageSize = 10L
    val sortColumn = "date"
    val sortedAsc = true

    val e = org.junit.jupiter.api.assertThrows<ValidationException> {
      configuredApiService.validateAndFetchData(reportId, reportVariantId, emptyMap(), selectedPage, pageSize, sortColumn, sortedAsc, authToken, setOf(fieldId), "ab")
    }
    assertEquals(SyncDataApiService.INVALID_FILTERS_MESSAGE, e.message)
    verify(configuredApiRepository, times(0)).executeQuery(
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
    )
  }

  @Test
  fun `validateAndFetchData should throw an exception for a fieldId which is a filter but not a dynamic one`() {
    val selectedPage = 1L
    val pageSize = 10L
    val sortColumn = "date"
    val sortedAsc = true
    val fieldId = "direction"

    val e = org.junit.jupiter.api.assertThrows<ValidationException> {
      configuredApiService.validateAndFetchData(reportId, reportVariantId, emptyMap(), selectedPage, pageSize, sortColumn, sortedAsc, authToken, setOf(fieldId), "ab")
    }
    assertEquals(SyncDataApiService.INVALID_DYNAMIC_FILTER_MESSAGE, e.message)
    verify(configuredApiRepository, times(0)).executeQuery(
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
    )
  }

  @Test
  fun `validateAndFetchData should throw an exception for a mandatory filter with no value`() {
    val selectedPage = 1L
    val pageSize = 10L
    val sortColumn = "date"
    val sortedAsc = true

    val e = org.junit.jupiter.api.assertThrows<ValidationException> {
      configuredApiService.validateAndFetchData(reportId, "last-year", emptyMap(), selectedPage, pageSize, sortColumn, sortedAsc, authToken)
    }
    assertEquals(SyncDataApiService.MISSING_MANDATORY_FILTER_MESSAGE + " date", e.message)
  }

  @Test
  fun `validateAndFetchData should not throw an exception for a mandatory filter with no value if querying for a specific field`() {
    val selectedPage = 1L
    val pageSize = 10L
    val sortColumn = "date"
    val sortedAsc = true

    val actual = configuredApiService.validateAndFetchData(
      reportId = reportId,
      reportVariantId = "last-year",
      filters = emptyMap(),
      selectedPage = selectedPage,
      pageSize = pageSize,
      sortColumn = sortColumn,
      sortedAsc = sortedAsc,
      userToken = authToken,
      reportFieldId = setOf("name"),
    )

    assertThat(actual).isNotNull
  }

  @Test
  fun `validateAndFetchData should throw an exception for a filter value that does not match the validation pattern`() {
    val selectedPage = 1L
    val pageSize = 10L
    val sortColumn = "date"
    val sortedAsc = true
    val filters = mapOf(
      "date.start" to "2000-01-02",
      "origin" to "Invalid",
    )
    val e = org.junit.jupiter.api.assertThrows<ValidationException> {
      configuredApiService.validateAndFetchData(reportId, "last-year", filters, selectedPage, pageSize, sortColumn, sortedAsc, authToken)
    }
    assertEquals(SyncDataApiService.FILTER_VALUE_DOES_NOT_MATCH_PATTERN_MESSAGE + " Invalid [A-Z]{3,3}", e.message)
  }

  @Test
  fun `validateAndFetchData should not throw an exception for a filter value that does not match the validation pattern if querying for a specific field`() {
    val selectedPage = 1L
    val pageSize = 10L
    val sortColumn = "date"
    val sortedAsc = true
    val filters = mapOf(
      "date.start" to "2000-01-02",
      "origin" to "Invalid",
    )

    val actual = configuredApiService.validateAndFetchData(
      reportId = reportId,
      reportVariantId = "last-year",
      filters = filters,
      selectedPage = selectedPage,
      pageSize = pageSize,
      sortColumn = sortColumn,
      sortedAsc = sortedAsc,
      userToken = authToken,
      reportFieldId = setOf("origin"),
    )

    assertThat(actual).isNotNull()
  }

  @Test
  fun `validateAndCount should throw an exception for a mandatory filter with no value`() {
    val e = org.junit.jupiter.api.assertThrows<ValidationException> {
      configuredApiService.validateAndCount(reportId, "last-year", emptyMap(), authToken)
    }
    assertEquals(SyncDataApiService.MISSING_MANDATORY_FILTER_MESSAGE + " date", e.message)
  }

  @Test
  fun `validateAndCount should throw an exception for a filter value that does not match the validation pattern`() {
    val filters = mapOf(
      "date.start" to "2000-01-02",
      "origin" to "Invalid",
    )
    val e = org.junit.jupiter.api.assertThrows<ValidationException> {
      configuredApiService.validateAndCount(reportId, "last-year", filters, authToken)
    }
    assertEquals(SyncDataApiService.FILTER_VALUE_DOES_NOT_MATCH_PATTERN_MESSAGE + " Invalid [A-Z]{3,3}", e.message)
  }

  @Test
  fun `validateAndCount should throw an exception for invalid filter`() {
    val filters = mapOf("non existent filter" to "blah")

    val e = org.junit.jupiter.api.assertThrows<ValidationException> {
      configuredApiService.validateAndCount(reportId, reportVariantId, filters, authToken)
    }
    assertEquals(SyncDataApiService.INVALID_FILTERS_MESSAGE, e.message)
    verify(configuredApiRepository, times(0)).count(any(), any(), any(), any(), any(), any())
  }

  @Test
  fun `validateAndFetchData should throw an exception when having a valid and an invalid filter`() {
    val filters = mapOf("non existent filter" to "blah", "date$RANGE_FILTER_START_SUFFIX" to "2023-01-01")
    val selectedPage = 1L
    val pageSize = 10L
    val sortColumn = "date"
    val sortedAsc = true

    val e = org.junit.jupiter.api.assertThrows<ValidationException> {
      configuredApiService.validateAndFetchData(reportId, reportVariantId, filters, selectedPage, pageSize, sortColumn, sortedAsc, authToken)
    }
    assertEquals(SyncDataApiService.INVALID_FILTERS_MESSAGE, e.message)
    verify(configuredApiRepository, times(0)).executeQuery(
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
    )
  }

  @Test
  fun `validateAndCount should throw an exception when having a valid and an invalid filter`() {
    val filters = mapOf("non existent filter" to "blah", "date$RANGE_FILTER_START_SUFFIX" to "2023-01-01")

    val e = org.junit.jupiter.api.assertThrows<ValidationException> {
      configuredApiService.validateAndCount(reportId, reportVariantId, filters, authToken)
    }
    assertEquals(SyncDataApiService.INVALID_FILTERS_MESSAGE, e.message)
    verify(configuredApiRepository, times(0)).count(any(), any(), any(), any(), any(), any())
  }

  @Test
  fun `validateAndFetchData should throw an exception when having invalid static options for a filter and a valid range filter`() {
    val filters = mapOf("direction" to "randomValue", "date$RANGE_FILTER_START_SUFFIX" to "2023-01-01")
    val selectedPage = 1L
    val pageSize = 10L
    val sortColumn = "date"
    val sortedAsc = true

    val e = org.junit.jupiter.api.assertThrows<ValidationException> {
      configuredApiService.validateAndFetchData(reportId, reportVariantId, filters, selectedPage, pageSize, sortColumn, sortedAsc, authToken)
    }
    assertEquals(SyncDataApiService.INVALID_STATIC_OPTIONS_MESSAGE, e.message)
    verify(configuredApiRepository, times(0)).executeQuery(
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
    )
  }

  @Test
  fun `validateAndCount should throw an exception when having invalid static options for a filter and a valid range filter`() {
    val filters = mapOf("direction" to "randomValue", "date$RANGE_FILTER_START_SUFFIX" to "2023-01-01")

    val e = org.junit.jupiter.api.assertThrows<ValidationException> {
      configuredApiService.validateAndCount(reportId, reportVariantId, filters, authToken)
    }
    assertEquals(SyncDataApiService.INVALID_STATIC_OPTIONS_MESSAGE, e.message)
    verify(configuredApiRepository, times(0)).count(any(), any(), any(), any(), any(), any())
  }

  @Test
  fun `validateAndFetchData should throw an exception when having invalid static options for a filter and no range filters`() {
    val filters = mapOf("direction" to "randomValue")
    val selectedPage = 1L
    val pageSize = 10L
    val sortColumn = "date"
    val sortedAsc = true

    val e = org.junit.jupiter.api.assertThrows<ValidationException> {
      configuredApiService.validateAndFetchData(reportId, reportVariantId, filters, selectedPage, pageSize, sortColumn, sortedAsc, authToken)
    }
    assertEquals(SyncDataApiService.INVALID_STATIC_OPTIONS_MESSAGE, e.message)
    verify(configuredApiRepository, times(0)).executeQuery(
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
    )
  }

  @Test
  fun `validateAndFetchData should throw an exception when having a prefix with fewer characters than the minimum length`() {
    val selectedPage = 1L
    val pageSize = 10L
    val sortColumn = "date"
    val sortedAsc = true

    val e = org.junit.jupiter.api.assertThrows<ValidationException> {
      configuredApiService.validateAndFetchData(
        reportId,
        reportVariantId,
        mapOf(),
        selectedPage,
        pageSize,
        sortColumn,
        sortedAsc,
        authToken,
        setOf("name"),
        "A",
      )
    }
    assertEquals(SyncDataApiService.INVALID_DYNAMIC_OPTIONS_MESSAGE, e.message)
    verify(configuredApiRepository, times(0)).executeQuery(
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
    )
  }

  @Test
  fun `validateAndCount should throw an exception when having invalid static options for a filter and no range filters`() {
    val filters = mapOf("direction" to "randomValue")

    val e = org.junit.jupiter.api.assertThrows<ValidationException> {
      configuredApiService.validateAndCount(reportId, reportVariantId, filters, authToken)
    }
    assertEquals(SyncDataApiService.INVALID_STATIC_OPTIONS_MESSAGE, e.message)
    verify(configuredApiRepository, times(0)).count(any(), any(), any(), any(), any(), any())
  }

  @Test
  fun `validateAndFetchData should throw an exception when having an invalid range filter`() {
    val filters = mapOf("date$RANGE_FILTER_START_SUFFIX" to "abc")
    val selectedPage = 1L
    val pageSize = 10L
    val sortColumn = "date"
    val sortedAsc = true

    val e = org.junit.jupiter.api.assertThrows<ValidationException> {
      configuredApiService.validateAndFetchData(reportId, reportVariantId, filters, selectedPage, pageSize, sortColumn, sortedAsc, authToken)
    }
    assertEquals("Invalid value abc for filter date. Cannot be parsed as a date.", e.message)
    verify(configuredApiRepository, times(0)).executeQuery(
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any(),
    )
  }

  @Test
  fun `validateAndCount should throw an exception when having an invalid range filter`() {
    val filters = mapOf("date$RANGE_FILTER_START_SUFFIX" to "abc")

    val e = org.junit.jupiter.api.assertThrows<ValidationException> {
      configuredApiService.validateAndCount(reportId, reportVariantId, filters, authToken)
    }
    assertEquals("Invalid value abc for filter date. Cannot be parsed as a date.", e.message)
    verify(configuredApiRepository, times(0)).count(any(), any(), any(), any(), any(), any())
  }

  @Test
  fun `should call the configuredApiRepository with the default sort column if none is provided`() {
    val filters = mapOf("direction" to "in", "date$RANGE_FILTER_START_SUFFIX" to "2023-04-25", "date$RANGE_FILTER_END_SUFFIX" to "2023-09-10")
    val repositoryFilters = listOf(Filter("direction", "in"), Filter("date", "2023-04-25", DATE_RANGE_START), Filter("date", "2023-09-10", DATE_RANGE_END))
    val selectedPage = 1L
    val pageSize = 10L
    val sortColumn = "date"
    val sortedAsc = true
    val dataSet = productDefinitionRepository.getProductDefinitions().first().dataset.first()
    val dataSourceName = productDefinitionRepository.getSingleReportProductDefinition(reportId, reportVariantId).datasource.name
    val singleReportProductDefinition =
      productDefinitionRepository.getSingleReportProductDefinition(reportId, reportVariantId)

    whenever(
      configuredApiRepository.executeQuery(
        query = dataSet.query,
        filters = repositoryFilters,
        selectedPage = selectedPage,
        pageSize = pageSize,
        sortColumn = sortColumn,
        sortedAsc = sortedAsc,
        policyEngineResult = policyEngineResult,
        dataSourceName = dataSourceName,
        reportFilter = singleReportProductDefinition.report.filter,
      ),
    ).thenReturn(expectedRepositoryResult)

    val actual = configuredApiService.validateAndFetchData(reportId, reportVariantId, filters, selectedPage, pageSize, null, sortedAsc, authToken)

    verify(configuredApiRepository, times(1)).executeQuery(
      query = dataSet.query,
      filters = repositoryFilters,
      selectedPage = selectedPage,
      pageSize = pageSize,
      sortColumn = sortColumn,
      sortedAsc = sortedAsc,
      policyEngineResult = policyEngineResult,
      dataSourceName = dataSourceName,
      reportFilter = singleReportProductDefinition.report.filter,
    )
    assertEquals(expectedServiceResult, actual)
  }

  @Test
  fun `should call the configuredApiRepository with no sort column if none is provided and there is no default`() {
    val dataSet =
      Dataset("datasetId", "datasetname", "redshift", "select *", Schema(listOf(SchemaField("9", ParameterType.String, display = "", filter = null))))
    val report = Report(
      id = "6",
      name = "7",
      created = LocalDateTime.MAX,
      version = "8",
      dataset = "\$ref:datasetId",
      render = RenderMethod.SVG,
      classification = "someClassification",
      specification = Specification(
        template = Template.List,
        field = listOf(
          ReportField(
            name = "\$ref:9",
            display = "Number 9",
            formula = "",
            visible = Visible.TRUE,
            sortable = true,
            defaultSort = false,
          ),
        ),
        section = null,
      ),
    )
    val policy = Policy(
      "caseload",
      PolicyType.ROW_LEVEL,
      listOf("TRUE"),
      listOf(Rule(Effect.PERMIT, emptyList())),
    )
    val productDefinition = ProductDefinition(
      id = "1",
      name = "2",
      metadata = MetaData(
        author = "3",
        owner = "4",
        version = "5",
      ),
      policy = listOf(policy),
      dataset = listOf(dataSet),
      report = listOf(
        report,
      ),
    )
    val expectedRepositoryResult = listOf(
      mapOf("9" to "1"),
    )
    val expectedServiceResult = listOf(
      mapOf("9" to "1"),
    )
    val productDefRepo = mock<ProductDefinitionRepository>()
    val configuredApiService = SyncDataApiService(productDefRepo, configuredApiRepository, productDefinitionTokenPolicyChecker, identifiedHelper)
    val dataSourceName = "name"

    whenever(productDefRepo.getProductDefinitions())
      .thenReturn(listOf(productDefinition))

    val singleReportProductDefinition = SingleReportProductDefinition(
      id = "1",
      name = "2",
      metadata = MetaData(
        author = "3",
        owner = "4",
        version = "5",
      ),
      policy = listOf(policy),
      reportDataset = dataSet,
      report = report,
      datasource = Datasource("id", dataSourceName),
      allDatasets = listOf(dataSet),
      allReports = emptyList(),
    )
    whenever(productDefRepo.getSingleReportProductDefinition(reportId, reportVariantId))
      .thenReturn(
        singleReportProductDefinition,
      )

    val selectedPage = 1L
    val pageSize = 10L
    val sortedAsc = true

    whenever(
      configuredApiRepository.executeQuery(
        query = dataSet.query,
        filters = emptyList(),
        selectedPage = selectedPage,
        pageSize = pageSize,
        sortColumn = null,
        sortedAsc = sortedAsc,
        policyEngineResult = POLICY_PERMIT,
        dataSourceName = dataSourceName,
        reportFilter = singleReportProductDefinition.report.filter,
      ),
    ).thenReturn(expectedRepositoryResult)

    val actual = configuredApiService.validateAndFetchData(reportId, reportVariantId, emptyMap(), selectedPage, pageSize, null, sortedAsc, authToken)

    verify(configuredApiRepository, times(1)).executeQuery(
      query = dataSet.query,
      filters = emptyList(),
      selectedPage = selectedPage,
      pageSize = pageSize,
      sortColumn = null,
      sortedAsc = sortedAsc,
      policyEngineResult = POLICY_PERMIT,
      dataSourceName = dataSourceName,
      reportFilter = singleReportProductDefinition.report.filter,
    )
    assertEquals(expectedServiceResult, actual)
  }

  @Test
  fun `should throw an exception when a filter is passed as a non boolean value when a boolean is expected`() {
    val exception = Assertions.assertThrows(ValidationException::class.java) {
      configuredApiService.validateAndFetchData(
        reportId,
        reportVariantId,
        mapOf("is_closed" to "in"),
        1L,
        10L,
        "date",
        true,
        authToken,
      )
    }
    verifyNoInteractions(configuredApiRepository)
    assertThat(exception).message().isEqualTo("Invalid value in for filter is_closed. Cannot be parsed as a boolean.")
  }

  @Test
  fun `should call the repository with Boolean Filter type for boolean filters`() {
    val filters = mapOf("is_closed" to "true", "date$RANGE_FILTER_START_SUFFIX" to "2023-04-25", "date$RANGE_FILTER_END_SUFFIX" to "2023-09-10")
    val repositoryFilters = listOf(Filter("is_closed", "true", BOOLEAN), Filter("date", "2023-04-25", DATE_RANGE_START), Filter("date", "2023-09-10", DATE_RANGE_END))
    val selectedPage = 1L
    val pageSize = 10L
    val sortColumn = "date"
    val sortedAsc = true
    val dataSet = productDefinitionRepository.getProductDefinitions().first().dataset.first()
    val dataSourceName = productDefinitionRepository.getSingleReportProductDefinition(reportId, reportVariantId).datasource.name
    val singleReportProductDefinition =
      productDefinitionRepository.getSingleReportProductDefinition(reportId, reportVariantId)

    whenever(
      configuredApiRepository.executeQuery(
        query = dataSet.query,
        filters = repositoryFilters,
        selectedPage = selectedPage,
        pageSize = pageSize,
        sortColumn = sortColumn,
        sortedAsc = sortedAsc,
        policyEngineResult = policyEngineResult,
        dataSourceName = dataSourceName,
        reportFilter = singleReportProductDefinition.report.filter,
      ),
    ).thenReturn(expectedRepositoryResult)

    val actual = configuredApiService.validateAndFetchData(reportId, reportVariantId, filters, selectedPage, pageSize, sortColumn, sortedAsc, authToken)

    verify(configuredApiRepository, times(1)).executeQuery(
      query = dataSet.query,
      filters = repositoryFilters,
      selectedPage = selectedPage,
      pageSize = pageSize,
      sortColumn = sortColumn,
      sortedAsc = sortedAsc,
      policyEngineResult = policyEngineResult,
      dataSourceName = dataSourceName,
      reportFilter = singleReportProductDefinition.report.filter,
    )
    assertEquals(expectedServiceResult, actual)
  }
}
