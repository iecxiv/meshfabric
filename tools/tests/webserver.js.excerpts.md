# package/webserver.js

Original source SHA-256: 46afba73577aa380cdc3a2edefc7c2ac4c1c3a4c50f020aefb1a3ce0f4c6420c

## Lines 227-391
```text
227:         if ((comparisonPathNoTrailing.length > 1) && comparisonPathNoTrailing.endsWith(obj.path.sep)) { comparisonPathNoTrailing = comparisonPathNoTrailing.slice(0, -1); }
228:         for (var i = 0; i < safeUploadTempRoots.length; i++) {
229:             var root = safeUploadTempRoots[i];
230:             if ((comparisonPathNoTrailing === root.comparison) || comparisonPath.startsWith(root.comparisonWithSep)) { return resolvedPath; }
231:         }
232:         return null;
233:     }
234: 
235:     // Web relay sessions
236:     var webRelayNextSessionId = 1;
237:     var webRelaySessions = {}                   // UserId/SessionId/Host --> Web Relay Session
238:     var webRelayCleanupTimer = null;
239: 
240:     // Monitor web relay session removals
241:     parent.AddEventDispatch(['server-shareremove'], obj);
242:     obj.HandleEvent = function (source, event, ids, id) {
243:         if (event.action == 'removedDeviceShare') {
244:             for (var relaySessionId in webRelaySessions) {
245:                 // A share was removed that matches an active session, close the web relay session.
246:                 if (webRelaySessions[relaySessionId].xpublicid === event.publicid) { webRelaySessions[relaySessionId].close(); }
247:             }
248:         }
249:     }
250: 
251:     // Mesh Rights
252:     const MESHRIGHT_EDITMESH = 0x00000001;
253:     const MESHRIGHT_MANAGEUSERS = 0x00000002;
254:     const MESHRIGHT_MANAGECOMPUTERS = 0x00000004;
255:     const MESHRIGHT_REMOTECONTROL = 0x00000008;
256:     const MESHRIGHT_AGENTCONSOLE = 0x00000010;
257:     const MESHRIGHT_SERVERFILES = 0x00000020;
258:     const MESHRIGHT_WAKEDEVICE = 0x00000040;
259:     const MESHRIGHT_SETNOTES = 0x00000080;
260:     const MESHRIGHT_REMOTEVIEWONLY = 0x00000100;
261:     const MESHRIGHT_NOTERMINAL = 0x00000200;
262:     const MESHRIGHT_NOFILES = 0x00000400;
263:     const MESHRIGHT_NOAMT = 0x00000800;
264:     const MESHRIGHT_DESKLIMITEDINPUT = 0x00001000;
265:     const MESHRIGHT_LIMITEVENTS = 0x00002000;
266:     const MESHRIGHT_CHATNOTIFY = 0x00004000;
267:     const MESHRIGHT_UNINSTALL = 0x00008000;
268:     const MESHRIGHT_NODESKTOP = 0x00010000;
269:     const MESHRIGHT_REMOTECOMMAND = 0x00020000;
270:     const MESHRIGHT_RESETOFF = 0x00040000;
271:     const MESHRIGHT_GUESTSHARING = 0x00080000;
272:     const MESHRIGHT_DEVICEDETAILS = 0x00100000;
273:     const MESHRIGHT_RELAY = 0x00200000;
274:     const MESHRIGHT_NOREGISTRY = 0x00400000;
275:     const MESHRIGHT_NOSOFTWARE = 0x00800000;
276:     const MESHRIGHT_ADMIN = 0xFFFFFFFF;
277: 
278:     // Site rights
279:     const SITERIGHT_SERVERBACKUP = 0x00000001;
280:     const SITERIGHT_MANAGEUSERS = 0x00000002;
281:     const SITERIGHT_SERVERRESTORE = 0x00000004;
282:     const SITERIGHT_FILEACCESS = 0x00000008;
283:     const SITERIGHT_SERVERUPDATE = 0x00000010;
284:     const SITERIGHT_LOCKED = 0x00000020;
285:     const SITERIGHT_NONEWGROUPS = 0x00000040;
286:     const SITERIGHT_NOMESHCMD = 0x00000080;
287:     const SITERIGHT_USERGROUPS = 0x00000100;
288:     const SITERIGHT_RECORDINGS = 0x00000200;
289:     const SITERIGHT_LOCKSETTINGS = 0x00000400;
290:     const SITERIGHT_ALLEVENTS = 0x00000800;
291:     const SITERIGHT_NONEWDEVICES = 0x00001000;
292:     const SITERIGHT_ADMIN = 0xFFFFFFFF;
293: 
294:     // Setup SSPI authentication if needed
295:     if ((obj.parent.platform == 'win32') && (obj.args.nousers != true) && (obj.parent.config != null) && (obj.parent.config.domains != null)) {
296:         for (i in obj.parent.config.domains) { if (obj.parent.config.domains[i].auth == 'sspi') { var nodeSSPI = require('node-sspi'); obj.parent.config.domains[i].sspi = new nodeSSPI({ retrieveGroups: false, offerBasic: false }); } }
297:     }
298: 
299:     // Perform hash on web certificate and agent certificate
300:     obj.webCertificateHash = parent.certificateOperations.getPublicKeyHashBinary(obj.certificates.web.cert);
301:     obj.webCertificateHashs = { '': obj.webCertificateHash };
302:     obj.webCertificateHashBase64 = Buffer.from(obj.webCertificateHash, 'binary').toString('base64').replace(/\+/g, '@').replace(/\//g, '$');
303:     obj.webCertificateFullHash = parent.certificateOperations.getCertHashBinary(obj.certificates.web.cert);
304:     obj.webCertificateFullHashs = { '': obj.webCertificateFullHash };
305:     obj.webCertificateExpire = { '': parent.certificateOperations.getCertificateExpire(parent.certificates.web.cert) };
306:     obj.agentCertificateHashHex = parent.certificateOperations.getPublicKeyHash(obj.certificates.agent.cert);
307:     obj.agentCertificateHashBase64 = Buffer.from(obj.agentCertificateHashHex, 'hex').toString('base64').replace(/\+/g, '@').replace(/\//g, '$');
308:     obj.agentCertificateAsn1 = parent.certificateOperations.forge.asn1.toDer(parent.certificateOperations.forge.pki.certificateToAsn1(parent.certificateOperations.forge.pki.certificateFromPem(parent.certificates.agent.cert))).getBytes();
309:     obj.defaultWebCertificateHash = obj.certificates.webdefault ? parent.certificateOperations.getPublicKeyHashBinary(obj.certificates.webdefault.cert) : null;
310:     obj.defaultWebCertificateFullHash = obj.certificates.webdefault ? parent.certificateOperations.getCertHashBinary(obj.certificates.webdefault.cert) : null;
311: 
312:     // Compute the hash of all of the web certificates for each domain
313:     for (var i in obj.parent.config.domains) {
314:         if (obj.parent.config.domains[i].certhash != null) {
315:             // If the web certificate hash is provided, use it.
316:             obj.webCertificateHashs[i] = obj.webCertificateFullHashs[i] = Buffer.from(obj.parent.config.domains[i].certhash, 'hex').toString('binary');
317:             if (obj.parent.config.domains[i].certkeyhash != null) { obj.webCertificateHashs[i] = Buffer.from(obj.parent.config.domains[i].certkeyhash, 'hex').toString('binary'); }
318:             delete obj.webCertificateExpire[i]; // Expire time is not provided
319:         } else if ((obj.parent.config.domains[i].dns != null) && (obj.parent.config.domains[i].certs != null)) {
320:             // If the domain has a different DNS name, use a different certificate hash.
321:             // Hash the full certificate
322:             obj.webCertificateFullHashs[i] = parent.certificateOperations.getCertHashBinary(obj.parent.config.domains[i].certs.cert);
323:             obj.webCertificateExpire[i] = Date.parse(parent.certificateOperations.forge.pki.certificateFromPem(obj.parent.config.domains[i].certs.cert).validity.notAfter);
324:             try {
325:                 // Decode a RSA certificate and hash the public key.
326:                 obj.webCertificateHashs[i] = parent.certificateOperations.getPublicKeyHashBinary(obj.parent.config.domains[i].certs.cert);
327:             } catch (ex) {
328:                 // This may be a ECDSA certificate, hash the entire cert.
329:                 obj.webCertificateHashs[i] = obj.webCertificateFullHashs[i];
330:             }
331:         } else if ((obj.parent.config.domains[i].dns != null) && (obj.certificates.dns[i] != null)) {
332:             // If this domain has a DNS and a matching DNS cert, use it. This case works for wildcard certs.
333:             obj.webCertificateFullHashs[i] = parent.certificateOperations.getCertHashBinary(obj.certificates.dns[i].cert);
334:             obj.webCertificateHashs[i] = parent.certificateOperations.getPublicKeyHashBinary(obj.certificates.dns[i].cert);
335:             obj.webCertificateExpire[i] = Date.parse(parent.certificateOperations.forge.pki.certificateFromPem(obj.certificates.dns[i].cert).validity.notAfter);
336:         } else if (i != '') {
337:             // For any other domain, use the default cert.
338:             obj.webCertificateFullHashs[i] = obj.webCertificateFullHashs[''];
339:             obj.webCertificateHashs[i] = obj.webCertificateHashs[''];
340:             obj.webCertificateExpire[i] = obj.webCertificateExpire[''];
341:         }
342:     }
343: 
344:     // If we are running the legacy swarm server, compute the hash for that certificate
345:     if (parent.certificates.swarmserver != null) {
346:         obj.swarmCertificateAsn1 = parent.certificateOperations.forge.asn1.toDer(parent.certificateOperations.forge.pki.certificateToAsn1(parent.certificateOperations.forge.pki.certificateFromPem(parent.certificates.swarmserver.cert))).getBytes();
347:         obj.swarmCertificateHash384 = parent.certificateOperations.forge.pki.getPublicKeyFingerprint(parent.certificateOperations.forge.pki.certificateFromPem(obj.certificates.swarmserver.cert).publicKey, { md: parent.certificateOperations.forge.md.sha384.create(), encoding: 'binary' });
348:         obj.swarmCertificateHash256 = parent.certificateOperations.forge.pki.getPublicKeyFingerprint(parent.certificateOperations.forge.pki.certificateFromPem(obj.certificates.swarmserver.cert).publicKey, { md: parent.certificateOperations.forge.md.sha256.create(), encoding: 'binary' });
349:     }
350: 
351:     // Main lists
352:     obj.wsagents = {};                // NodeId --> Agent
353:     obj.wsagentsWithBadWebCerts = {}; // NodeId --> Agent
354:     obj.wsagentsDisconnections = {};
355:     obj.wsagentsDisconnectionsTimer = null;
356:     obj.duplicateAgentsLog = {};
357:     obj.wssessions = {};              // UserId --> Array Of Sessions
358:     obj.wssessions2 = {};             // "UserId + SessionRnd" --> Session  (Note that the SessionId is the UserId + / + SessionRnd)
359:     obj.wsPeerSessions = {};          // ServerId --> Array Of "UserId + SessionRnd"
360:     obj.wsPeerSessions2 = {};         // "UserId + SessionRnd" --> ServerId
361:     obj.wsPeerSessions3 = {};         // ServerId --> UserId --> [ SessionId ]
362:     obj.sessionsCount = {};           // Merged session counters, used when doing server peering. UserId --> SessionCount
363:     obj.wsrelays = {};                // Id -> Relay
364:     obj.desktoprelays = {};           // Id -> Desktop Multiplexer Relay
365:     obj.wsPeerRelays = {};            // Id -> { ServerId, Time }
366:     var tlsSessionStore = {};         // Store TLS session information for quick resume.
367:     var tlsSessionStoreCount = 0;     // Number of cached TLS session information in store.
368: 
369:     // Setup randoms
370:     obj.crypto.randomBytes(48, function (err, buf) { obj.httpAuthRandom = buf; });
371:     obj.crypto.randomBytes(16, function (err, buf) { obj.httpAuthRealm = buf.toString('hex'); });
372:     obj.crypto.randomBytes(48, function (err, buf) { obj.relayRandom = buf; });
373: 
374:     // Get non-english web pages and emails
375:     getRenderList();
376:     getEmailLanguageList();
377: 
378:     // Setup DNS domain TLS SNI credentials
379:     {
380:         var dnscount = 0;
381:         obj.tlsSniCredentials = {};
382:         for (i in obj.certificates.dns) { if (obj.parent.config.domains[i].dns != null) { obj.dnsDomains[obj.parent.config.domains[i].dns.toLowerCase()] = obj.parent.config.domains[i]; obj.tlsSniCredentials[obj.parent.config.domains[i].dns] = obj.tls.createSecureContext(obj.certificates.dns[i]).context; dnscount++; } }
383:         if (dnscount > 0) { obj.tlsSniCredentials[''] = obj.tls.createSecureContext({ cert: obj.certificates.web.cert, key: obj.certificates.web.key, ca: obj.certificates.web.ca }).context; } else { obj.tlsSniCredentials = null; }
384:     }
385:     function TlsSniCallback(name, cb) {
386:         var c = obj.tlsSniCredentials[name];
387:         if (c != null) {
388:             cb(null, c);
389:         } else {
390:             cb(null, obj.tlsSniCredentials['']);
391:         }
```

## Lines 2326-2459
```text
2326:                                             render(req, res, getRenderPage((domain.sitestyle >= 2) ? 'message2' : 'message', req, domain), getRenderArgs({ titleid: 1, msgid: 14, domainurl: encodeURIComponent(domain.url).replace(/'/g, '%27') }, req, domain));
2327:                                         }
2328:                                     }
2329:                                 } else {
2330:                                     render(req, res, getRenderPage((domain.sitestyle >= 2) ? 'message2' : 'message', req, domain), getRenderArgs({ titleid: 1, msgid: 9, domainurl: encodeURIComponent(domain.url).replace(/'/g, '%27') }, req, domain));
2331:                                 }
2332:                             }
2333:                         }
2334:                 });
2335:                 }
2336:             } else {
2337:                 render(req, res, getRenderPage((domain.sitestyle >= 2) ? 'message2' : 'message', req, domain), getRenderArgs({ titleid: 1, msgid: 10, domainurl: encodeURIComponent(domain.url).replace(/'/g, '%27') }, req, domain));
2338:             }
2339:         }
2340:     }
2341: 
2342:     // Called to process an agent invite GET/POST request
2343:     function handleInviteRequest(req, res) {
2344:         const domain = getDomain(req);
2345:         if (domain == null) { parent.debug('web', 'handleInviteRequest: failed checks.'); res.sendStatus(404); return; }
2346:         if (domain.agentinvitecodes != true) { nice404(req, res); return; }
2347:         if ((domain.loginkey != null) && (domain.loginkey.indexOf(req.query.key) == -1)) { res.sendStatus(404); return; } // Check 3FA URL key
2348:         if ((req.body == null) || (req.body.inviteCode == null) || (req.body.inviteCode == '')) { render(req, res, getRenderPage('invite', req, domain), getRenderArgs({ messageid: 0 }, req, domain)); return; } // No invitation code
2349: 
2350:         // Each for a device group that has this invite code.
2351:         for (var i in obj.meshes) {
2352:             if ((obj.meshes[i].domain == domain.id) && (obj.meshes[i].deleted == null) && (obj.meshes[i].invite != null) && (obj.meshes[i].invite.codes.indexOf(req.body.inviteCode) >= 0)) {
2353:                 // Send invitation link, valid for 1 minute.
2354:                 res.redirect(domain.url + 'agentinvite?c=' + parent.encodeCookie({ a: 4, mid: i, f: obj.meshes[i].invite.flags, ag: obj.meshes[i].invite.ag, expire: 1 }, parent.invitationLinkEncryptionKey) + (req.query.key ? ('&key=' + encodeURIComponent(req.query.key)) : '') + (req.query.hide ? ('&hide=' + encodeURIComponent(req.query.hide)) : ''));
2355:                 return;
2356:             }
2357:         }
2358: 
2359:         render(req, res, getRenderPage('invite', req, domain), getRenderArgs({ messageid: 100 }, req, domain)); // Bad invitation code
2360:     }
2361: 
2362:     // Called to render the MSTSC (RDP) or SSH web page
2363:     function handleMSTSCRequest(req, res, page) {
2364:         const domain = getDomain(req);
2365:         if (domain == null) { parent.debug('web', 'handleMSTSCRequest: failed checks.'); res.sendStatus(404); return; }
2366:         if ((domain.loginkey != null) && (domain.loginkey.indexOf(req.query.key) == -1)) { res.sendStatus(404); return; } // Check 3FA URL key
2367: 
2368:         // Check if we are in maintenance mode
2369:         if ((parent.config.settings.maintenancemode != null) && (req.query.loginscreen !== '1')) {
2370:             render(req, res, getRenderPage((domain.sitestyle >= 2) ? 'message2' : 'message', req, domain), getRenderArgs({ titleid: 3, msgid: 13, domainurl: encodeURIComponent(domain.url).replace(/'/g, '%27') }, req, domain));
2371:             return;
2372:         }
2373: 
2374:         // Set features we want to send to this page
2375:         var features = 0;
2376:         if (domain.allowsavingdevicecredentials === false) { features |= 1; }
2377: 
2378:         // Get the logged in user if present
2379:         var user = null;
2380: 
2381:         // If there is a login token, use that
2382:         if (req.query.login != null) {
2383:             var ucookie = parent.decodeCookie(req.query.login, parent.loginCookieEncryptionKey, 60); // Cookie with 1 hour timeout
2384:             if ((ucookie != null) && (ucookie.a === 3) && (typeof ucookie.u == 'string')) { user = obj.users[ucookie.u]; }
2385:         }
2386: 
2387:         // If no token, see if we have an active session
2388:         if ((user == null) && (req.session.userid != null)) { user = obj.users[req.session.userid]; }
2389: 
2390:         // If still no user, see if we have a default user
2391:         if ((user == null) && (obj.args.user)) { user = obj.users['user/' + domain.id + '/' + obj.args.user.toLowerCase()]; }
2392: 
2393:         // No user login, exit now
2394:         if (user == null) { res.sendStatus(401); return; }
2395: 
2396:         if (req.query.ws != null) {
2397:             // This is a query with a websocket relay cookie, check that the cookie is valid and use it.
2398:             var rcookie = parent.decodeCookie(req.query.ws, parent.loginCookieEncryptionKey, 60); // Cookie with 1 hour timeout
2399:             if ((rcookie != null) && (rcookie.domainid == domain.id) && (rcookie.nodeid != null) && (rcookie.tcpport != null)) {
2400: 
2401:                 // Fetch the node from the database
2402:                 obj.db.Get(rcookie.nodeid, function (err, nodes) {
2403:                     if ((err != null) || (nodes.length != 1)) { res.sendStatus(404); return; }
2404:                     const node = nodes[0];
2405: 
2406:                     // Check if we have SSH/RDP credentials for this device
2407:                     var serverCredentials = 0;
2408:                     if (domain.allowsavingdevicecredentials !== false) {
2409:                         if (page == 'ssh') {
2410:                             if ((typeof node.ssh == 'object') && (typeof node.ssh.u == 'string') && (typeof node.ssh.p == 'string')) { serverCredentials = 1; } // Username and password
2411:                             else if ((typeof node.ssh == 'object') && (typeof node.ssh.k == 'string') && (typeof node.ssh.kp == 'string')) { serverCredentials = 2; } // Username, key and password
2412:                             else if ((typeof node.ssh == 'object') && (typeof node.ssh.k == 'string')) { serverCredentials = 3; } // Username and key. No password.
2413:                             else if ((typeof node.ssh == 'object') && (typeof node.ssh[user._id] == 'object') && (typeof node.ssh[user._id].u == 'string') && (typeof node.ssh[user._id].p == 'string')) { serverCredentials = 1; } // Username and password in per user format
2414:                             else if ((typeof node.ssh == 'object') && (typeof node.ssh[user._id] == 'object') && (typeof node.ssh[user._id].k == 'string') && (typeof node.ssh[user._id].kp == 'string')) { serverCredentials = 2; } // Username, key and password in per user format
2415:                             else if ((typeof node.ssh == 'object') && (typeof node.ssh[user._id] == 'object') && (typeof node.ssh[user._id].k == 'string')) { serverCredentials = 3; } // Username and key. No password. in per user format
2416:                         } else {
2417:                             if ((typeof node.rdp == 'object') && (typeof node.rdp.d == 'string') && (typeof node.rdp.u == 'string') && (typeof node.rdp.p == 'string')) { serverCredentials = 1; } // Username and password in legacy format
2418:                             if ((typeof node.rdp == 'object') && (typeof node.rdp[user._id] == 'object') && (typeof node.rdp[user._id].d == 'string') && (typeof node.rdp[user._id].u == 'string') && (typeof node.rdp[user._id].p == 'string')) { serverCredentials = 1; } // Username and password in per user format
2419:                         }
2420:                     }
2421: 
2422:                     // Render the page
2423:                     render(req, res, getRenderPage(page, req, domain), getRenderArgs({ cookie: req.query.ws, name: encodeURIComponent(req.query.name).replace(/'/g, '%27'), serverCredentials: serverCredentials, features: features }, req, domain));
2424:                 });
2425:                 return;
2426:             }
2427:         }
2428: 
2429:         // Check the nodeid
2430:         if (req.query.node != null) {
2431:             var nodeidsplit = req.query.node.split('/');
2432:             if (nodeidsplit.length == 1) {
2433:                 req.query.node = 'node/' + domain.id + '/' + nodeidsplit[0]; // Format the nodeid correctly
2434:             } else if (nodeidsplit.length == 3) {
2435:                 if ((nodeidsplit[0] != 'node') || (nodeidsplit[1] != domain.id)) { req.query.node = null; } // Check the nodeid format
2436:             } else {
2437:                 req.query.node = null; // Bad nodeid
2438:             }
2439:         }
2440: 
2441:         // If there is no nodeid, exit now
2442:         if (req.query.node == null) { render(req, res, getRenderPage(page, req, domain), getRenderArgs({ cookie: '', name: '', features: features }, req, domain)); return; }
2443: 
2444:         // Fetch the node from the database
2445:         obj.db.Get(req.query.node, function (err, nodes) {
2446:             if ((err != null) || (nodes.length != 1)) { res.sendStatus(404); return; }
2447:             const node = nodes[0];
2448: 
2449:             // Check access rights, must have remote control rights
2450:             if ((obj.GetNodeRights(user, node.meshid, node._id) & MESHRIGHT_REMOTECONTROL) == 0) { res.sendStatus(401); return; }
2451: 
2452:             // Figure out the target port
2453:             var port = 0, serverCredentials = false;
2454:             if (page == 'ssh') {
2455:                 // SSH port
2456:                 port = 22;
2457:                 if (typeof node.sshport == 'number') { port = node.sshport; }
2458: 
2459:                 // Check if we have SSH credentials for this device
```

