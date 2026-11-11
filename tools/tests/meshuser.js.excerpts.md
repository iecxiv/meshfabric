# package/meshuser.js

Original source SHA-256: 2683d9902631e4b0393a6391d4ae4ce0b6949dfeadd3384e5be7b9f09c36732b

## Lines 26-179
```text
26:     const common = parent.common;
27:     // Cross domain messages, for cross-domain administrators only.
28:     const allowedCrossDomainMessages = ['accountcreate', 'accountremove', 'accountchange', 'createusergroup', 'deleteusergroup', 'usergroupchange'];
29: 
30:     // User Consent Flags
31:     const USERCONSENT_DesktopNotifyUser = 1;
32:     const USERCONSENT_TerminalNotifyUser = 2;
33:     const USERCONSENT_FilesNotifyUser = 4;
34:     const USERCONSENT_DesktopPromptUser = 8;
35:     const USERCONSENT_TerminalPromptUser = 16;
36:     const USERCONSENT_FilesPromptUser = 32;
37:     const USERCONSENT_ShowConnectionToolbar = 64;
38: 
39:     // Mesh Rights
40:     const MESHRIGHT_EDITMESH            = 0x00000001; // 1
41:     const MESHRIGHT_MANAGEUSERS         = 0x00000002; // 2
42:     const MESHRIGHT_MANAGECOMPUTERS     = 0x00000004; // 4
43:     const MESHRIGHT_REMOTECONTROL       = 0x00000008; // 8
44:     const MESHRIGHT_AGENTCONSOLE        = 0x00000010; // 16
45:     const MESHRIGHT_SERVERFILES         = 0x00000020; // 32
46:     const MESHRIGHT_WAKEDEVICE          = 0x00000040; // 64
47:     const MESHRIGHT_SETNOTES            = 0x00000080; // 128
48:     const MESHRIGHT_REMOTEVIEWONLY      = 0x00000100; // 256
49:     const MESHRIGHT_NOTERMINAL          = 0x00000200; // 512
50:     const MESHRIGHT_NOFILES             = 0x00000400; // 1024
51:     const MESHRIGHT_NOAMT               = 0x00000800; // 2048
52:     const MESHRIGHT_DESKLIMITEDINPUT    = 0x00001000; // 4096
53:     const MESHRIGHT_LIMITEVENTS         = 0x00002000; // 8192
54:     const MESHRIGHT_CHATNOTIFY          = 0x00004000; // 16384
55:     const MESHRIGHT_UNINSTALL           = 0x00008000; // 32768
56:     const MESHRIGHT_NODESKTOP           = 0x00010000; // 65536
57:     const MESHRIGHT_REMOTECOMMAND       = 0x00020000; // 131072
58:     const MESHRIGHT_RESETOFF            = 0x00040000; // 262144
59:     const MESHRIGHT_GUESTSHARING        = 0x00080000; // 524288
60:     const MESHRIGHT_DEVICEDETAILS       = 0x00100000; // 1048576
61:     const MESHRIGHT_RELAY               = 0x00200000; // 2097152
62:     const MESHRIGHT_NOREGISTRY          = 0x00400000; // 4194304
63:     const MESHRIGHT_NOSOFTWARE          = 0x00800000; // 8388608
64:     const MESHRIGHT_ADMIN               = 0xFFFFFFFF;
65: 
66:     // Site rights
67:     const SITERIGHT_SERVERBACKUP        = 0x00000001; // 1
68:     const SITERIGHT_MANAGEUSERS         = 0x00000002; // 2
69:     const SITERIGHT_SERVERRESTORE       = 0x00000004; // 4
70:     const SITERIGHT_FILEACCESS          = 0x00000008; // 8
71:     const SITERIGHT_SERVERUPDATE        = 0x00000010; // 16
72:     const SITERIGHT_LOCKED              = 0x00000020; // 32
73:     const SITERIGHT_NONEWGROUPS         = 0x00000040; // 64
74:     const SITERIGHT_NOMESHCMD           = 0x00000080; // 128
75:     const SITERIGHT_USERGROUPS          = 0x00000100; // 256
76:     const SITERIGHT_RECORDINGS          = 0x00000200; // 512
77:     const SITERIGHT_LOCKSETTINGS        = 0x00000400; // 1024
78:     const SITERIGHT_ALLEVENTS           = 0x00000800; // 2048
79:     const SITERIGHT_NONEWDEVICES        = 0x00001000; // 4096
80:     const SITERIGHT_ADMIN               = 0xFFFFFFFF;
81: 
82:     // Protocol Numbers
83:     const PROTOCOL_TERMINAL             = 1;
84:     const PROTOCOL_DESKTOP              = 2;
85:     const PROTOCOL_FILES                = 5;
86:     const PROTOCOL_AMTWSMAN             = 100;
87:     const PROTOCOL_AMTREDIR             = 101;
88:     const PROTOCOL_MESSENGER            = 200;
89:     const PROTOCOL_WEBRDP               = 201;
90:     const PROTOCOL_WEBSSH               = 202;
91:     const PROTOCOL_WEBSFTP              = 203;
92:     const PROTOCOL_WEBVNC               = 204;
93: 
94:     // MeshCentral Satellite
95:     const SATELLITE_PRESENT = 1;     // This session is a MeshCentral Satellite session
96:     const SATELLITE_802_1x = 2;      // This session supports 802.1x profile checking and creation
97: 
98:     // Events
99:     /*
100:     var eventsMessageId = {
101:         1: "Account login",
102:         2: "Account logout",
103:         3: "Changed language from {1} to {2}",
104:         4: "Joined desktop multiplex session",
105:         5: "Left the desktop multiplex session",
106:         6: "Started desktop multiplex session",
107:         7: "Finished recording session, {0} second(s)",
108:         8: "Closed desktop multiplex session, {0} second(s)"
109:     };
110:     */
111: 
112:     var obj = {};
113:     obj.user = user;
114:     obj.domain = domain;
115:     obj.ws = ws;
116: 
117:     // Information related to the current page the user is looking at
118:     obj.deviceSkip = 0; // How many devices to skip
119:     obj.deviceLimit = 0; // How many devices to view
120:     obj.visibleDevices = null; // An object of visible nodeid's if the user is in paging mode
121:     obj.deviceFilter = null; // Device name filter applied by the server before paging, only used in paging mode
122:     if (domain.maxdeviceview != null) { obj.deviceLimit = domain.maxdeviceview; }
123: 
124:     // Check if we are a cross-domain administrator
125:     if (parent.parent.config.settings.managecrossdomain && (parent.parent.config.settings.managecrossdomain.indexOf(user._id) >= 0)) { obj.crossDomain = true; }
126: 
127:     // Server side Intel AMT stack
128:     const WsmanComm = require('./amt/amt-wsman-comm.js');
129:     const Wsman = require('./amt/amt-wsman.js');
130:     const Amt = require('./amt/amt.js');
131: 
132:     // If this session has an expire time, setup a timer now.
133:     if ((req.session != null) && (typeof req.session.expire == 'number')) {
134:         var delta = (req.session.expire - Date.now());
135:         if (delta <= 0) { req.session = {}; try { ws.close(); } catch (ex) { } return; } // Session is already expired, close now.
136:         obj.expireTimer = setTimeout(function () { for (var i in req.session) { delete req.session[i]; } obj.close(); }, delta);
137:     }
138: 
139:     // Send data through the websocket
140:     obj.send = function (object) { try { ws.send(JSON.stringify(object)); } catch(ex) {} }
141: 
142:     // Clean a IPv6 address that encodes a IPv4 address
143:     function cleanRemoteAddr(addr) { if (addr.startsWith('::ffff:')) { return addr.substring(7); } else { return addr; } }
144: 
145:     // Send a PING/PONG message
146:     function sendPing() { try { obj.ws.send('{"action":"ping"}'); } catch (ex) { } }
147:     function sendPong() { try { obj.ws.send('{"action":"pong"}'); } catch (ex) { } }
148: 
149:     // Setup the agent PING/PONG timers
150:     if ((typeof args.browserping == 'number') && (obj.pingtimer == null)) { obj.pingtimer = setInterval(sendPing, args.browserping * 1000); }
151:     else if ((typeof args.browserpong == 'number') && (obj.pongtimer == null)) { obj.pongtimer = setInterval(sendPong, args.browserpong * 1000); }
152: 
153:     // Disconnect this user
154:     obj.close = function (arg) {
155:         obj.ws.xclosed = 1; // This is for testing. Will be displayed when running "usersessions" server console command.
156: 
157:         if ((arg == 1) || (arg == null)) { try { obj.ws.close(); parent.parent.debug('user', 'Soft disconnect'); } catch (ex) { console.log(ex); } } // Soft close, close the websocket
158:         if (arg == 2) { try { obj.ws._socket._parent.end(); parent.parent.debug('user', 'Hard disconnect'); } catch (ex) { console.log(ex); } } // Hard close, close the TCP socket
159: 
160:         obj.ws.xclosed = 2; // DEBUG
161: 
162:         // Perform timer cleanup
163:         if (obj.pingtimer) { clearInterval(obj.pingtimer); delete obj.pingtimer; }
164:         if (obj.pongtimer) { clearInterval(obj.pongtimer); delete obj.pongtimer; }
165: 
166:         obj.ws.xclosed = 3; // DEBUG
167: 
168:         // Clear expire timeout
169:         if (obj.expireTimer != null) { clearTimeout(obj.expireTimer); delete obj.expireTimer; }
170: 
171:         obj.ws.xclosed = 4; // DEBUG
172: 
173:         // Perform cleanup
174:         parent.parent.RemoveAllEventDispatch(obj.ws);
175:         if (obj.serverStatsTimer != null) { clearInterval(obj.serverStatsTimer); delete obj.serverStatsTimer; }
176:         if (req.session && req.session.ws && req.session.ws == obj.ws) { delete req.session.ws; }
177:         if (parent.wssessions2[ws.sessionId]) { delete parent.wssessions2[ws.sessionId]; }
178: 
179:         obj.ws.xclosed = 5; // DEBUG
```

## Lines 431-550
```text
431:             ws.HandleEvent = function (source, event, ids, id) {
432:                 // If this session is logged in using a loginToken and the token is removed, disconnect.
433:                 if ((req.session.loginToken != null) && (typeof event == 'object') && (event.action == 'loginTokenChanged') && (event.removed != null) && (event.removed.indexOf(req.session.loginToken) >= 0)) { delete req.session; obj.close(); return; }
434: 
435:                 // If this user is not viewing all devices and paging, check if this event is in the current page
436:                 if (isEventWithinPage(ids) == false) return;
437: 
438:                 // Normally, only allow this user to receive messages from it's own domain.
439:                 // If the user is a cross domain administrator, allow some select messages from different domains.
440:                 if ((event.domain == null) || (event.domain == domain.id) || ((obj.crossDomain === true) && (allowedCrossDomainMessages.indexOf(event.action) >= 0))) {
441:                     try {
442:                         if (event == 'close') { try { delete req.session; } catch (ex) { } obj.close(); return; }
443:                         else if (event == 'resubscribe') { user.subscriptions = parent.subscribe(user._id, ws); }
444:                         else if (event == 'updatefiles') { updateUserFiles(user, ws, domain); }
445:                         else {
446:                             // If updating guest device shares, if we are updating a user that is not creator of the share, remove the URL.
447:                             if (((event.action == 'deviceShareUpdate') && (Array.isArray(event.deviceShares))) || ((event.action == 'changenode') && (event.node != null) && ((event.node.rdp != null) || (event.node.ssh != null)))) {
448:                                 event = common.Clone(event);
449:                                 if ((event.action == 'deviceShareUpdate') && (Array.isArray(event.deviceShares))) {
450:                                     for (var i in event.deviceShares) { if (event.deviceShares[i].userid != user._id) { delete event.deviceShares[i].url; } }
451:                                 }
452:                                 if ((event.action == 'changenode') && (event.node != null) && ((event.node.rdp != null) || (event.node.ssh != null))) {
453:                                     // Clean up RDP & SSH credentials
454:                                     if ((event.node.rdp != null) && (typeof event.node.rdp[user._id] == 'number')) { event.node.rdp = event.node.rdp[user._id]; } else { delete event.node.rdp; }
455:                                     if ((event.node.ssh != null) && (typeof event.node.ssh[user._id] == 'number')) { event.node.ssh = event.node.ssh[user._id]; } else { delete event.node.ssh; }
456:                                 }
457:                             }
458: 
459:                             // This is a MeshCentral Satellite message
460:                             if (event.action == 'satellite') { if ((obj.ws.satelliteFlags & event.satelliteFlags) != 0) { try { ws.send(JSON.stringify(event)); } catch (ex) { } return; } }
461: 
462:                             // Because of the device group "Show Self Events Only", we need to do more checks here.
463:                             if (id.startsWith('mesh/')) {
464:                                 // Check if we have rights to get this message. If we have limited events on this mesh, don't send the event to the user.
465:                                 var meshrights = parent.GetMeshRights(user, id);
466:                                 if ((meshrights === MESHRIGHT_ADMIN) || ((meshrights & MESHRIGHT_LIMITEVENTS) == 0) || (ids.indexOf(user._id) >= 0)) {
467:                                     // We have the device group rights to see this event or we are directly targetted by the event
468:                                     try { ws.send(JSON.stringify({ action: 'event', event: event })); } catch (ex) { }
469:                                 } else {
470:                                     // Check if no other users are targeted by the event, if not, we can get this event.
471:                                     var userTarget = false;
472:                                     for (var i in ids) { if (ids[i].startsWith('user/')) { userTarget = true; } }
473:                                     if (userTarget == false) { ws.send(JSON.stringify({ action: 'event', event: event })); }
474:                                 }
475:                             } else if (event.ugrpid != null) {
476:                                 if ((user.siteadmin & SITERIGHT_USERGROUPS) != 0) {
477:                                     // If we have the rights to see users in a group, send the group as is.
478:                                     try { ws.send(JSON.stringify({ action: 'event', event: event })); } catch (ex) { }
479:                                 } else {
480:                                     // We don't have the rights to see otehr users in the user group, remove the links that are not for ourselves.
481:                                     var links = {};
482:                                     if (event.links) { for (var i in event.links) { if ((i == user._id) || i.startsWith('mesh/') || i.startsWith('node/')) { links[i] = event.links[i]; } } }
483:                                     try { ws.send(JSON.stringify({ action: 'event', event: { ugrpid: event.ugrpid, domain: event.domain, time: event.time, name: event.name, action: event.action, username: event.username, links: links, h: event.h } })); } catch (ex) { }
484:                                 }
485:                             } else {
486:                                 // This is not a device group event, we can get this event.
487:                                 try { ws.send(JSON.stringify({ action: 'event', event: event })); } catch (ex) { }
488:                             }
489:                         }
490:                     } catch (ex) { console.log(ex); }
491:                 }
492:             };
493: 
494:             user.subscriptions = parent.subscribe(user._id, ws); // Subscribe to events
495:             try { ws._socket.setKeepAlive(true, 240000); } catch (ex) { } // Set TCP keep alive
496: 
497:             // Send current server statistics
498:             obj.SendServerStats = function () {
499:                 // Take a look at server stats
500:                 var os = require('os');
501:                 var stats = { action: 'serverstats', totalmem: os.totalmem(), freemem: os.freemem() };
502:                 try { stats.cpuavg = os.loadavg(); } catch (ex) { }
503:                 if (parent.parent.platform != 'win32') {
504:                     try { stats.availablemem = 1024 * Number(/MemAvailable:[ ]+(\d+)/.exec(fs.readFileSync('/proc/meminfo', 'utf8'))[1]); } catch (ex) { }
505:                 }
506: 
507:                 // Count the number of device groups that are not deleted
508:                 var activeDeviceGroups = 0;
509:                 for (var i in parent.meshes) { if (parent.meshes[i].deleted == null) { activeDeviceGroups++; } } // This is not ideal for performance, we want to dome something better.
510:                 var serverStats = {
511:                     UserAccounts: Object.keys(parent.users).length,
512:                     DeviceGroups: activeDeviceGroups,
513:                     AgentSessions: Object.keys(parent.wsagents).length,
514:                     ConnectedUsers: Object.keys(parent.wssessions).length,
515:                     UsersSessions: Object.keys(parent.wssessions2).length,
516:                     RelaySessions: parent.relaySessionCount,
517:                     RelayCount: Object.keys(parent.wsrelays).length,
518:                     ConnectedIntelAMT: 0
519:                 };
520:                 if (parent.relaySessionErrorCount != 0) { serverStats.RelayErrors = parent.relaySessionErrorCount; }
521:                 if (parent.parent.mpsserver != null) {
522:                     serverStats.ConnectedIntelAMTCira = 0;
523:                     for (var i in parent.parent.mpsserver.ciraConnections) { serverStats.ConnectedIntelAMTCira += parent.parent.mpsserver.ciraConnections[i].length; }
524:                 }
525:                 for (var i in parent.parent.connectivityByNode) {
526:                     const node = parent.parent.connectivityByNode[i];
527:                     if (node && typeof node.connectivity !== 'undefined' && node.connectivity === 4) { serverStats.ConnectedIntelAMT++; }
528:                 }
529: 
530:                 // Take a look at agent errors
531:                 var agentstats = parent.getAgentStats();
532:                 var errorCounters = {}, errorCountersCount = 0;
533:                 if (agentstats.meshDoesNotExistCount > 0) { errorCountersCount++; errorCounters.UnknownGroup = agentstats.meshDoesNotExistCount; }
534:                 if (agentstats.invalidPkcsSignatureCount > 0) { errorCountersCount++; errorCounters.InvalidPKCSsignature = agentstats.invalidPkcsSignatureCount; }
535:                 if (agentstats.invalidRsaSignatureCount > 0) { errorCountersCount++; errorCounters.InvalidRSAsignature = agentstats.invalidRsaSignatureCount; }
536:                 if (agentstats.invalidJsonCount > 0) { errorCountersCount++; errorCounters.InvalidJSON = agentstats.invalidJsonCount; }
537:                 if (agentstats.unknownAgentActionCount > 0) { errorCountersCount++; errorCounters.UnknownAction = agentstats.unknownAgentActionCount; }
538:                 if (agentstats.agentBadWebCertHashCount > 0) { errorCountersCount++; errorCounters.BadWebCertificate = agentstats.agentBadWebCertHashCount; }
539:                 if ((agentstats.agentBadSignature1Count + agentstats.agentBadSignature2Count) > 0) { errorCountersCount++; errorCounters.BadSignature = (agentstats.agentBadSignature1Count + agentstats.agentBadSignature2Count); }
540:                 if (agentstats.agentMaxSessionHoldCount > 0) { errorCountersCount++; errorCounters.MaxSessionsReached = agentstats.agentMaxSessionHoldCount; }
541:                 if ((agentstats.invalidDomainMeshCount + agentstats.invalidDomainMesh2Count) > 0) { errorCountersCount++; errorCounters.UnknownDeviceGroup = (agentstats.invalidDomainMeshCount + agentstats.invalidDomainMesh2Count); }
542:                 if ((agentstats.invalidMeshTypeCount + agentstats.invalidMeshType2Count) > 0) { errorCountersCount++; errorCounters.InvalidDeviceGroupType = (agentstats.invalidMeshTypeCount + agentstats.invalidMeshType2Count); }
543:                 //if (agentstats.duplicateAgentCount > 0) { errorCountersCount++; errorCounters.DuplicateAgent = agentstats.duplicateAgentCount; }
544: 
545:                 // Send out the stats
546:                 stats.values = { ServerState: serverStats }
547:                 if (errorCountersCount > 0) { stats.values.AgentErrorCounters = errorCounters; }
548:                 try { ws.send(JSON.stringify(stats)); } catch (ex) { }
549:             }
550: 
```

