package scorex.account


class Account(val address: String) {

  override def toString = address

  override def equals(b: Any) = b match {
    case a: Account => a.address == address
    case _ => false
  }

  override def hashCode(): Int = address.hashCode()
}


object Account {
  val ADDRESS_LENGTH = 25
}