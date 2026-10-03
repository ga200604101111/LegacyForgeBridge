#!/usr/bin/env python3
from pathlib import Path
import hashlib,json,struct,subprocess,sys,tempfile,zipfile,io

BASE_SHA='92d00685e9316946353bf410455e57f14a15fd4a903ce5eed749b4fafd658ae6'
OLD_VERSION='0.2.0-alpha.27-corpus4-local.28-rev245-deepdiag.1'
VERSION='0.2.0-alpha.27-corpus4-local.29-rev246-ui.1'
OLD_REVISION='2026-10-03.245-deep-motion-correlation-diagnostics'
REVISION='2026-10-03.246-desktop-status-support-window'
PKG='dev/yinghuang/legacyforgebridge/'
ROOT=Path(__file__).resolve().parent
SRC=ROOT/'source/dev/yinghuang/legacyforgebridge/desktop/DesktopProgressUi.java'
UI='dev/yinghuang/legacyforgebridge/desktop/DesktopProgressUi.class'
STATE='dev/yinghuang/legacyforgebridge/desktop/DesktopProgressUi$State.class'
HELPER='META-INF/lfb/desktop-helper.jar'

def sha(b):return hashlib.sha256(b).hexdigest()

def patch_utf8(data,replacements,label):
    if data[:4]!=b'\xca\xfe\xba\xbe':raise ValueError(label+' not class')
    cp=struct.unpack('>H',data[8:10])[0];pos=10;out=bytearray(data[:10]);i=1;counts={k:0 for k in replacements}
    while i<cp:
        tag=data[pos];pos+=1;out.append(tag)
        if tag==1:
            n=struct.unpack('>H',data[pos:pos+2])[0];pos+=2;raw=data[pos:pos+n];pos+=n
            try:s=raw.decode('utf-8')
            except UnicodeDecodeError:s=None
            if s is not None:
                for old,new in replacements.items():
                    if old in s:
                        counts[old]+=s.count(old);s=s.replace(old,new)
                raw=s.encode('utf-8')
            out+=struct.pack('>H',len(raw))+raw
        elif tag in (3,4):out+=data[pos:pos+4];pos+=4
        elif tag in (5,6):out+=data[pos:pos+8];pos+=8;i+=1
        elif tag in (7,8,16,19,20):out+=data[pos:pos+2];pos+=2
        elif tag in (9,10,11,12,17,18):out+=data[pos:pos+4];pos+=4
        elif tag==15:out+=data[pos:pos+3];pos+=3
        else:raise ValueError(f'{label} unknown cp tag {tag}')
        i+=1
    out+=data[pos:]
    missing=[k for k,v in counts.items() if v==0]
    if missing:raise ValueError(f'{label} missing patches {missing} counts={counts}')
    return bytes(out),counts

def rewrite_helper(data,new_ui,new_state):
    src=io.BytesIO(data)
    with zipfile.ZipFile(src) as z:
        if z.testzip() is not None:raise ValueError('helper corrupt')
        infos=z.infolist();old={i.filename:z.read(i.filename) for i in infos}
    if UI not in old or STATE not in old:raise ValueError('helper missing UI classes')
    new=dict(old);new[UI]=new_ui;new[STATE]=new_state
    out=io.BytesIO()
    with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for info in infos:
            ni=zipfile.ZipInfo(info.filename,info.date_time);ni.compress_type=zipfile.ZIP_DEFLATED
            ni.comment=info.comment;ni.extra=info.extra;ni.internal_attr=info.internal_attr;ni.external_attr=info.external_attr;ni.create_system=info.create_system
            z.writestr(ni,new[info.filename],compress_type=zipfile.ZIP_DEFLATED,compresslevel=9)
    with zipfile.ZipFile(io.BytesIO(out.getvalue())) as z:
        rebuilt={n:z.read(n) for n in z.namelist()}
    assert set(rebuilt)==set(old)
    assert all(rebuilt[n]==old[n] for n in old if n not in {UI,STATE})
    assert rebuilt[UI]==new_ui and rebuilt[STATE]==new_state
    return out.getvalue(),len(old)-2

