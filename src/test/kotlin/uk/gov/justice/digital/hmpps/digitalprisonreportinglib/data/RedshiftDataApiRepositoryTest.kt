package uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import software.amazon.awssdk.services.redshiftdata.RedshiftDataClient
import software.amazon.awssdk.services.redshiftdata.model.DescribeStatementRequest
import software.amazon.awssdk.services.redshiftdata.model.DescribeStatementResponse
import software.amazon.awssdk.services.redshiftdata.model.ExecuteStatementRequest
import software.amazon.awssdk.services.redshiftdata.model.ExecuteStatementResponse
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.ConfiguredApiRepository.Filter
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.ConfiguredApiRepositoryTest.Companion.REPOSITORY_TEST_POLICY_ENGINE_RESULT
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.ConfiguredApiRepositoryTest.Companion.REPOSITORY_TEST_QUERY
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.RepositoryHelper.Companion.DEFAULT_REPORT_CTE
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.RepositoryHelper.Companion.FALSE_WHERE_CLAUSE
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.RepositoryHelper.Companion.REPORT_
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.RepositoryHelper.Companion.TRUE_WHERE_CLAUSE
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.RepositoryHelper.FilterType
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Dataset
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Datasource
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Report
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.ReportFilter
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.SingleDashboardProductDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.SingleReportProductDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.policyengine.Policy.PolicyResult
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.redshiftdata.StatementExecutionResponse
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.redshiftdata.StatementExecutionStatus
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.service.TableIdGenerator
import java.time.LocalDateTime

class RedshiftDataApiRepositoryTest {

  companion object {
    fun sqlStatement(
      tableId: String,
      prefilter: ReportFilter? =
        ReportFilter(name = REPORT_, query = DEFAULT_REPORT_CTE),
    ) =
      """
                  CREATE EXTERNAL TABLE reports.$tableId 
                  STORED AS parquet 
                  LOCATION 's3://dpr-working-development/reports/$tableId/' 
                  AS ( 
                  WITH dataset_ AS (SELECT prisoners.number AS prisonNumber,CONCAT(CONCAT(prisoners.lastname, ', '), substring(prisoners.firstname, 1, 1)) AS name,movements.time AS date,movements.direction,movements.type,movements.origin,movements.origin_code,movements.destination,movements.destination_code,movements.reason
        FROM datamart.domain.movement_movement as movements
        JOIN datamart.domain.prisoner_prisoner as prisoners
        ON movements.prisoner = prisoners.id),${prefilter?.query},policy_ AS (SELECT * FROM ${prefilter?.name} WHERE (origin_code IN ('HEI','LWSTMC','NSI','LCI','TCI') AND lower(direction)='out') OR (destination_code IN ('HEI','LWSTMC','NSI','LCI','TCI') AND lower(direction)='in')),filter_ AS (SELECT * FROM policy_ WHERE lower(direction) = 'out')
        SELECT *
                  FROM filter_ ORDER BY date asc
                  );
                  
      """.trimIndent()

    val movementPrisoner1 = mapOf("id" to "171034.12", "prisoner" to 171034L, "date" to LocalDateTime.of(2010, 12, 17, 0, 0, 0), "time" to LocalDateTime.of(2010, 12, 17, 7, 12, 0), "direction" to "OUT", "type" to "CRT", "origin_code" to "LFI", "origin" to "LANCASTER FARMS (HMPYOI)", "destination_code" to "STHEMC", "destination" to "St. Helens Magistrates Court", "reason" to "Production (Sentence/Civil Custody)")
    val movementPrisoner2 = mapOf("id" to "227482.1", "prisoner" to 227482L, "date" to LocalDateTime.of(2010, 12, 8, 0, 0, 0), "time" to LocalDateTime.of(2010, 12, 8, 10, 8, 0), "direction" to "IN", "type" to "ADM", "origin_code" to "IMM", "origin" to "Immigration", "destination_code" to "HRI", "destination" to "Haslar Immigration Removal Centre", "reason" to "Detained Immigration Act 71 -Wait Deport")
    private const val REDSHIFT_DATA_API_DB: String = "redshiftDB"
    private const val REDSHIFT_DATA_API_CLUSTER_ID: String = "redshiftClusterId"
    private const val REDSHIFT_DATA_API_SECRET_ARN: String = "redshiftSecretArn"
    private const val TABLE_ID = "_a6227417_bdac_40bb_bc81_49c750daacd7"
    private const val EXECUTION_ID = "someId"
  }

