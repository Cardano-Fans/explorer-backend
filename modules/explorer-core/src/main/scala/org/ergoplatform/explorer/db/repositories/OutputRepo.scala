package org.ergoplatform.explorer.db.repositories

import cats.data.NonEmptyList
import cats.effect.Sync
import cats.implicits._
import doobie.free.implicits._
import doobie.refined.implicits._
import doobie.util.log.LogHandler
import fs2.Stream
import org.ergoplatform.explorer._
import org.ergoplatform.explorer.db.DoobieLogHandler
import org.ergoplatform.explorer.db.algebra.LiftConnectionIO
import org.ergoplatform.explorer.db.doobieInstances._
import org.ergoplatform.explorer.db.models.Output
import org.ergoplatform.explorer.db.models.aggregates.ExtendedOutput
import org.ergoplatform.explorer.db.syntax.liftConnectionIO._

/** [[Output]] and [[ExtendedOutput]] data access operations.
  */
trait OutputRepo[D[_], S[_[_], _]] {

  /** Put a given `output` to persistence.
    */
  def insert(output: Output): D[Unit]

  /** Put a given list of outputs to persistence.
    */
  def insertMany(outputs: List[Output]): D[Unit]

  /** Get an output with a given `boxId` from persistence.
    */
  def getByBoxId(boxId: BoxId): D[Option[ExtendedOutput]]

  /** Get all outputs with a given `ergoTree` appeared in the blockchain before `maxHeight`.
    */
  def getAllByErgoTree(ergoTree: HexString, maxHeight: Int): D[List[ExtendedOutput]]

  /** Get all unspent main-chain outputs with a given `ergoTree` from persistence.
    */
  def getAllMainUnspentIdsByErgoTree(ergoTree: HexString): D[List[BoxId]]

  /** Get total amount of all main-chain outputs with a given `ergoTree`.
    */
  def sumAllByErgoTree(ergoTree: HexString, minConfirmations: Int): D[Long]

  /** Get total amount of all unspent main-chain outputs with a given `ergoTree`.
    */
  def sumUnspentByErgoTree(ergoTree: HexString, minConfirmations: Int): D[Long]

  /** Get balances of all addresses in the network.
    */
  def balanceStatsMain(offset: Int, limit: Int): D[List[(Address, Long)]]

  /** Get total number of addresses in the network.
    */
  def totalAddressesMain: D[Int]

  /** Get outputs with a given `ergoTree` from persistence.
    */
  def streamAllByErgoTree(ergoTree: HexString, offset: Int, limit: Int): S[D, ExtendedOutput]

  /** Get unspent main-chain outputs with a given `ergoTree` from persistence.
    */
  def streamUnspentByErgoTree(
    ergoTree: HexString,
    offset: Int,
    limit: Int
  ): S[D, ExtendedOutput]

  /** Get all main-chain outputs that are protected with given ergo tree template.
    */
  def streamAllByErgoTreeTemplate(
    template: ErgoTreeTemplate,
    offset: Int,
    limit: Int
  ): S[D, ExtendedOutput]

  /** Get all unspent main-chain outputs that are protected with given ergo tree template.
    */
  def streamUnspentByErgoTreeTemplate(
    template: ErgoTreeTemplate,
    offset: Int,
    limit: Int
  ): S[D, Output]

  def streamUnspentByErgoTreeTemplateAndTokenId(
    ergoTreeTemplate: ErgoTreeTemplate,
    tokenId: TokenId,
    offset: Int,
    limit: Int
  ): Stream[D, ExtendedOutput]

  /** Get all main-chain outputs that are protected with given ergo tree template.
    */
  def streamAllByErgoTreeTemplateByEpochs(
    template: ErgoTreeTemplate,
    minHeight: Int,
    maxHeight: Int
  ): S[D, ExtendedOutput]

  /** Get all unspent main-chain outputs that are protected with given ergo tree template.
    */
  def streamUnspentByErgoTreeTemplateByEpochs(
    template: ErgoTreeTemplate,
    minHeight: Int,
    maxHeight: Int
  ): S[D, Output]

  /** Get all outputs related to a given `txId`.
    */
  def getAllByTxId(txId: TxId): D[List[ExtendedOutput]]

  /** Get all outputs related to a given list of `txId`.
    */
  def getAllByTxIds(txsId: NonEmptyList[TxId]): D[List[ExtendedOutput]]

  /** Get all addresses matching the given `query`.
    */
  def getAllLike(query: String): D[List[Address]]

  def sumOfAllUnspentOutputsSince(ts: Long): D[BigDecimal]

  def estimatedOutputsSince(ts: Long)(genesisAddress: Address): D[BigDecimal]

  /** Update main_chain status for all outputs related to given `headerId`.
    */
  def updateChainStatusByHeaderId(headerId: Id, newChainStatus: Boolean): D[Unit]

  /** Get all unspent outputs appeared in the main chain after `minHeight`.
    */
  def getAllMainUnspent(minHeight: Int, maxHeight: Int): S[D, Output]

  def getAllByTokenId(tokenId: TokenId, offset: Int, limit: Int): S[D, ExtendedOutput]

  def getUnspentByTokenId(tokenId: TokenId, offset: Int, limit: Int): S[D, Output]
}

object OutputRepo {

  def apply[F[_]: Sync, D[_]: LiftConnectionIO]: F[OutputRepo[D, Stream]] =
    DoobieLogHandler.create[F].map { implicit lh =>
      new Live[D]
    }

