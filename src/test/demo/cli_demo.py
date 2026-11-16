"""Types a scripted session into the real CLI for the demonstration video.

It starts the installed application (build/install/library-agent, made by `gradlew installDist`)
with a fresh demo database, shows its output unchanged, and "types" each line with a short delay
so the recording reads like a person using it. Assistant replies come from the live model, so
running this costs money.

Usage (from the project root, in the console window to be recorded):
    python src/test/demo/cli_demo.py <java.exe> <ffmpeg.exe or -> <output folder>
"""
import os
import re
import subprocess
import sys
import threading
import time

TITLE = "LibraryAgentCliDemo"
PROMPT = re.compile(r"(?:^|\n)(?:[A-Z]+\d+)?> $")


class Session:
    def __init__(self, java):
        env = dict(os.environ, DB_PATH="data/demo-cli.db")
        self.proc = subprocess.Popen(
            [java, "-cp", "build/install/library-agent/lib/*", "libraryagent.ui.cli.CliApp", "--reset-db"],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, env=env)
        self.seen = ""          # everything the CLI has printed
        self.mark = 0           # start of the output for the current command
        self.last = time.time()
        self.lock = threading.Lock()
        threading.Thread(target=self._pump, daemon=True).start()

    def _pump(self):
        decoder = __import__("codecs").getincrementaldecoder("utf-8")("replace")
        while True:
            chunk = self.proc.stdout.read(1)
            if not chunk:
                break
            text = decoder.decode(chunk)
            if text:
                with self.lock:
                    self.seen += text.replace("\r", "")
                    self.last = time.time()
                sys.stdout.write(text)
                sys.stdout.flush()

    def wait(self, pattern=PROMPT, timeout=300, quiet=0.5):
        """Waits until the output ends with the pattern (regex or literal suffix) and has gone quiet."""
        deadline = time.time() + timeout
        while time.time() < deadline:
            with self.lock:
                tail, idle = self.seen[self.mark:][-400:], time.time() - self.last
            done = pattern.search(tail) if hasattr(pattern, "search") else tail.endswith(pattern)
            if done and idle > quiet:
                return
            if self.proc.poll() is not None:
                return
            time.sleep(0.1)
        raise TimeoutError("CLI did not reach " + str(pattern))

    def output(self):
        with self.lock:
            return self.seen[self.mark:]

    def prompt_text(self):
        with self.lock:
            return self.seen[self.seen.rfind("\n") + 1:]

    def send(self, line, expect=PROMPT, pause=2.5):
        with self.lock:
            self.mark = len(self.seen)
        time.sleep(0.6)
        for ch in line:
            sys.stdout.write(ch)
            sys.stdout.flush()
            time.sleep(0.012 if len(line) > 60 else 0.04)
        time.sleep(0.4)
        sys.stdout.write("\n")
        sys.stdout.flush()
        self.proc.stdin.write((line + "\n").encode("utf-8"))
        self.proc.stdin.flush()
        self.wait(expect)
        time.sleep(pause)

    def chat(self, line):
        self.send(line, pause=7.0)

    def banner(self, text):
        """Shows a heading above the current prompt."""
        prompt = self.prompt_text()
        sys.stdout.write("\r\x1b[2K\n\x1b[1;97;44m  " + text + "  \x1b[0m\n\n" + prompt)
        sys.stdout.flush()
        time.sleep(3.0)

    def signed_in(self):
        return self.prompt_text().strip() != ">"


def loan_id(text, title):
    """The loan ID on the account line that mentions the title."""
    for line in text.splitlines():
        if title in line:
            found = re.search(r"\bL\d{3,}\b", line)
            if found:
                return found.group(0)
    return None