## Lines 2504-2746
```text
2504:         parent.debug('email', 'handleFirebasePushOnlyRelayRequest - ok');
2505:         obj.parent.firebase.sendToDevice({ pmt: data.pmt }, data.payload, data.options, function (id, err, errdesc) {
2506:             if (err == null) { res.sendStatus(200); } else { res.sendStatus(500); }
2507:         });
2508:     }
2509: 
2510:     // Called to handle two-way push notification relay request
2511:     function handleFirebaseRelayRequest(ws, req) {
2512:         parent.debug('email', 'handleFirebaseRelayRequest');
2513:         if (obj.parent.firebase == null) { try { ws.close(); } catch (e) { } return; }
2514:         if (obj.parent.firebase.setupRelay == null) { try { ws.close(); } catch (e) { } return; }
2515:         if (obj.parent.config.firebase.relayserver == null) { try { ws.close(); } catch (e) { } return; }
2516:         if ((typeof obj.parent.config.firebase.relayserver == 'string') && (req.query.key != obj.parent.config.firebase.relayserver)) { res.sendStatus(404); try { ws.close(); } catch (e) { } return; }
2517:         obj.parent.firebase.setupRelay(ws);
2518:     }
2519: 
2520:     // Called to process an agent invite request
2521:     function handleAgentInviteRequest(req, res) {
2522:         const domain = getDomain(req);
2523:         if ((domain == null) || ((req.query.m == null) && (req.query.c == null))) { parent.debug('web', 'handleAgentInviteRequest: failed checks.'); res.sendStatus(404); return; }
2524:         if ((domain.loginkey != null) && (domain.loginkey.indexOf(req.query.key) == -1)) { res.sendStatus(404); return; } // Check 3FA URL key
2525: 
2526:         if (req.query.c != null) {
2527:             // A cookie is specified in the query string, use that
2528:             var cookie = obj.parent.decodeCookie(req.query.c, obj.parent.invitationLinkEncryptionKey);
2529:             if (cookie == null) { res.sendStatus(404); return; }
2530:             var mesh = obj.meshes[cookie.mid];
2531:             if (mesh == null) { res.sendStatus(404); return; }
2532:             var installflags = cookie.f;
2533:             if (typeof installflags != 'number') { installflags = 0; }
2534:             var showagents = cookie.ag;
2535:             if (typeof showagents != 'number') { showagents = 0; }
2536:             parent.debug('web', 'handleAgentInviteRequest using cookie.');
2537: 
2538:             // Build the mobile agent URL, this is used to connect mobile devices
2539:             var agentServerName = obj.getWebServerName(domain, req);
2540:             if (typeof obj.args.agentaliasdns == 'string') { agentServerName = obj.args.agentaliasdns; }
2541:             var xdomain = (domain.dns == null) ? domain.id : '';
2542:             var agentHttpsPort = ((obj.args.aliasport == null) ? obj.args.port : obj.args.aliasport); // Use HTTPS alias port is specified
2543:             if (obj.args.agentport != null) { agentHttpsPort = obj.args.agentport; } // If an agent only port is enabled, use that.
2544:             if (obj.args.agentaliasport != null) { agentHttpsPort = obj.args.agentaliasport; } // If an agent alias port is specified, use that.
2545:             var magenturl = 'mc://' + agentServerName + ((agentHttpsPort != 443) ? (':' + agentHttpsPort) : '') + ((xdomain != '') ? ('/' + xdomain) : '') + ',' + obj.agentCertificateHashBase64 + ',' + mesh._id.split('/')[2];
2546: 
2547:             var meshcookie = parent.encodeCookie({ m: mesh._id.split('/')[2] }, parent.invitationLinkEncryptionKey);
2548:             render(req, res, getRenderPage('agentinvite', req, domain), getRenderArgs({ meshid: meshcookie, serverport: ((args.aliasport != null) ? args.aliasport : args.port), serverhttps: 1, servernoproxy: ((domain.agentnoproxy === true) ? '1' : '0'), meshname: encodeURIComponent(mesh.name).replace(/'/g, '%27'), installflags: installflags, showagents: showagents, magenturl: magenturl, assistanttype: (domain.assistanttypeagentinvite ? domain.assistanttypeagentinvite : 0) }, req, domain));
2549:         } else if (req.query.m != null) {
2550:             // The MeshId is specified in the query string, use that
2551:             var mesh = obj.meshes['mesh/' + domain.id + '/' + req.query.m.toLowerCase()];
2552:             if (mesh == null) { res.sendStatus(404); return; }
2553:             var installflags = 0;
2554:             if (req.query.f) { installflags = parseInt(req.query.f); }
2555:             if (typeof installflags != 'number') { installflags = 0; }
2556:             var showagents = 0;
2557:             if (req.query.f) { showagents = parseInt(req.query.ag); }
2558:             if (typeof showagents != 'number') { showagents = 0; }
2559:             parent.debug('web', 'handleAgentInviteRequest using meshid.');
2560: 
2561:             // Build the mobile agent URL, this is used to connect mobile devices
2562:             var agentServerName = obj.getWebServerName(domain, req);
2563:             if (typeof obj.args.agentaliasdns == 'string') { agentServerName = obj.args.agentaliasdns; }
2564:             var xdomain = (domain.dns == null) ? domain.id : '';
2565:             var agentHttpsPort = ((obj.args.aliasport == null) ? obj.args.port : obj.args.aliasport); // Use HTTPS alias port is specified
2566:             if (obj.args.agentport != null) { agentHttpsPort = obj.args.agentport; } // If an agent only port is enabled, use that.
2567:             if (obj.args.agentaliasport != null) { agentHttpsPort = obj.args.agentaliasport; } // If an agent alias port is specified, use that.
2568:             var magenturl = 'mc://' + agentServerName + ((agentHttpsPort != 443) ? (':' + agentHttpsPort) : '') + ((xdomain != '') ? ('/' + xdomain) : '') + ',' + obj.agentCertificateHashBase64 + ',' + mesh._id.split('/')[2];
2569: 
2570:             var meshcookie = parent.encodeCookie({ m: mesh._id.split('/')[2] }, parent.invitationLinkEncryptionKey);
2571:             render(req, res, getRenderPage('agentinvite', req, domain), getRenderArgs({ meshid: meshcookie, serverport: ((args.aliasport != null) ? args.aliasport : args.port), serverhttps: 1, servernoproxy: ((domain.agentnoproxy === true) ? '1' : '0'), meshname: encodeURIComponent(mesh.name).replace(/'/g, '%27'), installflags: installflags, showagents: showagents, magenturl: magenturl, assistanttype: (domain.assistanttypeagentinvite ? domain.assistanttypeagentinvite : 0) }, req, domain));
2572:         }
2573:     }
2574: 
2575:     // Called to process an agent invite request
2576:     function handleUserImageRequest(req, res) {
2577:         const domain = getDomain(req);
2578:         if (domain == null) { parent.debug('web', 'handleUserImageRequest: failed checks.'); res.sendStatus(404); return; }
2579:         if ((req.session == null) || (req.session.userid == null)) { parent.debug('web', 'handleUserImageRequest: failed checks 2.'); res.sendStatus(404); return; }
2580:         var imageUserId = req.session.userid;
2581:         if ((req.query.id != null)) {
2582:             var user = obj.users[req.session.userid];
2583:             if ((user == null) || (user.siteadmin == null) && ((user.siteadmin & 2) == 0)) { res.sendStatus(404); return; }
2584:             imageUserId = 'user/' + domain.id + '/' + req.query.id;
2585:         }
2586:         obj.db.Get('im' + imageUserId, function (err, docs) {
2587:             if ((err != null) || (docs == null) || (docs.length != 1) || (typeof docs[0].image != 'string')) { res.sendStatus(404); return; }
2588:             var imagebase64 = docs[0].image;
2589:             if (imagebase64.startsWith('data:image/png;base64,')) {
2590:                 res.set('Content-Type', 'image/png');
2591:                 res.set({ 'Cache-Control': 'no-store' });
2592:                 res.send(Buffer.from(imagebase64.substring(22), 'base64'));
2593:             } else if (imagebase64.startsWith('data:image/jpeg;base64,')) {
2594:                 res.set('Content-Type', 'image/jpeg');
2595:                 res.set({ 'Cache-Control': 'no-store' });
2596:                 res.send(Buffer.from(imagebase64.substring(23), 'base64'));
2597:             } else {
2598:                 res.sendStatus(404);
2599:             }
2600:         });
2601:     }
2602: 
2603:     function handleDeleteAccountRequest(req, res, direct) {
2604:         parent.debug('web', 'handleDeleteAccountRequest()');
2605:         const domain = checkUserIpAddress(req, res);
2606:         if (domain == null) { return; }
2607:         if ((domain.auth == 'sspi') || (domain.auth == 'ldap')) { parent.debug('web', 'handleDeleteAccountRequest: failed checks.'); res.sendStatus(404); return; }
2608:         if ((domain.loginkey != null) && (domain.loginkey.indexOf(req.query.key) == -1)) { res.sendStatus(404); return; } // Check 3FA URL key
2609:         if (req.session.loginToken != null) { res.sendStatus(404); return; } // Do not allow this command when logged in using a login token
2610:         if (req.body == null) { res.sendStatus(404); return; } // Post body is empty or can't be parsed
2611: 
2612:         var user = null;
2613:         if (req.body.authcookie) {
2614:             // If a authentication cookie is provided, decode it here
2615:             var loginCookie = obj.parent.decodeCookie(req.body.authcookie, obj.parent.loginCookieEncryptionKey, 60); // 60 minute timeout
2616:             if ((loginCookie != null) && (domain.id == loginCookie.domainid)) { user = obj.users[loginCookie.userid]; }
2617:         } else {
2618:             // Check if the user is logged and we have all required parameters
2619:             if (!req.session || !req.session.userid || !req.body.apassword1 || (req.body.apassword1 != req.body.apassword2) || (req.session.userid.split('/')[1] != domain.id)) {
2620:                 parent.debug('web', 'handleDeleteAccountRequest: required parameters not present.');
2621:                 if (direct === true) { handleRootRequestEx(req, res, domain); } else { res.redirect(domain.url + getQueryPortion(req)); }
2622:                 return;
2623:             } else {
2624:                 user = obj.users[req.session.userid];
2625:             }
2626:         }
2627:         if (!user) { parent.debug('web', 'handleDeleteAccountRequest: user not found.'); res.sendStatus(404); return; }
2628:         if ((user.siteadmin != 0xFFFFFFFF) && ((user.siteadmin & 1024) != 0)) { parent.debug('web', 'handleDeleteAccountRequest: account settings locked.'); res.sendStatus(404); return; }
2629: 
2630:         // Check if the password is correct
2631:         obj.authenticate(user._id.split('/')[2], req.body.apassword1, domain, function (err, userid, passhint, loginOptions) {
2632:             var deluser = obj.users[userid];
2633:             if ((userid != null) && (deluser != null)) {
2634:                 // Remove all links to this user
2635:                 if (deluser.links != null) {
2636:                     for (var i in deluser.links) {
2637:                         if (i.startsWith('mesh/')) {
2638:                             // Get the device group
2639:                             var mesh = obj.meshes[i];
2640:                             if (mesh) {
2641:                                 // Remove user from the mesh
2642:                                 if (mesh.links[deluser._id] != null) { delete mesh.links[deluser._id]; parent.db.Set(mesh); }
2643: 
2644:                                 // Notify mesh change
2645:                                 var change = 'Removed user ' + deluser.name + ' from group ' + mesh.name;
2646:                                 var event = { etype: 'mesh', userid: user._id, username: user.name, meshid: mesh._id, name: mesh.name, mtype: mesh.mtype, desc: mesh.desc, action: 'meshchange', links: mesh.links, msg: change, domain: domain.id, invite: mesh.invite };
2647:                                 if (db.changeStream) { event.noact = 1; } // If DB change stream is active, don't use this event to change the mesh. Another event will come.
2648:                                 parent.DispatchEvent(['*', mesh._id, deluser._id, user._id], obj, event);
2649:                             }
2650:                         } else if (i.startsWith('node/')) {
2651:                             // Get the node and the rights for this node
2652:                             obj.GetNodeWithRights(domain, deluser, i, function (node, rights, visible) {
2653:                                 if ((node == null) || (node.links == null) || (node.links[deluser._id] == null)) return;
2654: 
2655:                                 // Remove the link and save the node to the database
2656:                                 delete node.links[deluser._id];
2657:                                 if (Object.keys(node.links).length == 0) { delete node.links; }
2658:                                 db.Set(obj.cleanDevice(node));
2659: 
2660:                                 // Event the node change
2661:                                 var event = { etype: 'node', userid: user._id, username: user.name, action: 'changenode', nodeid: node._id, domain: domain.id, msg: ('Removed user device rights for ' + node.name), node: obj.CloneSafeNode(node) }
2662:                                 if (db.changeStream) { event.noact = 1; } // If DB change stream is active, don't use this event to change the mesh. Another event will come.
2663:                                 parent.DispatchEvent(['*', node.meshid, node._id], obj, event);
2664:                             });
2665:                         } else if (i.startsWith('ugrp/')) {
2666:                             // Get the device group
2667:                             var ugroup = obj.userGroups[i];
2668:                             if (ugroup) {
2669:                                 // Remove user from the user group
2670:                                 if (ugroup.links[deluser._id] != null) { delete ugroup.links[deluser._id]; parent.db.Set(ugroup); }
2671: 
2672:                                 // Notify user group change
2673:                                 var change = 'Removed user ' + deluser.name + ' from user group ' + ugroup.name;
2674:                                 var event = { etype: 'ugrp', userid: user._id, username: user.name, ugrpid: ugroup._id, name: ugroup.name, desc: ugroup.desc, action: 'usergroupchange', links: ugroup.links, msg: 'Removed user ' + deluser.name + ' from user group ' + ugroup.name, addUserDomain: domain.id };
2675:                                 if (db.changeStream) { event.noact = 1; } // If DB change stream is active, don't use this event to change the user group. Another event will come.
2676:                                 parent.DispatchEvent(['*', ugroup._id, user._id, deluser._id], obj, event);
2677:                             }
2678:                         }
2679:                     }
2680:                 }
2681: 
2682:                 obj.db.Remove('ws' + deluser._id);  // Remove user web state
2683:                 obj.db.Remove('nt' + deluser._id);  // Remove notes for this user
2684:                 obj.db.Remove('ntp' + deluser._id); // Remove personal notes for this user
2685:                 obj.db.Remove('im' + deluser._id);  // Remove image for this user
2686: 
2687:                 // Delete any login tokens
2688:                 parent.db.GetAllTypeNodeFiltered(['logintoken-' + deluser._id], domain.id, 'logintoken', null, function (err, docs) {
2689:                     if ((err == null) && (docs != null)) { for (var i = 0; i < docs.length; i++) { parent.db.Remove(docs[i]._id, function () { }); } }
2690:                 });
2691: 
2692:                 // Delete all files on the server for this account
2693:                 try {
2694:                     var deluserpath = obj.getServerRootFilePath(deluser);
2695:                     if (deluserpath != null) { obj.deleteFolderRec(deluserpath); }
2696:                 } catch (e) { }
2697: 
2698:                 // Remove the user
2699:                 obj.db.Remove(deluser._id);
2700:                 delete obj.users[deluser._id];
2701:                 req.session = null;
2702:                 if (direct === true) { handleRootRequestEx(req, res, domain); } else { res.redirect(domain.url + getQueryPortion(req)); }
2703:                 obj.parent.DispatchEvent(['*', 'server-users'], obj, { etype: 'user', userid: deluser._id, username: deluser.name, action: 'accountremove', msg: 'Account removed', domain: domain.id });
2704:                 parent.debug('web', 'handleDeleteAccountRequest: removed user.');
2705:             } else {
2706:                 parent.debug('web', 'handleDeleteAccountRequest: auth failed.');
2707:                 if (direct === true) { handleRootRequestEx(req, res, domain); } else { res.redirect(domain.url + getQueryPortion(req)); }
2708:             }
2709:         });
2710:     }
2711: 
2712:     // Check a user's password
2713:     obj.checkUserPassword = function (domain, user, password, func) {
2714:         // Check the old password
2715:         if (user.passtype != null) {
2716:             // IIS default clear or weak password hashing (SHA-1)
2717:             require('./pass').iishash(user.passtype, password, user.salt, function (err, hash) {
2718:                 if (err) { parent.debug('web', 'checkUserPassword: SHA-1 fail.'); return func(false); }
2719:                 if (hash == user.hash) {
2720:                     if ((user.siteadmin) && (user.siteadmin != 0xFFFFFFFF) && (user.siteadmin & 32) != 0) { parent.debug('web', 'checkUserPassword: SHA-1 locked.'); return func(false); } // Account is locked
2721:                     parent.debug('web', 'checkUserPassword: SHA-1 ok.');
2722:                     return func(true); // Allow password change
2723:                 }
2724:                 func(false);
2725:             });
2726:         } else {
2727:             // Default strong password hashing (pbkdf2 SHA384)
2728:             require('./pass').hash(password, user.salt, function (err, hash, tag) {
2729:                 if (err) { parent.debug('web', 'checkUserPassword: pbkdf2 SHA384 fail.'); return func(false); }
2730:                 if (hash == user.hash) {
2731:                     if ((user.siteadmin) && (user.siteadmin != 0xFFFFFFFF) && (user.siteadmin & 32) != 0) { parent.debug('web', 'checkUserPassword: pbkdf2 SHA384 locked.'); return func(false); } // Account is locked
2732:                     parent.debug('web', 'checkUserPassword: pbkdf2 SHA384 ok.');
2733:                     return func(true); // Allow password change
2734:                 }
2735:                 func(false);
2736:             }, 0);
2737:         }
2738:     }
2739: 
2740:     // Check a user's old passwords
2741:     // Callback: 0=OK, 1=OldPass, 2=CommonPass
2742:     obj.checkOldUserPasswords = function (domain, user, password, func) {
2743:         // Check how many old passwords we need to check
2744:         if ((domain.passwordrequirements != null) && (typeof domain.passwordrequirements.oldpasswordban == 'number') && (domain.passwordrequirements.oldpasswordban > 0)) {
2745:             if (user.oldpasswords != null) {
2746:                 const extraOldPasswords = user.oldpasswords.length - domain.passwordrequirements.oldpasswordban;
```

