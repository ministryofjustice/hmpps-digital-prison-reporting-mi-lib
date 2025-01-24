package uk.gov.justice.digital.hmpps.digitalprisonreportinglib.controller.model

import com.fasterxml.jackson.annotation.JsonValue

enum class Template(@JsonValue val template: String) {
  List("list"),
  ListSection("list-section"),
  ListTab("list-tab"),
  Summary("summary"),
  SectionedSummary("summary-section"),
  ParentChild("parent-child"),
}
