// Main.scala - Scala MCP server using Google Calendar "events.insert" with debug logs


// Example tool payload:
// {
//   "tool": "create_calendar_event",
//   "arguments": {
//     "title": "Dinner with Kelly",
//     "startIso": "2026-03-13T18:00:00-04:00",
//     "endIso": "2026-03-13T19:00:00-04:00",
//     "timeZone": "America/New_York",
//     "description": "Booked from chat",
//     "calendarId": "primary"
//   }
// }

import cats.effect.*
import cats.syntax.all.*

import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.circe.*
import org.http4s.headers.Authorization
import org.http4s.headers.`Content-Type`

import io.circe.generic.auto.*
import io.circe.syntax.*

import com.comcast.ip4s.*

import java.net.URLEncoder
import scala.util.Try

// ===== MCP models =====
case class MCPRequest(tool: String, arguments: Map[String, String])
case class MCPResponse(status: String, result: String)

// ===== Google Calendar models for events.insert =====
case class GCalEventDateTime(dateTime: String, timeZone: String)
case class GCalEventInsert(
    summary: String,
    start: GCalEventDateTime,
    end: GCalEventDateTime,
    description: Option[String] = None
)

// Response contains lots of fields; we only need a few
case class GCalInsertResponse(
    id: Option[String],
    htmlLink: Option[String],
    status: Option[String],
    summary: Option[String]
)

object Main extends IOApp {

  given EntityDecoder[IO, MCPRequest] = jsonOf
  given EntityEncoder[IO, MCPResponse] = jsonEncoderOf

  given EntityEncoder[IO, GCalEventInsert] = jsonEncoderOf
  given EntityDecoder[IO, GCalInsertResponse] = jsonOf

  private def urlEncode(s: String): String =
    URLEncoder.encode(s, "UTF-8")

  private def logAuthHeader(req: Request[IO]): IO[Unit] =
    req.headers.get[Authorization] match {
      case Some(Authorization(creds)) =>
        IO.println(s"AUTH header present=true scheme=${creds.authScheme}")
      case None =>
        IO.println("AUTH header present=false")
    }

  private def extractBearerToken(req: Request[IO]): IO[String] =
    req.headers.get[Authorization] match {
      case Some(Authorization(Credentials.Token(AuthScheme.Bearer, token))) =>
        IO.pure(token)
      case Some(Authorization(creds)) =>
        IO.raiseError(
          new RuntimeException(
            s"Invalid Authorization scheme: ${creds.authScheme}. Expected: Bearer <token>."
          )
        )
      case None =>
        IO.raiseError(
          new RuntimeException(
            "Missing Authorization header. n8n must send: Authorization: Bearer <token>."
          )
        )
    }

  private def dumpTokenInfo(
      client: org.http4s.client.Client[IO],
      token: String
  ): IO[Unit] = {
    val uri = Uri
      .unsafeFromString("https://oauth2.googleapis.com/tokeninfo")
      .withQueryParam("access_token", token)

    val req = Request[IO](Method.GET, uri)

    client
      .run(req)
      .use { resp =>
        resp.as[String].flatMap { body =>
          IO.println(s"TOKENINFO status=${resp.status.code} body=$body")
        }
      }
      .handleErrorWith(e => IO.println(s"TOKENINFO call failed: ${e.getMessage}"))
  }

  private def requireNonEmpty(name: String, v: String): IO[Unit] =
    if (v.trim.nonEmpty) IO.unit
    else IO.raiseError(new RuntimeException(s"$name is required"))

  private def parseIsoOrFail(field: String, v: String): IO[String] =
    if (v.contains("T")) IO.pure(v)
    else IO.raiseError(new RuntimeException(s"$field must be an ISO-8601 datetime like 2026-03-13T18:00:00-04:00"))

  /** Calls Google Calendar events.insert:
    * POST https://www.googleapis.com/calendar/v3/calendars/{calendarId}/events
    */
  private def insertEvent(
      client: org.http4s.client.Client[IO],
      accessToken: String,
      calendarId: String,
      event: GCalEventInsert
  ): IO[GCalInsertResponse] = {
    val uri =
      Uri.unsafeFromString(
        s"https://www.googleapis.com/calendar/v3/calendars/${urlEncode(calendarId)}/events"
      )

    val req =
      Request[IO](Method.POST, uri)
        .withEntity(event.asJson)
        .withHeaders(
          Authorization(Credentials.Token(AuthScheme.Bearer, accessToken)),
          `Content-Type`(MediaType.application.json)
        )

    client.run(req).use { resp =>
      resp.as[String].flatMap { body =>
        if (resp.status.isSuccess) {
          io.circe.parser.decode[GCalInsertResponse](body) match {
            case Right(decoded) => IO.pure(decoded)
            case Left(err) =>
              IO.raiseError(new RuntimeException(s"Failed to decode insert response: ${err.getMessage}. body=$body"))
          }
        } else {
          IO.raiseError(new RuntimeException(s"Google Calendar error: status=${resp.status.code} body=$body"))
        }
      }
    }
  }