  private val redshiftDataClient = mock<RedshiftDataClient>()
  private val tableIdGenerator = mock<TableIdGenerator>()
  private val productDefinition = mock<SingleReportProductDefinition>()
  private val datasource = mock<Datasource>()
  private val dataset = mock<Dataset>()
  private val executeStatementResponse = mock<ExecuteStatementResponse>()
  private val report = mock<Report>()
  private val identifiedHelper = IdentifiedHelper()
  private val redShiftSummaryTableHelper = mock<RedShiftSummaryTableHelper>()

  @BeforeEach
  fun setup() {
    whenever(tableIdGenerator.generateNewExternalTableId()).thenReturn(TABLE_ID)
    whenever(executeStatementResponse.id()).thenReturn(EXECUTION_ID)
    whenever(productDefinition.reportDataset).thenReturn(dataset)
    whenever(productDefinition.report).thenReturn(report)
    whenever(productDefinition.datasource).thenReturn(datasource)
    whenever(dataset.query).thenReturn(REPOSITORY_TEST_QUERY)
  }

  @Test
  fun `executeQueryAsync should call the redshift data api with the correct query and return the execution id and table id`() {
    val redshiftDataApiRepository = RedshiftDataApiRepository(
      redshiftDataClient,
      tableIdGenerator,
      identifiedHelper,
      redShiftSummaryTableHelper,
      REDSHIFT_DATA_API_DB,
      REDSHIFT_DATA_API_CLUSTER_ID,
      REDSHIFT_DATA_API_SECRET_ARN,
    )
    val executeStatementRequest = ExecuteStatementRequest.builder()
      .clusterIdentifier(REDSHIFT_DATA_API_CLUSTER_ID)
      .database(REDSHIFT_DATA_API_DB)
      .secretArn(REDSHIFT_DATA_API_SECRET_ARN)
      .sql(sqlStatement(TABLE_ID))
      .build()

    whenever(
      redshiftDataClient.executeStatement(
        executeStatementRequest,
      ),
    ).thenReturn(executeStatementResponse)

    val actual = redshiftDataApiRepository.executeQueryAsync(
      productDefinition = productDefinition,
      filters = listOf(Filter("direction", "out")),
      sortColumn = "date",
      sortedAsc = true,
      policyEngineResult = REPOSITORY_TEST_POLICY_ENGINE_RESULT,
    )

    assertEquals(StatementExecutionResponse(TABLE_ID, EXECUTION_ID), actual)
  }

