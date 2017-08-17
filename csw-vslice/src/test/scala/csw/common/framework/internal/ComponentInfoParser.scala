package csw.common.framework.internal

import java.util.concurrent.TimeUnit

import com.typesafe.config.Config
import csw.common.framework.models.ComponentInfo
import csw.common.framework.models.ComponentInfo.{AssemblyInfo, ContainerInfo, HcdInfo}
import csw.common.framework.models.LocationServiceUsages.{RegisterAndTrackServices, RegisterOnly}
import csw.services.location.models.ComponentType.{Assembly, HCD}
import csw.services.location.models.{ComponentId, ComponentType, Connection, ConnectionType}
import csw.services.location.models.ConnectionType.AkkaType

import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.concurrent.duration.{DurationLong, FiniteDuration}
import scala.util.{Failure, Success, Try}

object ComponentInfoParser {
  private val CONTAINER       = "container"
  private val COMPONENT_NAME  = "componentName"
  private val COMPONENT_TYPE  = "componentType"
  private val REGISTER_AS     = "registerAs"
  private val COMPONENTS      = "components"
  private val CLASS           = "class"
  private val PREFIX          = "prefix"
  private val CONNECTIONS     = "connections"
  private val RATE            = "rate"
  private val INITIAL_DELAY   = "initialDelay"
  private val CREATION_DELAY  = "creationDelay"
  private val LIFECYCLE_DELAY = "lifecycleDelay"

  def parse(config: Config): ContainerInfo =
    (for {
      containerConfig ← Try(config.getConfig(CONTAINER))
      containerName   ← parseComponentName(CONTAINER, containerConfig)
      componentInfoes ← parseComponents(containerConfig)
    } yield {
      // For container, if no connectionType, set to Akka
      val registerAs: Set[ConnectionType] = parseRegisterAs(containerName, containerConfig).getOrElse(Set(AkkaType))

      val initialDelay   = parseDuration(containerName, INITIAL_DELAY, containerConfig, 0.seconds)
      val creationDelay  = parseDuration(containerName, CREATION_DELAY, containerConfig, 0.seconds)
      val lifecycleDelay = parseDuration(containerName, LIFECYCLE_DELAY, containerConfig, 0.seconds)

      ContainerInfo(containerName,
                    RegisterOnly,
                    registerAs,
                    componentInfoes,
                    initialDelay,
                    creationDelay,
                    lifecycleDelay)
    }).get

  private def parseComponentName(name: String, containerConfig: Config): Try[String] = {
    if (!containerConfig.hasPath(COMPONENT_NAME))
      Failure(
        new RuntimeException(s"Missing configuration field: >$COMPONENT_NAME< in connections for component: $name")
      )
    else Success(containerConfig.getString(COMPONENT_NAME))
  }

  // Parse the "registerAs" section of the component config
  private def parseRegisterAs(name: String, config: Config): Try[Set[ConnectionType]] = {
    if (!config.hasPath(REGISTER_AS))
      Failure(new RuntimeException(s"Missing configuration field: >$REGISTER_AS< for component: $name"))
    else
      Try {
        val set = config.getStringList(REGISTER_AS).asScala.map(ctype => ConnectionType.withName(ctype)).toSet
        set
      }
  }

  private def parseDuration(name: String,
                            configName: String,
                            conf: Config,
                            defaultDuration: FiniteDuration): FiniteDuration = {
    import scala.concurrent.duration._
    val t = Try(FiniteDuration(conf.getDuration(configName).getSeconds, TimeUnit.SECONDS))
    if (t.isFailure)
      println(
        s"logger.debug(Container $configName for $name is missing or not valid, returning: $defaultDuration.)"
      )
    t.getOrElse(defaultDuration)
  }

  private def parseComponents(config: Config): Try[Set[ComponentInfo]] = {

    // Parse the "components" section of the config file
    def parseComponent(name: String, conf: Config): Option[ComponentInfo] = {
      val componentType = conf.getString(COMPONENT_TYPE)
      val componentInfo = ComponentType.withName(componentType) match {
        case Assembly => parseAssembly(name, conf)
        case HCD      => parseHcd(name, conf)
        case _        => None
      }
      componentInfo
    }

    Try {
      val conf  = config.getConfig(COMPONENTS)
      val names = conf.root.keySet().asScala.toList
      val entries = for {
        key   <- names
        value <- parseComponent(key, conf.getConfig(key))
      } yield value
      entries.toSet
    }

  }

