package uk.gov.justice.digital.hmpps.digitalprisonreportinglib.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.then
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.RenderMethod
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.ReportDefinitionSummary
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.SingleVariantReportDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.VariantDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.VariantDefinitionSummary
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.ProductDefinitionRepository
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Dataset
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Datasource
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.MetaData
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.ProductDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.RenderMethod.HTML
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Report
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Schema
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.SingleReportProductDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.policyengine.Effect
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.policyengine.Policy
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.policyengine.PolicyType.ROW_LEVEL
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.policyengine.Rule
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.security.DprAuthAwareAuthenticationToken
import java.time.LocalDateTime

class ReportDefinitionServiceTest {

  private val minimalDefinition = ProductDefinition(
    id = "1",
    name = "2",
    metadata = MetaData(
      author = "3",
      owner = "4",
      version = "5",
    ),
    report = emptyList(),
  )

  private val policy: Policy = Policy(
    "caseload",
    ROW_LEVEL,
    listOf("(origin_code=\${caseload} AND direction='OUT') OR (destination_code=\${caseload} AND direction='IN')"),
    listOf(Rule(Effect.PERMIT, emptyList())),
  )

  private val dataset = Dataset(
    id = "10",
    name = "11",
    query = "12",
    datasource = "12A",
    schema = Schema(emptyList()),
  )

  private val minimalSingleDefinition = SingleReportProductDefinition(
    id = "1",
    name = "2",
    report = Report(
      id = "3",
      name = "4",
      created = LocalDateTime.now(),
      version = "5",
      dataset = "\$ref:10",
      render = HTML,
    ),
    reportDataset = dataset,
    datasource = Datasource(
      id = "20",
      name = "21",
    ),
    metadata = MetaData(
      author = "30",
      version = "31",
      owner = "32",
    ),
    policy = listOf(policy),
    allDatasets = listOf(dataset),
    allReports = emptyList(),
  )

  private val productDefinitionTokenPolicyChecker = mock<ProductDefinitionTokenPolicyChecker>()

  @BeforeEach
  fun setup() {
    whenever(
      productDefinitionTokenPolicyChecker.determineAuth(
        withPolicy = any(),
        userToken = any(),
      ),
    ).thenReturn(true)
  }

  @Test
  fun `Getting report list for user maps correctly`() {
    val expectedResult = ReportDefinitionSummary(
      id = "1",
      name = "2",
      variants = listOf(
        VariantDefinitionSummary(
          id = "1",
          name = "2",
        ),
      ),
      authorised = true,
    )
    val authToken = mock<DprAuthAwareAuthenticationToken>()

    val repository = mock<ProductDefinitionRepository>()
    whenever(repository.getProductDefinitions()).thenReturn(listOf(minimalDefinition))

    val mapper = mock<ReportDefinitionMapper> {}

    val summaryMapper = mock<ReportDefinitionSummaryMapper> {
      on { map(any(), any(), any()) } doReturn expectedResult
    }
    val service = ReportDefinitionService(repository, mapper, summaryMapper, productDefinitionTokenPolicyChecker)

    val actualResult = service.getListForUser(RenderMethod.HTML, authToken)

    then(repository).should().getProductDefinitions()
    then(summaryMapper).should().map(minimalDefinition, RenderMethod.HTML, authToken)

    assertThat(actualResult).isNotEmpty
    assertThat(actualResult).hasSize(1)
    assertThat(actualResult[0]).isEqualTo(expectedResult)
  }

  @Test
  fun `Getting single report for user maps correctly`() {
    val expectedResult = SingleVariantReportDefinition(
      id = "1",
      name = "2",
      variant = VariantDefinition(
        id = "1",
        name = "2",
        resourceName = "3",
      ),
    )
    val authToken = mock<DprAuthAwareAuthenticationToken>()

    val repository = mock<ProductDefinitionRepository>()
    whenever(repository.getSingleReportProductDefinition(any(), any(), anyOrNull())).thenReturn(minimalSingleDefinition)

    val mapper = mock<ReportDefinitionMapper> {
      on { mapReport(any<SingleReportProductDefinition>(), any(), anyOrNull()) } doReturn expectedResult
    }
    val service = ReportDefinitionService(repository, mapper, mock<ReportDefinitionSummaryMapper> {}, productDefinitionTokenPolicyChecker)

    val actualResult = service.getDefinition(
      minimalSingleDefinition.id,
      minimalSingleDefinition.report.id,
      authToken,
    )

    then(repository).should().getSingleReportProductDefinition(
      minimalSingleDefinition.id,
      minimalSingleDefinition.report.id,
    )
    then(mapper).should().mapReport(minimalSingleDefinition, authToken)

    assertThat(actualResult).isNotNull
    assertThat(actualResult).isEqualTo(expectedResult)
  }

  @Test
  fun `Getting HTML report list with no matches returns no domains`() {
    val authToken = mock<DprAuthAwareAuthenticationToken>()
    val definitionWithNoVariants = ReportDefinitionSummary(
      id = "1",
      name = "2",
      variants = emptyList(),
      authorised = true,
    )
    val repository = mock<ProductDefinitionRepository>()
    whenever(repository.getProductDefinitions()).thenReturn(listOf(minimalDefinition))

    val mapper = mock<ReportDefinitionMapper> {}
    val summaryMapper = mock<ReportDefinitionSummaryMapper> {
      on { map(any(), any(), any()) } doReturn definitionWithNoVariants
    }
    val service = ReportDefinitionService(repository, mapper, summaryMapper, productDefinitionTokenPolicyChecker)

    val actualResult = service.getListForUser(RenderMethod.HTML, authToken)

    assertThat(actualResult).hasSize(0)
  }
}
