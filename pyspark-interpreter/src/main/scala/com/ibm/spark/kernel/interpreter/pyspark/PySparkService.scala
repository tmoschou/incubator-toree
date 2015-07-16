package com.ibm.spark.kernel.interpreter.pyspark

import com.ibm.spark.kernel.interpreter.pyspark.PySparkTypes._
import org.slf4j.LoggerFactory
import py4j.GatewayServer

import scala.concurrent.Future

/**
 * Represents the service that provides the high-level interface between the
 * JVM and Python.
 *
 * @param gatewayServer The backend to start to communicate between the JVM and
 *                      Python
 * @param pySparkBridge The bridge to use for communication between the JVM and
 *                      Python
 * @param pySparkProcessHandler The handler used for events that occur with
 *                              the PySpark process
 */
class PySparkService(
  private val gatewayServer: GatewayServer,
  private val pySparkBridge: PySparkBridge,
  private val pySparkProcessHandler: PySparkProcessHandler
) {
  private val logger = LoggerFactory.getLogger(this.getClass)

  /** Represents the process used to execute Python code via the bridge. */
  private lazy val pySparkProcess = {
    val p = new PySparkProcess(
      pySparkBridge,
      pySparkProcessHandler,
      gatewayServer.getListeningPort,
      pySparkBridge.javaSparkContext.version
    )

    // Update handlers to correctly reset and restart the process
    pySparkProcessHandler.setResetMethod(message => {
      p.stop()
      pySparkBridge.state.reset(message)
    })
    pySparkProcessHandler.setRestartMethod(() => p.start())

    p
  }

  /** Starts the PySpark service. */
  def start(): Unit = {
    // Start without forking the gateway server (needs to have access to
    // SparkContext in current JVM)
    logger.debug("Starting gateway server")
    gatewayServer.start()

    val port = gatewayServer.getListeningPort
    logger.debug(s"Gateway server running on port $port")

    // Start the Python process used to execute code
    logger.debug("Launching process to execute Python code")
    pySparkProcess.start()
  }

  /**
   * Submits code to the PySpark service to be executed and return a result.
   *
   * @param code The code to execute
   *
   * @return The result as a future to eventually return
   */
  def submitCode(code: Code): Future[CodeResults] = {
    pySparkBridge.state.pushCode(code)
  }

  /** Stops the running PySpark service. */
  def stop(): Unit = {
    // Stop the Python process used to execute code
    pySparkProcess.stop()

    // Stop the server used as an entrypoint for Python
    gatewayServer.shutdown()
  }
}