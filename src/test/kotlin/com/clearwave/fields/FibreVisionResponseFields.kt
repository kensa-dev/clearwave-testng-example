package com.clearwave.fields

import dev.kensa.kotest.testsupport.field.xml.XmlField
import dev.kensa.kotest.testsupport.field.xml.XmlListField
import dev.kensa.kotest.testsupport.field.xml.XmlStringField

/**
 * XML field declarations for the FibreVision feasibility response.
 *
 * Each declaration captures an XPath plus a transform where one is needed. The path-string
 * constructors compile the XPath internally and preserve the path so it surfaces as a hover
 * hint in the rendered Kensa report.
 */
object FibreVisionResponseFields {

    val status = XmlStringField("/FeasibilityResponse/Status", "Status")

    val firstProfileType          = XmlStringField("/FeasibilityResponse/Profiles/Profile[1]/Type", "Profile Type")
    val firstProfileDownloadSpeed = XmlField<Int>("/FeasibilityResponse/Profiles/Profile[1]/DownloadSpeed", "Download Speed") {
        it.textContent.toInt()
    }

    val profileTypes        = XmlListField<String>("/FeasibilityResponse/Profiles/Profile/Type", "Profile Types") { it.textContent }
    val profileDescriptions = XmlListField<String>("/FeasibilityResponse/Profiles/Profile/Description", "Profile Descriptions") { it.textContent }
}