  @Test
  fun `executeQueryAsync should call the redshift data api with the correct query including all filters and return the execution id and table id`() {
    val startDate = "2024-02-16"
    val endDate = "2024-02-17"
    val directionFilter = Filter("direction", "out")
    val startDateFilter = Filter("date", startDate, FilterType.DATE_RANGE_START)
    val endDateFilter = Filter("date", endDate, FilterType.DATE_RANGE_END)
    val nameDynamicFilter = Filter("name", "LastNa", FilterType.DYNAMIC)
    val executionId = "someId"
    val sqlStatement =
      """          CREATE EXTERNAL TABLE reports.$TABLE_ID 
          STORED AS parquet 
          LOCATION 's3://dpr-working-development/reports/$TABLE_ID/' 
          AS ( 
          WITH dataset_ AS (SELECT prisoners.number AS prisonNumber,CONCAT(CONCAT(prisoners.lastname, ', '), substring(prisoners.firstname, 1, 1)) AS name,movements.time AS date,movements.direction,movements.type,movements.origin,movements.origin_code,movements.destination,movements.destination_code,movements.reason
FROM datamart.domain.movement_movement as movements
JOIN datamart.domain.prisoner_prisoner as prisoners
ON movements.prisoner = prisoners.id),$DEFAULT_REPORT_CTE,policy_ AS (SELECT * FROM $REPORT_ WHERE (origin_code IN ('HEI','LWSTMC','NSI','LCI','TCI') AND lower(direction)='out') OR (destination_code IN ('HEI','LWSTMC','NSI','LCI','TCI') AND lower(direction)='in')),filter_ AS (SELECT * FROM policy_ WHERE date >= CAST('$startDate' AS timestamp) AND date < (CAST('$endDate' AS timestamp) + INTERVAL '1' day) AND lower(direction) = 'out' AND name ILIKE 'LastNa%')
SELECT *
          FROM filter_ ORDER BY date asc
          );
          
      """.trimIndent()
    val redshiftDataApiRepository = RedshiftDataApiRepository(
      redshiftDataClient,
      tableIdGenerator,
      identifiedHelper,
      redShiftSummaryTableHelper,
      REDSHIFT_DATA_API_DB,
      REDSHIFT_DATA_API_CLUSTER_ID,
      REDSHIFT_DATA_API_SECRET_ARN,
    )
    val executeStatementRequest = ExecuteStatementRequest.builder()
      .clusterIdentifier(REDSHIFT_DATA_API_CLUSTER_ID)
      .database(REDSHIFT_DATA_API_DB)
      .secretArn(REDSHIFT_DATA_API_SECRET_ARN)
      .sql(sqlStatement)
      .build()

    whenever(
      redshiftDataClient.executeStatement(
        any<ExecuteStatementRequest>(),
      ),
    ).thenReturn(executeStatementResponse)

    val actual = redshiftDataApiRepository.executeQueryAsync(
      productDefinition = productDefinition,
      filters = listOf(
        startDateFilter,
        endDateFilter,
        directionFilter,
        nameDynamicFilter,
      ),
      sortColumn = "date",
      sortedAsc = true,
      policyEngineResult = REPOSITORY_TEST_POLICY_ENGINE_RESULT,
    )

    assertEquals(StatementExecutionResponse(TABLE_ID, executionId), actual)
    verify(redshiftDataClient).executeStatement(executeStatementRequest)
  }

  @Test
  fun `executeQueryAsync for a dashboard should call the redshift data api with the correct query and return the execution id and table id`() {
    val productDefinition = mock<SingleDashboardProductDefinition>()
    val dataset = mock<Dataset>()
    whenever(productDefinition.dashboardDataset).thenReturn(dataset)
    whenever(dataset.query).thenReturn("SELECT establishment_id, has_ethnicity, ethnicity_is_missing FROM datamart.metrics.data_quality")
    whenever(productDefinition.dashboard).thenReturn(mock())
    whenever(productDefinition.datasource).thenReturn(datasource)
    val policyEngineResult = "(establishment_id='ABC')"
    val executionId = "someId"
    val sqlStatement =
      """          CREATE EXTERNAL TABLE reports.$TABLE_ID 
          STORED AS parquet 
          LOCATION 's3://dpr-working-development/reports/$TABLE_ID/' 
          AS ( 
            WITH dataset_ AS (SELECT establishment_id, has_ethnicity, ethnicity_is_missing FROM datamart.metrics.data_quality),report_ AS (SELECT * FROM dataset_),policy_ AS (SELECT * FROM report_ WHERE (establishment_id='ABC')),filter_ AS (SELECT * FROM policy_ WHERE 1=1)
SELECT *
          FROM filter_ 
          );
      """.trimIndent()
    val redshiftDataApiRepository = RedshiftDataApiRepository(
      redshiftDataClient,
      tableIdGenerator,
      identifiedHelper,
      redShiftSummaryTableHelper,
      REDSHIFT_DATA_API_DB,
      REDSHIFT_DATA_API_CLUSTER_ID,
      REDSHIFT_DATA_API_SECRET_ARN,
    )
    val executeStatementRequest = ExecuteStatementRequest.builder()
      .clusterIdentifier(REDSHIFT_DATA_API_CLUSTER_ID)
      .database(REDSHIFT_DATA_API_DB)
      .secretArn(REDSHIFT_DATA_API_SECRET_ARN)
      .sql(sqlStatement)
      .build()

    whenever(
      redshiftDataClient.executeStatement(
        eq(executeStatementRequest),
      ),
    ).thenReturn(executeStatementResponse)

    val actual = redshiftDataApiRepository.executeQueryAsync(
      productDefinition = productDefinition,
      policyEngineResult = policyEngineResult,
      filters = emptyList(),
    )

    assertEquals(StatementExecutionResponse(TABLE_ID, executionId), actual)
    verify(redshiftDataClient).executeStatement(executeStatementRequest)
  }

