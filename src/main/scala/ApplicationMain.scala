import java.io.File
import java.util.concurrent.Executors

import actors.Worker.Begin
import actors.{Worker, Coordinator}
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import scopt.OptionParser

import scala.concurrent.ExecutionContext

object ParallelConcat {

  case class Config(repoRoot: String = "", outputFolder: String = "", parallelism: Int = 4)

  def main(args: Array[String]) {

    val parser: OptionParser[Config] = new OptionParser[Config]("ParallelConcat") {
      opt[Int]('j', "jobs").text("Maximum number of jobs to run").action((j, c) => c.copy(parallelism = j))
      arg[String]("<REPO_ROOT>").text("Repository root").action((repo, c) => c.copy(repoRoot = repo))
      arg[String]("<OUTPUT_FOLDER>").text("Output folder for big files").action((out, c) => c.copy(outputFolder = out))
    }

    def fail(msg: String): Nothing = {
      Console.err.println(s"ERROR : $msg")
      sys.exit(1)
    }

    val conf = parser.parse(args, Config()) getOrElse sys.exit(1)

    val repoRoot = new File(conf.repoRoot)
    val outputFolder = new File(conf.outputFolder)

    if (!repoRoot.isDirectory) fail("Repository root is not a directory")
    if (!outputFolder.isDirectory) fail("Output folder is not a directory")

    if (!outputFolder.list.isEmpty) fail("Output folder is not empty")


    val numWorkers = conf.parallelism
    val threadPool = Executors.newFixedThreadPool(numWorkers)
    val executionContext = ExecutionContext.fromExecutor(threadPool)

    repoRoot.listFiles.filterNot(_.isDirectory).foreach { file =>
      fail(s"Found $file in the repository root which is not a directory!")
    }

    for ((langFolder, idx) <- repoRoot.listFiles.zipWithIndex) {

      val language = langFolder.getName
      println(s"Starting work for language ${language}")

      val out = new File(outputFolder, language)
      out.mkdir()

      /* Create new actor system */
      val system = ActorSystem(s"ParallelConcat-$idx", defaultExecutionContext = Some(executionContext))

      /* Initiate actors */
      val master = system.actorOf(Coordinator.props(langFolder, out, numWorkers))
      val workers = Vector.fill(numWorkers)(system.actorOf(Worker.props(master)))

      /* Start working */
      workers.foreach {
        _ ! Begin
      }

      /* Wait for termination */
      system.awaitTermination()

      println(s"Finished working for language ${language}")
    }

    threadPool.shutdown()
  }
}








