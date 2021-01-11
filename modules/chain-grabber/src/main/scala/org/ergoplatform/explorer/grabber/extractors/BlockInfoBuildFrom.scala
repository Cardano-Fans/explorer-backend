package org.ergoplatform.explorer.grabber.extractors

import cats.Monad
import org.ergoplatform.explorer.Err.{ProcessingErr, RefinementFailed}
import org.ergoplatform.explorer.db.models.BlockInfo
import org.ergoplatform.explorer.grabber.models.SlotData
import org.ergoplatform.explorer.grabber.modules.BuildFrom
import org.ergoplatform.explorer.protocol.constants
import org.ergoplatform.explorer.protocol.models.ApiFullBlock
import org.ergoplatform.explorer.settings.ProtocolSettings
import org.ergoplatform.explorer.{Address, CRaise}
import org.ergoplatform.{ErgoScriptPredef, Pay2SAddress}
import scorex.util.encode.Base16
import sigmastate.basics.DLogProtocol.ProveDlog
import sigmastate.interpreter.CryptoConstants.EcPointType
import sigmastate.serialization.{GroupElementSerializer, SigmaSerializer}
import tofu.WithContext
import tofu.syntax.context._
import tofu.syntax.monadic._
import tofu.syntax.raise._

import scala.util.Try

final class BlockInfoBuildFrom[
  F[_]: Monad: WithContext[*[_], ProtocolSettings]: CRaise[*[_], ProcessingErr]: CRaise[*[_], RefinementFailed]
] extends BuildFrom[F, SlotData, BlockInfo] {

  override def apply(slot: SlotData): F[BlockInfo] =
    context.flatMap { protocolSettings =>
      val SlotData(apiBlock, prevBlockInfo) = slot
      minerRewardAddress(apiBlock)(protocolSettings).map { minerAddress =>
        val (reward, fee) = minerRewardAndFee(apiBlock)(protocolSettings)
        val coinBaseValue = reward + fee
        val blockCoins = apiBlock.transactions.transactions
          .flatMap(_.outputs.toList)
          .map(_.value)
          .sum - coinBaseValue
        val miningTime = apiBlock.header.timestamp - prevBlockInfo
          .map(_.timestamp)
          .getOrElse(0L)

        BlockInfo(
          headerId   = apiBlock.header.id,
          timestamp  = apiBlock.header.timestamp,
          height     = apiBlock.header.height,
          difficulty = apiBlock.header.difficulty.value.toLong,
          blockSize  = apiBlock.size,
          blockCoins = blockCoins,
          blockMiningTime = apiBlock.header.timestamp - prevBlockInfo
            .map(_.timestamp)
            .getOrElse(0L),
          txsCount     = apiBlock.transactions.transactions.length,
          txsSize      = apiBlock.transactions.transactions.map(_.size).sum,
          minerAddress = minerAddress,
          minerReward  = reward,
          minerRevenue = reward + fee,
          blockFee     = fee,
          blockChainTotalSize = prevBlockInfo
            .map(_.blockChainTotalSize)
            .getOrElse(0L) + apiBlock.size,
          totalTxsCount = apiBlock.transactions.transactions.length.toLong + prevBlockInfo
            .map(_.totalTxsCount)
            .getOrElse(0L),
          totalCoinsIssued = protocolSettings.emission.issuedCoinsAfterHeight(apiBlock.header.height.toLong),
          totalMiningTime = prevBlockInfo
            .map(_.totalMiningTime)
            .getOrElse(0L) + miningTime,
          totalFees = prevBlockInfo.map(_.totalFees).getOrElse(0L) + fee,
          totalMinersReward = prevBlockInfo
            .map(_.totalMinersReward)
            .getOrElse(0L) + reward,
          totalCoinsInTxs = prevBlockInfo.map(_.totalCoinsInTxs).getOrElse(0L) + blockCoins,
          mainChain       = apiBlock.header.mainChain
        )
      }
    }

  private def minerRewardAddress(
    apiBlock: ApiFullBlock
  )(protocolSettings: ProtocolSettings): F[Address] =
    Base16
      .decode(apiBlock.header.minerPk.unwrapped)
      .flatMap { bytes =>
        Try(GroupElementSerializer.parse(SigmaSerializer.startReader(bytes)))
      }
      .fold[F[EcPointType]](
        e => ProcessingErr.EcPointDecodingFailed(e.getMessage).raise,
        _.pure[F]
      )
      .flatMap { x =>
        val minerPk = ProveDlog(x)
        val rewardScript =
          ErgoScriptPredef.rewardOutputScript(
            protocolSettings.monetary.minerRewardDelay,
            minerPk
          )
        val addressStr =
          Pay2SAddress(rewardScript)(protocolSettings.addressEncoder).toString
        Address.fromString(addressStr)
      }

  private def minerRewardAndFee(
    apiBlock: ApiFullBlock
  )(protocolSettings: ProtocolSettings): (Long, Long) = {
    val emission = protocolSettings.emission.emissionAtHeight(apiBlock.header.height.toLong)
    val reward   = math.min(constants.TeamTreasuryThreshold, emission)
    val fee = apiBlock.transactions.transactions
      .flatMap(_.outputs.toList)
      .filter(_.ergoTree.unwrapped == constants.FeePropositionScriptHex)
      .map(_.value)
      .sum
    (reward, fee)
  }
}
