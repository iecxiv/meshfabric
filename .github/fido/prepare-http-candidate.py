from pathlib import Path
import subprocess

exchange='app/src/main/java/iecxiv/meshfabric/data/remote/FidoLoginExchange.kt'
gradle='app/build.gradle.kts'
for path,sha in [(exchange,'1365a097de1ea7a1891c5950853539e0cf147ff7'),(gradle,'376499acc959259e381f4a7f22c7cce8f182b7d5')]:
    assert subprocess.check_output(['git','hash-object',path],text=True).strip()==sha,'Source changed: '+path
copies={
'FidoLoginChallengePage.kt':'app/src/main/java/iecxiv/meshfabric/data/remote/FidoLoginChallengePage.kt',
'FidoLoginHttpTransport.kt':'app/src/main/java/iecxiv/meshfabric/data/remote/FidoLoginHttpTransport.kt',
'FidoLoginChallengePageTest.kt':'app/src/test/java/iecxiv/meshfabric/data/remote/FidoLoginChallengePageTest.kt',
'FidoLoginHttpTransportTest.kt':'app/src/test/java/iecxiv/meshfabric/data/remote/FidoLoginHttpTransportTest.kt'}
for dest in copies.values(): assert not Path(dest).exists(),'Candidate already exists: '+dest
extra='    @Synchronized fun get(current:FidoLoginBinding,url:String):Request? {\n        if(!valid(current) || phase !in setOf(Phase.PASSWORD_SENT,Phase.SUBMITTED)) return null\n        if(!policy.allowsNavigation(url)) { stop(if(phase==Phase.SUBMITTED) Phase.UNKNOWN else Phase.INVALID); return null }\n        val target=requireNotNull(url.toHttpUrlOrNull())\n        return Request.Builder().url(target).get().header("Cache-Control","no-store")\n            .apply { val value=header(target); if(value.isNotEmpty()) header("Cookie",value) }.build()\n    }\n    @Synchronized fun cookieHeaderForVerification(current:FidoLoginBinding):String? {\n        if(!valid(current) || phase!=Phase.SUBMITTED) return null\n        return header(base.newBuilder().addPathSegment("control.ashx").build())\n    }\n'
p=Path(exchange); text=p.read_text()
anchor='    @Synchronized fun poll(current:FidoLoginBinding):Phase'
assert text.count(anchor)==1
p.write_text(text.replace(anchor,extra+anchor))
p=Path(gradle); text=p.read_text(); anchor='    testImplementation(libs.junit)'
assert text.count(anchor)==1
p.write_text(text.replace(anchor,anchor+'\n    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")\n    testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")'))
for name,dest in copies.items(): Path(dest).write_bytes((Path('.github/fido')/name).read_bytes())
expected={exchange,gradle,*copies.values()}
subprocess.run(['git','add','--',*sorted(expected)],check=True)
subprocess.run(['git','diff','--cached','--check'],check=True)
assert set(subprocess.check_output(['git','diff','--cached','--name-only'],text=True).splitlines())==expected
out=Path('validation'); out.mkdir(exist_ok=True)
(out/'http-candidate.patch').write_bytes(subprocess.check_output(['git','diff','--cached','--binary']))
(out/'expected-paths.txt').write_text('\n'.join(sorted(expected))+'\n')
