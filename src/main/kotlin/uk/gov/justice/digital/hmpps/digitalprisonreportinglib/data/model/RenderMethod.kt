package uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model

import com.google.gson.annotations.SerializedName

enum class RenderMethod {
  HTML,

  @SerializedName("HTML-child")
  HTMLChild,
  PDF,
  SVG,
}
