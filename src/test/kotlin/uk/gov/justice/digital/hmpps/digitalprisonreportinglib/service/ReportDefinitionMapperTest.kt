package uk.gov.justice.digital.hmpps.digitalprisonreportinglib.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.FieldDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.FieldType
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.FilterOption
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.SingleVariantReportDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.IdentifiedHelper
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.establishmentsAndWings.EstablishmentToWing
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.establishmentsAndWings.EstablishmentToWing.Companion.ALL_WINGS
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Dataset
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Datasource
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.DynamicFilterOption
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Feature
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.FeatureType
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.FilterDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.FilterType
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Identified.Companion.REF_PREFIX
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.MetaData
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Parameter
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.ParameterType
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.ReferenceType
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.RenderMethod
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Report
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.ReportChild
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.ReportField
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.ReportMetadata
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.ReportMetadataHint
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.ReportSummary
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Schema
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.SchemaField
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.SingleReportProductDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Specification
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.StaticFilterOption
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.SummaryField
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.SummaryTemplate
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Template
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Visible
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.WordWrap
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.policyengine.Effect
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.policyengine.Policy
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.policyengine.PolicyType
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.policyengine.PolicyType.ROW_LEVEL
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.policyengine.Rule
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.security.DprAuthAwareAuthenticationToken
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.service.DefinitionMapper.Companion.DEFAULT_MAX_STATIC_OPTIONS
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.service.estcodesandwings.EstablishmentCodesToWingsCacheService
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Collections.singletonMap

class ReportDefinitionMapperTest {

  private val fullDataset = Dataset(
    id = "10",
    name = "11",
    query = "12",
    datasource = "12A",
    schema = Schema(
      field = listOf(
        SchemaField(
          name = "13",
          type = ParameterType.Long,
          display = "14",
          filter = null,
        ),
      ),
    ),
  )

  private val fullDatasource = Datasource(
    id = "18",
    name = "19",
  )

  private val feature = Feature(
    type = FeatureType.PRINT,
  )

  private val childReport = Report(
    id = "C21",
    name = "C22",
    description = "C23",
    created = LocalDateTime.MIN,
    version = "C24",
    dataset = "\$ref:10",
    render = RenderMethod.HTMLChild,
    schedule = "C26",
    specification = Specification(
      template = Template.List,
      section = listOf("C30"),
      field = listOf(
        ReportField(
          name = "\$ref:13",
          display = "C14",
          wordWrap = WordWrap.None,
          filter = FilterDefinition(
            type = FilterType.Radio,
            staticOptions = listOf(
              StaticFilterOption(
                name = "C16",
                display = "C17",
              ),
            ),
            mandatory = true,
            pattern = ".+",
            interactive = true,
          ),
          sortable = true,
          defaultSort = true,
          formula = null,
          visible = Visible.TRUE,
        ),
      ),
    ),
    destination = emptyList(),
    classification = "someChildClassification",
    feature = emptyList(),
    summary = emptyList(),
  )

  private val fullReport = Report(
    id = "21",
    name = "22",
    description = "23",
    created = LocalDateTime.MAX,
    version = "24",
    dataset = "\$ref:10",
    render = RenderMethod.PDF,
    schedule = "26",
    specification = Specification(
      template = Template.ParentChild,
      section = listOf("30"),
      field = listOf(
        ReportField(
          name = "\$ref:13",
          display = "14",
          wordWrap = WordWrap.None,
          filter = FilterDefinition(
            type = FilterType.Radio,
            staticOptions = listOf(
              StaticFilterOption(
                name = "16",
                display = "17",
              ),
            ),
            mandatory = true,
            pattern = ".+",
            interactive = true,
          ),
          sortable = true,
          defaultSort = true,
          formula = null,
          visible = Visible.TRUE,
        ),
      ),
    ),
    destination = listOf(singletonMap("28", "29")),
    classification = "someClassification",
    feature = listOf(feature),
    summary = listOf(
      ReportSummary(
        id = "30",
        dataset = "\$ref:10",
        template = SummaryTemplate.PageFooter,
        field = listOf(
          SummaryField(
            name = "\$ref:13",
            header = true,
            mergeRows = false,
          ),
        ),
      ),
    ),
    child = listOf(ReportChild(childReport.id, listOf("13"))),
  )

  private val singleReportProductDefinition: SingleReportProductDefinition = SingleReportProductDefinition(
    id = "1",
    name = "2",
    description = "3",
    metadata = MetaData(
      author = "4",
      version = "5",
      owner = "6",
      purpose = "7",
      profile = "8",
    ),
    reportDataset = fullDataset,
    datasource = fullDatasource,
    report = fullReport,
    policy = listOf(
      Policy(
        id = "caseload",
        type = PolicyType.ACCESS,
        rule = listOf(Rule(Effect.PERMIT, emptyList())),
      ),
    ),
    allDatasets = listOf(fullDataset),
    allReports = listOf(fullReport, childReport),
  )

  private val policy: Policy = Policy(
    "caseload",
    ROW_LEVEL,
    listOf("(origin_code=\${caseload} AND direction='OUT') OR (destination_code=\${caseload} AND direction='IN')"),
    listOf(Rule(Effect.PERMIT, emptyList())),
  )

  private val fullSingleReportProductDefinition: SingleReportProductDefinition = SingleReportProductDefinition(
    id = "1",
    name = "2",
    description = "3",
    metadata = MetaData(
      author = "4",
      version = "5",
      owner = "6",
      purpose = "7",
      profile = "8",
    ),
    datasource = fullDatasource,
    reportDataset = fullDataset,
    report = fullReport,
    policy = listOf(policy),
    allDatasets = listOf(fullDataset),
    allReports = listOf(fullReport, childReport),
  )