## Lines 607-723
```text
607:             }
608:             if (parent.parent.msgserver != null) { // Setup messaging providers information
609:                 serverinfo.userMsgProviders = parent.parent.msgserver.providers;
610:                 if (parent.parent.msgserver.discordUrl != null) { serverinfo.discordUrl = parent.parent.msgserver.discordUrl; }
611:             }
612:             if ((typeof parent.parent.config.messaging == 'object') && (typeof parent.parent.config.messaging.ntfy == 'object') && (typeof parent.parent.config.messaging.ntfy.userurl == 'string')) { // nfty user url
613:                 serverinfo.userMsgNftyUrl = parent.parent.config.messaging.ntfy.userurl;
614:             }
615: 
616:             // Build the mobile agent URL, this is used to connect mobile devices
617:             var agentServerName = parent.getWebServerName(domain, req);
618:             if (typeof parent.args.agentaliasdns == 'string') { agentServerName = parent.args.agentaliasdns; }
619:             var xdomain = (domain.dns == null) ? domain.id : '';
620:             var agentHttpsPort = ((parent.args.aliasport == null) ? parent.args.port : parent.args.aliasport); // Use HTTPS alias port is specified
621:             if (parent.args.agentport != null) { agentHttpsPort = parent.args.agentport; } // If an agent only port is enabled, use that.
622:             if (parent.args.agentaliasport != null) { agentHttpsPort = parent.args.agentaliasport; } // If an agent alias port is specified, use that.
623:             serverinfo.magenturl = 'mc://' + agentServerName + ((agentHttpsPort != 443) ? (':' + agentHttpsPort) : '') + ((xdomain != '') ? ('/' + xdomain) : '');
624:             serverinfo.domainsuffix = xdomain;
625: 
626:             if (domain.guestdevicesharing === false) { serverinfo.guestdevicesharing = false; } else {
627:                 if (typeof domain.guestdevicesharing == 'object') {
628:                     if (typeof domain.guestdevicesharing.maxsessiontime == 'number') { serverinfo.guestdevicesharingmaxtime = domain.guestdevicesharing.maxsessiontime; }
629:                 }
630:             }
631:             if (typeof domain.userconsentflags == 'number') { serverinfo.consent = domain.userconsentflags; }
632:             if ((typeof domain.usersessionidletimeout == 'number') && (domain.usersessionidletimeout > 0)) {serverinfo.timeout = (domain.usersessionidletimeout * 60 * 1000); }
633:             if (typeof domain.logoutonidlesessiontimeout == 'boolean') {
634:                 serverinfo.logoutonidlesessiontimeout = domain.logoutonidlesessiontimeout;
635:             } else {
636:                 // Default
637:                 serverinfo.logoutonidlesessiontimeout = true;
638:             }
639:             if (user.siteadmin === SITERIGHT_ADMIN) {
640:             if (parent.parent.config.settings.managealldevicegroups.indexOf(user._id) >= 0 || (user.links && Object.keys(user.links).some(key => parent.parent.config.settings.managealldevicegroups.indexOf(key) >= 0))) { serverinfo.manageAllDeviceGroups = true; }
641:                 if (obj.crossDomain === true) { serverinfo.crossDomain = []; for (var i in parent.parent.config.domains) { serverinfo.crossDomain.push(i); } }
642:                 if (typeof parent.webCertificateExpire[domain.id] == 'number') { serverinfo.certExpire = parent.webCertificateExpire[domain.id]; }
643:             }
644:             if (typeof domain.terminal == 'object') { // Settings used for remote terminal feature
645:                 if ((typeof domain.terminal.linuxshell == 'string') && (domain.terminal.linuxshell != 'any')) { serverinfo.linuxshell = domain.terminal.linuxshell; }
646:             }
647:             if (Array.isArray(domain.preconfiguredremoteinput)) { serverinfo.preConfiguredRemoteInput = domain.preconfiguredremoteinput; }
648:             if (Array.isArray(domain.preconfiguredscripts)) {
649:                 const r = [];
650:                 for (var i in domain.preconfiguredscripts) {
651:                     const types = ['', 'bat', 'ps1', 'sh', 'agent']; // 1 = Windows Command, 2 = Windows PowerShell, 3 = Linux, 4 = Agent
652:                     const script = domain.preconfiguredscripts[i];
653:                     if ((typeof script.name == 'string') && (script.name.length <= 32) && (typeof script.type == 'string') && ((typeof script.file == 'string') || (typeof script.cmd == 'string'))) {
654:                         const s = { name: script.name, type: types.indexOf(script.type.toLowerCase()) };
655:                         if (s.type > 0) { r.push(s); }
656:                     }
657:                 }
658:                 serverinfo.preConfiguredScripts = r;
659:             }
660:             
661:             if (domain.maxdeviceview != null) { serverinfo.maxdeviceview = domain.maxdeviceview; } // Maximum number of devices a user can view at any given time
662: 
663:             // Send server information
664:             try { ws.send(JSON.stringify({ action: 'serverinfo', serverinfo: serverinfo })); } catch (ex) { }
665: 
666:             // Send user information to web socket, this is the first thing we send
667:             try { ws.send(JSON.stringify({ action: 'userinfo', userinfo: parent.CloneSafeUser(parent.users[user._id]) })); } catch (ex) { }
668: 
669:             if (user.siteadmin === SITERIGHT_ADMIN) {
670:                 // Check if tracing is allowed for this domain
671:                 if ((domain.myserver !== false) && ((domain.myserver == null) || (domain.myserver.trace === true))) {
672:                     // Send server tracing information
673:                     try { ws.send(JSON.stringify({ action: 'traceinfo', traceSources: parent.parent.debugRemoteSources })); } catch (ex) { }
674:                 }
675: 
676:                 // Send any server warnings if any
677:                 var serverWarnings = parent.parent.getServerWarnings();
678:                 if (serverWarnings.length > 0) { try { ws.send(JSON.stringify({ action: 'serverwarnings', warnings: serverWarnings })); } catch (ex) { } }
679:             }
680: 
681:             // See how many times bad login attempts where made since the last login
682:             const lastLoginTime = parent.users[user._id].pastlogin;
683:             if (lastLoginTime != null) {
684:                 db.GetFailedLoginCount(user._id, user.domain, new Date(lastLoginTime * 1000), function (count) {
685:                     if (count > 0) { try { ws.send(JSON.stringify({ action: 'msg', type: 'notify', title: "Security Warning", tag: 'ServerNotify', id: Math.random(), value: "There has been " + count + " failed login attempts on this account since the last login.", titleid: 3, msgid: 12, args: [count] })); } catch (ex) { } delete user.pastlogin; }
686:                 });
687:             }
688: 
689:             // If we are site administrator and Google Drive backup is setup, send out the status.
690:             if ((user.siteadmin === SITERIGHT_ADMIN) && (domain.id == '') && (typeof parent.parent.config.settings.autobackup == 'object') && (typeof parent.parent.config.settings.autobackup.googledrive == 'object')) {
691:                 db.Get('GoogleDriveBackup', function (err, docs) {
692:                     if (err != null) return;
693:                     if (docs.length == 0) { try { ws.send(JSON.stringify({ action: 'serverBackup', service: 'googleDrive', state: 1 })); } catch (ex) { } }
694:                     else { try { ws.send(JSON.stringify({ action: 'serverBackup', service: 'googleDrive', state: docs[0].state })); } catch (ex) { } }
695:                 });
696:             }
697: 
698:             // We are all set, start receiving data
699:             ws._socket.resume();
700:             if (parent.parent.pluginHandler != null) parent.parent.pluginHandler.callHook('hook_userLoggedIn', user);
701:         });
702:     } catch (ex) { console.log(ex); }
703: 
704:     // Process incoming web socket data from the browser
705:     function processWebSocketData(msg) {
706:         var command, i = 0, mesh = null, meshid = null, nodeid = null, meshlinks = null, change = 0;
707:         try { command = JSON.parse(msg.toString('utf8')); } catch (e) { return; }
708:         if (command == null) return;
709:         if (common.validateString(command.action, 3, 32) == false) return; // Action must be a string between 3 and 32 chars
710: 
711:         var commandHandler = serverCommands[command.action];
712:         if (commandHandler != null) { 
713:             try { commandHandler(command); return; }
714:             catch (e) {
715:                 console.log('Unhandled error while processing ' + command.action + ' for user ' + user.name + ':\n' + e);
716:                 parent.parent.logError(e.stack); return; // todo: remove returns when switch is gone
717:             }
718:         } else { }
719:             // console.log('Unknown action from user ' + user.name + ': ' + command.action + '.');
720:             // pass through to switch statement until refactoring complete
721: 
722:         switch (command.action) {
723:             case 'nodes':
```

## Lines 1301-1417
```text
1301:                         if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'deleteuser', responseid: command.responseid, result: err })); } catch (ex) { } }
1302:                         break;
1303:                     }
1304: 
1305:                     // Remove all links to this user
1306:                     if (deluser.links != null) {
1307:                         for (var i in deluser.links) {
1308:                             if (i.startsWith('mesh/')) {
1309:                                 // Get the device group
1310:                                 mesh = parent.meshes[i];
1311:                                 if (mesh) {
1312:                                     // Remove user from the mesh
1313:                                     if (mesh.links[deluser._id] != null) { delete mesh.links[deluser._id]; parent.db.Set(mesh); }
1314: 
1315:                                     // Notify mesh change
1316:                                     change = 'Removed user ' + deluser.name + ' from device group ' + mesh.name;
1317:                                     var event = { etype: 'mesh', userid: user._id, username: user.name, meshid: mesh._id, name: mesh.name, mtype: mesh.mtype, desc: mesh.desc, action: 'meshchange', links: mesh.links, msgid: 72, msgArgs: [deluser.name, mesh.name], msg: change, domain: deluserdomain.id, invite: mesh.invite };
1318:                                     if(user._id === req.session.userid && req.session.tokenUser != null && req.session.tokenName != null) {
1319:                                         event.tokenName = req.session.tokenName; event.tokenUser = req.session.tokenUser;
1320:                                     }
1321:                                     if (db.changeStream) { event.noact = 1; } // If DB change stream is active, don't use this event to change the mesh. Another event will come.
1322:                                     parent.parent.DispatchEvent(parent.CreateMeshDispatchTargets(mesh, [deluser._id, user._id]), obj, event);
1323:                                 }
1324:                             } else if (i.startsWith('node/')) {
1325:                                 // Get the node and the rights for this node
1326:                                 parent.GetNodeWithRights(deluserdomain, deluser, i, function (node, rights, visible) {
1327:                                     if ((node == null) || (node.links == null) || (node.links[deluser._id] == null)) return;
1328: 
1329:                                     // Remove the link and save the node to the database
1330:                                     delete node.links[deluser._id];
1331:                                     if (Object.keys(node.links).length == 0) { delete node.links; }
1332:                                     db.Set(parent.cleanDevice(node));
1333: 
1334:                                     // Event the node change
1335:                                     var event;
1336:                                     if (command.rights == 0) {
1337:                                         event = { etype: 'node', userid: user._id, username: user.name, action: 'changenode', nodeid: node._id, domain: deluserdomain.id, msgid: 60, msgArgs: [node.name], msg: 'Removed user device rights for ' + node.name, node: parent.CloneSafeNode(node) }
1338:                                     } else {
1339:                                         event = { etype: 'node', userid: user._id, username: user.name, action: 'changenode', nodeid: node._id, domain: deluserdomain.id, msgid: 61, msgArgs: [node.name], msg: 'Changed user device rights for ' + node.name, node: parent.CloneSafeNode(node) }
1340:                                     }
1341:                                     if(user._id === req.session.userid && req.session.tokenUser != null && req.session.tokenName != null) {
1342:                                         event.tokenName = req.session.tokenName; event.tokenUser = req.session.tokenUser;
1343:                                     }
1344:                                     if (db.changeStream) { event.noact = 1; } // If DB change stream is active, don't use this event to change the mesh. Another event will come.
1345:                                     parent.parent.DispatchEvent(parent.CreateNodeDispatchTargets(node.meshid, node._id), obj, event);
1346:                                 });
1347:                             } else if (i.startsWith('ugrp/')) {
1348:                                 // Get the device group
1349:                                 var ugroup = parent.userGroups[i];
1350:                                 if (ugroup) {
1351:                                     // Remove user from the user group
1352:                                     if (ugroup.links[deluser._id] != null) { delete ugroup.links[deluser._id]; parent.db.Set(ugroup); }
1353: 
1354:                                     // Notify user group change
1355:                                     change = 'Removed user ' + deluser.name + ' from user group ' + ugroup.name;
1356:                                     var event = { etype: 'ugrp', userid: user._id, username: user.name, ugrpid: ugroup._id, name: ugroup.name, desc: ugroup.desc, action: 'usergroupchange', links: ugroup.links, msgid: 62, msgArgs: [deluser.name, ugroup.name], msg: 'Removed user ' + deluser.name + ' from user group ' + ugroup.name, addUserDomain: deluserdomain.id };
1357:                                     if(user._id === req.session.userid && req.session.tokenUser != null && req.session.tokenName != null) {
1358:                                         event.tokenName = req.session.tokenName; event.tokenUser = req.session.tokenUser;
1359:                                     }
1360:                                     if (db.changeStream) { event.noact = 1; } // If DB change stream is active, don't use this event to change the user group. Another event will come.
1361:                                     parent.parent.DispatchEvent(['*', ugroup._id, user._id, deluser._id], obj, event);
1362:                                 }
1363:                             }
1364:                         }
1365:                     }
1366: 
1367:                     db.Remove('ws' + deluser._id);  // Remove user web state
1368:                     db.Remove('nt' + deluser._id);  // Remove notes for this user
1369:                     db.Remove('ntp' + deluser._id); // Remove personal notes for this user
1370:                     db.Remove('im' + deluser._id);  // Remove image for this user
1371: 
1372:                     // Delete any login tokens
1373:                     parent.parent.db.GetAllTypeNodeFiltered(['logintoken-' + deluser._id], domain.id, 'logintoken', null, function (err, docs) {
1374:                         if ((err == null) && (docs != null)) { for (var i = 0; i < docs.length; i++) { parent.parent.db.Remove(docs[i]._id, function () { }); } }
1375:                     });
1376: 
1377:                     // Delete all files on the server for this account
1378:                     try {
1379:                         var deluserpath = parent.getServerRootFilePath(deluser);
1380:                         if (deluserpath != null) { parent.deleteFolderRec(deluserpath); }
1381:                     } catch (e) { }
1382: 
1383:                     db.Remove(deluserid);
1384:                     delete parent.users[deluserid];
1385: 
1386:                     var targets = ['*', 'server-users'];
1387:                     if (deluser.groups) { for (var i in deluser.groups) { targets.push('server-users:' + i); } }
1388:                     parent.parent.DispatchEvent(targets, obj, { etype: 'user', userid: deluserid, username: deluser.name, action: 'accountremove', msgid: 63, msg: 'Account removed', domain: deluserdomain.id });
1389:                     parent.parent.DispatchEvent([deluserid], obj, 'close');
1390: 
1391:                     if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'deleteuser', responseid: command.responseid, result: 'ok' })); } catch (ex) { } }
1392: 
1393:                     // Log in the auth log
1394:                     if (parent.parent.authlog) { parent.parent.authLog('https', 'User ' + user.name + ' deleted user account ' + deluser.name); }
1395: 
1396:                     break;
1397:                 }
1398:             case 'userbroadcast':
1399:                 {
1400:                     var err = null;
1401:                     try {
1402:                         // Broadcast a message to all currently connected users.
1403:                         if ((user.siteadmin & 2) == 0) { err = "Permission denied"; }
1404:                         else if (common.validateString(command.msg, 1, 512) == false) { err = "Message is too long"; } // Notification message is between 1 and 256 characters
1405:                     } catch (ex) { err = "Validation exception: " + ex; }
1406: 
1407:                     // Handle any errors
1408:                     if (err != null) {
1409:                         if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'userbroadcast', responseid: command.responseid, result: err })); } catch (ex) { } }
1410:                         break;
1411:                     }
1412: 
1413:                     // Create the notification message
1414:                     var notification = { action: 'msg', type: 'notify', domain: domain.id, value: command.msg, title: user.name, icon: 0, tag: 'broadcast', id: Math.random() };
1415:                     if ((typeof command.maxtime == 'number') && (command.maxtime > 0)) { notification.maxtime = command.maxtime; }
1416: 
1417:                     // Send the notification on all user sessions for this server
```

## Lines 1686-1802
```text
1686:                                             var event = { etype: 'user', userid: user._id, username: user.name, account: parent.CloneSafeUser(xuser), action: 'accountchange', msgid: 67, msgArgs: [xuser.name], msg: 'User group membership changed: ' + xuser.name, domain: ugrpdomain.id };
1687:                                             if(user._id === req.session.userid && req.session.tokenUser != null && req.session.tokenName != null) {
1688:                                                 event.tokenName = req.session.tokenName; event.tokenUser = req.session.tokenUser;
1689:                                             }
1690:                                             if (db.changeStream) { event.noact = 1; } // If DB change stream is active, don't use this event to change the user. Another event will come.
1691:                                             //parent.parent.DispatchEvent(targets, obj, event);
1692:                                             pendingDispatchEvents.push([targets, obj, event]);
1693:                                         }
1694:                                     } else if (i.startsWith('mesh/')) {
1695:                                         var xmesh = parent.meshes[i];
1696:                                         if (xmesh && xmesh.links) {
1697:                                             ugrp.links[i] = { rights: cgroup.links[i].rights };
1698:                                             xmesh.links[ugrpid] = { rights: cgroup.links[i].rights };
1699:                                             db.Set(xmesh);
1700: 
1701:                                             // Notify mesh change
1702:                                             var event = { etype: 'mesh', userid: user._id, username: user.name, meshid: xmesh._id, name: xmesh.name, mtype: xmesh.mtype, desc: xmesh.desc, action: 'meshchange', links: xmesh.links, msgid: 68, msgArgs: [ugrp.name, xmesh.name], msg: 'Added user group ' + ugrp.name + ' to device group ' + xmesh.name, domain: ugrpdomain.id, invite: xmesh.invite };
1703:                                             if(user._id === req.session.userid && req.session.tokenUser != null && req.session.tokenName != null) {
1704:                                                 event.tokenName = req.session.tokenName; event.tokenUser = req.session.tokenUser;
1705:                                             }
1706:                                             if (db.changeStream) { event.noact = 1; } // If DB change stream is active, don't use this event to change the mesh. Another event will come.
1707:                                             //parent.parent.DispatchEvent(['*', xmesh._id, user._id], obj, event);
1708:                                             pendingDispatchEvents.push([parent.CreateMeshDispatchTargets(xmesh, [user._id]), obj, event]);
1709:                                         }
1710:                                     }
1711:                                 }
1712:                             }
1713:                         }
1714: 
1715:                         // Save the new group
1716:                         db.Set(ugrp);
1717:                         if (db.changeStream == false) { parent.userGroups[ugrpid] = ugrp; }
1718: 
1719:                         // Event the user group creation
1720:                         var event = { etype: 'ugrp', userid: user._id, username: user.name, ugrpid: ugrpid, name: ugrp.name, desc: ugrp.desc, action: 'createusergroup', links: ugrp.links, msgid: 69, msgArgv: [ugrp.name], msg: 'User group created: ' + ugrp.name, ugrpdomain: domain.id };
1721:                         if(user._id === req.session.userid && req.session.tokenUser != null && req.session.tokenName != null) {
1722:                             event.tokenName = req.session.tokenName; event.tokenUser = req.session.tokenUser;
1723:                         }
1724:                         parent.parent.DispatchEvent(['*', ugrpid, user._id], obj, event); // Even if DB change stream is active, this event must be acted upon.
1725: 
1726:                         // Event any pending events, these must be sent out after the group creation event is dispatched.
1727:                         for (var i in pendingDispatchEvents) { var ev = pendingDispatchEvents[i]; parent.parent.DispatchEvent(ev[0], ev[1], ev[2]); }
1728: 
1729:                         // Log in the auth log
1730:                         if (parent.parent.authlog) { parent.parent.authLog('https', 'User ' + user.name + ' created user group ' + ugrp.name); }
1731: 
1732:                         try { ws.send(JSON.stringify({ action: 'createusergroup', responseid: command.responseid, result: 'ok', ugrpid: ugrpid, links: ugrp.links })); } catch (ex) { }
1733:                     });
1734:                     break;
1735:                 }
1736:             case 'deleteusergroup':
1737:                 {
1738:                     var err = null;
1739: 
1740:                     if ((user.siteadmin & SITERIGHT_USERGROUPS) == 0) { err = "Permission denied"; }
1741: 
1742:                     // Change the name or description of a user group
1743:                     else if (common.validateString(command.ugrpid, 1, 1024) == false) { err = "Invalid group id"; } // Check the user group id
1744:                     else {
1745:                         var ugroupidsplit = command.ugrpid.split('/');
1746:                         if ((ugroupidsplit.length != 3) || (ugroupidsplit[0] != 'ugrp') || ((obj.crossDomain !== true) && (ugroupidsplit[1] != domain.id))) { err = "Invalid domain id"; }
1747:                     }
1748: 
1749:                     // Get the domain
1750:                     var delGroupDomain;
1751:                     if (ugroupidsplit != null) {
1752:                         delGroupDomain = parent.parent.config.domains[ugroupidsplit[1]];
1753:                         if (delGroupDomain == null) { err = "Invalid domain id"; }
1754:                     }
1755: 
1756:                     // Handle any errors
1757:                     if (err != null) {
1758:                         if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'deleteusergroup', responseid: command.responseid, result: err })); } catch (ex) { } }
1759:                         break;
1760:                     }
1761: 
1762:                     db.Get(command.ugrpid, function (err, groups) {
1763:                         if ((err != null) || (groups.length != 1)) {
1764:                             try { ws.send(JSON.stringify({ action: 'deleteusergroup', responseid: command.responseid, result: 'Unknown device group' })); } catch (ex) { }
1765:                             return;
1766:                         }
1767:                         var group = groups[0];
1768: 
1769:                         // If this user group is an externally managed user group, it can't be deleted unless there are no users in it.
1770:                         if (group.membershipType != null) {
1771:                             var userCount = 0;
1772:                             if (group.links != null) { for (var i in group.links) { if (i.startsWith('user/')) { userCount++; } } }
1773:                             if (userCount > 0) return;
1774:                         }
1775: 
1776:                         // Unlink any user and meshes that have a link to this group
1777:                         if (group.links) {
1778:                             for (var i in group.links) {
1779:                                 if (i.startsWith('user/')) {
1780:                                     var xuser = parent.users[i];
1781:                                     if ((xuser != null) && (xuser.links != null)) {
1782:                                         delete xuser.links[group._id];
1783:                                         db.SetUser(xuser);
1784:                                         parent.parent.DispatchEvent([xuser._id], obj, 'resubscribe');
1785: 
1786:                                         // Notify user change
1787:                                         var targets = ['*', 'server-users', user._id, xuser._id];
1788:                                         var event = { etype: 'user', userid: user._id, username: user.name, account: parent.CloneSafeUser(xuser), action: 'accountchange', msgid: 67, msgArgs: [xuser.name], msg: 'User group membership changed: ' + xuser.name, delGroupDomain: domain.id };
1789:                                         if(user._id === req.session.userid && req.session.tokenUser != null && req.session.tokenName != null) {
1790:                                             event.tokenName = req.session.tokenName; event.tokenUser = req.session.tokenUser;
1791:                                         }
1792:                                         if (db.changeStream) { event.noact = 1; } // If DB change stream is active, don't use this event to change the user. Another event will come.
1793:                                         parent.parent.DispatchEvent(targets, obj, event);
1794:                                     }
1795:                                 } else if (i.startsWith('mesh/')) {
1796:                                     var xmesh = parent.meshes[i];
1797:                                     if (xmesh && xmesh.links) {
1798:                                         delete xmesh.links[group._id];
1799:                                         db.Set(xmesh);
1800: 
1801:                                         // Notify mesh change
1802:                                         var event = { etype: 'mesh', userid: user._id, username: user.name, meshid: xmesh._id, name: xmesh.name, mtype: xmesh.mtype, desc: xmesh.desc, action: 'meshchange', links: xmesh.links, msgid: 70, msgArgs: [group.name, xmesh.name], msg: 'Removed user group ' + group.name + ' from device group ' + xmesh.name, domain: delGroupDomain.id };
```

