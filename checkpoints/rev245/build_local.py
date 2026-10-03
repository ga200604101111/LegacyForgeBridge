#!/usr/bin/env python3
from pathlib import Path
import atexit, hashlib, json, shutil, struct, subprocess, sys, tempfile, zipfile

BASE_SHA='87d4e3cbe4939b21c4eb955b66508bbc2724aafbf6b557c87245cc345121ef63'
OLD_VERSION='0.2.0-alpha.27-corpus4-local.27-rev243-diagnostic.1'
VERSION='0.2.0-alpha.27-corpus4-local.28-rev245-deepdiag.1'
OLD_REVISION='2026-10-02.243-observation-only-motion-diagnostics'
REVISION='2026-10-03.245-deep-motion-correlation-diagnostics'
PKG='dev/yinghuang/legacyforgebridge/'
WORK=Path(__file__).resolve().parent
SRC=WORK/'source'
CLASSES=Path(tempfile.mkdtemp(prefix='lfb-rev245-classes-'))
atexit.register(shutil.rmtree, CLASSES, ignore_errors=True)

def sha(data): return hashlib.sha256(data).hexdigest()

def patch_utf8(data: bytes, replacements: dict[str,str], label: str) -> tuple[bytes,dict[str,int]]:
    if data[:4] != b'\xca\xfe\xba\xbe': raise ValueError(f'{label}: not class')
    cp_count=struct.unpack('>H',data[8:10])[0]
    pos=10; out=bytearray(data[:10]); i=1; counts={k:0 for k in replacements}
    while i<cp_count:
        tag=data[pos];out.append(tag);pos+=1
        if tag==1:
            n=struct.unpack('>H',data[pos:pos+2])[0];pos+=2
            raw=data[pos:pos+n];pos+=n
            try: text=raw.decode('utf-8')
            except UnicodeDecodeError: text=None
            if text is not None:
                for old,new in replacements.items():
                    if old in text:
                        counts[old]+=text.count(old);text=text.replace(old,new)
                raw=text.encode('utf-8')
            out += struct.pack('>H',len(raw))+raw
        elif tag in (3,4): out += data[pos:pos+4];pos+=4
        elif tag in (5,6): out += data[pos:pos+8];pos+=8;i+=1
        elif tag in (7,8,16,19,20): out += data[pos:pos+2];pos+=2
        elif tag in (9,10,11,12,17,18): out += data[pos:pos+4];pos+=4
        elif tag==15: out += data[pos:pos+3];pos+=3
        else: raise ValueError(f'{label}: unknown cp tag {tag} at {i}')
        i+=1
    out += data[pos:]
    return bytes(out),counts

