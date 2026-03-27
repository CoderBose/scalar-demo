# scalar-demo

A Scala MCP (Model Context Protocol) server that exposes a `create_calendar_event` tool backed by the **Google Calendar API** (`events.insert`). It is designed to be called by an AI agent or workflow automation tool (e.g. n8n) to create calendar events on behalf of a user via OAuth 2.0 Bearer tokens.

## What it does

The server starts an HTTP server on port `8080` and exposes a single endpoint:

```
POST /mcp
```

The caller sends a JSON body identifying the tool to invoke and its arguments. The server extracts the Bearer token from the `Authorization` header, validates it, and calls the Google Calendar API to insert the event.

### Supported tool: `create_calendar_event`

| Argument | Required | Description |
|---|---|---|
| `title` | Yes | Event title / summary |
| `startIso` | Yes | Start time in ISO-8601 format, e.g. `2026-03-13T18:00:00-04:00` |
| `endIso` | No | End time in ISO-8601 format. Defaults to `startIso + 60 minutes` if omitted |
| `timeZone` | No | IANA timezone string. Defaults to `America/New_York` |
| `description` | No | Event description / notes |
| `calendarId` | No | Target calendar. Defaults to `primary` |

### Example request

```json
POST /mcp
Authorization: Bearer <google_oauth2_access_token>
Content-Type: application/json

{
  "tool": "create_calendar_event",
  "arguments": {
    "title": "Dinner with Kelly",
    "startIso": "2026-03-13T18:00:00-04:00",
    "endIso": "2026-03-13T19:00:00-04:00",
    "timeZone": "America/New_York",
    "description": "Booked from chat",
    "calendarId": "primary"
  }
}
```

### Example response

```json
{
  "status": "success",
  "result": "Created event: 'Dinner with Kelly' from 2026-03-13T18:00:00-04:00 to 2026-03-13T19:00:00-04:00 (America/New_York). id=<event_id> link=<html_link>"
}
```

## Tech stack

| Component | Library / Version |
|---|---|
| Language | Scala 3.3.1 |
| Build tool | sbt 1.9.7 |
| HTTP server/client | http4s (Ember) 0.23.26 |
| Effect system | Cats Effect 3.5.4 |
| JSON | Circe 0.14.7 |
| Code formatter | scalafmt (sbt-scalafmt 2.5.2) |

## Prerequisites

- JDK 11+
- sbt 1.9.7+
- A valid Google OAuth 2.0 access token with the `https://www.googleapis.com/auth/calendar.events` scope

## Running

```bash
sbt run
```

The server will start on `0.0.0.0:8080`.

## Authentication

The server does **not** manage OAuth credentials. The caller is responsible for obtaining and refreshing a Google OAuth 2.0 access token and passing it as a Bearer token in every request:

```
Authorization: Bearer ya29.<your_token>
```

The server will log token debug info (length, prefix, validity via Google's `tokeninfo` endpoint) on each request to aid debugging.

## Video Walkthroughs

- [Slack Integration with n8n](https://youtu.be/SbWaqJA-bNY)
- [Detailed configuration of Google credentials, to connect with n8n](https://youtu.be/EZb9KEeWaNk)
- [Working of MCP client-server architecture (off the shelf)](https://youtu.be/OQctb_H9cns)
- [Working of MCP client-server architecture (MCP server implemented with Scala)](https://youtu.be/MqeftfGKeRc)

Slides I presented at Scalar 2026 are linked below:
- [Scalar Slides](https://www.canva.com/design/DAG-Omrrig8/ZTXJN1x9jzUVTbEYRYvUIQ/edit?utm_content=DAG-Omrrig8&utm_campaign=designshare&utm_medium=link2&utm_source=sharebutton) 

## Project structure

```
scalar-demo/
├── Main.scala          # All server logic — MCP handler, Google Calendar client
├── build.sbt           # SBT dependencies and Scala version
└── project/
    ├── build.properties  # sbt version pin
    └── plugins.sbt       # scalafmt plugin
```