  @Test
  fun `getStatementStatus should call the redshift data api with the correct statement ID and return the StatementExecutionStatus`() {
    val redshiftDataClient = mock<RedshiftDataClient>()
    val redshiftDataApiRepository = RedshiftDataApiRepository(
      redshiftDataClient,
      tableIdGenerator,
      identifiedHelper,
      redShiftSummaryTableHelper,
      REDSHIFT_DATA_API_DB,
      REDSHIFT_DATA_API_CLUSTER_ID,
      REDSHIFT_DATA_API_SECRET_ARN,
    )
    val statementId = "statementId"
    val status = "FINISHED"
    val duration = 278109264L
    val query = "SELECT * FROM datamart.domain.movement_movement limit 10;"
    val resultRows = 0L
    val resultSize = 0L
    val executeStatementResponse = DescribeStatementResponse.builder()
      .status(status)
      .duration(duration)
      .queryString(query)
      .resultRows(resultRows)
      .resultSize(resultSize)
      .build()

    whenever(
      redshiftDataClient.describeStatement(
        DescribeStatementRequest.builder()
          .id(statementId)
          .build(),
      ),
    ).thenReturn(executeStatementResponse)

    val expected = StatementExecutionStatus(
      status,
      duration,
      resultRows,
      resultSize,
    )
    val actual = redshiftDataApiRepository.getStatementStatus(statementId)

    assertEquals(expected, actual)
  }

  @Test
  fun `executeQueryAsync should call the redshift data api and not error when no filters are provided`() {
    val redshiftDataApiRepository = RedshiftDataApiRepository(
      redshiftDataClient,
      tableIdGenerator,
      identifiedHelper,
      redShiftSummaryTableHelper,
      REDSHIFT_DATA_API_DB,
      REDSHIFT_DATA_API_CLUSTER_ID,
      REDSHIFT_DATA_API_SECRET_ARN,
    )
    val finalQuery =
      """
                  CREATE EXTERNAL TABLE reports.$TABLE_ID 
                  STORED AS parquet 
                  LOCATION 's3://dpr-working-development/reports/$TABLE_ID/' 
                  AS ( 
                  WITH dataset_ AS (SELECT prisoners.number AS prisonNumber,CONCAT(CONCAT(prisoners.lastname, ', '), substring(prisoners.firstname, 1, 1)) AS name,movements.time AS date,movements.direction,movements.type,movements.origin,movements.origin_code,movements.destination,movements.destination_code,movements.reason
        FROM datamart.domain.movement_movement as movements
        JOIN datamart.domain.prisoner_prisoner as prisoners
        ON movements.prisoner = prisoners.id),$DEFAULT_REPORT_CTE,policy_ AS (SELECT * FROM $REPORT_ WHERE (origin_code IN ('HEI','LWSTMC','NSI','LCI','TCI') AND lower(direction)='out') OR (destination_code IN ('HEI','LWSTMC','NSI','LCI','TCI') AND lower(direction)='in')),filter_ AS (SELECT * FROM policy_ WHERE $TRUE_WHERE_CLAUSE)
        SELECT *
                  FROM filter_ ORDER BY date asc
                  );
                  
      """.trimIndent()

    val executeStatementRequest = ExecuteStatementRequest.builder()
      .clusterIdentifier(REDSHIFT_DATA_API_CLUSTER_ID)
      .database(REDSHIFT_DATA_API_DB)
      .secretArn(REDSHIFT_DATA_API_SECRET_ARN)
      .sql(finalQuery)
      .build()

    whenever(
      redshiftDataClient.executeStatement(
        executeStatementRequest,
      ),
    ).thenReturn(executeStatementResponse)

    val actual = redshiftDataApiRepository.executeQueryAsync(
      productDefinition = productDefinition,
      filters = emptyList(),
      sortColumn = "date",
      sortedAsc = true,
      policyEngineResult = REPOSITORY_TEST_POLICY_ENGINE_RESULT,
    )

    assertEquals(StatementExecutionResponse(TABLE_ID, EXECUTION_ID), actual)
  }

