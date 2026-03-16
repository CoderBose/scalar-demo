error id: file://<WORKSPACE>/Main.scala:
file://<WORKSPACE>/Main.scala
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -cats/effect/MCPRequest#
	 -org/http4s/MCPRequest#
	 -org/http4s/dsl/io/MCPRequest#
	 -org/http4s/circe/MCPRequest#
	 -io/circe/generic/auto/MCPRequest#
	 -io/circe/syntax/MCPRequest#
	 -com/comcast/ip4s/MCPRequest#
	 -MCPRequest#
	 -scala/Predef.MCPRequest#
offset: 863
uri: file://<WORKSPACE>/Main.scala
text:
```scala
import cats.effect.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.circe.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import com.comcast.ip4s.*

case class MCPRequest(tool: String, arguments: Map[String, String])
case class MCPResponse(status: String, result: String)

object Main extends IOApp:

  given EntityDecoder[IO, MCPRequest] = jsonOf
  given EntityEncoder[IO, MCPResponse] = jsonEncoderOf

  def createCalendarEvent(args: Map[String, String]): IO[String] =
    val title = args.getOrElse("title", "Untitled")
    val start = args.getOrElse("start", "unknown")
    IO.println(s"Creating event: $title at $start") *>
      IO.pure(s"Booked '$title' at $start")

  val routes = HttpRoutes.of[IO] {
    case req @ POST -> Root / "mcp" =>
      for
        body <- req.as[MCPR@@equest]
        result <- body.tool match
          case "create_calendar_event" =>
            createCalendarEvent(body.arguments)
          case _ =>
            IO.pure("Unknown tool")
        resp <- Ok(MCPResponse("success", result).asJson)
      yield resp
  }

  override def run(args: List[String]): IO[ExitCode] =
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(routes.orNotFound)
      .build
      .useForever
      .as(ExitCode.Success)
```


#### Short summary: 

empty definition using pc, found symbol in pc: 