package uk.gov.justice.digital.hmpps.digitalprisonreportinglib.service

import jakarta.validation.ValidationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.jdbc.UncategorizedSQLException
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.config.DefinitionGsonConfig
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.DataApiSyncController.FiltersPrefix.RANGE_FILTER_END_SUFFIX
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.DataApiSyncController.FiltersPrefix.RANGE_FILTER_START_SUFFIX
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.Count
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.AthenaApiRepository
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.ConfiguredApiRepository
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.ConfiguredApiRepository.Filter
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.DatasetHelper
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.IsoLocalDateTimeTypeAdaptor
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.JsonFileProductDefinitionRepository
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.ProductDefinitionRepository
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.RedshiftDataApiRepository
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.RepositoryHelper.Companion.EXTERNAL_MOVEMENTS_PRODUCT_ID
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.RepositoryHelper.FilterType.BOOLEAN
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.RepositoryHelper.FilterType.DATE_RANGE_END
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.RepositoryHelper.FilterType.DATE_RANGE_START
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.RepositoryHelper.FilterType.STANDARD
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.FilterType
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.redshiftdata.StatementCancellationResponse
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.redshiftdata.StatementExecutionResponse
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.redshiftdata.StatementExecutionStatus
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.security.DprAuthAwareAuthenticationToken
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.service.model.Prompt
import java.sql.SQLException
import java.util.UUID

class AsyncDataApiServiceTest {
  private val productDefinitionRepository: ProductDefinitionRepository = JsonFileProductDefinitionRepository(
    listOf("productDefinition.json"),
    DefinitionGsonConfig().definitionGson(IsoLocalDateTimeTypeAdaptor()),
  )
  private val configuredApiRepository: ConfiguredApiRepository = mock<ConfiguredApiRepository>()
  private val redshiftDataApiRepository: RedshiftDataApiRepository = mock<RedshiftDataApiRepository>()
  private val athenaApiRepository: AthenaApiRepository = mock<AthenaApiRepository>()
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
  private val policyEngineResult = "(origin_code='WWI' AND lower(direction)='out') OR (destination_code='WWI' AND lower(direction)='in')"
  private val tableIdGenerator: TableIdGenerator = TableIdGenerator()
  private val datasetHelper: DatasetHelper = DatasetHelper()
  private val configuredApiService = AsyncDataApiService(productDefinitionRepository, configuredApiRepository, redshiftDataApiRepository, athenaApiRepository, tableIdGenerator, datasetHelper)

  @BeforeEach
  fun setup() {
    whenever(authToken.getCaseLoads()).thenReturn(listOf("WWI"))
  }

  @Test
  fun `validateAndExecuteStatementAsync should throw an exception for a mandatory filter with no value`() {
    val sortColumn = "date"
    val sortedAsc = true

    val e = org.junit.jupiter.api.assertThrows<ValidationException> {
      configuredApiService.validateAndExecuteStatementAsync(reportId, "last-year", emptyMap(), sortColumn, sortedAsc, authToken)
    }
    assertEquals(SyncDataApiService.MISSING_MANDATORY_FILTER_MESSAGE + " Date", e.message)
  }

  @Test
  fun `validateAndExecuteStatementAsync should throw an exception for a filter value that does not match the validation pattern`() {
    val sortColumn = "date"
    val sortedAsc = true
    val filters = mapOf(
      "date.start" to "2000-01-02",
      "origin" to "Invalid",
    )
    val e = org.junit.jupiter.api.assertThrows<ValidationException> {
      configuredApiService.validateAndExecuteStatementAsync(reportId, "last-year", filters, sortColumn, sortedAsc, authToken)
    }
    assertEquals(SyncDataApiService.FILTER_VALUE_DOES_NOT_MATCH_PATTERN_MESSAGE + " Invalid [A-Z]{3,3}", e.message)
  }

