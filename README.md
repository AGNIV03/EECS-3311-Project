# Library Agent

An AI agent for a public library, written in Java 21 for the EECS 3311 course project. Members find, reserve, borrow, return and rate books by **talking to the agent in plain language**, in a command-line interface, a JavaFX desktop app, or a web version hosted online. Librarians use the same app to manage the catalogue, check books in and out, and follow up overdue loans.

> "Reserve two sci-fi books under 300 pages, whichever are available."

A request like this would normally mean several searches, availability checks and hold forms. The agent plans the steps itself: it searches the catalogue, checks which copies are free, reserves them, and summarises what it did. Every rule (borrowing limits, copy states, fees, hold queues) is enforced by ordinary Java code. The language model only chooses which tools to call.

**Status:** Stage 2 (implementation) is complete; submission is due Nov 10, 2026. All 14 features work end to end in the CLI, the JavaFX GUI and the web version, with the AI agent. The week of Nov 3 is for testing, fixes and additional features. See [Feature status](#features) below.

---

## Contents
- [Features](#features)
- [How the agent works](#how-the-agent-works)
- [Architecture](#architecture)
- [Requirements](#requirements)
- [Setup](#setup)
- [Running the application](#running-the-application)
- [Using the CLI](#using-the-cli)
- [Web version and deployment](#web-version-and-deployment)
- [Testing](#testing)
- [Configuration reference](#configuration-reference)
- [Project structure](#project-structure)
- [Design documents](#design-documents)

---

## Features

The system specifies 14 features (F01–F14) in the Stage 1 design. ✅ works end to end, 🟡 partly built, ⬜ not yet built.

| ID | Feature | Type | Status |
|---|---|---|---|
| F01 | Conversational registration with reading preferences | Hybrid | ✅ CLI, GUI and web |
| F02 | Natural-language catalogue search ("something like The Hobbit, not too long") | Hybrid | ✅ CLI, GUI and web |
| F03 | Reading lists (create, add, remove, reorder) | Hybrid | ✅ CLI, GUI and web |
| F04 | Multi-book find and reserve in one request | Hybrid | ✅ CLI, GUI and web |
| F05 | Hold queue with hold-ready notifications | Deterministic | ✅ CLI, GUI and web |
| F06 | Checkout with borrowing limits (5 standard, 8 student) | Deterministic | ✅ CLI, GUI and web |
| F07 | Book return with late fees (desk and self-service by chat) | Deterministic / hybrid | ✅ CLI, GUI and web |
| F08 | Due dates, renewals, overdue detection and late fees | Deterministic | ✅ CLI, GUI and web |
| F09 | Personalised recommendations and AI-curated reading lists | Hybrid | ✅ CLI, GUI and web |
| F10 | Grounded book Q&A (answers only from the catalogue record) | AI | ✅ CLI, GUI and web |
| F11 | Catalogue management with AI genre tagging | Hybrid | ✅ CLI, GUI and web |
| F12 | Undo last action (from commands, menus or chat) | Deterministic | ✅ CLI, GUI and web |
| F13 | Conversation memory ("reserve the second one") | AI | ✅ CLI, GUI and web |
| F14 | Reading feedback tracking (liked / neutral / disliked) | Hybrid | ✅ CLI, GUI and web |

Every feature is available from the CLI, the JavaFX GUI and the browser interface.

---

## How the agent works

- **Model:** Claude Sonnet 5.5 (`claude-sonnet-5-5`) through the Anthropic Messages API, using native tool calling. It is the only model the app uses.
- **Loop:** the `Planner` sends the conversation and the list of tools to the model. The model either asks for tools to run, whose results are fed back, or gives a final answer. A step limit stops runaway loops.
- **Tools (15):** search the catalogue, read a book's record, check availability, reserve, return, renew, view the account, register a member, manage reading lists, record a rating, get recommendations, save a curated list, and undo. AI genre tagging for new books is a separate, one-shot model call. Each tool validates its arguments before calling a Java service, so the model can never break a library rule.
- **Grounding:** the app only shows books that tools actually returned. The model cannot invent a title and have it appear as a result.
- **Safety:**
  - Book descriptions and other tool output are treated as data, never as instructions. The sample catalogue includes one book with a planted instruction, so this can be tested.
  - The agent asks before putting a member in a hold queue.
- **Memory:** each session remembers its conversation and the books it last showed, so follow-ups like "the second one" work. Logging in as someone else starts a fresh conversation.
- **Without an API key:** every feature still works through commands and menus; only the chat assistant is turned off.

---

## Architecture

A layered design. Both user interfaces call one controller:

```
CLI (CliApp)  ─┐
               ├─►  AgentController (Facade)  ─►  Planner + tools (agent core)  ─►  ClaudeClient ─► Anthropic API
JavaFX GUI    ─┘            │                              │
                            ▼                              ▼
                 Domain services (catalogue, circulation, members, reading lists, notifications)
                            │
                            ▼
                 Repositories ─► SQLite (data/library.db)
```

Design patterns used:

| Pattern | Where | Purpose |
|---|---|---|
| State | `domain/copy` (`BookCopy`, `AvailableState`, `ReservedState`, `OnLoanState`, `OverdueState`) | A copy's current state decides whether it can be reserved, lent or returned |
| Strategy | `domain/fee` (`StandardFeePolicy`, `StudentFeePolicy`); `LLMClient` (`ClaudeClient`, `FakeLLMClient`) | Fee rules per member type; real or scripted language model |
| Composite | `domain/genre` (`GenreCategory`, `Subgenre`) | Searching "Fiction" includes every subgenre beneath it |
| Command | `command` (`CommandHistory`, 7 commands) | One undo for actions from the CLI, the GUI and the agent |
| Observer | `notification` (`NotificationService`, `CliNotifier`) | Hold-ready and overdue notices reach whichever interface is open |
| Template Method | `agent/tools/AbstractTool` | Every tool validates its arguments before acting |
| Facade | `controller/AgentController` | One entry point and one set of permission checks for both UIs |
| Adapter | `agent/llm/ClaudeClient` | Wraps the Anthropic SDK behind the app's own `LLMClient` interface |
| Decorator | `agent/llm/BudgetedLLMClient` | Adds a daily spending cap to any `LLMClient` (web version) |
| MVC | JavaFX views, the CLI and the browser interface → `AgentController` → services | Three interfaces share one controller and the same rules |

**Tech stack:** Java 21, Gradle (wrapper included), JavaFX 21, SQLite (`sqlite-jdbc`), Jackson, Anthropic Java SDK 3.2.0, JUnit 6.

---

## Requirements

- **JDK 21.** [Eclipse Temurin 21](https://adoptium.net/) is recommended. On Windows: `winget install EclipseAdoptium.Temurin.21.JDK`.
- **Internet access on the first build.** Gradle and the libraries download automatically; nothing else needs installing.
- **An Anthropic API key** from https://console.anthropic.com, for the AI assistant. Optional: without one, everything except chat still works.

Check your Java version with `java -version`. It should report 21. If it reports an older version, set `JAVA_HOME` to your JDK 21 folder and open a new terminal.

---

## Setup

```bash
git clone https://github.com/AGNIV03/EECS-3311-Project.git
cd EECS-3311-Project
```

Create your local configuration from the template:

| Shell | Command |
|---|---|
| Windows PowerShell | `Copy-Item .env.example .env` |
| Windows cmd | `copy .env.example .env` |
| macOS / Linux | `cp .env.example .env` |

Open `.env` and replace `YOUR_LOCAL_API_KEY_HERE` with your Anthropic API key:

```
ANTHROPIC_API_KEY=sk-ant-...
```

`.env` is listed in `.gitignore` and must never be committed. Only `.env.example`, which has placeholders, is in the repository. You can also set `ANTHROPIC_API_KEY` as an environment variable instead; environment variables override `.env`.

The database is created and filled with sample data automatically on first run.

---

## Running the application

Run these from the project folder. On macOS/Linux use `./gradlew` instead of `.\gradlew.bat`.

**CLI:**
```powershell
.\gradlew.bat runCli -q --console=plain
```

**CLI options** (passed with `--args`; combine as needed):
```powershell
.\gradlew.bat runCli -q --console=plain --args="--member M001"              # log in on start
.\gradlew.bat runCli -q --console=plain --args="--reset-db --member LIB001"  # fresh sample data, as the librarian
```
- `--member <id>`: log in as this member when the CLI starts.
- `--reset-db`: delete `data/library.db` so it is recreated from the sample data.
- `--verbose`: print each tool the assistant calls, with its arguments and outcome.

When the CLI exits, it prints the tokens the assistant used and an estimated cost.

**GUI (JavaFX):**
```powershell
.\gradlew.bat run
```
The start screen lets you sign in, register, browse as a guest, or sign up by chatting. After signing in you get:
- **Chat:** talk to the assistant. Books in replies have Details and Reserve buttons.
- **Catalog:** search by keywords, genre and length; book details; Reserve or Join hold queue; Add to list; Ask about this book; Recommended for you.
- **Account:** loans (Renew), holds (Cancel), fees, reading lists (create, reorder, remove, Generate a list with AI), borrowing history with ratings, and the tracking switch.
- **Librarian** (librarian accounts only): desk check-out and check-in, the overdue list, and Add a book with Suggest genres.

The toolbar shows notices (hold ready, overdue) and has Undo and Sign out.

GUI options: `.\gradlew.bat run --args="--member M001 --tab account"` signs in and opens a tab; `--reset-db` restores the sample data.

The first launch takes a little longer while Gradle downloads dependencies and compiles.

### Sample accounts

| Member ID | Name | Role | Starting situation |
|---|---|---|---|
| `M001` | Alex Morgan | Standard member (limit 5 loans) | Has *Dune*, 9 days overdue |
| `M002` | Sam Patel | Student (limit 8 loans) | Has the only copy of *The Hobbit* |
| `LIB001` | Lee Chen | Librarian | Can check books in and out, list overdue loans and add books |

Login is by member ID only; there are no passwords in this version. The catalogue has 40 books across Fiction, Nonfiction and Young Adult subgenres.

---

## Using the CLI

Type a command, or just type what you want in plain English. Lines that aren't commands go to the AI assistant.

| Command | What it does |
|---|---|
| `login <memberId>` / `logout` / `whoami` | Sign in or out |
| `register` | Create an account (asks for name, email, member type and preferences) |
| `search <keywords>` | Keyword search (works without AI) |
| `book <isbn>` | A book's details and the status of each copy |
| `genres` | List the subgenres |
| `reserve <isbn>` | Reserve a book, or join its hold queue |
| `cancel <reservationId>` | Cancel a reservation |
| `renew <loanId>` | Renew a loan |
| `account` | Loans, holds, fees and reading lists |
| `history` | Every book you have borrowed |
| `lists` / `list create\|show\|add\|remove\|move "<name>" ...` | Manage reading lists |
| `undo` | Undo your last action |
| `rate <isbn> liked\|neutral\|disliked [comment]` | Rate a book you borrowed |
| `tracking on\|off` | Turn reading-feedback tracking on or off |
| `recommend` | Books picked from your preferences and ratings (no AI) |
| `checkout <memberId> <copyId>` | *(librarian)* Lend a copy |
| `return <copyId>` | *(librarian)* Check a copy in |
| `overdue` | *(librarian)* List overdue loans and notify borrowers |
| `addbook` | *(librarian)* Add a book (asks for its details; the assistant suggests subgenres) |
| `help` / `exit` | Show help, or quit |

### Example session

The assistant's replies are written by the model, so the wording differs between runs.

```
> login M002
Welcome, Sam Patel.
M002> find me a fantasy book under 300 pages
Three fantasy books under 300 pages are available now:
1. A Wizard of Earthsea by Ursula K. Le Guin has 183 pages. 2 copies are free.
2. Piranesi by Susanna Clarke has 272 pages. 1 copy is free.
3. Gardens of the Lower City by Ines Valcourt has 288 pages. 1 copy is free.
M002> what is the second one about?
Piranesi is about a man who lives in a vast house of endless halls and statues...
M002> reserve it
Done! Piranesi is on the hold shelf for you for the next 3 days.
M002> undo that
I've undone that reservation, so Piranesi is available again.
```

More things to try:
- `login M001` then "can I keep Dune longer?". The renewal is refused because the loan is overdue, and the agent explains why.
- Ask about *The Lighthouse Ledger*. Its description contains a planted instruction, which the agent ignores.
- `login LIB001`, then `return C0001`. If someone is waiting for The Hobbit, the copy is held for them and they see a notice when they next log in.

---

## Web version and deployment

The same application also runs as a web server with a JSON API, for hosting on a public site:

```powershell
.\gradlew.bat runWeb        # http://localhost:8080
```

- Each browser gets its own session (cookie), with its own login, undo history and conversation.
- Protections for a public site:
  - a daily AI spending cap (`DAILY_BUDGET_USD`)
  - per-visitor chat limits
  - message size limits
  - librarian sign-in only with `LIBRARIAN_PASSCODE`
- Open `http://localhost:8080` for the browser interface. It has the same four tabs as the GUI. The JSON API is under `/api/...` (for example `/api/health`).

To host it on a VPS with HTTPS on your own domain (Docker and Caddy), follow **[deploy/DEPLOY.md](deploy/DEPLOY.md)**.

---

## Testing

```powershell
.\gradlew.bat test                                                       # all automated tests (offline, free)
.\gradlew.bat test --tests "libraryagent.service.CirculationServiceTest"  # one test class
.\gradlew.bat liveTest                                                   # real-API test (needs a key; costs < $0.01)
```

- `gradlew test` runs the JUnit tests for the domain, persistence, services, commands, controller, CLI and the agent loop. Agent tests use `FakeLLMClient` (scripted model replies), so they need no network and cost nothing.
- `gradlew liveTest` runs tests tagged `live` against the real Anthropic API. They are excluded from `gradlew test`.
- The test report is written to `build/reports/tests/test/index.html`.

---

## Configuration reference

All settings live in `.env`; environment variables with the same names take precedence.

| Key | Default | Meaning |
|---|---|---|
| `ANTHROPIC_API_KEY` | *(empty)* | Anthropic API key. If empty, the AI assistant is turned off |
| `LLM_EFFORT` | `medium` | Model reasoning effort: `low`, `medium`, `high`, `xhigh` or `max` |
| `AGENT_MAX_STEPS` | `8` | Maximum tool-calling steps for one message |
| `DB_PATH` | `data/library.db` | SQLite database file |
| `WEB_PORT` | `8080` | Web version: listening port |
| `DAILY_BUDGET_USD` | `2.00` | Web version: estimated AI spend allowed per day |
| `CHAT_LIMIT_PER_MINUTE` / `CHAT_LIMIT_PER_HOUR` | `6` / `40` | Web version: chat messages per visitor |
| `LIBRARIAN_PASSCODE` | *(empty)* | Web version: passcode for librarian sign-in; empty disables it |
| `DOMAIN` | | Deployment: public domain served by Caddy |

The model is fixed to Claude Sonnet 5.5 and cannot be changed in configuration.

---

## Project structure

```
src/main/java/libraryagent/
  config/          .env loading and settings
  domain/          entities: Book, Member, Loan, Reservation, HoldQueue, ReadingList, ...
    copy/          BookCopy and its states (State pattern)
    fee/           late-fee policies (Strategy pattern)
    genre/         genre hierarchy (Composite pattern)
  persistence/     SQLite database, repositories, sample-data loader
  service/         catalogue, circulation, member and reading-list rules
  command/         undoable actions and CommandHistory (Command pattern)
  notification/    library events and listeners (Observer pattern)
  controller/      AgentController (Facade), sessions, account summary
  agent/           Planner loop, prompt composer, response parser, memory
    llm/           LLMClient, ClaudeClient (Adapter), FakeLLMClient, message types
    tools/         AbstractTool (Template Method) and the agent's tools
  ui/cli/          command-line interface
  ui/gui/          JavaFX interface
  ui/web/          web server, JSON API, sessions, rate limits
src/main/resources/
  db/schema.sql          database schema
  seed/library.json      sample catalogue, members and loans
  prompts/system.txt     the agent's instructions
  web/                   browser interface files
deploy/            Caddyfile and deployment guide (with Dockerfile, docker-compose.yml at the root)
src/test/java/...  JUnit tests, mirroring the main packages
```

---

## Design documents

- **Stage 1 design report** ([`stage1-report.pdf`](stage1-report.pdf)): problem, features F01–F14, use cases, design patterns and feature-to-design traceability.
- **UML diagrams** (repository root; editable `.uxf` files for [UMLet](https://www.umlet.com/) plus exported `.png` images):
  - `UC`: use case diagram
  - `CD1`–`CD6`: class diagrams (domain, states and fees, services and commands, agent core, tools, user interfaces)
  - `SD01`–`SD08`: sequence diagrams for the key scenarios
- **Stage 2 implementation report** (`stage2-report.pdf`, added at submission): what was built, design changes and their reasons, design patterns, AI-assisted development log, and demonstration videos.

This project was developed with AI assistance (Claude Code), as required by the course. The Stage 2 report documents how.