  // Parse the "services" section of the component config
  private def parseAssembly(assemblyName: String, conf: Config): Option[AssemblyInfo] = {

    def parseComponentId(name: String, connectionConfig: Config): Try[ComponentId] = {
      if (!connectionConfig.hasPath(COMPONENT_TYPE))
        Failure(new RuntimeException(s"Missing configuration field: >$COMPONENT_TYPE< for component: $name"))
      else {
        val componentType = ComponentType.withName(connectionConfig.getString(COMPONENT_TYPE))
        Success(ComponentId(name, componentType))
      }
    }

    def parseConnections(assemblyName: String, assemblyConfig: Config): Try[Set[Connection]] = {
      if (!assemblyConfig.hasPath(CONNECTIONS))
        Failure(new RuntimeException(s"Missing configuration field: >$CONNECTIONS< for Assembly: $assemblyName"))
      else
        Try {
          // Note: config.getConfigList could throw an exception...
          val list = assemblyConfig.getConfigList(CONNECTIONS).asScala.toList.map { connectionConfig: Config =>
            for {
              connectionName  <- parseComponentName(assemblyName, connectionConfig)
              componentId     <- parseComponentId(connectionName, connectionConfig)
              connectionTypes <- parseRegisterAs(connectionName, connectionConfig)
            } yield
              connectionTypes.map(connectionType ⇒ Connection.from(s"${componentId.fullName}-${connectionType.name}"))
          }
          val failed = list.find(_.isFailure).map(_.asInstanceOf[Failure[_]].exception)
          if (failed.nonEmpty)
            throw failed.get
          else
            list.flatMap(_.get).toSet
        }
    }

    val assemblyInfo = for {
      componentClassName <- parseClassName(assemblyName, conf)
      prefix             <- parsePrefix(assemblyName, conf)
      registerAs         <- parseRegisterAs(assemblyName, conf)
      connections        <- parseConnections(assemblyName, conf)
    } yield AssemblyInfo(assemblyName, prefix, componentClassName, RegisterAndTrackServices, registerAs, connections)
    if (assemblyInfo.isFailure) {
      println(s"An error occurred while parsing Assembly info for: $assemblyName")
      println(assemblyInfo.asInstanceOf[Failure[_]].exception)
    }
    assemblyInfo.toOption
  }

  // Parse the "services" section of the component config
  private def parseHcd(name: String, conf: Config): Option[HcdInfo] = {
    // Parse the "services" section of the component config
    def parseRate(name: String, conf: Config): Try[FiniteDuration] = {
      import scala.concurrent.duration._
      if (!conf.hasPath(RATE))
        Failure(new RuntimeException(s"Missing configuration field: >$RATE< for component: $name"))
      else
        Try(FiniteDuration(conf.getDuration(RATE).getSeconds, TimeUnit.SECONDS))
    }

    val x = for {
      componentClassName <- parseClassName(name, conf)
      prefix             <- parsePrefix(name, conf)
      registerAs         <- parseRegisterAs(name, conf)
      cycle              <- parseRate(name, conf)
    } yield HcdInfo(name, prefix, componentClassName, RegisterOnly, registerAs, cycle)
    if (x.isFailure) {
      println(s"An error occurred while parsing HCD info for: $name")
      println(x.asInstanceOf[Failure[_]].exception)
    }
    x.toOption
  }

  private def parseClassName(name: String, config: Config): Try[String] = {
    if (!config.hasPath(CLASS))
      Failure(new RuntimeException(s"Missing configuration field: >$CLASS< for component: $name"))
    else Success(config.getString(CLASS))
  }

  private def parsePrefix(name: String, config: Config): Try[String] = {
    if (!config.hasPath(PREFIX))
      Failure(new RuntimeException(s"Missing configuration field: >$PREFIX< for component: $name"))
    else Success(config.getString(PREFIX))
  }
}
