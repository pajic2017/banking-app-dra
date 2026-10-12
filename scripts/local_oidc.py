"""Local development OIDC helpers. Tokens/passwords stay in memory; never log them."""
import base64
import hashlib
import html
import http.cookiejar
import json
import os
import re
import secrets
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
ISSUER=os.environ.get('KEYCLOAK_ISSUER','http://localhost:8090/realms/banking')
USERS={role:'phase6-'+role for role in ('admin','employee','alice','bob','roleless')}
def local_value(name):
    if os.environ.get(name): return os.environ[name]
    for line in (ROOT/'.env').read_text().splitlines():
        key,sep,value=line.partition('=')
        if sep and key.strip()==name and value.strip(): return value.strip()
    raise RuntimeError(f'Missing local configuration: {name}')

def form(url,values):
    request=urllib.request.Request(url,data=urllib.parse.urlencode(values).encode(),headers={'Content-Type':'application/x-www-form-urlencoded'})
    try:
        with urllib.request.urlopen(request,timeout=10) as response:
            raw=response.read();return json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        raise RuntimeError(f'OIDC request failed with HTTP {error.code}') from None

def service_token(client):
    return form(ISSUER+'/protocol/openid-connect/token',{'grant_type':'client_credentials','client_id':client,
        'client_secret':local_value(client.split('-')[0].upper()+'_SERVICE_CLIENT_SECRET')})['access_token']

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,request,fp,code,msg,headers,newurl): return None

def login(role='admin'):
    """Exercise real Keycloak login and S256 code exchange for local fixtures; not a password grant."""
    if ISSUER!='http://localhost:8090/realms/banking':raise RuntimeError('Local fixture login requires the loopback development issuer; supply BANK_ACCESS_TOKEN for external APIs.')
    verifier=secrets.token_urlsafe(48)
    challenge=base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip('=')
    state=secrets.token_urlsafe(24)
    redirect='http://localhost:8080/callback'
    query=urllib.parse.urlencode({'client_id':'banking-local','response_type':'code','scope':'openid','redirect_uri':redirect,
        'state':state,'code_challenge':challenge,'code_challenge_method':'S256'})
    cookies=http.cookiejar.CookieJar()
    browser=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookies),NoRedirect())
    with browser.open(ISSUER+'/protocol/openid-connect/auth?'+query,timeout=10) as response: page=response.read().decode()
    action=re.search(r'<form[^>]+action="([^"]+)"',page)
    if not action: raise RuntimeError('Keycloak login form was not returned.')
    body=urllib.parse.urlencode({'username':USERS[role],'password':local_value('BANK_'+role.upper()+'_PASSWORD'),'credentialId':''}).encode()
    try:
        action_url=html.unescape(action.group(1))
        headers={}
        # Browsers treat localhost as a trustworthy origin for Secure cookies; urllib does not.
        # Limit this development adapter to the exact same loopback origin and realm path.
        origin=urllib.parse.urlsplit(ISSUER);target=urllib.parse.urlsplit(action_url)
        if (origin.scheme,origin.netloc)==(target.scheme,target.netloc) and origin.scheme=='http' and origin.hostname=='localhost':
            headers['Cookie']='; '.join(c.name+'='+c.value for c in cookies if target.path.startswith(c.path))
        browser.open(urllib.request.Request(action_url,data=body,headers=headers),timeout=10)
        raise RuntimeError('Keycloak login did not return an authorization code.')
    except urllib.error.HTTPError as response:
        if response.code not in (302,303): raise RuntimeError(f'Keycloak login failed: HTTP {response.code}') from None
        location=response.headers['Location']
    result=urllib.parse.parse_qs(urllib.parse.urlsplit(location).query)
    if result.get('state')!=[state] or not result.get('code'): raise RuntimeError('OIDC state/code check failed.')
    return form(ISSUER+'/protocol/openid-connect/token',{'grant_type':'authorization_code','client_id':'banking-local',
        'code':result['code'][0],'code_verifier':verifier,'redirect_uri':redirect})

_admin_session=None
_admin_renew_at=0
_admin_lock=threading.Lock()
def admin_token():
    global _admin_session,_admin_renew_at
    if os.environ.get('BANK_ACCESS_TOKEN'): return os.environ['BANK_ACCESS_TOKEN']
    with _admin_lock:
        if _admin_session is None or time.monotonic() >= _admin_renew_at:
            if _admin_session is not None:
                try: _admin_session=form(ISSUER+'/protocol/openid-connect/token',{'grant_type':'refresh_token','client_id':'banking-local','refresh_token':_admin_session['refresh_token']})
                except RuntimeError: _admin_session=None
            if _admin_session is None: _admin_session=login('admin')
            _admin_renew_at=time.monotonic()+max(1,int(_admin_session['expires_in'])-30)
        return _admin_session['access_token']

def management_token():
    if ISSUER!='http://localhost:8090/realms/banking':raise RuntimeError('Local administration is restricted to the loopback development realm.')
    base=ISSUER.rsplit('/realms/',1)[0]
    return form(base+'/realms/master/protocol/openid-connect/token',{'grant_type':'password','client_id':'admin-cli',
        'username':'local-admin','password':local_value('KEYCLOAK_ADMIN_PASSWORD')})['access_token']

def management(method,path,body=None,token=None):
    base=ISSUER.rsplit('/realms/',1)[0]+'/admin/realms/banking'
    request=urllib.request.Request(base+path,data=json.dumps(body).encode() if body is not None else None,
        headers={'Authorization':'Bearer '+(token or management_token()),'Content-Type':'application/json'},method=method)
    try:
        with urllib.request.urlopen(request,timeout=15) as response:
            raw=response.read();return json.loads(raw) if raw else None
    except urllib.error.HTTPError as error: raise RuntimeError(f'Keycloak administration failed: HTTP {error.code}') from None

def bind_customer(role,customer_id):
    """Privileged development fixture binding; business customers cannot assign this claim themselves."""
    token=management_token();rows=management('GET','/users?username='+USERS[role]+'&exact=true',token=token)
    if len(rows)!=1 or rows[0].get('attributes',{}).get('banking_fixture')!=['phase6']: raise RuntimeError('Fixture identity not found.')
    row=rows[0];row.setdefault('attributes',{})['customer_id']=[str(customer_id)]
    management('PUT','/users/'+row['id'],row,token)
