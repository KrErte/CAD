import paramiko, sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect("62.171.153.133", username="root", password="irval55678", timeout=15)

def run(cmd, timeout=120):
    print(f"\n>>> {cmd}")
    _, stdout, stderr = ssh.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode('utf-8', errors='replace').strip()
    err = stderr.read().decode('utf-8', errors='replace').strip()
    rc = stdout.channel.recv_exit_status()
    if out: print(out[-2000:])
    if err: print(f"STDERR: {err[-1000:]}")
    return rc, out

# Check what backend endpoints exist
run("curl -s http://localhost:8080/api/templates | head -c 200")
run("curl -s http://localhost:8080/ | head -c 200")

# Check frontend issue - likely Angular host check
run("curl -s -H 'Host: tehisaicad.ee' http://localhost:4200 | head -c 500")
run("curl -s http://localhost:4200 | head -c 500")

# Check Angular Dockerfile for --disable-host-check
run("cat /root/CAD/frontend/Dockerfile")
run("cat /root/CAD/frontend/package.json | grep -A2 start")

ssh.close()
