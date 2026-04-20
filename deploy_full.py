import paramiko, time, sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
print("Connecting...")
ssh.connect("62.171.153.133", username="root", password="irval55678", timeout=15)
print("Connected!")

def run(cmd, timeout=300):
    print(f"\n>>> {cmd}")
    _, stdout, stderr = ssh.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode('utf-8', errors='replace').strip()
    err = stderr.read().decode('utf-8', errors='replace').strip()
    rc = stdout.channel.recv_exit_status()
    if out: print(out[-3000:])
    if err: print(f"STDERR: {err[-2000:]}")
    print(f"[exit: {rc}]")
    return rc, out

# Check where repo is
run("ls /root/CAD/Caddyfile 2>/dev/null && echo 'ROOT' || (ls /opt/CAD/Caddyfile 2>/dev/null && echo 'OPT' || echo 'NEITHER')")

# Pull latest
run("cd /root/CAD && git pull")

# Install Caddy
run("apt update -qq && apt install -y -qq caddy", timeout=180)

# Copy Caddyfile
run("cp /root/CAD/Caddyfile /etc/caddy/Caddyfile && cat /etc/caddy/Caddyfile")

# Enable and restart Caddy
run("systemctl enable caddy && systemctl restart caddy")
run("systemctl status caddy --no-pager -l")

# .env updates
run("cd /root/CAD && grep -q JWT_SECRET .env 2>/dev/null || echo \"JWT_SECRET=$(openssl rand -base64 48 | tr -d '\\n')\" >> .env")
run("cd /root/CAD && grep -q FRONTEND_URL .env 2>/dev/null || echo 'FRONTEND_URL=https://tehisaicad.ee' >> .env")
run("cd /root/CAD && grep -q OAUTH_REDIRECT_URI .env 2>/dev/null || echo 'OAUTH_REDIRECT_URI=https://tehisaicad.ee/login/oauth2/code/google' >> .env")

# Redeploy
run("cd /root/CAD && docker compose up -d --build", timeout=600)

# Firewall
run("ufw allow 22/tcp && ufw allow 80/tcp && ufw allow 443/tcp && ufw deny 4200 && ufw deny 8080 && ufw --force enable")

# Wait and check
print("\nWaiting 15s...")
time.sleep(15)
run("cd /root/CAD && docker compose ps")
run("ss -tlnp | grep -E ':80|:443'")
run("curl -s https://tehisaicad.ee/api/health || echo 'HTTPS failed'")
run("curl -s http://localhost:8080/api/health || echo 'Backend local failed'")
run("curl -s -o /dev/null -w '%{http_code}' https://tehisaicad.ee || echo 'Frontend HTTPS failed'")

print("\n" + "="*50)
print("https://tehisaicad.ee")
print("="*50)
ssh.close()