  @Test
  fun `should make the async call to the RedshiftDataApiRepository for datamart with all provided arguments when validateAndExecuteStatementAsync is called`() {
    val asyncDataApiService = AsyncDataApiService(productDefinitionRepository, configuredApiRepository, redshiftDataApiRepository, athenaApiRepository, tableIdGenerator, datasetHelper)
    val filters = mapOf("is_closed" to "true", "date$RANGE_FILTER_START_SUFFIX" to "2023-04-25", "date$RANGE_FILTER_END_SUFFIX" to "2023-09-10")
    val repositoryFilters = listOf(Filter("is_closed", "true", BOOLEAN), Filter("date", "2023-04-25", DATE_RANGE_START), Filter("date", "2023-09-10", DATE_RANGE_END))
    val sortColumn = "date"
    val sortedAsc = true
    val productDefinition = productDefinitionRepository.getProductDefinitions().first()
    val singleReportProductDefinition = productDefinitionRepository.getSingleReportProductDefinition(productDefinition.id, productDefinition.report.first().id)
    val executionId = UUID.randomUUID().toString()
    val tableId = executionId.replace("-", "_")
    val statementExecutionResponse = StatementExecutionResponse(tableId, executionId)
    whenever(
      redshiftDataApiRepository.executeQueryAsync(
        productDefinition = singleReportProductDefinition,
        filters = repositoryFilters,
        sortColumn = sortColumn,
        sortedAsc = sortedAsc,
        policyEngineResult = policyEngineResult,
        prompts = emptyList(),
        userToken = authToken,
      ),
    ).thenReturn(statementExecutionResponse)

    val actual = asyncDataApiService.validateAndExecuteStatementAsync(reportId, reportVariantId, filters, sortColumn, sortedAsc, authToken)

    verify(redshiftDataApiRepository, times(1)).executeQueryAsync(
      productDefinition = singleReportProductDefinition,
      filters = repositoryFilters,
      sortColumn = sortColumn,
      sortedAsc = sortedAsc,
      policyEngineResult = policyEngineResult,
      prompts = emptyList(),
      userToken = authToken,
    )
    assertEquals(statementExecutionResponse, actual)
  }

  @ParameterizedTest
  @CsvSource(
    "nomis_db, nomis, productDefinitionNomis.json",
    "bodmis_db, bodmis, productDefinitionBodmis.json",
  )
  fun `should make the async call to the AthenaApiRepository for nomis and bodmis with all provided arguments when validateAndExecuteStatementAsync is called`(database: String, catalog: String, definitionFile: String) {
    val productDefinitionRepository: ProductDefinitionRepository = JsonFileProductDefinitionRepository(
      listOf(definitionFile),
      DefinitionGsonConfig().definitionGson(IsoLocalDateTimeTypeAdaptor()),
    )
    val asyncDataApiService = AsyncDataApiService(productDefinitionRepository, configuredApiRepository, redshiftDataApiRepository, athenaApiRepository, tableIdGenerator, datasetHelper)
    val filters = mapOf("is_closed" to "true", "date$RANGE_FILTER_START_SUFFIX" to "2023-04-25", "date$RANGE_FILTER_END_SUFFIX" to "2023-09-10")
    val repositoryFilters = listOf(Filter("is_closed", "true", BOOLEAN), Filter("date", "2023-04-25", DATE_RANGE_START), Filter("date", "2023-09-10", DATE_RANGE_END))
    val sortColumn = "date"
    val sortedAsc = true
    val productDefinition = productDefinitionRepository.getProductDefinitions().first()
    val singleReportProductDefinition = productDefinitionRepository.getSingleReportProductDefinition(productDefinition.id, productDefinition.report.first().id)
    val executionId = UUID.randomUUID().toString()
    val tableId = executionId.replace("-", "_")
    val statementExecutionResponse = StatementExecutionResponse(tableId, executionId)
    whenever(
      athenaApiRepository.executeQueryAsync(
        productDefinition = singleReportProductDefinition,
        filters = repositoryFilters,
        sortColumn = sortColumn,
        sortedAsc = sortedAsc,
        policyEngineResult = policyEngineResult,
        prompts = emptyList(),
        userToken = authToken,
      ),
    ).thenReturn(statementExecutionResponse)

    val actual = asyncDataApiService.validateAndExecuteStatementAsync(reportId, reportVariantId, filters, sortColumn, sortedAsc, authToken)

    verify(athenaApiRepository, times(1)).executeQueryAsync(
      productDefinition = singleReportProductDefinition,
      filters = repositoryFilters,
      sortColumn = sortColumn,
      sortedAsc = sortedAsc,
      policyEngineResult = policyEngineResult,
      prompts = emptyList(),
      userToken = authToken,
    )
    verifyNoInteractions(redshiftDataApiRepository)
    assertEquals(statementExecutionResponse, actual)
  }

