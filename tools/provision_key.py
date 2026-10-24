#!/usr/bin/env python3
"""Provision a debug install over authorized ADB, then consume into Android Keystore.
The key is never placed in a command argument, a repo file, an APK or log.
"""
import subprocess,sys
from check_deepseek import read_key
serial,path=sys.argv[1:3]
key=read_key(path)
base=['adb','-s',serial]
subprocess.run(base+['shell','am','force-stop','com.halo.notice'],check=True,capture_output=True)
subprocess.run(base+['shell',"run-as com.halo.notice sh -c 'cat > files/key-import'"],input=key.encode(),check=True,capture_output=True)
subprocess.run(base+['shell','am','start','-n','com.halo.notice/.MainActivity'],check=True,capture_output=True)
print('Key provisioned to private app storage; launch consumes it into Keystore and deletes the import.')
