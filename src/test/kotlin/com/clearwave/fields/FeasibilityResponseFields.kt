package com.clearwave.fields

import com.fasterxml.jackson.databind.node.ArrayNode
import dev.kensa.kotest.testsupport.field.json.ArrayNodeField
import dev.kensa.kotest.testsupport.field.json.JsonBooleanField
import dev.kensa.kotest.testsupport.field.json.JsonField
import dev.kensa.kotest.testsupport.field.json.JsonIntField
import dev.kensa.kotest.testsupport.field.json.JsonTextField

/**
 * JSON field declarations for the FeasibilityService response payload.
 *
 * Each declaration captures a JSONPointer plus the typed extraction. Tests then compose them
 * with `of` / `matching` infix operators into readable kotest matchers.
 */
object FeasibilityResponseFields {
    val anAddressPostcode = JsonTextField("/address/postcode")
    val serviceable = JsonBooleanField("/serviceable")
    val profiles = ArrayNodeField("/profiles")

    val profileCount = JsonField("/profiles") { node, path -> (node.at(path) as? ArrayNode)?.size() }

    val fastestProfileSupplier      = JsonTextField("/profiles/0/supplier")
    val fastestProfileDownloadSpeed = JsonIntField("/profiles/0/downloadSpeed")
    val fastestProfileUploadSpeed   = JsonIntField("/profiles/0/uploadSpeed")
    val fastestProfileType          = JsonTextField("/profiles/0/type")
}