  @Test
  fun `should call the RedshiftDataApiRepository for datamart with the statement execution ID when getStatementStatus is called`() {
    val asyncDataApiService = AsyncDataApiService(productDefinitionRepository, configuredApiRepository, redshiftDataApiRepository, athenaApiRepository, tableIdGenerator, datasetHelper)
    val statementId = "statementId"
    val status = "FINISHED"
    val duration = 278109264L
    val resultRows = 10L
    val resultSize = 100L
    val statementExecutionStatus = StatementExecutionStatus(
      status,
      duration,
      resultRows,
      resultSize,
    )
    whenever(
      redshiftDataApiRepository.getStatementStatus(statementId),
    ).thenReturn(statementExecutionStatus)

    val actual = asyncDataApiService.getStatementStatus(statementId, "external-movements", "last-month")
    verify(redshiftDataApiRepository, times(1)).getStatementStatus(statementId)
    verifyNoInteractions(athenaApiRepository)
    assertEquals(statementExecutionStatus, actual)
  }

  @ParameterizedTest
  @ValueSource(strings = ["productDefinitionNomis.json", "productDefinitionBodmis.json"])
  fun `should call the AthenaApiRepository for nomis and bodmis with the statement execution ID when getStatementStatus is called`(definitionFile: String) {
    val productDefinitionRepository: ProductDefinitionRepository = JsonFileProductDefinitionRepository(
      listOf(definitionFile),
      DefinitionGsonConfig().definitionGson(IsoLocalDateTimeTypeAdaptor()),
    )
    val asyncDataApiService = AsyncDataApiService(productDefinitionRepository, configuredApiRepository, redshiftDataApiRepository, athenaApiRepository, tableIdGenerator, datasetHelper)
    val statementId = "statementId"
    val status = "FINISHED"
    val duration = 278109264L
    val resultRows = 10L
    val resultSize = 100L
    val statementExecutionStatus = StatementExecutionStatus(
      status,
      duration,
      resultRows,
      resultSize,
    )
    whenever(
      athenaApiRepository.getStatementStatus(statementId),
    ).thenReturn(statementExecutionStatus)

    val actual = asyncDataApiService.getStatementStatus(statementId, "external-movements", "last-month")
    verify(athenaApiRepository, times(1)).getStatementStatus(statementId)
    verifyNoInteractions(redshiftDataApiRepository)
    assertEquals(statementExecutionStatus, actual)
  }

  @Test
  fun `should call the RedshiftDataApiRepository for datamart with the statement execution ID when cancelStatementExecution is called`() {
    val asyncDataApiService = AsyncDataApiService(productDefinitionRepository, configuredApiRepository, redshiftDataApiRepository, athenaApiRepository, tableIdGenerator, datasetHelper)
    val statementId = "statementId"
    val statementCancellationResponse = StatementCancellationResponse(
      true,
    )
    whenever(
      redshiftDataApiRepository.cancelStatementExecution(statementId),
    ).thenReturn(statementCancellationResponse)

    val actual = asyncDataApiService.cancelStatementExecution(statementId, "external-movements", "last-month")
    verify(redshiftDataApiRepository, times(1)).cancelStatementExecution(statementId)
    verifyNoInteractions(athenaApiRepository)
    assertEquals(statementCancellationResponse, actual)
  }