## Lines 2387-2625
```text
2387:                                 delete xgroup.links[mesh._id];
2388:                                 db.Set(xgroup);
2389: 
2390:                                 // Notify user group change
2391:                                 var targets = ['*', 'server-ugroups', user._id, xgroup._id];
2392:                                 var event = { etype: 'ugrp', userid: user._id, username: user.name, ugrpid: xgroup._id, name: xgroup.name, desc: xgroup.desc, action: 'usergroupchange', links: xgroup.links, msgid: 79, msgArgs: [xgroup.name], msg: 'User group changed: ' + xgroup.name, domain: domain.id };
2393:                                 if(user._id === req.session.userid && req.session.tokenUser != null && req.session.tokenName != null) {
2394:                                     event.tokenName = req.session.tokenName; event.tokenUser = req.session.tokenUser;
2395:                                 }
2396:                                 if (db.changeStream) { event.noact = 1; } // If DB change stream is active, don't use this event to change the user. Another event will come.
2397:                                 parent.parent.DispatchEvent(targets, obj, event);
2398:                             }
2399:                         }
2400:                     }
2401: 
2402:                     // Delete any invitation codes
2403:                     delete mesh.invite;
2404: 
2405:                     // Delete all files on the server for this mesh
2406:                     try {
2407:                         var meshpath = parent.getServerRootFilePath(mesh);
2408:                         if (meshpath != null) { parent.deleteFolderRec(meshpath); }
2409:                     } catch (e) { }
2410: 
2411:                     parent.parent.RemoveEventDispatchId(command.meshid); // Remove all subscriptions to this mesh
2412: 
2413:                     // Notify the devices that they have changed relay roles
2414:                     if (mesh.relayid != null) {
2415:                         // Get the node and the rights for this node
2416:                         parent.GetNodeWithRights(domain, user, mesh.relayid, function (node, rights, visible) {
2417:                             if (node == null) return;
2418:                             var event = { etype: 'node', userid: user._id, username: user.name, action: 'changenode', nodeid: node._id, domain: domain.id, msg: 'No longer a relay for ' + mesh.name + '.', msgid: 152, msgArgs: [mesh.name], node: parent.CloneSafeNode(node) };
2419:                             if(user._id === req.session.userid && req.session.tokenUser != null && req.session.tokenName != null) {
2420:                                 event.tokenName = req.session.tokenName; event.tokenUser = req.session.tokenUser;
2421:                             }
2422:                             if (db.changeStream) { event.noact = 1; } // If DB change stream is active, don't use this event to change the node. Another event will come.
2423:                             parent.parent.DispatchEvent(parent.CreateNodeDispatchTargets(node.meshid, node._id, [user._id]), obj, event);
2424:                         });
2425:                     }
2426: 
2427:                     // Mark the mesh as deleted
2428:                     mesh.deleted = new Date(); // Mark the time this mesh was deleted, we can expire it at some point.
2429:                     db.Set(mesh); // We don't really delete meshes because if a device connects to is again, we will un-delete it.
2430: 
2431:                     // Delete all devices attached to this mesh in the database
2432:                     db.RemoveMeshDocuments(command.meshid);
2433:                     // TODO: We are possibly deleting devices that users will have links to. We need to clean up the broken links from on occasion.
2434: 
2435:                     // Log in the auth log
2436:                     if (parent.parent.authlog) { parent.parent.authLog('https', 'User ' + user.name + ' deleted device group ' + mesh.name); }
2437: 
2438:                     if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'deletemesh', responseid: command.responseid, result: 'ok' })); } catch (ex) { } }
2439:                     break;
2440:                 }
2441:             case 'editmesh':
2442:                 {
2443:                     // Change the name or description of a device group (mesh)
2444:                     var err = null;
2445: 
2446:                     // Resolve the device group name if needed
2447:                     if ((typeof command.meshidname == 'string') && (command.meshid == null)) {
2448:                         for (var i in parent.meshes) {
2449:                             var m = parent.meshes[i];
2450:                             if ((m.mtype == 2) && (m.name == command.meshidname) && parent.IsMeshViewable(user, m)) {
2451:                                 if (command.meshid == null) { command.meshid = m._id; } else { err = 'Duplicate device groups found'; }
2452:                             }
2453:                         }
2454:                     }
2455: 
2456:                     // Validate input
2457:                     try {
2458:                         if (common.validateString(command.meshid, 8, 134) == false) { err = 'Invalid group identifier'; } // Check the meshid
2459:                         else if (command.meshid.indexOf('/') == -1) { command.meshid = 'mesh/' + domain.id + '/' + command.meshid; }
2460:                         if (err == null) {
2461:                             mesh = parent.meshes[command.meshid];
2462:                             if (mesh == null) { err = 'Invalid group identifier '; }
2463:                         }
2464:                     } catch (ex) { err = 'Validation exception: ' + ex; }
2465: 
2466:                     // Handle any errors
2467:                     if (err != null) { if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'editmesh', responseid: command.responseid, result: err })); } catch (ex) { } } break; }
2468: 
2469:                     change = '';
2470: 
2471:                     // Check if this user has rights to do this
2472:                     if ((parent.GetMeshRights(user, mesh) & MESHRIGHT_EDITMESH) == 0) return;
2473:                     if ((command.meshid.split('/').length != 3) || (command.meshid.split('/')[1] != domain.id)) return; // Invalid domain, operation only valid for current domain
2474: 
2475:                     var changesids = [];
2476:                     if ((common.validateString(command.meshname, 1, 128) == true) && (command.meshname != mesh.name)) { change = 'Device group name changed from "' + mesh.name + '" to "' + command.meshname + '"'; changesids.push(1); mesh.name = command.meshname; }
2477:                     if ((common.validateString(command.desc, 0, 1024) == true) && (command.desc != mesh.desc)) { if (change != '') change += ' and description changed'; else change += 'Device group "' + mesh.name + '" description changed'; changesids.push(2); mesh.desc = command.desc; }
2478:                     if ((common.validateInt(command.flags) == true) && (command.flags != mesh.flags)) { if (change != '') change += ' and flags changed'; else change += 'Device group "' + mesh.name + '" flags changed'; changesids.push(3); mesh.flags = command.flags; }
2479:                     if ((common.validateInt(command.consent) == true) && (command.consent != mesh.consent)) { if (change != '') change += ' and consent changed'; else change += 'Device group "' + mesh.name + '" consent changed'; changesids.push(4); mesh.consent = command.consent; }
2480:                     if ((common.validateInt(command.expireDevs, 0, 2000) == true) && (command.expireDevs != mesh.expireDevs)) { if (change != '') change += ' and auto-remove changed'; else change += 'Device group "' + mesh.name + '" auto-remove changed'; changesids.push(5); if (command.expireDevs == 0) { delete mesh.expireDevs; } else { mesh.expireDevs = command.expireDevs; } }
2481: 
2482:                     var oldRelayNodeId = null, newRelayNodeId = null;
2483:                     if ((typeof command.relayid == 'string') && ((mesh.mtype == 3) || (mesh.mtype == 4)) && (mesh.relayid != null) && (command.relayid != mesh.relayid)) {
2484:                         var relayIdSplit = command.relayid.split('/');
2485:                         if ((relayIdSplit.length == 3) && (relayIdSplit[0] = 'node') && (relayIdSplit[1] == domain.id)) {
2486:                             if (change != '') { change += ' and device relay changed'; } else { change = 'Device relay changed'; }
2487:                             changesids.push(7);
2488:                             oldRelayNodeId = mesh.relayid;
2489:                             newRelayNodeId = mesh.relayid = command.relayid;
2490:                         }
2491:                     }
2492: 
2493:                     // See if we need to change device group invitation codes
2494:                     if (mesh.mtype == 2) {
2495:                         if (command.invite === '*') {
2496:                             // Clear invite codes
2497:                             if (mesh.invite != null) { delete mesh.invite; }
2498:                             if (change != '') { change += ' and invite code changed'; } else { change += 'Device group "' + mesh.name + '" invite code changed'; }
2499:                             changesids.push(6);
2500:                         } else if ((command.invite != null) && (typeof command.invite == 'object') && (Array.isArray(command.invite.codes)) && (typeof command.invite.flags == 'number')) {
2501:                             // Set invite codes
2502:                             if ((mesh.invite == null) || (mesh.invite.codes != command.invite.codes) || (mesh.invite.flags != command.invite.flags)) {
2503:                                 // Check if an invite code is not already in use.
2504:                                 var dup = null;
2505:                                 for (var i in command.invite.codes) {
2506:                                     for (var j in parent.meshes) {
2507:                                         if ((j != command.meshid) && (parent.meshes[j].deleted == null) && (parent.meshes[j].domain == domain.id) && (parent.meshes[j].invite != null) && (parent.meshes[j].invite.codes.indexOf(command.invite.codes[i]) >= 0)) { dup = command.invite.codes[i]; break; }
2508:                                     }
2509:                                 }
2510:                                 if (dup != null) {
2511:                                     // A duplicate was found, don't allow this change.
2512:                                     displayNotificationMessage("Error, invite code \"" + dup + "\" already in use.", "Invite Codes", null, 6, 22, [dup]);
2513:                                     return;
2514:                                 }
2515:                                 mesh.invite = { codes: command.invite.codes, flags: command.invite.flags };
2516:                                 if (typeof command.invite.ag == 'number') { mesh.invite.ag = command.invite.ag; }
2517:                                 if (change != '') { change += ' and invite code changed'; } else { change += 'Device group "' + mesh.name + '" invite code changed'; }
2518:                                 changesids.push(6);
2519:                             }
2520:                         }
2521:                     }
2522: 
2523:                     if (change != '') {
2524:                         db.Set(mesh);
2525:                         var event = { etype: 'mesh', userid: user._id, username: user.name, meshid: mesh._id, name: mesh.name, mtype: mesh.mtype, desc: mesh.desc, flags: mesh.flags, consent: mesh.consent, action: 'meshchange', links: mesh.links, msgid: 142, msgArgs: [mesh.name, changesids], msg: change, domain: domain.id, invite: mesh.invite, expireDevs: command.expireDevs, relayid: mesh.relayid };
2526:                         if(user._id === req.session.userid && req.session.tokenUser != null && req.session.tokenName != null) {
2527:                             event.tokenName = req.session.tokenName; event.tokenUser = req.session.tokenUser;
2528:                         }
2529:                         if (db.changeStream) { event.noact = 1; } // If DB change stream is active, don't use this event to change the mesh. Another event will come.
2530:                         parent.parent.DispatchEvent(parent.CreateMeshDispatchTargets(mesh, [user._id, 'server-editmesh']), obj, event);
2531:                     }
2532: 
2533:                     // Notify the devices that they have changed relay roles
2534:                     if (oldRelayNodeId != null) {
2535:                         // Get the node and the rights for this node
2536:                         parent.GetNodeWithRights(domain, user, oldRelayNodeId, function (node, rights, visible) {
2537:                             if (node == null) return;
2538:                             var event = { etype: 'node', userid: user._id, username: user.name, action: 'changenode', nodeid: node._id, domain: domain.id, msg: 'No longer a relay for ' + mesh.name + '.', msgid: 152, msgArgs: [mesh.name], node: parent.CloneSafeNode(node) };
2539:                             if(user._id === req.session.userid && req.session.tokenUser != null && req.session.tokenName != null) {
2540:                                 event.tokenName = req.session.tokenName; event.tokenUser = req.session.tokenUser;
2541:                             }
2542:                             if (db.changeStream) { event.noact = 1; } // If DB change stream is active, don't use this event to change the node. Another event will come.
2543:                             parent.parent.DispatchEvent(parent.CreateNodeDispatchTargets(node.meshid, node._id, [user._id]), obj, event);
2544:                         });
2545:                     }
2546:                     if (newRelayNodeId != null) {
2547:                         // Get the node and the rights for this node
2548:                         parent.GetNodeWithRights(domain, user, newRelayNodeId, function (node, rights, visible) {
2549:                             if (node == null) return;
2550:                             var event = { etype: 'node', userid: user._id, username: user.name, action: 'changenode', nodeid: node._id, domain: domain.id, msg: 'Is a relay for ' + mesh.name + '.', msgid: 153, msgArgs: [mesh.name], node: parent.CloneSafeNode(node) };
2551:                             if(user._id === req.session.userid && req.session.tokenUser != null && req.session.tokenName != null) {
2552:                                 event.tokenName = req.session.tokenName; event.tokenUser = req.session.tokenUser;
2553:                             }
2554:                             if (db.changeStream) { event.noact = 1; } // If DB change stream is active, don't use this event to change the node. Another event will come.
2555:                             parent.parent.DispatchEvent(parent.CreateNodeDispatchTargets(node.meshid, node._id, [user._id]), obj, event);
2556:                         });
2557:                     } else if ((mesh.relayid != null) && (changesids.indexOf(1) >= 0)) {
2558:                         // Notify of node name change, get the node and the rights for this node, we just want to trigger a device update.
2559:                         parent.GetNodeWithRights(domain, user, mesh.relayid, function (node, rights, visible) {
2560:                             if (node == null) return;
2561:                             var event = { etype: 'node', userid: user._id, username: user.name, action: 'changenode', nodeid: node._id, domain: domain.id, node: parent.CloneSafeNode(node), nolog: 1 };
2562:                             if(user._id === req.session.userid && req.session.tokenUser != null && req.session.tokenName != null) {
2563:                                 event.tokenName = req.session.tokenName; event.tokenUser = req.session.tokenUser;
2564:                             }
2565:                             if (db.changeStream) { event.noact = 1; } // If DB change stream is active, don't use this event to change the node. Another event will come.
2566:                             parent.parent.DispatchEvent(parent.CreateNodeDispatchTargets(node.meshid, node._id, [user._id]), obj, event);
2567:                         });
2568:                     }
2569: 
2570:                     if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'editmesh', responseid: command.responseid, result: 'ok' })); } catch (ex) { } }
2571:                     break;
2572:                 }
2573:             case 'removemeshuser':
2574:                 {
2575:                     var xdomain, err = null;
2576: 
2577:                     // Resolve the device group name if needed
2578:                     if ((typeof command.meshname == 'string') && (command.meshid == null)) {
2579:                         for (var i in parent.meshes) {
2580:                             var m = parent.meshes[i];
2581:                             if ((m.mtype == 2) && (m.name == command.meshname) && parent.IsMeshViewable(user, m)) {
2582:                                 if (command.meshid == null) { command.meshid = m._id; } else { err = 'Duplicate device groups found'; }
2583:                             }
2584:                         }
2585:                     }
2586: 
2587:                     try {
2588:                         if (common.validateString(command.userid, 1, 1024) == false) { err = "Invalid userid"; } // Check userid
2589:                         if (common.validateString(command.meshid, 8, 134) == false) { err = "Invalid groupid"; } // Check meshid
2590:                         if (command.userid.indexOf('/') == -1) { command.userid = 'user/' + domain.id + '/' + command.userid; }
2591:                         if (command.userid == obj.user._id) { err = "Can't remove self"; } // Can't add of modify self
2592:                         if ((command.userid.split('/').length != 3) || ((obj.crossDomain !== true) && (command.userid.split('/')[1] != domain.id))) { err = "Invalid userid"; } // Invalid domain, operation only valid for current domain
2593:                         else {
2594:                             if (command.meshid.indexOf('/') == -1) { command.meshid = 'mesh/' + domain.id + '/' + command.meshid; }
2595:                             mesh = parent.meshes[command.meshid];
2596:                             var meshIdSplit = command.meshid.split('/');
2597:                             if (mesh == null) { err = "Unknown device group"; }
2598:                             else if ((parent.GetMeshRights(user, mesh) & MESHRIGHT_MANAGEUSERS) == 0) { err = "Permission denied"; }
2599:                             else if (meshIdSplit.length != 3) { err = "Invalid domain"; } // Invalid domain, operation only valid for current domain
2600:                             else {
2601:                                 xdomain = domain;
2602:                                 if (obj.crossDomain !== true) { xdomain = parent.parent.config.domains[meshIdSplit[1]]; }
2603:                                 if (xdomain == null) { err = "Invalid domain"; }
2604:                             }
2605:                         }
2606:                     } catch (ex) { err = "Validation exception: " + ex; }
2607: 
2608:                     // Handle any errors
2609:                     if (err != null) {
2610:                         console.log(err);
2611:                         if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'removemeshuser', responseid: command.responseid, result: err })); } catch (ex) { } }
2612:                         break;
2613:                     }
2614: 
2615:                     // Check if the user exists - Just in case we need to delete a mesh right for a non-existant user, we do it this way. Technically, it's not possible, but just in case.
2616:                     var deluserid = command.userid, deluser = null;
2617:                     if (deluserid.startsWith('user/')) { deluser = parent.users[deluserid]; }
2618:                     else if (deluserid.startsWith('ugrp/')) { deluser = parent.userGroups[deluserid]; }
2619: 
2620:                     // Search for a user name in that windows domain is the username starts with *\
2621:                     if ((deluser == null) && (deluserid.startsWith('user/' + xdomain.id + '/*\\')) == true) {
2622:                         var search = deluserid.split('/')[2].substring(1);
2623:                         for (var i in parent.users) { if (i.endsWith(search) && (parent.users[i].domain == xdomain.id)) { deluser = parent.users[i]; command.userid = deluserid = deluser._id; break; } }
2624:                     }
2625: 
```

