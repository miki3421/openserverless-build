#!/usr/bin/env python3
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.

"""Offline compatibility checks for an explicitly selected controller/invoker pair."""
import argparse, hashlib, json, subprocess
from pathlib import Path

JAVA = '''import java.security.*;
import javax.net.ssl.*;
import java.util.*;
public class RuntimeCheck {
 public static void main(String[] args) throws Exception {
  if (!System.getProperty("java.version").equals(args[0])) throw new Exception("version");
  if (MessageDigest.getInstance("SHA-256").digest("synthetic".getBytes("UTF-8")).length != 32) throw new Exception("digest");
  if (!Arrays.asList(SSLContext.getDefault().getSupportedSSLParameters().getProtocols()).contains("TLSv1.3")) throw new Exception("TLS");
  TrustManagerFactory f = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
  f.init((KeyStore)null); int count=0;
  for (TrustManager m:f.getTrustManagers()) if (m instanceof X509TrustManager) count+=((X509TrustManager)m).getAcceptedIssuers().length;
  if (count==0) throw new Exception("trust store");
  if (!java.time.ZoneId.of("Europe/Rome").getId().equals("Europe/Rome")) throw new Exception("timezone");
  System.out.println("PASS version,compiler,SHA256,TLS1.3,trust-store,timezone");
 }
}'''

def run(image, script, user='1001:1001'):
    return subprocess.check_output(['docker','run','--rm','--network=none','--read-only',
        '--tmpfs=/tmp:rw,nosuid,nodev,size=64m','--user='+user,'--entrypoint=sh','-i',image,'-s'],
        input=script.encode(),stderr=subprocess.STDOUT,timeout=90)

def verify(base, candidate, role):
    if role not in ('controller','invoker'): raise ValueError('Explicit component required')
    command='set -eu\ntest -d /'+role+'\nfind /'+role+' -type f -exec sha256sum {} \\; > /tmp/distribution.sha256\ntest -s /tmp/distribution.sha256\nsort /tmp/distribution.sha256\nsha256sum /init.sh /transformEnvironment.sh /copyJMXFiles.sh\n'
    original=run(base,command);updated=run(candidate,command)
    if original != updated: raise ValueError('Application distribution or launch scripts changed')
    for image,version in [(base,'11.0.17'),(candidate,'11.0.33')]:
        script="set -eu\ncat > /tmp/RuntimeCheck.java <<'JAVA_SOURCE'\n"+JAVA+"\nJAVA_SOURCE\njava /tmp/RuntimeCheck.java "+version+'\n'
        output=run(image,script)
        if not output.startswith(b'PASS '): raise ValueError('Java compatibility check failed')
    return {'component':role,'distributionAndLaunchersUnchanged':True,
        'distributionManifestSha256':hashlib.sha256(original).hexdigest(),
        'filesCompared':len(original.splitlines()),'baseJava':'11.0.17','candidateJava':'11.0.33',
        'checksPerImage':6,'user':'1001:1001','network':'none','rootFilesystem':'read-only'}

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--base',required=True);p.add_argument('--candidate',required=True);p.add_argument('--component',required=True,choices=['controller','invoker']);p.add_argument('--output',required=True,type=Path);a=p.parse_args()
    result=verify(a.base,a.candidate,a.component);a.output.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
