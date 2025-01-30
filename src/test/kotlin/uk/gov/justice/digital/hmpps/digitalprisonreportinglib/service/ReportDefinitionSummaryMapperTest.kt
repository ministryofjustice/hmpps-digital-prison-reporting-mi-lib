package uk.gov.justice.digital.hmpps.digitalprisonreportinglib.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.DashboardDefinitionSummary
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.RenderMethod.HTML
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Chart
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.ChartType
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Column
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.ColumnAggregateType
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Dashboard
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Dataset
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Datasource
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.FilterDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.FilterType
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Label
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.MetaData
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Metric
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.ParameterType
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.ProductDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.RenderMethod
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Report
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.ReportField
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Schema
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.SchemaField
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Specification
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.StaticFilterOption
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Template
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Visible
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.WordWrap
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.security.DprAuthAwareAuthenticationToken
import java.time.LocalDateTime
import java.util.Collections.singletonMap

class ReportDefinitionSummaryMapperTest {

  val authToken = mock<DprAuthAwareAuthenticationToken>()

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
          display = "",
          filter = null,
        ),
      ),
    ),
  )

  private val fullDatasource = Datasource(
    id = "18",
    name = "19",
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
      template = Template.ListSection,
      section = null,
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

  private val fullProductDefinition: ProductDefinition = ProductDefinition(
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
    dataset = listOf(fullDataset),
    datasource = listOf(fullDatasource),
    report = listOf(fullReport),
  )

  @Test
  fun `Getting report list for user maps full data correctly`() {
    val mapper = ReportDefinitionSummaryMapper()

    val result = mapper.map(fullProductDefinition, null, authToken)

    assertThat(result).isNotNull
    assertThat(result.id).isEqualTo(fullProductDefinition.id)
    assertThat(result.name).isEqualTo(fullProductDefinition.name)
    assertThat(result.description).isEqualTo(fullProductDefinition.description)
    assertThat(result.variants).isNotEmpty
    assertThat(result.variants).hasSize(1)

    val variant = result.variants.first()

    assertThat(variant.id).isEqualTo(fullProductDefinition.report.first().id)
    assertThat(variant.name).isEqualTo(fullProductDefinition.report.first().name)
    assertThat(variant.description).isEqualTo(fullProductDefinition.report.first().description)
  }

  @Test
  fun `Getting report list for user maps minimal data successfully`() {
    val productDefinition = ProductDefinition(
      id = "1",
      name = "2",
      metadata = MetaData(
        author = "3",
        owner = "4",
        version = "5",
      ),
    )
    val mapper = ReportDefinitionSummaryMapper()

    val result = mapper.map(productDefinition, null, authToken)

    assertThat(result).isNotNull
    assertThat(result.variants).hasSize(0)
  }

  @Test
  fun `Getting HTML report list returns relevant reports`() {
    val productDefinition = ProductDefinition(
      id = "1",
      name = "2",
      metadata = MetaData(
        author = "3",
        owner = "4",
        version = "5",
      ),
      dataset = listOf(
        Dataset(
          id = "10",
          name = "11",
          query = "12",
          datasource = "12A",
          schema = Schema(
            field = emptyList(),
          ),
        ),
      ),
      report = listOf(
        Report(
          id = "13",
          name = "14",
          created = LocalDateTime.MAX,
          version = "15",
          dataset = "\$ref:10",
          render = RenderMethod.SVG,
          classification = "someClassification",
        ),
        Report(
          id = "16",
          name = "17",
          created = LocalDateTime.MAX,
          version = "18",
          dataset = "\$ref:10",
          render = RenderMethod.HTML,
          classification = "someClassification",
        ),
      ),
    )
    val mapper = ReportDefinitionSummaryMapper()

    val result = mapper.map(productDefinition, HTML, authToken)

    assertThat(result).isNotNull
    assertThat(result.variants).hasSize(1)
    assertThat(result.variants.first().id).isEqualTo("16")
  }

  @Test
  fun `Getting report list with dashboards for user includes the dashboard definition in the mapped data`() {
    val mapper = ReportDefinitionSummaryMapper()

    val dashboard = Dashboard(
      id = "d1",
      name = "n1",
      description = "abc",
      dataset = "dataset1",
      metrics = listOf(
        Metric(
          id = "m1",
          name = "n1",
          display = "d1",
          description = "d2",
          columns = listOf(Column(name = "column1", display = "Column 1", unit = "dim_1", aggregate = ColumnAggregateType.SUM)),
          charts = listOf(
            Chart(
              type = ChartType.BAR,
              label = Label(name = "label1", display = "Label 1"),
              columns = listOf("column1", "Column 1"),
            ),
          ),
        ),
      ),
    )
    val dashboardDefinition = DashboardDefinitionSummary(
      id = "d1",
      name = "n1",
      description = "abc",
    )

    val result = mapper.map(
      fullProductDefinition
        .copy(
          dashboards = listOf(
            dashboard,
          ),
        ),
      null,
      authToken,
    )

    assertThat(result.dashboards!![0]).isEqualTo(dashboardDefinition)
  }
}
