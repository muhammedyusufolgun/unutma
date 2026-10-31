#!/usr/bin/env python3
"""Fail closed before building a Time-ikiz APK from incomplete Android sources."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/com/olgun/unutma"
required = ["LanTransfer.kt", "LanDiscovery.kt", "PairHandshake.kt", "InternetMessaging.kt", "MessageSyncWorker.kt", "Store.kt",
            "Alarms.kt", "MainActivity.kt", "ReminderWidget.kt", "SpeechService.kt"]
errors = []
for name in required:
    if not (src / name).is_file():
        errors.append(f"Missing Android source: {name}")
for xml in (root / "app/src/main").rglob("*.xml"):
    try:
        ET.parse(xml)
    except ET.ParseError as exc:
        errors.append(f"Invalid XML {xml.relative_to(root)}: {exc}")
def check(file, phrase, label):
    if not file.is_file() or phrase not in file.read_text(encoding="utf-8"):
        errors.append(label)
check(src/"Store.kt", "fun addIncomingAtomic(", "Atomic incoming persistence missing")
check(src/"InternetMessaging.kt", "verifyPeer(", "Sender signature verification missing")
check(src/"InternetMessaging.kt", "Store.addIncomingAtomic(", "Incoming persistence not integrated")
check(src/"MessageSyncWorker.kt", "InternetMessaging.sync(", "Worker not connected to sync")
check(root/"app/build.gradle.kts", "androidx.work:work-runtime-ktx", "WorkManager dependency missing")
check(root/"app/build.gradle.kts", "org.bouncycastle:bcprov", "Ed25519 dependency missing")
check(root/"app/src/main/AndroidManifest.xml", "android.permission.INTERNET", "Internet permission missing")
# Guard against canceling a scheduled alarm before an edited reminder is durable.
activity = (src / "MainActivity.kt").read_text(encoding="utf-8")
save_pos = activity.find("Store.save(this,items);Alarms.cancel(this,id);Alarms.plan(this,r);")
if save_pos < 0:
    errors.append("Reminder edit must persist before canceling/replanning its alarm")
check(src/"InternetMessaging.kt", "Inbox item $i failed; retry on next sync", "Inbox failure isolation missing")
check(src/"MainActivity.kt", "Sesli mesaj boş olamaz", "Empty reminder text validation missing")
check(src/"MainActivity.kt", "isLenient=false", "Strict reminder date/time parsing missing")
check(src/"MainActivity.kt", "Kaydet ve İlet", "New reminder share action missing")
check(root/"app/build.gradle.kts", 'applicationId = "com.olgun.timeikiz"', "Distinct Time-ikiz application ID missing")
if "android.permission.RECEIVE_SMS" in (root/"app/src/main/AndroidManifest.xml").read_text():
    errors.append("Sensitive SMS permission must not be present")
check(src/"LanTransfer.kt", "AES/GCM/NoPadding", "Missing authenticated LAN encryption")
check(src/"LanForegroundService.kt", "LanTransfer.start(applicationContext)", "LAN listener not connected")
check(src/"MainActivity.kt", "LanTransfer.send(this,r)", "LAN send not connected")
check(src/"LanDiscovery.kt", "discoverServices(", "NSD discovery missing")
check(src/"MainActivity.kt", "selectNearby(", "Contact-based nearby pairing missing")
check(src/"PairHandshake.kt", "fun approve(", "Recipient approval flow missing")
check(src/"PairHandshake.kt", "RSA/ECB/OAEPWithSHA-256AndMGF1Padding", "Encrypted pairing handshake missing")
check(src/"MainActivity.kt", "PairHandshake.request(", "Automatic pairing initiation missing")
check(src/"LanTransfer.kt", "fun confirmPair(", "Reciprocal LAN confirmation missing")
check(src/"PairHandshake.kt", "LanTransfer.confirmPair(", "Handshake does not confirm both devices")
check(src/"LanForegroundService.kt", "startForeground(", "LAN foreground service missing")
check(src/"MainActivity.kt", "startLanService()", "LAN foreground service not wired")
check(src/"LanForegroundService.kt", "LanTransfer.stop()", "LAN listener cleanup missing")
check(src/"LanForegroundService.kt", "LanDiscovery.stop(", "LAN discovery cleanup missing")
check(src/"LanForegroundService.kt", "fun enabled(", "LAN listener user preference missing")
check(src/"MainActivity.kt", "LanForegroundService.enabled(this)", "LAN listener preference ignored on app start")
check(src/"LanForegroundService.kt", "LanTransfer.stop()", "LAN shutdown missing")
if errors:
    for error in errors:
        print("FAIL:", error, file=sys.stderr)
    sys.exit(1)
print("PASS: Required sources, integrations, dependencies, and XML")
