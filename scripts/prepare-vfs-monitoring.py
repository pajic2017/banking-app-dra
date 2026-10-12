#!/usr/bin/env python3
"""Local VFS-only vendor-image flattening, using digest-verified registry content.
Requires the official checksum-verified crane tool; normal Compose images stay unchanged.
"""
import json,os,subprocess,tempfile
from pathlib import Path
from local_oidc import ROOT
crane='/workspace/toolchains/bin/crane'
if subprocess.check_output(['docker','info','--format','{{.Driver}}'],text=True).strip()!='vfs':raise SystemExit('Flattening is only for the constrained VFS cloud machine.')
config={'services':{}};provenance={}
images={'elasticsearch':'library/elasticsearch:8.17.6','logstash':'library/logstash:8.17.6','kibana':'library/kibana:8.17.6',
        'prometheus':'prom/prometheus:v3.5.0','grafana':'grafana/grafana:12.1.1','zipkin':'openzipkin/zipkin:3.5','filebeat':'elastic/filebeat:8.17.6'}
for service,reference in images.items():
    source='registry-1.docker.io/'+reference;target='bank-local-'+service+':'+reference.rsplit(':',1)[1]
    exists=subprocess.run(['docker','image','inspect',target],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode==0
    digest=subprocess.check_output([crane,'digest',source],text=True).strip()
    pinned=source.rsplit(':',1)[0]+'@'+digest
    if exists:
        recorded=subprocess.check_output(['docker','image','inspect','--format','{{index .Config.Labels "bank.vendor.source"}}',target],text=True).strip()
        if recorded!=pinned:raise SystemExit('Local vendor image digest differs; review before rebuilding this local copy.')
    if not exists:
        local_source=reference.removeprefix('library/')
        local=subprocess.run(['docker','image','inspect',local_source],stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
        local_image=json.loads(local.stdout)[0] if local.returncode==0 else None
        if local_image is not None and not any(d.endswith('@'+digest) for d in local_image.get('RepoDigests',[])):raise SystemExit('Local vendor digest does not match the registry reference.')
        image=json.loads(subprocess.check_output([crane,'config',pinned])) if local_image is None else None
        metadata=image['config'] if image is not None else local_image['Config']
        args=['docker','import','--change','LABEL bank.vendor.source='+pinned]
        for key,directive in [('Entrypoint','ENTRYPOINT'),('Cmd','CMD')]:
            if metadata.get(key):args+=['--change',directive+' '+json.dumps(metadata[key])]
        for key,directive in [('WorkingDir','WORKDIR'),('User','USER'),('StopSignal','STOPSIGNAL')]:
            if metadata.get(key):args+=['--change',directive+' '+metadata[key]]
        for entry in metadata.get('Env',[]):
            key,value=entry.split('=',1);args+=['--change','ENV '+key+'='+json.dumps(value)]
        for port in metadata.get('ExposedPorts',{}):args+=['--change','EXPOSE '+port]
        args+=['-',target]
        temporary=None
        if local_image is not None:
            temporary=subprocess.check_output(['docker','create',local_source],text=True).strip()
            exporter=subprocess.Popen(['docker','export',temporary],stdout=subprocess.PIPE)
        else:exporter=subprocess.Popen([crane,'export',pinned,'-'],stdout=subprocess.PIPE)
        imported=subprocess.run(args,stdin=exporter.stdout,stdout=subprocess.DEVNULL)
        exporter.stdout.close();status=exporter.wait()
        if temporary:subprocess.run(['docker','rm','--volumes',temporary],stdout=subprocess.DEVNULL,check=True)
        if status or imported.returncode:raise SystemExit('Verified vendor filesystem export/import failed.')
    # Retain the verified local copy; remove only an unused duplicate vendor tag, never force removal.
    if service in ('prometheus','grafana','zipkin','filebeat'):
        subprocess.run(['docker','image','rm',reference.removeprefix('library/')],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    provenance[service]=pinned;config['services'][service]={'image':target}
    print('Prepared verified single-layer local image:',service,flush=True)
directory=ROOT/'.run/monitoring';directory.mkdir(parents=True,exist_ok=True)
(directory/'vfs.compose.json').write_text(json.dumps(config,indent=2)+'\n')
(directory/'image-provenance.json').write_text(json.dumps(provenance,indent=2)+'\n')
