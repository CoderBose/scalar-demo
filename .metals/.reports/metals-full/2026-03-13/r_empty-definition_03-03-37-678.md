error id: file://<WORKSPACE>/Main.scala:EmberClientBuilder.
file://<WORKSPACE>/Main.scala
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 378
uri: file://<WORKSPACE>/Main.scala
text:
```scala
//> using dep org.http4s::http4s-ember-client:<same-http4s-version>
//> using dep org.http4s::http4s-client:<same-http4s-version>
//> using dep org.typelevel::ci-string:<same-compatible-version>

import cats.effect.*
import cats.syntax.all.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.ember.client.EmberCli@@entBuilder
import org.http4s.circe.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import com.comcast.ip4s.*
import java.net.URLEncoder
import org.typelevel.ci.CIStringSyntax
import org.typelevel.ci.CIString

Header.Raw(CIString("Authorization"), s"Bearer $accessToken")
Header.Raw(CIString("Content-Type"), "application/json")

case class MCPRequest(tool: String, arguments: Map[String, String])
case class MCPResponse(status: String, result: String)

// Google Calendar request/response models
case class GCalEventDateTime(dateTime: String, timeZone: String)
case class GCalEvent(
  summary: String,
  start: GCalEventDateTime,
  end: GCalEventDateTime,
  description: Option[String] = None
)
case class GCalInsertResponse(id: String, htmlLink: Option[String])

object Main extends IOApp:

  // JSON codecs for MCP + Google Calendar payloads
  given EntityDecoder[IO, MCPRequest] = jsonOf
  given EntityEncoder[IO, MCPResponse] = jsonEncoderOf

  given EntityEncoder[IO, GCalEvent] = jsonEncoderOf
  given EntityDecoder[IO, GCalInsertResponse] = jsonOf

  private def urlEncode(s: String): String =
    URLEncoder.encode(s, "UTF-8")

  private def extractBearerToken(req: Request[IO]): IO[String] =
    req.headers.get[headers.Authorization] match
      case Some(headers.Authorization(Credentials.Token(AuthScheme.Bearer, token))) =>
        IO.pure(token)
      case _ =>
        IO.raiseError(new RuntimeException("Missing/invalid Authorization: Bearer <token> header (n8n should attach this via OAuth2 credentials)."))

  private def createCalendarEvent(
    client: org.http4s.client.Client[IO],
    accessToken: String,
    args: Map[String, String]
  ): IO[String] =
    val title    = args.getOrElse("title", "Untitled")
    val startIso = args.getOrElse("startIso", "")
    val endIso   = args.getOrElse("endIso", startIso)
    val tz       = args.getOrElse("timeZone", "America/New_York")
    val desc     = args.get("description")
    val calId    = args.getOrElse("calendarId", "primary")

    if startIso.isEmpty then
      IO.raiseError(new RuntimeException("startIso is required (ISO-8601 string like 2026-03-13T18:00:00-05:00)."))
    else
      val event = GCalEvent(
        summary = title,
        start = GCalEventDateTime(startIso, tz),
        end = GCalEventDateTime(endIso, tz),
        description = desc
      )

      val uri =
        Uri.unsafeFromString(
          s"https://www.googleapis.com/calendar/v3/calendars/${urlEncode(calId)}/events"
        )

      val gReq =
        Request[IO](Method.POST, uri)
          .withEntity(event.asJson)
          .withHeaders(
            Headers(
              Header.Raw(ci"Authorization", s"Bearer $accessToken"),
              Header.Raw(ci"Content-Type", "application/json")
            )
          )

      client.expect[GCalInsertResponse](gReq).map { resp =>
        val link = resp.htmlLink.getOrElse("(no link returned)")
        s"Created event '$title' from $startIso to $endIso ($tz). id=${resp.id}. link=$link"
      }

  private def routes(client: org.http4s.client.Client[IO]) = HttpRoutes.of[IO] {
    case req @ POST -> Root / "mcp" =>
      (for
        token <- extractBearerToken(req)
        body  <- req.as[MCPRequest]
        // helpful debug: shows what n8n actually sent
        _ <- IO.println(s"MCP tool=${body.tool}, args=${body.arguments}")

        result <- body.tool match
          case "create_calendar_event" =>
            createCalendarEvent(client, token, body.arguments)
          case other =>
            IO.pure(s"Unknown tool: $other")

        resp <- Ok(MCPResponse("success", result).asJson)
      yield resp).handleErrorWith { e =>
        BadRequest(MCPResponse("error", e.getMessage).asJson)
      }
  }

  override def run(args: List[String]): IO[ExitCode] =
    EmberClientBuilder.default[IO].build.use { client =>
      EmberServerBuilder
        .default[IO]
        .withHost(ipv4"0.0.0.0")
        .withPort(port"8080")
        .withHttpApp(routes(client).orNotFound)
        .build
        .useForever
        .as(ExitCode.Success)
    }
```


#### Short summary: 

empty definition using pc, found symbol in pc: 