## Lines 3538-3681
```text
3538:             }
3539:             handleRootRequestLogin(req, res, domain, '', passRequirements);
3540:         }
3541:     }
3542: 
3543:     // Return a list of server supported features for a given domain and user
3544:     obj.getDomainUserFeatures = function (domain, user, req) {
3545:         var features = 0;
3546:         var features2 = 0;
3547:         var features3 = 0;
3548:         if (obj.args.wanonly == true) { features += 0x00000001; } // WAN-only mode
3549:         if (obj.args.lanonly == true) { features += 0x00000002; } // LAN-only mode
3550:         if (obj.args.nousers == true) { features += 0x00000004; } // Single user mode
3551:         if (domain.userQuota == -1) { features += 0x00000008; } // No server files mode
3552:         if (obj.args.mpstlsoffload) { features += 0x00000010; } // No mutual-auth CIRA
3553:         if ((parent.config.settings.allowframing != null) || (domain.allowframing != null) || (parent.config.settings.allowedframingorigins != null) || (domain.allowedframingorigins != null)) { features += 0x00000020; } // Allow site within iframe
3554:         if ((domain.mailserver != null) && (obj.parent.certificates.CommonName != null) && (obj.parent.certificates.CommonName.indexOf('.') != -1) && (obj.args.lanonly != true)) { features += 0x00000040; } // Email invites
3555:         if (obj.args.webrtc == true) { features += 0x00000080; } // Enable WebRTC (Default false for now)
3556:         // 0x00000100 --> This feature flag is free for future use.
3557:         if (obj.args.allowhighqualitydesktop !== false) { features += 0x00000200; } // Enable AllowHighQualityDesktop (Default true)
3558:         if ((obj.args.lanonly == true) || (obj.args.mpsport == 0)) { features += 0x00000400; } // No CIRA
3559:         if ((obj.parent.serverSelfWriteAllowed == true) && (user != null) && ((user.siteadmin & 0x00000010) != 0)) { features += 0x00000800; } // Server can self-write (Allows self-update)
3560:         if ((parent.config.settings.no2factorauth !== true) && (domain.auth != 'sspi') && (obj.parent.certificates.CommonName.indexOf('.') != -1) && (obj.args.nousers !== true) && (user._id.split('/')[2][0] != '~')) { features += 0x00001000; } // 2FA login supported
3561:         if (domain.agentnoproxy === true) { features += 0x00002000; } // Indicates that agents should be installed without using a HTTP proxy
3562:         if ((parent.config.settings.no2factorauth !== true) && domain.yubikey && domain.yubikey.id && domain.yubikey.secret && (user._id.split('/')[2][0] != '~')) { features += 0x00004000; } // Indicates Yubikey support
3563:         if (domain.geolocation == true) { features += 0x00008000; } // Enable geo-location features
3564:         if ((domain.passwordrequirements != null) && (domain.passwordrequirements.hint === true)) { features += 0x00010000; } // Enable password hints
3565:         if (parent.config.settings.no2factorauth !== true) { features += 0x00020000; } // Enable WebAuthn/FIDO2 support
3566:         if ((obj.args.nousers != true) && (domain.passwordrequirements != null) && (domain.passwordrequirements.force2factor === true) && (user._id.split('/')[2][0] != '~')) {
3567:             // Check if we can skip 2nd factor auth because of the source IP address
3568:             var skip2factor = false;
3569:             if ((req != null) && (req.clientIp != null) && (domain.passwordrequirements != null) && (domain.passwordrequirements.skip2factor != null)) {
3570:                 for (var i in domain.passwordrequirements.skip2factor) {
3571:                     if (require('ipcheck').match(req.clientIp, domain.passwordrequirements.skip2factor[i]) === true) { skip2factor = true; }
3572:                 }
3573:             }
3574:             if (skip2factor == false) { features += 0x00040000; } // Force 2-factor auth
3575:         }
3576:         if ((domain.auth == 'sspi') || (domain.auth == 'ldap')) { features += 0x00080000; } // LDAP or SSPI in use, warn that users must login first before adding a user to a group.
3577:         if (domain.amtacmactivation) { features += 0x00100000; } // Intel AMT ACM activation/upgrade is possible
3578:         if (domain.usernameisemail) { features += 0x00200000; } // Username is email address
3579:         if (parent.mqttbroker != null) { features += 0x00400000; } // This server supports MQTT channels
3580:         if (((typeof domain.passwordrequirements != 'object') || (domain.passwordrequirements.email2factor != false)) && (domain.mailserver != null)) { features += 0x00800000; } // using email for 2FA is allowed
3581:         if (domain.agentinvitecodes == true) { features += 0x01000000; } // Support for agent invite codes
3582:         if (parent.smsserver != null) { features += 0x02000000; } // SMS messaging is supported
3583:         if ((parent.smsserver != null) && ((typeof domain.passwordrequirements != 'object') || (domain.passwordrequirements.sms2factor != false))) { features += 0x04000000; } // SMS 2FA is allowed
3584:         if (domain.sessionrecording != null) { features += 0x08000000; } // Server recordings enabled
3585:         if (domain.urlswitching === false) { features += 0x10000000; } // Disables the URL switching feature
3586:         if (domain.novnc === false) { features += 0x20000000; } // Disables noVNC
3587:         if (domain.mstsc === false) { features += 0x40000000; } // Disables MSTSC.js
3588:         if (obj.isTrustedCert(domain) == false) { features += 0x80000000; } // Indicate we are not using a trusted certificate
3589:         if (obj.parent.amtManager != null) { features2 += 0x00000001; } // Indicates that the Intel AMT manager is active
3590:         if (obj.parent.firebase != null) { features2 += 0x00000002; } // Indicates the server supports Firebase push messaging
3591:         if ((obj.parent.firebase != null) && (obj.parent.firebase.pushOnly != true)) { features2 += 0x00000004; } // Indicates the server supports Firebase two-way push messaging
3592:         if (obj.parent.webpush != null) { features2 += 0x00000008; } // Indicates web push is enabled
3593:         if (((obj.args.noagentupdate == 1) || (obj.args.noagentupdate == true))) { features2 += 0x00000010; } // No agent update
3594:         if (parent.amtProvisioningServer != null) { features2 += 0x00000020; } // Intel AMT LAN provisioning server
3595:         if (((typeof domain.passwordrequirements != 'object') || (domain.passwordrequirements.push2factor != false)) && (obj.parent.firebase != null)) { features2 += 0x00000040; } // Indicates device push notification 2FA is enabled
3596:         if ((typeof domain.passwordrequirements != 'object') || ((domain.passwordrequirements.logintokens !== false) && ((Array.isArray(domain.passwordrequirements.logintokens) == false) || ((domain.passwordrequirements.logintokens.indexOf(user._id) >= 0) || (user.links && Object.keys(user.links).some(key => domain.passwordrequirements.logintokens.indexOf(key) >= 0)) )))) { features2 += 0x00000080; } // Indicates login tokens are allowed
3597:         if (req.session.loginToken != null) { features2 += 0x00000100; } // LoginToken mode, no account changes.
3598:         if (domain.ssh == true) { features2 += 0x00000200; } // SSH is enabled
3599:         if (domain.localsessionrecording === false) { features2 += 0x00000400; } // Disable local recording feature
3600:         if (domain.clipboardget == false) { features2 += 0x00000800; } // Disable clipboard get
3601:         if (domain.clipboardset == false) { features2 += 0x00001000; } // Disable clipboard set
3602:         if ((typeof domain.desktop == 'object') && (domain.desktop.viewonly == true)) { features2 += 0x00002000; } // Indicates remote desktop is viewonly
3603:         if (domain.mailserver != null) { features2 += 0x00004000; } // Indicates email server is active
3604:         if (domain.devicesearchbarserverandclientname) { features2 += 0x00008000; } // Search bar will find both server name and client name
3605:         if (domain.ipkvm) { features2 += 0x00010000; } // Indicates support for IP KVM device groups
3606:         if ((domain.passwordrequirements) && (domain.passwordrequirements.otp2factor == false)) { features2 += 0x00020000; } // Indicates support for OTP 2FA is disabled
3607:         if ((typeof domain.passwordrequirements == 'object') && (domain.passwordrequirements.backupcode2factor === false)) { features2 += 0x00040000; } // Indicates 2FA backup codes are disabled
3608:         if ((typeof domain.passwordrequirements == 'object') && (domain.passwordrequirements.single2factorwarning === false)) { features2 += 0x00080000; } // Indicates no warning if a single 2FA is in use
3609:         if (domain.nightmode === 1) { features2 += 0x00100000; } // Always night mode
3610:         if (domain.nightmode === 2) { features2 += 0x00200000; } // Always day mode
3611:         if (domain.allowsavingdevicecredentials == false) { features2 += 0x00400000; } // Do not allow device credentials to be saved on the server
3612:         if ((typeof domain.files == 'object') && (domain.files.sftpconnect === false)) { features2 += 0x00800000; } // Remove the "SFTP Connect" button in the "Files" tab when the device is agent managed
3613:         if ((typeof domain.terminal == 'object') && (domain.terminal.sshconnect === false)) { features2 += 0x01000000; } // Remove the "SSH Connect" button in the "Terminal" tab when the device is agent managed
3614:         if ((parent.msgserver != null) && (parent.msgserver.providers != 0)) { features2 += 0x02000000; } // User messaging server is enabled
3615:         if ((parent.msgserver != null) && (parent.msgserver.providers != 0) && ((typeof domain.passwordrequirements != 'object') || (domain.passwordrequirements.msg2factor != false))) { features2 += 0x04000000; } // User messaging 2FA is allowed
3616:         if (domain.scrolltotop == true) { features2 += 0x08000000; } // Show the "Scroll to top" button
3617:         if (domain.devicesearchbargroupname === true) { features2 += 0x10000000; } // Search bar will find by group name too
3618:         if (((typeof domain.passwordrequirements != 'object') || (domain.passwordrequirements.duo2factor != false)) && (typeof domain.duo2factor == 'object') && (typeof domain.duo2factor.integrationkey == 'string') && (typeof domain.duo2factor.secretkey == 'string') && (typeof domain.duo2factor.apihostname == 'string')) { features2 += 0x20000000; } // using Duo for 2FA is allowed
3619:         if (domain.showmodernuitoggle == true) { features2 += 0x40000000; } // Indicates that the new UI should be shown
3620:         if (domain.sitestyle === 3) { features2 |= 0x80000000; } // Indicates that Modern UI is forced (siteStyle = 3)
3621:         if ((typeof domain.desktop == 'object') && (domain.desktop.disableconnectall == true)) { features3 += 0x00000001; } // Disable "Connect All" button when multiple sessions are active on a device
3622:         if (domain.upninsteadofuser === true) { features3 += 0x00000002; } // Show UPN instead of username in General tab
3623:         return { features: features, features2: features2, features3: features3 };
3624:     }
3625: 
3626:     function handleRootRequestLogin(req, res, domain, hardwareKeyChallenge, passRequirements) {
3627:         parent.debug('web', 'handleRootRequestLogin()');
3628:         var features = 0;
3629:         if ((parent.config != null) && (parent.config.settings != null) && ((parent.config.settings.allowframing == true) || (typeof parent.config.settings.allowframing == 'string') || (parent.config.settings.allowedframingorigins != null) || (domain != null && domain.allowedframingorigins != null))) { features += 32; } // Allow site within iframe
3630:         if (domain.usernameisemail) { features += 0x00200000; } // Username is email address
3631:         var httpsPort = ((obj.args.aliasport == null) ? obj.args.port : obj.args.aliasport); // Use HTTPS alias port is specified
3632:         var loginmode = 0;
3633:         if (req.session) { loginmode = req.session.loginmode; delete req.session.loginmode; } // Clear this state, if the user hits refresh, we want to go back to the login page.
3634: 
3635:         // Format an error message if needed
3636:         var passhint = null, msgid = 0;
3637:         if (req.session != null) {
3638:             msgid = req.session.messageid;
3639:             if ((msgid == 5) || (loginmode == 7) || ((domain.passwordrequirements != null) && (domain.passwordrequirements.hint === true))) { passhint = EscapeHtml(req.session.passhint); }
3640:             delete req.session.messageid;
3641:             delete req.session.passhint;
3642:         }
3643:         const allowAccountReset = ((typeof domain.passwordrequirements != 'object') || (domain.passwordrequirements.allowaccountreset !== false));
3644:         const emailcheck = (allowAccountReset && (domain.mailserver != null) && (obj.parent.certificates.CommonName != null) && (obj.parent.certificates.CommonName.indexOf('.') != -1) && (obj.args.lanonly != true) && (domain.auth != 'sspi') && (domain.auth != 'ldap'))
3645: 
3646:         // Check if we are allowed to create new users using the login screen
3647:         var newAccountsAllowed = true;
3648:         if ((domain.newaccounts !== 1) && (domain.newaccounts !== true)) { for (var i in obj.users) { if (obj.users[i].domain == domain.id) { newAccountsAllowed = false; break; } } }
3649:         if (parent.config.settings.maintenancemode != null) { newAccountsAllowed = false; }
3650: 
3651:         // Encrypt the hardware key challenge state if needed
3652:         var hwstate = null;
3653:         if (hardwareKeyChallenge && req.session) {
3654:             const sec = parent.decryptSessionData(req.session.e);
3655:             hwstate = obj.parent.encodeCookie({ u: sec.tuser, p: sec.tpass, c: sec.u2f }, obj.parent.loginCookieEncryptionKey)
3656:         }
3657: 
3658:         // Check if we can use OTP tokens with email. We can't use email for 2FA password recovery (loginmode 5).
3659:         var otpemail = (loginmode != 5) && (domain.mailserver != null) && (req.session != null) && ((req.session.temail === 1) || (typeof req.session.temail == 'string'));
3660:         if ((typeof domain.passwordrequirements == 'object') && (domain.passwordrequirements.email2factor == false)) { otpemail = false; }
3661:         var otpduo = (req.session != null) && (req.session.tduo === 1);
3662:         if (((typeof domain.passwordrequirements == 'object') && (domain.passwordrequirements.duo2factor == false)) || (typeof domain.duo2factor != 'object')) { otpduo = false; }
3663:         var otpsms = (parent.smsserver != null) && (req.session != null) && (req.session.tsms === 1);
3664:         if ((typeof domain.passwordrequirements == 'object') && (domain.passwordrequirements.sms2factor == false)) { otpsms = false; }
3665:         var otpmsg = (parent.msgserver != null) && (req.session != null) && (req.session.tmsg === 1);
3666:         if ((typeof domain.passwordrequirements == 'object') && (domain.passwordrequirements.msg2factor == false)) { otpmsg = false; }
3667:         var otppush = (parent.firebase != null) && (req.session != null) && (req.session.tpush === 1);
3668:         if ((typeof domain.passwordrequirements == 'object') && (domain.passwordrequirements.push2factor == false)) { otppush = false; }
3669:         const autofido = ((typeof domain.passwordrequirements == 'object') && (domain.passwordrequirements.autofido2fa == true)); // See if FIDO should be automatically prompted if user account has it.
3670: 
3671:         // See if we support two-factor trusted cookies
3672:         var twoFactorCookieDays = 30;
3673:         if (typeof domain.twofactorcookiedurationdays == 'number') { twoFactorCookieDays = domain.twofactorcookiedurationdays; }
3674: 
3675:         // See what authentication strategies we have
3676:         var authStrategies = [];
3677:         if (typeof domain.authstrategies == 'object') {
3678:             if (typeof domain.authstrategies.twitter == 'object') { authStrategies.push('twitter'); }
3679:             if (typeof domain.authstrategies.google == 'object') { authStrategies.push('google'); }
3680:             if (typeof domain.authstrategies.github == 'object') { authStrategies.push('github'); }
3681:             if (typeof domain.authstrategies.azure == 'object') { authStrategies.push('azure'); }
```

