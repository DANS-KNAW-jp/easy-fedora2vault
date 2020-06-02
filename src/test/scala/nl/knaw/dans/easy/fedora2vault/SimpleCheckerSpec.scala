/**
 * Copyright (C) 2020 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.easy.fedora2vault

import java.net.URI

import com.typesafe.scalalogging.Logger
import nl.knaw.dans.easy.fedora2vault.fixture.{ EmdSupport, TestSupportFixture }
import org.scalamock.scalatest.MockFactory
import org.slf4j.{ Logger => UnderlyingLogger }

import scala.util.{ Failure, Success, Try }
import scala.xml.Elem

class SimpleCheckerSpec extends TestSupportFixture with MockFactory with EmdSupport {

  private class MockedBagIndex extends BagIndex(new URI("http://localhost:20120/"))

  private val emdRights = <emd:rights>
                            <dct:accessRights eas:schemeId="common.dcterms.accessrights"
                                >REQUEST_PERMISSION</dct:accessRights>
                          </emd:rights>
  private val emdDoi = <emd:identifier>
                         <dc:identifier eas:identification-system="https://doi.org"
                                        eas:scheme="DOI"
                         >10.17026/test-Iiib-z9p-4ywa</dc:identifier>
                       </emd:identifier>

  private val noBagInTheVault = Some(Success(None))
  private val aBagInTheVault = Some(Success(Some("---")))

  "isSimple" should "succeed" in {
    val emdTitle = <emd:title><dc:title xml:lang="nld">no theme</dc:title></emd:title>
    val emd = parseEmdContent(Seq(emdTitle, emdDoi, emdRights))

    simpleCheckerExpecting(bagIndexExpects = noBagInTheVault, loggerWarnCalledWith = Seq())
      .isSimple(emd, emd2ddm(emd), amd("PUBLISHED"), Seq.empty) shouldBe
      Success(())
  }

  it should "report missing DOI" in {
    val emd = parseEmdContent(emdRights)
    simpleCheckerExpecting(
      bagIndexExpects = None, // no call expected
      loggerWarnCalledWith = Seq(
        "violated 1: DANS DOI not found",
        "violated 5: invalid state SUBMITTED",
      )).isSimple(emd, emd2ddm(emd), amd("SUBMITTED"), Seq.empty) should matchPattern {
      case Failure(t: Throwable) if t.getMessage == "Not a simple dataset. Violates rule 1, 5" =>
    }
  }

  it should "report thematische collectie" in {
    val emdTitle = <emd:title><dc:title xml:lang="nld">thematische collectie</dc:title></emd:title>
    val emd = parseEmdContent(Seq(emdTitle, emdDoi))

    simpleCheckerExpecting(bagIndexExpects = noBagInTheVault, loggerWarnCalledWith = Seq(
      "violated 3: invalid title thematische collectie",
      "violated 4: invalid rights not found",
    )).isSimple(emd, emd2ddm(emd), amd("PUBLISHED"), Seq()) should matchPattern {
      case Failure(t: Throwable) if t.getMessage == "Not a simple dataset. Violates rule 3, 4" =>
    }
  }

  it should "report jump off" in {
    val emdTitle = <emd:title><dc:title xml:lang="nld">thematische collectie</dc:title></emd:title>
    val emd = parseEmdContent(Seq(emdTitle, emdDoi))

    simpleCheckerExpecting(bagIndexExpects = noBagInTheVault, loggerWarnCalledWith = Seq(
      "violated 2: has jump off easy-jumpoff:123",
      "violated 3: invalid title thematische collectie",
      "violated 4: invalid rights not found",
    )).isSimple(emd, emd2ddm(emd), amd("PUBLISHED"), Seq("easy-jumpoff:123")) should matchPattern {
      case Failure(t: Throwable) if t.getMessage == "Not a simple dataset. Violates rule 2, 3, 4" =>
    }
  }

  it should "report invalid status" in {
    val emd = parseEmdContent(emdDoi)

    simpleCheckerExpecting(bagIndexExpects = noBagInTheVault, loggerWarnCalledWith = Seq(
      "violated 4: invalid rights not found",
      "violated 5: invalid state SUBMITTED",
    )).isSimple(emd, emd2ddm(emd), amd("SUBMITTED"), Seq.empty) should matchPattern {
      case Failure(t: Throwable) if t.getMessage == "Not a simple dataset. Violates rule 4, 5" =>
    }
  }

  it should "report invalid relations" in {
    val emd = parseEmdContent(Seq(emdDoi,
      <emd:relation>
          <dct:isVersionOf>https://doi.org/11.111/test-abc-123</dct:isVersionOf>
          <dct:isVersionOf>https://doi.org/10.17026/test-123-456</dct:isVersionOf>
          <dct:isVersionOf>http://www.persistent-identifier.nl/?identifier=urn:nbn:nl:ui:13-2ajw-cq</dct:isVersionOf>
          <eas:replaces>
              <eas:subject-title>Prehistorische bewoning op het World Forum gebied - Den Haag (replaces)</eas:subject-title>
              <eas:subject-identifier eas:scheme="BID1" eas:identification-system="http://pid.org/sys1">ABC1</eas:subject-identifier>
              <eas:subject-link>http://persistent-identifier.nl/?identifier=urn:nbn:nl:ui:13-aka-hff</eas:subject-link>
          </eas:replaces>
      </emd:relation>,
      emdRights
    ))
    simpleCheckerExpecting(bagIndexExpects = noBagInTheVault, loggerWarnCalledWith = Seq(
      "violated 6: DANS relations <dct:isVersionOf>https://doi.org/10.17026/test-123-456</dct:isVersionOf>",
      "violated 6: DANS relations <dct:isVersionOf>http://www.persistent-identifier.nl/?identifier=urn:nbn:nl:ui:13-2ajw-cq</dct:isVersionOf>",
      """violated 6: DANS relations <ddm:replaces scheme="id-type:URN" href="http://persistent-identifier.nl/?identifier=urn:nbn:nl:ui:13-aka-hff">Prehistorische bewoning op het World Forum gebied - Den Haag (replaces)</ddm:replaces>""",
    )).isSimple(emd, emd2ddm(emd), amd("PUBLISHED"), Seq.empty) should matchPattern {
      case Failure(t: Throwable) if t.getMessage == "Not a simple dataset. Violates rule 6" =>
    }
  }

  it should "report existing bag" in {
    val emd = parseEmdContent(Seq(emdDoi, emdRights))
    simpleCheckerExpecting(
      bagIndexExpects = aBagInTheVault,
      loggerWarnCalledWith = Seq("violated 7: is in the vault ---")
    ).isSimple(emd, emd2ddm(emd), amd("PUBLISHED"), Seq.empty) should matchPattern {
      case Failure(t: Throwable) if t.getMessage == "Not a simple dataset. Violates rule 7" =>
    }
  }

  private def amd(state: String): Elem =
    <damd:administrative-md xmlns:damd="http://easy.dans.knaw.nl/easy/dataset-administrative-metadata/" version="0.1">
      <datasetState>{ state }</datasetState>
    </damd:administrative-md>

  private def simpleCheckerExpecting(bagIndexExpects: Option[Try[Option[String]]],
                                     loggerWarnCalledWith: Seq[String],
                                    ): SimpleChecker = {
    val mockedBagIndex: MockedBagIndex = mock[MockedBagIndex]
    bagIndexExpects.foreach(expected =>
      (mockedBagIndex.bagByDoi(_: String)) expects * returning expected once()
    )
    bagIndexExpects.getOrElse(
      (mockedBagIndex.bagByDoi(_: String)) expects * never()
    )

    val mockLogger = mock[UnderlyingLogger]
    (() => mockLogger.isWarnEnabled()) expects() anyNumberOfTimes() returning true
    loggerWarnCalledWith.foreach(s =>
      (mockLogger.warn(_: String)) expects s once()
    )

    new SimpleChecker(mockedBagIndex) {
      override lazy val logger: Logger = Logger(mockLogger)
    }
  }
}