  @ParameterizedTest
  @ValueSource(strings = ["productDefinitionNomis.json", "productDefinitionBodmis.json"])
  fun `should call the AthenaApiRepository for nomis and bodmis with the statement execution ID when cancelStatementExecution is called`(definitionFile: String) {
    val productDefinitionRepository: ProductDefinitionRepository = JsonFileProductDefinitionRepository(
      listOf(definitionFile),
      DefinitionGsonConfig().definitionGson(IsoLocalDateTimeTypeAdaptor()),
    )
    val asyncDataApiService = AsyncDataApiService(productDefinitionRepository, configuredApiRepository, redshiftDataApiRepository, athenaApiRepository, tableIdGenerator, datasetHelper)
    val statementId = "statementId"
    val statementCancellationResponse = StatementCancellationResponse(true)
    whenever(
      athenaApiRepository.cancelStatementExecution(statementId),
    ).thenReturn(statementCancellationResponse)

    val actual = asyncDataApiService.cancelStatementExecution(statementId, "external-movements", "last-month")
    verify(athenaApiRepository, times(1)).cancelStatementExecution(statementId)
    verifyNoInteractions(redshiftDataApiRepository)
    assertEquals(statementCancellationResponse, actual)
  }

  @Test
  fun `validateAndExecuteStatementAsync should not fail validation for filters which were converted from DPD parameters and convert these filters to prompts`() {
    val productDefinitionRepository: ProductDefinitionRepository = JsonFileProductDefinitionRepository(
      listOf("productDefinitionWithParameters.json"),
      DefinitionGsonConfig().definitionGson(IsoLocalDateTimeTypeAdaptor()),
    )
    val productDefinition = productDefinitionRepository.getProductDefinitions().first()
    val singleReportProductDefinition = productDefinitionRepository.getSingleReportProductDefinition(productDefinition.id, productDefinition.report.first().id)
    val asyncDataApiService = AsyncDataApiService(productDefinitionRepository, configuredApiRepository, redshiftDataApiRepository, athenaApiRepository, tableIdGenerator, datasetHelper)
    val parameterName = "prisoner_number"
    val parameterValue = "somePrisonerNumber"
    val filters = mapOf(
      "origin" to "someOrigin",
      parameterName to parameterValue,
    )
    val repositoryFilters = listOf(Filter("origin", "someOrigin", STANDARD))
    val prompts = listOf(Prompt(parameterName, parameterValue, FilterType.Text))
    val sortColumn = "date"
    val sortedAsc = true
    val executionId = UUID.randomUUID().toString()
    val tableId = executionId.replace("-", "_")
    val statementExecutionResponse = StatementExecutionResponse(tableId, executionId)
    whenever(
      redshiftDataApiRepository.executeQueryAsync(
        productDefinition = singleReportProductDefinition,
        filters = repositoryFilters,
        sortColumn = sortColumn,
        sortedAsc = sortedAsc,
        policyEngineResult = policyEngineResult,
        prompts = prompts,
        userToken = authToken,
      ),
    ).thenReturn(statementExecutionResponse)

    val actual = asyncDataApiService.validateAndExecuteStatementAsync("external-movements-with-parameters", reportVariantId, filters, sortColumn, sortedAsc, authToken)

    verify(redshiftDataApiRepository, times(1)).executeQueryAsync(
      productDefinition = singleReportProductDefinition,
      filters = repositoryFilters,
      sortColumn = sortColumn,
      sortedAsc = sortedAsc,
      policyEngineResult = policyEngineResult,
      prompts = prompts,
      userToken = authToken,
    )
    assertEquals(statementExecutionResponse, actual)
  }