  final private class Live[D[_]: LiftConnectionIO](implicit lh: LogHandler) extends OutputRepo[D, Stream] {

    import org.ergoplatform.explorer.db.queries.{OutputQuerySet => QS}

    private val liftK = LiftConnectionIO[D].liftConnectionIOK

    def insert(output: Output): D[Unit] =
      QS.insert(output).void.liftConnectionIO

    def insertMany(outputs: List[Output]): D[Unit] =
      QS.insertMany(outputs).void.liftConnectionIO

    def getByBoxId(boxId: BoxId): D[Option[ExtendedOutput]] =
      QS.getByBoxId(boxId).option.liftConnectionIO

    def getAllByErgoTree(ergoTree: HexString, maxHeight: Int): D[List[ExtendedOutput]] =
      QS.getMainByErgoTree(ergoTree, offset = 0, limit = Int.MaxValue, maxHeight = maxHeight)
        .to[List]
        .liftConnectionIO

    def streamAllByErgoTree(
      ergoTree: HexString,
      offset: Int,
      limit: Int
    ): Stream[D, ExtendedOutput] =
      QS.getMainByErgoTree(ergoTree, offset, limit).stream.translate(liftK)

    def getAllMainUnspentIdsByErgoTree(ergoTree: HexString): D[List[BoxId]] =
      QS.getAllMainUnspentIdsByErgoTree(ergoTree)
        .to[List]
        .liftConnectionIO

    def sumAllByErgoTree(ergoTree: HexString, maxHeight: Int): D[Long] =
      QS.sumAllByErgoTree(ergoTree, maxHeight).unique.liftConnectionIO

    def sumUnspentByErgoTree(ergoTree: HexString, maxHeight: Int): D[Long] =
      QS.sumUnspentByErgoTree(ergoTree, maxHeight).unique.liftConnectionIO

    def balanceStatsMain(offset: Int, limit: Int): D[List[(Address, Long)]] =
      QS.balanceStatsMain(offset, limit).to[List].liftConnectionIO

    def totalAddressesMain: D[Int] =
      QS.totalAddressesMain.unique.liftConnectionIO

    def streamUnspentByErgoTree(
      ergoTree: HexString,
      offset: Int,
      limit: Int
    ): Stream[D, ExtendedOutput] =
      QS.getMainUnspentByErgoTree(ergoTree, offset, limit).stream.translate(liftK)

    def streamAllByErgoTreeTemplate(template: ErgoTreeTemplate, offset: Int, limit: Int): Stream[D, ExtendedOutput] =
      QS.getAllByErgoTreeTemplate(template, offset, limit).stream.translate(liftK)

    def streamUnspentByErgoTreeTemplate(template: ErgoTreeTemplate, offset: Int, limit: Int): Stream[D, Output] =
      QS.getUnspentByErgoTreeTemplate(template, offset, limit).stream.translate(liftK)

    def streamAllByErgoTreeTemplateByEpochs(
      template: ErgoTreeTemplate,
      minHeight: Int,
      maxHeight: Int
    ): Stream[D, ExtendedOutput] =
      QS.getAllByErgoTreeTemplateByEpochs(template, minHeight, maxHeight).stream.translate(liftK)

    def streamUnspentByErgoTreeTemplateByEpochs(
      template: ErgoTreeTemplate,
      minHeight: Int,
      maxHeight: Int
    ): Stream[D, Output] =
      QS.getUnspentByErgoTreeTemplateByEpochs(template, minHeight, maxHeight).stream.translate(liftK)

    def getAllByTxId(txId: TxId): D[List[ExtendedOutput]] =
      QS.getAllByTxId(txId).to[List].liftConnectionIO

    def getAllByTxIds(txIds: NonEmptyList[TxId]): D[List[ExtendedOutput]] =
      QS.getAllByTxIds(txIds).to[List].liftConnectionIO

    def streamUnspentByErgoTreeTemplateAndTokenId(
      ergoTreeTemplate: ErgoTreeTemplate,
      tokenId: TokenId,
      offset: Int,
      limit: Int
    ): Stream[D, ExtendedOutput] =
      QS.getUnspentByErgoTreeTemplateAndTokenId(
        ergoTreeTemplate,
        tokenId,
        offset,
        limit
      ).stream
        .translate(liftK)

    def getAllLike(query: String): D[List[Address]] =
      QS.getAllLike(query).to[List].liftConnectionIO

    def sumOfAllUnspentOutputsSince(ts: Long): D[BigDecimal] =
      QS.sumOfAllUnspentOutputsSince(ts).unique.liftConnectionIO

    def estimatedOutputsSince(ts: Long)(genesisAddress: Address): D[BigDecimal] =
      QS.estimatedOutputsSince(ts)(genesisAddress).unique.liftConnectionIO

    def updateChainStatusByHeaderId(headerId: Id, newChainStatus: Boolean): D[Unit] =
      QS.updateChainStatusByHeaderId(headerId, newChainStatus).run.void.liftConnectionIO

    def getAllMainUnspent(minHeight: Int, maxHeight: Int): Stream[D, Output] =
      QS.getUnspent(minHeight, maxHeight).stream.translate(liftK)

    def getAllByTokenId(tokenId: TokenId, offset: Int, limit: Int): Stream[D, ExtendedOutput] =
      QS.getAllByTokenId(tokenId, offset, limit).stream.translate(liftK)

    def getUnspentByTokenId(tokenId: TokenId, offset: Int, limit: Int): Stream[D, Output] =
      QS.getUnspentByTokenId(tokenId, offset, limit).stream.translate(liftK)
  }
}
