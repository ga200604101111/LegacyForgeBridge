"""Summarize real probe captures. No cross-JVM nanosecond subtraction or guessed causality.
Usage: python analyze_probe.py capture1.jsonl capture2.jsonl --output summary.json
"""
from __future__ import annotations
import argparse, collections, json
from pathlib import Path

def analyze(path: Path) -> dict:
 rows=[];issues=[]
 with path.open(encoding='utf-8-sig',errors='replace') as f:
  for line_no,line in enumerate(f,1):
   if not line.strip():continue
   try:rows.append(json.loads(line))
   except (ValueError,TypeError):issues.append(f'malformed line {line_no}')
 rows.sort(key=lambda r:(r.get('ns',0),r.get('seq',0)))
 counts=collections.Counter(r.get('stage','UNKNOWN') for r in rows)
 by_packet=collections.defaultdict(list)
 for r in rows:
  if 'packetKey' in r:by_packet[r['packetKey']].append(r)
 footer=next((r for r in reversed(rows) if r.get('stage')=='FOOTER'),None)
 if not footer:issues.append('No footer: process may still be running or shutdown was not clean.')
 elif not footer.get('completeForAnalysis'):issues.append('Footer flags capture limits, errors, drops, transformation failure, or no player match.')
 for r in rows:
  if r.get('stage')=='TRANSFORM_FAILED':issues.append(str(r.get('detail')))
  if r.get('stage')=='TRANSFORM' and 'hooks=[]' in r.get('detail',''):issues.append('Target loaded without matched hooks: '+r['detail'])
 if not any('player' in r for r in rows):issues.append('No matching player snapshots. Check configured player name and loaded hooks.')
 applies=[];calls=[];origins=[]
 for r in rows:
  stage=r.get('stage','')
  if stage=='SERVER_C03_JUMP_CALL_BEFORE':
   calls.append({'seq':r['seq'],'packetKey':r.get('packetKey'),'serverTick':r.get('serverTick'),'player':r.get('player'),'packet':r.get('packet'),'serverHandler':r.get('serverHandler'),'causalProof':'exact call-site'} )
  if stage.startswith('S12_CREATE'):
   origins.append({'seq':r['seq'],'packetKey':r.get('packetKey'),'packet':r.get('packet'),'stack':r.get('stack'), 'note':'constructors are not send count; group same packetKey within this file'})
  if stage=='CLIENT_S12_APPLY_HEAD':
   same=by_packet[r['packetKey']]
   received=[x for x in same if x.get('stage')=='NET_RECEIVE_HEAD' and x['ns']<=r['ns']]
   tails=[x for x in same if x.get('stage')=='CLIENT_S12_APPLY_RETURN' and x['ns']>=r['ns']]
   recv=received[-1] if received else None;tail=tails[0] if tails else None
   applies.append({'seq':r['seq'],'packetKey':r['packetKey'],'packet':r.get('packet'),'before':r.get('player'),'after':tail.get('player') if tail else None,
                   'clientTick':r.get('clientTick'),'clientPhase':r.get('clientPhase'),'receiveToApplyMs':(r['ns']-recv['ns'])/1e6 if recv else None,
                   'delayMeaning':'within this JVM only; not RTT or server latency'})
 return {'file':path.name,'records':len(rows),'stageCounts':dict(counts),'issues':issues,'footer':footer,
         'serverJumpCalls':calls,'s12Constructors':origins,'nativeS12Applications':applies,
         'crossCaptureRule':'packetKey and ns are local to each JVM/file. Match streams cautiously; identical S12 values alone do not establish server-to-client packet identity.'}

def main():
 p=argparse.ArgumentParser();p.add_argument('files',nargs='+',type=Path);p.add_argument('--output',type=Path);a=p.parse_args()
 result={'schema':1,'captures':[analyze(f) for f in a.files]};s=json.dumps(result,ensure_ascii=False,indent=2)
 if a.output:a.output.write_text(s,encoding='utf-8')
 else:print(s)
if __name__=='__main__':main()
