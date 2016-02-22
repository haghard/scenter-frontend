/*

package frontend

import scalikejdbc._

object AccountSql extends scalikejdbc.SQLSyntaxSupport[Account] {
  private val auto = AutoSession

  val salt = org.mindrot.jbcrypt.BCrypt.gensalt(3573452)
  val a = syntax("a")

  def apply(a: SyntaxProvider[Account])(rs: WrappedResultSet): Account = apply(a.resultName)(rs)

  def apply(a: ResultName[Account])(rs: WrappedResultSet): Account = new Account(
    id = rs.int(a.id),
    login = rs.string(a.login),
    password = rs.string(a.password),
    permission = Permission.valueOf(rs.string(a.permission)),
    token = rs.string(a.token)
  )

  def authenticate(login: String, password: String)(implicit s: DBSession = auto): Option[Account] = withSQL {
    select.from(AccountSql as a).where.eq(a.login, login).and.eq(a.password, password)
  }.map(AccountSql(a)).single.apply()
   .filter(a=>org.mindrot.jbcrypt.BCrypt.checkpw(password, a.password))

  def count()(implicit s: DBSession = auto): Int = withSQL {
    select.from(AccountSql as a)
  }.map(AccountSql(a)).list.apply().size


  def create(account: Account)(implicit s: DBSession = auto) {
    withSQL {
      import account._
      val hashpw = org.mindrot.jbcrypt.BCrypt.hashpw(account.password, salt)
      insert.into(AccountSql).values(account.id, login, hashpw, permission.toString, token)
    }.update.apply()
  }

  def updateToken(login: String, password: String, token: String)(implicit s: DBSession = auto) {
    withSQL {
      update(AccountSql as a).set((a.token -> token))
      .where.eq(a.login, login).and.eq(a.password, password)
    }.update().apply()
  }

  override lazy val connectionPoolName = 'default
}*/
