/**
 * Created by htaylor on 4/2/17.
 */

//println 'exists >>> ' + new File("/Users/htaylor/DevTools/genrocket-3.4.0/lib/genrocket-3.4.0.jar").exists()

//this.class.classLoader.rootLoader.addURL(new URL("file:///Users/htaylor/DevTools/genrocket-3.4.0/lib/genrocket-3.4.0.jar"))
//this.class.classLoader.rootLoader.addURL(new URL("file:///Users/htaylor/DevTools/genrocket-3.4.0/lib/gr-generators-3.4.0.3.jar"))
//this.class.classLoader.rootLoader.addURL(new URL("file:///Users/htaylor/DevTools/genrocket-3.4.0/lib/gr-receivers-3.4.0.1.jar"))

import com.genRocket.GenRocketException
import com.genRocket.engine.EngineAPI
import com.genRocket.engine.EngineManual
import groovy.sql.Sql

emptyTables()
loadTestData()

def getConnection() {
  return Sql.newInstance("jdbc:mysql://localhost:3306/genrocket_bank", "root", "admin", "com.mysql.jdbc.Driver")
}

def emptyTables() {
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

def loadTestData() {
  def fileSep = System.getProperty("file.separator")
  def userHome = System.getProperty("user.home")
  def scenarioPath = "${userHome}${fileSep}Downloads${fileSep}"
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

    def branchCount = '1000'

    api.scenarioLoad("${scenarioPath}BranchRestScenario.grs")
    api.domainSetLoopCount('Branch', branchCount)
    api.scenarioRun()

    api.scenarioLoad("${scenarioPath}CardPoolRestScenario.grs")
    api.domainSetLoopCount('CardPool', '30000')
    api.scenarioRun()

    api.scenarioLoad("${scenarioPath}OpenAccountRestScenario.grs")
    api.domainSetLoopCount('OpenAccount', '10000')
    api.generatorParameterSet('OpenAccount.branchCode', 0, 'var2', branchCount)
    api.scenarioRun()

    println('All Done!')
  } catch (GenRocketException e) {
    println(e.getMessage())
  }
}