def script(s):
    s.wait()
    s.banner("Library Agent: CLI walkthrough. The real application; typing is scripted, "
             "assistant replies come live from Claude Sonnet 5.5")

    s.banner("F01 Conversational registration - error case: the email already has an account")
    s.send("register", expect="Name: ", pause=0.4)
    s.send("Jordan Lee", expect="Email: ", pause=0.4)
    s.send("alex.morgan@example.com", expect="Member type (standard/student): ", pause=0.4)
    s.send("standard", pause=3.5)
    s.banner("F01 Conversational registration - plain language; the assistant calls the registerMember tool")
    s.chat("I'd like to sign up. I'm Jordan Lee, jordan.lee@example.com, a standard member. "
           "I like Fantasy and Science Fiction, and books under 400 pages.")
    if not s.signed_in():
        s.chat("Yes, that's all correct. Please create my account.")
    s.send("whoami")

    s.banner("F02 Natural-language search - by command (no AI), then in plain language (searchCatalog tool)")
    s.send("search murder")
    s.chat("Find me science fiction books that are under 320 pages.")
    s.banner("F13 Conversation memory - a follow-up refers to the previous answer")
    s.chat("Tell me more about the second one.")
    s.banner("F02 Search - error case: nothing matches, and nothing is invented")
    s.send("search xyzzy")
    s.chat("Do you have any books about underwater basket weaving?")

    s.banner("F10 Grounded questions - answers come from the catalogue record; "
             "this book's description contains a planted instruction, which is ignored")
    s.send("book 9781000000038", pause=4.0)
    s.chat("What is The Lighthouse Ledger about, and who would enjoy it?")
    s.banner("F10 Grounded questions - alternative case: a book the library does not own")
    s.chat("Do you have Nineteen Eighty-Four by George Orwell?")

    s.banner("F04 Multi-book find and reserve - one request, several tool calls")
    s.chat("Reserve two short fantasy books for me, whichever are available right now.")
    s.banner("F04 Reserve by command - and the error case: the same book twice")
    s.send("reserve 9781000000010")
    s.send("reserve 9781000000010", pause=3.5)
    s.banner("F05 Hold queue - every copy of The Hobbit is out, so the member joins the queue")
    s.send("reserve 9781000000001", pause=3.5)
    s.send("account", pause=5.0)

    s.banner("F12 Undo - by command, then in plain language (undoLastAction tool)")
    s.send("undo", pause=3.5)
    s.chat("Undo that last reservation too.")
    s.send("reserve 9781000000001", pause=3.0)

    s.banner("F03 Reading lists - create, add, reorder, remove")
    s.send('list create "Weekend reads"')
    s.send('list add "Weekend reads" 9781000000005')
    s.send('list add "Weekend reads" 9781000000002')
    s.send('list add "Weekend reads" 9781000000009')
    s.banner("F03 Reading lists - error case: the same book twice")
    s.send('list add "Weekend reads" 9781000000009', pause=3.5)
    s.send('list move "Weekend reads" 9781000000009 1')
    s.send('list remove "Weekend reads" 9781000000002')
    s.send('list show "Weekend reads"', pause=4.0)
    s.banner("F03 Reading lists in plain language (manageReadingList tool)")
    s.chat("Add Dune to my Weekend reads list.")

    s.banner("F09 Recommendations - computed in Java from genres, authors and ratings")
    s.send("recommend", pause=5.0)
    s.banner("F09 Curated list - the assistant chooses only from Java's candidates, names the list and saves it")
    s.chat("Make me a reading list of 4 books based on my tastes, give it a fitting name, and save it.")
    s.send("lists", pause=5.0)

    s.banner("F08 Renewals - error case: Sam cannot renew The Hobbit while another member is waiting")
    s.send("login M002")
    s.send("account", pause=4.0)
    s.send("renew " + (loan_id(s.output(), "The Hobbit") or "L0004"), pause=4.0)

    s.banner("F12 Undo - error case: a new session has nothing to undo")
    s.send("login LIB001")
    s.send("undo", pause=3.0)
    s.banner("F08 Overdue loans - the librarian's list, with the fee so far")
    s.send("overdue", pause=5.0)
    s.banner("F07 Return - checking in The Hobbit holds the copy for the next member in the queue")
    s.send("return C0001", pause=5.0)
    s.banner("F06 Checkout - error case: a copy held for one member cannot be lent to another")
    s.send("checkout M001 C0001", pause=5.0)

    s.banner("F05 Notifications - Jordan signs in and is told the hold is ready (Observer)")
    s.send("login M003", pause=5.0)
    s.send("account", pause=5.0)

    s.banner("F06 Checkout - the librarian lends the held copy to Jordan")
    s.send("login LIB001")
    s.send("checkout M003 C0001", pause=4.0)
    s.send("overdue", pause=3.0)
    dune = re.search(r"\bC\d{4}\b", next((l for l in s.output().splitlines() if "Dune" in l), ""))
    s.banner("F07 Return of an overdue book - the late fee comes from the member's fee policy")
    s.send("return " + (dune.group(0) if dune else "C0009"), pause=5.0)
    s.banner("F12 Undo of a return - the loan is reopened")
    s.send("undo", pause=4.0)

    s.banner("F11 Add a book - error case: pages must be a number")
    s.send("addbook", expect="ISBN: ", pause=0.4)
    s.send("9781000000041", expect="Title: ", pause=0.4)
    s.send("The Clockwork Orchard", expect="Author: ", pause=0.4)
    s.send("Mira Tanaka", expect="Pages: ", pause=0.4)
    s.send("about three hundred", pause=3.5)
    s.banner("F11 Add a book with AI genre tagging - suggestions outside the genre hierarchy are dropped")
    s.send("addbook", expect="ISBN: ", pause=0.4)
    s.send("9781000000041", expect="Title: ", pause=0.4)
    s.send("The Clockwork Orchard", expect="Author: ", pause=0.4)
    s.send("Mira Tanaka", expect="Pages: ", pause=0.4)
    s.send("312", expect="Description: ", pause=0.4)
    s.send("A young inventor discovers a hidden orchard of mechanical trees and must protect it from "
           "a rival guild of mages in this adventure of magic and machines.",
           expect="Number of copies: ", pause=0.4)
    s.send("2", expect=re.compile(r"Subgenres, comma-separated.*: $"), pause=4.0)
    s.send("", pause=5.0)
    s.send("search clockwork", pause=4.0)

    s.banner("F08 Renewals - Jordan renews The Hobbit: nobody is waiting and it is not overdue")
    s.send("login M003")
    s.send("account", pause=4.0)
    s.send("renew " + (loan_id(s.output(), "The Hobbit") or "L0006"), pause=5.0)

    s.banner("F08 Overdue and fees - Alex is told about the overdue book; it cannot be renewed")
    s.send("login M001", pause=4.0)
    s.send("account", pause=5.0)
    s.send("renew " + (loan_id(s.output(), "Dune") or "L0001"), pause=5.0)

    s.banner("F14 Reading feedback - rate a borrowed book")
    s.send("history", pause=4.0)
    s.send("rate 9781000000009 liked Funny and clever", pause=3.5)
    s.banner("F14 Reading feedback - error case: with tracking off, ratings are not saved")
    s.send("tracking off", pause=3.0)
    s.send("rate 9781000000013 disliked", pause=4.0)
    s.send("tracking on", pause=3.0)
    s.banner("F09/F14 Recommendations in plain language use the member's ratings and preferences")
    s.chat("Based on what I've liked, what should I read next?")

    s.banner("End of the CLI walkthrough - on exit the CLI reports the session's AI token use and cost")
    s.send("exit", expect="Goodbye.\n", pause=6.0)


