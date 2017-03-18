package com.wavesplatform.state2

import com.wavesplatform.settings.FunctionalitySettings
import com.wavesplatform.state2.diffs.BlockDiffer
import com.wavesplatform.state2.reader.StateReader
import play.api.libs.json.JsObject
import scorex.account.{Account, Alias}
import scorex.block.Block
import scorex.transaction._
import scorex.transaction.assets.IssueTransaction
import scorex.transaction.assets.exchange.{ExchangeTransaction, Order}
import scorex.transaction.state.database.state.{AccState, AddressString, Reasons}

import scala.reflect.ClassTag
import scala.util.{Failure, Try}

class StateWriterAdapter(r: StateWriter with StateReader, settings: FunctionalitySettings) extends State {
  override def included(signature: Array[Byte]): Option[Int] = r.transactionInfo(EqByteArray(signature)).map(_._1)

  override def findTransaction[T <: Transaction](signature: Array[Byte])(implicit ct: ClassTag[T]): Option[T]
  = r.findTransaction(signature)

  override def accountTransactions(account: Account, limit: Int): Seq[_ <: Transaction] =
    r.accountTransactionIds(account).flatMap(r.transactionInfo).map(_._2)

  override def lastAccountPaymentTransaction(account: Account): Option[PaymentTransaction] = {
    r.lastAccountPaymentTransaction(account)
  }

  override def balance(account: Account): Long = r.accountPortfolio(account).balance

  override def assetBalance(account: AssetAcc): Long =
    r.accountPortfolio(account.account)
      .assets
      .getOrElse(EqByteArray(account.assetId.get), 0)

  override def getAccountBalance(account: Account): Map[AssetId, (Long, Boolean, Long, IssueTransaction)] =
    r.accountPortfolio(account).assets.map { case (id, amt) =>
      val assetInfo = r.assetInfo(id).get
      id.arr -> (amt, assetInfo.isReissuable, assetInfo.volume, findTransaction[IssueTransaction](id.arr).get)
    }

  override def assetDistribution(assetId: Array[Byte]): Map[String, Long] =
    r.assetDistribution(EqByteArray(assetId))
      .map { case (acc, amt) => (acc.address, amt) }

  override def effectiveBalance(account: Account): Long = r.accountPortfolio(account).effectiveBalance

  override def getLeasedSum(address: AddressString): Long = {
    val portfolio = r.accountPortfolio(Account.fromString(address).right.get)
    portfolio.effectiveBalance - portfolio.balance
  }

  override def isReissuable(id: Array[Byte]): Boolean =
    r.assetInfo(EqByteArray(id)).get.isReissuable

  override def totalAssetQuantity(assetId: AssetId): Long =
    r.assetInfo(EqByteArray(assetId)).get.volume

  override def balanceWithConfirmations(account: Account, confirmations: Int): Long = ???

  override def wavesDistributionAtHeight(height: Int): Seq[(AddressString, Long)] = ???

  override def effectiveBalanceWithConfirmations(account: Account, confirmations: Int, height: Int): Long =
    r.effectiveBalanceAtHeightWithConfirmations(account, height, confirmations)

  override def findPrevOrderMatchTxs(order: Order): Set[ExchangeTransaction] = ???

  override def resolveAlias(a: Alias): Option[Account] = ???

  override def getAlias(a: Account): Option[Alias] = ???

  override def stateHeight: Int = r.height

  override def toJson(heightOpt: Option[Int]): JsObject = ???

  override def processBlock(block: Block): Try[State] = Try {
    BlockDiffer(settings)(r, block) match {
      case Right(blockDiff) =>
        r.applyBlockDiff(blockDiff)
        this
      case Left(m) => ???
    }
  }


  override def rollbackTo(height: Int): State = ???

  override def applyChanges(changes: Map[AssetAcc, (AccState, Reasons)], blockTs: Long): Unit = ???

  override def calcNewBalances(trans: Seq[Transaction], fees: Map[AssetAcc, (AccState, Reasons)], allowTemporaryNegative: Boolean): Map[AssetAcc, (AccState, Reasons)] = ???

  override def addAsset(assetId: AssetId, height: Int, transactionId: Array[Byte], quantity: Long, reissuable: Boolean): Unit = ???

  override def burnAsset(assetId: AssetId, height: Int, transactionId: Array[Byte], quantity: Long): Unit = ???

  override def assetRollbackTo(assetId: Array[Byte], height: Int, newReissuable: Option[Boolean] = None): Unit = ???

}