## Lines 4542-4705
```text
4542: 
4543:         if (c.a === 5) {
4544:             // This is the older style sharing cookie with everything encoded within it.
4545:             // This cookie style gives a very large URL, so it's not used anymore.
4546:             if ((typeof c.p !== 'number') || (c.p < 1) || (c.p > 7) || (typeof c.uid != 'string') || (typeof c.nid != 'string') || (typeof c.gn != 'string') || (typeof c.cf != 'number') || (typeof c.pid != 'string')) { res.sendStatus(404); return; }
4547:             handleSharingRequestEx(req, res, domain, c);
4548:             return;
4549:         }
4550:         if (c.a === 6) {
4551:             // This is the new style sharing cookie, just encodes the pointer to the sharing information in the database.
4552:             // Gives a much more compact URL.
4553:             if (typeof c.pid != 'string') { res.sendStatus(404); return; }
4554: 
4555:             // Check the expired time, expire message.
4556:             if ((c.e != null) && (c.e <= Date.now())) { res.status(404); render(req, res, getRenderPage((domain.sitestyle >= 2) ? 'message2' : 'message', req, domain), getRenderArgs({ titleid: 2, msgid: 12, domainurl: encodeURIComponent(domain.url).replace(/'/g, '%27') }, req, domain)); return; }
4557: 
4558:             obj.db.Get('deviceshare-' + c.pid, function (err, docs) {
4559:                 if ((err != null) || (docs == null) || (docs.length != 1)) { res.sendStatus(404); return; }
4560:                 const doc = docs[0];
4561: 
4562:                 // If this is a recurrent share, check if we are at the correct time to make use of it
4563:                 if (typeof doc.recurring == 'number') {
4564:                     const now = Date.now();
4565:                     if (now >= doc.startTime) { // We don't want to move the validity window before the start time
4566:                         const deltaTime = (now - doc.startTime);
4567:                         if (doc.recurring === 1) {
4568:                             // This moves the start time to the next valid daily window
4569:                             const oneDay = (24 * 60 * 60 * 1000);
4570:                             var addition = Math.floor(deltaTime / oneDay);
4571:                             if ((deltaTime - (addition * oneDay)) > (doc.duration * 60000)) { addition++; } // If we are passed the current windows, move to the next one. This will show link as not being valid yet.
4572:                             doc.startTime += (addition * oneDay);
4573:                         } else if (doc.recurring === 2) {
4574:                             // This moves the start time to the next valid weekly window
4575:                             const oneWeek = (7 * 24 * 60 * 60 * 1000);
4576:                             var addition = Math.floor(deltaTime / oneWeek);
4577:                             if ((deltaTime - (addition * oneWeek)) > (doc.duration * 60000)) { addition++; } // If we are passed the current windows, move to the next one. This will show link as not being valid yet.
4578:                             doc.startTime += (addition * oneWeek);
4579:                         }
4580:                     }
4581:                 }
4582: 
4583:                 // Generate an old style cookie from the information in the database
4584:                 var cookie = { a: 5, p: doc.p, gn: doc.guestName, nid: doc.nodeid, cf: doc.consent, pid: doc.publicid, k: doc.extrakey ? doc.extrakey : null, port: doc.port };
4585:                 if (doc.userid) { cookie.uid = doc.userid; }
4586:                 if ((cookie.userid == null) && (cookie.pid.startsWith('AS:node/'))) { cookie.nouser = 1; }
4587:                 if (doc.startTime != null) {
4588:                     if (doc.expireTime != null) { cookie.start = doc.startTime; cookie.expire = doc.expireTime; }
4589:                     else if (doc.duration != null) { cookie.start = doc.startTime; cookie.expire = doc.startTime + (doc.duration * 60000); }
4590:                 }
4591:                 if (doc.viewOnly === true) { cookie.vo = 1; }
4592:                 handleSharingRequestEx(req, res, domain, cookie);
4593:             });
4594:             return;
4595:         }
4596:         res.sendStatus(404); return;
4597:     }
4598: 
4599:     // Serve the guest sharing page
4600:     function handleSharingRequestEx(req, res, domain, c) {
4601:         // Check the expired time, expire message.
4602:         if ((c.expire != null) && (c.expire <= Date.now())) { res.status(404); render(req, res, getRenderPage((domain.sitestyle >= 2) ? 'message2' : 'message', req, domain), getRenderArgs({ titleid: 2, msgid: 12, domainurl: encodeURIComponent(domain.url).replace(/'/g, '%27') }, req, domain)); return; }
4603: 
4604:         // Check the public id
4605:         obj.db.GetAllTypeNodeFiltered([c.nid], domain.id, 'deviceshare', null, function (err, docs) {
4606:             // Check if any sharing links are present, expire message.
4607:             if ((err != null) || (docs.length == 0)) { res.status(404); render(req, res, getRenderPage((domain.sitestyle >= 2) ? 'message2' : 'message', req, domain), getRenderArgs({ titleid: 2, msgid: 12, domainurl: encodeURIComponent(domain.url).replace(/'/g, '%27') }, req, domain)); return; }
4608: 
4609:             // Search for the device share public identifier, expire message.
4610:             var found = false;
4611:             for (var i = 0; i < docs.length; i++) { if ((docs[i].publicid == c.pid) && ((docs[i].extrakey == null) || (docs[i].extrakey === c.k))) { found = true; } }
4612:             if (found == false) { res.status(404); render(req, res, getRenderPage((domain.sitestyle >= 2) ? 'message2' : 'message', req, domain), getRenderArgs({ titleid: 2, msgid: 12, domainurl: encodeURIComponent(domain.url).replace(/'/g, '%27') }, req, domain)); return; }
4613: 
4614:             // Get information about this node
4615:             obj.db.Get(c.nid, function (err, nodes) {
4616:                 if ((err != null) || (nodes == null) || (nodes.length != 1)) { res.sendStatus(404); return; }
4617:                 var node = nodes[0];
4618: 
4619:                 // Check the start time, not yet valid message.
4620:                 if ((c.start != null) && (c.expire != null) && ((c.start > Date.now()) || (c.start > c.expire))) { res.status(404); render(req, res, getRenderPage((domain.sitestyle >= 2) ? 'message2' : 'message', req, domain), getRenderArgs({ titleid: 2, msgid: 11, domainurl: encodeURIComponent(domain.url).replace(/'/g, '%27') }, req, domain)); return; }
4621: 
4622:                 // If this is a web relay share, check if this feature is active
4623:                 if ((c.p == 8) || (c.p == 16)) {
4624:                     // This is a HTTP or HTTPS share
4625:                     var webRelayPort = ((args.relaydns != null) ? ((typeof args.aliasport == 'number') ? args.aliasport : args.port) : ((parent.webrelayserver != null) ? ((typeof args.relayaliasport == 'number') ? args.relayaliasport : parent.webrelayserver.port) : 0));
4626:                     if (webRelayPort == 0) { res.sendStatus(404); return; }
4627: 
4628:                     // Create the authentication cookie
4629:                     // Include cf so these cookies match the device-share guest shape used elsewhere.
4630:                     const authCookieData = { userid: c.uid, domainid: domain.id, nid: c.nid, ip: req.clientIp, p: c.p, gn: c.gn, cf: (typeof c.cf == 'number') ? c.cf : 0, r: 8, expire: c.expire, pid: c.pid, port: c.port };
4631:                     if ((authCookieData.userid == null) && (authCookieData.pid.startsWith('AS:node/'))) { authCookieData.nouser = 1; }
4632:                     const authCookie = obj.parent.encodeCookie(authCookieData, obj.parent.loginCookieEncryptionKey);
4633: 
4634:                     // Redirect to a URL
4635:                     var webRelayDns = (args.relaydns != null) ? args.relaydns[0] : obj.getWebServerName(domain, req);
4636:                     var url = 'https://' + webRelayDns + ':' + webRelayPort + '/control-redirect.ashx?n=' + c.nid + '&p=' + c.port + '&appid=' + c.p + '&c=' + authCookie;
4637:                     if (c.addr != null) { url += '&addr=' + c.addr; }
4638:                     if (c.pid != null) { url += '&relayid=' + c.pid; }
4639:                     parent.debug('web', 'handleSharingRequest: Redirecting guest to HTTP relay page for \"' + c.uid + '\", guest \"' + c.gn + '\".');
4640:                     res.redirect(url);
4641:                 } else {
4642:                     // Looks good, let's create the outbound session cookies.
4643:                     // This is a desktop, terminal or files share. We need to display the sharing page.
4644:                     // Consent flags are 1 = Notify, 8 = Prompt, 64 = Privacy Bar.
4645:                     const authCookieData = { userid: c.uid, domainid: domain.id, nid: c.nid, ip: req.clientIp, p: c.p, gn: c.gn, cf: c.cf, r: 8, expire: c.expire, pid: c.pid, vo: c.vo };
4646:                     if ((authCookieData.userid == null) && (authCookieData.pid.startsWith('AS:node/'))) { authCookieData.nouser = 1; }
4647:                     if (c.k != null) { authCookieData.k = c.k; }
4648:                     const authCookie = obj.parent.encodeCookie(authCookieData, obj.parent.loginCookieEncryptionKey);
4649: 
4650:                     // Server features
4651:                     var features2 = 0;
4652:                     if (obj.args.allowhighqualitydesktop !== false) { features2 += 1; } // Enable AllowHighQualityDesktop (Default true)
4653: 
4654:                     // Lets respond by sending out the desktop viewer.
4655:                     var httpsPort = ((obj.args.aliasport == null) ? obj.args.port : obj.args.aliasport); // Use HTTPS alias port is specified
4656:                     parent.debug('web', 'handleSharingRequest: Sending guest sharing page for \"' + c.uid + '\", guest \"' + c.gn + '\".');
4657:                     res.set({ 'Cache-Control': 'no-store' });
4658:                     render(req, res, getRenderPage('sharing', req, domain), getRenderArgs({ authCookie: authCookie, authRelayCookie: '', domainurl: encodeURIComponent(domain.url).replace(/'/g, '%27'), nodeid: c.nid, agentId: (node.agent != null) ? node.agent.id : 0, serverDnsName: obj.getWebServerName(domain, req), serverRedirPort: args.redirport, serverPublicPort: httpsPort, expire: c.expire, viewOnly: (c.vo == 1) ? 1 : 0, nodeName: encodeURIComponent(node.name).replace(/'/g, '%27'), features: c.p, features2: features2 }, req, domain));
4659:                 }
4660:             });
4661:         });
4662:     }
4663: 
4664:     // Handle domain redirection
4665:     obj.handleDomainRedirect = function (req, res) {
4666:         const domain = checkUserIpAddress(req, res);
4667:         if (domain == null) { return; }
4668:         if (domain.redirects == null) { res.sendStatus(404); return; }
4669:         var urlArgs = '', urlName = null, splitUrl = req.originalUrl.split('?');
4670:         if (splitUrl.length > 1) { urlArgs = '?' + splitUrl[1]; }
4671:         if ((splitUrl.length > 0) && (splitUrl[0].length > 1)) { urlName = splitUrl[0].substring(1).toLowerCase(); }
4672:         if ((urlName == null) || (domain.redirects[urlName] == null) || (urlName[0] == '_')) { res.sendStatus(404); return; }
4673:         if (domain.redirects[urlName] == '~showversion') {
4674:             // Show the current version
4675:             res.end('MeshCentral v' + obj.parent.currentVer);
4676:         } else {
4677:             // Perform redirection
4678:             res.redirect(domain.redirects[urlName] + urlArgs + getQueryPortion(req));
4679:         }
4680:     }
4681: 
4682:     // Take a "user/domain/userid/path/file" format and return the actual server disk file path if access is allowed
4683:     obj.getServerFilePath = function (user, domain, path) {
4684:         var splitpath = path.split('/'), serverpath = obj.path.join(obj.filespath, 'domain'), filename = '';
4685:         if ((splitpath.length < 3) || (splitpath[0] != 'user' && splitpath[0] != 'mesh') || (splitpath[1] != domain.id)) return null; // Basic validation
4686:         var objid = splitpath[0] + '/' + splitpath[1] + '/' + splitpath[2];
4687:         if (splitpath[0] == 'user' && (objid != user._id)) return null; // User validation, only self allowed
4688:         if (splitpath[0] == 'mesh') { if ((obj.GetMeshRights(user, objid) & 32) == 0) { return null; } } // Check mesh server file rights
4689:         if (splitpath[1] != '') { serverpath += '-' + splitpath[1]; } // Add the domain if needed
4690:         serverpath += ('/' + splitpath[0] + '-' + splitpath[2]);
4691:         for (var i = 3; i < splitpath.length; i++) { if (obj.common.IsFilenameValid(splitpath[i]) == true) { serverpath += '/' + splitpath[i]; filename = splitpath[i]; } else { return null; } } // Check that each folder is correct
4692:         return { fullpath: obj.path.resolve(obj.filespath, serverpath), path: serverpath, name: filename, quota: obj.getQuota(objid, domain) };
4693:     };
4694: 
4695:     // Return the maximum number of bytes allowed in the user account "My Files".
4696:     obj.getQuota = function (objid, domain) {
4697:         if (objid == null) return 0;
4698:         if (objid.startsWith('user/')) {
4699:             var user = obj.users[objid];
4700:             if (user == null) return 0;
4701:             if (user.siteadmin == 0xFFFFFFFF) return null; // Administrators have no user limit
4702:             if ((user.quota != null) && (typeof user.quota == 'number')) { return user.quota; }
4703:             if ((domain != null) && (domain.userquota != null) && (typeof domain.userquota == 'number')) { return domain.userquota; }
4704:             return null; // By default, the user will have no limit
4705:         } else if (objid.startsWith('mesh/')) {
```