  private val configuredApiService: SyncDataApiService = mock()
  private val authToken = mock<DprAuthAwareAuthenticationToken>()
  private val identifiedHelper = IdentifiedHelper()
  private val establishmentCodesToWingsCacheService = mock<EstablishmentCodesToWingsCacheService>()

  @Test
  fun `Getting report for user maps full data correctly`() {
    val mapper = ReportDefinitionMapper(configuredApiService, identifiedHelper, establishmentCodesToWingsCacheService)

    val result = mapper.mapReport(definition = singleReportProductDefinition, userToken = authToken)

    assertThat(result).isNotNull
    assertThat(result.id).isEqualTo(singleReportProductDefinition.id)
    assertThat(result.name).isEqualTo(singleReportProductDefinition.name)
    assertThat(result.description).isEqualTo(singleReportProductDefinition.description)
    assertThat(result.variant).isNotNull

    val variant = result.variant

    assertThat(variant.id).isEqualTo(singleReportProductDefinition.report.id)
    assertThat(variant.name).isEqualTo(singleReportProductDefinition.report.name)
    assertThat(variant.resourceName).isEqualTo("reports/${singleReportProductDefinition.id}/${singleReportProductDefinition.report.id}")
    assertThat(variant.description).isEqualTo(singleReportProductDefinition.report.description)
    assertThat(variant.specification).isNotNull
    assertThat(variant.classification).isEqualTo(singleReportProductDefinition.report.classification)
    assertThat(variant.printable).isEqualTo(singleReportProductDefinition.report.feature?.first()?.type == FeatureType.PRINT)
    assertThat(variant.specification?.template.toString()).isEqualTo(singleReportProductDefinition.report.specification?.template.toString())
    assertThat(variant.specification?.sections).hasSize(1)
    assertThat(variant.specification?.sections?.first())
      .isEqualTo(singleReportProductDefinition.report.specification?.section?.first()?.removePrefix(REF_PREFIX))
    assertThat(variant.specification?.fields).isNotEmpty
    assertThat(variant.specification?.fields).hasSize(1)

    val field = variant.specification!!.fields.first()
    val sourceSchemaField = singleReportProductDefinition.reportDataset.schema.field.first()
    val sourceReportField = singleReportProductDefinition.report.specification!!.field.first()

    assertThat(field.name).isEqualTo(sourceSchemaField.name)
    assertThat(field.display).isEqualTo(sourceReportField.display)
    assertThat(field.wordWrap.toString()).isEqualTo(sourceReportField.wordWrap.toString())
    assertThat(field.sortable).isEqualTo(sourceReportField.sortable)
    assertThat(field.defaultsort).isEqualTo(sourceReportField.defaultSort)
    assertThat(field.visible).isTrue()
    assertThat(field.mandatory).isFalse()
    assertThat(field.calculated).isFalse()
    assertThat(field.filter).isNotNull
    assertThat(field.filter?.type.toString()).isEqualTo(sourceReportField.filter?.type.toString())
    assertThat(field.filter?.staticOptions).isNotEmpty
    assertThat(field.filter?.staticOptions).hasSize(1)
    assertThat(field.type.toString()).isEqualTo(sourceSchemaField.type.toString())

    val filterOption = field.filter?.staticOptions?.first()
    val sourceFilterOption = sourceReportField.filter?.staticOptions?.first()

    assertThat(filterOption?.name).isEqualTo(sourceFilterOption?.name)
    assertThat(filterOption?.display).isEqualTo(sourceFilterOption?.display)
    verifyNoInteractions(configuredApiService)

    assertThat(variant.summaries?.count()).isEqualTo(1)

    val summary = variant.summaries!!.first()
    val sourceSummary = singleReportProductDefinition.report.summary!!.first()
    assertThat(summary.id).isEqualTo(sourceSummary.id)
    assertThat(summary.template.toString()).isEqualTo(sourceSummary.template.toString())

    assertThat(summary.fields.count()).isEqualTo(1)

    val summaryField = summary.fields.first()
    assertThat(summaryField.name).isEqualTo(fullDataset.schema.field.first().name)
    assertThat(summaryField.display).isEqualTo(fullDataset.schema.field.first().display)
    assertThat(summaryField.header).isEqualTo(sourceSummary.field?.first()?.header)
    assertThat(summaryField.mergeRows).isEqualTo(sourceSummary.field?.first()?.mergeRows)

    assertThat(variant.childVariants?.count()).isEqualTo(1)
  }

  @Test
  fun `Getting report for statically returned dynamic filter values on a number succeeds`() {
    whenever(
      configuredApiService.validateAndFetchData(any(), any(), any(), anyLong(), anyLong(), any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()),
    ).thenReturn(listOf(mapOf("1" to BigDecimal(1)), mapOf("2" to BigDecimal(2))))

    val productDefinition = SingleReportProductDefinition(
      id = "1",
      name = "2",
      metadata = MetaData(
        author = "3",
        owner = "4",
        version = "5",
      ),
      reportDataset = fullDataset,
      report = Report(
        id = "21",
        name = "22",
        description = "23",
        created = LocalDateTime.MAX,
        version = "24",
        dataset = "\$ref:10",
        render = RenderMethod.PDF,
        schedule = "26",
        specification = Specification(
          template = Template.ListSection,
          section = null,
          field = listOf(
            ReportField(
              name = "\$ref:13",
              display = "14",
              wordWrap = WordWrap.None,
              filter = FilterDefinition(
                type = FilterType.Radio,
                dynamicOptions = DynamicFilterOption(
                  returnAsStaticOptions = true,
                ),
              ),
              sortable = true,
              defaultSort = true,
              formula = null,
              visible = Visible.TRUE,
            ),
          ),
        ),
        destination = listOf(singletonMap("28", "29")),
        classification = "someClassification",
      ),
      policy = listOf(
        Policy(
          id = "caseload",
          type = PolicyType.ACCESS,
          rule = listOf(Rule(Effect.PERMIT, emptyList())),
        ),
      ),
      datasource = Datasource("datasourceId", "datasourceName"),
      allDatasets = listOf(fullDataset),
      allReports = emptyList(),
    )
    val mapper = ReportDefinitionMapper(configuredApiService, identifiedHelper, establishmentCodesToWingsCacheService)

    val result = mapper.mapReport(productDefinition, authToken)

    assertThat(result.variant.specification!!.fields[0].filter!!.staticOptions).hasSize(2)
  }

