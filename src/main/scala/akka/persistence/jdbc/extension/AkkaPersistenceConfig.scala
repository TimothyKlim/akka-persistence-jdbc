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

package akka.persistence.jdbc.extension

import java.util.concurrent.TimeUnit

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.event.{Logging, LoggingAdapter}
import akka.persistence.jdbc.util.ConfigOps._
import com.typesafe.config.Config

import scala.concurrent.duration.FiniteDuration

object AkkaPersistenceConfig extends ExtensionId[AkkaPersistenceConfigImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): AkkaPersistenceConfigImpl = new AkkaPersistenceConfigImpl()(system)

  override def lookup(): ExtensionId[_ <: Extension] = AkkaPersistenceConfig
}

object SlickConfiguration {
  def apply(cfg: Config): SlickConfiguration =
    cfg.withPath("akka-persistence-jdbc.slick") { cfg =>
      SlickConfiguration(
        cfg.as[String]("driver", "slick.driver.PostgresDriver"),
        cfg.as[String]("jndiName")
      )
    }
}

case class SlickConfiguration(slickDriver: String, jndiName: Option[String])

object PersistenceQueryConfiguration {
  def apply(cfg: Config): PersistenceQueryConfiguration =
    cfg.withPath("akka-persistence-jdbc.query") { cfg =>
      PersistenceQueryConfiguration(cfg.as[String]("separator", ","))
    }
}

case class PersistenceQueryConfiguration(separator: String)

case class JournalTableColumnNames(persistenceId: String, sequenceNumber: String, created: String, tags: String, message: String)

case class JournalTableConfiguration(tableName: String, schemaName: Option[String], columnNames: JournalTableColumnNames)

object JournalTableConfiguration {
  def apply(cfg: Config): JournalTableConfiguration =
    cfg.withPath("akka-persistence-jdbc.tables.journal") { cfg =>
      JournalTableConfiguration(
        cfg.as[String]("tableName", "journal"),
        cfg.as[String]("schemaName").map(_.trim).filter(_.nonEmpty),
        cfg.withPath("columnNames") { cfg =>
          JournalTableColumnNames(
            cfg.as[String]("persistenceId", "persistence_id"),
            cfg.as[String]("sequenceNumber", "sequence_nr"),
            cfg.as[String]("created", "created"),
            cfg.as[String]("tags", "tags"),
            cfg.as[String]("message", "message")
          )
        }
      )
    }
}

case class DeletedToTableColumnNames(persistenceId: String, deletedTo: String)

case class DeletedToTableConfiguration(tableName: String, schemaName: Option[String], columnNames: DeletedToTableColumnNames)

object DeletedToTableConfiguration {
  def apply(cfg: Config): DeletedToTableConfiguration =
    cfg.withPath("akka-persistence-jdbc.tables.deletedTo") { cfg =>
      DeletedToTableConfiguration(
        cfg.as[String]("tableName", "journal"),
        cfg.as[String]("schemeName").map(_.trim).filter(_.nonEmpty),
        cfg.withPath("columnNames") { cfg =>
          DeletedToTableColumnNames(
            cfg.as[String]("persistenceId", "persistence_id"),
            cfg.as[String]("deletedTo", "deleted_to")
          )
        }
      )
    }
}

case class SnapshotTableColumnNames(persistenceId: String, sequenceNumber: String, created: String, snapshot: String)

case class SnapshotTableConfiguration(tableName: String, schemaName: Option[String], columnNames: SnapshotTableColumnNames)

object SnapshotTableConfiguration {
  def apply(cfg: Config): SnapshotTableConfiguration =
    cfg.withPath("akka-persistence-jdbc.tables.snapshot") { cfg =>
      SnapshotTableConfiguration(
        cfg.as[String]("tableName", "journal"),
        cfg.as[String]("schemeName").map(_.trim).filter(_.nonEmpty),
        cfg.withPath("columnNames") { cfg =>
          SnapshotTableColumnNames(
            cfg.as[String]("persistenceId", "persistence_id"),
            cfg.as[String]("sequenceNumber", "sequence_nr"),
            cfg.as[String]("created", "created"),
            cfg.as[String]("snapshot", "snapshot")
          )
        }
      )
    }
}

trait AkkaPersistenceConfig {

  def slickConfiguration: SlickConfiguration

  def persistenceQueryConfiguration: PersistenceQueryConfiguration

  def journalTableConfiguration: JournalTableConfiguration

  def deletedToTableConfiguration: DeletedToTableConfiguration

  def snapshotTableConfiguration: SnapshotTableConfiguration

  def inMemory: Boolean

  def inMemoryTimeout: FiniteDuration

  def fullSerialization: Boolean
}

class AkkaPersistenceConfigImpl()(implicit val system: ExtendedActorSystem) extends AkkaPersistenceConfig with Extension {
  val log: LoggingAdapter = Logging(system, this.getClass)

  override val slickConfiguration: SlickConfiguration =
    if(inMemory) SlickConfiguration("", None)
    else SlickConfiguration(system.settings.config)

  override val persistenceQueryConfiguration: PersistenceQueryConfiguration =
    if(inMemory) PersistenceQueryConfiguration("")
    else PersistenceQueryConfiguration(system.settings.config)

  override def journalTableConfiguration: JournalTableConfiguration =
  if(inMemory) JournalTableConfiguration("", None, JournalTableColumnNames("", "", "", "", ""))
  else JournalTableConfiguration(system.settings.config)

  override def deletedToTableConfiguration: DeletedToTableConfiguration =
    if(inMemory) DeletedToTableConfiguration("", None, DeletedToTableColumnNames("", ""))
    else DeletedToTableConfiguration(system.settings.config)

  override def snapshotTableConfiguration: SnapshotTableConfiguration =
    if(inMemory) SnapshotTableConfiguration("", None, SnapshotTableColumnNames("", "", "", ""))
    else SnapshotTableConfiguration(system.settings.config)

  override def inMemory: Boolean =
    system.settings.config.getBoolean("akka-persistence-jdbc.inMemory")

  override def inMemoryTimeout: FiniteDuration =
    FiniteDuration(system.settings.config.getDuration("akka-persistence-jdbc.inMemoryTimeout", TimeUnit.SECONDS), TimeUnit.SECONDS)

  override def fullSerialization: Boolean =
    system.settings.config.getBoolean("akka-persistence-jdbc.fullSerialization")

  def debugInfo: String =
    s"""
       | ====================================
       | Akka Persistence JDBC Configuration:
       | ====================================
       | InMemory mode: $inMemory
       | ====================================
       | $slickConfiguration
       | ====================================
       | $persistenceQueryConfiguration
       | ====================================
       | $journalTableConfiguration
       | ====================================
       | $deletedToTableConfiguration
       | ====================================
       | $snapshotTableConfiguration
       | ====================================
    """.stripMargin

  log.debug(debugInfo)
}
