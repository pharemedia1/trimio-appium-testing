import os, re, json, sys
BE = os.path.expanduser('~/StudioProjects/trimio/backend')
ROUTES = os.path.join(BE,'routes')
server = open(os.path.join(BE,'server.js')).read()
MW = ['firebaseAuth','adminAuth','requireStaff','requireProAuth','requireSuperAdmin','requireAdmin',
      'requireStoreAdmin','requireVendor','requireClient','optionalAuth','verifyToken','staffActivity']

def strip_comments(s):
    s = re.sub(r'/\*.*?\*/','',s,flags=re.S)
    return re.sub(r'^\s*//.*$','',s,flags=re.M)

srv = strip_comments(server)

def resolve_var(var, src):
    m = re.search(r"(?:const|let|var)\s+"+re.escape(var)+r"\s*=\s*require\(\s*['\"]\./routes/([^'\"]+)['\"]\s*\)", src)
    return m.group(1) if m else None

def find_uses(src, use_re):
    """Yield (prefix, chain) for app.use/router.use with a string first arg, multi-line safe."""
    out=[]
    for m in re.finditer(use_re + r"\(\s*", src):
        i = m.end()
        depth = 1; j = i
        while j < len(src) and depth:
            if src[j]=='(': depth+=1
            elif src[j]==')': depth-=1
            j+=1
        args = src[i:j-1]
        sm = re.match(r"\s*(['\"])([^'\"]*)\1\s*,?(.*)", args, re.S)
        if sm: out.append((sm.group(2), sm.group(3)))
        else:  out.append(('', args))   # app.use(router) — root mount
    return out

def router_file(chain, src):
    rs = re.findall(r"require\(\s*['\"]\.{1,2}/routes/([^'\"]+)['\"]\s*\)", chain)
    if rs: return rs[-1]
    rs = re.findall(r"require\(\s*['\"]\./([^'\"]+)['\"]\s*\)", chain)
    for cand in reversed(rs):
        if os.path.exists(os.path.join(ROUTES, os.path.basename(cand if cand.endswith('.js') else cand+'.js'))):
            return cand
    v = chain.strip().rstrip(',').split(',')[-1].strip()
    v = re.sub(r'[^\w.]','',v)
    return resolve_var(v, src)

rows=[]; files_seen=set()

def walk(prefix, rfile, inherited_mw, depth=0):
    if not rfile or depth>4: return
    f = rfile if rfile.endswith('.js') else rfile+'.js'
    p = os.path.join(ROUTES, os.path.basename(f))
    if not os.path.exists(p):
        rows.append(dict(prefix=prefix, file=f, method='?', path=prefix, mw=','.join(inherited_mw), note='NOT FOUND')); return
    files_seen.add(os.path.basename(p))
    src = strip_comments(open(p).read())
    # nested router.use mounts
    nested_prefixes=[]
    for np, chain in find_uses(src, r"router\.use"):
        nf = router_file(chain, src)
        if nf:
            nmw = inherited_mw + [m for m in MW if re.search(r'\b'+m+r'\b', chain)]
            full = (prefix.rstrip('/')+'/'+np.lstrip('/')).replace('//','/') or '/'
            nested_prefixes.append(np)
            walk(full, nf, sorted(set(nmw)), depth+1)
    for rm in re.finditer(r"router\.(get|post|put|patch|delete)\(\s*(['\"`])([^'\"`]*)\2\s*,?([^\n]*)", src):
        method, path, tail = rm.group(1).upper(), rm.group(3), rm.group(4)
        rmw = inherited_mw + [m for m in MW if re.search(r'\b'+m+r'\b', tail)]
        full = (prefix.rstrip('/')+'/'+path.lstrip('/')).replace('//','/') or '/'
        rows.append(dict(prefix=prefix, file=os.path.basename(p), method=method, path=full,
                         mw=','.join(sorted(set(rmw))) or 'PUBLIC', note=''))

# routers mounted through normalizeRouter(require(...)) inside if-blocks
MANUAL = [('/webhooks','stripeWebhook.routes.js',[]),
          ('/stripe','stripeOnboardingReturn.routes.js',[]),
          ('/api/pro','proWallet.routes.js',['firebaseAuth','requireProAuth'])]
for pre, f, mw in MANUAL:
    walk(pre, f, mw)

for prefix, chain in find_uses(srv, r"app\.use"):
    rf = router_file(chain, srv)
    if not rf: continue
    mw = sorted(set(m for m in MW if re.search(r'\b'+m+r'\b', chain)))
    walk(prefix or '', rf, mw)

allf = set(f for f in os.listdir(ROUTES) if f.endswith('.js'))
missing = sorted(allf - files_seen)
json.dump(dict(rows=rows, unmounted=missing), open(sys.argv[1],'w'), indent=1)
print("endpoints:", len(rows))
print("route files covered:", len(files_seen), "/", len(allf))
print("still unresolved:", missing)
