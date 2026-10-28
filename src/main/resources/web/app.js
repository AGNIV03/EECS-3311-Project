// Library Agent web interface. Talks only to this server's /api endpoints.
// All server and model text is inserted with textContent, never as HTML.
"use strict";

const state = { me: null, assistant: false, librarianSignIn: false, tab: "chat", lists: [], selectedList: null, notices: [] };
const $ = (id) => document.getElementById(id);

// ---- Helpers ---------------------------------------------------------------------------

function el(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (key === "class") node.className = value;
    else if (key.startsWith("on")) node.addEventListener(key.slice(2), value);
    else node[key] = value;
  }
  for (const child of children) {
    if (child == null) continue;
    node.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return node;
}

async function api(method, path, body) {
  const response = await fetch(path, {
    method,
    headers: body ? { "Content-Type": "application/json" } : {},
    body: body ? JSON.stringify(body) : undefined,
    credentials: "same-origin",
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.error || "Request failed (" + response.status + ")");
  return data;
}

function status(message, isError = false) {
  const bar = $("status");
  bar.textContent = message;
  bar.classList.toggle("error", isError);
}

async function attempt(action) {
  try {
    return await action();
  } catch (error) {
    status(error.message, true);
    return null;
  }
}

function availabilityText(book) {
  return book.availableCopies > 0
    ? book.availableCopies + " of " + book.totalCopies + " available"
    : "All out" + (book.waiting > 0 ? ", " + book.waiting + " waiting" : "");
}

function formData(form) {
  return Object.fromEntries(new FormData(form).entries());
}

// ---- Session and screens ---------------------------------------------------------------

async function loadMe() {
  const me = await api("GET", "/api/me");
  state.me = me.member;
  state.assistant = me.assistantAvailable;
  state.librarianSignIn = me.librarianSignIn;
  renderHeader();
}

function renderHeader() {
  const signedIn = !!state.me;
  $("who").textContent = signedIn
    ? state.me.name + " (" + state.me.memberId + (state.me.librarian ? ", librarian" : "") + ")"
    : ($("app").classList.contains("hidden") ? "" : "Guest");
  $("btn-notices").classList.toggle("hidden", !signedIn);
  $("btn-undo").classList.toggle("hidden", !signedIn);
  $("btn-signout").classList.toggle("hidden", $("app").classList.contains("hidden"));
  $("btn-signout").textContent = signedIn ? "Sign out" : "Back";
  $("btn-notices").textContent = state.notices.length ? "Notices (" + state.notices.length + ")" : "Notices";
  $("btn-notices").classList.toggle("has-notices", state.notices.length > 0);
  document.querySelectorAll(".needs-member").forEach((n) => n.classList.toggle("hidden", !signedIn));
  document.querySelectorAll(".needs-librarian").forEach((n) => n.classList.toggle("hidden", !(signedIn && state.me.librarian)));
  $("passcode-row").classList.toggle("hidden", !state.librarianSignIn);
  $("lib-hint").classList.toggle("hidden", !state.librarianSignIn);
  const chatOn = state.assistant;
  $("chat-message").disabled = !chatOn;
  $("chat-send").disabled = !chatOn;
  $("btn-chat-signup").disabled = !chatOn;
  $("btn-suggest").disabled = !chatOn;
  $("btn-generate-list").disabled = !chatOn;
  if (!chatOn) $("chat-message").placeholder = "The AI assistant is not available on this server. Use the other tabs.";
}

function showStart() {
  $("start").classList.remove("hidden");
  $("app").classList.add("hidden");
  renderHeader();
}

function showApp(tab) {
  $("start").classList.add("hidden");
  $("app").classList.remove("hidden");
  renderHeader();
  selectTab(tab || state.tab);
}

function selectTab(tab) {
  const button = document.querySelector('.tabs button[data-tab="' + tab + '"]');
  if (!button || button.classList.contains("hidden")) tab = "chat";
  state.tab = tab;
  document.querySelectorAll(".tabs button").forEach((b) => b.classList.toggle("active", b.dataset.tab === tab));
  document.querySelectorAll(".tab").forEach((t) => t.classList.toggle("hidden", t.id !== "tab-" + tab));
  refreshTab(tab);
}

async function refreshTab(tab) {
  if (tab === "catalog") { await search(); await loadRecommendations(); }
  if (tab === "account" && state.me) await loadAccount();
}

async function signedInChanged() {
  resetChat();
  state.notices = [];
  await loadMe();
  showApp(state.me ? "chat" : state.tab);
  if (state.me) status("Signed in as " + state.me.name + " (" + state.me.memberId + ").");
}

// ---- Chat ------------------------------------------------------------------------------

function bubble(text, kind) {
  const node = el("div", { class: "bubble " + kind }, text);
  $("chat-log").append(node);
  $("chat-log").scrollTop = $("chat-log").scrollHeight;
  return node;
}

function resetChat() {
  $("chat-log").replaceChildren();
  bubble("Hi! I'm the library assistant. I can search the catalogue, answer questions about books, reserve, renew "
    + "and return books, manage your reading lists and recommend what to read next.", "assistant");
}

function bookCard(book) {
  return el("div", { class: "book-card" },
    el("div", { class: "info" }, el("strong", {}, book.title), " · " + book.author + " · " + book.pages + " pages",
      el("br"), el("span", { class: "muted" }, availabilityText(book))),
    el("button", { type: "button", onclick: () => openBook(book.isbn, true) }, "Details"),
    el("button", { type: "button", disabled: !state.me, onclick: () => reserve(book.isbn) }, "Reserve"));
}

async function sendChat(message) {
  message = (message || "").trim();
  if (!message || !state.assistant) return;
  $("chat-message").value = "";
  bubble(message, "user");
  const thinking = bubble("Thinking…", "thinking");
  $("chat-message").disabled = true;
  $("chat-send").disabled = true;
  const wasMember = state.me ? state.me.memberId : null;
  try {
    const reply = await api("POST", "/api/chat", { message });
    thinking.remove();
    bubble(reply.text, reply.error ? "error" : "assistant");
    for (const book of reply.books) $("chat-log").append(bookCard(book));
    $("chat-log").scrollTop = $("chat-log").scrollHeight;
    await loadMe();
    const isMember = state.me ? state.me.memberId : null;
    if (isMember !== wasMember) { showApp("chat"); status("Signed in as " + state.me.name + "."); }
  } catch (error) {
    thinking.remove();
    bubble(error.message, "error");
  } finally {
    $("chat-message").disabled = !state.assistant;
    $("chat-send").disabled = !state.assistant;
    $("chat-message").focus();
  }
}

function askInChat(message) {
  selectTab("chat");
  sendChat(message);
}

// ---- Catalog ---------------------------------------------------------------------------

async function search() {
  const params = new URLSearchParams(formData($("form-search")));
  for (const [key, value] of [...params]) if (!value) params.delete(key);
  const data = await attempt(() => api("GET", "/api/search?" + params));
  if (!data) return;
  const rows = data.books.map((book) => el("tr", { onclick: () => openBook(book.isbn, false) },
    el("td", {}, book.title), el("td", {}, book.author), el("td", {}, book.pages),
    el("td", {}, book.genres.join(", ")), el("td", {}, availabilityText(book))));
  $("results").replaceChildren(...rows);
  if (!rows.length) $("results").append(el("tr", {}, el("td", { colSpan: 5, class: "muted" }, "No books match. Try fewer words or another genre.")));
  status(data.books.length + " book(s) found.");
}

async function loadRecommendations() {
  if (!state.me) return;
  const data = await attempt(() => api("GET", "/api/recommendations"));
  if (!data) return;
  const items = data.books.map((b) => el("li", { onclick: () => openBook(b.isbn, false) },
    el("strong", {}, b.title), el("span", { class: "muted small" }, b.author)));
  $("recommendations").replaceChildren(...items);
  if (!items.length) $("recommendations").append(el("li", { class: "muted small" }, "Rate books or set favourite genres to get suggestions."));
}

async function openBook(isbn, switchTab) {
  if (switchTab) selectTab("catalog");
  const book = await attempt(() => api("GET", "/api/books/" + encodeURIComponent(isbn)));
  if (!book) return;
  const detail = $("book-detail");
  detail.classList.remove("muted");
  const copies = book.copies.map((c) => c.copyId + " " + c.status).join(", ");
  const actions = el("div", { class: "row" },
    el("button", { type: "button", disabled: !state.me, onclick: async () => { await reserve(isbn); openBook(isbn, false); } },
      book.availableCopies > 0 ? "Reserve" : "Join hold queue"),
    el("button", { type: "button", disabled: !state.assistant, onclick: () => askInChat(
      'Tell me about "' + book.title + '" (ISBN ' + book.isbn + "). What is it about, and who is it suitable for?") },
      "Ask about this book"));
  if (state.me) {
    const select = el("select", {}, el("option", { value: "" }, "Reading list…"),
      ...state.lists.map((l) => el("option", { value: l.name }, l.name)));
    actions.append(select, el("button", { type: "button", onclick: async () => {
      if (!select.value) { status("Choose a reading list first (create one in the Account tab)."); return; }
      const r = await attempt(() => api("POST", "/api/lists/add", { list: select.value, isbn }));
      if (r) status(r.message, !r.success);
    } }, "Add to list"));
  } else {
    actions.append(el("span", { class: "muted small" }, "Sign in to reserve or save books."));
  }
  detail.replaceChildren(
    el("h2", {}, book.title),
    el("p", { class: "muted" }, book.author + " · " + book.pages + " pages · " + book.genres.join(", ") + " · ISBN " + book.isbn),
    el("p", {}, book.description),
    el("p", {}, availabilityText(book) + "  (copies: " + copies + ")"),
    actions);
}

async function reserve(isbn) {
  if (!state.me) { status("Sign in to reserve books."); return; }
  const r = await attempt(() => api("POST", "/api/reserve", { isbn }));
  if (r) { status(r.message, !r.success); refreshTab(state.tab); }
}

// ---- Account ---------------------------------------------------------------------------

async function loadAccount() {
  const account = await attempt(() => api("GET", "/api/account"));
  if (!account) return;
  const m = account.member;
  $("account-summary").textContent = m.name + " · " + m.memberId + " · " + m.type + " member · up to " + m.borrowLimit
    + " loans · Fees owed: " + account.feesOwed
    + (m.favoriteGenres.length ? " · Favourite genres: " + m.favoriteGenres.join(", ") : "")
    + (m.favoriteAuthors.length ? " · Favourite authors: " + m.favoriteAuthors.join(", ") : "");
  $("tracking").checked = m.trackingEnabled;

  $("loans").replaceChildren(...account.loans.map((l) => el("tr", {},
    el("td", {}, l.title), el("td", {}, l.copyId), el("td", {}, l.dueDate),
    el("td", { class: l.overdue ? "error" : "" }, l.overdue ? "OVERDUE" : "On loan"),
    el("td", {}, l.renewals + " of " + l.maxRenewals), el("td", {}, l.fee),
    el("td", {}, el("button", { type: "button", onclick: () => act("POST", "/api/loans/" + l.loanId + "/renew") }, "Renew")))));
  if (!account.loans.length) $("loans").append(el("tr", {}, el("td", { colSpan: 7, class: "muted" }, "No books on loan.")));

  $("holds").replaceChildren(...account.holds.map((h) => el("tr", {},
    el("td", {}, h.title),
    el("td", {}, h.status === "ready" ? "Ready: pick up copy " + h.copyId + " by " + h.pickupBy : "Waiting, number " + h.queuePosition),
    el("td", {}, el("button", { type: "button", onclick: () => act("POST", "/api/reservations/" + h.reservationId + "/cancel") }, "Cancel")))));
  if (!account.holds.length) $("holds").append(el("tr", {}, el("td", { colSpan: 3, class: "muted" }, "No reservations.")));

  state.lists = account.readingLists;
  if (state.selectedList && !state.lists.some((l) => l.name === state.selectedList)) state.selectedList = null;
  renderLists();

  const history = await attempt(() => api("GET", "/api/history"));
  if (history) {
    $("history").replaceChildren(...history.loans.map((l) => {
      const rating = el("select", {}, el("option", { value: "" }, "Rate…"),
        ...["liked", "neutral", "disliked"].map((r) => el("option", { value: r, selected: l.rating === r }, r)));
      rating.addEventListener("change", async () => {
        if (!rating.value) return;
        const r = await attempt(() => api("POST", "/api/rate", { isbn: l.isbn, rating: rating.value }));
        if (r) { status(r.message); loadAccount(); }
      });
      return el("tr", {}, el("td", {}, l.checkoutDate), el("td", {}, l.title),
        el("td", {}, l.returnDate || "on loan"), el("td", {}, l.rating || ""), el("td", {}, rating));
    }));
    if (!history.loans.length) $("history").append(el("tr", {}, el("td", { colSpan: 5, class: "muted" }, "You haven't borrowed anything yet.")));
  }
}

function renderLists() {
  $("lists").replaceChildren(...state.lists.map((l) => el("li", {
    class: l.name === state.selectedList ? "selected" : "",
    onclick: () => { state.selectedList = l.name; renderLists(); },
  }, l.name + " (" + l.books.length + ")")));
  if (!state.lists.length) $("lists").append(el("li", { class: "muted small" }, "No lists yet."));
  const list = state.lists.find((l) => l.name === state.selectedList);
  const books = list ? list.books : [];
  $("list-books").replaceChildren(...books.map((b, i) => el("li", {},
    el("span", { class: "grow" }, (i + 1) + ". " + b.title + " — " + b.author),
    el("button", { type: "button", class: "link", title: "Move up", disabled: i === 0,
      onclick: () => act("POST", "/api/lists/move", { list: list.name, isbn: b.isbn, position: i }) }, "↑"),
    el("button", { type: "button", class: "link", title: "Move down", disabled: i === books.length - 1,
      onclick: () => act("POST", "/api/lists/move", { list: list.name, isbn: b.isbn, position: i + 2 }) }, "↓"),
    el("button", { type: "button", class: "link", title: "Remove",
      onclick: () => act("POST", "/api/lists/remove", { list: list.name, isbn: b.isbn }) }, "×"))));
  if (!books.length) $("list-books").append(el("li", { class: "muted small" },
    list ? "This list is empty. Add books from the Catalog tab or by chat." : "Select a list."));
}

async function act(method, path, body) {
  const r = await attempt(() => api(method, path, body));
  if (r) { status(r.message, r.success === false); await refreshTab(state.tab); }
}

// ---- Librarian -------------------------------------------------------------------------

async function loadOverdue() {
  const data = await attempt(() => api("GET", "/api/desk/overdue"));
  if (!data) return;
  $("overdue").replaceChildren(...data.loans.map((l) => el("tr", {},
    el("td", {}, l.title), el("td", {}, l.copyId), el("td", {}, l.memberName + " (" + l.memberId + ")"),
    el("td", {}, l.dueDate), el("td", {}, l.fee))));
  if (!data.loans.length) $("overdue").append(el("tr", {}, el("td", { colSpan: 5, class: "muted" }, "No overdue loans.")));
  status(data.loans.length + " overdue loan(s).");
}

function deskResult(r) {
  if (!r) return;
  $("desk-result").textContent = r.message;
  $("desk-result").className = r.success ? "ok" : "error";
  status(r.message, !r.success);
}

function bookForm() {
  const f = $("form-addbook");
  return {
    isbn: f.isbn.value, title: f.title.value, author: f.author.value, pages: Number(f.pages.value),
    description: f.description.value, copies: Number(f.copies.value),
    genres: [...$("book-genres").selectedOptions].map((o) => o.value),
  };
}

// ---- Notices ---------------------------------------------------------------------------

async function pollNotices() {
  if (!state.me) return;
  const data = await api("GET", "/api/notifications").catch(() => null);
  if (!data || !data.notices.length) return;
  for (const notice of data.notices) {
    state.notices.push(notice.message);
    bubble("🔔 " + notice.message, "notice");
  }
  status("New notice: " + data.notices[data.notices.length - 1].message);
  renderHeader();
  refreshTab(state.tab);
}

// ---- Wiring ----------------------------------------------------------------------------

function wire() {
  $("form-signin").addEventListener("submit", async (e) => {
    e.preventDefault();
    $("signin-error").textContent = "";
    try {
      await api("POST", "/api/login", formData(e.target));
      e.target.reset();
      await signedInChanged();
    } catch (error) { $("signin-error").textContent = error.message; }
  });
  $("form-register").addEventListener("submit", async (e) => {
    e.preventDefault();
    $("register-error").textContent = "";
    const f = e.target;
    try {
      await api("POST", "/api/register", {
        name: f.name.value, email: f.email.value, memberType: f.memberType.value,
        maxPages: f.maxPages.value ? Number(f.maxPages.value) : 0,
        favoriteGenres: [...$("reg-genres").selectedOptions].map((o) => o.value),
        favoriteAuthors: f.favoriteAuthors.value.split(",").map((s) => s.trim()).filter(Boolean),
      });
      f.reset();
      await signedInChanged();
      status("Welcome, " + state.me.name + "! Your member ID is " + state.me.memberId + ".");
    } catch (error) { $("register-error").textContent = error.message; }
  });
  $("btn-guest").addEventListener("click", () => showApp("catalog"));
  $("btn-chat-signup").addEventListener("click", () => { showApp("chat"); sendChat("I'd like to sign up"); });
  $("btn-signout").addEventListener("click", async () => {
    if (state.me) await attempt(() => api("POST", "/api/logout"));
    await loadMe();
    resetChat();
    state.notices = [];
    showStart();
    status("Signed out.");
  });
  $("btn-undo").addEventListener("click", async () => {
    const r = await attempt(() => api("POST", "/api/undo"));
    if (r) { status(r.message); refreshTab(state.tab); }
  });
  $("btn-notices").addEventListener("click", () => {
    alert(state.notices.length ? state.notices.join("\n\n") : "No new notices.");
    state.notices = [];
    renderHeader();
  });
  document.querySelectorAll(".tabs button").forEach((b) => b.addEventListener("click", () => selectTab(b.dataset.tab)));
  $("form-chat").addEventListener("submit", (e) => { e.preventDefault(); sendChat($("chat-message").value); });
  $("form-search").addEventListener("submit", (e) => { e.preventDefault(); search(); });
  $("tracking").addEventListener("change", async (e) => {
    const r = await attempt(() => api("POST", "/api/tracking", { on: e.target.checked }));
    if (r) status(e.target.checked ? "Reading feedback tracking is on." : "Reading feedback tracking is off. Ratings won't be saved.");
  });
  $("form-new-list").addEventListener("submit", async (e) => {
    e.preventDefault();
    const name = e.target.name.value;
    await act("POST", "/api/lists", { name });
    state.selectedList = name;
    e.target.reset();
    renderLists();
  });
  $("btn-generate-list").addEventListener("click", () =>
    askInChat("Make me a reading list of 5 books based on my tastes, give it a fitting name, and save it."));
  $("form-checkout").addEventListener("submit", async (e) => {
    e.preventDefault();
    deskResult(await attempt(() => api("POST", "/api/desk/checkout", formData(e.target))));
  });
  $("form-checkin").addEventListener("submit", async (e) => {
    e.preventDefault();
    deskResult(await attempt(() => api("POST", "/api/desk/checkin", formData(e.target))));
  });
  $("btn-overdue").addEventListener("click", loadOverdue);
  $("btn-suggest").addEventListener("click", async () => {
    $("addbook-result").textContent = "Asking the assistant…";
    const r = await attempt(() => api("POST", "/api/desk/suggest-genres", bookForm()));
    if (!r) { $("addbook-result").textContent = ""; return; }
    for (const option of $("book-genres").options) option.selected = r.genres.includes(option.value);
    $("addbook-result").textContent = r.genres.length
      ? "Suggested: " + r.genres.join(", ") + ". Adjust if needed, then add the book."
      : "No suggestion; please choose genres yourself.";
  });
  $("form-addbook").addEventListener("submit", async (e) => {
    e.preventDefault();
    const r = await attempt(() => api("POST", "/api/desk/books", bookForm()));
    if (r) {
      $("addbook-result").textContent = 'Added "' + r.book.title + '" with ' + r.book.copies.length + " copies.";
      e.target.reset();
    }
  });
}

async function init() {
  wire();
  resetChat();
  const genres = await api("GET", "/api/genres");
  for (const name of genres.subgenres) {
    $("reg-genres").append(el("option", { value: name }, name));
    $("book-genres").append(el("option", { value: name }, name));
  }
  for (const name of genres.all) $("search-genre").append(el("option", { value: name }, name));
  await loadMe();
  if (state.me) showApp("chat"); else showStart();
  setInterval(pollNotices, 15000);
}

init().catch((error) => status("Could not reach the server: " + error.message, true));
