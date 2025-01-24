package uk.gov.justice.digital.hmpps.digitalprisonreportinglib.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.RenderMethod
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.ReportDefinitionSummary
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model.SingleVariantReportDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.ProductDefinitionRepository
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.policyengine.WithPolicy
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.exception.UserAuthorisationException
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.security.DprAuthAwareAuthenticationToken

@Service
class ReportDefinitionService(
  val productDefinitionRepository: ProductDefinitionRepository,
  val mapper: ReportDefinitionMapper,
  val summaryMapper: ReportDefinitionSummaryMapper,
  val productDefinitionTokenPolicyChecker: ProductDefinitionTokenPolicyChecker,
) {

  fun getListForUser(
    renderMethod: RenderMethod?,
    userToken: DprAuthAwareAuthenticationToken?,
    dataProductDefinitionsPath: String? = null,
  ): List<ReportDefinitionSummary> {
    return productDefinitionRepository.getProductDefinitions(dataProductDefinitionsPath)
      .map { summaryMapper.map(it, renderMethod, userToken) }
      .filter { containsReportVariantsOrDashboards(it) }
  }

  fun getDefinition(
    reportId: String,
    variantId: String,
    userToken: DprAuthAwareAuthenticationToken?,
    dataProductDefinitionsPath: String? = null,
  ): SingleVariantReportDefinition {
    val singleReportDefinitionDefinition = productDefinitionRepository.getSingleReportProductDefinition(reportId, variantId, dataProductDefinitionsPath)
    checkAuth(singleReportDefinitionDefinition, userToken)
    return mapper.mapReport(
      definition = singleReportDefinitionDefinition,
      userToken = userToken,
      dataProductDefinitionsPath = dataProductDefinitionsPath,
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
  private fun containsReportVariantsOrDashboards(it: ReportDefinitionSummary) =
    it.variants.isNotEmpty() || hasDashboards(it)
  private fun hasDashboards(it: ReportDefinitionSummary) =
    (it.dashboards?.isNotEmpty() ?: false)
}