## Lines 6292-6557
```text
6292:     obj.handleMeshAgentRequest = function (req, res) {
6293:         var domain = getDomain(req, res);
6294:         if (domain == null) { parent.debug('web', 'handleRootRequest: invalid domain.'); try { res.sendStatus(404); } catch (ex) { } return; }
6295: 
6296:         // If required, check if this user has rights to do this
6297:         if ((obj.parent.config.settings != null) && ((obj.parent.config.settings.lockagentdownload == true) || (domain.lockagentdownload == true)) && (req.session.userid == null)) {
6298:             // Agents fetching their own update have no session, the agentupdate command gives them a cookie instead
6299:             var updatecookie = obj.parent.decodeCookie(req.query.auth, obj.parent.loginCookieEncryptionKey, 10); // 10 minute timeout, covers the agent's download retries
6300:             if ((updatecookie == null) || (updatecookie.a != 'aupd') || (req.query.id == null) || (updatecookie.i != req.query.id)) { res.sendStatus(401); return; }
6301:         }
6302: 
6303:         if ((req.query.meshinstall != null) && (req.query.id != null)) {
6304:             if ((domain.loginkey != null) && (domain.loginkey.indexOf(req.query.key) == -1)) { try { res.sendStatus(404); } catch (ex) { } return; } // Check 3FA URL key
6305: 
6306:             // Send meshagent with included self installer for a specific platform back
6307:             // Start by getting the .msh for this request
6308:             var meshsettings = getMshFromRequest(req, res, domain);
6309:             if (meshsettings == null) { try { res.sendStatus(401); } catch (ex) { } return; }
6310: 
6311:             // Get the interactive install script, this only works for non-Windows agents
6312:             var agentid = parseInt(req.query.meshinstall);
6313:             var argentInfo = obj.parent.meshAgentBinaries[agentid];
6314:             if (domain.meshAgentBinaries && domain.meshAgentBinaries[agentid]) { argentInfo = domain.meshAgentBinaries[agentid]; }
6315:             var scriptInfo = obj.parent.meshAgentInstallScripts[6];
6316:             if ((argentInfo == null) || (scriptInfo == null) || (argentInfo.platform == 'win32')) { try { res.sendStatus(404); } catch (ex) { } return; }
6317: 
6318:             // Change the .msh file into JSON format and merge it into the install script
6319:             var tokens, msh = {}, meshsettingslines = meshsettings.split('\r').join('').split('\n');
6320:             for (var i in meshsettingslines) { tokens = meshsettingslines[i].split('='); if (tokens.length == 2) { msh[tokens[0]] = tokens[1]; } }
6321:             var js = scriptInfo.data.replace('var msh = {};', 'var msh = ' + JSON.stringify(msh) + ';');
6322: 
6323:             // Get the agent filename
6324:             var meshagentFilename = 'meshagent';
6325:             if ((domain.agentcustomization != null) && (typeof domain.agentcustomization.filename == 'string')) { meshagentFilename = domain.agentcustomization.filename; }
6326: 
6327:             setContentDispositionHeader(res, 'application/octet-stream', meshagentFilename, null, 'meshagent');
6328:             if (argentInfo.mtime != null) { res.setHeader('Last-Modified', argentInfo.mtime.toUTCString()); }
6329:             res.statusCode = 200;
6330:             obj.parent.exeHandler.streamExeWithJavaScript({ platform: argentInfo.platform, sourceFileName: argentInfo.path, destinationStream: res, js: Buffer.from(js, 'utf8'), peinfo: argentInfo.pe });
6331:         } else if (req.query.id != null) {
6332:             // Send a specific mesh agent back
6333:             var argentInfo = obj.parent.meshAgentBinaries[req.query.id];
6334:             if (domain.meshAgentBinaries && domain.meshAgentBinaries[req.query.id]) { argentInfo = domain.meshAgentBinaries[req.query.id]; }
6335:             if (argentInfo == null) { try { res.sendStatus(404); } catch (ex) { } return; }
6336: 
6337:             // Download PDB debug files, only allowed for administrator or accounts with agent dump access
6338:             if (req.query.pdb == 1) {
6339:                 if ((req.session == null) || (req.session.userid == null)) { try { res.sendStatus(404); } catch (ex) { } return; }
6340:                 var user = obj.users[req.session.userid];
6341:                 if (user == null) { try { res.sendStatus(404); } catch (ex) { } return; }
6342:                 if ((user != null) && ((user.siteadmin == 0xFFFFFFFF) || ((Array.isArray(obj.parent.config.settings.agentcoredumpusers)) && (obj.parent.config.settings.agentcoredumpusers.indexOf(user._id) >= 0)))) {
6343:                     if (argentInfo.id == 3) {
6344:                         setContentDispositionHeader(res, 'application/octet-stream', 'MeshService.pdb', null, 'MeshService.pdb');
6345:                         if (argentInfo.mtime != null) { res.setHeader('Last-Modified', argentInfo.mtime.toUTCString()); }
6346:                         try { res.sendFile(argentInfo.path.split('MeshService-signed.exe').join('MeshService.pdb')); } catch (ex) { }
6347:                         return;
6348:                     }
6349:                     if (argentInfo.id == 4) {
6350:                         setContentDispositionHeader(res, 'application/octet-stream', 'MeshService64.pdb', null, 'MeshService64.pdb');
6351:                         if (argentInfo.mtime != null) { res.setHeader('Last-Modified', argentInfo.mtime.toUTCString()); }
6352:                         try { res.sendFile(argentInfo.path.split('MeshService64-signed.exe').join('MeshService64.pdb')); } catch (ex) { }
6353:                         return;
6354:                     }
6355:                 }
6356:                 try { res.sendStatus(404); } catch (ex) { }
6357:                 return;
6358:             }
6359: 
6360:             if ((req.query.meshid == null) || (argentInfo.platform != 'win32')) {
6361:                 // Get the agent filename
6362:                 var meshagentFilename = argentInfo.rname;
6363:                 if ((domain.agentcustomization != null) && (typeof domain.agentcustomization.filename == 'string')) { meshagentFilename = domain.agentcustomization.filename; }
6364:                 if (argentInfo.rname.endsWith('.apk') && !meshagentFilename.endsWith('.apk')) { meshagentFilename = meshagentFilename + '.apk'; }
6365:                 if (argentInfo.mtime != null) { res.setHeader('Last-Modified', argentInfo.mtime.toUTCString()); }
6366:                 if (req.query.zip == 1) { if (argentInfo.zdata != null) { setContentDispositionHeader(res, 'application/octet-stream', meshagentFilename + '.zip', null, 'meshagent.zip'); res.send(argentInfo.zdata); } else { try { res.sendStatus(404); } catch (ex) { } } return; } // Send compressed agent
6367:                 setContentDispositionHeader(res, 'application/octet-stream', meshagentFilename, null, 'meshagent');
6368:                 if (argentInfo.data == null) { res.sendFile(argentInfo.path); } else { res.send(argentInfo.data); }
6369:                 return;
6370:             } else {
6371:                 // Check if the meshid is a time limited, encrypted cookie
6372:                 var meshcookie = obj.parent.decodeCookie(req.query.meshid, obj.parent.invitationLinkEncryptionKey);
6373:                 if ((meshcookie != null) && (meshcookie.m != null)) { req.query.meshid = meshcookie.m; }
6374: 
6375:                 // We are going to embed the .msh file into the Windows executable (signed or not).
6376:                 // First, fetch the mesh object to build the .msh file
6377:                 var mesh = obj.meshes['mesh/' + domain.id + '/' + req.query.meshid];
6378:                 if (mesh == null) { try { res.sendStatus(401); } catch (ex) { } return; }
6379: 
6380:                 // If required, check if this user has rights to do this
6381:                 if ((obj.parent.config.settings != null) && ((obj.parent.config.settings.lockagentdownload == true) || (domain.lockagentdownload == true))) {
6382:                     if ((domain.id != mesh.domain) || ((obj.GetMeshRights(req.session.userid, mesh) & 1) == 0)) { try { res.sendStatus(401); } catch (ex) { } return; }
6383:                 }
6384: 
6385:                 var meshidhex = Buffer.from(req.query.meshid.replace(/\@/g, '+').replace(/\$/g, '/'), 'base64').toString('hex').toUpperCase();
6386:                 var serveridhex = Buffer.from(obj.agentCertificateHashBase64.replace(/\@/g, '+').replace(/\$/g, '/'), 'base64').toString('hex').toUpperCase();
6387:                 var httpsPort = ((obj.args.aliasport == null) ? obj.args.port : obj.args.aliasport); // Use HTTPS alias port if specified
6388:                 if (obj.args.agentport != null) { httpsPort = obj.args.agentport; } // If an agent only port is enabled, use that.
6389:                 if (obj.args.agentaliasport != null) { httpsPort = obj.args.agentaliasport; } // If an agent alias port is specified, use that.
6390: 
6391:                 // Prepare a mesh agent file name using the device group name.
6392:                 var meshfilename = mesh.name
6393:                 meshfilename = meshfilename.split('\\').join('').split('/').join('').split(':').join('').split('*').join('').split('?').join('').split('"').join('').split('<').join('').split('>').join('').split('|').join('').split(' ').join('').split('\'').join('');
6394:                 if (argentInfo.rname.endsWith('.exe')) { meshfilename = argentInfo.rname.substring(0, argentInfo.rname.length - 4) + '-' + meshfilename + '.exe'; } else { meshfilename = argentInfo.rname + '-' + meshfilename; }
6395: 
6396:                 // Customize the mesh agent file name
6397:                 if ((domain.agentcustomization != null) && (typeof domain.agentcustomization.filename == 'string')) {
6398:                     meshfilename = meshfilename.split('meshagent').join(domain.agentcustomization.filename).split('MeshAgent').join(domain.agentcustomization.filename);
6399:                 }
6400: 
6401:                 // Get the agent connection server name
6402:                 var serverName = obj.getWebServerName(domain, req);
6403:                 if (typeof obj.args.agentaliasdns == 'string') { serverName = obj.args.agentaliasdns; }
6404: 
6405:                 // Build the agent connection URL. If we are using a sub-domain or one with a DNS, we need to craft the URL correctly.
6406:                 var xdomain = (domain.dns == null) ? domain.id : '';
6407:                 if (xdomain != '') xdomain += '/';
6408:                 var meshsettings = '';
6409:                 if (req.query.ac != '4') { // If MeshCentral Assistant Monitor Mode, DONT INCLUDE SERVER DETAILS!
6410:                     meshsettings += '\r\nMeshName=' + mesh.name + '\r\nMeshType=' + mesh.mtype + '\r\nMeshID=0x' + meshidhex + '\r\nServerID=' + serveridhex + '\r\n';
6411:                     if (obj.args.lanonly != true) { meshsettings += 'MeshServer=wss://' + serverName + ':' + httpsPort + '/' + xdomain + 'agent.ashx\r\n'; } else {
6412:                         meshsettings += 'MeshServer=local\r\n';
6413:                         if ((obj.args.localdiscovery != null) && (typeof obj.args.localdiscovery.key == 'string') && (obj.args.localdiscovery.key.length > 0)) { meshsettings += 'DiscoveryKey=' + obj.args.localdiscovery.key + '\r\n'; }
6414:                     }
6415:                     if ((req.query.tag != null) && (typeof req.query.tag == 'string') && (obj.common.isAlphaNumeric(req.query.tag) == true)) { meshsettings += 'Tag=' + encodeURIComponent(req.query.tag) + '\r\n'; }
6416:                     if ((req.query.installflags != null) && (req.query.installflags != 0) && (parseInt(req.query.installflags) == req.query.installflags)) { meshsettings += 'InstallFlags=' + parseInt(req.query.installflags) + '\r\n'; }
6417:                 }
6418:                 if (req.query.id == '10006') { // Assistant settings and customizations
6419:                     if ((req.query.ac != null)) { meshsettings += 'AutoConnect=' + req.query.ac + '\r\n'; } // Set MeshCentral Assistant flags if needed. 0x01 = Always Connected, 0x02 = Not System Tray
6420:                     if (obj.args.assistantconfig) { for (var i in obj.args.assistantconfig) { meshsettings += obj.args.assistantconfig[i] + '\r\n'; } }
6421:                     if (domain.assistantconfig) { for (var i in domain.assistantconfig) { meshsettings += domain.assistantconfig[i] + '\r\n'; } }
6422:                     if ((domain.assistantnoproxy === true) || (obj.args.lanonly == true)) { meshsettings += 'ignoreProxyFile=1\r\n'; }
6423:                     if ((domain.assistantcustomization != null) && (typeof domain.assistantcustomization == 'object')) {
6424:                         if (typeof domain.assistantcustomization.title == 'string') { meshsettings += 'Title=' + domain.assistantcustomization.title + '\r\n'; }
6425:                         if (typeof domain.assistantcustomization.image == 'string') {
6426:                             try { meshsettings += 'Image=' + Buffer.from(obj.fs.readFileSync(parent.getConfigFilePath(domain.assistantcustomization.image)), 'binary').toString('base64') + '\r\n'; } catch (ex) { console.log(ex); }
6427:                         }
6428:                         if (req.query.ac != '4') {
6429:                             // Send with custom filename followed by device group name
6430:                             if (typeof domain.assistantcustomization.filename == 'string') { meshfilename = meshfilename.split('MeshCentralAssistant').join(domain.assistantcustomization.filename); }
6431:                         } else {
6432:                             // Send with custom filename, no device group name
6433:                             if (typeof domain.assistantcustomization.filename == 'string') { meshfilename = domain.assistantcustomization.filename + '.exe'; } else { meshfilename = 'MeshCentralAssistant.exe'; }
6434:                         }
6435:                     }
6436:                 } else { // Add agent customization, not for Assistant
6437:                     if (obj.args.agentconfig) { for (var i in obj.args.agentconfig) { meshsettings += obj.args.agentconfig[i] + '\r\n'; } }
6438:                     if (domain.agentconfig) { for (var i in domain.agentconfig) { meshsettings += domain.agentconfig[i] + '\r\n'; } }
6439:                     if ((domain.agentnoproxy === true) || (obj.args.lanonly == true)) { meshsettings += 'ignoreProxyFile=1\r\n'; }
6440:                     if (domain.agentcustomization != null) {
6441:                         if (domain.agentcustomization.displayname != null) { meshsettings += 'displayName=' + domain.agentcustomization.displayname + '\r\n'; }
6442:                         if (domain.agentcustomization.description != null) { meshsettings += 'description=' + domain.agentcustomization.description + '\r\n'; }
6443:                         if (domain.agentcustomization.companyname != null) { meshsettings += 'companyName=' + domain.agentcustomization.companyname + '\r\n'; }
6444:                         if (domain.agentcustomization.servicename != null) { meshsettings += 'meshServiceName=' + domain.agentcustomization.servicename + '\r\n'; }
6445:                         if (domain.agentcustomization.filename != null) { meshsettings += 'fileName=' + domain.agentcustomization.filename + '\r\n'; }
6446:                         if (domain.agentcustomization.image != null) { meshsettings += 'image=' + domain.agentcustomization.image + '\r\n'; }
6447:                         if (domain.agentcustomization.foregroundcolor != null) { meshsettings += checkAgentColorString('foreground=', domain.agentcustomization.foregroundcolor); }
6448:                         if (domain.agentcustomization.backgroundcolor != null) { meshsettings += checkAgentColorString('background=', domain.agentcustomization.backgroundcolor); }
6449:                     }
6450:                     if (domain.agentTranslations != null) { meshsettings += 'translation=' + domain.agentTranslations + '\r\n'; } // Translation strings, not for MeshCentral Assistant
6451:                 }
6452:                 setContentDispositionHeader(res, 'application/octet-stream', meshfilename, null, argentInfo.rname);
6453:                 if (argentInfo.mtime != null) { res.setHeader('Last-Modified', argentInfo.mtime.toUTCString()); }
6454:                 if (domain.meshAgentBinaries && domain.meshAgentBinaries[req.query.id]) {
6455:                     obj.parent.exeHandler.streamExeWithMeshPolicy({ platform: 'win32', sourceFileName: domain.meshAgentBinaries[req.query.id].path, destinationStream: res, msh: meshsettings, peinfo: domain.meshAgentBinaries[req.query.id].pe });
6456:                 } else {
6457:                     obj.parent.exeHandler.streamExeWithMeshPolicy({ platform: 'win32', sourceFileName: obj.parent.meshAgentBinaries[req.query.id].path, destinationStream: res, msh: meshsettings, peinfo: obj.parent.meshAgentBinaries[req.query.id].pe });
6458:                 }
6459:                 return;
6460:             }
6461:         } else if (req.query.script != null) {
6462:             if ((domain.loginkey != null) && (domain.loginkey.indexOf(req.query.key) == -1)) { try { res.sendStatus(404); } catch (ex) { } return; } // Check 3FA URL key
6463: 
6464:             // Send a specific mesh install script back
6465:             var scriptInfo = obj.parent.meshAgentInstallScripts[req.query.script];
6466:             if (scriptInfo == null) { try { res.sendStatus(404); } catch (ex) { } return; }
6467:             setContentDispositionHeader(res, 'application/octet-stream', scriptInfo.rname, null, 'script');
6468:             var data = scriptInfo.data;
6469:             var cmdoptions = { wgetoptionshttp: '', wgetoptionshttps: '', curloptionshttp: '-L ', curloptionshttps: '-L ' }
6470:             if (obj.isTrustedCert(domain) != true) {
6471:                 cmdoptions.wgetoptionshttps += '--no-check-certificate ';
6472:                 cmdoptions.curloptionshttps += '-k ';
6473:             }
6474:             if (domain.agentnoproxy === true) {
6475:                 cmdoptions.wgetoptionshttp += '--no-proxy ';
6476:                 cmdoptions.wgetoptionshttps += '--no-proxy ';
6477:                 cmdoptions.curloptionshttp += '--noproxy \'*\' ';
6478:                 cmdoptions.curloptionshttps += '--noproxy \'*\' ';
6479:             }
6480:             for (var i in cmdoptions) { data = data.split('{{{' + i + '}}}').join(cmdoptions[i]); }
6481:             res.send(data);
6482:             return;
6483:         } else if (req.query.meshcmd != null) {
6484:             if ((domain.loginkey != null) && (domain.loginkey.indexOf(req.query.key) == -1)) { try { res.sendStatus(404); } catch (ex) { } return; } // Check 3FA URL key
6485: 
6486:             // Send meshcmd for a specific platform back
6487:             var agentid = parseInt(req.query.meshcmd);
6488: 
6489:             // If the agentid is 3 or 4, check if we have a signed MeshCmd.exe
6490:             if ((agentid == 3) && (obj.parent.meshAgentBinaries[11000] != null)) { // Signed Windows MeshCmd.exe x86-32
6491:                 var stats = null, meshCmdPath = obj.parent.meshAgentBinaries[11000].path;
6492:                 try { stats = obj.fs.statSync(meshCmdPath); } catch (e) { }
6493:                 if ((stats != null)) {
6494:                     setContentDispositionHeader(res, 'application/octet-stream', 'meshcmd.exe', null, 'meshcmd');
6495:                     res.sendFile(meshCmdPath); return;
6496:                 }
6497:             } else if ((agentid == 4) && (obj.parent.meshAgentBinaries[11001] != null)) { // Signed Windows MeshCmd64.exe x86-64
6498:                 var stats = null, meshCmd64Path = obj.parent.meshAgentBinaries[11001].path;
6499:                 try { stats = obj.fs.statSync(meshCmd64Path); } catch (e) { }
6500:                 if ((stats != null)) {
6501:                     setContentDispositionHeader(res, 'application/octet-stream', 'meshcmd.exe', null, 'meshcmd');
6502:                     res.sendFile(meshCmd64Path); return;
6503:                 }
6504:             } else if ((agentid == 43) && (obj.parent.meshAgentBinaries[11002] != null)) { // Signed Windows MeshCmd64.exe ARM-64
6505:                 var stats = null, meshCmdAMR64Path = obj.parent.meshAgentBinaries[11002].path;
6506:                 try { stats = obj.fs.statSync(meshCmdAMR64Path); } catch (e) { }
6507:                 if ((stats != null)) {
6508:                     setContentDispositionHeader(res, 'application/octet-stream', 'meshcmd-arm64.exe', null, 'meshcmd');
6509:                     res.sendFile(meshCmdAMR64Path); return;
6510:                 }
6511:             }
6512: 
6513:             // No signed agents, we are going to merge a new MeshCmd.
6514:             if (((agentid == 3) || (agentid == 4)) && (obj.parent.meshAgentBinaries[agentid + 10000] != null)) { agentid += 10000; } // Avoid merging javascript to a signed mesh agent.
6515:             var argentInfo = obj.parent.meshAgentBinaries[agentid];
6516:             if (domain.meshAgentBinaries && domain.meshAgentBinaries[agentid]) { argentInfo = domain.meshAgentBinaries[agentid]; }
6517:             if ((argentInfo == null) || (obj.parent.defaultMeshCmd == null)) { try { res.sendStatus(404); } catch (ex) { } return; }
6518:             setContentDispositionHeader(res, 'application/octet-stream', 'meshcmd' + ((req.query.meshcmd <= 4) ? '.exe' : ''), null, 'meshcmd');
6519:             res.statusCode = 200;
6520: 
6521:             if (argentInfo.signedMeshCmdPath != null) {
6522:                 // If we have a pre-signed MeshCmd, send that.
6523:                 res.sendFile(argentInfo.signedMeshCmdPath);
6524:             } else {
6525:                 // Merge JavaScript to a unsigned agent and send that.
6526:                 obj.parent.exeHandler.streamExeWithJavaScript({ platform: argentInfo.platform, sourceFileName: argentInfo.path, destinationStream: res, js: Buffer.from(obj.parent.defaultMeshCmd, 'utf8'), peinfo: argentInfo.pe });
6527:             }
6528:             return;
6529:         } else if (req.query.meshaction != null) {
6530:             if ((domain.loginkey != null) && (domain.loginkey.indexOf(req.query.key) == -1)) { try { res.sendStatus(404); } catch (ex) { } return; } // Check 3FA URL key
6531:             var user = obj.users[req.session.userid];
6532:             if (user == null) {
6533:                 // Check if we have an authentication cookie
6534:                 var c = obj.parent.decodeCookie(req.query.auth, obj.parent.loginCookieEncryptionKey);
6535:                 if (c == null) { try { res.sendStatus(404); } catch (ex) { } return; }
6536: 
6537:                 // Download tools using a cookie
6538:                 if (c.download == req.query.meshaction) {
6539:                     if (req.query.meshaction == 'winrouter') {
6540:                         var p = null;
6541:                         if (obj.parent.meshToolsBinaries['MeshCentralRouter']) { p = obj.parent.meshToolsBinaries['MeshCentralRouter'].path; }
6542:                         if ((p == null) || (!obj.fs.existsSync(p))) { p = obj.parent.getAgentBinaryPath('MeshCentralRouter.exe'); }
6543:                         if (obj.fs.existsSync(p)) {
6544:                             setContentDispositionHeader(res, 'application/octet-stream', 'MeshCentralRouter.exe', null, 'MeshCentralRouter.exe');
6545:                             try { res.sendFile(p); } catch (ex) { }
6546:                         } else { try { res.sendStatus(404); } catch (ex) { } }
6547:                         return;
6548:                     } else if (req.query.meshaction == 'winassistant') {
6549:                         var p = null;
6550:                         if (obj.parent.meshToolsBinaries['MeshCentralAssistant']) { p = obj.parent.meshToolsBinaries['MeshCentralAssistant'].path; }
6551:                         if ((p == null) || (!obj.fs.existsSync(p))) { p = obj.parent.getAgentBinaryPath('MeshCentralAssistant.exe'); }
6552:                         if (obj.fs.existsSync(p)) {
6553:                             setContentDispositionHeader(res, 'application/octet-stream', 'MeshCentralAssistant.exe', null, 'MeshCentralAssistant.exe');
6554:                             try { res.sendFile(p); } catch (ex) { }
6555:                         } else { try { res.sendStatus(404); } catch (ex) { } }
6556:                         return;
6557:                     } else if (req.query.meshaction == 'macrouter') {
```