  @ParameterizedTest
  @CsvSource(
    "-2,DAYS",
    "-1,DAYS",
    "0,DAYS",
    "1,DAYS",
    "2,DAYS",
    "2,WEEKS",
    "2,MONTHS",
    "2,YEARS",
  )
  fun `Default value token is mapped correctly`(offset: Long, magnitude: ChronoUnit) {
    val defaultValue = createProductDefinition("today($offset, $magnitude)")
    val expectedDate = getExpectedDate(offset, magnitude)

    val result = ReportDefinitionMapper(configuredApiService, identifiedHelper, establishmentCodesToWingsCacheService).mapReport(definition = defaultValue, userToken = authToken)

    assertThat(result.variant.specification!!.fields[0].filter!!.defaultValue).isEqualTo(expectedDate)

    verifyNoInteractions(configuredApiService)
  }

  @Test
  fun `Default value token for today is mapped correctly`() {
    val defaultValue = createProductDefinition("today()")
    val expectedDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    val result = ReportDefinitionMapper(configuredApiService, identifiedHelper, establishmentCodesToWingsCacheService).mapReport(definition = defaultValue, userToken = authToken)

    assertThat(result.variant.specification!!.fields[0].filter!!.defaultValue).isEqualTo(expectedDate)

    verifyNoInteractions(configuredApiService)
  }

  @Test
  fun `Multiple default value tokens are mapped correctly`() {
    val defaultValue = createProductDefinition("today(-7,DAYS), today(), today(7,DAYS)")
    val expectedDate1 = getExpectedDate(-7, ChronoUnit.DAYS)
    val expectedDate2 = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    val expectedDate3 = getExpectedDate(7, ChronoUnit.DAYS)
    val expectedResult = "$expectedDate1, $expectedDate2, $expectedDate3"

    val result = ReportDefinitionMapper(configuredApiService, identifiedHelper, establishmentCodesToWingsCacheService).mapReport(definition = defaultValue, userToken = authToken)

    assertThat(result.variant.specification!!.fields[0].filter!!.defaultValue).isEqualTo(expectedResult)

    verifyNoInteractions(configuredApiService)
  }

  @ParameterizedTest
  @CsvSource(
    "-2,DAYS",
    "-1,DAYS",
    "0,DAYS",
    "1,DAYS",
    "2,DAYS",
    "2,WEEKS",
    "2,MONTHS",
    "2,YEARS",
  )
  fun `Min and Max value tokens are mapped correctly`(offset: Long, magnitude: ChronoUnit) {
    val defaultValue = createProductDefinition(
      "today($offset, $magnitude)",
      min = "today($offset, $magnitude)",
      max = "today($offset, $magnitude)",
    )
    val expectedDate = getExpectedDate(offset, magnitude)

    val result = ReportDefinitionMapper(configuredApiService, identifiedHelper, establishmentCodesToWingsCacheService).mapReport(definition = defaultValue, userToken = authToken)

    assertThat(result.variant.specification!!.fields[0].filter!!.min).isEqualTo(expectedDate)
    assertThat(result.variant.specification!!.fields[0].filter!!.max).isEqualTo(expectedDate)

    verifyNoInteractions(configuredApiService)
  }