  @ParameterizedTest
  @CsvSource(
    "${PolicyResult.POLICY_PERMIT}, $TRUE_WHERE_CLAUSE",
    "${PolicyResult.POLICY_DENY}, $FALSE_WHERE_CLAUSE",
  )
  fun `executeQueryAsync should call the redshift data api with the converted sql where clause for permit and denied policies`(policyEngineResult: String, queryWhereClause: String) {
    val redshiftDataApiRepository = RedshiftDataApiRepository(
      redshiftDataClient,
      tableIdGenerator,
      identifiedHelper,
      redShiftSummaryTableHelper,
      REDSHIFT_DATA_API_DB,
      REDSHIFT_DATA_API_CLUSTER_ID,
      REDSHIFT_DATA_API_SECRET_ARN,
    )
    val finalQuery =
      """
                  CREATE EXTERNAL TABLE reports.$TABLE_ID 
                  STORED AS parquet 
                  LOCATION 's3://dpr-working-development/reports/$TABLE_ID/' 
                  AS ( 
                  WITH dataset_ AS (SELECT prisoners.number AS prisonNumber,CONCAT(CONCAT(prisoners.lastname, ', '), substring(prisoners.firstname, 1, 1)) AS name,movements.time AS date,movements.direction,movements.type,movements.origin,movements.origin_code,movements.destination,movements.destination_code,movements.reason
        FROM datamart.domain.movement_movement as movements
        JOIN datamart.domain.prisoner_prisoner as prisoners
        ON movements.prisoner = prisoners.id),$DEFAULT_REPORT_CTE,policy_ AS (SELECT * FROM $REPORT_ WHERE $queryWhereClause),filter_ AS (SELECT * FROM policy_ WHERE $TRUE_WHERE_CLAUSE)
        SELECT *
                  FROM filter_ ORDER BY date asc
                  );
                  
      """.trimIndent()

    val executeStatementRequest = ExecuteStatementRequest.builder()
      .clusterIdentifier(REDSHIFT_DATA_API_CLUSTER_ID)
      .database(REDSHIFT_DATA_API_DB)
      .secretArn(REDSHIFT_DATA_API_SECRET_ARN)
      .sql(finalQuery)
      .build()

    whenever(
      redshiftDataClient.executeStatement(
        executeStatementRequest,
      ),
    ).thenReturn(executeStatementResponse)

    val actual = redshiftDataApiRepository.executeQueryAsync(
      productDefinition = productDefinition,
      filters = emptyList(),
      sortColumn = "date",
      sortedAsc = true,
      policyEngineResult = policyEngineResult,
    )

    assertEquals(StatementExecutionResponse(TABLE_ID, EXECUTION_ID), actual)
  }

  @Test
  fun `executeQueryAsync should call the redshift data api with the correct query when a report filter exists and return the execution id and table id`() {
    val redshiftDataApiRepository = RedshiftDataApiRepository(
      redshiftDataClient,
      tableIdGenerator,
      identifiedHelper,
      redShiftSummaryTableHelper,
      REDSHIFT_DATA_API_DB,
      REDSHIFT_DATA_API_CLUSTER_ID,
      REDSHIFT_DATA_API_SECRET_ARN,
    )
    val reportQuery = "report_ AS (SELECT * FROM dataset_ WHERE 1=1)"
    val executeStatementRequest = ExecuteStatementRequest.builder()
      .clusterIdentifier(REDSHIFT_DATA_API_CLUSTER_ID)
      .database(REDSHIFT_DATA_API_DB)
      .secretArn(REDSHIFT_DATA_API_SECRET_ARN)
      .sql(sqlStatement(tableId = TABLE_ID, prefilter = ReportFilter(name = REPORT_, query = reportQuery)))
      .build()

    whenever(
      redshiftDataClient.executeStatement(
        executeStatementRequest,
      ),
    ).thenReturn(executeStatementResponse)

    whenever(report.filter).thenReturn(ReportFilter(name = REPORT_, query = reportQuery))

    val actual = redshiftDataApiRepository.executeQueryAsync(
      productDefinition = productDefinition,
      filters = listOf(Filter("direction", "out")),
      sortColumn = "date",
      sortedAsc = true,
      policyEngineResult = REPOSITORY_TEST_POLICY_ENGINE_RESULT,
    )

    assertEquals(StatementExecutionResponse(TABLE_ID, EXECUTION_ID), actual)
  }

