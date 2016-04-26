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

package akka.persistence.jdbc.dao.varcharmeta

import akka.persistence.jdbc.dao.varcharmeta.JournalTables._
import akka.persistence.jdbc.extension.{ DeletedToTableConfiguration, JournalTableConfiguration }

object JournalTables {

  case class JournalRow(
    persistenceId: String,
    sequenceNumber: Long,
    message: String,
    created: Long,
    manifest: String,
    deleted: Boolean,
    writerUuid: String,
    tags: Option[String] = None
  )

  case class JournalDeletedToRow(persistenceId: String, deletedTo: Long)
}

trait JournalTables {
  val profile: slick.driver.JdbcProfile

  import profile.api._

  def journalTableCfg: JournalTableConfiguration

  def deletedToTableCfg: DeletedToTableConfiguration

  class Journal(_tableTag: Tag) extends Table[JournalRow](_tableTag, _schemaName = journalTableCfg.schemaName, _tableName = journalTableCfg.tableName) {
    def * = (persistenceId, sequenceNumber, message, created, manifest, deleted, writerUuid, tags) <> (JournalRow.tupled, JournalRow.unapply)

    val persistenceId: Rep[String] = column[String](journalTableCfg.columnNames.persistenceId, O.Length(255, varying = true))
    val sequenceNumber: Rep[Long] = column[Long](journalTableCfg.columnNames.sequenceNumber)
    val message: Rep[String] = column[String](journalTableCfg.columnNames.message)
    val created: Rep[Long] = column[Long](journalTableCfg.columnNames.created)
    val manifest: Rep[String] = column[String]("manifest")
    val deleted: Rep[Boolean] = column[Boolean]("deleted")
    val writerUuid: Rep[String] = column[String]("writer_uuid")
    val tags: Rep[Option[String]] = column[String](journalTableCfg.columnNames.tags, O.Length(255, varying = true))
    val pk = primaryKey("journal_pk", (persistenceId, sequenceNumber))
  }

  lazy val JournalTable = new TableQuery(tag ⇒ new Journal(tag))

  class DeletedTo(_tableTag: Tag) extends Table[JournalDeletedToRow](_tableTag, _schemaName = deletedToTableCfg.schemaName, _tableName = deletedToTableCfg.tableName) {
    def * = (persistenceId, deletedTo) <> (JournalDeletedToRow.tupled, JournalDeletedToRow.unapply)

    val persistenceId: Rep[String] = column[String](deletedToTableCfg.columnNames.persistenceId, O.Length(255, varying = true))
    val deletedTo: Rep[Long] = column[Long](deletedToTableCfg.columnNames.deletedTo)
  }

  lazy val DeletedToTable = new TableQuery(tag ⇒ new DeletedTo(tag))
}