  @Test
  fun `Getting single report for user maps full data correctly`() {
    val mapper = ReportDefinitionMapper(configuredApiService, identifiedHelper, establishmentCodesToWingsCacheService)

    val result = mapper.mapReport(fullSingleReportProductDefinition, authToken)

    assertThat(result).isNotNull
    assertThat(result.id).isEqualTo(fullSingleReportProductDefinition.id)
    assertThat(result.name).isEqualTo(fullSingleReportProductDefinition.name)
    assertThat(result.description).isEqualTo(fullSingleReportProductDefinition.description)

    val variant = result.variant

    assertThat(variant.id).isEqualTo(fullSingleReportProductDefinition.report.id)
    assertThat(variant.name).isEqualTo(fullSingleReportProductDefinition.report.name)
    assertThat(variant.resourceName).isEqualTo("reports/${fullSingleReportProductDefinition.id}/${fullSingleReportProductDefinition.report.id}")
    assertThat(variant.description).isEqualTo(fullSingleReportProductDefinition.report.description)
    assertThat(variant.specification).isNotNull
    assertThat(variant.specification?.template.toString()).isEqualTo(fullSingleReportProductDefinition.report.specification?.template.toString())
    assertThat(variant.specification?.fields).isNotEmpty
    assertThat(variant.specification?.fields).hasSize(1)

    val field = variant.specification!!.fields.first()
    val sourceSchemaField = fullSingleReportProductDefinition.reportDataset.schema.field.first()
    val sourceReportField = fullSingleReportProductDefinition.report.specification!!.field.first()

    assertThat(field.name).isEqualTo(sourceSchemaField.name)
    assertThat(field.display).isEqualTo(sourceReportField.display)
    assertThat(field.wordWrap.toString()).isEqualTo(sourceReportField.wordWrap.toString())
    assertThat(field.sortable).isEqualTo(sourceReportField.sortable)
    assertThat(field.defaultsort).isEqualTo(sourceReportField.defaultSort)
    assertThat(field.filter).isNotNull
    assertThat(field.filter?.type.toString()).isEqualTo(sourceReportField.filter?.type.toString())
    assertThat(field.filter?.staticOptions).isNotEmpty
    assertThat(field.filter?.staticOptions).hasSize(1)
    assertThat(field.filter?.mandatory).isEqualTo(sourceReportField.filter?.mandatory)
    assertThat(field.filter?.pattern).isEqualTo(sourceReportField.filter?.pattern)
    assertThat(field.filter?.interactive).isEqualTo(sourceReportField.filter?.interactive)
    assertThat(field.type.toString()).isEqualTo(sourceSchemaField.type.toString())

    val filterOption = field.filter?.staticOptions?.first()
    val sourceFilterOption = sourceReportField.filter?.staticOptions?.first()

    assertThat(filterOption?.name).isEqualTo(sourceFilterOption?.name)
    assertThat(filterOption?.display).isEqualTo(sourceFilterOption?.display)
    assertThat(variant.interactive).isNull()

    verifyNoInteractions(configuredApiService)
  }

  @Test
  fun `getting single report with dynamic options maps full data correctly and generates the static options in the result when returnAsStaticOptions is true`() {
    val reportWithDynamicFilter = generateReport(DynamicFilterOption(minimumLength = 2, returnAsStaticOptions = true))

    val fullSingleProductDefinition = fullSingleReportProductDefinition.copy(report = reportWithDynamicFilter)

    val mapper = ReportDefinitionMapper(configuredApiService, identifiedHelper, establishmentCodesToWingsCacheService)

    whenever(
      configuredApiService.validateAndFetchData(
        fullSingleProductDefinition.id,
        reportWithDynamicFilter.id, emptyMap(), 1, DEFAULT_MAX_STATIC_OPTIONS, "13", true, authToken, setOf("13"),
      ),
    ).thenReturn(listOf(mapOf("13" to "static1"), mapOf("13" to "static2")))

    val result = mapper.mapReport(fullSingleProductDefinition, authToken)

    assertResult(result, fullSingleProductDefinition)
    val field = result.variant.specification!!.fields.first()

    assertThat(field.filter?.staticOptions).isNotEmpty
    assertThat(field.filter?.staticOptions).hasSize(2)
    assertThat(field.filter?.staticOptions).isEqualTo(
      listOf(
        FilterOption("static1", "static1"),
        FilterOption("static2", "static2"),
      ),
    )
  }

  @Test
  fun `getting single report with dynamic options which have a dataset maps full data correctly and generates the static options in the result when returnAsStaticOptions is true`() {
    val estCodeSchemaFieldName = "establishment_code"
    val establishmentCodeSchemaField = SchemaField(estCodeSchemaFieldName, ParameterType.String, "Establishment Code", null)
    val estNameSchemaFieldName = "establishment_name"
    val establishmentNameSchemaField = SchemaField(estNameSchemaFieldName, ParameterType.String, "Establishment Name", null)
    val estDatasetId = "establishment-dataset-id"
    val establishmentDataset = Dataset(
      estDatasetId,
      "establishment-dataset-name",
      "12A",
      "select * from table",
      Schema(
        listOf(
          establishmentCodeSchemaField,
          establishmentNameSchemaField,
        ),
      ),
    )
    val reportWithDynamicFilter = generateReport(DynamicFilterOption(returnAsStaticOptions = true, dataset = REF_PREFIX + estDatasetId, name = REF_PREFIX + estCodeSchemaFieldName, display = REF_PREFIX + estNameSchemaFieldName))

    val fullSingleProductDefinition = fullSingleReportProductDefinition.copy(
      report = reportWithDynamicFilter,
      allDatasets = listOf(establishmentDataset),
    )

    val mapper = ReportDefinitionMapper(configuredApiService, identifiedHelper, establishmentCodesToWingsCacheService)

    whenever(
      configuredApiService.validateAndFetchDataForFilterWithDataset(any(), any(), any()),
    ).thenReturn(
      listOf(
        mapOf(estCodeSchemaFieldName to "code1", estNameSchemaFieldName to "name1"),
        mapOf(estCodeSchemaFieldName to "code2", estNameSchemaFieldName to "name2"),
      ),
    )

    val result = mapper.mapReport(fullSingleProductDefinition, authToken)

    assertResult(result, fullSingleProductDefinition)
    val field = result.variant.specification!!.fields.first()

    assertThat(field.filter?.staticOptions).isNotEmpty
    assertThat(field.filter?.staticOptions).hasSize(2)
    assertThat(field.filter?.staticOptions).isEqualTo(
      listOf(
        FilterOption("code1", "name1"),
        FilterOption("code2", "name2"),
      ),
    )

    verify(configuredApiService).validateAndFetchDataForFilterWithDataset(
      pageSize = DEFAULT_MAX_STATIC_OPTIONS,
      sortColumn = estCodeSchemaFieldName,
      dataset = establishmentDataset,
    )
  }