## Lines 2650-3012
```text
2650:                                     event.tokenName = req.session.tokenName; event.tokenUser = req.session.tokenUser;
2651:                                 }
2652:                                 if (db.changeStream) { event.noact = 1; } // If DB change stream is active, don't use this event to change the user. Another event will come.
2653:                                 parent.parent.DispatchEvent(targets, obj, event);
2654:                             }
2655:                         }
2656:                     }
2657: 
2658:                     // Remove user from the mesh
2659:                     if (mesh.links[command.userid] != null) {
2660:                         delete mesh.links[command.userid];
2661:                         db.Set(mesh);
2662: 
2663:                         // Notify mesh change
2664:                         var event;
2665:                         if (deluser != null) {
2666:                             event = { etype: 'mesh', username: user.name, userid: deluser.name, meshid: mesh._id, name: mesh.name, mtype: mesh.mtype, desc: mesh.desc, action: 'meshchange', links: mesh.links, msgid: 83, msgArgs: [deluser.name, mesh.name], msg: 'Removed user ' + deluser.name + ' from device group ' + mesh.name, domain: xdomain.id, invite: mesh.invite };
2667:                         } else {
2668:                             event = { etype: 'mesh', username: user.name, userid: (deluserid.split('/')[2]), meshid: mesh._id, name: mesh.name, mtype: mesh.mtype, desc: mesh.desc, action: 'meshchange', links: mesh.links, msgid: 83, msgArgs: [(deluserid.split('/')[2]), mesh.name], msg: 'Removed user ' + (deluserid.split('/')[2]) + ' from device group ' + mesh.name, domain: xdomain.id, invite: mesh.invite };
2669:                         }
2670:                         if(user._id === req.session.userid && req.session.tokenUser != null && req.session.tokenName != null) {
2671:                             event.tokenName = req.session.tokenName; event.tokenUser = req.session.tokenUser;
2672:                         }
2673:                         parent.parent.DispatchEvent(parent.CreateMeshDispatchTargets(mesh, [user._id, command.userid]), obj, event);
2674:                         if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'removemeshuser', responseid: command.responseid, result: 'ok' })); } catch (ex) { } }
2675:                     } else {
2676:                         if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'removemeshuser', responseid: command.responseid, result: 'User not in group' })); } catch (ex) { } }
2677:                     }
2678:                     break;
2679:                 }
2680:             case 'meshamtpolicy':
2681:                 {
2682:                     // Change a mesh Intel AMT policy
2683:                     if (common.validateString(command.meshid, 8, 134) == false) break; // Check the meshid
2684:                     if (common.validateObject(command.amtpolicy) == false) break; // Check the amtpolicy
2685:                     if (common.validateInt(command.amtpolicy.type, 0, 4) == false) break; // Check the amtpolicy.type
2686:                     if (command.amtpolicy.type === 2) {
2687:                         if ((command.amtpolicy.password != null) && (common.validateString(command.amtpolicy.password, 0, 32) == false)) break; // Check the amtpolicy.password
2688:                         if ((command.amtpolicy.badpass != null) && common.validateInt(command.amtpolicy.badpass, 0, 1) == false) break; // Check the amtpolicy.badpass
2689:                         if (common.validateInt(command.amtpolicy.cirasetup, 0, 2) == false) break; // Check the amtpolicy.cirasetup
2690:                     } else if (command.amtpolicy.type === 3) {
2691:                         if ((command.amtpolicy.password != null) && (common.validateString(command.amtpolicy.password, 0, 32) == false)) break; // Check the amtpolicy.password
2692:                         if ((command.amtpolicy.badpass != null) && common.validateInt(command.amtpolicy.badpass, 0, 1) == false) break; // Check the amtpolicy.badpass
2693:                         if ((command.amtpolicy.ccm != null) && common.validateInt(command.amtpolicy.ccm, 0, 2) == false) break; // Check the amtpolicy.ccm
2694:                         if (common.validateInt(command.amtpolicy.cirasetup, 0, 2) == false) break; // Check the amtpolicy.cirasetup
2695:                     }
2696: 
2697:                     mesh = parent.meshes[command.meshid];
2698:                     if (mesh) {
2699:                         // Check if this user has rights to do this
2700:                         if ((parent.GetMeshRights(user, mesh) & MESHRIGHT_EDITMESH) == 0) return;
2701:                         if ((command.meshid.split('/').length != 3) || (command.meshid.split('/')[1] != domain.id)) return; // Invalid domain, operation only valid for current domain
2702: 
2703:                         // TODO: Check if this is a change from the existing policy
2704: 
2705:                         // Perform the Intel AMT policy change
2706:                         var amtpolicy = { type: command.amtpolicy.type };
2707:                         if ((command.amtpolicy.type === 2) || (command.amtpolicy.type === 3)) {
2708:                             amtpolicy = { type: command.amtpolicy.type, badpass: command.amtpolicy.badpass, cirasetup: command.amtpolicy.cirasetup };
2709:                             if (command.amtpolicy.type === 3) { amtpolicy.ccm = command.amtpolicy.ccm; }
2710:                             if ((command.amtpolicy.password == null) && (mesh.amt != null) && (typeof mesh.amt.password == 'string')) { amtpolicy.password = mesh.amt.password; } // Keep the last password
2711:                             if ((typeof command.amtpolicy.password == 'string') && (command.amtpolicy.password.length >= 8)) { amtpolicy.password = command.amtpolicy.password; } // Set a new password
2712:                         }
2713:                         mesh.amt = amtpolicy;
2714:                         db.Set(mesh);
2715:                         var amtpolicy2 = Object.assign({}, amtpolicy); // Shallow clone
2716:                         if (amtpolicy2.password != null) { amtpolicy2.password = 1; }
2717:                         var event = { etype: 'mesh', userid: user._id, username: user.name, meshid: mesh._id, amt: amtpolicy2, action: 'meshchange', links: mesh.links, msgid: 141, msg: "Intel(r) AMT policy change", domain: domain.id, invite: mesh.invite };
2718:                         if(user._id === req.session.userid && req.session.tokenUser != null && req.session.tokenName != null) {
2719:                             event.tokenName = req.session.tokenName; event.tokenUser = req.session.tokenUser;
2720:                         }
2721:                         if (db.changeStream) { event.noact = 1; } // If DB change stream is active, don't use this event to change the mesh. Another event will come.
2722:                         parent.parent.DispatchEvent(parent.CreateMeshDispatchTargets(mesh, [user._id]), obj, event);
2723: 
2724:                         // If we have peer servers, inform them of the new Intel AMT policy for this device group
2725:                         if (parent.parent.multiServer != null) { parent.parent.multiServer.DispatchMessage({ action: 'newIntelAmtPolicy', meshid: command.meshid, amtpolicy: amtpolicy }); }
2726: 
2727:                         // See if any agents for the affected device group is connected, if so, update the Intel AMT policy
2728:                         for (var nodeid in parent.wsagents) {
2729:                             const agent = parent.wsagents[nodeid];
2730:                             if (agent.dbMeshKey == command.meshid) { agent.sendUpdatedIntelAmtPolicy(amtpolicy); }
2731:                         }
2732:                     }
2733:                     break;
2734:                 }
2735:             case 'addlocaldevice':
2736:                 {
2737:                     var err = null;
2738:                     // Perform input validation
2739:                     try {
2740:                         if (common.validateString(command.meshid, 8, 134) == false) { err = "Invalid device group id"; } // Check meshid
2741:                         if (common.validateString(command.devicename, 1, 256) == false) { err = "Invalid devicename"; } // Check device name
2742:                         if (common.validateString(command.hostname, 1, 256) == false) { err = "Invalid hostname"; } // Check hostname
2743:                         if (typeof command.type != 'number') { err = "Invalid type"; } // Type must be a number
2744:                         if ((command.type != 4) && (command.type != 6) && (command.type != 29)) { err = "Invalid type"; } // Check device type
2745:                         else {
2746:                             if (command.meshid.indexOf('/') == -1) { command.meshid = 'mesh/' + domain.id + '/' + command.meshid; }
2747:                             mesh = parent.meshes[command.meshid];
2748:                             if (mesh == null) { err = "Unknown device group"; }
2749:                             if (mesh.mtype != 3) { err = "Local device agentless mesh only allowed" } // This operation is only allowed for mesh type 3, local device agentless mesh.
2750:                             else if ((parent.GetMeshRights(user, mesh) & MESHRIGHT_MANAGECOMPUTERS) == 0) { err = "Permission denied"; }
2751:                             else if ((command.meshid.split('/').length != 3) || (command.meshid.split('/')[1] != domain.id)) { err = "Invalid domain"; } // Invalid domain, operation only valid for current domain
2752:                         }
2753:                     } catch (ex) { console.log(ex); err = "Validation exception: " + ex; }
2754:                     // Handle any errors
2755:                     if (err != null) {
2756:                         if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'changeDeviceMesh', responseid: command.responseid, result: err })); } catch (ex) { } }
2757:                         break;
2758:                     }
2759: 
2760:                     // Create a new nodeid
2761:                     parent.crypto.randomBytes(48, function (err, buf) {
2762:                         // Create the new node
2763:                         nodeid = 'node/' + domain.id + '/' + buf.toString('base64').replace(/\+/g, '@').replace(/\//g, '$');
2764:                         var device = { type: 'node', _id: nodeid, meshid: command.meshid, mtype: 3, icon: 1, name: command.devicename, host: command.hostname, domain: domain.id, agent: { id: command.type, caps: 0 } };
2765:                         db.Set(device);
2766: 
2767:                         // Event the new node
2768:                         parent.parent.DispatchEvent(parent.CreateMeshDispatchTargets(command.meshid, [nodeid]), obj, { etype: 'node', userid: user._id, username: user.name, action: 'addnode', node: parent.CloneSafeNode(device), msgid: 84, msgArgs: [command.devicename, mesh.name], msg: 'Added device ' + command.devicename + ' to device group ' + mesh.name, domain: domain.id });
2769:                         // Send response if required
2770:                         if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'addlocaldevice', responseid: command.responseid, result: 'ok' })); } catch (ex) { } }
2771:                     });
2772:                     break;
2773:                 }
2774:             case 'addamtdevice':
2775:                 {
2776:                     if (args.wanonly == true) return; // This is a WAN-only server, local Intel AMT computers can't be added
2777:                     var err = null;
2778:                     // Perform input validation
2779:                     try {
2780:                         if (common.validateString(command.meshid, 8, 134) == false) { err = "Invalid device group id"; } // Check meshid
2781:                         if (common.validateString(command.devicename, 1, 256) == false) { err = "Invalid devicename"; } // Check device name
2782:                         if (common.validateString(command.hostname, 1, 256) == false) { err = "Invalid hostname"; } // Check hostname
2783:                         if (common.validateString(command.amtusername, 0, 16) == false) { err = "Invalid amtusername"; } // Check username
2784:                         if (common.validateString(command.amtpassword, 0, 16) == false) { err = "Invalid amtpassword"; } // Check password
2785:                         if (command.amttls == '0') { command.amttls = 0; } else if (command.amttls == '1') { command.amttls = 1; } // Check TLS flag
2786:                         if ((command.amttls != 1) && (command.amttls != 0)) { err = "Invalid amttls"; }
2787:                         else {
2788:                             if (command.meshid.indexOf('/') == -1) { command.meshid = 'mesh/' + domain.id + '/' + command.meshid; }
2789:                             // Get the mesh
2790:                             mesh = parent.meshes[command.meshid];
2791:                             if (mesh == null) { err = "Unknown device group"; }
2792:                             if (mesh.mtype != 1) { err = "Intel AMT agentless mesh only allowed"; } // This operation is only allowed for mesh type 1, Intel AMT agentless mesh.
2793:                             // Check if this user has rights to do this
2794:                             else if ((parent.GetMeshRights(user, mesh) & MESHRIGHT_MANAGECOMPUTERS) == 0) { err = "Permission denied"; }
2795:                             else if ((command.meshid.split('/').length != 3) || (command.meshid.split('/')[1] != domain.id)) { err = "Invalid domain"; } // Invalid domain, operation only valid for current domain
2796:                         }
2797:                     } catch (ex) { console.log(ex); err = "Validation exception: " + ex; }
2798: 
2799:                     // Handle any errors
2800:                     if (err != null) {
2801:                         if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'changeDeviceMesh', responseid: command.responseid, result: err })); } catch (ex) { } }
2802:                         break;
2803:                     }
2804: 
2805:                     // If we are in WAN-only mode, hostname is not used
2806:                     if ((args.wanonly == true) && (command.hostname)) { delete command.hostname; }
2807: 
2808:                     // Create a new nodeid
2809:                     parent.crypto.randomBytes(48, function (err, buf) {
2810:                         // Create the new node
2811:                         nodeid = 'node/' + domain.id + '/' + buf.toString('base64').replace(/\+/g, '@').replace(/\//g, '$');
2812:                         var device = { type: 'node', _id: nodeid, meshid: command.meshid, mtype: 1, icon: 1, name: command.devicename, host: command.hostname, domain: domain.id, intelamt: { user: command.amtusername, pass: command.amtpassword, tls: command.amttls } };
2813: 
2814:                         // Add optional feilds
2815:                         if (common.validateInt(command.state, 0, 3)) { device.intelamt.state = command.state; }
2816:                         if (common.validateString(command.ver, 1, 16)) { device.intelamt.ver = command.ver; }
2817:                         if (common.validateString(command.hash, 1, 256)) { device.intelamt.hash = command.hash; }
2818:                         if (common.validateString(command.realm, 1, 256)) { device.intelamt.realm = command.realm; }
2819: 
2820:                         // Save the device to the database
2821:                         db.Set(device);
2822: 
2823:                         // Event the new node
2824:                         parent.parent.DispatchEvent(parent.CreateMeshDispatchTargets(command.meshid, [nodeid]), obj, { etype: 'node', userid: user._id, username: user.name, action: 'addnode', node: parent.CloneSafeNode(device), msgid: 84, msgArgs: [command.devicename, mesh.name], msg: 'Added device ' + command.devicename + ' to device group ' + mesh.name, domain: domain.id });
2825:                         // Send response if required
2826:                         if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'addamtdevice', responseid: command.responseid, result: 'ok' })); } catch (ex) { } }
2827:                     });
2828: 
2829:                     break;
2830:                 }
2831:             case 'scanamtdevice':
2832:                 {
2833:                     if (args.wanonly == true) return; // This is a WAN-only server, this type of scanning is not allowed.
2834:                     if (common.validateString(command.range, 1, 256) == false) break; // Check range string
2835: 
2836:                     // Ask the RMCP scanning to scan a range of IP addresses
2837:                     if (parent.parent.amtScanner) {
2838:                         if (parent.parent.amtScanner.performRangeScan(user._id, command.range) == false) {
2839:                             parent.parent.DispatchEvent(['*', user._id], obj, { action: 'scanamtdevice', range: command.range, results: null, nolog: 1 });
2840:                         }
2841:                     }
2842:                     break;
2843:                 }
2844:             case 'changeDeviceMesh':
2845:                 {
2846:                     var err = null;
2847: 
2848:                     // Resolve the device group name if needed
2849:                     if ((typeof command.meshname == 'string') && (command.meshid == null)) {
2850:                         for (var i in parent.meshes) {
2851:                             var m = parent.meshes[i];
2852:                             if ((m.mtype == 2) && (m.name == command.meshname) && parent.IsMeshViewable(user, m)) {
2853:                                 if (command.meshid == null) { command.meshid = m._id; } else { err = 'Duplicate device groups found'; }
2854:                             }
2855:                         }
2856:                     }
2857: 
2858:                     // Perform input validation
2859:                     try {
2860:                         if (common.validateStrArray(command.nodeids, 1, 256) == false) { err = "Invalid nodeids"; } // Check nodeids
2861:                         if (common.validateString(command.meshid, 8, 134) == false) { err = "Invalid groupid"; } // Check meshid
2862:                         else {
2863:                             if (command.meshid.indexOf('/') == -1) { command.meshid = 'mesh/' + domain.id + '/' + command.meshid; }
2864:                             mesh = parent.meshes[command.meshid];
2865:                             if (mesh == null) { err = "Unknown device group"; }
2866:                             else if ((parent.GetMeshRights(user, mesh) & MESHRIGHT_MANAGECOMPUTERS) == 0) { err = "Permission denied"; }
2867:                             else if ((command.meshid.split('/').length != 3) || (command.meshid.split('/')[1] != domain.id)) { err = "Invalid domain"; } // Invalid domain, operation only valid for current domain
2868:                         }
2869:                     } catch (ex) { console.log(ex); err = "Validation exception: " + ex; }
2870: 
2871:                     // Handle any errors
2872:                     if (err != null) {
2873:                         if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'changeDeviceMesh', responseid: command.responseid, result: err })); } catch (ex) { } }
2874:                         break;
2875:                     }
2876: 
2877:                     // This is to change device guest sharing to the new device group
2878:                     var changeDeviceShareMeshIdNodeCount = command.nodeids.length;
2879:                     var changeDeviceShareMeshIdNodeList = [];
2880: 
2881:                     // For each nodeid, change the group
2882:                     for (var i = 0; i < command.nodeids.length; i++) {
2883:                         var xnodeid = command.nodeids[i];
2884:                         if (xnodeid.indexOf('/') == -1) { xnodeid = 'node/' + domain.id + '/' + xnodeid; }
2885: 
2886:                         // Get the node and the rights for this node
2887:                         parent.GetNodeWithRights(domain, user, xnodeid, function (node, rights, visible) {
2888:                             // Check if we found this device
2889:                             if (node == null) { if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'changeDeviceMesh', responseid: command.responseid, result: 'Device not found' })); } catch (ex) { } } changeDeviceShareMeshIdNodeCount--; return; }
2890: 
2891:                             // Check if already in the right mesh
2892:                             if (node.meshid == command.meshid) { if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'changeDeviceMesh', responseid: command.responseid, result: 'Device already in correct group' })); } catch (ex) { } } changeDeviceShareMeshIdNodeCount--; return; }
2893: 
2894:                             // Make sure both source and target mesh are the same type
2895:                             try { if (parent.meshes[node.meshid].mtype != parent.meshes[command.meshid].mtype) { changeDeviceShareMeshIdNodeCount--; return; } } catch (e) {
2896:                                 if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'changeDeviceMesh', responseid: command.responseid, result: 'Device groups are of different types' })); } catch (ex) { } }
2897:                                 changeDeviceShareMeshIdNodeCount--;
2898:                                 return;
2899:                             };
2900: 
2901:                             // Make sure that we have rights on both source and destination mesh
2902:                             const targetMeshRights = parent.GetMeshRights(user, command.meshid);
2903:                             if (((rights & MESHRIGHT_EDITMESH) == 0) || ((targetMeshRights & MESHRIGHT_EDITMESH) == 0)) {
2904:                                 if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'changeDeviceMesh', responseid: command.responseid, result: 'Permission denied' })); } catch (ex) { } }
2905:                                 changeDeviceShareMeshIdNodeCount--;
2906:                                 return;
2907:                             }
2908: 
2909:                             // Perform the switch, start by saving the node with the new meshid.
2910:                             changeDeviceShareMeshIdNodeList.push(node._id);
2911:                             changeDeviceShareMeshIdNodeCount--;
2912:                             if (changeDeviceShareMeshIdNodeCount == 0) { changeDeviceShareMeshId(changeDeviceShareMeshIdNodeList, command.meshid); }
2913:                             const oldMeshId = node.meshid;
2914:                             node.meshid = command.meshid;
2915:                             db.Set(parent.cleanDevice(node));
2916: 
2917:                             // If the device is connected on this server, switch it now.
2918:                             var agentSession = parent.wsagents[node._id];
2919:                             if (agentSession != null) {
2920:                                 agentSession.dbMeshKey = command.meshid; // Switch the agent mesh
2921:                                 agentSession.meshid = command.meshid.split('/')[2]; // Switch the agent mesh
2922:                                 agentSession.sendUpdatedIntelAmtPolicy(); // Send the new Intel AMT policy
2923:                             }
2924: 
2925:                             // If any MQTT sessions are connected on this server, switch it now.
2926:                             if (parent.parent.mqttbroker != null) { parent.parent.mqttbroker.changeDeviceMesh(node._id, command.meshid); }
2927: 
2928:                             // If any CIRA sessions are connected on this server, switch it now.
2929:                             if (parent.parent.mpsserver != null) { parent.parent.mpsserver.changeDeviceMesh(node._id, command.meshid); }
2930: 
2931:                             // Add the connection state
2932:                             const state = parent.parent.GetConnectivityState(node._id);
2933:                             if (state) {
2934:                                 node.conn = state.connectivity;
2935:                                 node.pwr = state.powerState;
2936:                                 if ((state.connectivity & 1) != 0) { var agent = parent.wsagents[node._id]; if (agent != null) { node.agct = agent.connectTime; } }
2937: 
2938:                                 // Uuse the connection time of the CIRA/Relay connection
2939:                                 if ((state.connectivity & 2) != 0) {
2940:                                     var ciraConnection = parent.parent.mpsserver.GetConnectionToNode(node._id, null, true);
2941:                                     if ((ciraConnection != null) && (ciraConnection.tag != null)) { node.cict = ciraConnection.tag.connectTime; }
2942:                                 }
2943:                             }
2944: 
2945:                             // Update lastconnect meshid for this node
2946:                             db.Get('lc' + node._id, function (err, xnodes) {
2947:                                 if ((xnodes != null) && (xnodes.length == 1) && (xnodes[0].meshid != command.meshid)) { xnodes[0].meshid = command.meshid; db.Set(xnodes[0]); }
2948:                             });
2949: 
2950:                             // Event the node change
2951:                             var newMesh = parent.meshes[command.meshid];
2952:                             var event = { etype: 'node', userid: user._id, username: user.name, action: 'nodemeshchange', nodeid: node._id, node: node, oldMeshId: oldMeshId, newMeshId: command.meshid, msgid: 85, msgArgs: [node.name, newMesh.name], msg: 'Moved device ' + node.name + ' to group ' + newMesh.name, domain: domain.id };
2953:                             if(user._id === req.session.userid && req.session.tokenUser != null && req.session.tokenName != null) {
2954:                                 event.tokenName = req.session.tokenName; event.tokenUser = req.session.tokenUser;
2955:                             }
2956:                             // Even if change stream is enabled on this server, we still make the nodemeshchange actionable. This is because the DB can't send out a change event that will match this.
2957:                             parent.parent.DispatchEvent(parent.CreateMeshDispatchTargets(command.meshid, [oldMeshId, node._id]), obj, event);
2958: 
2959:                             // Send response if required
2960:                             if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'changeDeviceMesh', responseid: command.responseid, result: 'ok' })); } catch (ex) { } }
2961:                         });
2962:                     }
2963:                     break;
2964:                 }
2965:             case 'removedevices':
2966:                 {
2967:                     if (common.validateArray(command.nodeids, 1) == false) break; // Check nodeid's
2968: 
2969:                     // Users and user groups that lose device links are saved once, after all the devices in this
2970:                     // request are processed. Saving them per device rewrites each user record once per removed
2971:                     // device, which is very expensive when users have many device links.
2972:                     var removeDevicesChangedUsers = {}, removeDevicesChangedUserGroups = {}, removeDevicesPending = 1;
2973:                     function removeDevicesDone() {
2974:                         if (--removeDevicesPending > 0) return;
2975:                         for (var uid in removeDevicesChangedUsers) {
2976:                             var cuser = removeDevicesChangedUsers[uid];
2977:                             db.SetUser(cuser);
2978: 
2979:                             // Notify user change
2980:                             var targets = ['*', 'server-users', cuser._id];
2981:                             var event = { etype: 'user', userid: cuser._id, username: cuser.name, action: 'accountchange', msgid: 86, msgArgs: [cuser.name], msg: 'Removed user device rights for ' + cuser.name, domain: domain.id, account: parent.CloneSafeUser(cuser) };
2982:                             if (db.changeStream) { event.noact = 1; } // If DB change stream is active, don't use this event to change the user. Another event will come.
2983:                             parent.parent.DispatchEvent(targets, obj, event);
2984:                         }
2985:                         for (var gid in removeDevicesChangedUserGroups) { db.Set(removeDevicesChangedUserGroups[gid]); }
2986:                     }
2987: 
2988:                     for (i in command.nodeids) {
2989:                         var nodeid = command.nodeids[i], err = null;
2990: 
2991:                         // Argument validation
2992:                         if (common.validateString(nodeid, 1, 1024) == false) { err = 'Invalid nodeid'; }  // Check nodeid
2993:                         else {
2994:                             if (nodeid.indexOf('/') == -1) { nodeid = 'node/' + domain.id + '/' + nodeid; }
2995:                             if ((nodeid.split('/').length != 3) || (nodeid.split('/')[1] != domain.id)) { err = 'Invalid domain'; } // Invalid domain, operation only valid for current domain
2996:                         }
2997:                         if (err != null) {
2998:                             if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'removedevices', responseid: command.responseid, result: err })); } catch (ex) { } }
2999:                             continue;
3000:                         }
3001:                         // Get the node and the rights for this node
3002:                         removeDevicesPending++;
3003:                         parent.GetNodeWithRights(domain, user, nodeid, function (node, rights, visible) {
3004:                             // Check we have the rights to delete this device
3005:                             if ((rights & MESHRIGHT_UNINSTALL) == 0) {
3006:                                 if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'removedevices', responseid: command.responseid, result: 'Denied' })); } catch (ex) { } }
3007:                                 removeDevicesDone();
3008:                                 return;
3009:                             }
3010: 
3011:                             // Delete this node including network interface information, events and timeline
3012:                             db.Remove(node._id);                                 // Remove node with that id
```