def main(base: Path, output: Path):
    base=base.resolve();output=output.resolve()
    if base==output: raise SystemExit('refusing to overwrite base')
    b=base.read_bytes()
    if sha(b)!=BASE_SHA: raise SystemExit('base SHA-256 mismatch')
    CLASSES.mkdir(parents=True,exist_ok=True)
    sources=sorted(SRC.rglob('*.java'))
    p=subprocess.run(['javac','--release','21','-encoding','UTF-8','-cp',str(base),'-d',str(CLASSES),*map(str,sources)],capture_output=True,text=True)
    if p.returncode: raise SystemExit(p.stdout+p.stderr)
    with zipfile.ZipFile(base) as z:
        if z.testzip() is not None: raise SystemExit('base zip corrupt')
        infos={i.filename:i for i in z.infolist()}; old={n:z.read(n) for n in infos}
    new=dict(old); edits={}
    compiled={f.relative_to(CLASSES).as_posix():f.read_bytes() for f in CLASSES.rglob('*.class')}
    for n,data in compiled.items():
        if not n.startswith(PKG): raise SystemExit('foreign compiled class: '+n)
        new[n]=data
    replacements={
      PKG+'BuildInfo.class': {OLD_VERSION:VERSION,OLD_REVISION:REVISION},
      PKG+'LegacyFileLogger.class': {'version='+OLD_VERSION:'version='+VERSION,'converterRevision='+OLD_REVISION:'converterRevision='+REVISION},
      PKG+'network/FmlConnectionTrace.class': {'version='+OLD_VERSION:'version='+VERSION},
      PKG+'behavior/LegacyClientJumpMotion.class': {
        'LFB source motion diagnostics READY rev243; observation only; all historical Y reconciliation disabled; source jump and rev242 landing effects retained; old jumpEcho={} ignored':
        'LFB source motion diagnostics READY rev245 deep correlation; observation only; all historical Y reconciliation disabled; source jump and rev242 landing effects retained; old jumpEcho={} ignored',
        'action=OBSERVE_ONLY_REV243 caller=':'action=OBSERVE_ONLY_REV245 caller='
      },
      PKG+'behavior/Rev243TraceWriter.class': {
        '=== LegacyForgeBridge rev243 observation-only motion trace ===':'=== LegacyForgeBridge rev245 deep-correlation observation-only motion trace ===',
        'rev243 motion trace stopped at disk budget; use /lfbtrace start for a new capture':'rev245 motion trace stopped at disk budget; use /lfbtrace start for a new capture',
        'rev243 diagnostic writer interrupted; log incomplete':'rev245 diagnostic writer interrupted; log incomplete',
        'rev243 diagnostics writing ':'rev245 diagnostics writing ',
        'rev243 diagnostic writer failed: ':'rev245 diagnostic writer failed: '
      }
    }
    for n,repl in replacements.items():
        patched,counts=patch_utf8(new[n],repl,n)
        missing=[k for k,v in counts.items() if v==0]
        if missing: raise SystemExit(f'{n}: missing string patches {missing}; counts={counts}')
        new[n]=patched;edits[n]=counts
    mod=json.loads(new['fabric.mod.json'])
    if mod.get('version')!=OLD_VERSION: raise SystemExit('unexpected fabric version')
    mod['version']=VERSION
    new['fabric.mod.json']=(json.dumps(mod,indent=2,ensure_ascii=False)+'\n').encode()
    provenance={
      'schema':1,'checkpoint':'rev245-deep-motion-correlation-diagnostics','baseArtifact':base.name,'baseSha256':BASE_SHA,
      'version':VERSION,'converterRevision':REVISION,'cacheCompatibilityVersion':'0.2.0-alpha.27-corpus4-local.17-rev233-cache.1',
      'buildMethod':'offline JDK21 javac replacements + guarded class constant-pool metadata edits over exact rev243 full main JAR',
      'githubActionsBuild':False,'networkDependencyDownloads':False,'fullGradleLoomBuild':False,
      'motionPolicy':'observation-only; rev241/rev242 velocity heuristics remain hard-disabled; source jump and rev242 landing behavior retained',
      'correlation':{
        'schema':'rev245-deep.1','legacyRawToVia':'exact wire id','nettyToClientApply':'exact modern packet object identity',
        'viaToModernPacket':'vector + monotonic time + order candidate only; explicitly not causal proof',
        'postApplyContext':['tick start/end','move head/tail','camera return','velocity/position write','source jump/event stages'],
        'positiveY':'ordinal since observed ground contact / same localJump candidate; labels secondLiftCandidate and causalProof=false'
      },
      'newPerRecordFields':['capture','captureAgeNs','threadId','correlation annotations when applicable'],
      'limitations':['No server tick or server call-site causal id.','Via-output to modern packet identity remains candidate matching because retained hooks expose no shared identifier.',
                     'Camera sample precedes final bobbing/shader transforms.','Not all packet classes/direct field writes are covered.',
                     'Diagnostic synchronization/string parsing adds overhead and is not a performance build.','No live Minecraft/Forge server validation performed by the build environment.'],
      'sourceSha256':{f.relative_to(WORK).as_posix():sha(f.read_bytes()) for f in sources},'metadataPatchCounts':edits
    }
    new[PKG+'rev245-build.json']=(json.dumps(provenance,indent=2,ensure_ascii=False)+'\n').encode()
    output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,data in new.items():
            if name in infos:
                oi=infos[name]; info=zipfile.ZipInfo(name,oi.date_time);info.compress_type=zipfile.ZIP_DEFLATED
                info.comment=oi.comment;info.extra=oi.extra;info.internal_attr=oi.internal_attr;info.external_attr=oi.external_attr;info.create_system=oi.create_system
            else:
                info=zipfile.ZipInfo(name,(2026,10,3,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=(0o100644<<16)
            z.writestr(info,data,compress_type=zipfile.ZIP_DEFLATED,compresslevel=9)
    with zipfile.ZipFile(output) as z:
        if z.testzip() is not None or len(z.namelist())!=len(set(z.namelist())): raise SystemExit('output zip invalid')
        rebuilt={n:z.read(n) for n in z.namelist()}
    changed=sorted(n for n in old if old[n]!=rebuilt[n]);added=sorted(set(rebuilt)-set(old));removed=sorted(set(old)-set(rebuilt))
    expected_changed={n for n,data in compiled.items() if n in old and old[n]!=data}
    expected_changed.update(replacements);expected_changed.add('fabric.mod.json')
    if set(changed)!=expected_changed: raise SystemExit('unexpected changed entries: '+repr(sorted(set(changed)^expected_changed)))
    expected_added=(set(compiled)-set(old))|{PKG+'rev245-build.json'}
    if set(added)!=expected_added or removed: raise SystemExit(f'unexpected add/remove added={added} removed={removed}')
    evidence={**provenance,'artifact':output.name,'bytes':output.stat().st_size,'sha256':sha(output.read_bytes()),
              'baselineEntries':len(old),'outputEntries':len(rebuilt),'changedEntries':changed,'addedEntries':added,'removedEntries':removed,
              'allOtherEntriesContentPreserved':all(old[n]==rebuilt[n] for n in set(old)-set(changed)),'zipIntegrity':'pass'}
    output.with_suffix('.evidence.json').write_text(json.dumps(evidence,indent=2,ensure_ascii=False)+'\n')
    output.with_suffix('.sha256').write_text(evidence['sha256']+'  '+output.name+'\n')
    print(json.dumps({k:evidence[k] for k in ['artifact','bytes','sha256','changedEntries','addedEntries','allOtherEntriesContentPreserved']},indent=2))

if __name__=='__main__':
    if len(sys.argv)!=3: raise SystemExit('usage: build_rev245.py BASE.jar OUTPUT.jar')
    main(Path(sys.argv[1]),Path(sys.argv[2]))
