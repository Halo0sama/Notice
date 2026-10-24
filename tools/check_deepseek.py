#!/usr/bin/env python3
"""Read a single key from a DOCX in memory. Never log it or store it in an artifact."""
import json,re,sys,urllib.request,urllib.error,zipfile,xml.etree.ElementTree as E

def read_key(path):
    with zipfile.ZipFile(path) as z:
        root=E.fromstring(z.read('word/document.xml'))
    text=''.join(root.itertext())
    keys=re.findall(r'sk-[A-Za-z0-9_-]+',text)
    if len(set(keys))!=1: raise SystemExit('Expected exactly one API key; refusing to guess')
    return keys[0]

def main():
    key=read_key(sys.argv[1])
    tool={'type':'function','name':'lookup_channel','description':'Look up the marketing test channel','parameters':{'type':'object','properties':{},'additionalProperties':False}}
    wire=[{'role':'user','content':'用lookup_channel工具查询测试通道，然后用中文告诉我该通道的名称和开关状态。不要做任何修改。'}]
    def request(items,tool_choice):
        data=json.dumps({'model':'deepseek-flash','instructions':'你是通知助手，先调用工具再回答。','input':items,'tools':[tool],'tool_choice':tool_choice,'stream':True,'max_output_tokens':800,'reasoning':{'effort':'low'}}).encode()
        req=urllib.request.Request('https://api.deepseek.com/responses',data,{'Authorization':'Bearer '+key,'Content-Type':'application/json'})
        try:
            with urllib.request.urlopen(req,timeout=120) as res:
                events=[];terminal=None
                for line in res:
                    if line.startswith(b'data:'):
                        j=json.loads(line[5:].strip());events.append(j.get('type'))
                        if j.get('type')=='response.completed': terminal=j['response'];break
                if terminal is None: raise RuntimeError('No completed event')
                print(json.dumps({'event_count':len(events),'status':terminal.get('status'),'usage':terminal.get('usage')},ensure_ascii=False))
                return terminal
        except urllib.error.HTTPError as e:
            raise SystemExit('HTTP '+str(e.code)+' '+e.read().decode().replace(key,'[REDACTED]')[:1200])
    first=request(wire,'auto')
    calls=[x for x in first['output'] if x['type']=='function_call']
    assert calls, 'No tool call returned'
    wire.extend(first['output'])
    for c in calls: wire.append({'type':'function_call_output','call_id':c['call_id'],'output':json.dumps({'name':'营销推广','importance':4,'enabled':True},ensure_ascii=False)})
    second=request(wire,'auto')
    texts=[p.get('text','') for o in second['output'] for p in o.get('content',[]) if p.get('type')=='output_text']
    assert texts,'No final text'
    print('Responses SSE + tool round-trip passed:', ' '.join(texts)[:240])
if __name__=='__main__':main()