def main(base,output):
    base=Path(base).resolve();output=Path(output).resolve()
    if base==output:raise SystemExit('refusing overwrite')
    if sha(base.read_bytes())!=BASE_SHA:raise SystemExit('base SHA mismatch')
    with tempfile.TemporaryDirectory(prefix='lfb-rev246-') as td:
        classes=Path(td)/'classes'
        p=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',classes,SRC],capture_output=True,text=True)
        if p.returncode:raise SystemExit(p.stdout+p.stderr)
        new_ui=(classes/UI).read_bytes();new_state=(classes/STATE).read_bytes()
        with zipfile.ZipFile(base) as z:
            if z.testzip() is not None or len(z.namelist())!=len(set(z.namelist())):raise SystemExit('base invalid')
            infos=z.infolist();old={i.filename:z.read(i.filename) for i in infos}
        new=dict(old)
        new[UI]=new_ui;new[STATE]=new_state
        helper,helper_preserved=rewrite_helper(old[HELPER],new_ui,new_state);new[HELPER]=helper
        patches={
          PKG+'BuildInfo.class':{OLD_VERSION:VERSION,OLD_REVISION:REVISION},
          PKG+'LegacyFileLogger.class':{'version='+OLD_VERSION:'version='+VERSION,'converterRevision='+OLD_REVISION:'converterRevision='+REVISION},
          PKG+'network/FmlConnectionTrace.class':{'version='+OLD_VERSION:'version='+VERSION},
          PKG+'behavior/LegacyMotionTraceLog.class':{OLD_VERSION:VERSION},
        }
        patch_counts={}
        for n,repl in patches.items():new[n],patch_counts[n]=patch_utf8(new[n],repl,n)
        mod=json.loads(new['fabric.mod.json'])
        if mod.get('version')!=OLD_VERSION:raise SystemExit('unexpected fabric version '+str(mod.get('version')))
        mod['version']=VERSION;new['fabric.mod.json']=(json.dumps(mod,indent=2,ensure_ascii=False)+'\n').encode()
        provenance={
          'schema':1,'checkpoint':'rev246-desktop-status-support-window','baseArtifact':base.name,'baseSha256':BASE_SHA,
          'version':VERSION,'converterRevision':REVISION,'motionDiagnosticSchema':'rev245-deep.1 unchanged',
          'buildMethod':'offline JDK21 compile DesktopProgressUi + guarded constant-pool metadata edits over exact rev245 full main JAR; same UI classes embedded into desktop-helper.jar',
          'githubActionsBuild':False,'networkDependencyDownloads':False,'fullGradleLoomBuild':False,
          'uiChanges':['details toggle restored to normal button styling','details label reflects expanded/collapsed state','supported-mods child dialog added inside details area','main window title is LegacyForgeBridge | [display state] | by YingHunag09'],
          'supportedModsNotice':'informational corpus/live-tested list, not production allow-list; generic source-driven conversion remains authoritative',
          'motionBehaviorChanged':False,'serverChanged':False,'originalModsChanged':False,
          'sourceSha256':sha(SRC.read_bytes()),'metadataPatchCounts':patch_counts,'helperUnchangedEntriesContentPreserved':helper_preserved
        }
        new[PKG+'rev246-build.json']=(json.dumps(provenance,indent=2,ensure_ascii=False)+'\n').encode()
        output.parent.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
            old_infos={i.filename:i for i in infos}
            order=[i.filename for i in infos]+[n for n in new if n not in old_infos]
            for name in order:
                if name in old_infos:
                    oi=old_infos[name];info=zipfile.ZipInfo(name,oi.date_time);info.compress_type=zipfile.ZIP_DEFLATED
                    info.comment=oi.comment;info.extra=oi.extra;info.internal_attr=oi.internal_attr;info.external_attr=oi.external_attr;info.create_system=oi.create_system
                else:
                    info=zipfile.ZipInfo(name,(2026,10,3,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=(0o100644<<16)
                z.writestr(info,new[name],compress_type=zipfile.ZIP_DEFLATED,compresslevel=9)
        with zipfile.ZipFile(output) as z:
            if z.testzip() is not None or len(z.namelist())!=len(set(z.namelist())):raise SystemExit('output invalid')
            rebuilt={n:z.read(n) for n in z.namelist()}
        changed=sorted(n for n in old if old[n]!=rebuilt[n]);added=sorted(set(rebuilt)-set(old));removed=sorted(set(old)-set(rebuilt))
        expected={UI,STATE,HELPER,'fabric.mod.json',*patches.keys()}
        if set(changed)!=expected:raise SystemExit('unexpected changed '+repr(sorted(set(changed)^set(expected))))
        if added!=[PKG+'rev246-build.json'] or removed:raise SystemExit(f'unexpected add/remove {added} {removed}')
        evidence={**provenance,'artifact':output.name,'bytes':output.stat().st_size,'sha256':sha(output.read_bytes()),
          'baselineEntries':len(old),'outputEntries':len(rebuilt),'changedEntries':changed,'addedEntries':added,'removedEntries':removed,
          'allOtherOuterEntriesContentPreserved':all(old[n]==rebuilt[n] for n in old if n not in expected),'helperUnchangedEntriesContentPreservedCount':helper_preserved,'zipIntegrity':'pass'}
        output.with_suffix('.evidence.json').write_text(json.dumps(evidence,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
        output.with_suffix('.sha256').write_text(evidence['sha256']+'  '+output.name+'\n')
        print(json.dumps({k:evidence[k] for k in ['artifact','bytes','sha256','changedEntries','addedEntries','allOtherOuterEntriesContentPreserved','helperUnchangedEntriesContentPreservedCount']},indent=2,ensure_ascii=False))

if __name__=='__main__':
    if len(sys.argv)!=3:raise SystemExit('usage: build_local.py BASE.jar OUTPUT.jar')
    main(sys.argv[1],sys.argv[2])
