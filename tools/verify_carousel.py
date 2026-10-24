#!/usr/bin/env python3
"""Verify actual leftward gestures, indicators and each preset's review without applying."""
import subprocess,sys,xml.etree.ElementTree as E,pathlib
serial=sys.argv[1];out=pathlib.Path(sys.argv[2]);out.mkdir(parents=True,exist_ok=True)
base=['adb','-s',serial]
def adb(*args):return subprocess.run(base+list(args),check=True,capture_output=True)
def dump():
 adb('shell','uiautomator','dump','/sdcard/notice-carousel-check.xml')
 return E.fromstring(adb('shell','cat','/sdcard/notice-carousel-check.xml').stdout)
def screen(name): (out/name).write_bytes(adb('exec-out','screencap','-p').stdout)
adb('shell','am','start','-n','com.halo.notice/.MainActivity')
for i,expected in enumerate(['和营销推广\n说声再见','优惠促销\n少一点打扰','互动消息\n安静一点到达','热门推广\n不必响起']):
 if i:adb('shell','input','swipe','1040','1210','150','1210','400')
 root=dump();texts=[n.get('text') for n in root.iter('node')]
 assert expected in texts,(i,texts)
 assert any(n.get('content-desc')=='通知预设轮播，第 '+str(i+1)+' / 4 张' for n in root.iter('node'))
 screen('Notice-0.2.0-preset-'+str(i+1)+'.png')
 print('PASS left swipe:',i+1,expected.replace('\n',' '))
for i in range(3):adb('shell','input','swipe','150','1210','1040','1210','400');dump()
adb('shell','rm','-f','/sdcard/notice-carousel-check.xml')
