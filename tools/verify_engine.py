#!/usr/bin/env python3
"""Exercise only the disposable fixture package. Verify original settings in finally."""
import json,subprocess,sys,re
serial=sys.argv[1]
pkg='com.halo.notice.fixture'
def shell(cmd,check=True):
 r=subprocess.run(['adb','-s',serial,'shell',cmd],capture_output=True,text=True)
 if check and r.returncode:raise RuntimeError(r.stderr+r.stdout)
 return r
uid=int(re.search(r'uid:(\d+)',shell('cmd package list packages -U '+pkg).stdout)[1])
def probe(*args,check=True):
 r=shell('CLASSPATH=/data/local/tmp/notice-probe.jar app_process /system/bin com.halo.notice.SystemProbe '+pkg+' '+str(uid)+' '+' '.join(map(str,args)),check)
 return json.loads(r.stdout) if r.returncode==0 else r
before=probe();original={x['id']:x for x in before};id='fixture_marketing';level=original[id]['importance']
try:
 assert probe(id,level,0)['verified']
 assert {x['id']:x for x in probe()}[id]['importance']==0
 stale=probe(id,level,2,check=False)
 assert isinstance(stale,subprocess.CompletedProcess) and stale.returncode!=0
 assert probe(id,0,2)['verified']
 assert {x['id']:x for x in probe()}[id]['importance']==2
 assert probe(id,2,level)['verified']
 after={x['id']:x for x in probe()}
 assert original==after, 'Full channel settings changed outside expected importance round-trip'
 print('PASS: close, readback, stale rejection, quiet, exact restore, unchanged other settings on '+serial)
finally:
 now={x['id']:x for x in probe()}
 if now[id]['importance']!=level:probe(id,now[id]['importance'],level)
