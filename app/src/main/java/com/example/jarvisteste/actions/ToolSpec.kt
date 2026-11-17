package com.example.jarvisteste.actions

import org.json.JSONArray
import org.json.JSONObject

/**
 * Uma ação que o Jarvis pode executar no aparelho.
 *
 * O Gemini recebe apenas a [declaration] (nome, descrição e parâmetros). Quando
 * decide chamar a ferramenta, o app executa [run] localmente e devolve o texto
 * resultante como observação — o modelo então fala a confirmação ao usuário.
 */
class ToolSpec(
    val name: String,
    val description: String,
    val parameters: JSONObject,
    val run: suspend (JSONObject) -> String
) {
    fun declaration(): JSONObject = JSONObject()
        .put("name", name)
        .put("description", description)
        .put("parameters", parameters)
}

internal fun text(description: String): JSONObject = JSONObject()
    .put("type", "STRING")
    .put("description", description)

internal fun integer(description: String): JSONObject = JSONObject()
    .put("type", "INTEGER")
    .put("description", description)

internal fun choice(description: String, values: List<String>): JSONObject = JSONObject()
    .put("type", "STRING")
    .put("description", description)
    .put("enum", JSONArray(values))

internal fun schema(
    vararg properties: Pair<String, JSONObject>,
    required: List<String>
): JSONObject {
    val props = JSONObject()
    properties.forEach { (key, value) -> props.put(key, value) }
    return JSONObject()
        .put("type", "OBJECT")
        .put("properties", props)
        .put("required", JSONArray(required))
}