  @Test
  fun `getStatementResult should make a paginated JDBC call and return the existing results`() {
    val jdbcTemplate = mock<NamedParameterJdbcTemplate>()
    val redshiftDataApiRepository = RedshiftDataApiRepository(
      redshiftDataClient,
      tableIdGenerator,
      identifiedHelper,
      redShiftSummaryTableHelper,
      REDSHIFT_DATA_API_DB,
      REDSHIFT_DATA_API_CLUSTER_ID,
      REDSHIFT_DATA_API_SECRET_ARN,
    )
    val selectedPage = 1L
    val pageSize = 10L
    val expected = listOf<Map<String, Any?>>(movementPrisoner1, movementPrisoner2)

    whenever(jdbcTemplate.queryForList(any(), any<MapSqlParameterSource>())).thenReturn(expected)

    val actual = redshiftDataApiRepository.getPaginatedExternalTableResult(
      tableId = TABLE_ID,
      selectedPage = selectedPage,
      pageSize = pageSize,
      filters = emptyList(),
      jdbcTemplate = jdbcTemplate,
    )

    assertEquals(expected, actual)
    verify(jdbcTemplate).queryForList(
      eq("SELECT * FROM reports.$TABLE_ID WHERE 1=1  LIMIT $pageSize OFFSET ($selectedPage - 1) * $pageSize;"),
      any<MapSqlParameterSource>(),
    )
  }

  @ParameterizedTest
  @CsvSource(
    "true,asc",
    "false,desc",
  )
  fun `getStatementResult with filters and sorting should make a paginated JDBC call and return the existing results`(sortedAsc: Boolean, ascOrDesc: String) {
    val jdbcTemplate = mock<NamedParameterJdbcTemplate>()
    val sortColumn = "columnA"
    val redshiftDataApiRepository = RedshiftDataApiRepository(
      redshiftDataClient,
      tableIdGenerator,
      identifiedHelper,
      redShiftSummaryTableHelper,
      REDSHIFT_DATA_API_DB,
      REDSHIFT_DATA_API_CLUSTER_ID,
      REDSHIFT_DATA_API_SECRET_ARN,
    )
    val selectedPage = 1L
    val pageSize = 10L
    val expected = listOf<Map<String, Any?>>(movementPrisoner1, movementPrisoner2)

    whenever(jdbcTemplate.queryForList(any(), any<MapSqlParameterSource>())).thenReturn(expected)

    val filters = listOf(
      Filter("filterName1", "filterValue1"),
      Filter("filterName2", "filterValue2"),
    )
    val actual = redshiftDataApiRepository.getPaginatedExternalTableResult(
      tableId = TABLE_ID,
      selectedPage = selectedPage,
      pageSize = pageSize,
      filters = filters,
      sortedAsc = sortedAsc,
      sortColumn = sortColumn,
      jdbcTemplate = jdbcTemplate,
    )

    assertEquals(expected, actual)
    verify(jdbcTemplate).queryForList(
      eq("SELECT * FROM reports.$TABLE_ID WHERE lower(filterName1) = 'filtervalue1' AND lower(filterName2) = 'filtervalue2' ORDER BY $sortColumn $ascOrDesc LIMIT $pageSize OFFSET ($selectedPage - 1) * $pageSize;"),
      any<MapSqlParameterSource>(),
    )
  }

  @Test
  fun `getFullExternalTableResult should make an unpaginated JDBC call and return the existing results`() {
    val jdbcTemplate = mock<NamedParameterJdbcTemplate>()
    val redshiftDataApiRepository = RedshiftDataApiRepository(
      redshiftDataClient,
      tableIdGenerator,
      identifiedHelper,
      redShiftSummaryTableHelper,
      REDSHIFT_DATA_API_DB,
      REDSHIFT_DATA_API_CLUSTER_ID,
      REDSHIFT_DATA_API_SECRET_ARN,
    )
    val expected = listOf<Map<String, Any?>>(movementPrisoner1, movementPrisoner2)

    whenever(
      jdbcTemplate.queryForList(
        eq("SELECT * FROM reports.$TABLE_ID;"),
        any<MapSqlParameterSource>(),
      ),
    ).thenReturn(expected)

    val actual = redshiftDataApiRepository.getFullExternalTableResult(TABLE_ID, jdbcTemplate)

    assertEquals(expected, actual)
  }