## Lines 6717-7127
```text
6717:                             response += '<td style=text-align:center>' + d.currentAgent + '</td><td><a download href="' + d.downloadUrl + '">Download</a></td><td style=text-align:right>' + d.filestats.size + '</td>';
6718:                             if (d.currentAgent) { response += '<td><a download href="' + d.agentUrl + '">Download</a></td>'; } else { response += '<td></td>'; }
6719:                             response += '<td>' + d.fileSplit[1].toLowerCase() + '</td><td>' + d.fileSplit[2] + '</td><td><a href="' + d.deleteUrl + '">Delete</a></td></tr>';
6720:                         }
6721:                     }
6722:                     response += '</table><a href="' + req.originalUrl.split('?')[0] + (req.query.key ? ('?key=' + encodeURIComponent(req.query.key)) : '') + '">Mesh Agents</a></body></html>';
6723:                     res.send(response);
6724:                     return;
6725:                 }
6726:             }
6727: 
6728:             if (req.query.cores != null) {
6729:                 // Send list of agent cores
6730:                 var response = '<html><head><title>Mesh Agents Cores</title><style>table,th,td { border:1px solid black;border-collapse:collapse;padding:3px; }</style></head><body style=overflow:auto><table>';
6731:                 response += '<tr style="background-color:lightgray"><th>Name</th><th>Size</th><th>Comp</th><th>Decompressed Hash SHA384</th></tr>';
6732:                 for (var i in parent.defaultMeshCores) {
6733:                     response += '<tr><td>' + i.split(' ').join('&nbsp;') + '</td><td style="text-align:right"><a download href="/meshagents?dlcore=' + i + (req.query.key ? ('&key=' + encodeURIComponent(req.query.key)) : '') + '">' + parent.defaultMeshCores[i].length + '</a></td><td style="text-align:right"><a download href="/meshagents?dlccore=' + i + (req.query.key ? ('&key=' + encodeURIComponent(req.query.key)) : '') + '">' + parent.defaultMeshCoresDeflate[i].length + '</a></td><td>' + Buffer.from(parent.defaultMeshCoresHash[i], 'binary').toString('hex') + '</td></tr>';
6734:                 }
6735:                 response += '</table><a href="' + req.originalUrl.split('?')[0] + (req.query.key ? ('?key=' + encodeURIComponent(req.query.key)) : '') + '">Mesh Agents</a></body></html>';
6736:                 res.send(response);
6737:                 return;
6738:             }
6739: 
6740:             if (req.query.dlcore != null) {
6741:                 // Download mesh core
6742:                 var bin = parent.defaultMeshCores[req.query.dlcore];
6743:                 if ((bin == null) || (bin.length < 5)) { try { res.sendStatus(404); } catch (ex) { } return; }
6744:                 setContentDispositionHeader(res, 'application/octet-stream', encodeURIComponent(req.query.dlcore) + '.js', null, 'meshcore.js');
6745:                 res.send(bin.slice(4));
6746:                 return;
6747:             }
6748: 
6749:             if (req.query.dlccore != null) {
6750:                 // Download compressed mesh core
6751:                 var bin = parent.defaultMeshCoresDeflate[req.query.dlccore];
6752:                 if (bin == null) { try { res.sendStatus(404); } catch (ex) { } return; }
6753:                 setContentDispositionHeader(res, 'application/octet-stream', req.query.dlccore + '.js.deflate', null, 'meshcore.js.deflate');
6754:                 res.send(bin);
6755:                 return;
6756:             }
6757: 
6758:             // Send a list of available mesh agents
6759:             var response = '<html><head><title>Mesh Agents</title><style>table,th,td { border:1px solid black;border-collapse:collapse;padding:3px; }</style></head><body style=overflow:auto><table>';
6760:             response += '<tr style="background-color:lightgray"><th>ID</th><th>Description</th><th>Link</th><th>Size</th><th>SHA384</th><th>MeshCmd</th></tr>';
6761:             var originalUrl = req.originalUrl.split('?')[0];
6762:             for (var agentid in obj.parent.meshAgentBinaries) {
6763:                 if ((agentid >= 10000) && (agentid != 10005)) continue;
6764:                 var agentinfo = obj.parent.meshAgentBinaries[agentid];
6765:                 if (domain.meshAgentBinaries && domain.meshAgentBinaries[agentid]) { argentInfo = domain.meshAgentBinaries[agentid]; }
6766:                 response += '<tr><td>' + agentinfo.id + '</td><td>' + agentinfo.desc.split(' ').join('&nbsp;') + '</td>';
6767:                 response += '<td><a download href="' + originalUrl + '?id=' + agentinfo.id + (req.query.key ? ('&key=' + encodeURIComponent(req.query.key)) : '') + '">' + agentinfo.rname + '</a>';
6768:                 if ((user.siteadmin == 0xFFFFFFFF) || ((Array.isArray(obj.parent.config.settings.agentcoredumpusers)) && (obj.parent.config.settings.agentcoredumpusers.indexOf(user._id) >= 0))) {
6769:                     if ((agentid == 3) || (agentid == 4)) { response += ', <a download href="' + originalUrl + '?id=' + agentinfo.id + '&pdb=1' + (req.query.key ? ('&key=' + encodeURIComponent(req.query.key)) : '') + '">PDB</a>'; }
6770:                 }
6771:                 if (agentinfo.zdata != null) { response += ', <a download href="' + originalUrl + '?id=' + agentinfo.id + '&zip=1' + (req.query.key ? ('&key=' + encodeURIComponent(req.query.key)) : '') + '">ZIP</a>'; }
6772:                 response += '</td>';
6773:                 response += '<td>' + agentinfo.size + '</td><td>' + agentinfo.hashhex + '</td>';
6774:                 response += '<td><a download href="' + originalUrl + '?meshcmd=' + agentinfo.id + (req.query.key ? ('&key=' + encodeURIComponent(req.query.key)) : '') + '">' + agentinfo.rname.replace('agent', 'cmd') + '</a></td></tr>';
6775:             }
6776:             response += '</table>';
6777:             response += '<a href="' + originalUrl + '?cores=1' + (req.query.key ? ('&key=' + encodeURIComponent(req.query.key)) : '') + '">MeshCores</a> ';
6778:             if (coreDumpsAllowed) { response += '<a href="' + originalUrl + '?dumps=1' + (req.query.key ? ('&key=' + encodeURIComponent(req.query.key)) : '') + '">MeshAgent Crash Dumps</a>'; }
6779:             response += '</body></html>';
6780:             res.send(response);
6781:             return;
6782:         }
6783:     };
6784: 
6785:     // generate the server url
6786:     obj.generateBaseURL = function (domain, req) {
6787:         var serverName = obj.getWebServerName(domain, req);
6788:         var httpsPort = ((args.aliasport == null) ? args.port : args.aliasport); // Use HTTPS alias port is specified
6789:         var xdomain = (domain.dns == null) ? domain.id : '';
6790:         if (xdomain != '') xdomain += '/';
6791:         return ('https://' + serverName + ':' + httpsPort + '/' + xdomain);
6792:     }
6793: 
6794:     // Get the web server hostname. This may change if using a domain with a DNS name.
6795:     obj.getWebServerName = function (domain, req) {
6796:         if (domain.dns != null) return domain.dns;
6797:         if ((obj.certificates.CommonName == 'un-configured') && (req != null) && (req.headers != null) && (typeof req.headers.host == 'string')) { return req.headers.host.split(':')[0]; }
6798:         return obj.certificates.CommonName;
6799:     }
6800: 
6801:     // Return true if this is an allowed HTTP request origin hostname.
6802:     obj.CheckWebServerOriginName = function (domain, req) {
6803:         if (domain.allowedorigin === true) return true; // Ignore origin
6804:         if (typeof req.headers.origin != 'string') return true; // No origin in the header, this is a desktop app
6805:         let originUrl; try { originUrl = new URL(req.headers.origin); } catch (ex) { return false; }
6806:         if (!originUrl.hostname) return false; // Origin hostname is not valid
6807:         if (Array.isArray(domain.allowedorigin)) return (domain.allowedorigin.indexOf(originUrl.hostname) >= 0); // Check if this is an allowed origin from an explicit list
6808:         if (domain.dns != null) return (domain.dns == originUrl.hostname); // Match the domain DNS
6809:         return (obj.getWebServerName(domain, req) == originUrl.hostname); // Match the server hostname
6810:     }
6811: 
6812:     // Create a OSX mesh agent installer
6813:     obj.handleMeshOsxAgentRequest = function (req, res) {
6814:         const domain = getDomain(req, res);
6815:         if (domain == null) { parent.debug('web', 'handleRootRequest: invalid domain.'); try { res.sendStatus(404); } catch (ex) { } return; }
6816:         if (req.query.id == null) { res.sendStatus(404); return; }
6817: 
6818:         // If required, check if this user has rights to do this
6819:         if ((obj.parent.config.settings != null) && ((obj.parent.config.settings.lockagentdownload == true) || (domain.lockagentdownload == true)) && (req.session.userid == null)) { res.sendStatus(401); return; }
6820: 
6821:         // Send a specific mesh agent back
6822:         var argentInfo = obj.parent.meshAgentBinaries[req.query.id];
6823:         if (domain.meshAgentBinaries && domain.meshAgentBinaries[req.query.id]) { argentInfo = domain.meshAgentBinaries[req.query.id]; }
6824:         if ((argentInfo == null) || (req.query.meshid == null)) { res.sendStatus(404); return; }
6825: 
6826:         // Check if the meshid is a time limited, encrypted cookie
6827:         var meshcookie = obj.parent.decodeCookie(req.query.meshid, obj.parent.invitationLinkEncryptionKey);
6828:         if ((meshcookie != null) && (meshcookie.m != null)) { req.query.meshid = meshcookie.m; }
6829: 
6830:         // We are going to embed the .msh file into the Windows executable (signed or not).
6831:         // First, fetch the mesh object to build the .msh file
6832:         var mesh = obj.meshes['mesh/' + domain.id + '/' + req.query.meshid];
6833:         if (mesh == null) { res.sendStatus(401); return; }
6834: 
6835:         // If required, check if this user has rights to do this
6836:         if ((obj.parent.config.settings != null) && ((obj.parent.config.settings.lockagentdownload == true) || (domain.lockagentdownload == true))) {
6837:             if ((domain.id != mesh.domain) || ((obj.GetMeshRights(req.session.userid, mesh) & 1) == 0)) { res.sendStatus(401); return; }
6838:         }
6839: 
6840:         var meshidhex = Buffer.from(req.query.meshid.replace(/\@/g, '+').replace(/\$/g, '/'), 'base64').toString('hex').toUpperCase();
6841:         var serveridhex = Buffer.from(obj.agentCertificateHashBase64.replace(/\@/g, '+').replace(/\$/g, '/'), 'base64').toString('hex').toUpperCase();
6842: 
6843:         // Get the agent connection server name
6844:         var serverName = obj.getWebServerName(domain, req);
6845:         if (typeof obj.args.agentaliasdns == 'string') { serverName = obj.args.agentaliasdns; }
6846: 
6847:         // Build the agent connection URL. If we are using a sub-domain or one with a DNS, we need to craft the URL correctly.
6848:         var xdomain = (domain.dns == null) ? domain.id : '';
6849:         if (xdomain != '') xdomain += '/';
6850:         var meshsettings = '\r\nMeshName=' + mesh.name + '\r\nMeshType=' + mesh.mtype + '\r\nMeshID=0x' + meshidhex + '\r\nServerID=' + serveridhex + '\r\n';
6851:         var httpsPort = ((obj.args.aliasport == null) ? obj.args.port : obj.args.aliasport); // Use HTTPS alias port is specified
6852:         if (obj.args.agentport != null) { httpsPort = obj.args.agentport; } // If an agent only port is enabled, use that.
6853:         if (obj.args.agentaliasport != null) { httpsPort = obj.args.agentaliasport; } // If an agent alias port is specified, use that.
6854:         if (obj.args.lanonly != true) { meshsettings += 'MeshServer=wss://' + serverName + ':' + httpsPort + '/' + xdomain + 'agent.ashx\r\n'; } else {
6855:             meshsettings += 'MeshServer=local\r\n';
6856:             if ((obj.args.localdiscovery != null) && (typeof obj.args.localdiscovery.key == 'string') && (obj.args.localdiscovery.key.length > 0)) { meshsettings += 'DiscoveryKey=' + obj.args.localdiscovery.key + '\r\n'; }
6857:         }
6858:         if ((req.query.tag != null) && (typeof req.query.tag == 'string') && (obj.common.isAlphaNumeric(req.query.tag) == true)) { meshsettings += 'Tag=' + encodeURIComponent(req.query.tag) + '\r\n'; }
6859:         if ((req.query.installflags != null) && (req.query.installflags != 0) && (parseInt(req.query.installflags) == req.query.installflags)) { meshsettings += 'InstallFlags=' + parseInt(req.query.installflags) + '\r\n'; }
6860:         if ((domain.agentnoproxy === true) || (obj.args.lanonly == true)) { meshsettings += 'ignoreProxyFile=1\r\n'; }
6861:         if (obj.args.agentconfig) { for (var i in obj.args.agentconfig) { meshsettings += obj.args.agentconfig[i] + '\r\n'; } }
6862:         if (domain.agentconfig) { for (var i in domain.agentconfig) { meshsettings += domain.agentconfig[i] + '\r\n'; } }
6863:         if (domain.agentcustomization != null) { // Add agent customization
6864:             if (domain.agentcustomization.displayname != null) { meshsettings += 'displayName=' + domain.agentcustomization.displayname + '\r\n'; }
6865:             if (domain.agentcustomization.description != null) { meshsettings += 'description=' + domain.agentcustomization.description + '\r\n'; }
6866:             if (domain.agentcustomization.companyname != null) { meshsettings += 'companyName=' + domain.agentcustomization.companyname + '\r\n'; }
6867:             if (domain.agentcustomization.servicename != null) { meshsettings += 'meshServiceName=' + domain.agentcustomization.servicename + '\r\n'; }
6868:             if (domain.agentcustomization.filename != null) { meshsettings += 'fileName=' + domain.agentcustomization.filename + '\r\n'; }
6869:             if (domain.agentcustomization.image != null) { meshsettings += 'image=' + domain.agentcustomization.image + '\r\n'; }
6870:             if (domain.agentcustomization.foregroundcolor != null) { meshsettings += checkAgentColorString('foreground=', domain.agentcustomization.foregroundcolor); }
6871:             if (domain.agentcustomization.backgroundcolor != null) { meshsettings += checkAgentColorString('background=', domain.agentcustomization.backgroundcolor); }
6872:         }
6873:         if (domain.agentTranslations != null) { meshsettings += 'translation=' + domain.agentTranslations + '\r\n'; }
6874: 
6875:         // Setup the response output
6876:         // Customize the mesh agent file name
6877:         var meshfilename = 'MeshAgent-' + mesh.name + '.zip';
6878:         var meshexecutablename = 'meshagent';
6879:         var meshpkgname = 'MeshAgent.pkg';
6880:         if ((domain.agentcustomization != null) && (typeof domain.agentcustomization.filename == 'string')) {
6881:             meshfilename = meshfilename.split('MeshAgent').join(domain.agentcustomization.filename);
6882:             meshexecutablename = meshexecutablename.split('meshagent').join(domain.agentcustomization.filename);
6883:             meshpkgname = meshpkgname.split('MeshAgent').join(domain.agentcustomization.filename);
6884:         }
6885: 
6886:         // Customise the mesh agent display name
6887:         var meshdisplayname = 'Mesh Agent';
6888:         if ((domain.agentcustomization != null) && (typeof domain.agentcustomization.displayname == 'string')) {
6889:             meshdisplayname = meshdisplayname.split('Mesh Agent').join(domain.agentcustomization.displayname);
6890:         }
6891: 
6892:         // Customise the mesh agent service name
6893:         var meshservicename = 'meshagent';
6894:         if ((domain.agentcustomization != null) && (typeof domain.agentcustomization.servicename == 'string')) {
6895:             meshservicename = meshservicename.split('meshagent').join(domain.agentcustomization.servicename);
6896:         }
6897: 
6898:         // Customise the mesh agent company name
6899:         var meshcompanyname = 'meshagent';
6900:         if ((domain.agentcustomization != null) && (typeof domain.agentcustomization.companyname == 'string')) {
6901:             meshcompanyname = meshcompanyname.split('meshagent').join(domain.agentcustomization.companyname);
6902:         }
6903: 
6904:         // Set the agent download including the mesh name.
6905:         setContentDispositionHeader(res, 'application/octet-stream', meshfilename, null, 'MeshAgent.zip');
6906: 
6907:         // Create a flat XAR macOS installer package. Bundle .mpkg installers are rejected by recent macOS versions.
6908:         const macosInstallerOpts = {
6909:             agentPath: argentInfo.path,
6910:             meshSettings: meshsettings,
6911:             meshName: mesh.name.split(']').join('').split('[').join(''), // We can't have ']]' in the string since it will terminate the CDATA.
6912:             executableName: meshexecutablename,
6913:             packageName: meshpkgname,
6914:             displayName: meshdisplayname,
6915:             serviceName: meshservicename,
6916:             companyName: meshcompanyname
6917:         };
6918: 
6919:         if ((domain.agentcustomization != null) && (typeof domain.agentcustomization.macosinstallerimage == 'string')) {
6920:             macosInstallerOpts.backgroundPath = parent.path.join(parent.datapath, domain.agentcustomization.macosinstallerimage);
6921:         }
6922: 
6923: 		require('./macosinstaller').createMacOSInstaller(macosInstallerOpts).then(async function (installer) {
6924: 			const { Writable } = require('node:stream');
6925: 			const zip = require('@zip.js/zip.js');
6926: 
6927: 			const zipWriter = new zip.ZipWriter(Writable.toWeb(res), { level: 5 });
6928: 
6929: 			await zipWriter.add(
6930: 				meshpkgname,
6931: 				new zip.Uint8ArrayReader(installer.pkg)
6932: 			);
6933: 
6934: 			await zipWriter.add(
6935: 				'Uninstall.command',
6936: 				(typeof installer.uninstall === 'string')
6937: 					? new zip.TextReader(installer.uninstall)
6938: 					: new zip.Uint8ArrayReader(installer.uninstall),
6939: 				{
6940: 					executable: true,
6941: 					unixMode: 0o100755
6942: 				}
6943: 			);
6944: 
6945: 			await zipWriter.close();
6946: 		}).catch(function (err) {
6947: 			parent.debug('web', 'Failed to build macOS MeshAgent package: ' + err);
6948: 			try {
6949: 				if (!res.headersSent) {
6950: 					res.sendStatus(500);
6951: 				} else {
6952: 					res.destroy(err);
6953: 				}
6954: 			} catch (ex) { }
6955: 		});
6956:     }
6957: 
6958:     // Return a .msh file from a given request, id is the device group identifier or encrypted cookie with the identifier.
6959:     function getMshFromRequest(req, res, domain) {
6960:         // If required, check if this user has rights to do this
6961:         if ((obj.parent.config.settings != null) && ((obj.parent.config.settings.lockagentdownload == true) || (domain.lockagentdownload == true)) && (req.session.userid == null)) { return null; }
6962: 
6963:         // Check if the meshid is a time limited, encrypted cookie
6964:         var meshcookie = obj.parent.decodeCookie(req.query.id, obj.parent.invitationLinkEncryptionKey);
6965:         if ((meshcookie != null) && (meshcookie.m != null)) { req.query.id = meshcookie.m; }
6966: 
6967:         // Fetch the mesh object
6968:         var mesh = obj.meshes['mesh/' + domain.id + '/' + req.query.id];
6969:         if (mesh == null) { return null; }
6970: 
6971:         // If needed, check if this user has rights to do this
6972:         if ((obj.parent.config.settings != null) && ((obj.parent.config.settings.lockagentdownload == true) || (domain.lockagentdownload == true))) {
6973:             if ((domain.id != mesh.domain) || ((obj.GetMeshRights(req.session.userid, mesh) & 1) == 0)) { return null; }
6974:         }
6975: 
6976:         var meshidhex = Buffer.from(req.query.id.replace(/\@/g, '+').replace(/\$/g, '/'), 'base64').toString('hex').toUpperCase();
6977:         var serveridhex = Buffer.from(obj.agentCertificateHashBase64.replace(/\@/g, '+').replace(/\$/g, '/'), 'base64').toString('hex').toUpperCase();
6978: 
6979:         // Get the agent connection server name
6980:         var serverName = obj.getWebServerName(domain, req);
6981:         if (typeof obj.args.agentaliasdns == 'string') { serverName = obj.args.agentaliasdns; }
6982: 
6983:         // Build the agent connection URL. If we are using a sub-domain or one with a DNS, we need to craft the URL correctly.
6984:         var xdomain = (domain.dns == null) ? domain.id : '';
6985:         if (xdomain != '') xdomain += '/';
6986:         var meshsettings = '\r\nMeshName=' + mesh.name + '\r\nMeshType=' + mesh.mtype + '\r\nMeshID=0x' + meshidhex + '\r\nServerID=' + serveridhex + '\r\n';
6987:         var httpsPort = ((obj.args.aliasport == null) ? obj.args.port : obj.args.aliasport); // Use HTTPS alias port is specified
6988:         if (obj.args.agentport != null) { httpsPort = obj.args.agentport; } // If an agent only port is enabled, use that.
6989:         if (obj.args.agentaliasport != null) { httpsPort = obj.args.agentaliasport; } // If an agent alias port is specified, use that.
6990:         if (obj.args.lanonly != true) { meshsettings += 'MeshServer=wss://' + serverName + ':' + httpsPort + '/' + xdomain + 'agent.ashx\r\n'; } else {
6991:             meshsettings += 'MeshServer=local\r\n';
6992:             if ((obj.args.localdiscovery != null) && (typeof obj.args.localdiscovery.key == 'string') && (obj.args.localdiscovery.key.length > 0)) { meshsettings += 'DiscoveryKey=' + obj.args.localdiscovery.key + '\r\n'; }
6993:         }
6994:         if ((req.query.tag != null) && (typeof req.query.tag == 'string') && (obj.common.isAlphaNumeric(req.query.tag) == true)) { meshsettings += 'Tag=' + encodeURIComponent(req.query.tag) + '\r\n'; }
6995:         if ((req.query.installflags != null) && (req.query.installflags != 0) && (parseInt(req.query.installflags) == req.query.installflags)) { meshsettings += 'InstallFlags=' + parseInt(req.query.installflags) + '\r\n'; }
6996:         if ((domain.agentnoproxy === true) || (obj.args.lanonly == true)) { meshsettings += 'ignoreProxyFile=1\r\n'; }
6997:         if (obj.args.agentconfig) { for (var i in obj.args.agentconfig) { meshsettings += obj.args.agentconfig[i] + '\r\n'; } }
6998:         if (domain.agentconfig) { for (var i in domain.agentconfig) { meshsettings += domain.agentconfig[i] + '\r\n'; } }
6999:         if (domain.agentcustomization != null) { // Add agent customization
7000:             if (domain.agentcustomization.displayname != null) { meshsettings += 'displayName=' + domain.agentcustomization.displayname + '\r\n'; }
7001:             if (domain.agentcustomization.description != null) { meshsettings += 'description=' + domain.agentcustomization.description + '\r\n'; }
7002:             if (domain.agentcustomization.companyname != null) { meshsettings += 'companyName=' + domain.agentcustomization.companyname + '\r\n'; }
7003:             if (domain.agentcustomization.servicename != null) { meshsettings += 'meshServiceName=' + domain.agentcustomization.servicename + '\r\n'; }
7004:             if (domain.agentcustomization.filename != null) { meshsettings += 'fileName=' + domain.agentcustomization.filename + '\r\n'; }
7005:             if (domain.agentcustomization.image != null) { meshsettings += 'image=' + domain.agentcustomization.image + '\r\n'; }
7006:             if (domain.agentcustomization.foregroundcolor != null) { meshsettings += checkAgentColorString('foreground=', domain.agentcustomization.foregroundcolor); }
7007:             if (domain.agentcustomization.backgroundcolor != null) { meshsettings += checkAgentColorString('background=', domain.agentcustomization.backgroundcolor); }
7008:         }
7009:         if (domain.agentTranslations != null) { meshsettings += 'translation=' + domain.agentTranslations + '\r\n'; }
7010:         return meshsettings;
7011:     }
7012: 
7013:     // Handle a request to download a mesh settings
7014:     obj.handleMeshSettingsRequest = function (req, res) {
7015:         const domain = getDomain(req);
7016:         if (domain == null) { return; }
7017:         //if ((domain.id !== '') || (!req.session) || (req.session == null) || (!req.session.userid)) { res.sendStatus(401); return; }
7018: 
7019:         var meshsettings = getMshFromRequest(req, res, domain);
7020:         if (meshsettings == null) { res.sendStatus(401); return; }
7021: 
7022:         // Get the agent filename
7023:         var meshagentFilename = 'meshagent';
7024:         if ((domain.agentcustomization != null) && (typeof domain.agentcustomization.filename == 'string')) { meshagentFilename = domain.agentcustomization.filename; }
7025: 
7026:         setContentDispositionHeader(res, 'application/octet-stream', meshagentFilename + '.msh', null, 'meshagent.msh');
7027:         res.send(meshsettings);
7028:     };
7029: 
7030:     // Handle a request for power events
7031:     obj.handleDevicePowerEvents = function (req, res) {
7032:         const domain = checkUserIpAddress(req, res);
7033:         if (domain == null) { return; }
7034:         if ((domain.loginkey != null) && (domain.loginkey.indexOf(req.query.key) == -1)) { res.sendStatus(404); return; } // Check 3FA URL key
7035:         if ((domain.id !== '') || (!req.session) || (req.session == null) || (!req.session.userid) || (req.query.id == null) || (typeof req.query.id != 'string')) { res.sendStatus(401); return; }
7036:         var x = req.query.id.split('/');
7037:         var user = obj.users[req.session.userid];
7038:         if ((x.length != 3) || (x[0] != 'node') || (x[1] != domain.id) || (user == null) || (user.links == null)) { res.sendStatus(401); return; }
7039: 
7040:         obj.db.Get(req.query.id, function (err, docs) {
7041:             if (docs.length != 1) {
7042:                 res.sendStatus(401);
7043:             } else {
7044:                 var node = docs[0];
7045: 
7046:                 // Check if we have right to this node
7047:                 if (obj.GetNodeRights(user, node.meshid, node._id) == 0) { res.sendStatus(401); return; }
7048: 
7049:                 // See how we will convert UTC time to local time
7050:                 var localTimeOffset = 0;
7051:                 var timeConversionSystem = 0;
7052:                 if ((req.query.l != null) && (req.query.tz != null)) {
7053:                     timeConversionSystem = 1;
7054:                 } else if (req.query.tf != null) {
7055:                     // Get local time offset (bad way)
7056:                     timeConversionSystem = 2;
7057:                     localTimeOffset = parseInt(req.query.tf);
7058:                     if (isNaN(localTimeOffset)) { localTimeOffset = 0; }
7059:                 }
7060: 
7061:                 // Get the list of power events and send them
7062:                 setContentDispositionHeader(res, 'application/octet-stream', 'powerevents.csv', null, 'powerevents.csv');
7063:                 obj.db.getPowerTimeline(node._id, function (err, docs) {
7064:                     var xevents = ['UTC Time, Local Time, State, Previous State'], prevState = 0;
7065:                     for (var i in docs) {
7066:                         if (docs[i].power != prevState) {
7067:                             var timedoc = docs[i].time;
7068:                             if (typeof timedoc == 'string') {
7069:                                 timedoc = new Date(timedoc);
7070:                             }
7071:                             prevState = docs[i].power;
7072:                             var localTime = '';
7073:                             if (timeConversionSystem == 1) { // Good way
7074:                                 localTime = new Date(timedoc.getTime()).toLocaleString(req.query.l, { timeZone: req.query.tz })
7075:                             } else if (timeConversionSystem == 2) { // Bad way
7076:                                 localTime = new Date(timedoc.getTime() + (localTimeOffset * 60000)).toISOString();
7077:                                 localTime = localTime.substring(0, localTime.length - 1);
7078:                             }
7079:                             if (docs[i].oldPower != null) {
7080:                                 xevents.push('\"' + timedoc.toISOString() + '\",\"' + localTime + '\",' + docs[i].power + ',' + docs[i].oldPower);
7081:                             } else {
7082:                                 xevents.push('\"' + timedoc.toISOString() + '\",\"' + localTime + '\",' + docs[i].power);
7083:                             }
7084:                         }
7085:                     }
7086:                     res.send(xevents.join('\r\n'));
7087:                 });
7088:             }
7089:         });
7090:     }
7091: 
7092:     if (parent.pluginHandler != null) {
7093:         // Handle a plugin admin request
7094:         obj.handlePluginAdminReq = function (req, res) {
7095:             const domain = checkUserIpAddress(req, res);
7096:             if (domain == null) { return; }
7097:             if ((!req.session) || (req.session == null) || (!req.session.userid)) { res.sendStatus(401); return; }
7098:             var user = obj.users[req.session.userid];
7099:             if (user == null) { res.sendStatus(401); return; }
7100: 
7101:             parent.pluginHandler.handleAdminReq(req, res, user, obj);
7102:         }
7103: 
7104:         obj.handlePluginAdminPostReq = function (req, res) {
7105:             const domain = checkUserIpAddress(req, res);
7106:             if (domain == null) { return; }
7107:             if ((!req.session) || (req.session == null) || (!req.session.userid)) { res.sendStatus(401); return; }
7108:             var user = obj.users[req.session.userid];
7109:             if (user == null) { res.sendStatus(401); return; }
7110: 
7111:             parent.pluginHandler.handleAdminPostReq(req, res, user, obj);
7112:         }
7113: 
7114:         obj.handlePluginJS = function (req, res) {
7115:             const domain = checkUserIpAddress(req, res);
7116:             if (domain == null) { return; }
7117:             if ((!req.session) || (req.session == null) || (!req.session.userid)) { res.sendStatus(401); return; }
7118:             var user = obj.users[req.session.userid];
7119:             if (user == null) { res.sendStatus(401); return; }
7120: 
7121:             parent.pluginHandler.refreshJS(req, res);
7122:         }
7123:     }
7124: 
7125:     // Starts the HTTPS server, this should be called after the user/mesh tables are loaded
7126:     function serverStart() {
7127:         // Start the server, only after users and meshes are loaded from the database.
```

