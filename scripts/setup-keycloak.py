#!/usr/bin/env python3
"""Reconcile this repository's local development realm, clients and tagged fixture users.
No credentials are printed. Do not run against a production identity provider.
"""
import json
from local_oidc import ROOT,ISSUER,USERS,local_value,management,management_token

def setup():
    if ISSUER!='http://localhost:8090/realms/banking':raise SystemExit('Provisioning is restricted to the local development realm.')
    token=management_token()
    config=json.loads((ROOT/'infrastructure/keycloak/banking-realm.json').read_text())
    # Realm import only applies on first startup; reconcile deliberate Phase 6 settings on retained volumes.
    realm={k:config[k] for k in ['accessTokenLifespan','ssoSessionIdleTimeout','ssoSessionMaxLifespan','sslRequired','registrationAllowed','resetPasswordAllowed']}
    management('PUT','',realm,token)
    roles={r['name'] for r in management('GET','/roles',token=token)}
    for role in config['roles']['realm']:
        if role['name'] not in roles: management('POST','/roles',role,token)
    scopes=management('GET','/client-scopes',token=token)
    scope=next((s for s in scopes if s['name']=='banking-api'),None)
    if scope is None:
        management('POST','/client-scopes',config['clientScopes'][0],token);scopes=management('GET','/client-scopes',token=token);scope=next(s for s in scopes if s['name']=='banking-api')
    profile=management('GET','/users/profile',token=token)
    profile.pop('unmanagedAttributePolicy',None)  # Absent policy disables unmanaged attributes in Keycloak 26.
    profile['attributes']=[a for a in profile['attributes'] if a['name'] not in ('customer_id','banking_fixture')]
    for name in ['customer_id','banking_fixture']:
        profile['attributes'].append({'name':name,'displayName':name,'permissions':{'view':['admin'],'edit':['admin']},'multivalued':False})
    management('PUT','/users/profile',profile,token)
    for client in config['clients']:
        found=management('GET','/clients?clientId='+client['clientId'],token=token)
        if not found: management('POST','/clients',client,token);found=management('GET','/clients?clientId='+client['clientId'],token=token)
        client_id=found[0]['id'];definition=dict(client)
        if client['clientId']!='banking-local': definition['secret']=local_value(client['clientId'].split('-')[0].upper()+'_SERVICE_CLIENT_SECRET')
        management('PUT','/clients/'+client_id,definition,token)
        for name in client['defaultClientScopes']:
            target=next(s for s in scopes if s['name']==name)
            management('PUT',f'/clients/{client_id}/default-client-scopes/{target["id"]}',token=token)
        if client['clientId']!='banking-local':
            account=management('GET',f'/clients/{client_id}/service-account-user',token=token)
            mappings=management('GET',f'/users/{account["id"]}/role-mappings/realm',token=token)
            if mappings: management('DELETE',f'/users/{account["id"]}/role-mappings/realm',mappings,token)
            role_name='SERVICE_'+client['clientId'].split('-')[0].upper()
            role=management('GET','/roles/'+role_name,token=token)
            management('POST',f'/users/{account["id"]}/role-mappings/realm',[role],token)
    for kind,username in USERS.items():
        found=management('GET','/users?username='+username+'&exact=true',token=token)
        if found and found[0].get('attributes',{}).get('banking_fixture')!=['phase6']:
            raise RuntimeError('Refusing to overwrite an untagged identity.')
        if not found:
            management('POST','/users',{'username':username,'enabled':True,'emailVerified':True,
                'firstName':'Phase6','lastName':kind,'email':username+'@example.com','attributes':{'banking_fixture':['phase6']}},token)
            found=management('GET','/users?username='+username+'&exact=true',token=token)
        user=found[0]['id']
        management('PUT',f'/users/{user}/reset-password',{'type':'password','value':local_value('BANK_'+kind.upper()+'_PASSWORD'),'temporary':False},token)
        mappings=management('GET',f'/users/{user}/role-mappings/realm',token=token)
        if mappings: management('DELETE',f'/users/{user}/role-mappings/realm',mappings,token)
        role={'admin':'ADMIN','employee':'BANK_EMPLOYEE','alice':'CUSTOMER','bob':'CUSTOMER'}.get(kind)
        if role: management('POST',f'/users/{user}/role-mappings/realm',[management('GET','/roles/'+role,token=token)],token)
    print('PASS: local Keycloak realm, S256 public client, audience, admin-only identity attributes, three scoped service identities and five tagged users configured.')
if __name__=='__main__': setup()