  @Test
  fun `count should make a JDBC call and return the existing results`() {
    val jdbcTemplate = mock<NamedParameterJdbcTemplate>()
    val redshiftDataApiRepository = RedshiftDataApiRepository(
      mock(),
      mock(),
      identifiedHelper,
      redShiftSummaryTableHelper,
      REDSHIFT_DATA_API_DB,
      REDSHIFT_DATA_API_CLUSTER_ID,
      REDSHIFT_DATA_API_SECRET_ARN,
    )
    val expected = listOf<Map<String, Any?>>(mapOf("total" to 5L))

    whenever(
      jdbcTemplate.queryForList(
        any<String>(),
        any<MapSqlParameterSource>(),
      ),
    ).thenReturn(expected)

    val actual = redshiftDataApiRepository.count(tableId = TABLE_ID, jdbcTemplate = jdbcTemplate)

    verify(jdbcTemplate, times(1)).queryForList(
      eq("SELECT COUNT(1) as total FROM reports.$TABLE_ID WHERE 1=1;"),
      any<MapSqlParameterSource>(),
    )
    assertEquals(5L, actual)
  }

  @Test
  fun `count should make a JDBC call with the filters in the where clause and return the existing results`() {
    val jdbcTemplate = mock<NamedParameterJdbcTemplate>()
    val filters = listOf(Filter("direction", "out"))
    val redshiftDataApiRepository = RedshiftDataApiRepository(
      mock(),
      mock(),
      identifiedHelper,
      redShiftSummaryTableHelper,
      REDSHIFT_DATA_API_DB,
      REDSHIFT_DATA_API_CLUSTER_ID,
      REDSHIFT_DATA_API_SECRET_ARN,
    )
    val expected = listOf<Map<String, Any?>>(mapOf("total" to 5L))

    whenever(
      jdbcTemplate.queryForList(
        any<String>(),
        any<MapSqlParameterSource>(),
      ),
    ).thenReturn(expected)

    val actual = redshiftDataApiRepository.count(tableId = TABLE_ID, jdbcTemplate = jdbcTemplate, filters = filters)

    verify(jdbcTemplate, times(1)).queryForList(
      eq("SELECT COUNT(1) as total FROM reports.$TABLE_ID WHERE lower(direction) = 'out';"),
      any<MapSqlParameterSource>(),
    )
    assertEquals(5L, actual)
  }

  @Test
  fun `isTableMissing should return false if the table exists`() {
    val jdbcTemplate = mock<NamedParameterJdbcTemplate>()
    val redshiftDataApiRepository = RedshiftDataApiRepository(
      redshiftDataClient,
      tableIdGenerator,
      identifiedHelper,
      redShiftSummaryTableHelper,
      REDSHIFT_DATA_API_DB,
      REDSHIFT_DATA_API_CLUSTER_ID,
      REDSHIFT_DATA_API_SECRET_ARN,
    )
    val tableId = TableIdGenerator().generateNewExternalTableId()

    whenever(jdbcTemplate.queryForList(any(), any<MapSqlParameterSource>())).thenReturn(listOf(mapOf("tablename" to tableId)))

    assertFalse(redshiftDataApiRepository.isTableMissing(tableId, jdbcTemplate))
    verify(jdbcTemplate).queryForList(
      eq("SELECT tablename FROM SVV_EXTERNAL_TABLES WHERE schemaname = 'reports' AND tablename = '$tableId'"),
      any<MapSqlParameterSource>(),
    )
  }

  @Test
  fun `isTableMissing should return true if the table is missing`() {
    val jdbcTemplate = mock<NamedParameterJdbcTemplate>()
    val redshiftDataApiRepository = RedshiftDataApiRepository(
      redshiftDataClient,
      tableIdGenerator,
      identifiedHelper,
      redShiftSummaryTableHelper,
      REDSHIFT_DATA_API_DB,
      REDSHIFT_DATA_API_CLUSTER_ID,
      REDSHIFT_DATA_API_SECRET_ARN,
    )
    val tableId = TableIdGenerator().generateNewExternalTableId()

    whenever(jdbcTemplate.queryForList(any(), any<MapSqlParameterSource>())).thenReturn(emptyList())

    assertTrue(redshiftDataApiRepository.isTableMissing(tableId, jdbcTemplate))
    verify(jdbcTemplate).queryForList(
      eq("SELECT tablename FROM SVV_EXTERNAL_TABLES WHERE schemaname = 'reports' AND tablename = '$tableId'"),
      any<MapSqlParameterSource>(),
    )
  }
}
