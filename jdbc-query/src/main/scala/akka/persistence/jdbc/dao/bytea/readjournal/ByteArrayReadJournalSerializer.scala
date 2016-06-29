/*
 * Copyright 2016 Dennis Vriend
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akka.persistence.jdbc.dao.bytea.readjournal

import akka.persistence.PersistentRepr
import akka.persistence.jdbc.dao.bytea.readjournal.ReadJournalTables.JournalRow
import akka.persistence.jdbc.serialization.FlowPersistentReprSerializer
import akka.serialization.Serialization

import scala.compat.Platform
import scala.util.Try

object ByteArrayReadJournalSerializer {
  def encodeTags(tags: Set[String], separator: String): Option[String] =
    if (tags.isEmpty) None else Option(tags.mkString(separator))

  def decodeTags(tags: Option[String], separator: String): Set[String] =
    tags.map(_.split(separator).toSet).getOrElse(Set.empty[String])
}

class ByteArrayReadJournalSerializer(serialization: Serialization, separator: String) extends FlowPersistentReprSerializer[JournalRow] {
  import ByteArrayReadJournalSerializer._
  override def serialize(persistentRepr: PersistentRepr, tags: Set[String]): Try[JournalRow] = ???

  override def deserialize(journalRow: JournalRow): Try[(PersistentRepr, Set[String], JournalRow)] = {
    serialization.deserialize(journalRow.message, classOf[PersistentRepr])
      .map((_, decodeTags(journalRow.tags, separator), journalRow))
  }
}
