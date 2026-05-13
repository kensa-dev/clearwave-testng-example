package com.clearwave.support

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT
import dev.kensa.render.InteractionRenderer
import dev.kensa.render.Language
import dev.kensa.render.RenderedAttributes
import dev.kensa.render.RenderedInteraction
import dev.kensa.util.Attributes
import dev.kensa.util.NamedValue
import org.http4k.core.Request
import org.http4k.core.Response

fun String.prettyPrintJson(): String {
    val mapper = ObjectMapper().enable(INDENT_OUTPUT)
    return mapper.writeValueAsString(mapper.readTree(this))
}

/**
 * Renders http4k [Response] objects in the Kensa sequence diagram.
 * Detects JSON vs XML from the response body and pretty-prints accordingly.
 */
object RequestRenderer : InteractionRenderer<Request> {
    override fun render(value: Request, attributes: Attributes): List<RenderedInteraction> {
        val body = value.bodyString()
        if (body.isBlank()) return emptyList()
        val language = if (body.trimStart().startsWith("<")) Language.Xml else Language.Json
        val prettyBody = if (language == Language.Json) body.prettyPrintJson() else body
        return listOf(RenderedInteraction("Request Body", prettyBody, language))
    }

    override fun renderAttributes(value: Request): List<RenderedAttributes> = listOf(
        RenderedAttributes("Request", setOf(NamedValue("Method", value.method.name), NamedValue("URI", value.uri.toString()))),
        RenderedAttributes("Headers", value.headers.map { NamedValue(it.first, it.second ?: "") }.toSet()),
    )
}

object ResponseRenderer : InteractionRenderer<Response> {
    override fun render(value: Response, attributes: Attributes): List<RenderedInteraction> {
        val body = value.bodyString()
        val language = if (body.trimStart().startsWith("<")) Language.Xml else Language.Json
        val prettyBody = if (language == Language.Json) body.prettyPrintJson() else body
        return listOf(RenderedInteraction("Response Body", prettyBody, language))
    }

    override fun renderAttributes(value: Response): List<RenderedAttributes> = listOf(
        RenderedAttributes("Status",  setOf(NamedValue("Status",  value.status.code.toString()))),
        RenderedAttributes("Headers", value.headers.map { NamedValue(it.first, it.second ?: "") }.toSet()),
    )
}
