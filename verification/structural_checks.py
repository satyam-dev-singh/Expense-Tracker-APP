#!/usr/bin/env python3
import os, re, sys, xml.etree.ElementTree as ET
ROOT=os.environ.get('LEFT_ROOT','/data/Left'); failures=[]; checked=0
def check(n,ok):
 global checked; checked+=1
 if not ok: failures.append(n); print('FAIL:',n)
required=['progress_so_far.txt','README.md','DEVELOPMENT.md','app/src/main/AndroidManifest.xml','app/src/main/java/com/left/app/LeftApplication.kt','app/src/main/java/com/left/app/core/domain/SubscriptionUseCases.kt','app/src/main/java/com/left/app/core/domain/NotificationPlans.kt','app/src/main/java/com/left/app/feature/subscriptions/SubscriptionsViewModel.kt','app/src/main/java/com/left/app/feature/subscriptions/SubscriptionsScreen.kt','app/src/main/java/com/left/app/feature/subscriptions/SubscriptionReminderWorker.kt','app/src/main/java/com/left/app/feature/subscriptions/SubscriptionReminderScheduler.kt','verification/SubscriptionHarness.java','verification/Phase7NotificationHarness.java']
for r in required: check('file exists: '+r, os.path.isfile(os.path.join(ROOT,r)))
for dp,_,fs in os.walk(ROOT):
 for f in fs:
  if f.endswith('.xml'):
   try: ET.parse(os.path.join(dp,f)); check('xml well-formed: '+f, True)
   except Exception: check('xml well-formed: '+f, False)
kt=0
for dp,_,fs in os.walk(ROOT):
 for f in fs:
  if f.endswith('.kt') or f.endswith('.kts'):
   kt+=1; s=open(os.path.join(dp,f)).read(); s=re.sub(r'""".*?"""','""',s,flags=re.S); s=re.sub(r'"(?:[^"\\]|\\.)*"','""',s); s=re.sub(r'//.*','',s); check('braces balanced: '+f, s.count('{')==s.count('}'))
print(); print(f'Structural checks run: {checked} ({kt} Kotlin files scanned)')
if failures: print('FAILURES:',len(failures)); sys.exit(1)
print('ALL STRUCTURAL CHECKS PASSED')
