#!/usr/bin/env python3
"""End-to-end smoke test: login > KYC > account > fund > transfer > Kafka notifications/audit."""
import json, os, sys, time, uuid
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.parse import urlparse
from urllib.error import HTTPError

base=os.environ.get('DEMO_URL','http://localhost:8080')
vars={}
for line in (Path(__file__).resolve().parents[1]/'.env').read_text().splitlines():
    if '=' in line and not line.startswith('#'):
        k,v=line.split('=',1);vars[k]=v

def api(method,path,payload=None,token=None,headers=None,expect=(200,201)):
    h={'Content-Type':'application/json','X-Correlation-Id':str(uuid.uuid4())}
    if token:h['Authorization']='Bearer '+token
    if headers:h.update(headers)
    request=Request(base+path, data=json.dumps(payload).encode() if payload is not None else None, headers=h, method=method)
    try:
        with urlopen(request,timeout=15) as r: code=r.status;raw=r.read()
    except HTTPError as ex:
        code=ex.code;raw=ex.read()
    try:result=json.loads(raw or b'{}')
    except json.JSONDecodeError:result={'raw':raw.decode(errors='replace')}
    if code not in expect:raise AssertionError(f'{method} {path}: expected {expect}; got {code}: {result}')
    return result

def login(name,password):
    return api('POST','/api/auth/login',{'username':name,'password':password})['accessToken']

auth_base=os.environ.get('AUTH_URL','http://localhost:8081')
print('Waiting for gateway and auth-service to become ready (up to 120s)...')
end_ready=time.time()+120
while time.time()<end_ready:
    try:
        with urlopen(base+'/actuator/health',timeout=3) as gateway_health, urlopen(auth_base+'/actuator/health',timeout=3) as auth_health:
            if gateway_health.status==200 and auth_health.status==200:break
    except Exception:pass
    time.sleep(2)
else:raise SystemExit('FAIL: gateway or auth-service never became healthy. Check docker compose logs')

print('Checking OAuth2/OIDC discovery and signing keys')
with urlopen(auth_base+'/.well-known/openid-configuration',timeout=10) as response:
    metadata=json.loads(response.read())
expected_issuer=vars.get('JWT_ISSUER','http://localhost:8081')
if metadata.get('issuer') != expected_issuer:
    raise AssertionError(f"OIDC issuer mismatch: expected {expected_issuer}, got {metadata.get('issuer')}")
for key,path in [('authorization_endpoint','/oauth2/authorize'),('token_endpoint','/oauth2/token'),('jwks_uri','/oauth2/jwks')]:
    if urlparse(metadata.get(key,'' )).path != path:
        raise AssertionError(f"OIDC discovery has invalid {key}: {metadata.get(key)}")
with urlopen(metadata['jwks_uri'],timeout=10) as response:
    jwks=json.loads(response.read())
if not any(key.get('kid')=='demo-rsa-1' and key.get('kty')=='RSA' for key in jwks.get('keys',[])):
    raise AssertionError('OAuth2 JWKS did not publish the expected demo-rsa-1 RSA key')
print('PASS: OIDC discovery, authorization/token endpoints, and JWKS signing key')

print('1/9 Logging in as demo and admin')
end_auth=time.time()+90
while True:
    try:
        admin=login('admin',vars['DEMO_ADMIN_PASSWORD'])
        user=login('demo',vars['DEMO_USER_PASSWORD'])
        break
    except Exception:
        if time.time()>end_auth:raise
        time.sleep(2)
print('2/9 Creating demo customer, then approving KYC')
customer=api('POST','/api/customers',{'firstName':'Demo','lastName':'Customer','dateOfBirth':'1998-01-01','phone':'00000000'},user)
customer_id=customer['id']
api('POST',f'/api/customers/{customer_id}/kyc/approve',{},admin)
print('3/9 Opening two owned USD accounts')
a=api('POST','/api/accounts',{'customerId':customer_id,'currency':'USD'},user)
b=api('POST','/api/accounts',{'customerId':customer_id,'currency':'USD'},user)
print('4/9 Funding first account with offsetting clearing ledger entry')
api('POST',f"/api/accounts/{a['id']}/fund",{'amount':'500.00'},admin)
print('5/9 Performing transfer for 125.00 USD')
key='demo-'+str(uuid.uuid4())
body={'sourceAccountId':a['id'],'destinationAccountId':b['id'],'amount':'125.00','currency':'USD','reference':'Demo order'}
result=api('POST','/api/transfers',body,user,{'Idempotency-Key':key})
assert result['status']=='COMPLETED', result
print('6/9 Retrying identical request using same idempotency key')
again=api('POST','/api/transfers',body,user,{'Idempotency-Key':key})
assert result['id']==again['id'], 'Duplicate created new transfer!'
print('7/9 Verifying balances and ledger-backed atomic account operation')
source=api('GET',f"/api/accounts/{a['id']}",token=user)
target=api('GET',f"/api/accounts/{b['id']}",token=user)
assert str(source['availableBalance']) in ('375.00','375.0','375'),source
assert str(target['availableBalance']) in ('125.00','125.0','125'),target
ledger=api('GET',f"/api/accounts/ledger/{result['id']}",token=admin)
assert ledger['balanced'] is True and len(ledger['entries'])==2,ledger
assert str(ledger['totalDebit'])==str(ledger['totalCredit']),ledger
print('8/9 Ensuring same key with different body fails')
bad={**body,'amount':'1.00'}
api('POST','/api/transfers',bad,user,{'Idempotency-Key':key},expect=(409,))
print('9/9 Waiting briefly for asynchronous event consumers')
end=time.time()+45
while time.time()<end:
    notes=api('GET','/api/notifications',token=user)
    audits=api('GET',f"/api/audit/transfers/{result['id']}",token=admin)
    if any(result['id'] in n['message'] for n in notes) and audits:
        print('\nPASS: auth, KYC, 2 accounts, funding, transfer, idempotency, balances, Kafka notification and audit')
        print(json.dumps({'customerId':customer_id,'accountA':a['id'],'accountB':b['id'],'transferId':result['id'],'sourceBalance':str(source['availableBalance']),'targetBalance':str(target['availableBalance']),'notifications':len(notes),'auditEvents':len(audits),'ledgerBalanced':ledger['balanced']},indent=2))
        sys.exit(0)
    time.sleep(2)
raise SystemExit('FAIL: transfer passed but Kafka notifications/audit did not arrive; check broker & outbox logs')