  @Test
  fun `getting single report with dynamic options maps full data correctly and generates dynamic options in the result when returnAsStaticOptions is false`() {
    val reportWithDynamicFilter = generateReport(DynamicFilterOption(minimumLength = 2, returnAsStaticOptions = false))
    val fullSingleProductDefinition = fullSingleReportProductDefinition.copy(report = reportWithDynamicFilter)

    val mapper = ReportDefinitionMapper(configuredApiService, identifiedHelper, establishmentCodesToWingsCacheService)

    whenever(
      configuredApiService.validateAndFetchData(
        fullSingleProductDefinition.id,
        reportWithDynamicFilter.id, emptyMap(), 1, 10, "13", true, authToken, setOf("13"),
      ),
    ).thenReturn(listOf(mapOf("13" to "static1"), mapOf("13" to "static2")))

    val result = mapper.mapReport(fullSingleProductDefinition, authToken)

    assertThat(result).isNotNull
    assertThat(result.id).isEqualTo(fullSingleProductDefinition.id)
    assertThat(result.name).isEqualTo(fullSingleProductDefinition.name)
    assertThat(result.description).isEqualTo(fullSingleProductDefinition.description)

    assertResult(result, fullSingleProductDefinition)

    val field = result.variant.specification!!.fields.first()
    assertThat(field.filter?.staticOptions).isNull()
    assertThat(field.filter?.dynamicOptions?.minimumLength).isEqualTo(2)
    verifyNoInteractions(configuredApiService)
  }

  @Test
  fun `getting single report with a field containing a make_url formula maps full data correctly and generates HTML type for that field`() {
    val reportWithMakeUrlFormula = createReport("make_url('\${profile_host}/prisoner/\${prisoner_number}',\${full_name},TRUE)")

    val fullSingleProductDefinition = fullSingleReportProductDefinition.copy(report = reportWithMakeUrlFormula)

    val mapper = ReportDefinitionMapper(configuredApiService, identifiedHelper, establishmentCodesToWingsCacheService)

    val result = mapper.mapReport(fullSingleProductDefinition, authToken)

    assertThat(result).isNotNull
    assertThat(result.id).isEqualTo(fullSingleProductDefinition.id)
    assertThat(result.name).isEqualTo(fullSingleProductDefinition.name)
    assertThat(result.description).isEqualTo(fullSingleProductDefinition.description)

    val variant = result.variant

    assertThat(variant.id).isEqualTo(fullSingleProductDefinition.report.id)
    assertThat(variant.name).isEqualTo(fullSingleProductDefinition.report.name)
    assertThat(variant.resourceName).isEqualTo("reports/${fullSingleProductDefinition.id}/${fullSingleProductDefinition.report.id}")
    assertThat(variant.description).isEqualTo(fullSingleProductDefinition.report.description)
    assertThat(variant.specification).isNotNull
    assertThat(variant.specification?.template.toString()).isEqualTo(fullSingleProductDefinition.report.specification?.template.toString())
    assertThat(variant.specification?.fields).isNotEmpty
    assertThat(variant.specification?.fields).hasSize(1)

    val field = variant.specification!!.fields.first()
    val sourceSchemaField = fullSingleProductDefinition.reportDataset.schema.field.first()
    val sourceReportField = fullSingleProductDefinition.report.specification!!.field.first()

    assertThat(field.name).isEqualTo(sourceSchemaField.name)
    assertThat(field.display).isEqualTo(sourceReportField.display)
    assertThat(field.wordWrap.toString()).isEqualTo(sourceReportField.wordWrap.toString())
    assertThat(field.sortable).isEqualTo(sourceReportField.sortable)
    assertThat(field.defaultsort).isEqualTo(sourceReportField.defaultSort)
    assertThat(field.type).isEqualTo(FieldType.HTML)
    assertThat(field.calculated).isEqualTo(true)
    verifyNoInteractions(configuredApiService)
  }

  @Test
  fun `getting single report with a field containing a format_date formula maps full data correctly and generates date type for that field`() {
    val reportWithFormatDateFormula = createReport("format_date(\${11}, \"dd/MM/yyyy\")")

    val fullSingleProductDefinition = fullSingleReportProductDefinition
      .copy(
        report = reportWithFormatDateFormula,
        reportDataset = Dataset(
          id = "10",
          name = "11",
          query = "12",
          datasource = "12A",
          schema = Schema(
            field = listOf(
              SchemaField(
                name = "13",
                type = ParameterType.Date,
                display = "",
                filter = null,
              ),
            ),
          ),
        ),
      )

    val mapper = ReportDefinitionMapper(configuredApiService, identifiedHelper, establishmentCodesToWingsCacheService)

    val result = mapper.mapReport(fullSingleProductDefinition, authToken)

    assertThat(result).isNotNull
    assertThat(result.id).isEqualTo(fullSingleProductDefinition.id)
    assertThat(result.name).isEqualTo(fullSingleProductDefinition.name)
    assertThat(result.description).isEqualTo(fullSingleProductDefinition.description)

    val variant = result.variant

    assertThat(variant.id).isEqualTo(fullSingleProductDefinition.report.id)
    assertThat(variant.name).isEqualTo(fullSingleProductDefinition.report.name)
    assertThat(variant.resourceName).isEqualTo("reports/${fullSingleProductDefinition.id}/${fullSingleProductDefinition.report.id}")
    assertThat(variant.description).isEqualTo(fullSingleProductDefinition.report.description)
    assertThat(variant.specification).isNotNull
    assertThat(variant.specification?.template.toString()).isEqualTo(fullSingleProductDefinition.report.specification?.template.toString())
    assertThat(variant.specification?.fields).isNotEmpty
    assertThat(variant.specification?.fields).hasSize(1)

    val field = variant.specification!!.fields.first()
    val sourceSchemaField = fullSingleProductDefinition.reportDataset.schema.field.first()
    val sourceReportField = fullSingleProductDefinition.report.specification!!.field.first()

    assertThat(field.name).isEqualTo(sourceSchemaField.name)
    assertThat(field.display).isEqualTo(sourceReportField.display)
    assertThat(field.wordWrap.toString()).isEqualTo(sourceReportField.wordWrap.toString())
    assertThat(field.sortable).isEqualTo(sourceReportField.sortable)
    assertThat(field.defaultsort).isEqualTo(sourceReportField.defaultSort)
    assertThat(field.type).isEqualTo(FieldType.Date)
    assertThat(field.calculated).isEqualTo(true)
    verifyNoInteractions(configuredApiService)
  }

