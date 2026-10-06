import re, subprocess, sys
PAT = re.compile(r'(ghp_[A-Za-z0-9]{20,}|gho_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_\-]{30,}|sk-[A-Za-z0-9]{32,}|-----BEGIN [A-Z ]*PRIVATE KEY-----|'
                 r'(?i:(?:password|passwd|secret|api[_-]?key|token|storepass|keypass)\s*[=:]\s*["\']?[A-Za-z0-9_\-/+]{12,}))')
FILES = re.compile(r'(\.jks|\.keystore|\.pem|\.p12|\.env|local\.properties|id_rsa|google-services\.json|\.apk|\.aab)$', re.I)
for repo in sys.argv[1:]:
    print('==', repo)
    names = subprocess.run(['git', '-C', repo, 'log', '--all', '--name-only', '--pretty=format:'], capture_output=True, text=True).stdout.split('\n')
    bad = sorted({n for n in names if n and FILES.search(n)})
    print('sensitive-looking files in history:', bad[:20])
    p = subprocess.Popen(['git', '-C', repo, 'log', '--all', '-p', '--no-color', '--pretty=format:commit %h'], stdout=subprocess.PIPE, text=True, errors='replace')
    commit = ''; hits = 0
    for line in p.stdout:
        if line.startswith('commit '):
            commit = line[7:].strip(); continue
        if line.startswith('+') and len(line) < 400:
            m = PAT.search(line)
            if m and hits < 15:
                hits += 1
                print(' ', commit, line.strip()[:160])
    print('hits:', hits)
