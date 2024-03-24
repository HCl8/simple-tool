package com.hcl.tool

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

val mapper = jacksonObjectMapper()

fun isJsonStr(str: String): Boolean {
    return runCatching {
        mapper.readTree(str)
    }.isSuccess
}

fun format(json: JsonNode): JsonNode {
    return when (json) {
        is ArrayNode -> {
            for (i in 0..json.size()) {
                val node = json.get(i)
                if (node != null) {
                    json.set(i, format(node))
                }
            }
            json
        }

        is ObjectNode -> {
            val toList = json.fieldNames().toList()
            toList.forEach { json.set<JsonNode>(it, format(json.get(it))) }
            json
        }

        is TextNode -> {
            val str = json.textValue()
            if (isJsonStr(str)) format(mapper.readTree(str)) else json
        }

        else -> json
    }
}