  @ParameterizedTest
  @CsvSource(
    "TRUE, true, false",
    "FALSE, false, false",
    "MANDATORY, true, true",
  )
  fun `Visible in the report definition is mapped correctly to the visible and mandatory fields in the controller model`(visibleDpd: Visible, visibleControllerModel: Boolean, mandatoryControllerModel: Boolean) {
    val defaultValue = createProductDefinition(
      defaultFilterValue = "today()",
      visible = visibleDpd,
    )

    val result: SingleVariantReportDefinition = ReportDefinitionMapper(configuredApiService, identifiedHelper, establishmentCodesToWingsCacheService).mapReport(definition = defaultValue, userToken = authToken)

    assertThat(result.variant.specification!!.fields[0].visible).isEqualTo(visibleControllerModel)
    assertThat(result.variant.specification!!.fields[0].mandatory).isEqualTo(mandatoryControllerModel)

    verifyNoInteractions(configuredApiService)
  }

  @ParameterizedTest
  @CsvSource(
    "a,'', a",
    "'', a, a",
    "a, b, b",
    "a, null, a",
    nullValues = ["null"],
  )
  fun `Display field falls back to dataset display when the report display field is not specified `(datasetDisplay: String, reportDisplay: String?, expectedDisplay: String) {
    val defaultValue = createProductDefinition(
      defaultFilterValue = "today()",
      datasetDisplay = datasetDisplay,
      reportFieldDisplay = reportDisplay,
    )

    val result = ReportDefinitionMapper(configuredApiService, identifiedHelper, establishmentCodesToWingsCacheService).mapReport(definition = defaultValue, userToken = authToken)

    assertThat(result.variant.specification!!.fields[0].display).isEqualTo(expectedDisplay)

    verifyNoInteractions(configuredApiService)
  }

  @Test
  fun `getting single report with parameters maps full data correctly and converts the parameters to filters`() {
    val parameterName = "paramName"
    val parameterDisplay = "paramDisplay"
    val parameter = Parameter(
      index = 0,
      name = parameterName,
      reportFieldType = ParameterType.String,
      filterType = FilterType.Text,
      display = parameterDisplay,
      mandatory = true,
    )
    val productDefinition = createProductDefinition("today()", parameters = listOf(parameter))

    val mapper = ReportDefinitionMapper(configuredApiService, identifiedHelper, establishmentCodesToWingsCacheService)

    val result = mapper.mapReport(productDefinition, authToken)

    val matchingField = result.variant.specification!!.fields.filter { it.name == parameterName }

    val expectedReportField = FieldDefinition(
      type = FieldType.String,
      name = parameterName,
      display = parameterDisplay,
      mandatory = false,
      defaultsort = false,
      sortable = false,
      calculated = false,
      filter = uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.FilterDefinition(
        type = uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.FilterType.Text,
        mandatory = true,
      ),
      visible = false,
    )
    assertThat(result.variant.specification!!.fields.size).isEqualTo(2)
    assertThat(matchingField.size).isEqualTo(1)
    assertThat(matchingField[0]).isEqualTo(expectedReportField)
  }

  @Test
  fun `getting single report with parameters with referenceType of establishment_code includes all the establishments as static options`() {
    val parameterName = "paramName"
    val parameterDisplay = "paramDisplay"
    val parameter = Parameter(
      index = 0,
      name = parameterName,
      reportFieldType = ParameterType.String,
      filterType = FilterType.Text,
      display = parameterDisplay,
      mandatory = true,
      referenceType = ReferenceType.ESTABLISHMENT,
    )
    val productDefinition = createProductDefinition("today()", parameters = listOf(parameter))
    val bfiEstCode = "BFI"
    val bfiDescription = "BEDFORD (HMP)"
    val bsiEstCode = "BSI"
    val bsiDescription = "BRINSFORD (HMP)"

    whenever(
      establishmentCodesToWingsCacheService.getEstablishmentsAndPopulateCacheIfNeeded(),
    ).thenReturn(
      mapOf(
        bfiEstCode to listOf(
          EstablishmentToWing(bfiEstCode, bfiDescription, "BFI-G"),
          EstablishmentToWing(bfiEstCode, bfiDescription, "BFI-E"),
        ),
        bsiEstCode to listOf(
          EstablishmentToWing(bsiEstCode, bsiDescription, "BSI-R"),
          EstablishmentToWing(bsiDescription, bsiDescription, "BSI-I"),
        ),
      ),
    )

    val mapper = ReportDefinitionMapper(configuredApiService, identifiedHelper, establishmentCodesToWingsCacheService)

    val result = mapper.mapReport(productDefinition, authToken)

    val matchingField = result.variant.specification!!.fields.filter { it.name == parameterName }

    val expectedStaticOptions = listOf(
      FilterOption(bfiEstCode, bfiDescription),
      FilterOption(bsiEstCode, bsiDescription),
    )

    val expectedReportField = createReportFieldDefinition(parameter, expectedStaticOptions)
    assertThat(result.variant.specification!!.fields.size).isEqualTo(2)
    assertThat(matchingField.size).isEqualTo(1)
    assertThat(matchingField[0]).isEqualTo(expectedReportField)
  }

