#!/usr/bin/env python3
"""Publish a Test build to the server (api/app_update/upload/*), in 5 MB chunks.

    upload.py <api base url> <app: staff|client> <apk> <version code> <version name> <notes>

The admin token comes from the environment (PUBLISH_TOKEN), never the command
line, so it stays out of process listings and shell history.
"""
import hashlib, json, os, ssl, sys, urllib.error, urllib.parse, urllib.request

base, app, path, code, name, notes = sys.argv[1:7]
token = os.environ.get('PUBLISH_TOKEN', '')
host = os.environ.get('PUBLISH_HOST', '')   # dev: the vhost behind 127.0.0.1
ctx = ssl._create_unverified_context() if host else ssl.create_default_context()


def call(url, data, ctype):
    req = urllib.request.Request(base + url, data=data, method='POST')
    req.add_header('Authtoken', token)
    req.add_header('Content-Type', ctype)
    if host:
        req.add_header('Host', host)
    try:
        with urllib.request.urlopen(req, context=ctx, timeout=180) as r:
            return r.status, json.loads(r.read() or b'{}')
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read() or b'{}')
        except ValueError:
            return e.code, {'message': 'HTTP %d' % e.code}


def form(d):
    return urllib.parse.urlencode(d).encode(), 'application/x-www-form-urlencoded'


data = open(path, 'rb').read()
st, r = call('app_update/upload/begin', *form({'app': app, 'version_code': code, 'version_name': name,
                                                'notes': notes, 'size': len(data),
                                                'sha256': hashlib.sha256(data).hexdigest()}))
if st != 200:
    sys.exit('Not published: %s' % r.get('message', st))
tok, off, chunk = r['data']['token'], 0, 5 * 1024 * 1024
while off < len(data):
    st, r = call('app_update/upload/chunk?' + urllib.parse.urlencode({'app': app, 'token': tok, 'offset': off}),
                 data[off:off + chunk], 'application/octet-stream')
    if st != 200:
        sys.exit('Upload stopped: %s' % r.get('message', st))
    off = r['data']['received']
    print('\r  %d of %d MB' % (off >> 20, len(data) >> 20), end='', flush=True)
print()
st, r = call('app_update/upload/finish', *form({'app': app, 'token': tok}))
if st != 200:
    sys.exit('Not published: %s' % r.get('message', st))
l = r['data']['latest']
print('Published %s (%s), %.1f MB' % (l['version_name'], l['version_code'], l['size'] / 1048576))
