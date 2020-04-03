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
package nl.knaw.dans.easy

import org.joda.time.format.{ DateTimeFormatter, ISODateTimeFormat }
import org.joda.time.{ DateTime, DateTimeZone }

import scala.util.{ Failure, Try }
import scala.xml.{ Node, PrettyPrinter, Utility }

package object fedora2vault {

  type DatasetId = String
  type Depositor = String

  type LdapEnv = java.util.Hashtable[String, String]

  val dateTimeFormatter: DateTimeFormatter = ISODateTimeFormat.dateTime()

  def now: String = DateTime.now(DateTimeZone.UTC).toString(dateTimeFormatter)

  val prologue = """<?xml version='1.0' encoding='UTF-8'?>"""

  implicit class XmlExtensions(val elem: Node) extends AnyVal {

    def serialize: String = {
      val printer = new PrettyPrinter(160, 2)
      val trimmed = Utility.trim(elem)
      prologue + "\n" + printer.format(trimmed)
    }
  }

  implicit class RichTries[T](val tries: TraversableOnce[Try[T]]) extends AnyVal {
    // TODO candidate for nl.knaw.dans.lib.error ?
    //  copied from https://github.com/DANS-KNAW/easy-deposit-api/blob/ff109d27d2f2548c9e053c34d41627a539a381d9/src/main/scala/nl.knaw.dans.easy.deposit/package.scala#L48
    def failFastOr[R](onSuccess: => Try[R]): Try[R] = {
      tries
        .collectFirst { case Failure(e) => Failure(e) }
        .getOrElse(onSuccess)
    }
  }
}