  @Test
  fun `getting single report with parameters with referenceType of wing includes all the wings as static options`() {
    val parameterName = "paramName"
    val parameterDisplay = "paramDisplay"
    val parameter = Parameter(
      index = 0,
      name = parameterName,
      reportFieldType = ParameterType.String,
      filterType = FilterType.Text,
      display = parameterDisplay,
      mandatory = true,
      referenceType = ReferenceType.WING,
    )
    val productDefinition = createProductDefinition("today()", parameters = listOf(parameter))
    val bfiEstCode = "BFI"
    val bfiDescription = "BEDFORD (HMP)"
    val bsiEstCode = "BSI"
    val bsiDescription = "BRINSFORD (HMP)"

    val wingBfiG = "BFI-G"
    val wingBfiE = "BFI-E"
    val wingBsiR = "BSI-R"
    val wingBsiI = "BSI-I"

    whenever(
      establishmentCodesToWingsCacheService.getEstablishmentsAndPopulateCacheIfNeeded(),
    ).thenReturn(
      mapOf(
        bfiEstCode to listOf(
          EstablishmentToWing(bfiEstCode, bfiDescription, wingBfiG),
          EstablishmentToWing(bfiEstCode, bfiDescription, wingBfiE),
        ),
        bsiEstCode to listOf(
          EstablishmentToWing(bsiEstCode, bsiDescription, wingBsiR),
          EstablishmentToWing(bsiDescription, bsiDescription, wingBsiI),
        ),
      ),
    )

    val mapper = ReportDefinitionMapper(configuredApiService, identifiedHelper, establishmentCodesToWingsCacheService)

    val result = mapper.mapReport(productDefinition, authToken)

    val matchingField = result.variant.specification!!.fields.filter { it.name == parameterName }

    val expectedStaticOptions = listOf(
      FilterOption(wingBfiG, wingBfiG),
      FilterOption(wingBfiE, wingBfiE),
      FilterOption(wingBsiR, wingBsiR),
      FilterOption(wingBsiI, wingBsiI),
      FilterOption(ALL_WINGS, ALL_WINGS),
    )

    val expectedReportField = createReportFieldDefinition(parameter, expectedStaticOptions)
    assertThat(result.variant.specification!!.fields.size).isEqualTo(2)
    assertThat(matchingField.size).isEqualTo(1)
    assertThat(matchingField[0]).isEqualTo(expectedReportField)
  }

  @Test
  fun `Interactive report metadata hint is mapped to the report correctly`() {
    val defaultValue = createProductDefinition("today(-2,DAYS)", interactive = true)

    val result = ReportDefinitionMapper(configuredApiService, identifiedHelper, establishmentCodesToWingsCacheService).mapReport(definition = defaultValue, userToken = authToken)

    assertThat(result.variant.interactive).isEqualTo(true)
  }

  @Test
  fun `Field filter falls back to dataset filter when the report field filter is not specified `() {
    val sourceDataset = Dataset(
      id = "10",
      name = "11",
      query = "12",
      datasource = "12A",
      schema = Schema(
        field = listOf(
          SchemaField(
            name = "13",
            type = ParameterType.Long,
            display = "14",
            filter = FilterDefinition(
              type = FilterType.Text,
              mandatory = true,
            ),
          ),
        ),
      ),
    )

    val sourceDefinition = SingleReportProductDefinition(
      id = "1",
      name = "2",
      description = "3",
      metadata = MetaData(
        author = "4",
        version = "5",
        owner = "6",
        purpose = "7",
        profile = "8",
      ),
      reportDataset = sourceDataset,
      datasource = fullDatasource,
      report = fullReport,
      policy = listOf(
        Policy(
          id = "caseload",
          type = PolicyType.ACCESS,
          rule = listOf(Rule(Effect.PERMIT, emptyList())),
        ),
      ),
      allDatasets = listOf(sourceDataset),
      allReports = listOf(fullReport, childReport),
    )

    val result = ReportDefinitionMapper(configuredApiService, identifiedHelper, establishmentCodesToWingsCacheService).mapReport(definition = sourceDefinition, userToken = authToken)

    assertThat(result.variant.specification!!.fields[0].filter?.type.toString()).isEqualTo("Text")
    assertThat(result.variant.specification!!.fields[0].filter?.mandatory).isTrue()

    verifyNoInteractions(configuredApiService)
  }

  private fun createReportFieldDefinition(
    parameter: Parameter,
    expectedStaticOptions: List<FilterOption>?,
  ) = FieldDefinition(
    type = FieldType.String,
    name = parameter.name,
    display = parameter.display,
    mandatory = false,
    defaultsort = false,
    sortable = false,
    calculated = false,
    filter = uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.FilterDefinition(
      type = uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.FilterType.valueOf(parameter.filterType.toString()),
      mandatory = true,
      staticOptions = expectedStaticOptions,
    ),
    visible = false,
  )

  private fun generateReport(dynamicFilterOption: DynamicFilterOption) = Report(
    id = "21",
    name = "22",
    description = "23",
    created = LocalDateTime.MAX,
    version = "24",
    dataset = "\$ref:10",
    render = RenderMethod.PDF,
    schedule = "26",
    specification = Specification(
      template = Template.ListSection,
      section = null,
      field = listOf(
        ReportField(
          name = "\$ref:13",
          display = "14",
          wordWrap = WordWrap.None,
          filter = FilterDefinition(
            type = FilterType.AutoComplete,
            dynamicOptions = dynamicFilterOption,
          ),
          sortable = true,
          defaultSort = true,
          formula = null,
          visible = Visible.TRUE,
        ),
      ),
    ),
    destination = listOf(singletonMap("28", "29")),
    classification = "someClassification",
  )

