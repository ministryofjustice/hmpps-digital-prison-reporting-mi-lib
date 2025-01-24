package uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Identified
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.Identified.Companion.REF_PREFIX

@Component
class IdentifiedHelper {
  final inline fun <reified T : Identified> findOrFail(all: List<T>?, id: String?): T =
    findOrNull(all, id) ?: throw IllegalArgumentException("Invalid ${T::class.simpleName} ID: ${cleanId(id)}")

  final inline fun <reified T : Identified> findOrNull(all: List<T>?, id: String?): T? {
    val cleanId = cleanId(id)
    return all?.find { cleanId(it.getIdentifier()) == cleanId }
  }

  fun cleanId(id: String?) = id?.removePrefix(REF_PREFIX)
}