## Lines 3519-3635
```text
3519:                         else {
3520:                             if (command.nodeid.indexOf('/') == -1) { command.nodeid = 'node/' + domain.id + '/' + command.nodeid; }
3521:                             if ((command.nodeid.split('/').length != 3) || (command.nodeid.split('/')[1] != domain.id)) { err = "Invalid nodeid"; } // Invalid domain, operation only valid for current domain
3522:                             else if ((command.userloc) && ((common.validateArray(command.userloc) == false) || ((command.userloc.length != 2) && (command.userloc.length != 0)))) { err = "Invalid user location"; }
3523:                             else if ((command.tags != null) && (common.validateString(command.tags, 0, 4096) == false) && (common.validateStrArray(command.tags) == false)) { err = "Invalid tags"; }
3524:                         }
3525:                     } catch (ex) { console.log(ex); err = "Validation exception: " + ex; }
3526: 
3527:                     // Handle any errors
3528:                     if (err != null) {
3529:                         if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'changedevice', responseid: command.responseid, result: err })); } catch (ex) { } }
3530:                         break;
3531:                     }
3532: 
3533:                     // Get the node and the rights for this node
3534:                     parent.GetNodeWithRights(domain, user, command.nodeid, function (node, rights, visible) {
3535:                         if ((rights & MESHRIGHT_MANAGECOMPUTERS) == 0) {
3536:                             if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'changedevice', responseid: command.responseid, result: 'Access Denied' })); } catch (ex) { } }
3537:                             return;
3538:                         }
3539:                         node = common.unEscapeLinksFieldName(node); // unEscape node data for rdp/ssh credentials
3540:                         var mesh = parent.meshes[node.meshid], amtchange = 0;
3541: 
3542:                         // Ready the node change event
3543:                         var changes = [], event = { etype: 'node', userid: user._id, username: user.name, action: 'changenode', nodeid: node._id, domain: domain.id };
3544:                         change = 0;
3545:                         event.msg = ': ';
3546: 
3547:                         // If we are in WAN-only mode, host is not used
3548:                         if ((args.wanonly == true) && (command.host) && (node.mtype != 3) && (node.mtype != 4)) { delete command.host; }
3549: 
3550:                         // Look for a change
3551:                         if ((typeof command.icon == 'number') && (command.icon != node.icon)) { change = 1; node.icon = command.icon; changes.push('icon'); }
3552:                         if ((typeof command.name == 'string') && (command.name != node.name)) { change = 1; node.name = command.name; changes.push('name'); }
3553:                         if ((typeof command.host == 'string') && (command.host != node.host)) { change = 1; node.host = command.host; changes.push('host'); }
3554:                         if (typeof command.consent == 'number') {
3555:                             var oldConsent = node.consent;
3556:                             if (command.consent != node.consent) { node.consent = command.consent; }
3557:                             if (command.consent == 0) { delete node.consent; }
3558:                             if (oldConsent != node.consent) { change = 1; changes.push('consent'); }
3559:                         }
3560: 
3561:                         if ((typeof command.rdpport == 'number') && (command.rdpport > 0) && (command.rdpport < 65536)) {
3562:                             if ((command.rdpport == 3389) && (node.rdpport != null)) {
3563:                                 delete node.rdpport; change = 1; changes.push('rdpport'); // Delete the RDP port
3564:                             } else {
3565:                                 node.rdpport = command.rdpport; change = 1; changes.push('rdpport'); // Set the RDP port
3566:                             }
3567:                         }
3568: 
3569:                         if ((typeof command.rfbport == 'number') && (command.rfbport > 0) && (command.rfbport < 65536)) {
3570:                             if ((command.rfbport == 5900) && (node.rfbport != null)) {
3571:                                 delete node.rfbport; change = 1; changes.push('rfbport'); // Delete the RFB port
3572:                             } else {
3573:                                 node.rfbport = command.rfbport; change = 1; changes.push('rfbport'); // Set the RFB port
3574:                             }
3575:                         }
3576: 
3577:                         if ((typeof command.sshport == 'number') && (command.sshport > 0) && (command.sshport < 65536)) {
3578:                             if ((command.sshport == 22) && (node.sshport != null)) {
3579:                                 delete node.sshport; change = 1; changes.push('sshport'); // Delete the SSH port
3580:                             } else {
3581:                                 node.sshport = command.sshport; change = 1; changes.push('sshport'); // Set the SSH port
3582:                             }
3583:                         }
3584: 
3585:                         if ((typeof command.httpport == 'number') && (command.httpport > 0) && (command.httpport < 65536)) {
3586:                             if ((command.httpport == 80) && (node.httpport != null)) {
3587:                                 delete node.httpport; change = 1; changes.push('httpport'); // Delete the HTTP port
3588:                             } else {
3589:                                 node.httpport = command.httpport; change = 1; changes.push('httpport'); // Set the HTTP port
3590:                             }
3591:                         }
3592: 
3593:                         if ((typeof command.httpsport == 'number') && (command.httpsport > 0) && (command.httpsport < 65536)) {
3594:                             if ((command.httpsport == 443) && (node.httpsport != null)) {
3595:                                 delete node.httpsport; change = 1; changes.push('httpsport'); // Delete the HTTPS port
3596:                             } else {
3597:                                 node.httpsport = command.httpsport; change = 1; changes.push('httpsport'); // Set the HTTPS port
3598:                             }
3599:                         }
3600: 
3601:                         if ((typeof command.ssh == 'number') && (command.ssh == 0)) {
3602:                             if ((node.ssh != null) && (node.ssh[user._id] != null)) { delete node.ssh[user._id]; change = 1; changes.push('ssh'); } // Delete the SSH cendentials
3603:                         }
3604: 
3605:                         if ((typeof command.rdp == 'number') && (command.rdp == 0)) {
3606:                             if ((node.rdp != null) && (node.rdp[user._id] != null)) { delete node.rdp[user._id]; change = 1; changes.push('rdp'); } // Delete the RDP cendentials
3607:                         }
3608: 
3609:                         // Clean up any legacy RDP and SSH credentials
3610:                         if (node.rdp != null) { delete node.rdp.d; delete node.rdp.u; delete node.rdp.p; }
3611:                         if (node.ssh != null) { delete node.ssh.u; delete node.ssh.p; delete node.ssh.k; delete node.ssh.kp; }
3612: 
3613:                         if (domain.geolocation && command.userloc && ((node.userloc == null) || (command.userloc[0] != node.userloc[0]) || (command.userloc[1] != node.userloc[1]))) {
3614:                             change = 1;
3615:                             if ((command.userloc.length == 0) && (node.userloc)) {
3616:                                 delete node.userloc;
3617:                                 changes.push('location removed');
3618:                             } else {
3619:                                 command.userloc.push((Math.floor((new Date()) / 1000)));
3620:                                 node.userloc = command.userloc.join(',');
3621:                                 changes.push('location');
3622:                             }
3623:                         }
3624:                         if (command.desc != null && (command.desc != node.desc)) { change = 1; node.desc = command.desc; changes.push('description'); }
3625:                         if ((command.intelamt != null) && (node.intelamt != null)) {
3626:                             if ((parent.parent.amtManager == null) || (node.intelamt.pass == null) || (node.intelamt.pass == '') || ((node.intelamt.warn != null) && (((node.intelamt.warn) & 9) != 0))) { // Only allow changes to Intel AMT credentials if AMT manager is not running, or manager warned of unknown/trying credentials.
3627:                                 if ((command.intelamt.user != null) && (command.intelamt.pass != null) && ((command.intelamt.user != node.intelamt.user) || (command.intelamt.pass != node.intelamt.pass))) {
3628:                                     change = 1;
3629:                                     node.intelamt.user = command.intelamt.user;
3630:                                     node.intelamt.pass = command.intelamt.pass;
3631:                                     node.intelamt.warn |= 8; // Change warning to "Trying". Bit flags: 1 = Unknown credentials, 2 = Realm Mismatch, 4 = TLS Cert Mismatch, 8 = Trying credentials
3632:                                     changes.push('Intel AMT credentials');
3633:                                     amtchange = 1;
3634:                                 }
3635:                             }
```

## Lines 3697-3855
```text
3697:                             } else if ((command.type == 'custom') && (common.validateString(command.path, 1, 2048) == true)) {
3698:                                 // Send a mesh agent core to the mesh agent
3699:                                 var file = parent.getServerFilePath(user, domain, command.path);
3700:                                 if (file != null) {
3701:                                     fs.readFile(file.fullpath, 'utf8', function (err, data) {
3702:                                         if (err != null) {
3703:                                             data = common.IntToStr(0) + data; // Add the 4 bytes encoding type & flags (Set to 0 for raw)
3704:                                             parent.sendMeshAgentCore(user, domain, node._id, 'custom', data);
3705:                                         }
3706:                                     });
3707:                                 }
3708:                             }
3709:                         });
3710:                     }
3711:                     break;
3712:                 }
3713:             case 'inviteAgent':
3714:                 {
3715:                     var err = null, mesh = null;
3716: 
3717:                     // Resolve the device group name if needed
3718:                     if ((typeof command.meshname == 'string') && (command.meshid == null)) {
3719:                         for (var i in parent.meshes) {
3720:                             var m = parent.meshes[i];
3721:                             if ((m.mtype == 2) && (m.name == command.meshname) && parent.IsMeshViewable(user, m)) {
3722:                                 if (command.meshid == null) { command.meshid = m._id; } else { err = 'Duplicate device groups found'; }
3723:                             }
3724:                         }
3725:                     }
3726: 
3727:                     try {
3728:                         if ((domain.mailserver == null) || (args.lanonly == true)) { err = 'Unsupported feature'; } // This operation requires the email server
3729:                         else if ((parent.parent.certificates.CommonName == null) || (parent.parent.certificates.CommonName.indexOf('.') == -1)) { err = 'Unsupported feature'; } // Server name must be configured
3730:                         else if (common.validateString(command.meshid, 8, 134) == false) { err = 'Invalid group identifier'; } // Check meshid
3731:                         else {
3732:                             if (command.meshid.split('/').length == 1) { command.meshid = 'mesh/' + domain.id + '/' + command.meshid; }
3733:                             if ((command.meshid.split('/').length != 3) || (command.meshid.split('/')[1] != domain.id)) { err = 'Invalid group identifier'; } // Invalid domain, operation only valid for current domain
3734:                             else if (common.validateString(command.email, 4, 1024) == false) { err = 'Invalid email'; } // Check email
3735:                             else if (command.email.split('@').length != 2) { err = 'Invalid email'; } // Check email
3736:                             else {
3737:                                 mesh = parent.meshes[command.meshid];
3738:                                 if (mesh == null) { err = 'Unknown device group'; } // Check if the group exists
3739:                                 else if (mesh.mtype != 2) { err = 'Invalid group type'; } // Check if this is the correct group type
3740:                                 else if (parent.IsMeshViewable(user, mesh) == false) { err = 'Not allowed'; } // Check if this user has rights to do this
3741:                             }
3742:                         }
3743:                     } catch (ex) { err = 'Validation exception: ' + ex; }
3744: 
3745:                     // Handle any errors
3746:                     if (err != null) {
3747:                         if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'inviteAgent', responseid: command.responseid, result: err })); } catch (ex) { } }
3748:                         break;
3749:                     }
3750: 
3751:                     // Perform email invitation
3752:                     domain.mailserver.sendAgentInviteMail(domain, (user.realname ? user.realname : user.name), command.email.toLowerCase(), command.meshid, command.name, command.os, command.msg, command.flags, command.expire, parent.getLanguageCodes(req), req.query.key);
3753: 
3754:                     // Send a response if needed
3755:                     if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'inviteAgent', responseid: command.responseid, result: 'ok' })); } catch (ex) { } }
3756:                     break;
3757:                 }
3758:             case 'setDeviceEvent':
3759:                 {
3760:                     // Argument validation
3761:                     if (common.validateString(command.msg, 1, 4096) == false) break; // Check event
3762:                     var decodedMsg;
3763:                     try { decodedMsg = decodeURIComponent(command.msg); } catch (ex) { break; }
3764: 
3765:                     // Get the node and the rights for this node
3766:                     parent.GetNodeWithRights(domain, user, command.nodeid, function (node, rights, visible) {
3767:                         if (rights == 0) return;
3768: 
3769:                         // Add an event for this device
3770:                         var targets = parent.CreateNodeDispatchTargets(node.meshid, node._id, ['server-users', user._id]);
3771:                         var event = { etype: 'node', userid: user._id, username: user.name, nodeid: node._id, action: 'manual', msg: decodedMsg, domain: domain.id };
3772:                         if(user._id === req.session.userid && req.session.tokenUser != null && req.session.tokenName != null) {
3773:                             event.tokenName = req.session.tokenName; event.tokenUser = req.session.tokenUser;
3774:                         }
3775:                         parent.parent.DispatchEvent(targets, obj, event);
3776:                     });
3777:                     break;
3778:                 }
3779:             case 'setNotes':
3780:                 {
3781:                     // Argument validation
3782:                     if (common.validateString(command.id, 1, 1024) == false) break; // Check id
3783:                     var splitid = command.id.split('/');
3784:                     if ((splitid.length != 3) || (splitid[1] != domain.id)) return; // Invalid domain, operation only valid for current domain
3785:                     var idtype = splitid[0];
3786:                     if ((idtype != 'puser') && (idtype != 'user') && (idtype != 'mesh') && (idtype != 'node')) return;
3787: 
3788:                     if (idtype == 'node') {
3789:                         // Get the node and the rights for this node
3790:                         parent.GetNodeWithRights(domain, user, command.id, function (node, rights, visible) {
3791:                             if ((rights & MESHRIGHT_SETNOTES) != 0) {
3792:                                 // Set the id's notes
3793:                                 if (common.validateString(command.notes, 1) == false) {
3794:                                     db.Remove('nt' + node._id); // Delete the note for this node
3795:                                 } else {
3796:                                     db.Set({ _id: 'nt' + node._id, type: 'note', value: command.notes }); // Set the note for this node
3797:                                 }
3798:                             }
3799:                         });
3800:                     } else if (idtype == 'mesh') {
3801:                         // Get the mesh for this device
3802:                         mesh = parent.meshes[command.id];
3803:                         if (mesh) {
3804:                             // Check if this user has rights to do this
3805:                             if ((parent.GetMeshRights(user, mesh) & MESHRIGHT_EDITMESH) == 0) return; // Must have rights to edit the mesh
3806: 
3807:                             // Set the id's notes
3808:                             if (common.validateString(command.notes, 1) == false) {
3809:                                 db.Remove('nt' + command.id); // Delete the note for this node
3810:                             } else {
3811:                                 db.Set({ _id: 'nt' + command.id, type: 'note', value: command.notes }); // Set the note for this mesh
3812:                             }
3813:                         }
3814:                     } else if ((idtype == 'user') && ((user.siteadmin & 2) != 0)) {
3815:                         // Set the id's notes
3816:                         if (common.validateString(command.notes, 1) == false) {
3817:                             db.Remove('nt' + command.id); // Delete the note for this node
3818:                         } else {
3819:                             // Can only perform this operation on other users of our group.
3820:                             var chguser = parent.users[command.id];
3821:                             if (chguser == null) break; // This user does not exists
3822:                             if ((user.groups != null) && (user.groups.length > 0) && ((chguser.groups == null) || (findOne(chguser.groups, user.groups) == false))) break;
3823:                             db.Set({ _id: 'nt' + command.id, type: 'note', value: command.notes }); // Set the note for this user
3824:                         }
3825:                     } else if (idtype == 'puser') {
3826:                         // Set the user's personal note, starts with 'ntp' + userid.
3827:                         if (common.validateString(command.notes, 1) == false) {
3828:                             db.Remove('ntp' + user._id); // Delete the note for this node
3829:                         } else {
3830:                             db.Set({ _id: 'ntp' + user._id, type: 'note', value: command.notes }); // Set the note for this user
3831:                         }
3832:                     }
3833: 
3834:                     break;
3835:                 }
3836:             case 'otpemail':
3837:                 {
3838:                     // Do not allow this command if 2FA's are locked
3839:                     if ((domain.passwordrequirements) && (domain.passwordrequirements.lock2factor == true)) return;
3840: 
3841:                     // Do not allow this command when logged in using a login token
3842:                     if (req.session.loginToken != null) break;
3843: 
3844:                     if ((user.siteadmin != 0xFFFFFFFF) && ((user.siteadmin & 1024) != 0)) return; // If this account is settings locked, return here.
3845: 
3846:                     // Check input
3847:                     if (typeof command.enabled != 'boolean') return;
3848: 
3849:                     // See if we really need to change the state
3850:                     if ((command.enabled === true) && (user.otpekey != null)) return;
3851:                     if ((command.enabled === false) && (user.otpekey == null)) return;
3852: 
3853:                     // Change the email 2FA of this user
3854:                     if (command.enabled === true) { user.otpekey = {}; } else { delete user.otpekey; }
3855:                     parent.db.SetUser(user);
```