## Lines 7557-7768
```text
7557:                 if ((domain.myserver !== false) && ((domain.myserver == null) || (domain.myserver.backup === true))) { obj.app.get(url + 'backup.zip', handleBackupRequest); }
7558:                 if ((domain.myserver !== false) && ((domain.myserver == null) || (domain.myserver.restore === true))) { obj.app.post(url + 'restoreserver.ashx', obj.bodyParser.urlencoded({ extended: false }), handleRestoreRequest); }
7559:                 obj.app.get(url + 'terms', handleTermsRequest);
7560:                 obj.app.get(url + 'xterm', handleXTermRequest);
7561:                 obj.app.get(url + 'login', handleRootRequest);
7562:                 obj.app.post(url + 'login', obj.bodyParser.urlencoded({ extended: false }), handleRootPostRequest);
7563:                 obj.app.post(url + 'tokenlogin', obj.bodyParser.urlencoded({ extended: false }), handleLoginRequest);
7564:                 obj.app.get(url + 'logout', handleLogoutRequest);
7565:                 obj.app.get(url + 'MeshServerRootCert.cer', handleRootCertRequest);
7566:                 obj.app.get(url + 'manifest.json', handleManifestRequest);
7567:                 obj.app.post(url + 'changepassword', obj.bodyParser.urlencoded({ extended: false }), handlePasswordChangeRequest);
7568:                 obj.app.post(url + 'deleteaccount', obj.bodyParser.urlencoded({ extended: false }), handleDeleteAccountRequest);
7569:                 obj.app.post(url + 'createaccount', obj.bodyParser.urlencoded({ extended: false }), handleCreateAccountRequest);
7570:                 obj.app.post(url + 'resetpassword', obj.bodyParser.urlencoded({ extended: false }), handleResetPasswordRequest);
7571:                 obj.app.post(url + 'resetaccount', obj.bodyParser.urlencoded({ extended: false }), handleResetAccountRequest);
7572:                 obj.app.get(url + 'checkmail', handleCheckMailRequest);
7573:                 obj.app.get(url + 'agentinvite', handleAgentInviteRequest);
7574:                 obj.app.get(url + 'userimage.ashx', handleUserImageRequest);
7575:                 obj.app.post(url + 'amtevents.ashx', obj.bodyParser.urlencoded({ extended: false }), obj.handleAmtEventRequest);
7576:                 obj.app.get(url + 'meshagents', obj.handleMeshAgentRequest);
7577:                 obj.app.get(url + 'messenger', handleMessengerRequest);
7578:                 obj.app.get(url + 'messenger.png', handleMessengerImageRequest);
7579:                 obj.app.get(url + 'meshosxagent', obj.handleMeshOsxAgentRequest);
7580:                 obj.app.get(url + 'meshsettings', obj.handleMeshSettingsRequest);
7581:                 obj.app.get(url + 'devicepowerevents.ashx', obj.handleDevicePowerEvents);
7582:                 obj.app.get(url + 'downloadfile.ashx', handleDownloadFile);
7583:                 obj.app.get(url + 'commander.ashx', handleMeshCommander);
7584:                 obj.app.post(url + 'uploadfile.ashx', obj.bodyParser.urlencoded({ extended: false }), handleUploadFile);
7585:                 obj.app.post(url + 'uploadfilebatch.ashx', obj.bodyParser.urlencoded({ extended: false }), handleUploadFileBatch);
7586:                 obj.app.post(url + 'customiconupload.ashx', handleCustomIconUpload);
7587:                 obj.app.post(url + 'customicondelete.ashx', obj.bodyParser.urlencoded({ extended: false }), handleCustomIconDelete);
7588:                 obj.app.get(url + 'icons/custom/*', handleCustomIconDownload);
7589:                 obj.app.post(url + 'uploadmeshcorefile.ashx', obj.bodyParser.urlencoded({ extended: false }), handleUploadMeshCoreFile);
7590:                 obj.app.post(url + 'oneclickrecovery.ashx', obj.bodyParser.urlencoded({ extended: false }), handleOneClickRecoveryFile);
7591:                 obj.app.get(url + 'userfiles/*', handleDownloadUserFiles);
7592:                 obj.app.ws(url + 'echo.ashx', handleEchoWebSocket);
7593:                 obj.app.ws(url + '2fahold.ashx', handle2faHoldWebSocket);
7594:                 obj.app.ws(url + 'apf.ashx', function (ws, req) { obj.parent.mpsserver.onWebSocketConnection(ws, req); })
7595:                 obj.app.get(url + 'webrelay.ashx', function (req, res) { res.send('Websocket connection expected'); });
7596:                 obj.app.get(url + 'health.ashx', function (req, res) { res.send('ok'); }); // TODO: Perform more server checking.
7597:                 obj.app.ws(url + 'webrelay.ashx', function (ws, req) { PerformWSSessionAuth(ws, req, false, handleRelayWebSocket); });
7598:                 obj.app.ws(url + 'webider.ashx', function (ws, req) { PerformWSSessionAuth(ws, req, false, function (ws1, req1, domain, user, cookie, authData) { obj.meshIderHandler.CreateAmtIderSession(obj, obj.db, ws1, req1, obj.args, domain, user); }); });
7599:                 obj.app.ws(url + 'control.ashx', function (ws, req) {
7600:                     getWebsocketArgs(ws, req, function (ws, req) {
7601:                         const domain = getDomain(req);
7602:                         if (obj.CheckWebServerOriginName(domain, req) == false) {
7603:                             try { ws.send(JSON.stringify({ action: 'close', cause: 'invalidorigin', msg: 'invalidorigin' })); } catch (ex) { }
7604:                             try { ws.close(); } catch (ex) { }
7605:                             return;
7606:                         }
7607:                         if ((domain.loginkey != null) && (domain.loginkey.indexOf(req.query.key) == -1)) { // Check 3FA URL key
7608:                             try { ws.send(JSON.stringify({ action: 'close', cause: 'noauth', msg: 'nokey' })); } catch (ex) { }
7609:                             try { ws.close(); } catch (ex) { }
7610:                             return;
7611:                         }
7612:                         PerformWSSessionAuth(ws, req, true, function (ws1, req1, domain, user, cookie, authData) {
7613:                             // Device share guest cookies are for relay sessions only, not control.ashx
7614:                             if (isDeviceShareGuestCookie(cookie)) {
7615:                                 try { ws.send(JSON.stringify({ action: 'close', cause: 'noauth', msg: 'noauth' })); } catch (ex) { }
7616:                                 try { ws.close(); } catch (ex) { }
7617:                                 return;
7618:                             }
7619:                             if (user == null) { // User is not authenticated, perform inner server authentication
7620:                                 if (req.headers['x-meshauth'] === '*') {
7621:                                     PerformWSSessionInnerAuth(ws, req, domain, function (ws1, req1, domain, user) { obj.meshUserHandler.CreateMeshUser(obj, obj.db, ws1, req1, obj.args, domain, user, authData); }); // User is authenticated
7622:                                 } else {
7623:                                     try { ws.send(JSON.stringify({ action: 'close', cause: 'noauth', msg: 'noauth' })); } catch (ex) { }
7624:                                     try { ws.close(); } catch (ex) { } // user is not authenticated and inner authentication was not requested, disconnect now.
7625:                                 }
7626:                             } else {
7627:                                 obj.meshUserHandler.CreateMeshUser(obj, obj.db, ws1, req1, obj.args, domain, user, authData); // User is authenticated
7628:                             }
7629:                         });
7630:                     });
7631:                 });
7632:                 obj.app.ws(url + 'devicefile.ashx', function (ws, req) { obj.meshDeviceFileHandler.CreateMeshDeviceFile(obj, ws, null, req, domain); });
7633:                 obj.app.get(url + 'devicefile.ashx', handleDeviceFile);
7634:                 obj.app.get(url + 'agentdownload.ashx', handleAgentDownloadFile);
7635:                 obj.app.get(url + 'logo.png', handleLogoRequest);
7636:                 obj.app.get(url + 'loginlogo.png', handleLoginLogoRequest);
7637:                 obj.app.get(url + 'pwalogo.png', handlePWALogoRequest);
7638:                 obj.app.post(url + 'translations', obj.bodyParser.urlencoded({ extended: false }), handleTranslationsRequest);
7639:                 obj.app.get(url + 'welcome.jpg', handleWelcomeImageRequest);
7640:                 obj.app.get(url + 'welcome.png', handleWelcomeImageRequest);
7641:                 obj.app.get(url + 'recordings.ashx', handleGetRecordings);
7642:                 obj.app.ws(url + 'recordings.ashx', handleGetRecordingsWebSocket);
7643:                 obj.app.get(url + 'player.htm', handlePlayerRequest);
7644:                 obj.app.get(url + 'player', handlePlayerRequest);
7645:                 obj.app.get(url + 'sharing', handleSharingRequest);
7646:                 obj.app.ws(url + 'agenttransfer.ashx', handleAgentFileTransfer); // Setup agent to/from server file transfer handler
7647:                 obj.app.ws(url + 'meshrelay.ashx', function (ws, req) {
7648:                     PerformWSSessionAuth(ws, req, true, function (ws1, req1, domain, user, cookie, authData) {
7649:                         if (((parent.config.settings.desktopmultiplex === true) || (domain.desktopmultiplex === true)) && (req.query.p == 2)) {
7650:                             obj.meshDesktopMultiplexHandler.CreateMeshRelay(obj, ws1, req1, domain, user, cookie); // Desktop multiplexor 1-to-n
7651:                         } else {
7652:                             obj.meshRelayHandler.CreateMeshRelay(obj, ws1, req1, domain, user, cookie); // Normal relay 1-to-1
7653:                         }
7654:                     });
7655:                 });
7656:                 if (obj.args.wanonly != true) { // If the server is not in WAN mode, allow server relayed connections.
7657:                     obj.app.ws(url + 'localrelay.ashx', function (ws, req) {
7658:                         PerformWSSessionAuth(ws, req, true, function (ws1, req1, domain, user, cookie, authData) {
7659:                             if ((user == null) || (cookie == null)) {
7660:                                 try { ws1.close(); } catch (ex) { }
7661:                             } else {
7662:                                 obj.meshRelayHandler.CreateLocalRelay(obj, ws1, req1, domain, user, cookie); // Local relay
7663:                             }
7664:                         });
7665:                     });
7666:                 }
7667:                 obj.app.get(url + 'invite', handleInviteRequest);
7668:                 obj.app.post(url + 'invite', obj.bodyParser.urlencoded({ extended: false }), handleInviteRequest);
7669: 
7670:                 if (parent.pluginHandler != null) {
7671:                     obj.app.get(url + 'pluginadmin.ashx', obj.handlePluginAdminReq);
7672:                     obj.app.post(url + 'pluginadmin.ashx', obj.bodyParser.urlencoded({ extended: false }), obj.handlePluginAdminPostReq);
7673:                     obj.app.get(url + 'pluginHandler.js', obj.handlePluginJS);
7674:                 }
7675: 
7676:                 // New account CAPTCHA request
7677:                 if ((domain.newaccountscaptcha != null) && (domain.newaccountscaptcha !== false)) {
7678:                     obj.app.get(url + 'newAccountCaptcha.ashx', handleNewAccountCaptchaRequest);
7679:                 }
7680: 
7681:                 // Check CrowdSec Bounser if configured
7682:                 if (parent.crowdSecBounser != null) {
7683:                     obj.app.get(url + 'captcha.ashx', handleCaptchaGetRequest);
7684:                     obj.app.post(url + 'captcha.ashx', obj.bodyParser.urlencoded({ extended: false }), handleCaptchaPostRequest);
7685:                 }
7686: 
7687:                 // Setup IP-KVM relay if supported
7688:                 if (domain.ipkvm) {
7689:                     obj.app.ws(url + 'ipkvm.ashx/*', function (ws, req) {
7690:                         const domain = getDomain(req);
7691:                         if (domain == null) { parent.debug('web', 'ipkvm: failed domain checks.'); try { ws.close(); } catch (ex) { } return; }
7692:                         parent.ipKvmManager.handleIpKvmWebSocket(domain, ws, req);
7693:                     });
7694:                     obj.app.get(url + 'ipkvm.ashx/*', function (req, res, next) {
7695:                         const domain = getDomain(req);
7696:                         if (domain == null) return;
7697:                         parent.ipKvmManager.handleIpKvmGet(domain, req, res, next);
7698:                     });
7699:                 }
7700: 
7701:                 // Setup RDP unless indicated as disabled
7702:                 if (domain.mstsc !== false) {
7703:                     obj.app.get(url + 'mstsc.html', function (req, res) { handleMSTSCRequest(req, res, 'mstsc'); });
7704:                     obj.app.ws(url + 'mstscrelay.ashx', function (ws, req) {
7705:                         const domain = getDomain(req);
7706:                         if (domain == null) { parent.debug('web', 'mstsc: failed checks.'); try { ws.close(); } catch (e) { } return; }
7707:                         // If no user is logged in and we have a default user, set it now.
7708:                         if ((req.session.userid == null) && (typeof obj.args.user == 'string') && (obj.users['user/' + domain.id + '/' + obj.args.user.toLowerCase()])) { req.session.userid = 'user/' + domain.id + '/' + obj.args.user.toLowerCase(); }
7709:                         try { require('./apprelays.js').CreateMstscRelay(obj, obj.db, ws, req, obj.args, domain); } catch (ex) { console.log(ex); }
7710:                     });
7711:                 }
7712: 
7713:                 // Setup SSH if needed
7714:                 if (domain.ssh === true) {
7715:                     obj.app.get(url + 'ssh.html', function (req, res) { handleMSTSCRequest(req, res, 'ssh'); });
7716:                     obj.app.ws(url + 'sshrelay.ashx', function (ws, req) {
7717:                         const domain = getDomain(req);
7718:                         if (domain == null) { parent.debug('web', 'ssh: failed checks.'); try { ws.close(); } catch (e) { } return; }
7719:                         // If no user is logged in and we have a default user, set it now.
7720:                         if ((req.session.userid == null) && (typeof obj.args.user == 'string') && (obj.users['user/' + domain.id + '/' + obj.args.user.toLowerCase()])) { req.session.userid = 'user/' + domain.id + '/' + obj.args.user.toLowerCase(); }
7721:                         try { require('./apprelays.js').CreateSshRelay(obj, obj.db, ws, req, obj.args, domain); } catch (ex) { console.log(ex); }
7722:                     });
7723:                     obj.app.ws(url + 'sshterminalrelay.ashx', function (ws, req) {
7724:                         PerformWSSessionAuth(ws, req, true, function (ws1, req1, domain, user, cookie, authData) {
7725:                             require('./apprelays.js').CreateSshTerminalRelay(obj, obj.db, ws1, req1, domain, user, cookie, obj.args);
7726:                         });
7727:                     });
7728:                     obj.app.ws(url + 'sshfilesrelay.ashx', function (ws, req) {
7729:                         PerformWSSessionAuth(ws, req, true, function (ws1, req1, domain, user, cookie, authData) {
7730:                             require('./apprelays.js').CreateSshFilesRelay(obj, obj.db, ws1, req1, domain, user, cookie, obj.args);
7731:                         });
7732:                     });
7733:                 }
7734: 
7735:                 // Setup firebase push only server
7736:                 if ((obj.parent.firebase != null) && (obj.parent.config.firebase)) {
7737:                     if (obj.parent.config.firebase.pushrelayserver) { parent.debug('email', 'Firebase-pushrelay-handler'); obj.app.post(url + 'firebaserelay.aspx', obj.bodyParser.urlencoded({ extended: false }), handleFirebasePushOnlyRelayRequest); }
7738:                     if (obj.parent.config.firebase.relayserver) { parent.debug('email', 'Firebase-relay-handler'); obj.app.ws(url + 'firebaserelay.aspx', handleFirebaseRelayRequest); }
7739:                 }
7740: 
7741:                 // Setup auth strategies using passport if needed
7742:                 if (typeof domain.authstrategies == 'object') {
7743:                     parent.authLog('setupHTTPHandlers', `Setting up authentication strategies login and callback URLs for ${domain.id == '' ? 'root' : '"' + domain.id + '"'} domain.`);
7744:                     // Twitter
7745:                     if ((domain.authstrategies.authStrategyFlags & domainAuthStrategyConsts.twitter) != 0) {
7746:                         obj.app.get(url + 'auth-twitter', function (req, res, next) {
7747:                             var domain = getDomain(req);
7748:                             if (domain.passport == null) { next(); return; }
7749:                             domain.passport.authenticate('twitter-' + domain.id)(req, res, function (err) { console.log('c1', err, req.session); next(); });
7750:                         });
7751:                         obj.app.get(url + 'auth-twitter-callback', function (req, res, next) {
7752:                             var domain = getDomain(req);
7753:                             if (domain.passport == null) { next(); return; }
7754:                             if ((Object.keys(req.session).length == 0) && (req.query.nmr == null)) {
7755:                                 // This is an empty session likely due to the 302 redirection, redirect again (this is a bit of a hack).
7756:                                 var url = req.url;
7757:                                 if (url.indexOf('?') >= 0) { url += '&nmr=1'; } else { url += '?nmr=1'; } // Add this to the URL to prevent redirect loop.
7758:                                 res.set('Content-Type', 'text/html');
7759:                                 res.end('<html><head><meta http-equiv="refresh" content=0;url="' + encodeURIComponent(url) + '"></head><body></body></html>');
7760:                             } else {
7761:                                 domain.passport.authenticate('twitter-' + domain.id, { failureRedirect: domain.url })(req, res, function (err) { if (err != null) { console.log(err); } next(); });
7762:                             }
7763:                         }, handleStrategyLogin);
7764:                     }
7765: 
7766:                     // Google
7767:                     if ((domain.authstrategies.authStrategyFlags & domainAuthStrategyConsts.google) != 0) {
7768:                         obj.app.get(url + 'auth-google', function (req, res, next) {
```