  private fun assertResult(result: SingleVariantReportDefinition, fullSingleProductDefinition: SingleReportProductDefinition) {
    assertThat(result).isNotNull
    assertThat(result.id).isEqualTo(fullSingleProductDefinition.id)
    assertThat(result.name).isEqualTo(fullSingleProductDefinition.name)
    assertThat(result.description).isEqualTo(fullSingleProductDefinition.description)

    val variant = result.variant

    assertThat(variant.id).isEqualTo(fullSingleProductDefinition.report.id)
    assertThat(variant.name).isEqualTo(fullSingleProductDefinition.report.name)
    assertThat(variant.resourceName).isEqualTo("reports/${fullSingleProductDefinition.id}/${fullSingleProductDefinition.report.id}")
    assertThat(variant.description).isEqualTo(fullSingleProductDefinition.report.description)
    assertThat(variant.specification).isNotNull
    assertThat(variant.specification?.template.toString()).isEqualTo(fullSingleProductDefinition.report.specification?.template.toString())
    assertThat(variant.specification?.fields).isNotEmpty
    assertThat(variant.specification?.fields).hasSize(1)

    val field = variant.specification!!.fields.first()
    val sourceSchemaField = fullSingleProductDefinition.reportDataset.schema.field.first()
    val sourceReportField = fullSingleProductDefinition.report.specification!!.field.first()

    assertThat(field.name).isEqualTo(sourceSchemaField.name)
    assertThat(field.display).isEqualTo(sourceReportField.display)
    assertThat(field.wordWrap.toString()).isEqualTo(sourceReportField.wordWrap.toString())
    assertThat(field.sortable).isEqualTo(sourceReportField.sortable)
    assertThat(field.defaultsort).isEqualTo(sourceReportField.defaultSort)
    assertThat(field.filter).isNotNull
    assertThat(field.filter?.type.toString()).isEqualTo(sourceReportField.filter?.type.toString())
    assertThat(field.type.toString()).isEqualTo(sourceSchemaField.type.toString())
  }

  private fun getExpectedDate(offset: Long, magnitude: ChronoUnit): String? {
    val expectedDate = when (magnitude) {
      ChronoUnit.DAYS -> LocalDate.now().plusDays(offset)
      ChronoUnit.WEEKS -> LocalDate.now().plusWeeks(offset)
      ChronoUnit.MONTHS -> LocalDate.now().plusMonths(offset)
      ChronoUnit.YEARS -> LocalDate.now().plusYears(offset)
      else -> null
    }.let { it!!.format(DateTimeFormatter.ISO_LOCAL_DATE) }
    return expectedDate
  }

  private fun createReport(formula: String? = null) = Report(
    id = "21",
    name = "22",
    description = "23",
    created = LocalDateTime.MAX,
    version = "24",
    dataset = "\$ref:10",
    render = RenderMethod.PDF,
    schedule = "26",
    specification = Specification(
      template = Template.ListSection,
      section = null,
      field = listOf(
        ReportField(
          name = "\$ref:13",
          display = "14",
          wordWrap = WordWrap.None,
          sortable = true,
          defaultSort = true,
          formula = formula,
          visible = Visible.TRUE,
        ),
      ),
    ),
    destination = listOf(singletonMap("28", "29")),
    classification = "someClassification",
  )

  private fun createProductDefinition(
    defaultFilterValue: String,
    min: String? = null,
    max: String? = null,
    visible: Visible? = Visible.TRUE,
    datasetDisplay: String = "",
    reportFieldDisplay: String? = "20",
    formula: String? = null,
    parameters: List<Parameter>? = null,
    interactive: Boolean? = null,
  ): SingleReportProductDefinition {
    return SingleReportProductDefinition(
      id = "1",
      name = "2",
      metadata = MetaData(
        author = "3",
        owner = "4",
        version = "5",
      ),
      datasource = Datasource("datasourceId", "datasourceName"),
      reportDataset =
      Dataset(
        id = "10",
        name = "11",
        query = "12",
        datasource = "12A",
        schema = Schema(
          field = listOf(
            SchemaField(
              name = "13",
              type = ParameterType.Date,
              display = datasetDisplay,
              filter = null,
            ),
          ),
        ),
        parameters = parameters,
      ),
      report =
      Report(
        id = "16",
        name = "17",
        created = LocalDateTime.MAX,
        version = "18",
        dataset = "\$ref:10",
        render = RenderMethod.HTML,
        specification = Specification(
          template = Template.List,
          section = null,
          field = listOf(
            ReportField(
              name = "\$ref:13",
              display = reportFieldDisplay,
              filter = FilterDefinition(
                type = FilterType.DateRange,
                default = defaultFilterValue,
                min = min,
                max = max,
              ),
              formula = formula,
              visible = visible,
            ),
          ),
        ),
        classification = "someClassification",
        metadata = interactive?.takeIf { it }?.let { ReportMetadata(hints = listOf(ReportMetadataHint.INTERACTIVE)) },
      ),
      policy = listOf(
        Policy(
          id = "caseload",
          type = PolicyType.ACCESS,
          rule = listOf(Rule(Effect.PERMIT, emptyList())),
        ),
      ),
      allDatasets = listOf(fullDataset),
      allReports = emptyList(),
    )
  }
}