def main():
    java, ffmpeg, out = sys.argv[1], sys.argv[2], sys.argv[3]
    os.makedirs(out, exist_ok=True)
    os.system("")  # turn on ANSI escape handling in the Windows console
    sys.stdout.write("\x1b]0;" + TITLE + "\x07\x1b[2J\x1b[H")
    sys.stdout.flush()
    time.sleep(1.5)
    recorder = None
    if ffmpeg != "-":
        log = open(os.path.join(out, "cli-ffmpeg.log"), "wb")
        recorder = subprocess.Popen(
            [ffmpeg, "-y", "-hide_banner", "-loglevel", "error", "-f", "lavfi", "-i",
             "gfxcapture=window_title='" + TITLE + "':capture_cursor=false:max_framerate=20",
             "-vf", "hwdownload,format=bgra,scale=trunc(iw/2)*2:trunc(ih/2)*2,format=yuv420p",
             "-c:v", "libx264", "-preset", "veryfast", "-crf", "20", os.path.join(out, "cli-raw.mp4")],
            stdin=subprocess.PIPE, stdout=log, stderr=log)
        time.sleep(2.5)
    session = Session(java)
    try:
        script(session)
    except Exception as error:  # keep the recording even if a step fails
        sys.stdout.write("\n[demo stopped: %r]\n" % (error,))
        time.sleep(3)
    finally:
        if recorder:
            recorder.stdin.write(b"q")
            recorder.stdin.flush()
            recorder.wait(30)
        session.proc.kill()


if __name__ == "__main__":
    main()