## Lines 8123-8239
```text
8123:                         PerformWSSessionAuth(ws, req, true, function (ws1, req1, domain, user, cookie, authData) {
8124:                             if (((parent.config.settings.desktopmultiplex === true) || (domain.desktopmultiplex === true)) && (req.query.p == 2)) {
8125:                                 obj.meshDesktopMultiplexHandler.CreateMeshRelay(obj, ws1, req1, domain, user, cookie); // Desktop multiplexor 1-to-n
8126:                             } else {
8127:                                 obj.meshRelayHandler.CreateMeshRelay(obj, ws1, req1, domain, user, cookie); // Normal relay 1-to-1
8128:                             }
8129:                         });
8130:                     });
8131: 
8132:                     // Allows agents to transfer files
8133:                     obj.agentapp.ws(url + 'devicefile.ashx', function (ws, req) { obj.meshDeviceFileHandler.CreateMeshDeviceFile(obj, ws, null, req, domain); });
8134: 
8135:                     // Setup agent to/from server file transfer handler
8136:                     obj.agentapp.ws(url + 'agenttransfer.ashx', handleAgentFileTransfer); // Setup agent to/from server file transfer handler
8137: 
8138:                     // Setup agent downloads for meshcore updates
8139:                     obj.agentapp.get(url + 'meshagents', obj.handleMeshAgentRequest);
8140: 
8141:                     // Setup agent file downloads
8142:                     obj.agentapp.get(url + 'agentdownload.ashx', handleAgentDownloadFile);
8143: 
8144:                     // Setup APF.ashx for AMT communication
8145:                     if (obj.parent.mpsserver != null) {
8146:                         obj.agentapp.ws(url + 'apf.ashx', function (ws, req) { obj.parent.mpsserver.onWebSocketConnection(ws, req); })
8147:                     }
8148:                 }
8149: 
8150:                 // Setup web relay on this web server if needed
8151:                 // We set this up when a DNS name is used as a web relay instead of a port
8152:                 if (obj.args.relaydns != null) {
8153:                     obj.webRelayRouter = require('express').Router();
8154: 
8155:                     // This is the magic URL that will setup the relay session
8156:                     obj.webRelayRouter.get('/control-redirect.ashx', function (req, res, next) {
8157:                         if (obj.args.relaydns.indexOf(req.hostname) == -1) { res.sendStatus(404); return; }
8158:                         if ((req.session.userid == null) && obj.args.user && obj.users['user//' + obj.args.user.toLowerCase()]) { req.session.userid = 'user//' + obj.args.user.toLowerCase(); } // Use a default user if needed
8159:                         res.set({ 'Cache-Control': 'no-store' });
8160:                         parent.debug('web', 'webRelaySetup');
8161: 
8162:                         // Decode the relay cookie
8163:                         if (req.query.c == null) { res.sendStatus(404); return; }
8164: 
8165:                         // Decode and check if this relay cookie is valid
8166:                         var userid, domainid, domain, nodeid, addr, port, appid, webSessionId, expire, publicid;
8167:                         const urlCookie = obj.parent.decodeCookie(req.query.c, parent.loginCookieEncryptionKey, 32); // Allow cookies up to 32 minutes old. The web page will renew this cookie every 30 minutes.
8168:                         if (urlCookie == null) { res.sendStatus(404); return; }
8169: 
8170:                         // Decode the incoming cookie
8171:                         if ((urlCookie.ruserid != null) && (urlCookie.x != null)) {
8172:                             if (parent.webserver.destroyedSessions[urlCookie.ruserid + '/' + urlCookie.x] != null) { res.sendStatus(404); return; }
8173: 
8174:                             // This is a standard user, figure out what our web relay will be.
8175:                             if (req.session.x != urlCookie.x) { req.session.x = urlCookie.x; } // Set the sessionid if missing
8176:                             if (req.session.userid != urlCookie.ruserid) { req.session.userid = urlCookie.ruserid; } // Set the session userid if missing
8177:                             if (req.session.z) { delete req.session.z; } // Clear the web relay guest session
8178:                             userid = req.session.userid;
8179:                             domainid = userid.split('/')[1];
8180:                             domain = parent.config.domains[domainid];
8181:                             nodeid = ((req.query.relayid != null) ? req.query.relayid : req.query.n);
8182:                             addr = (req.query.addr != null) ? req.query.addr : '127.0.0.1';
8183:                             port = parseInt(req.query.p);
8184:                             appid = parseInt(req.query.appid);
8185:                             webSessionId = req.session.userid + '/' + req.session.x;
8186: 
8187:                             // Check that all the required arguments are present
8188:                             if ((req.session.userid == null) || (req.session.x == null) || (req.query.n == null) || (req.query.p == null) || (parent.webserver.destroyedSessions[webSessionId] != null) || ((req.query.appid != 1) && (req.query.appid != 2))) { res.redirect('/'); return; }
8189:                         } else if (urlCookie.r == 8) {
8190:                             // This is a guest user, figure out what our web relay will be.
8191:                             userid = urlCookie.userid;
8192:                             domainid = userid.split('/')[1];
8193:                             domain = parent.config.domains[domainid];
8194:                             nodeid = urlCookie.nid;
8195:                             addr = (urlCookie.addr != null) ? urlCookie.addr : '127.0.0.1';
8196:                             port = urlCookie.port;
8197:                             appid = (urlCookie.p == 16) ? 2 : 1; // appid: 1 = HTTP, 2 = HTTPS
8198:                             webSessionId = userid + '/' + urlCookie.pid;
8199:                             publicid = urlCookie.pid;
8200:                             if (req.session.x) { delete req.session.x; } // Clear the web relay sessionid
8201:                             if (req.session.userid) { delete req.session.userid; }  // Clear the web relay userid
8202:                             if (req.session.z != webSessionId) { req.session.z = webSessionId; } // Set the web relay guest session
8203:                             expire = urlCookie.expire;
8204:                             if ((expire != null) && (expire <= Date.now())) { parent.debug('webrelay', 'expired link'); res.sendStatus(404); return; }
8205:                         }
8206: 
8207:                         // No session identifier was setup, exit now
8208:                         if (webSessionId == null) { res.sendStatus(404); return; }
8209: 
8210:                         // Check that we have an exact session on any of the relay DNS names
8211:                         var xrelaySessionId, xrelaySession, freeRelayHost, oldestRelayTime, oldestRelayHost;
8212:                         for (var hostIndex in obj.args.relaydns) {
8213:                             const host = obj.args.relaydns[hostIndex];
8214:                             xrelaySessionId = webSessionId + '/' + host;
8215:                             xrelaySession = webRelaySessions[xrelaySessionId];
8216:                             if (xrelaySession == null) {
8217:                                 // We found an unused hostname, save this as it could be useful.
8218:                                 if (freeRelayHost == null) { freeRelayHost = host; }
8219:                             } else {
8220:                                 // Check if we already have a relay session that matches exactly what we want
8221:                                 if ((xrelaySession.domain.id == domain.id) && (xrelaySession.userid == userid) && (xrelaySession.nodeid == nodeid) && (xrelaySession.addr == addr) && (xrelaySession.port == port) && (xrelaySession.appid == appid)) {
8222:                                     // We found an exact match, we are all setup already, redirect to root of that DNS name
8223:                                     if (host == req.hostname) {
8224:                                         // Request was made on the same host, redirect to root.
8225:                                         res.redirect('/');
8226:                                     } else {
8227:                                         // Request was made to a different host
8228:                                         const httpport = ((args.aliasport != null) ? args.aliasport : args.port);
8229:                                         res.redirect('https://' + host + ((httpport != 443) ? (':' + httpport) : '') + '/');
8230:                                     }
8231:                                     return;
8232:                                 }
8233: 
8234:                                 // Keep a record of the oldest web relay session, this could be useful.
8235:                                 if (oldestRelayHost == null) {
8236:                                     // Oldest host not set yet, set it
8237:                                     oldestRelayHost = host;
8238:                                     oldestRelayTime = xrelaySession.lastOperation;
8239:                                 } else {
```

## Lines 9222-9338
```text
9222:             }
9223: 
9224:         });
9225: 
9226:         // If error, do nothing
9227:         ws.on('error', function (err) { try { ws.close(); } catch (e) { console.log(e); } });
9228: 
9229:         // If the web socket is closed
9230:         ws.on('close', function (req) { try { ws.close(); } catch (e) { console.log(e); } });
9231: 
9232:         // Resume the socket to perform inner authentication
9233:         try { ws._socket.resume(); } catch (ex) { }
9234:     }
9235: 
9236:     // Device share guest cookies include these fields and are intended for relay sessions only.
9237:     // HTTP/HTTPS shares historically omitted cf; desktop/terminal/files shares include it.
9238:     function isDeviceShareGuestCookie(cookie) {
9239:         return ((cookie != null) && (cookie.nid != null) && (typeof cookie.r == 'number') && (typeof cookie.p == 'number') && (typeof cookie.gn == 'string'));
9240:     }
9241: 
9242:     // Authenticates a session and forwards
9243:     function PerformWSSessionAuth(ws, req, noAuthOk, func) {
9244:         // Check if the session expired
9245:         if ((req.session != null) && (typeof req.session.expire == 'number') && (req.session.expire <= Date.now())) {
9246:             parent.debug('web', 'WSERROR: Session expired.'); try { ws.send(JSON.stringify({ action: 'close', cause: 'expired', msg: 'expired-1' })); ws.close(); } catch (e) { } return;
9247:         }
9248: 
9249:         const blockingMode = parent.config.settings.maxinvalidlogin?.blocking || 'iprange';
9250:         
9251:         // Check if blocking mode is ip and this is a banned ip address
9252:         if (blockingMode !== 'username' && obj.checkAllowLogin(req) === false) { parent.debug('web', 'WSERROR: Banned connection.'); try { ws.send(JSON.stringify({ action: 'close', cause: 'banned', msg: 'banned-1' })); ws.close(); } catch (e) { } return; }
9253:         // Check if blocking mode is username and this is a banned username
9254:         if (blockingMode === 'username') {
9255:             let username = null;
9256:             if (req.query && req.query.user) {
9257:                 username = req.query.user;
9258:                 if (username.startsWith('~t:')) { tokenUser = username; }
9259:             } else if (req.headers && req.headers['x-meshauth']) {
9260:                 try {
9261:                     username = Buffer.from(req.headers['x-meshauth'].split(',')[0], 'base64').toString();
9262:                 } catch (e) {}
9263:             } else if (req.session) {
9264:                 if (req.session.loginToken) { username = req.session.loginToken; }
9265:                 if (req.session.userid) { username = req.session.userid.split('/')[2]; }
9266:             }
9267:             if(username && obj.checkAllowLogin(null, username) === false) { parent.debug('web', 'WSERROR: Locked user.'); try { ws.send(JSON.stringify({ action: 'close', cause: 'locked', msg: 'locked-1' })); ws.close(); } catch (e) { } return; }
9268:         }
9269:         
9270:         try {
9271:             // Hold this websocket until we are ready.
9272:             ws._socket.pause();
9273: 
9274:             // Check IP filtering and domain
9275:             var domain = null;
9276:             if (noAuthOk == true) {
9277:                 domain = getDomain(req);
9278:                 if (domain == null) { parent.debug('web', 'WSERROR: Got no domain, no auth ok.'); try { ws.send(JSON.stringify({ action: 'close', cause: 'noauth', msg: 'noauth-1' })); ws.close(); return; } catch (e) { } return; }
9279:             } else {
9280:                 // If authentication is required, enforce IP address filtering.
9281:                 domain = checkUserIpAddress(ws, req);
9282:                 if (domain == null) { parent.debug('web', 'WSERROR: Got no domain, user auth required.'); return; }
9283:             }
9284: 
9285:             // Check if inner authentication is requested
9286:             if (req.headers['x-meshauth'] === '*') { func(ws, req, domain, null); return; }
9287: 
9288:             const emailcheck = ((domain.mailserver != null) && (obj.parent.certificates.CommonName != null) && (obj.parent.certificates.CommonName.indexOf('.') != -1) && (obj.args.lanonly != true) && (domain.auth != 'sspi') && (domain.auth != 'ldap'))
9289: 
9290:             // A web socket session can be authenticated in many ways (Default user, session, user/pass and cookie). Check authentication here.
9291:             if ((req.query.user != null) && (req.query.pass != null)) {
9292:                 // A user/pass is provided in URL arguments
9293:                 obj.authenticate(req.query.user, req.query.pass, domain, function (err, userid, passhint, loginOptions) {
9294:                     var user = obj.users[userid];
9295: 
9296:                     // Check if user as the "notools" site right. If so, deny this connection as tools are not allowed to connect.
9297:                     if ((user != null) && (user.siteadmin != 0xFFFFFFFF) && (user.siteadmin & SITERIGHT_NOMESHCMD)) {
9298:                         // No tools allowed, close the websocket connection
9299:                         parent.debug('web', 'ERR: Websocket no tools allowed');
9300:                         try { ws.send(JSON.stringify({ action: 'close', cause: 'notools', msg: 'notools' })); ws.close(); } catch (e) { }
9301:                         return;
9302:                     }
9303: 
9304:                     // See if we support two-factor trusted cookies
9305:                     var twoFactorCookieDays = 30;
9306:                     if (typeof domain.twofactorcookiedurationdays == 'number') { twoFactorCookieDays = domain.twofactorcookiedurationdays; }
9307: 
9308:                     if ((err == null) && (user)) {
9309:                         // Check if a 2nd factor is needed
9310:                         if (checkUserOneTimePasswordRequired(domain, user, req, loginOptions) == true) {
9311:                             // Figure out if email 2FA is allowed
9312:                             var email2fa = (((typeof domain.passwordrequirements != 'object') || (domain.passwordrequirements.email2factor != false)) && (domain.mailserver != null) && (user.otpekey != null));
9313:                             var sms2fa = (((typeof domain.passwordrequirements != 'object') || (domain.passwordrequirements.sms2factor != false)) && (parent.smsserver != null) && (user.phone != null));
9314:                             var msg2fa = (((typeof domain.passwordrequirements != 'object') || (domain.passwordrequirements.msg2factor != false)) && (parent.msgserver != null) && (parent.msgserver.providers != 0) && (user.msghandle != null));
9315:                             //var push2fa = ((parent.firebase != null) && (user.otpdev != null));
9316:                             if ((typeof req.query.token != 'string') || (req.query.token == '**email**') || (req.query.token == '**sms**')/* || (req.query.token == '**push**')*/) {
9317:                                 if ((req.query.token == '**email**') && (email2fa == true)) {
9318:                                     // Cause a token to be sent to the user's registered email
9319:                                     user.otpekey = { k: obj.common.zeroPad(getRandomEightDigitInteger(), 8), d: Date.now() };
9320:                                     obj.db.SetUser(user);
9321:                                     parent.debug('web', 'Sending 2FA email to: ' + user.email);
9322:                                     domain.mailserver.sendAccountLoginMail(domain, user.email, user.otpekey.k, obj.getLanguageCodes(req), req.query.key);
9323:                                     // Ask for a login token & confirm email was sent
9324:                                     try { ws.send(JSON.stringify({ action: 'close', cause: 'noauth', msg: 'tokenrequired', email2fa: email2fa, sms2fa: sms2fa, msg2fa: msg2fa, email2fasent: true, twoFactorCookieDays: twoFactorCookieDays })); ws.close(); } catch (e) { }
9325:                                 } else if ((req.query.token == '**sms**') && (sms2fa == true)) {
9326:                                     // Cause a token to be sent to the user's phone number
9327:                                     user.otpsms = { k: obj.common.zeroPad(getRandomSixDigitInteger(), 6), d: Date.now() };
9328:                                     obj.db.SetUser(user);
9329:                                     parent.debug('web', 'Sending 2FA SMS to: ' + user.phone);
9330:                                     parent.smsserver.sendToken(domain, user.phone, user.otpsms.k, obj.getLanguageCodes(req));
9331:                                     // Ask for a login token & confirm sms was sent
9332:                                     try { ws.send(JSON.stringify({ action: 'close', cause: 'noauth', msg: 'tokenrequired', email2fa: email2fa, sms2fa: sms2fa, msg2fa: msg2fa, sms2fasent: true, twoFactorCookieDays: twoFactorCookieDays })); ws.close(); } catch (e) { }
9333:                                 } else if ((req.query.token == '**msg**') && (msg2fa == true)) {
9334:                                     // Cause a token to be sent to the user's messenger account
9335:                                     user.otpmsg = { k: obj.common.zeroPad(getRandomSixDigitInteger(), 6), d: Date.now() };
9336:                                     obj.db.SetUser(user);
9337:                                     parent.debug('web', 'Sending 2FA message to: ' + user.msghandle);
9338:                                     parent.msgserver.sendToken(domain, user.msghandle, user.otpmsg.k, obj.getLanguageCodes(req));
```