  /** Tool implementation (events.insert).
    *
    * Expected arguments from n8n/agent:
    * - title (required)
    * - startIso (required) ISO-8601
    * - endIso (required) ISO-8601 (if missing, we'll default to +60 minutes ONLY if we can parse)
    * - timeZone (optional, default America/New_York)
    * - description (optional)
    * - calendarId (optional, default primary)
    */
  private def createCalendarEventInsert(
      client: org.http4s.client.Client[IO],
      accessToken: String,
      args: Map[String, String]
  ): IO[String] = {
    val title = args.getOrElse("title", "")
    val startIsoRaw = args.getOrElse("startIso", "")
    val endIsoRaw = args.getOrElse("endIso", "")
    val tz = args.getOrElse("timeZone", "America/New_York")
    val calId = args.getOrElse("calendarId", "primary")
    val desc = args.get("description").map(_.trim).filter(_.nonEmpty)

    for {
      _ <- IO.println(s"MCP args=$args")

      _ <- IO.println(
        s"TOKEN debug: length=${accessToken.length}, prefix=${accessToken.take(12)}..., startsWithYa29=${accessToken.startsWith("ya29.")}"
      )
      _ <- dumpTokenInfo(client, accessToken)

      _ <- requireNonEmpty("title", title)
      _ <- requireNonEmpty("startIso", startIsoRaw)

      startIso <- parseIsoOrFail("startIso", startIsoRaw)

      endIso <-
        if (endIsoRaw.trim.nonEmpty) parseIsoOrFail("endIso", endIsoRaw)
        else
          IO
            .fromEither {
              Either.catchNonFatal {
                val zdt = java.time.OffsetDateTime.parse(startIso)
                zdt.plusMinutes(60).toString
              }.leftMap(e => new RuntimeException(s"endIso missing and could not compute from startIso: ${e.getMessage}"))
            }

      event = GCalEventInsert(
        summary = title,
        start = GCalEventDateTime(startIso, tz),
        end = GCalEventDateTime(endIso, tz),
        description = desc
      )

      _ <- IO.println(s"events.insert calendarId='$calId' summary='${event.summary}' start='$startIso' end='$endIso' tz='$tz'")

      resp <- insertEvent(client, accessToken, calId, event)

      id = resp.id.getOrElse("(no id)")
      link = resp.htmlLink.getOrElse("(no link)")
      sum = resp.summary.getOrElse(title)
      status = resp.status.getOrElse("")

      _ <- IO.println(s"insert success: id=$id link=$link summary=$sum status=$status")

    } yield s"Created event: '$sum' from $startIso to $endIso ($tz). id=$id link=$link"
  }

  private def httpApp(client: org.http4s.client.Client[IO]): HttpApp[IO] = {
    val routes = HttpRoutes.of[IO] {
      case req @ POST -> Root / "mcp" =>
        (for {
          _ <- logAuthHeader(req)
          token <- extractBearerToken(req)

          body <- req.as[MCPRequest]
          _ <- IO.println(s"MCP tool=${body.tool}")

          result <- body.tool match {
            case "create_calendar_event" =>
              createCalendarEventInsert(client, token, body.arguments)
            case other =>
              IO.raiseError(new RuntimeException(s"Unknown tool: $other"))
          }

          resp <- Ok(MCPResponse("success", result).asJson)
        } yield resp).handleErrorWith { e =>
          BadRequest(MCPResponse("error", e.getMessage).asJson)
        }
    }
    routes.orNotFound
  }

  override def run(args: List[String]): IO[ExitCode] =
    EmberClientBuilder.default[IO].build.use { client =>
      EmberServerBuilder
        .default[IO]
        .withHost(ipv4"0.0.0.0")
        .withPort(port"8080")
        .withHttpApp(httpApp(client))
        .build
        .useForever
        .as(ExitCode.Success)
    }
}