## Lines 4384-4902
```text
4384:                                 if ((notes == null) || (notes.length != 1)) { ws.send(JSON.stringify({ action: 'getNotes', id: command.id, notes: null })); return; }
4385:                                 ws.send(JSON.stringify({ action: 'getNotes', id: command.id, notes: notes[0].value }));
4386:                             } catch (ex) { }
4387:                         });
4388:                     } else if (idtype == 'puser') {
4389:                         // Get personal note, starts with 'ntp' + userid
4390:                         db.Get('ntp' + user._id, function (err, notes) {
4391:                             try {
4392:                                 if ((notes == null) || (notes.length != 1)) { ws.send(JSON.stringify({ action: 'getNotes', id: command.id, notes: null })); return; }
4393:                                 ws.send(JSON.stringify({ action: 'getNotes', id: command.id, notes: notes[0].value }));
4394:                             } catch (ex) { }
4395:                         });
4396:                     }
4397: 
4398:                     break;
4399:                 }
4400:             case 'createInviteLink': {
4401:                 var err = null;
4402: 
4403:                 // Resolve the device group name if needed
4404:                 if ((typeof command.meshname == 'string') && (command.meshid == null)) {
4405:                     for (var i in parent.meshes) {
4406:                         var m = parent.meshes[i];
4407:                         if ((m.mtype == 2) && (m.name == command.meshname) && parent.IsMeshViewable(user, m)) {
4408:                             if (command.meshid == null) { command.meshid = m._id; } else { err = 'Duplicate device groups found'; }
4409:                         }
4410:                     }
4411:                 }
4412: 
4413:                 if (common.validateString(command.meshid, 8, 134) == false) { err = 'Invalid group id'; } // Check the meshid (Max length of a meshid is 134 bytes).
4414:                 else if (common.validateInt(command.expire, 0, 99999) == false) { err = 'Invalid expire time'; } // Check the expire time in hours
4415:                 else if (common.validateInt(command.flags, 0, 256) == false) { err = 'Invalid flags'; } // Check the flags
4416:                 else {
4417:                     if (command.meshid.split('/').length == 1) { command.meshid = 'mesh/' + domain.id + '/' + command.meshid; }
4418:                     var smesh = command.meshid.split('/');
4419:                     if ((smesh.length != 3) || (smesh[0] != 'mesh') || (smesh[1] != domain.id)) { err = 'Invalid group id'; }
4420:                     mesh = parent.meshes[command.meshid];
4421:                     if ((mesh == null) || (parent.IsMeshViewable(user, mesh) == false)) { err = 'Invalid group id'; }
4422:                 }
4423:                 var serverName = parent.getWebServerName(domain, req);
4424: 
4425:                 // Handle any errors
4426:                 if (err != null) {
4427:                     console.log(err, command.meshid);
4428:                     if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'createInviteLink', responseid: command.responseid, result: err })); } catch (ex) { } }
4429:                     break;
4430:                 }
4431: 
4432:                 const cookie = { a: 4, mid: command.meshid, f: command.flags, expire: command.expire * 60 };
4433:                 if ((typeof command.agents == 'number') && (command.agents != 0)) { cookie.ag = command.agents; }
4434:                 const inviteCookie = parent.parent.encodeCookie(cookie, parent.parent.invitationLinkEncryptionKey);
4435:                 if (inviteCookie == null) { if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'createInviteLink', responseid: command.responseid, result: 'Unable to generate invitation cookie' })); } catch (ex) { } } break; }
4436: 
4437:                 // Create the server url
4438:                 var httpsPort = ((args.aliasport == null) ? args.port : args.aliasport); // Use HTTPS alias port is specified
4439:                 var xdomain = (domain.dns == null) ? domain.id : '';
4440:                 if (xdomain != '') xdomain += '/';
4441:                 var url = 'https://' + serverName + ':' + httpsPort + '/' + xdomain + 'agentinvite?c=' + inviteCookie;
4442:                 if (serverName.split('.') == 1) { url = '/' + xdomain + 'agentinvite?c=' + inviteCookie; }
4443: 
4444:                 ws.send(JSON.stringify({ action: 'createInviteLink', meshid: command.meshid, url: url, expire: command.expire, cookie: inviteCookie, responseid: command.responseid, tag: command.tag }));
4445:                 break;
4446:             }
4447:             case 'deviceMeshShares': {
4448:                 if (domain.guestdevicesharing === false) return; // This feature is not allowed.
4449:                 var err = null;
4450: 
4451:                 // Argument validation
4452:                 if (common.validateString(command.meshid, 8, 134) == false) { err = 'Invalid device group id'; } // Check the meshid
4453:                 else if (command.meshid.indexOf('/') == -1) { command.meshid = 'mesh/' + domain.id + '/' + command.meshid; }
4454:                 else if ((command.meshid.split('/').length != 3) || (command.meshid.split('/')[1] != domain.id)) { err = 'Invalid domain'; } // Invalid domain, operation only valid for current domain
4455:                 else {
4456:                     // Check if we have rights on this device group
4457:                     mesh = parent.meshes[command.meshid];
4458:                     if (mesh == null) { err = 'Invalid device group id'; } // Check the meshid
4459:                     else if (parent.GetMeshRights(user, mesh) == 0) { err = 'Access denied'; }
4460:                 }
4461: 
4462:                 // Handle any errors
4463:                 if (err != null) {
4464:                     if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'deviceShares', responseid: command.responseid, result: err })); } catch (ex) { } }
4465:                     break;
4466:                 }
4467: 
4468:                 // Get all device shares
4469:                 parent.db.GetAllTypeNoTypeField('deviceshare', domain.id, function (err, docs) {
4470:                     if (err != null) return;
4471:                     var now = Date.now(), okDocs = [];
4472:                     for (var i = 0; i < docs.length; i++) {
4473:                         const doc = docs[i];
4474:                         if ((doc.expireTime != null) && (doc.expireTime < now)) {
4475:                             // This share is expired.
4476:                             parent.db.Remove(doc._id, function () { });
4477: 
4478:                             // Send device share update
4479:                             var targets = parent.CreateNodeDispatchTargets(doc.xmeshid, doc.nodeid, ['server-users', user._id]);
4480:                             parent.parent.DispatchEvent(targets, obj, { etype: 'node', meshid: doc.xmeshid, nodeid: doc.nodeid, action: 'deviceShareUpdate', domain: domain.id, deviceShares: okDocs, nolog: 1 });
4481:                         } else {
4482:                             if (doc.xmeshid == null) {
4483:                                 // This is an old share with missing meshid, fix it here.
4484:                                 const f = function fixShareMeshId(err, nodes) {
4485:                                     if (err != null) return;
4486:                                     if (nodes.length == 1) {
4487:                                         // Add the meshid to the device share
4488:                                         fixShareMeshId.xdoc.xmeshid = nodes[0].meshid;
4489:                                         fixShareMeshId.xdoc.type = 'deviceshare';
4490:                                         delete fixShareMeshId.xdoc.meshid;
4491:                                         parent.db.Set(fixShareMeshId.xdoc);
4492:                                     } else {
4493:                                         // This node no longer exists, remove the device share.
4494:                                         parent.db.Remove(fixShareMeshId.xdoc._id);
4495:                                     }
4496:                                 }
4497:                                 f.xdoc = doc;
4498:                                 db.Get(doc.nodeid, f);
4499:                             } else if (doc.xmeshid == command.meshid) {
4500:                                 // This share is ok, remove extra data we don't need to send.
4501:                                 delete doc._id; delete doc.domain; delete doc.type; delete doc.xmeshid;
4502:                                 if (doc.userid != user._id) { delete doc.url; } // If this is not the user who created this link, don't give the link.
4503:                                 okDocs.push(doc);
4504:                             }
4505:                         }
4506:                     }
4507:                     try { ws.send(JSON.stringify({ action: 'deviceMeshShares', meshid: command.meshid, deviceShares: okDocs })); } catch (ex) { }
4508:                 });
4509:                 break;
4510:             }
4511:             case 'deviceShares': {
4512:                 if (domain.guestdevicesharing === false) return; // This feature is not allowed.
4513:                 var err = null;
4514: 
4515:                 // Argument validation
4516:                 if (common.validateString(command.nodeid, 8, 128) == false) { err = 'Invalid node id'; } // Check the nodeid
4517:                 else if (command.nodeid.indexOf('/') == -1) { command.nodeid = 'node/' + domain.id + '/' + command.nodeid; }
4518:                 else if ((command.nodeid.split('/').length != 3) || (command.nodeid.split('/')[1] != domain.id)) { err = 'Invalid domain'; } // Invalid domain, operation only valid for current domain
4519: 
4520:                 // Handle any errors
4521:                 if (err != null) {
4522:                     if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'deviceShares', responseid: command.responseid, result: err })); } catch (ex) { } }
4523:                     break;
4524:                 }
4525: 
4526:                 // Get the device rights
4527:                 parent.GetNodeWithRights(domain, user, command.nodeid, function (node, rights, visible) {
4528:                     // If node not found or we don't have remote control, reject.
4529:                     if (node == null) {
4530:                         if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'deviceShares', responseid: command.responseid, result: 'Invalid node id' })); } catch (ex) { } }
4531:                         return;
4532:                     }
4533: 
4534:                     // If there is MESHRIGHT_DESKLIMITEDINPUT or we don't have MESHRIGHT_GUESTSHARING on this account, reject this request.
4535:                     if (rights != MESHRIGHT_ADMIN) {
4536:                         // If we don't have remote control, or have limited input, or don't have guest sharing permission, fail here.
4537:                         if (((rights & MESHRIGHT_REMOTECONTROL) == 0) || ((rights & MESHRIGHT_DESKLIMITEDINPUT) != 0) || ((rights & MESHRIGHT_GUESTSHARING) == 0)) {
4538:                             if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'deviceShares', responseid: command.responseid, result: 'Access denied' })); } catch (ex) { } }
4539:                             return;
4540:                         }
4541:                     }
4542: 
4543:                     parent.db.GetAllTypeNodeFiltered([command.nodeid], domain.id, 'deviceshare', null, function (err, docs) {
4544:                         if (err != null) return;
4545:                         var now = Date.now(), removed = false, okDocs = [];
4546:                         for (var i = 0; i < docs.length; i++) {
4547:                             const doc = docs[i];
4548:                             if ((doc.expireTime != null) && (doc.expireTime < now)) {
4549:                                 // This share is expired.
4550:                                 parent.db.Remove(doc._id, function () { }); removed = true;
4551:                             } else {
4552:                                 // This share is ok, remove extra data we don't need to send.
4553:                                 delete doc._id; delete doc.domain; delete doc.nodeid; delete doc.type; delete doc.xmeshid;
4554:                                 if ((user.siteadmin !== SITERIGHT_ADMIN) && (doc.userid != user._id)) { delete doc.url; } // If this is not the user who created this link AND the user is not a site admin, don't give the link.
4555:                                 okDocs.push(doc);
4556:                             }
4557:                         }
4558:                         try { ws.send(JSON.stringify({ action: 'deviceShares', nodeid: command.nodeid, deviceShares: okDocs })); } catch (ex) { }
4559: 
4560:                         // If we removed any shares, send device share update
4561:                         if (removed == true) {
4562:                             var targets = parent.CreateNodeDispatchTargets(node.meshid, node._id, ['server-users', user._id]);
4563:                             parent.parent.DispatchEvent(targets, obj, { etype: 'node', nodeid: node._id, action: 'deviceShareUpdate', domain: domain.id, deviceShares: okDocs, nolog: 1 });
4564:                         }
4565:                     });
4566:                 });
4567: 
4568:                 break;
4569:             }
4570:             case 'removeDeviceShare': {
4571:                 if (domain.guestdevicesharing === false) return; // This feature is not allowed.
4572:                 var err = null;
4573: 
4574:                 // Argument validation
4575:                 if (common.validateString(command.nodeid, 8, 128) == false) { err = 'Invalid node id'; } // Check the nodeid
4576:                 else if (command.nodeid.indexOf('/') == -1) { command.nodeid = 'node/' + domain.id + '/' + command.nodeid; }
4577:                 else if ((command.nodeid.split('/').length != 3) || (command.nodeid.split('/')[1] != domain.id)) { err = 'Invalid domain'; } // Invalid domain, operation only valid for current domain
4578:                 if (common.validateString(command.publicid, 1, 128) == false) { err = 'Invalid public id'; } // Check the public identifier
4579: 
4580:                 // Handle any errors
4581:                 if (err != null) {
4582:                     if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'removeDeviceShare', responseid: command.responseid, result: err })); } catch (ex) { } }
4583:                     break;
4584:                 }
4585: 
4586:                 // Get the device rights
4587:                 parent.GetNodeWithRights(domain, user, command.nodeid, function (node, rights, visible) {
4588:                     // If node not found or we don't have remote control, reject.
4589:                     if (node == null) {
4590:                         if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'deviceShares', responseid: command.responseid, result: 'Invalid node id' })); } catch (ex) { } }
4591:                         return;
4592:                     }
4593: 
4594:                     // If there is MESHRIGHT_DESKLIMITEDINPUT or we don't have MESHRIGHT_GUESTSHARING on this account, reject this request.
4595:                     if (rights != MESHRIGHT_ADMIN) {
4596:                         // If we don't have remote control, or have limited input, or don't have guest sharing permission, fail here.
4597:                         if (((rights & MESHRIGHT_REMOTECONTROL) == 0) || ((rights & MESHRIGHT_DESKLIMITEDINPUT) != 0) || ((rights & MESHRIGHT_GUESTSHARING) == 0)) {
4598:                             if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'deviceShares', responseid: command.responseid, result: 'Access denied' })); } catch (ex) { } }
4599:                             return;
4600:                         }
4601:                     }
4602: 
4603:                     parent.db.GetAllTypeNodeFiltered([command.nodeid], domain.id, 'deviceshare', null, function (err, docs) {
4604:                         if (err != null) return;
4605: 
4606:                         // Remove device sharing
4607:                         var now = Date.now(), removedExact = null, removed = false, okDocs = [];
4608:                         for (var i = 0; i < docs.length; i++) {
4609:                             const doc = docs[i];
4610:                             if (doc.publicid == command.publicid) { parent.db.Remove(doc._id, function () { }); removedExact = doc; removed = true; }
4611:                             else if (doc.expireTime < now) { parent.db.Remove(doc._id, function () { }); removed = true; } else {
4612:                                 // This share is ok, remove extra data we don't need to send.
4613:                                 delete doc._id; delete doc.domain; delete doc.nodeid; delete doc.type;
4614:                                 okDocs.push(doc);
4615:                             }
4616:                         }
4617: 
4618:                         // Confirm removal if requested
4619:                         if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'removeDeviceShare', responseid: command.responseid, nodeid: command.nodeid, publicid: command.publicid, removed: removedExact })); } catch (ex) { } }
4620: 
4621:                         // Event device share removal
4622:                         if (removedExact != null) {
4623:                             // Send out an event that we removed a device share
4624:                             var targets = parent.CreateNodeDispatchTargets(node.meshid, node._id, ['server-users', 'server-shareremove', user._id]);
4625:                             var event = { etype: 'node', userid: user._id, username: user.name, nodeid: node._id, action: 'removedDeviceShare', msg: 'Removed Device Share', msgid: 102, msgArgs: [removedExact.guestName], domain: domain.id, publicid: command.publicid };
4626:                             if(user._id === req.session.userid && req.session.tokenUser != null && req.session.tokenName != null) {
4627:                                 event.tokenName = req.session.tokenName; event.tokenUser = req.session.tokenUser;
4628:                             }
4629:                             parent.parent.DispatchEvent(targets, obj, event);
4630: 
4631:                             // If this is an agent self-sharing link, notify the agent
4632:                             if (command.publicid.startsWith('AS:node/')) { routeCommandToNode({ action: 'msg', type: 'guestShare', nodeid: command.publicid.substring(3), flags: 0, url: null, viewOnly: false }); }
4633:                         }
4634: 
4635:                         // If we removed any shares, send device share update
4636:                         if (removed == true) {
4637:                             var targets = parent.CreateNodeDispatchTargets(node.meshid, node._id, ['server-users', user._id]);
4638:                             parent.parent.DispatchEvent(targets, obj, { etype: 'node', nodeid: node._id, action: 'deviceShareUpdate', domain: domain.id, deviceShares: okDocs, nolog: 1 });
4639:                             if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'removeDeviceShare', responseid: command.responseid, result: 'OK' })); } catch (ex) { } }
4640:                         } else {
4641:                             if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'removeDeviceShare', responseid: command.responseid, result: 'Invalid device share identifier.' })); } catch (ex) { } }
4642:                         }
4643:                     });
4644:                 });
4645:                 break;
4646:             }
4647:             case 'createDeviceShareLink': {
4648:                 if (domain.guestdevicesharing === false) return; // This feature is not allowed.
4649:                 var err = null;
4650: 
4651:                 // Argument validation
4652:                 if (common.validateString(command.nodeid, 8, 128) == false) { err = 'Invalid node id'; } // Check the nodeid
4653:                 else if (command.nodeid.indexOf('/') == -1) { command.nodeid = 'node/' + domain.id + '/' + command.nodeid; }
4654:                 else if ((command.nodeid.split('/').length != 3) || (command.nodeid.split('/')[1] != domain.id)) { err = 'Invalid domain'; } // Invalid domain, operation only valid for current domain
4655:                 if (common.validateString(command.guestname, 1, 128) == false) { err = 'Invalid guest name'; } // Check the guest name
4656:                 else if ((command.expire != null) && (typeof command.expire != 'number')) { err = 'Invalid expire time'; } // Check the expire time in minutes
4657:                 else if ((command.start != null) && (typeof command.start != 'number')) { err = 'Invalid start time'; } // Check the start time in UTC seconds
4658:                 else if ((command.end != null) && (typeof command.end != 'number')) { err = 'Invalid end time'; } // Check the end time in UTC seconds
4659:                 else if (common.validateInt(command.consent, 0, 256) == false) { err = 'Invalid flags'; } // Check the flags
4660:                 else if (common.validateInt(command.p, 1, 31) == false) { err = 'Invalid protocol'; } // Check the protocol, 1 = Terminal, 2 = Desktop, 4 = Files, 8 = HTTP, 16 = HTTPS
4661:                 else if ((command.recurring != null) && (common.validateInt(command.recurring, 1, 2) == false)) { err = 'Invalid recurring value'; } // Check the recurring value, 1 = Daily, 2 = Weekly
4662:                 else if ((command.port != null) && (common.validateInt(command.port, 1, 65535) == false)) { err = 'Invalid port value'; } // Check the port if present
4663:                 else if ((command.recurring != null) && ((command.end != null) || (command.start == null) || (command.expire == null))) { err = 'Invalid recurring command'; }
4664:                 else if ((command.expire == null) && ((command.start == null) || (command.end == null) || (command.start > command.end))) { err = 'No time specified'; } // Check that a time range is present
4665:                 else {
4666:                     if (command.nodeid.split('/').length == 1) { command.nodeid = 'node/' + domain.id + '/' + command.nodeid; }
4667:                     var snode = command.nodeid.split('/');
4668:                     if ((snode.length != 3) || (snode[0] != 'node') || (snode[1] != domain.id)) { err = 'Invalid node id'; }
4669:                 }
4670: 
4671:                 // Handle any errors
4672:                 if (err != null) {
4673:                     if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'createDeviceShareLink', responseid: command.responseid, result: err })); } catch (ex) { } }
4674:                     break;
4675:                 }
4676: 
4677:                 // Correct maximum session length if needed
4678:                 if ((typeof domain.guestdevicesharing == 'object') && (typeof domain.guestdevicesharing.maxsessiontime == 'number') && (domain.guestdevicesharing.maxsessiontime > 0)) {
4679:                     const maxtime = domain.guestdevicesharing.maxsessiontime;
4680:                     if ((command.expire != null) && (command.expire > maxtime)) { command.expire = maxtime; }
4681:                     if ((command.start != null) && (command.end != null)) { if ((command.end - command.start) > (maxtime * 60)) { command.end = (command.start + (maxtime * 60)); } }
4682:                 }
4683: 
4684:                 // Get the device rights
4685:                 parent.GetNodeWithRights(domain, user, command.nodeid, function (node, rights, visible) {
4686:                     // If node not found or we don't have remote control, reject.
4687:                     if (node == null) {
4688:                         if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'deviceShares', responseid: command.responseid, result: 'Invalid node id' })); } catch (ex) { } }
4689:                         return;
4690:                     }
4691: 
4692:                     // If there is MESHRIGHT_DESKLIMITEDINPUT or we don't have MESHRIGHT_GUESTSHARING on this account, reject this request.
4693:                     if (rights != MESHRIGHT_ADMIN) {
4694:                         // If we don't have remote control, or have limited input, or don't have guest sharing permission, fail here.
4695:                         if (((rights & MESHRIGHT_REMOTECONTROL) == 0) || ((rights & MESHRIGHT_DESKLIMITEDINPUT) != 0) || ((rights & MESHRIGHT_GUESTSHARING) == 0)) {
4696:                             if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'deviceShares', responseid: command.responseid, result: 'Access denied' })); } catch (ex) { } }
4697:                             return;
4698:                         }
4699:                     }
4700: 
4701:                     // If we are limited to no terminal, don't allow terminal sharing
4702:                     if (((command.p & 1) != 0) && (rights != MESHRIGHT_ADMIN) && ((rights & MESHRIGHT_NOTERMINAL) != 0)) {
4703:                         if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'deviceShares', responseid: command.responseid, result: 'Access denied' })); } catch (ex) { } }
4704:                         return;
4705:                     }
4706: 
4707:                     // If we are limited to no desktop, don't allow desktop sharing
4708:                     if (((command.p & 2) != 0) && (rights != MESHRIGHT_ADMIN) && ((rights & MESHRIGHT_NODESKTOP) != 0)) {
4709:                         if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'deviceShares', responseid: command.responseid, result: 'Access denied' })); } catch (ex) { } }
4710:                         return;
4711:                     }
4712: 
4713:                     // If we are limited to no files, don't allow file sharing
4714:                     if (((command.p & 4) != 0) && (rights != MESHRIGHT_ADMIN) && ((rights & MESHRIGHT_NOFILES) != 0)) {
4715:                         if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'deviceShares', responseid: command.responseid, result: 'Access denied' })); } catch (ex) { } }
4716:                         return;
4717:                     }
4718: 
4719:                     // If we have view only remote desktop rights, force view-only on the guest share.
4720:                     if ((rights != MESHRIGHT_ADMIN) && ((rights & MESHRIGHT_REMOTEVIEWONLY) != 0)) { command.viewOnly = true; }
4721: 
4722:                     // Create cookie
4723:                     var publicid = getRandomPassword(), startTime = null, expireTime = null, duration = null;
4724:                     if (command.recurring) {
4725:                         // Recurring share
4726:                         startTime = command.start * 1000;
4727:                         duration = command.expire;
4728:                     } else if (command.expire != null) {
4729:                         if (command.expire !== 0) {
4730:                             // Now until expire in hours
4731:                             startTime = Date.now();
4732:                             expireTime = Date.now() + (60000 * command.expire);
4733:                         } else {
4734:                             delete command.expire;
4735:                         }
4736:                     } else {
4737:                         // Time range in seconds
4738:                         startTime = command.start * 1000;
4739:                         expireTime = command.end * 1000;
4740:                     }
4741: 
4742:                     //var cookie = { a: 5, p: command.p, uid: user._id, gn: command.guestname, nid: node._id, cf: command.consent, pid: publicid }; // Old style sharing cookie
4743:                     var cookie = { a: 6, pid: publicid }; // New style sharing cookie
4744:                     if ((startTime != null) && (expireTime != null)) { command.start = startTime; command.expire = cookie.e = expireTime; }
4745:                     else if ((startTime != null) && (duration != null)) { command.start = startTime; }
4746:                     const inviteCookie = parent.parent.encodeCookie(cookie, parent.parent.invitationLinkEncryptionKey);
4747:                     if (inviteCookie == null) { if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'createDeviceShareLink', responseid: command.responseid, result: 'Unable to generate shareing cookie' })); } catch (ex) { } } return; }
4748: 
4749:                     // Create the server url
4750:                     var serverName = parent.getWebServerName(domain, req);
4751:                     var httpsPort = ((args.aliasport == null) ? args.port : args.aliasport); // Use HTTPS alias port is specified
4752:                     var xdomain = (domain.dns == null) ? domain.id : '';
4753:                     if (xdomain != '') xdomain += '/';
4754:                     var url = 'https://' + serverName + ':' + httpsPort + '/' + xdomain + 'sharing?c=' + inviteCookie;
4755:                     if (serverName.split('.') == 1) { url = '/' + xdomain + page + '?c=' + inviteCookie; }
4756:                     command.url = url;
4757:                     command.publicid = publicid;
4758:                     if (command.responseid != null) { command.result = 'OK'; }
4759:                     try { ws.send(JSON.stringify(command)); } catch (ex) { }
4760: 
4761:                     // Create a device sharing database entry
4762:                     var shareEntry = { _id: 'deviceshare-' + publicid, type: 'deviceshare', xmeshid: node.meshid, nodeid: node._id, p: command.p, domain: node.domain, publicid: publicid, userid: user._id, guestName: command.guestname, consent: command.consent, port: command.port, url: url };
4763:                     if ((startTime != null) && (expireTime != null)) { shareEntry.startTime = startTime; shareEntry.expireTime = expireTime; }
4764:                     else if ((startTime != null) && (duration != null)) { shareEntry.startTime = startTime; shareEntry.duration = duration; }
4765:                     if (command.recurring) { shareEntry.recurring = command.recurring; }
4766:                     if (command.viewOnly === true) { shareEntry.viewOnly = true; }
4767:                     parent.db.Set(shareEntry);
4768: 
4769:                     // Send out an event that we added a device share
4770:                     var targets = parent.CreateNodeDispatchTargets(node.meshid, node._id, ['server-users', user._id]);
4771:                     var event;
4772:                     if (command.recurring == 1) {
4773:                         event = { etype: 'node', userid: user._id, username: user.name, meshid: node.meshid, nodeid: node._id, action: 'addedDeviceShare', msg: 'Added device share ' + command.guestname + ' recurring daily.', msgid: 138, msgArgs: [command.guestname], domain: domain.id };
4774:                     } else if (command.recurring == 2) {
4775:                         event = { etype: 'node', userid: user._id, username: user.name, meshid: node.meshid, nodeid: node._id, action: 'addedDeviceShare', msg: 'Added device share ' + command.guestname + ' recurring weekly.', msgid: 139, msgArgs: [command.guestname], domain: domain.id };
4776:                     } else if ((startTime != null) && (expireTime != null)) {
4777:                         event = { etype: 'node', userid: user._id, username: user.name, meshid: node.meshid, nodeid: node._id, action: 'addedDeviceShare', msg: 'Added device share: ' + command.guestname + '.', msgid: 101, msgArgs: [command.guestname, 'DATETIME:' + startTime, 'DATETIME:' + expireTime], domain: domain.id };
4778:                     } else {
4779:                         event = { etype: 'node', userid: user._id, username: user.name, meshid: node.meshid, nodeid: node._id, action: 'addedDeviceShare', msg: 'Added device share ' + command.guestname + ' with unlimited time.', msgid: 131, msgArgs: [command.guestname], domain: domain.id };
4780:                     }
4781:                     if(user._id === req.session.userid && req.session.tokenUser != null && req.session.tokenName != null) {
4782:                         event.tokenName = req.session.tokenName; event.tokenUser = req.session.tokenUser;
4783:                     }
4784:                     parent.parent.DispatchEvent(targets, obj, event);
4785: 
4786:                     // Send device share update
4787:                     parent.db.GetAllTypeNodeFiltered([command.nodeid], domain.id, 'deviceshare', null, function (err, docs) {
4788:                         if (err != null) return;
4789: 
4790:                         // Check device sharing
4791:                         var now = Date.now();
4792:                         for (var i = 0; i < docs.length; i++) {
4793:                             const doc = docs[i];
4794:                             if (doc.expireTime < now) { parent.db.Remove(doc._id, function () { }); docs.splice(i--, 1); } else {
4795:                                 // This share is ok, remove extra data we don't need to send.
4796:                                 delete doc._id; delete doc.domain; delete doc.nodeid; delete doc.type; delete doc.xmeshid;
4797:                             }
4798:                         }
4799: 
4800:                         // Send device share update
4801:                         var targets = parent.CreateNodeDispatchTargets(node.meshid, node._id, ['server-users', user._id]);
4802:                         parent.parent.DispatchEvent(targets, obj, { etype: 'node', nodeid: node._id, action: 'deviceShareUpdate', domain: domain.id, deviceShares: docs, nolog: 1 });
4803:                     });
4804:                 });
4805:                 break;
4806:             }
4807:             case 'traceinfo': {
4808:                 // Only accept if the tracing tab is allowed for this domain
4809:                 if ((domain.myserver === false) || ((domain.myserver != null) && (domain.myserver !== true) && (domain.myserver.trace !== true))) break;
4810: 
4811:                 if ((user.siteadmin === SITERIGHT_ADMIN) && (typeof command.traceSources == 'object')) {
4812:                     parent.parent.debugRemoteSources = command.traceSources;
4813:                     parent.parent.DispatchEvent(['*'], obj, { action: 'traceinfo', userid: user._id, username: user.name, traceSources: command.traceSources, nolog: 1, domain: domain.id });
4814:                 }
4815:                 break;
4816:             }
4817:             case 'sendmqttmsg': {
4818:                 if (parent.parent.mqttbroker == null) { err = 'MQTT not supported on this server'; }; // MQTT not available
4819:                 if (common.validateArray(command.nodeids, 1) == false) { err = 'Invalid nodeids'; }; // Check nodeid's
4820:                 if (common.validateString(command.topic, 1, 64) == false) { err = 'Invalid topic'; } // Check the topic
4821:                 if (common.validateString(command.msg, 1, 4096) == false) { err = 'Invalid msg'; } // Check the message
4822: 
4823:                 // Handle any errors
4824:                 if (err != null) {
4825:                     if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'sendmqttmsg', responseid: command.responseid, result: err })); } catch (ex) { } }
4826:                     break;
4827:                 }
4828: 
4829:                 // Send the MQTT message
4830:                 for (i in command.nodeids) {
4831:                     // Get the node and the rights for this node
4832:                     parent.GetNodeWithRights(domain, user, command.nodeids[i], function (node, rights, visible) {
4833:                         // If this device is connected on MQTT, send a wake action.
4834:                         if (rights != 0) {
4835:                             parent.parent.mqttbroker.publish(node._id, command.topic, command.msg);
4836:                         }
4837:                     });
4838:                 }
4839: 
4840:                 break;
4841:             }
4842:             case 'getmqttlogin': {
4843:                 var err = null;
4844:                 if (parent.parent.mqttbroker == null) { err = 'MQTT not supported on this server'; }
4845:                 if (common.validateString(command.nodeid, 1, 1024) == false) { err = 'Invalid nodeid'; } // Check the nodeid
4846: 
4847:                 // Handle any errors
4848:                 if (err != null) { if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'getmqttlogin', responseid: command.responseid, result: err })); } catch (ex) { } } break; }
4849: 
4850:                 // Get the node and the rights for this node
4851:                 parent.GetNodeWithRights(domain, user, command.nodeid, function (node, rights, visible) {
4852:                     // Check if this user has rights to do this
4853:                     if (rights == MESHRIGHT_ADMIN) {
4854:                         var token = parent.parent.mqttbroker.generateLogin(node.meshid, node._id);
4855:                         var r = { action: 'getmqttlogin', responseid: command.responseid, nodeid: node._id, user: token.user, pass: token.pass };
4856:                         const serverName = parent.getWebServerName(domain, req);
4857: 
4858:                         // Add MPS URL
4859:                         if (parent.parent.mpsserver != null) {
4860:                             r.mpsCertHashSha384 = parent.parent.certificateOperations.getCertHash(parent.parent.mpsserver.certificates.mps.cert);
4861:                             r.mpsCertHashSha1 = parent.parent.certificateOperations.getCertHashSha1(parent.parent.mpsserver.certificates.mps.cert);
4862:                             r.mpsUrl = 'mqtts://' + serverName + ':' + ((args.mpsaliasport != null) ? args.mpsaliasport : args.mpsport) + '/';
4863:                         }
4864: 
4865:                         // Add WS URL
4866:                         var xdomain = (domain.dns == null) ? domain.id : '';
4867:                         if (xdomain != '') xdomain += '/';
4868:                         var httpsPort = ((args.aliasport == null) ? args.port : args.aliasport); // Use HTTPS alias port is specified
4869:                         r.wsUrl = 'wss://' + serverName + ':' + httpsPort + '/' + xdomain + 'mqtt.ashx';
4870:                         r.wsTrustedCert = parent.isTrustedCert(domain);
4871: 
4872:                         try { ws.send(JSON.stringify(r)); } catch (ex) { }
4873:                     } else {
4874:                         if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'getmqttlogin', responseid: command.responseid, result: 'Unable to perform this operation' })); } catch (ex) { } }
4875:                     }
4876:                 });
4877:                 break;
4878:             }
4879:             case 'amt': {
4880:                 if (common.validateString(command.nodeid, 1, 1024) == false) break; // Check nodeid
4881:                 if (common.validateInt(command.mode, 0, 3) == false) break; // Check connection mode
4882:                 // Validate if communication mode is possible                
4883:                 if (command.mode == null || command.mode == 0) {
4884:                     break; //unsupported
4885:                 } else if (command.mode == 1) {
4886:                     var state = parent.parent.GetConnectivityState(command.nodeid);
4887:                     if ((state == null) || (state.connectivity & 4) == 0) break;
4888:                 } else if (command.mode == 2) {
4889:                     if (parent.parent.mpsserver.ciraConnections[command.nodeid] == null) break;
4890:                 }
4891:                 /*
4892:                 else if (command.mode == 3) {
4893:                     if (parent.parent.apfserver.apfConnections[command.nodeid] == null) break;
4894:                 }
4895:                 */
4896: 
4897:                 // Get the node and the rights for this node
4898:                 parent.GetNodeWithRights(domain, user, command.nodeid, function (node, rights, visible) {
4899:                     if ((rights & MESHRIGHT_REMOTECONTROL) == 0) return;
4900:                     handleAmtCommand(command, node);
4901:                 });
4902:                 break;
```

