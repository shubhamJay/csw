package csw.command.client.handlers

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import csw.command.api.codecs.CommandServiceCodecs
import csw.command.api.messages.CommandServiceHttpMessage
import csw.command.api.messages.CommandServiceHttpMessage._
import csw.command.api.scaladsl.CommandService
import msocket.api.MessageHandler
import msocket.impl.post.ServerHttpCodecs

class CommandServiceHttpHandlers(commandService: CommandService)
    extends MessageHandler[CommandServiceHttpMessage, Route]
    with CommandServiceCodecs
    with ServerHttpCodecs {

  override def handle(request: CommandServiceHttpMessage): Route = request match {
    case Validate(controlCommand) => complete(commandService.validate(controlCommand))
    case Submit(controlCommand)   => complete(commandService.submit(controlCommand))
    case Oneway(controlCommand)   => complete(commandService.oneway(controlCommand))
    case Query(runId)             => complete(commandService.query(runId))
  }
}