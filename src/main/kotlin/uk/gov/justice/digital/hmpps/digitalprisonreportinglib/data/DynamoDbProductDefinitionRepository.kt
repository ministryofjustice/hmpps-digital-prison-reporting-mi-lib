package uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data

import com.google.common.cache.Cache
import com.google.gson.Gson
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.config.AwsProperties
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.ProductDefinition

class DynamoDbProductDefinitionRepository(
  private val dynamoDbClient: DynamoDbClient,
  private val properties: AwsProperties,
  private val gson: Gson,
  private val definitionsCache: Cache<String, List<ProductDefinition>>? = null,
  identifiedHelper: IdentifiedHelper,
) : AbstractProductDefinitionRepository(identifiedHelper) {
  companion object {
    const val DEFAULT_PATH = "definitions/prisons/orphanage"

    fun getQueryRequest(properties: AwsProperties, path: String, exclusiveStartKey: Map<String, AttributeValue>? = null): QueryRequest {
      val attrValues: Map<String, AttributeValue> = mapOf(":${properties.dynamoDb.categoryFieldName}" to AttributeValue.fromS(path))

      return QueryRequest.builder()
        .tableName(properties.getDynamoDbTableArn())
        .indexName(properties.dynamoDb.categoryIndexName)
        .keyConditionExpression("${properties.dynamoDb.categoryFieldName} = :${properties.dynamoDb.categoryFieldName}")
        .expressionAttributeValues(attrValues)
        .exclusiveStartKey(exclusiveStartKey)
        .build()
    }
  }

  override fun getProductDefinitions(path: String?): List<ProductDefinition> {
    val usePath = path ?: DEFAULT_PATH

    val cachedDefinitions = definitionsCache?.let { cache ->
      path?.let { path -> cache.getIfPresent(path) }
    }
    cachedDefinitions?.let { return it }

    var response = dynamoDbClient.query(getQueryRequest(properties, usePath))
    val items: MutableList<Map<String, AttributeValue>> = mutableListOf()

    while (response.hasLastEvaluatedKey()) {
      items.addAll(response.items())
      response = dynamoDbClient.query(getQueryRequest(properties, usePath, response.lastEvaluatedKey()))
    }

    items.addAll(response.items())

    val definitions = items
      .filter { it[properties.dynamoDb.definitionFieldName] != null }
      .map { gson.fromJson(it[properties.dynamoDb.definitionFieldName]!!.s(), ProductDefinition::class.java) }

    if (definitions.isNotEmpty()) {
      definitionsCache?.put(usePath, definitions)
    }

    return definitions
  }
}
