package com.wavesplatform.matcher.market

import com.wavesplatform.UtxPool
import com.wavesplatform.matcher.model._
import com.wavesplatform.matcher.{MatcherSettings, MatcherTestData}
import com.wavesplatform.settings.{Constants, WalletSettings}
import com.wavesplatform.state2.reader.SnapshotStateReader
import com.wavesplatform.state2.{AssetInfo, ByteStr, LeaseInfo, Portfolio}
import org.h2.mvstore.MVStore
import org.scalamock.scalatest.PathMockFactory
import org.scalatest._
import org.scalatest.prop.PropertyChecks
import scorex.account.{PrivateKeyAccount, PublicKeyAccount}
import scorex.transaction.ValidationError
import scorex.transaction.assets.IssueTransaction
import scorex.transaction.assets.exchange.{AssetPair, Order}
import scorex.wallet.Wallet

class OrderValidatorSpecification extends WordSpec
  with PropertyChecks
  with Matchers
  with MatcherTestData
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with PathMockFactory {

  var storage = new OrderHistoryStorage(new MVStore.Builder().open())
  var oh = OrderHistoryImpl(storage)

  val utxPool: UtxPool = stub[UtxPool]

  val ss: SnapshotStateReader = stub[SnapshotStateReader]
  (ss.assetInfo _).when(*).returns(Some(AssetInfo(true, 10000000000L)))
  val i1: IssueTransaction = IssueTransaction.create(PrivateKeyAccount(Array.empty), "WBTC".getBytes(), Array.empty, 10000000000L, 8.toByte, true, 100000L, 10000L).right.get
  (ss.transactionInfo _).when(*).returns(Some((1, Some(i1))))

  val s: MatcherSettings = matcherSettings.copy(account = MatcherAccount.address)
  val w = Wallet(WalletSettings(None, "matcher", Some(WalletSeed)))
  val acc: Option[PrivateKeyAccount] = w.generateNewAccount()

  val matcherPubKey: PublicKeyAccount = w.findWallet(s.account).right.get

  private var ov = new OrderValidator {
    override val orderHistory: OrderHistory = oh
    override val utxPool: UtxPool = stub[UtxPool]
    override val settings: MatcherSettings = s
    override val wallet: Wallet = w
  }

  override protected def beforeEach(): Unit = {
    storage = new OrderHistoryStorage(new MVStore.Builder().open())
    ov = new OrderValidator {
      override val orderHistory: OrderHistory = oh
      override val utxPool: UtxPool = stub[UtxPool]
      override val settings: MatcherSettings = s
      override val wallet: Wallet = w
    }
  }

  val wbtc = ByteStr("WBTC".getBytes)
  val pairWavesBtc = AssetPair(None, Some(wbtc))

  "OrderValidator" should {
    "allows buy WAVES for BTC without balance for order fee" in {
      validateNewOrderTest(Portfolio(0, LeaseInfo.empty, Map(
        wbtc -> 10 * Constants.UnitsInWave
      ))) shouldBe an[Right[_, _]]
    }

    "does not allow buy WAVES for BTC when assets number is negative" in {
      validateNewOrderTest(Portfolio(0, LeaseInfo.empty, Map(
        wbtc -> -10 * Constants.UnitsInWave
      ))) shouldBe a[Left[_, _]]
    }
  }

  private def validateNewOrderTest(expectedPortfolio: Portfolio): Either[ValidationError.GenericError, Order] = {
    (ov.utxPool.portfolio _).when(*).returns(expectedPortfolio)
    val o = buy(
      pair = pairWavesBtc,
      price = 0.0022,
      amount = 100 * Constants.UnitsInWave,
      matcherFee = Some((0.003 * Constants.UnitsInWave).toLong)
    )
    ov.validateNewOrder(o)
  }
}
