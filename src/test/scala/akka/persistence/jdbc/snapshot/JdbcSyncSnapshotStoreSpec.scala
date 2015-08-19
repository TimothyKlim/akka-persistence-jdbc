/*
 * Copyright 2015 Dennis Vriend
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

package akka.persistence.jdbc.snapshot

import akka.event.Logging
import akka.persistence.SnapshotProtocol.{ LoadSnapshotResult, LoadSnapshot, SaveSnapshot }
import akka.persistence.jdbc.actor.MacBeth
import akka.persistence.jdbc.common.PluginConfig
import akka.persistence.jdbc.extension.ScalikeExtension
import akka.persistence.jdbc.util._
import akka.persistence.snapshot.SnapshotStoreSpec
import akka.persistence.{ SelectedSnapshot, SnapshotSelectionCriteria, SaveSnapshotSuccess, SnapshotMetadata }
import akka.testkit.TestProbe
import com.typesafe.config.ConfigFactory
import scalikejdbc.DBSession
import scala.concurrent.duration._

trait JdbcSyncSnapshotStoreSpec extends SnapshotStoreSpec with JdbcInit {
  val cfg = PluginConfig(system)
  lazy val config = ConfigFactory.load("application.conf")
  val log = Logging(system, this.getClass)

  "The snapshot store must also" must {
    "be able to store a snapshot when the state has not changed" in {
      val senderProbe = TestProbe()
      val metadata = SnapshotMetadata("same-pid", 1)
      snapshotStore.tell(SaveSnapshot(metadata, MacBeth.text), senderProbe.ref)
      senderProbe.expectMsgPF() { case SaveSnapshotSuccess(md) ⇒ md }
      snapshotStore.tell(SaveSnapshot(metadata, MacBeth.text), senderProbe.ref)
      senderProbe.expectMsgPF() { case SaveSnapshotSuccess(md) ⇒ md }
    }

    "be able to store a whole lot of snapshots without running out of memory when requesting latest shapshot with latest timestamp" in {
      val senderProbe = TestProbe()
      val pid = "pid-1000-get-latest"
      (1 to 1000).toStream.foreach { seqNo ⇒
        if (seqNo % 100 == 0) log.info("{}", seqNo)
        val metadata = SnapshotMetadata(persistenceId = pid, sequenceNr = seqNo, timestamp = System.currentTimeMillis())
        snapshotStore.tell(SaveSnapshot(metadata, MacBeth.text), senderProbe.ref)
        senderProbe.expectMsgPF(10.minute) {
          case SaveSnapshotSuccess(md) ⇒ md
          case notSucess: Object ⇒
            log.error(notSucess.toString)
        }
      }
      snapshotStore.tell(LoadSnapshot(pid, SnapshotSelectionCriteria.Latest, Long.MaxValue), senderProbe.ref)
      senderProbe.expectMsgPF() {
        case lssr @ LoadSnapshotResult(Some(SelectedSnapshot(SnapshotMetadata(`pid`, 1000, _), MacBeth.text)), _) ⇒ lssr
      }
    }

    "be able to store a whole lot of snapshots without running out of memory when requesting one but last snapshot" in {
      val senderProbe = TestProbe()
      val pid = "pid-1000-get-999"
      (1 to 1000).toStream.foreach { seqNo ⇒
        val metadata = SnapshotMetadata(persistenceId = pid, sequenceNr = seqNo, timestamp = System.currentTimeMillis())
        snapshotStore.tell(SaveSnapshot(metadata, MacBeth.text), senderProbe.ref)
        senderProbe.expectMsgPF(1.minute) { case SaveSnapshotSuccess(md) ⇒ md }
      }
      snapshotStore.tell(LoadSnapshot(pid, SnapshotSelectionCriteria(999, Long.MaxValue), Long.MaxValue), senderProbe.ref)
      senderProbe.expectMsgPF(3.minute) {
        case lssr @ LoadSnapshotResult(Some(SelectedSnapshot(SnapshotMetadata(`pid`, 999, _), MacBeth.text)), _) ⇒ lssr
      }
    }

    "be able to store a whole lot of snapshots with the same seqNo" in {
      val senderProbe = TestProbe()
      val pid = "pid-1000-save-same-id"
      Stream.continually(1).take(1000000).foreach { seqNo ⇒
        val metadata = SnapshotMetadata(persistenceId = pid, sequenceNr = seqNo, timestamp = System.currentTimeMillis())
        snapshotStore.tell(SaveSnapshot(metadata, MacBeth.text), senderProbe.ref)
        senderProbe.expectMsgPF(1.minute) { case SaveSnapshotSuccess(md) ⇒ md }
      }
    }
  }

  override def beforeAll() {
    dropJournalTable()
    createJournalTable()
    dropSnapshotTable()
    createSnapshotTable()
    super.beforeAll()
  }

  override def afterAll() {
    super.afterAll()
  }
}

trait GenericSyncSnapshotStoreSpec extends JdbcSyncSnapshotStoreSpec {
  implicit val session: DBSession = ScalikeExtension(system).session
}

class H2JdbcSyncSnapshotStoreSpec extends GenericSyncSnapshotStoreSpec with H2JdbcInit {
  override lazy val config = ConfigFactory.load("h2-application.conf")
}

class PostgresqlJdbcSyncSnapshotStoreSpec extends GenericSyncSnapshotStoreSpec with PostgresqlJdbcInit {
  override lazy val config = ConfigFactory.load("postgres-application.conf")
}

//class MssqlSyncSnapshotStoreSpec extends GenericSyncSnapshotStoreSpec with MssqlJdbcInit {
//  override lazy val config = ConfigFactory.load("mssql-application.conf")
//}

class MysqlSyncSnapshotStoreSpec extends GenericSyncSnapshotStoreSpec with MysqlJdbcInit {
  override lazy val config = ConfigFactory.load("mysql-application.conf")
}

class OracleSyncSnapshotStoreSpec extends GenericSyncSnapshotStoreSpec with OracleJdbcInit {
  override lazy val config = ConfigFactory.load("oracle-application.conf")
}

//class InformixSyncSnapshotStoreSpec extends GenericSyncSnapshotStoreSpec with InformixJdbcInit {
//  override lazy val config = ConfigFactory.load("informix-application.conf")
//}