  @Test
  fun `should call the repository with all provided arguments when getStatementResult is called`() {
    val tableId = TableIdGenerator().generateNewExternalTableId()
    val selectedPage = 1L
    val pageSize = 20L
    whenever(
      redshiftDataApiRepository.getPaginatedExternalTableResult(tableId, selectedPage, pageSize),
    ).thenReturn(expectedRepositoryResult)

    val actual = configuredApiService.getStatementResult(
      tableId,
      reportId,
      reportVariantId,
      selectedPage = selectedPage,
      pageSize = pageSize,
    )

    assertEquals(expectedServiceResult, actual)
  }

  @Test
  fun `getStatementResult should apply formulas to the rows returned by the repository`() {
    val selectedPage = 1L
    val pageSize = 20L
    val expectedRepositoryResult = listOf(
      mapOf("PRISONNUMBER" to "1", "NAME" to "FirstName", "ORIGIN" to "OriginLocation", "ORIGIN_CODE" to "abc"),
    )
    val expectedServiceResult = listOf(
      mapOf("prisonNumber" to "1", "name" to "FirstName", "origin" to "OriginLocation", "origin_code" to "OriginLocation"),
    )
    val productDefinitionRepository: ProductDefinitionRepository = JsonFileProductDefinitionRepository(
      listOf("productDefinitionWithFormula.json"),
      DefinitionGsonConfig().definitionGson(IsoLocalDateTimeTypeAdaptor()),
    )
    val asyncDataApiService = AsyncDataApiService(productDefinitionRepository, configuredApiRepository, redshiftDataApiRepository, athenaApiRepository, tableIdGenerator, datasetHelper)
    val executionID = UUID.randomUUID().toString()
    whenever(
      redshiftDataApiRepository.getPaginatedExternalTableResult(executionID, selectedPage, pageSize),
    ).thenReturn(expectedRepositoryResult)

    val actual = asyncDataApiService.getStatementResult(
      tableId = executionID,
      reportId = reportId,
      reportVariantId = reportVariantId,
      selectedPage = selectedPage,
      pageSize = pageSize,
    )

    assertEquals(expectedServiceResult, actual)
  }

  @Test
  fun `should call the repository with all provided arguments when getSummaryResult is called`() {
    val tableId = TableIdGenerator().generateNewExternalTableId()
    val summaryId = "summaryId"
    whenever(
      redshiftDataApiRepository.getFullExternalTableResult(tableIdGenerator.getTableSummaryId(tableId, summaryId)),
    ).thenReturn(listOf(mapOf("TOTAL" to 1)))

    val actual = configuredApiService.getSummaryResult(
      tableId,
      summaryId,
      reportId,
      reportVariantId,
    )

    assertEquals(listOf(mapOf("total" to 1)), actual)
  }

  @Test
  fun `should create and query summary table when it doesn't exist`() {
    val tableId = TableIdGenerator().generateNewExternalTableId()
    val summaryId = "summaryId"
    whenever(
      redshiftDataApiRepository.getFullExternalTableResult(tableIdGenerator.getTableSummaryId(tableId, summaryId)),
    )
      .thenThrow(UncategorizedSQLException("EntityNotFoundException from glue - Entity Not Found", "", SQLException()))
      .thenReturn(listOf(mapOf("TOTAL" to 1)))

    val actual = configuredApiService.getSummaryResult(
      tableId,
      summaryId,
      reportId,
      reportVariantId,
    )

    assertEquals(listOf(mapOf("total" to 1)), actual)
    verify(redshiftDataApiRepository, times(2)).getFullExternalTableResult(any(), anyOrNull())
    verify(configuredApiRepository).createSummaryTable(any(), any(), any(), any())
  }

  @Test
  fun `should call the repository with all provided arguments when count is called`() {
    val tableId = "123"
    val expectedRepositoryResult = 5L
    whenever(
      redshiftDataApiRepository.count(tableId),
    ).thenReturn(expectedRepositoryResult)

    val actual = configuredApiService.count(tableId)

    assertEquals(Count(expectedRepositoryResult), actual)
  }
}