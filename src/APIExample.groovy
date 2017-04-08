/**
 * Created by htaylor on 4/2/17.
 */

import com.genRocket.GenRocketException
import com.genRocket.engine.EngineAPI
import com.genRocket.engine.EngineManual
import com.genRocket.utils.ElapsedTime
import groovy.sql.Sql

new Simulation().simulationOne()

class Simulation {
  def fileSep = System.getProperty("file.separator")
  def userHome = System.getProperty("user.home")
  def scenarioPath = "${userHome}${fileSep}Downloads${fileSep}"

  def branchCount = 1000
  def cardPoolCount = 40000
  def customerCount = 10000

  def static elapsedTime(long start) {
    // Get elapsed time in milliseconds
    long mills = System.currentTimeMillis() - start

    // Get elapsed time in seconds
    long sec = mills / 1000F

    // Get elapsed time in minutes
    long min = sec / 60

    // Get elapsed time in hours
    long hour = min / 60

    // Get elapsed time in days
    long day = hour / 24

    if (day > 0)
      return day + "d:" + hour % 24 + "h:" + min % 60 + "m:" + sec % 60 + "s"

    if (hour > 0)
      return hour % 24 + "h:" + min % 60 + "m:" + sec % 60 + "s"

    return min % 60 + "m:" + sec % 60 + "s"
  }

  def static getConnection() {
    return Sql.newInstance("jdbc:mysql://localhost:3306/genrocket_bank", "root", "admin", "com.mysql.jdbc.Driver")
  }

  def static emptyTables() {
    def sql = getConnection()
    def tables = [
      'card', 'card_type', 'customer', 'customer_level', 'transaction',
      'transaction_type', 'account', 'account_type', 'branch', 'user', 'card_pool'
    ]

    tables.each { table ->
      println "delete from ${table}..."
      sql.execute("delete from ${table}".toString())
    }
  }

  def static getAverageBalance(String accountType) {
    def sql = getConnection()
    def query = """select round(avg(aco.balance)) as avg from account aco
                   join account_type act on act.id = aco.account_type_id
                   where act.name = '${accountType}'"""

    def row = sql.rows(query.toString())

    return row[0]['avg']
  }

  def loadTestData() {
    def scenarios = [
      'AccountTypeRestScenario',
      'CardTypeRestScenario',
      'CustomerLevelRestScenario',
      'TransactionTypeRestScenario',
    ]

    EngineAPI api = new EngineManual()

    try {
      scenarios.each { scenario ->
        api.scenarioLoad("${scenarioPath}${scenario}.grs")
        api.scenarioRun()
      }

      api.scenarioLoad("${scenarioPath}BranchRestScenario.grs")
      api.domainSetLoopCount('Branch', branchCount.toString())
      api.scenarioRun()

      api.scenarioLoad("${scenarioPath}CardPoolRestScenario.grs")
      api.domainSetLoopCount('CardPool', cardPoolCount.toString())
      api.scenarioRun()

      println('Load Data All Done!')
    } catch (GenRocketException e) {
      println(e.getMessage())
    }
  }

  public simulationOne() {
    def startTime = System.currentTimeMillis()

    emptyTables()
    loadTestData()

    EngineAPI api = new EngineManual()
    Boolean done = false
    Float avg = 0

    // Open all checking accounts with a balance of 1000.00
    // Open all savings accounts with a balance of 3000.00
    api.scenarioLoad("${scenarioPath}OpenAccountRestScenario.grs")
    api.domainSetLoopCount('OpenAccount', customerCount.toString())
    api.generatorParameterSet('OpenAccount.branchCode', 0, 'var2', (branchCount - 1).toString())
    api.generatorParameterSet('OpenAccount.checking', 0, 'map', [Silver: '1000.00', Gold: '1000.00', Platinum: '1000.00'])
    api.generatorParameterSet('OpenAccount.savings', 0, 'map', [Silver: '3000.00', Gold: '3000.00', Platinum: '3000.00'])
    api.scenarioRun()

    while (!done) {
      try {
        // Withdrawal 100.00, 150.00, 200.00 or 250.00 from checking
        api.scenarioLoad("${scenarioPath}AccountWithdrawalRestScenario.grs")
        api.receiverParameterSet('User','RealtimeTestReceiver','threadCount', 10.toString())
        api.generatorParameterSet('User.amount', 0, 'list', ['100', '150', '200', '250'])
        api.domainSetLoopCount('User', customerCount.toString())
        api.scenarioRun()

        avg = getAverageBalance('Checking')

        if (avg <= 500) {
          // Switch fromCardNumber to Savings and toCardNumber to Checking
          // Transfer 750 from savings into checking
          api.scenarioLoad("${scenarioPath}AccountTransferRestScenario.grs")
          api.receiverParameterSet('User','RealtimeTestReceiver','threadCount', 10.toString())
          api.generatorParameterSet('User.fromCardNumber', 0, 'equation', 'var1 + var1')
          api.generatorParameterSet('User.toCardNumber', 0, 'equation', 'var1 + var1 - 1')
          api.generatorParameterSet('User.amount', 0, 'value', 750.00.toString())
          api.domainSetLoopCount('User', customerCount.toString())
          api.scenarioRun()
        }

        avg = getAverageBalance('Savings')

        if (avg <= 1500) {
          // Deposit 2000.00 into checking
          api.scenarioLoad("${scenarioPath}AccountDepositRestScenario.grs")
          api.receiverParameterSet('User','RealtimeTestReceiver','threadCount', 10.toString())
          api.domainSetLoopCount('User', customerCount.toString())
          api.generatorParameterSet('User.amount', 0, 'value', 2000.00.toString())
          api.scenarioRun()

          // Transfer 1500.00 from checking to savings
          api.scenarioLoad("${scenarioPath}AccountTransferRestScenario.grs")
          api.receiverParameterSet('User','RealtimeTestReceiver','threadCount', 10.toString())
          api.generatorParameterSet('User.amount', 0, 'value', 1500.00.toString())
          api.domainSetLoopCount('User', customerCount.toString())
          api.scenarioRun()

          // End the simulation
          done = true
        }

      } catch (GenRocketException e) {
        println(e.getMessage())
      }
    }

    def totalTime = ElapsedTime.elapsedTime(startTime)
    println("Simulation Complete! - Total Time: ${totalTime}")
  }
}