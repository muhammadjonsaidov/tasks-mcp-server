# tasks-mcp-server

A Spring Boot [MCP](https://modelcontextprotocol.io) server that exposes a small in-memory task manager as MCP tools over **Streamable HTTP**, plus a live web UI that updates over SSE as tasks change.

Claude (or any MCP client) can create, list, update, and delete tasks; the browser view at `http://localhost:8080/` reflects the changes in real time.

## Tech stack

- Java 25
- Spring Boot 4.1.1
- Spring AI 2.0.1 — `spring-ai-starter-mcp-server-webmvc`
- Maven (wrapper included)

## Requirements

- JDK 25
- No separate Maven install needed — use `./mvnw`

## Run

```bash
./mvnw spring-boot:run
```

The app starts on **port 8080**:

| URL | Description |
| --- | --- |
| `http://localhost:8080/mcp` | MCP endpoint (Streamable HTTP) |
| `http://localhost:8080/` | Live web UI |
| `http://localhost:8080/api/tasks` | REST: all tasks as JSON |
| `http://localhost:8080/api/tasks/stream` | SSE stream of task snapshots |

The server must be running before an MCP client can connect — there is no auto-spawn.

## MCP tools

| Tool | Parameters | Returns |
| --- | --- | --- |
| `createTask` | `title`, `description` | New task as JSON (`status` defaults to `TODO`) |
| `listTasks` | — | Newline-separated list of tasks |
| `getTaskStatus` | `taskId` | Task as JSON |
| `updateTaskStatus` | `taskId`, `newStatus` | Confirmation message |
| `deleteTask` | `taskId` | Confirmation message |

`newStatus` is typed as the `TaskStatus` enum, so the tool's JSON schema advertises the allowed values automatically:

```json
"newStatus": { "type": "string", "enum": ["TODO", "IN_PROGRESS", "DONE"] }
```

### Adding a status

Edit the `TaskStatus` enum only — the MCP schema, validation, and the UI's default pill styling all follow automatically. A custom color is optional (add a `.YOUR_STATUS { … }` rule in `index.html`).

## Connecting a client

### Claude Code

A project-scoped `.mcp.json` is already provided:

```json
{
  "mcpServers": {
    "my-java-server": {
      "type": "http",
      "url": "http://localhost:8080/mcp"
    }
  }
}
```

Or add it via the CLI:

```bash
claude mcp add -t http my-java-server http://localhost:8080/mcp
```

Verify with `/mcp`.

### Claude Desktop

Add a custom connector: **Settings → Connectors → Add custom connector** with URL `http://localhost:8080/mcp`. If your version only supports stdio config entries, bridge it with:

```json
{
  "mcpServers": {
    "my-java-server": {
      "command": "npx",
      "args": ["mcp-remote", "http://localhost:8080/mcp"]
    }
  }
}
```

## Configuration

`src/main/resources/application.yaml`:

```yaml
spring:
  ai:
    mcp:
      server:
        stdio: false          # HTTP transport, not stdio
        protocol: streamable
        name: task-manager-server
        version: 1.0.0
server:
  port: 8080
```

## Project structure

```
src/main/java/com/example/tasksmcpserver/
├── TasksMcpServerApplication.java   # Spring Boot entry point
└── service/
    ├── Task.java                    # record: id, title, description, status
    ├── TaskStatus.java              # enum: TODO, IN_PROGRESS, DONE
    ├── TaskService.java             # MCP tools + in-memory store + SSE broadcast
    └── TaskController.java          # REST + SSE endpoints for the UI
src/main/resources/
├── application.yaml
└── static/
    ├── index.html                   # live UI (SSE + markdown rendering)
    └── vendor/                      # marked + DOMPurify (vendored, offline)
```

The UI renders task descriptions as **Markdown** (via marked), sanitized with DOMPurify to prevent XSS.

## Notes

- **In-memory only.** Tasks live in a `ConcurrentHashMap` and are lost on restart.
- The MCP and REST endpoints are **unauthenticated** by default — keep this on `localhost`.
