# Deploying the Library Agent web version

This runs the web version on a Linux VPS behind [Caddy](https://caddyserver.com/), which gets and renews an HTTPS certificate for your domain automatically. Two containers run:

- **app**: the Library Agent web server (JSON API and browser interface) on port 8080 inside Docker.
- **caddy**: the public entry point on ports 80/443, forwarding to `app`.

The library database lives in a Docker volume, so it survives restarts and rebuilds.

---

## 1. Prerequisites

- A Linux VPS (Ubuntu 22.04/24.04 or Debian 12 recommended) with **at least 1 GB RAM** (2 GB is more comfortable for the build step).
- SSH access with `sudo`.
- A domain or subdomain you control, e.g. `library.example.com`.
- An Anthropic API key. Use a **separate key** for the website, and set a monthly spend limit for it in the Anthropic Console as a second safety net.

## 2. Point the domain at the VPS

At your DNS provider, create an **A record** (and an **AAAA record** if the VPS has IPv6) from your domain to the VPS's public IP. Check it from your computer:

```bash
nslookup library.example.com
```

Wait until it returns the VPS IP before step 6, or Caddy can't get a certificate.

## 3. Install Docker on the VPS

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER    # then log out and back in
docker --version && docker compose version
```

## 4. Open the firewall

If `ufw` is enabled:

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 443/udp
```

Also allow ports 80 and 443 in your provider's cloud firewall, if it has one.

## 5. Get the code and configure it

```bash
sudo mkdir -p /opt/library-agent && sudo chown $USER /opt/library-agent
git clone https://github.com/AGNIV03/EECS-3311-Project.git /opt/library-agent
cd /opt/library-agent
cp .env.example .env
chmod 600 .env
nano .env
```

Set at least these values in `.env`:

| Key | Set it to |
|---|---|
| `ANTHROPIC_API_KEY` | Your API key for the website |
| `DOMAIN` | Your domain, e.g. `library.example.com` (no `https://`) |
| `DAILY_BUDGET_USD` | Most you are willing to spend per day on the AI assistant, e.g. `2.00` |
| `LIBRARIAN_PASSCODE` | A long random passcode, for markers or demo use. Leave blank to disable librarian sign-in on the website |
| `CHAT_LIMIT_PER_MINUTE` / `CHAT_LIMIT_PER_HOUR` | Per-visitor chat limits (defaults 6 and 40) |

Leave `DB_PATH`, `WEB_PORT`, `TRUST_PROXY` and `COOKIE_SECURE` alone; `docker-compose.yml` sets them for the container.

## 6. Build and start

```bash
docker compose up -d --build
```

The first build downloads Gradle and the libraries and takes a few minutes. Then check:

```bash
docker compose ps                     # both services "running"
docker compose logs -f app            # "Library Agent web version listening on port 8080"
docker compose logs -f caddy          # certificate obtained for your domain
curl https://library.example.com/api/health    # {"status":"ok"}
```

Open `https://<your domain>` in a browser.

## 7. Everyday operations

| Task | Command (in `/opt/library-agent`) |
|---|---|
| Update to the latest code | `git pull && docker compose up -d --build` |
| View logs | `docker compose logs -f app` |
| Restart | `docker compose restart app` |
| Stop everything | `docker compose down` |
| Back up the database | `docker compose cp app:/app/data/library.db ./library-backup.db` |
| Reset to the sample data | `docker compose down && docker volume rm library-agent_library-data && docker compose up -d` |
| Change a setting | Edit `.env`, then `docker compose up -d` |

The volume name is prefixed with the project folder name. Check it with `docker volume ls` if the reset command reports no such volume.

## 8. Cost and abuse protection (built in)

- **Daily budget:** once the estimated spend for the day reaches `DAILY_BUDGET_USD`, chat replies say the daily budget is used up. Everything else (search, reserve, account, lists) keeps working, and the budget resets at midnight server time.
- **Rate limits:** each visitor (IP address) can send `CHAT_LIMIT_PER_MINUTE` messages per minute and `CHAT_LIMIT_PER_HOUR` per hour, plus 3 sign-ups per minute. Caddy replaces the client address header, so visitors can't spoof it.
- **Size limits:** chat messages are capped at 2,000 characters and request bodies at 16 KB.
- **Librarian functions** (desk checkout and check-in, overdue list, adding books) require `LIBRARIAN_PASSCODE`.
- **Sessions** are per-browser cookies (HttpOnly, SameSite=Strict, Secure) and expire after 2 idle hours.

Visitors share one demo library, so one visitor's reservations are visible to others. Reset the data (section 7) whenever you want a clean slate, for example before recording demo videos.

## 9. Troubleshooting

| Symptom | Likely cause |
|---|---|
| Caddy logs show certificate errors | DNS not pointing at the VPS yet, or ports 80/443 blocked |
| `502 Bad Gateway` | `app` is still starting or crashed. Check `docker compose logs app` |
| Chat says the assistant is not configured | `ANTHROPIC_API_KEY` missing or still the placeholder in `.env` |
| "Librarian sign-in is disabled on this site." | `LIBRARIAN_PASSCODE` is blank |
| Build fails with out-of-memory | Add swap or use a VPS with 2 GB RAM |