## Lines 6321-6442
```text
6321:                 } else if (newuserid.startsWith('ugrp/')) {
6322:                     // Notify user group change
6323:                     var targets = ['*', 'server-ugroups', user._id, newuser._id];
6324:                     var event = { etype: 'ugrp', username: user.name, ugrpid: newuser._id, name: newuser.name, desc: newuser.desc, action: 'usergroupchange', links: newuser.links, msgid: 79, msgArgs: [newuser.name], msg: 'User group changed: ' + newuser.name, domain: domain.id };
6325:                     if(user._id === req.session.userid && req.session.tokenUser != null && req.session.tokenName != null) {
6326:                         event.tokenName = req.session.tokenName; event.tokenUser = req.session.tokenUser;
6327:                     }
6328:                     if (db.changeStream) { event.noact = 1; } // If DB change stream is active, don't use this event to change the user. Another event will come.
6329:                     parent.parent.DispatchEvent(targets, obj, event);
6330:                 }
6331:     
6332:                 var event;
6333:                 if (command.remove === true) {
6334:                     // Remove userid from the mesh
6335:                     delete mesh.links[newuserid];
6336:                     db.Set(mesh);
6337:                     event = { etype: 'mesh', username: newuser.name, userid: user._id, meshid: mesh._id, name: mesh.name, mtype: mesh.mtype, desc: mesh.desc, action: 'meshchange', links: mesh.links, msg: 'Removed user ' + newuser.name + ' from device group ' + mesh.name, domain: domain.id, invite: mesh.invite };
6338:                 } else {
6339:                     // Add userid to the mesh
6340:                     mesh.links[newuserid] = { name: newuser.name, rights: command.meshadmin };
6341:                     db.Set(mesh);
6342:                     event = { etype: 'mesh', username: newuser.name, userid: user._id, meshid: mesh._id, name: mesh.name, mtype: mesh.mtype, desc: mesh.desc, action: 'meshchange', links: mesh.links, msg: 'Added user ' + newuser.name + ' to device group ' + mesh.name, domain: domain.id, invite: mesh.invite };
6343:                 }
6344:                 if(user._id === req.session.userid && req.session.tokenUser != null && req.session.tokenName != null) {
6345:                     event.tokenName = req.session.tokenName; event.tokenUser = req.session.tokenUser;
6346:                 }
6347:     
6348:                 // Notify mesh change
6349:                 if (db.changeStream) { event.noact = 1; } // If DB change stream is active, don't use this event to change the mesh. Another event will come.
6350:                 parent.parent.DispatchEvent(parent.CreateMeshDispatchTargets(mesh, [user._id, newuserid]), obj, event);
6351:                 if (command.remove === true) { msgs.push("Removed user " + newuserid.split('/')[2]); } else { msgs.push("Added user " + newuserid.split('/')[2]); }
6352:                 successCount++;
6353:                 parent.InvalidateNodeCache(newuser, mesh)
6354:             } else {
6355:                 msgs.push("Unknown user " + newuserid.split('/')[2]);
6356:                 unknownUsers.push(newuserid.split('/')[2]);
6357:                 failCount++;
6358:             }
6359:         }
6360:     
6361:         if ((successCount == 0) && (failCount == 0)) { msgs.push("Nothing done"); }
6362:     
6363:         if (unknownUsers.length > 0) {
6364:             // Send error back, user not found.
6365:             displayNotificationMessage('User' + ((unknownUsers.length > 1) ? 's' : '') + ' ' + EscapeHtml(unknownUsers.join(', ')) + ' not found.', "Device Group", 'ServerNotify', 5, (unknownUsers.length > 1) ? 16 : 15, [EscapeHtml(unknownUsers.join(', '))]);
6366:         }
6367:     
6368:         if (command.responseid != null) { obj.send({ action: 'addmeshuser', responseid: command.responseid, result: msgs.join(', '), success: successCount, failed: failCount }); }
6369:     }
6370: 
6371:     function serverCommandAddUser(command) {
6372:         // If the email is the username, set this here.
6373:         if (domain.usernameisemail) { if (command.email) { command.username = command.email; } else { command.email = command.username; } }
6374: 
6375:         // Randomize the password if needed
6376:         if (command.randomPassword === true) { command.pass = getRandomPassword(); }
6377: 
6378:         // Add a new user account
6379:         var err = null, errid = 0, args = null, newusername, newuserid, newuserdomain;
6380:         try {
6381:             if ((user.siteadmin & MESHRIGHT_MANAGEUSERS) == 0) { err = "Permission denied"; errid = 1; }
6382:             else if (common.validateUsername(command.username, 1, 256) == false) { err = "Invalid username"; errid = 2; } // Username is between 1 and 64 characters, no spaces
6383:             else if ((command.username[0] == '~') || (command.username.indexOf('/') >= 0)) { err = "Invalid username"; errid = 2; } // Usernames cant' start with ~ and can't have '/'
6384:             else if (common.validateString(command.pass, 1, 256) == false) { err = "Invalid password"; errid = 3; } // Password is between 1 and 256 characters
6385:             else if ((command.randomPassword !== true) && (common.checkPasswordRequirements(command.pass, domain.passwordrequirements) == false)) { err = "Invalid password"; errid = 3; } // Password does not meet requirements
6386:             else if ((command.email != null) && (common.validateEmail(command.email, 1, 1024) == false)) { err = "Invalid email"; errid = 4; } // Check if this is a valid email address
6387:             else if ((obj.crossDomain === true) && (command.domain != null) && ((typeof command.domain != 'string') || (parent.parent.config.domains[command.domain] == null))) { err = "Invalid domain"; errid = 5; } // Check if this is a valid domain
6388:             else if ((domain.newaccountemaildomains != null) && Array.isArray(domain.newaccountemaildomains) && !common.validateEmailDomain(command.email, domain.newaccountemaildomains)) { err = "Email domain is not allowed. Only (" + domain.newaccountemaildomains.join(', ') + ") are allowed."; errid=30; args = [common.getEmailDomain(command.email), domain.newaccountemaildomains.join(', ')]; }
6389:             else {
6390:                 newuserdomain = domain;
6391:                 if ((obj.crossDomain === true) && (command.domain != null)) { newuserdomain = parent.parent.config.domains[command.domain]; }
6392:                 newusername = command.username;
6393:                 newuserid = 'user/' + newuserdomain.id + '/' + command.username.toLowerCase();
6394:                 if (command.siteadmin != null) {
6395:                     if ((typeof command.siteadmin != 'number') || (Number.isInteger(command.siteadmin) == false)) { err = "Invalid site permissions"; errid = 6; } // Check permissions
6396:                     else if ((user.siteadmin != SITERIGHT_ADMIN) && ((command.siteadmin & (SITERIGHT_ADMIN - 224)) != 0)) { err = "Invalid site permissions"; errid = 6; }
6397:                 }
6398:                 if (parent.users[newuserid]) { err = "User already exists"; errid = 7; } // Account already exists
6399:                 else if ((newuserdomain.auth == 'sspi') || (newuserdomain.auth == 'ldap')) { err = "Unable to add user in this mode"; errid = 8; }
6400:             }
6401:         } catch (ex) { err = "Validation exception"; errid = 9; }
6402: 
6403:         // Handle any errors
6404:         if (err != null) {
6405:             if (command.responseid != null) {
6406:                 obj.send({ action: 'adduser', responseid: command.responseid, result: err, msgid: errid });
6407:             } else {
6408:                 // Send error back, user not found.
6409:                 displayNotificationMessage(err, "New Account", 'ServerNotify', 1, errid, args);
6410:             }
6411:             return;
6412:         }
6413: 
6414:         // Check if we exceed the maximum number of user accounts
6415:         db.isMaxType(newuserdomain.limits.maxuseraccounts, 'user', newuserdomain.id, function (maxExceed) {
6416:             if (maxExceed) {
6417:                 // Account count exceed, do notification
6418:                 if (command.responseid != null) {
6419:                     // Respond privately if requested
6420:                     obj.send({ action: 'adduser', responseid: command.responseid, result: 'maxUsersExceed' });
6421:                 } else {
6422:                     // Create the notification message
6423:                     var notification = { action: 'msg', type: 'notify', id: Math.random(), value: "Account limit reached.", title: "Server Limit", userid: user._id, username: user.name, domain: newuserdomain.id, titleid: 2, msgid: 10 };
6424: 
6425:                     // Get the list of sessions for this user
6426:                     var sessions = parent.wssessions[user._id];
6427:                     if (sessions != null) { for (var i in sessions) { try { if (sessions[i].domainid == newuserdomain.id) { sessions[i].send(JSON.stringify(notification)); } } catch (ex) { } } }
6428:                     // TODO: Notify all sessions on other peers.
6429:                 }
6430:             } else {
6431:                 // Remove any events for this userid
6432:                 if (command.removeEvents === true) { db.RemoveAllUserEvents(newuserdomain.id, newuserid); }
6433: 
6434:                 // Create a new user
6435:                 var newuser = { type: 'user', _id: newuserid, name: newusername, creation: Math.floor(Date.now() / 1000), domain: newuserdomain.id };
6436:                 if (command.siteadmin != null) { newuser.siteadmin = command.siteadmin; }
6437:                 else if (newuserdomain.newaccountsrights) { newuser.siteadmin = newuserdomain.newaccountsrights; }
6438:                 if (command.email != null) { newuser.email = command.email.toLowerCase(); if (command.emailVerified === true) { newuser.emailVerified = true; } } // Email
6439:                 if (command.resetNextLogin === true) { newuser.passchange = -1; } else { newuser.passchange = Math.floor(Date.now() / 1000); }
6440:                 if (user.groups) { newuser.groups = user.groups; } // New accounts are automatically part of our groups (Realms).
6441:                 if (common.validateString(command.realname, 1, 256)) { newuser.realname = command.realname; }
6442:                 if ((command.consent != null) && (typeof command.consent == 'number')) { if (command.consent == 0) { delete chguser.consent; } else { newuser.consent = command.consent; } change = 1; }
```

