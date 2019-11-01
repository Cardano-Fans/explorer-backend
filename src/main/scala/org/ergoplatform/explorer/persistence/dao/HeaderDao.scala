package org.ergoplatform.explorer.persistence.dao

import cats.implicits._
import doobie.implicits._
import doobie.refined.implicits._
import doobie.{ConnectionIO, Update}
import org.ergoplatform.explorer._
import org.ergoplatform.explorer.persistence.models.Header

object HeaderDao extends Dao {

  val tableName: String = "node_headers"

  val fields: List[String] = List(
    "id",
    "parent_id",
    "version",
    "height",
    "n_bits",
    "difficulty",
    "timestamp",
    "state_root",
    "ad_proofs_root",
    "transactions_root",
    "extension_hash",
    "miner_pk",
    "w",
    "n",
    "d",
    "votes",
    "main_chain"
  )

  def update(h: Header): ConnectionIO[Header] =
    update
      .withUniqueGeneratedKeys[Header](fields: _*)(h -> h.id)

  def updateMany(list: List[Header]): ConnectionIO[List[Header]] =
    update
      .updateManyWithGeneratedKeys[Header](fields: _*)(list.map(h => h -> h.id))
      .compile
      .to[List]

  def get(id: Id): ConnectionIO[Option[Header]] =
    (selectFr ++ fr"where id = $id").query[Header].option

  def getAllByHeight(height: Int): ConnectionIO[List[Header]] =
    (selectFr ++ fr"where height = $height").query[Header].to[List]

  def getHeightOf(id: Id): ConnectionIO[Option[Int]] =
    (fr"select height from" ++ tableNameFr ++ fr"where id = $id").query[Int].option

  private def update: Update[(Header, Id)] =
    Update[(Header, Id)](
      s"update $tableName set ${fields.map(f => s"$f = ?").mkString(", ")} where id = ?"
    )
}
