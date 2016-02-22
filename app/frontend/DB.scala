package frontend

import slick.util.AsyncExecutor

object DB {
  val connection = slick.jdbc.JdbcBackend.Database.forURL(
    driver="org.h2.Driver",
    url = "jdbc:h2:./db/accounts;DB_CLOSE_DELAY=-1",
    //url = "jdbc:h2:mem:accounts;DB_CLOSE_DELAY=-1",
    user = "sa", password ="sa",
    executor = AsyncExecutor("db-pool", numThreads=4, queueSize=5000))
}