## Lines 6479-6595
```text
6479: 
6480:                         var event, targets = ['*', 'server-users'];
6481:                         if (newuser.groups) { for (var i in newuser.groups) { targets.push('server-users:' + i); } }
6482:                         if (command.email == null) {
6483:                             event = { etype: 'user', userid: newuser._id, username: newusername, account: parent.CloneSafeUser(newuser), action: 'accountcreate', msgid: 64, msgArgs: [command.username], msg: 'Account created, username is ' + command.username, domain: newuserdomain.id };
6484:                         } else {
6485:                             event = { etype: 'user', userid: newuser._id, username: newusername, account: parent.CloneSafeUser(newuser), action: 'accountcreate', msgid: 65, msgArgs: [command.email.toLowerCase()], msg: 'Account created, email is ' + command.email.toLowerCase(), domain: newuserdomain.id };
6486:                         }
6487:                         if(user._id === req.session.userid && req.session.tokenUser != null && req.session.tokenName != null) {
6488:                             event.tokenName = req.session.tokenName; event.tokenUser = req.session.tokenUser;
6489:                         }
6490:                         if (parent.db.changeStream) { event.noact = 1; } // If DB change stream is active, don't use this event to create the user. Another event will come.
6491:                         parent.parent.DispatchEvent(targets, obj, event);
6492: 
6493:                         // Perform email invitation
6494:                         if ((command.emailInvitation == true) && (command.emailVerified == true) && command.email && domain.mailserver) {
6495:                             domain.mailserver.sendAccountInviteMail(newuserdomain, (user.realname ? user.realname : user.name), newusername, command.email.toLowerCase(), command.pass, parent.getLanguageCodes(req), req.query.key);
6496:                         }
6497: 
6498:                         // Log in the auth log
6499:                         if (parent.parent.authlog) { parent.parent.authLog('https', 'User ' + user.name + ' created a user account ' + newuser.name); }
6500: 
6501:                         // OK Response
6502:                         if (command.responseid != null) { obj.send({ action: 'adduser', responseid: command.responseid, result: 'ok' }); }
6503:                     } else {
6504:                         if (command.responseid != null) { obj.send({ action: 'adduser', responseid: command.responseid, result: 'passwordHashError' }); }
6505:                     }
6506:                 }, 0);
6507:             }
6508:         });
6509:     }
6510: 
6511:     function serverCommandAddUserBatch(command) {
6512:         var err = null;
6513: 
6514:         // Add many new user accounts
6515:         if ((user.siteadmin & 2) == 0) { err = 'Access denied'; }
6516:         else if ((domain.auth == 'sspi') || (domain.auth == 'ldap')) { err = 'Unable to create users when in SSPI or LDAP mode'; }
6517:         else if (!Array.isArray(command.users)) { err = 'Invalid users'; }
6518:         else {
6519:             var userCount = 0;
6520:             for (var i in command.users) {
6521:                 if (domain.usernameisemail) { if (command.users[i].email) { command.users[i].user = command.users[i].email; } else { command.users[i].email = command.users[i].user; } } // If the email is the username, set this here.
6522:                 if (common.validateUsername(command.users[i].user, 1, 256) == false) { err = 'Invalid username'; } // Username is between 1 and 64 characters, no spaces
6523:                 if ((command.users[i].user[0] == '~') || (command.users[i].user.indexOf('/') >= 0)) { err = 'Invalid username'; } // This is a reserved user name or invalid name
6524:                 if (common.validateString(command.users[i].pass, 1, 256) == false) { err = 'Invalid password'; } // Password is between 1 and 256 characters
6525:                 if (common.checkPasswordRequirements(command.users[i].pass, domain.passwordrequirements) == false) { err = 'Invalid password'; } // Password does not meet requirements
6526:                 if ((command.users[i].email != null) && (common.validateEmail(command.users[i].email, 1, 1024) == false)) { err = 'Invalid email'; } // Check if this is a valid email address
6527:                 userCount++;
6528:             }
6529:         }
6530: 
6531:         // Handle any errors
6532:         if (err != null) {
6533:             if (command.responseid != null) { obj.send({ action: 'adduserbatch', responseid: command.responseid, result: err }); }
6534:             return;
6535:         }
6536: 
6537:         // Check if we exceed the maximum number of user accounts
6538:         db.isMaxType(domain.limits.maxuseraccounts + userCount, 'user', domain.id, function (maxExceed) {
6539:             if (maxExceed) {
6540:                 // Account count exceed, do notification
6541: 
6542:                 // Create the notification message
6543:                 var notification = { action: 'msg', type: 'notify', id: Math.random(), value: "Account limit reached.", title: "Server Limit", userid: user._id, username: user.name, domain: domain.id, titleid: 2, msgid: 10 };
6544: 
6545:                 // Get the list of sessions for this user
6546:                 var sessions = parent.wssessions[user._id];
6547:                 if (sessions != null) { for (var i in sessions) { try { if (sessions[i].domainid == domain.id) { sessions[i].send(JSON.stringify(notification)); } } catch (ex) { } } }
6548:                 // TODO: Notify all sessions on other peers.
6549:             } else {
6550:                 for (var i in command.users) {
6551:                     // Check if this is an existing user
6552:                     var newuserid = 'user/' + domain.id + '/' + command.users[i].user.toLowerCase();
6553:                     var newuser = { type: 'user', _id: newuserid, name: command.users[i].user, creation: Math.floor(Date.now() / 1000), domain: domain.id };
6554:                     if (domain.newaccountsrights) { newuser.siteadmin = domain.newaccountsrights; }
6555:                     if (common.validateString(command.users[i].realname, 1, 256)) { newuser.realname = command.users[i].realname; }
6556:                     if (command.users[i].email != null) { newuser.email = command.users[i].email.toLowerCase(); if (command.users[i].emailVerified === true) { newuser.emailVerified = true; } } // Email, always lowercase
6557:                     if (command.users[i].resetNextLogin === true) { newuser.passchange = -1; } else { newuser.passchange = Math.floor(Date.now() / 1000); }
6558:                     if (user.groups) { newuser.groups = user.groups; } // New accounts are automatically part of our groups (Realms).
6559: 
6560:                     if (parent.users[newuserid] == null) {
6561:                         parent.users[newuserid] = newuser;
6562: 
6563:                         // Create a user, generate a salt and hash the password
6564:                         require('./pass').hash(command.users[i].pass, function (err, salt, hash, newuser) {
6565:                             if (err) throw err;
6566:                             newuser.salt = salt;
6567:                             newuser.hash = hash;
6568:                             db.SetUser(newuser);
6569: 
6570:                             var event, targets = ['*', 'server-users'];
6571:                             if (newuser.groups) { for (var i in newuser.groups) { targets.push('server-users:' + i); } }
6572:                             if (newuser.email == null) {
6573:                                 event = { etype: 'user', userid: newuser._id, username: newuser.name, account: parent.CloneSafeUser(newuser), action: 'accountcreate', msgid: 64, msgArgs: [newuser.name], msg: 'Account created, username is ' + newuser.name, domain: domain.id };
6574:                             } else {
6575:                                 event = { etype: 'user', userid: newuser._id, username: newuser.name, account: parent.CloneSafeUser(newuser), action: 'accountcreate', msgid: 65, msgArgs: [newuser.email], msg: 'Account created, email is ' + newuser.email, domain: domain.id };
6576:                             }
6577:                             if(user._id === req.session.userid && req.session.tokenUser != null && req.session.tokenName != null) {
6578:                                 event.tokenName = req.session.tokenName; event.tokenUser = req.session.tokenUser;
6579:                             }
6580:                             if (parent.db.changeStream) { event.noact = 1; } // If DB change stream is active, don't use this event to create the user. Another event will come.
6581:                             parent.parent.DispatchEvent(targets, obj, event);
6582: 
6583:                             // Log in the auth log
6584:                             if (parent.parent.authlog) { parent.parent.authLog('https', 'User ' + user.name + ' created user account ' + newuser.name); }
6585:                         }, newuser);
6586:                     }
6587:                 }
6588:             }
6589:         });
6590:     }
6591: 
6592:     function serverCommandAddUserToUserGroup(command) {
6593:         var err = null;
6594:         try {
6595:             if ((user.siteadmin & SITERIGHT_USERGROUPS) == 0) { err = 'Permission denied'; }
```

## Lines 8681-8804
```text
8681:                     getNodeSysInfoFunc.nodeid = getNodeFunc.nodeid;
8682:                     getNodeSysInfoFunc.node = node;
8683: 
8684:                     // Query the database for system information
8685:                     db.Get('si' + getNodeFunc.nodeid, getNodeSysInfoFunc);
8686:                 } else { resultPendingCount--; }
8687:                 if (resultPendingCount == 0) { func(getNodeFunc.results.join('\r\n'), type); }
8688:             }
8689:             getNodeFunc.results = results;
8690:             getNodeFunc.nodeid = nodeids[i];
8691:             parent.GetNodeWithRights(domain, user, nodeids[i], getNodeFunc);
8692:         }
8693:     }
8694: 
8695:     // Update all device shares for a nodeid list to a new meshid
8696:     // This is used when devices move to a new device group, changes are not evented.
8697:     function changeDeviceShareMeshId(nodes, meshid) {
8698:         parent.db.GetAllTypeNoTypeField('deviceshare', domain.id, function (err, docs) {
8699:             if (err != null) return;
8700:             for (var i = 0; i < docs.length; i++) {
8701:                 const doc = docs[i];
8702:                 if (nodes.indexOf(doc.nodeid) >= 0) {
8703:                     doc.xmeshid = meshid;
8704:                     doc.type = 'deviceshare';
8705:                     db.Set(doc);
8706:                 }
8707:             }
8708:         });
8709:     }
8710: 
8711:     // Return detailed information about all nodes this user has access to
8712:     function getAllDeviceDetailedInfo(type, func) {
8713:         // If we are not paging, get all devices visible to this user
8714:         if (obj.visibleDevices == null) {
8715: 
8716:             // Get all device groups this user has access to
8717:             var links = parent.GetAllMeshIdWithRights(user);
8718: 
8719:             // Add any nodes with direct rights or any nodes with user group direct rights
8720:             var extraids = getUserExtraIds();
8721: 
8722:             // Request a list of all nodes
8723:             db.GetAllTypeNoTypeFieldMeshFiltered(links, extraids, domain.id, 'node', null, obj.deviceSkip, obj.deviceLimit, obj.deviceFilter, function (err, docs) {
8724:                 if (docs == null) { docs = []; }
8725:                 parent.common.unEscapeAllLinksFieldName(docs);
8726: 
8727:                 var results = [], resultPendingCount = 0;
8728:                 if (docs.length == 0) { // no results return blank array
8729:                     func(docs, type);
8730:                 } else {
8731:                     for (i in docs) {
8732:                         // Check device links, if a link points to an unknown user, remove it.
8733:                         parent.cleanDevice(docs[i]);
8734: 
8735:                         // Fetch the node from the database
8736:                         resultPendingCount++;
8737:                         const getNodeFunc = function (node, rights, visible) {
8738:                             if ((node != null) && (visible == true)) {
8739:                                 const getNodeSysInfoFunc = function (err, docs) {
8740:                                     const getNodeNetInfoFunc = function (err, docs) {
8741:                                         var netinfo = null;
8742:                                         if ((err == null) && (docs != null) && (docs.length == 1)) { netinfo = docs[0]; }
8743:                                         resultPendingCount--;
8744:                                         getNodeNetInfoFunc.results.push({ node: parent.CloneSafeNode(getNodeNetInfoFunc.node), sys: getNodeNetInfoFunc.sysinfo, net: netinfo });
8745:                                         if (resultPendingCount == 0) { func(getNodeFunc.results, type); }
8746:                                     }
8747:                                     getNodeNetInfoFunc.results = getNodeSysInfoFunc.results;
8748:                                     getNodeNetInfoFunc.nodeid = getNodeSysInfoFunc.nodeid;
8749:                                     getNodeNetInfoFunc.node = getNodeSysInfoFunc.node;
8750:                                     if ((err == null) && (docs != null) && (docs.length == 1)) { getNodeNetInfoFunc.sysinfo = docs[0]; }
8751: 
8752:                                     // Query the database for network information
8753:                                     db.Get('if' + getNodeSysInfoFunc.nodeid, getNodeNetInfoFunc);
8754:                                 }
8755:                                 getNodeSysInfoFunc.results = getNodeFunc.results;
8756:                                 getNodeSysInfoFunc.nodeid = getNodeFunc.nodeid;
8757:                                 getNodeSysInfoFunc.node = node;
8758: 
8759:                                 // Query the database for system information
8760:                                 db.Get('si' + getNodeFunc.nodeid, getNodeSysInfoFunc);
8761:                             } else { resultPendingCount--; }
8762:                             if (resultPendingCount == 0) { func(getNodeFunc.results.join('\r\n'), type); }
8763:                         }
8764:                         getNodeFunc.results = results;
8765:                         getNodeFunc.nodeid = docs[i]._id;
8766:                         parent.GetNodeWithRights(domain, user, docs[i]._id, getNodeFunc);
8767:                     }
8768:                 }
8769:             });
8770:         } else {
8771:             // If we are paging, we know what devices the user is look at
8772:             for (var id in obj.visibleDevices) {
8773:                 // Fetch the node from the database
8774:                 resultPendingCount++;
8775:                 const getNodeFunc = function (node, rights, visible) {
8776:                     if ((node != null) && (visible == true)) {
8777:                         const getNodeSysInfoFunc = function (err, docs) {
8778:                             const getNodeNetInfoFunc = function (err, docs) {
8779:                                 var netinfo = null;
8780:                                 if ((err == null) && (docs != null) && (docs.length == 1)) { netinfo = docs[0]; }
8781:                                 resultPendingCount--;
8782:                                 getNodeNetInfoFunc.results.push({ node: parent.CloneSafeNode(getNodeNetInfoFunc.node), sys: getNodeNetInfoFunc.sysinfo, net: netinfo });
8783:                                 if (resultPendingCount == 0) { func(getNodeFunc.results, type); }
8784:                             }
8785:                             getNodeNetInfoFunc.results = getNodeSysInfoFunc.results;
8786:                             getNodeNetInfoFunc.nodeid = getNodeSysInfoFunc.nodeid;
8787:                             getNodeNetInfoFunc.node = getNodeSysInfoFunc.node;
8788:                             if ((err == null) && (docs != null) && (docs.length == 1)) { getNodeNetInfoFunc.sysinfo = docs[0]; }
8789: 
8790:                             // Query the database for network information
8791:                             db.Get('if' + getNodeSysInfoFunc.nodeid, getNodeNetInfoFunc);
8792:                         }
8793:                         getNodeSysInfoFunc.results = getNodeFunc.results;
8794:                         getNodeSysInfoFunc.nodeid = getNodeFunc.nodeid;
8795:                         getNodeSysInfoFunc.node = node;
8796: 
8797:                         // Query the database for system information
8798:                         db.Get('si' + getNodeFunc.nodeid, getNodeSysInfoFunc);
8799:                     } else { resultPendingCount--; }
8800:                     if (resultPendingCount == 0) { func(getNodeFunc.results.join('\r\n'), type); }
8801:                 }
8802:                 getNodeFunc.results = results;
8803:                 getNodeFunc.nodeid = id;
8804:                 parent.GetNodeWithRights(domain, user, id, getNodeFunc);
```

