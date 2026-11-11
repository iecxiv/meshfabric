# package/views/default.handlebars

Original source SHA-256: 9f1e0f7b80c18b1d243394849661eb502e5b1a6eaf7ca7ecfe82e2706ebf6247

## Lines 2330-2446
```text
2330:             if (x.endsWith('/#')) { x = x.substring(0, x.length - 2); }
2331:             if (x.endsWith('#')) { x = x.substring(0, x.length - 1); }
2332:             window.location.href = x;
2333:         }
2334: 
2335:         function onStateChanged(server, state, prevState, errorCode) {
2336:             if (state == 0) {
2337:                 // Control web socket disconnected
2338:                 setDialogMode(0); // Close any dialog boxes if present
2339:                 go(0); // Go to disconnection panel
2340: 
2341:                 // Clean up
2342:                 powerTimeline = null;
2343:                 powerTimelineReq = null;
2344:                 powerTimelineNode = null;
2345:                 powerTimelineUpdate = null;
2346:                 deviceShares = null;
2347:                 deleteAllNotifications(); // Close and clear notifications if present
2348:                 hideContextMenu(); // Hide the context menu if present
2349:                 QV('verifyEmailId2', false);
2350:                 QV('logoutControl', false);
2351:                 if (errorCode == 'noauth') { QH('p0span', "Unable to perform authentication"); return; }
2352:                 if (errorCode == 'invalidorigin') { QH('p0span', "Invalid origin in HTTP request"); return; }
2353:                 if (prevState == 2) { if (autoReconnect) { setTimeout(serverPoll, 5000); } } else { QH('p0span', "Unable to connect web socket"); }
2354:                 if (authCookieRenewTimer != null) { clearInterval(authCookieRenewTimer); authCookieRenewTimer = null; }
2355:             } else if (state == 2) {
2356:                 // Fetch list of meshes, nodes, files
2357:                 meshserver.send({ action: 'usergroups' });
2358:                 meshserver.send({ action: 'meshes' });
2359:                 deviceServerFilter = getDeviceServerFilter();
2360:                 meshserver.send({ action: 'nodes', id: '{{currentNode}}', skip: (devicePagingState == null) ? 0 : devicePagingState.skip, filter: deviceServerFilter });
2361:                 meshserver.send({ action: 'loginTokens' });
2362:                 if (pluginHandler != null) { meshserver.send({ action: 'plugins' }); }
2363:                 if ('{{currentNode}}'.toLowerCase() == '') { meshserver.send({ action: 'files' }); }
2364:                 if ('{{viewmode}}'.toLowerCase() == '') { go(1); }
2365:                 authCookieRenewTimer = setInterval(function () { meshserver.send({ action: 'authcookie' }); }, 1800000); // Request a cookie refresh every 30 minutes.
2366:                 if (xxcurrentView == 40) { refreshServerTimelineStats(); }
2367:             }
2368:         }
2369: 
2370:         // Poll the server, if it responds, refresh the page.
2371:         function serverPoll() {
2372:             var xdr = null;
2373:             try { xdr = new XDomainRequest(); } catch (e) { }
2374:             if (!xdr) xdr = new XMLHttpRequest();
2375:             xdr.open('HEAD', window.location.href);
2376:             xdr.timeout = 15000;
2377:             // Make sure there isn't a reverse proxy in front that may just be returning 5xx codes
2378:             // Status code 4xx should still be allowed, since a page could potentially be removed, etc
2379:             xdr.onload = function () { if (xdr.status < 500) reload(); else setTimeout(serverPoll, 10000); };
2380:             xdr.onerror = xdr.ontimeout = function () { setTimeout(serverPoll, 10000); };
2381:             xdr.send();
2382:         }
2383: 
2384:         // Return true if this browser is Windows based
2385:         function detectWindowsBrowser() {
2386:             var userAgent = window.navigator.userAgent.toUpperCase();
2387:             return (userAgent.indexOf('WINDOWS') >= 0) || (userAgent.indexOf('WIN32') >= 0) || (userAgent.indexOf('WIN64') >= 0);
2388:         }
2389: 
2390:         function updateSiteAdmin() {
2391:             var serverFeatures = parseInt('{{{serverfeatures}}}');
2392:             var siteRights = userinfo.siteadmin;
2393:             var accountSettingsLocked = ((siteRights != 0xFFFFFFFF) && ((siteRights & 1024) != 0)) || ((features2 & 0x100) != 0); // Not admin and have account features locked, or using a loginToken
2394: 
2395:             // Update account actions
2396:             QV('p2AccountSecurity', ((features & 4) == 0) && (serverinfo.domainauth == false) && ((features & 4096) != 0) && (accountSettingsLocked == false)); // Hide Account Security if in single user mode or domain authentication, 2 factor auth not supported.
2397:             QV('p2AccountActions', !accountSettingsLocked)
2398:             QV('managePhoneNumber1', (features & 0x02000000) && (features & 0x04000000) && (serverinfo.lock2factor != true));
2399:             QV('managePhoneNumber2', (features & 0x02000000) && !(features & 0x04000000) && (serverinfo.lock2factor != true));
2400:             QV('manageMessaging1', (features2 & 0x02000000) && (features2 & 0x04000000) && (serverinfo.lock2factor != true));
2401:             QV('manageMessaging2', (features2 & 0x02000000) && !(features2 & 0x04000000) && (serverinfo.lock2factor != true));
2402:             QV('manageEmail2FA', (features & 0x00800000) && (serverinfo.lock2factor != true));
2403:             QV('p2AccountPassActions', ((features & 4) == 0) && (serverinfo.domainauth == false) && (userinfo != null) && (userinfo._id.split('/')[2].startsWith('~') == false)); // Hide Account Actions if in single user mode or domain authentication
2404:             //QV('p2AccountImage', ((features & 4) == 0) && (serverinfo.domainauth == false)); // If account actions are not visible, also remove the image on that panel
2405:             QV('accountCreateLoginTokenSpan', features2 & 0x00000080);
2406:             QV('p2AccountImageFrame', !accountSettingsLocked)
2407:             QV('p2ServerActions', (siteRights & 21) && ((serverFeatures & 143) != 0));
2408:             QV('LeftMenuMyServer', (siteRights & 21) && ((serverFeatures & 64) != 0)); // 16 + 4 + 1
2409:             QV('MainMenuMyServer', siteRights & 21);
2410:             QV('p2ServerActionsBackup', (siteRights & 1) && ((serverFeatures & 1) != 0));
2411:             QV('p2ServerActionsRestore', (siteRights & 4) && ((serverFeatures & 2) != 0));
2412:             QV('p2ServerActionsVersion', (siteRights & 16) && ((serverFeatures & 4) != 0));
2413:             QV('p2ServerActionsErrors', (siteRights & 16) && ((serverFeatures & 8) != 0));
2414:             QV('p2ServerActionsConfig', (siteRights & 16) && ((serverFeatures & 128) != 0));
2415:             QV('MainMenuMyFiles', siteRights & 8);
2416:             QV('LeftMenuMyFiles', siteRights & 8);
2417:             if (((siteRights & 8) == 0) && (xxcurrentView == 5)) { setDialogMode(0); go(1); }
2418:             if (currentNode != null) { gotoDevice(currentNode._id, xxcurrentView, true); }
2419: 
2420:             // Update user image
2421:             if ((userinfo.flags != null) && (userinfo.flags & 1)) {
2422:                 if (userinfo.accountImageRnd == null) { userinfo.accountImageRnd = Math.floor(Math.random() * 9999999999); }
2423:                 Q('p2AccountImage').src = 'userimage.ashx?rnd=' + userinfo.accountImageRnd;
2424:             } else {
2425:                 Q('p2AccountImage').src = 'images/user-256.png';
2426:             }
2427: 
2428:             // Update user management state
2429:             if ((userinfo.siteadmin & 2) != 0) {
2430:                 // We are user administrator
2431:                 if (users == null) { meshserver.send({ action: 'users' }); }
2432:                 if (wssessions == null) { meshserver.send({ action: 'wssessioncount' }); }
2433:                 mainUpdate(8192 + 16384);
2434:             } else {
2435:                 // We are not user administrator
2436:                 users = null;
2437:                 wssessions = null;
2438:                 mainUpdate(16384);
2439:                 if (xxcurrentView == 4 || ((xxcurrentView >= 30) && (xxcurrentView < 40))) { setDialogMode(0); go(1); currentUser = null; }
2440:             }
2441:             meshserver.send({ action: 'events', limit: parseInt(p3limitdropdown.value) });
2442:             QV('ServerConsole', (userinfo.siteadmin === 0xFFFFFFFF) && ((serverFeatures & 16) != 0));
2443:             QV('ServerTrace', (userinfo.siteadmin === 0xFFFFFFFF) && ((serverFeatures & 32) != 0));
2444:             if ((xxcurrentView == 115) && (userinfo.siteadmin != 0xFFFFFFFF)) { go(6); }
2445:             if ((xxcurrentView == 6) && ((userinfo.siteadmin & 21) == 0)) { go(1); }
2446: 
```

## Lines 2788-2914
```text
2788:                     mainUpdate(1 | 2 | 4 | 64);
2789:                     
2790:                     // If groups are to be collapsed by default, do it now
2791:                     if (collapseGroups === 'true' && Object.keys(CollapsedGroups).length === 0 && typeof(getstore('_collapse')) === 'undefined') { cmexpandaction(2); }
2792:                     
2793:                     break;
2794:                 }
2795:                 case 'powertimeline': {
2796:                     if (message.nodeid != powerTimelineReq) break;
2797:                     powerTimelineNode = message.nodeid;
2798:                     powerTimeline = message.timeline;
2799:                     powerTimelineUpdate = Date.now() + 300000; // Update every 5 minutes
2800:                     for (var i in powerTimeline) { if (i % 2 == 1) { powerTimeline[i] = powerTimeline[i] * 1000; } } // Decompress time
2801:                     if (currentNode._id == message.nodeid) { mainUpdate(256); }
2802:                     break;
2803:                 }
2804:                 case 'deviceShares': {
2805:                     if (message.nodeid != deviceSharesReq) break;
2806:                     deviceSharesNode = message.nodeid;
2807:                     deviceShares = message.deviceShares;
2808:                     if ((currentNode != null) && (currentNode._id == message.nodeid)) { gotoDevice(currentNode._id, xxcurrentView, true); }
2809:                     break;
2810:                 }
2811:                 case 'deviceMeshShares': {
2812:                     if (message.meshid != deviceSharesReq) break;
2813:                     deviceSharesNode = message.meshid;
2814:                     deviceShares = message.deviceShares;
2815:                     if ((currentMesh != null) && (currentMesh._id == message.meshid)) { p20updateMesh(); }
2816:                     break;
2817:                 }
2818:                 case 'getsysinfo': {
2819:                     if (message.nodeid != powerTimelineReq) break;
2820:                     if (message.noinfo === true) {
2821:                         updateDeviceDetails(getNodeFromId(message.nodeid));
2822:                     } else {
2823:                         message.hardware.time = message.time;
2824:                         updateDeviceDetails(getNodeFromId(message.nodeid), message.hardware);
2825:                     }
2826:                     break;
2827:                 }
2828:                 case 'software': {
2829:                     try {
2830:                         var softwareData = message.value;
2831:                         if (JSON.parse(message.value).error) {
2832:                             QH('p18Status', '<span>' + "ERROR: " + EscapeHtml(JSON.parse(message.value).error) + '</span>');
2833:                             QH('p18html', '<div style="text-align:center;padding:50px"><i>' + "Click Refresh to load installed software..." + '</i></div>');
2834:                             installedAppsLoading = false;
2835:                             installedAppsLoadingType = 0;
2836:                             return;
2837:                         }
2838:                         if (typeof softwareData === 'string' && softwareData.trim().charAt(0) === '[' && softwareData.indexOf('"name"') >= 0) {
2839:                             handleInstalledAppsResponse(softwareData);
2840:                         } else if (typeof softwareData === 'string' && (softwareData.indexOf('"success"') >= 0 || softwareData.indexOf('"status"') >= 0 || softwareData.indexOf('"error"') >= 0)) {
2841:                             handleUninstallResponse(softwareData);
2842:                         } else {
2843:                             installedAppsLoading = false;
2844:                             installedAppsLoadingType = 0;
2845:                             displayInstalledApps();
2846:                         }
2847:                     } catch (xx) { }
2848:                     break;
2849:                 }
2850:                 case 'lastconnect': {
2851:                     var node = getNodeFromId(message.nodeid);
2852:                     if (node != null) {
2853:                         node.lastconnect = message.time;
2854:                         node.lastaddr = message.addr;
2855:                         if ((currentNode._id == node._id) && (Q('MainComputerState').innerHTML == '') && (node.lastconnect != null)) {
2856:                             QH('MainComputerState', '<span>' + "Last seen:" + '<br />' + printDateTime(new Date(node.lastconnect)) + '</span>');
2857:                         }
2858:                     }
2859:                     break;
2860:                 }
2861:                 case 'lastconnects': {
2862:                     var lcnodes = Object.keys(message.lastconnects);
2863:                     for (var i in lcnodes) {
2864:                         var lcnodeid = lcnodes[i];
2865:                         var node = getNodeFromId(lcnodeid);
2866:                         if (node != null) { node.lastconnect = message.lastconnects[lcnodeid] }
2867:                     }
2868:                     mainUpdate(4);
2869: 					break;
2870:                 }
2871:                 case 'msg': {
2872:                     // Check if this is a message from a node
2873:                     if (message.nodeid != null) {
2874:                         var index = -1;
2875:                         if (nodes != null) { for (var i in nodes) { if (nodes[i]._id == message.nodeid) { index = i; break; } } }
2876:                         if (index != -1) {
2877:                             // Node was found, dispatch the message
2878:                             if ((message.type === 'cpuinfo') && (currentNode != null) && (currentNode._id == message.nodeid)) {
2879:                                 var now = (Date.now() / 1000), cpu = 0, memory = 0;
2880:                                 if (typeof message.cpu.total == 'number') { cpu = message.cpu.total; }
2881:                                 if (typeof message.memory.percentConsumed == 'number') { memory = message.memory.percentConsumed; }
2882:                                 deviceDetailsStatsData.push([now, cpu, memory]);
2883:                                 deviceDetailsStatsDraw(message);
2884:                             } else if (message.type === 'console') { p15consoleReceive(nodes[index], message.value, message.source); } // This is a console message.
2885:                             else if (message.type === 'notify') { // This is a notification message.
2886:                                 var n = getstore('notifications', 0);
2887:                                 if (((n & 8) == 0) && (message.amtMessage != null)) { break; } // Intel AMT desktop & terminal messages should be ignored.
2888:                                 var n = { text: message.value, title: message.title, icon: message.icon, titleid: message.titleid, msgid: message.msgid, args: message.args };
2889:                                 if (message.id != null) { n.id = message.id; }
2890:                                 if (message.nodeid != null) { n.nodeid = message.nodeid; }
2891:                                 if (message.tag != null) { n.tag = message.tag; }
2892:                                 if (message.url != null) { n.url = message.url; }
2893:                                 if (message.username != null) { n.username = message.username; }
2894:                                 if (typeof message.maxtime === 'number') { n.maxtime = message.maxtime; }
2895:                                 addNotification(n);
2896:                             } else if (message.type === 'ps') {
2897:                                 showDeskToolsProcesses(message);
2898:                             } else if (message.type === 'services') {
2899:                                 showDeskToolsServices(message);
2900:                             } else if (message.type === 'service') {
2901:                                 showServiceDetailsDialog(message);
2902:                             } else if ((message.type === 'getclip') && (currentNode != null) && (currentNode._id == message.nodeid)) {
2903:                                 if ((message.tag == 1) && (xxdialogTag === 'clipboard')) {
2904:                                     Q('d2clipText').value = message.data; // Put remote clipboard data into dialog box
2905:                                 } else if ((message.tag === 2) || (message.tag === 3)) {
2906:                                     // tag 2 = manual clipboard-in button, tag 3 = auto-clipboard poll
2907:                                     if ((navigator.clipboard != null) && (message.data != deskLastClipboardReceived) && document.hasFocus()) {
2908:                                         deskLastClipboardReceived = message.data;
2909:                                         deskLastClipboardSent = message.data; // prevent echo back to remote
2910:                                         navigator.clipboard.writeText(message.data).then(function() { }).catch(function(err) { });
2911:                                     }
2912:                                 }
2913:                             } else if ((message.type === 'setclip') && (xxdialogTag === 'clipboard') && (currentNode != null) && (currentNode._id == message.nodeid)) {
2914:                                 // Display success/fail on the clipboard dialog box.
```

## Lines 3418-3538
```text
3418:                         if ((message.event.action == p3filterevents.value) || (p3filterevents.value == "")) {
3419:                             if(events != null) {
3420:                                 events.unshift(message.event);
3421:                                 var eventLimit = parseInt(p3limitdropdown.value);
3422:                                 while (events.length > eventLimit) { events.pop(); } // Remove element(s) at the end
3423:                             }
3424:                             mainUpdate(32);
3425:                         }
3426:                     }
3427:                     if (message.event.noact) break; // Take no action on this event
3428: 
3429:                     switch (message.event.action) {
3430:                         case 'serverinfochange': {
3431:                             if (message.event.lock2factor != null) { serverinfo.lock2factor = message.event.lock2factor; updateSelf(); updateSiteAdmin(); }
3432:                             break;
3433:                         }
3434:                         case 'deviceShareUpdate': {
3435:                             if (message.event.nodeid != deviceSharesReq) break;
3436:                             deviceSharesNode = message.event.nodeid;
3437:                             deviceShares = message.event.deviceShares;
3438:                             if (currentNode._id == deviceSharesNode) { gotoDevice(currentNode._id, xxcurrentView, true); }
3439:                             break;
3440:                         }
3441:                         case 'agentlog': {
3442:                             if (message.event.msgid == 98) {
3443:                                 // This is a agent help request, popup a notification.
3444:                                 if (GetNodeRights(message.event.nodeid) > 0) {
3445:                                     var n = getNodeFromId(message.event.nodeid);
3446:                                     addNotification({ text: format("Help requested from {0}: {1}", message.event.msgArgs[0], message.event.msgArgs[1]), title: n.name, icon: n.icon, nodeid: message.event.nodeid });
3447:                                 }
3448:                             }
3449:                             break;
3450:                         }
3451:                         case 'recording': {
3452:                             if ((p52recordings != null) && (typeof p52recordings == 'object')) { p52recordings.unshift(message.event); message.event.present = 1; updateRecordings(); }
3453:                             break;
3454:                         }
3455:                         case 'userWebState': {
3456:                             // New user web state, update the web page as needed
3457:                             try {
3458:                                 if (localStorage != null) {
3459:                                     // TODO: The problem with this "old" values is that if changed from a different tab, the old and new values will be the same.
3460:                                     // So comparing against these values is not a good idea.
3461:                                     var oldShowRealNames = localStorage.getItem('showRealNames');
3462:                                     var oldUiMode = localStorage.getItem('uiMode');
3463:                                     var oldSort = localStorage.getItem('sort');
3464:                                     var oldLoctag = localStorage.getItem('loctag');
3465:                                     var oldNightMode = localStorage.getItem('nightMode');
3466:                                     var oldFooterBar = localStorage.getItem('footerBar');
3467: 
3468:                                     var webstate = JSON.parse(message.event.state);
3469:                                     for (var i in webstate) { localStorage.setItem(i, webstate[i]); }
3470: 
3471:                                     // Update the web page
3472:                                     //if ((webstate.deskAspectRatio != null) && (webstate.deskAspectRatio != deskAspectRatio)) { deskAspectRatio = webstate.deskAspectRatio; deskAdjust(); }
3473:                                     if ((webstate.showRealNames != null) && (webstate.showRealNames != oldShowRealNames)) { showRealNames = Q('RealNameCheckBox').checked = (webstate.showRealNames == '1'); mainUpdate(6); }
3474:                                     if ((webstate.uiMode != null) && (webstate.uiMode != oldUiMode)) { userInterfaceSelectMenu(parseInt(webstate.uiMode)); }
3475:                                     if ((webstate.sort != null) && (webstate.sort != oldSort)) { document.getElementById('sortselect').selectedIndex = sort = parseInt(webstate.sort); mainUpdate(6); }
3476:                                     if ((webstate.loctag != null) && (webstate.loctag != oldLoctag)) { if (webstate.loctag != null) { args.locale = webstate.loctag; } else { delete args.locale; } mainUpdate(0xFFFFFFFF); }
3477:                                     if ((webstate.nightMode != null) && (webstate.nightMode != oldNightMode)) { putstore('nightMode', webstate.nightMode); setNightMode(); }
3478:                                     if ((webstate.footerBar != null) && (webstate.footerBar != oldFooterBar)) { footerBar = (webstate.footerBar == '1'); QS('container')['grid-template-rows'] = null; QS('container')['-ms-grid-rows'] = null; adjustPanels(); }
3479:                                     if ((webstate.stars != null) && (webstate.stars != JSON.stringify(stars))) {
3480:                                         stars = JSON.parse(webstate.stars);
3481:                                         if (Q('DevFilterSelect').value == 3) { mainUpdate(5); } else { mainUpdate(4); }
3482:                                         if (currentNode) { refreshDevice(currentNode._id); }
3483:                                     }
3484:                                     if (webstate.deskKeyShortcuts != null) {
3485:                                         // Set the user's desktop shortcut keys
3486:                                         deskKeyboardShortcuts = [];
3487:                                         var deskKeyboardShortcutsStr = webstate.deskKeyShortcuts.split(',');
3488:                                         for (var i in deskKeyboardShortcutsStr) { if (deskKeyboardShortcutsStr[i] != "") { deskKeyboardShortcuts.push(parseInt(deskKeyboardShortcutsStr[i])); } }
3489:                                         updateDeskShortcutKeys();
3490:                                     }
3491:                                     if (webstate.deskStrings != null) {
3492:                                         // Set the user's desktop strings
3493:                                         var s = null;
3494:                                         try { s = JSON.parse(webstate.deskStrings); } catch (ex) {}
3495:                                         if (Array.isArray(s)) { deskKeyboardStrings = s; updateDesktopStrings(); }
3496:                                     }
3497:                                 }
3498:                             } catch (ex) {}
3499:                             break;
3500:                         }
3501:                         case 'servertimelinestats': { addServerTimelineStats(message.event.data); break; }
3502:                         case 'accountcreate':
3503:                         case 'accountchange': {
3504:                             // An account was created or changed
3505:                             if ((typeof message.event.account != 'object') || (message.event.account == null)) { console.log(message.event); return; }
3506:                             if (userinfo._id == message.event.account._id) {
3507:                                 var newsiteadmin = message.event.account.siteadmin?message.event.account.siteadmin:0;
3508:                                 var oldsiteadmin = userinfo.siteadmin?userinfo.siteadmin:0;
3509:                                 var newRemoveRights = message.event.account.removeRights?message.event.account.removeRights:0;
3510:                                 var oldRemoveRights = userinfo.removeRights?userinfo.removeRights:0;
3511:                                 if ((message.event.account.quota != userinfo.quota) || (((userinfo.siteadmin & 8) == 0) && ((message.event.account.siteadmin & 8) != 0))) { meshserver.send({ action: 'files' }); }
3512:                                 var oldgroups = userinfo.groups;
3513:                                 userinfo = message.event.account;
3514:                                 if ((oldsiteadmin != newsiteadmin) || (oldRemoveRights != newRemoveRights) || (message.event.accountImageChange == 1)) { // If the site admin permission or user image has changed...
3515:                                     if (message.event.accountImageChange == 1) { userinfo.accountImageRnd = Math.floor(Math.random() * 9999999999); }
3516:                                     updateSiteAdmin();
3517:                                 }
3518:                                 updateSelf();
3519: 
3520:                                 if ((userinfo.siteadmin & 2) != 0) {
3521:                                     // Compare our groups
3522:                                     var og = oldgroups ? oldgroups : [];
3523:                                     var ng = userinfo.groups ? userinfo.groups : [];
3524:                                     if (og.join(',') != ng.join(',')) {
3525:                                         // Our groups have changed, re-ask for a list of users.
3526:                                         users = wssessions = null;
3527:                                         meshserver.send({ action: 'users' });
3528:                                         meshserver.send({ action: 'wssessioncount' });
3529:                                     }
3530:                                 }
3531: 
3532:                                 // If our list of nodes may have changes, request the new list now.
3533:                                 if (message.event.nodeListChange == userinfo._id) { meshserver.send({ action: 'nodes', skip: (devicePagingState == null) ? 0 : devicePagingState.skip }); }
3534:                             }
3535:                             if (currentNode) { refreshDevice(currentNode._id); }
3536:                             if (users == null) break;
3537: 
3538:                             // Check if the account is part of our user group
```

## Lines 3593-3723
```text
3593:                                 mainUpdate(4 + 128 + 8192 + 16384);
3594:                                 meshserver.send({ action: 'files' });
3595:                             }
3596:                             break;
3597:                         }
3598:                         case 'meshchange': {
3599:                             // Update mesh information
3600:                             if (meshes[message.event.meshid] == null) {
3601:                                 // Check if we have any access to this device group
3602:                                 var add = false;
3603:                                 if (message.event.links[userinfo._id] != null) { add = true; }
3604:                                 if (userinfo.links[message.event.meshid] != null) { add = true; }
3605:                                 for (var i in userinfo.links) { if ((i.startsWith('ugrp/')) && (message.event.links[i] != null)) { add = true; } }
3606: 
3607:                                 // This is a new mesh for us
3608:                                 if (add) {
3609:                                     meshes[message.event.meshid] = { _id: message.event.meshid, name: message.event.name, mtype: message.event.mtype, desc: message.event.desc, links: message.event.links, amt: message.event.amt, invite: message.event.invite, expireDevs: message.event.expireDevs, relayid: message.event.relayid };
3610:                                     meshserver.send({ action: 'nodes', skip: (devicePagingState == null) ? 0 : devicePagingState.skip }); // Request a refresh of all nodes (TODO: We could optimize this to only request nodes for the new mesh).
3611:                                 }
3612:                             } else {
3613:                                 // This is an existing device group
3614:                                 if (message.event.name != null) {
3615:                                     meshes[message.event.meshid].name = message.event.name;
3616:                                     for (var i in nodes) { if (nodes[i].meshid == message.event.meshid) { nodes[i].meshnamel = message.event.name.toLowerCase(); } }
3617:                                 }
3618:                                 if (message.event.desc != null) { meshes[message.event.meshid].desc = message.event.desc; }
3619:                                 if (message.event.flags != null) { meshes[message.event.meshid].flags = message.event.flags; }
3620:                                 if (message.event.consent != null) { meshes[message.event.meshid].consent = message.event.consent; }
3621:                                 if (message.event.links) { meshes[message.event.meshid].links = message.event.links; }
3622:                                 if (message.event.amt) { meshes[message.event.meshid].amt = message.event.amt; }
3623:                                 if (message.event.invite != null) { meshes[message.event.meshid].invite = message.event.invite; } else { delete meshes[message.event.meshid].invite; }
3624:                                 if (message.event.expireDevs != null) { if (message.event.expireDevs > 0) { meshes[message.event.meshid].expireDevs = message.event.expireDevs; } else { delete meshes[message.event.meshid].expireDevs; } }
3625:                                 if (message.event.relayid != null) { meshes[message.event.meshid].relayid = message.event.relayid; }
3626: 
3627:                                 // Check if we lost rights to this mesh in this change.
3628:                                 if (IsMeshViewable(message.event.meshid) == false) {
3629:                                     if ((xxcurrentView == 20) && (currentMesh == meshes[message.event.meshid])) go(2);
3630:                                     delete meshes[message.event.meshid];
3631: 
3632:                                     // Delete all nodes in that mesh, except ones with direct links
3633:                                     var newnodes = [];
3634:                                     for (var i in nodes) { if ((nodes[i].meshid != message.event.meshid) || ((userinfo.links != null) && (userinfo.links[nodes[i]._id] != null))) { newnodes.push(nodes[i]); } }
3635:                                     nodes = newnodes;
3636: 
3637:                                     // If we are looking at a node that is no longer visible, move back to "My Devices"
3638:                                     if ((xxcurrentView >= 9) && (xxcurrentView < 20) && currentNode && !IsNodeViewable(currentNode)) { setDialogMode(0); go(1); }
3639:                                 }
3640:                             }
3641:                             mainUpdate(4 + 128 + 8192 + 16384);
3642:                             if (currentNode && !IsNodeViewable(currentNode)) { currentNode = null; if ((xxcurrentView >= 9) && (xxcurrentView < 20)) { go(1); } }
3643:                             //meshserver.send({ action: 'files' }); // TODO: Why do we need to do this??
3644: 
3645:                             // If we are looking at a mesh that is now deleted, move back to "My Account"
3646:                             if (xxcurrentView == 20 && currentMesh._id == message.event.meshid) { mainUpdate(4096); }
3647:                             break;
3648:                         }
3649:                         case 'deletemesh': {
3650:                             // Delete the mesh
3651:                             if (meshes[message.event.meshid]) {
3652:                                 delete meshes[message.event.meshid];
3653:                                 mainUpdate(128);
3654:                                 meshserver.send({ action: 'files' });
3655:                             }
3656: 
3657:                             // Delete all nodes in that mesh
3658:                             var newnodes = [];
3659:                             if (nodes != null) { for (var i in nodes) { if (nodes[i].meshid != message.event.meshid) { newnodes.push(nodes[i]); } } }
3660:                             nodes = newnodes;
3661:                             mainUpdate(4 + 8192 + 16384);
3662: 
3663:                             // If we are looking at a mesh that is now deleted, move back to "My Account"
3664:                             if (xxcurrentView >= 20 && xxcurrentView < 30 && currentMesh._id == message.event.meshid) { setDialogMode(0); go(2); }
3665:                             // If we are looking at a node in the deleted mesh, move back to "My Devices"
3666:                             if (xxcurrentView >= 9 && xxcurrentView < 20 && currentNode && !IsNodeViewable(currentNode)) { setDialogMode(0); go(1); }
3667:                             break;
3668:                         }
3669:                         case 'addnode': {
3670:                             var node = message.event.node;
3671:                             if (!meshes[node.meshid]) break; // This is a node for a mesh we don't know. Happens when we are site administrator, we get all messages.
3672:                             if (getNodeFromId(node._id) != null) break; // This node is already known.
3673:                             node.namel = node.name.toLowerCase();
3674:                             if (node.rname) { node.rnamel = node.rname.toLowerCase(); } else { node.rnamel = node.namel; }
3675:                             node.meshnamel = meshes[node.meshid]?meshes[node.meshid].name.toLowerCase():'*';
3676:                             node.state = 0;
3677:                             if (!node.icon) node.icon = 1;
3678:                             node.ident = ++nodeShortIdent;
3679:                             if (nodes == null) { }
3680:                             nodes.push(node);
3681: 
3682:                             // Web page update
3683:                             mainUpdate(1 | 2 | 4 | 16);
3684: 
3685:                             // If we are looking at the device group for this device, update that.
3686:                             if ((xxcurrentView == 20) && (currentMesh != null) && (currentMesh._id == node.meshid)) { mainUpdate(4096); }
3687: 
3688:                             break;
3689:                         }
3690:                         case 'removenode': {
3691:                             var index = -1;
3692:                             for (var i in nodes) { if (nodes[i]._id == message.event.nodeid) { index = i; break; } }
3693:                             if (index != -1) {
3694:                                 var node = nodes[index];
3695:                                 if (currentNode == node) {
3696:                                     if (xxcurrentView >= 9 && xxcurrentView < 20) { setDialogMode(0); go(1); }
3697:                                     currentNode = null;
3698:                                     // TODO: Correctly disconnect from this node (Desktop/Terminal/Files...)
3699:                                 }
3700:                                 nodes.splice(index, 1);
3701: 
3702:                                 // Web page update
3703:                                 mainUpdate(4 | 16);
3704: 
3705:                                 // If we are looking at the device group for this device, update that.
3706:                                 if ((xxcurrentView == 20) && (currentMesh != null) && (currentMesh._id == node.meshid)) { mainUpdate(4096); }
3707:                             }
3708:                             break;
3709:                         }
3710:                         case 'changenode': {
3711:                             var index = -1;
3712:                             for (var i in nodes) { if (nodes[i]._id == message.event.nodeid) { index = i; break; } }
3713:                             if (index != -1) {
3714:                                 var node = nodes[index];
3715: 
3716:                                 // Change the node
3717:                                 node.name = message.event.node.name;
3718:                                 node.rname = message.event.node.rname;
3719:                                 node.lusers = message.event.node.lusers;
3720:                                 node.upnusers = message.event.node.upnusers;
3721:                                 node.users = message.event.node.users;
3722:                                 node.host = message.event.node.host;
3723:                                 node.desc = message.event.node.desc;
```

## Lines 4054-4219
```text
4054:                 case 'pluginPermissions': {
4055:                     handlePluginPermissions(message);
4056:                     break;
4057:                 }
4058:                 case 'pluginPermissionsSet': {
4059:                     if (message.success) {
4060:                         // Already handled in save function
4061:                     } else {
4062:                         alert("Error saving permissions: " + message.error);
4063:                     }
4064:                     break;
4065:                 }
4066:                 case 'pluginPermissionList': {
4067:                     handlePluginPermissionList(message);
4068:                     break;
4069:                 }
4070:                 case 'createInviteLink': { // Agent installation invitation link
4071:                     if (xxdialogTag != message.meshid) break;
4072:                     var servername = serverinfo.name;
4073:                     if ((servername.indexOf('.') == -1) || ((features & 2) != 0)) { servername = window.location.hostname; } // If the server name is not set or it's in LAN-only mode, use the URL hostname as server name.
4074:                     var domainUrlNoSlash = domainUrl.substring(0, domainUrl.length - 1);
4075:                     var url;
4076:                     if (serverinfo.https == true) {
4077:                         var portStr = (serverinfo.port == 443) ? '' : (':' + serverinfo.port);
4078:                         url = 'https://' + servername + portStr + domainUrl + 'agentinvite?c=' + message.cookie + (urlargs.key?('&key=' + urlargs.key):'');
4079:                     } else {
4080:                         var portStr = (serverinfo.port == 80) ? '' : (':' + serverinfo.port);
4081:                         url = 'http://' + servername + portStr + domainUrl + 'agentinvite?c=' + message.cookie + (urlargs.key?('&key=' + urlargs.key):'');
4082:                     }
4083:                     Q('agentInvitationLink').href = url;
4084:                     var t = format((message.expire == 1)?"1 hour":"{0} hours", message.expire);
4085:                     if (message.expire == 24) { t = "1 day"; }
4086:                     if (message.expire == 168) { t = "1 week"; }
4087:                     if (message.expire == 5040) { t = "1 month"; }
4088:                     if (message.expire == 0) { t = "Unlimited"; }
4089:                     QH('agentInvitationLink', format("Invitation Link ({0})", t));
4090:                     QV('agentInvitationLinkDiv', true);
4091:                     break;
4092:                 }
4093:                 case 'createDeviceShareLink': { // Guest sharing link
4094:                     if (xxdialogTag) break;
4095:                     var node = getNodeFromId(message.nodeid), x = '';
4096:                     if (node == null) break;
4097:                     x += addHtmlValue("Device", EscapeHtml(node.name));
4098:                     x += addHtmlValue("Guest Name", message.guestname);
4099:                     x += addHtmlValue("User Input", message.viewOnly ? "Not allowed, view only" : "Allowed");
4100:                     if (message.start && message.expire) {
4101:                         if (message.recurring) {
4102:                             x += addHtmlValue("Start Time", printDateTime(new Date(message.start)));
4103:                             if (message.expire < 2) { x += addHtmlValue("Duration", format("{0} minute", message.expire) ); }
4104:                             else { x += addHtmlValue("Duration", format("{0} minutes", message.expire) ); }
4105:                         } else {
4106:                             x += addHtmlValue("Start Time", printDateTime(new Date(message.start)));
4107:                             x += addHtmlValue("Expire Time", printDateTime(new Date(message.expire)));
4108:                         }
4109:                     }
4110:                     if (message.recurring == 1) { x += addHtmlValue("Recurring", "Daily"); }
4111:                     if (message.recurring == 2) { x += addHtmlValue("Recurring", "Weekly"); }
4112:                     var y = [];
4113:                     if (message.consent & 0x0007) { y.push("Notify"); }
4114:                     if (message.consent & 0x0038) { y.push("Prompt"); }
4115:                     if (message.consent & 0x0040) { y.push("Privacy bar"); }
4116:                     if (y.length == 0) { y.push("None"); }
4117:                     x += addHtmlValue("User Consent", y.join(', '));
4118:                     var type = ''; if (message.p <= 7) { type = ['', "Remote Terminal Link", "Remote Desktop Link", "Remote Desktop + Terminal Link", "Remote Files Link", "Remote Terminal + Files Link", "Remote Desktop + Files Link", "Remote Desktop + Terminal + Files Link"][message.p]; } else if (message.p == 8) { type = format("HTTP/{0} link", message.port); } else if (message.p == 16) { type = format("HTTPS/{0}", message.port); }
4119:                     x += '<div id=agentInvitationLinkDiv style="text-align:center;font-size:large;margin:16px"><a href="' + message.url + '" id=agentInvitationLink rel="noreferrer noopener" target="_blank" style=cursor:pointer>' + type + '</a> <img src=images/link4.png height=10 width=10 title="' + "Copy link to clipboard" + '" style=cursor:pointer onclick=d2CopyInviteToClip()></div></div>';
4120:                     setDialogMode(2, "Share Device", 1, null, x);
4121:                     break;
4122:                 }
4123:                 case 'getmqttlogin': {
4124:                     if ((currentNode == null) || (currentNode._id != message.nodeid) || (xxdialogMode != null)) return;
4125:                     var x = "These settings can be used to connect MQTT for this device." + '<br /><br />';
4126:                     delete message.action;
4127:                     delete message.nodeid;
4128:                     x += '<textarea readonly=readonly style=width:100%;resize:none;height:100px;overflow:auto;font-size:12px readonly>' + JSON.stringify(message) + '</textarea>';
4129:                     /*
4130:                     x += addHtmlValue('Username', '<input style=width:230px readonly value="' + message.user + '" />');
4131:                     x += addHtmlValue('Password', '<input style=width:230px readonly value="' + message.pass + '" />');
4132:                     x += addHtmlValue('WS URL', '<input style=width:230px readonly value="' + message.wsUrl + '" />');
4133:                     if (message.mpsUrl && message.mpsCertHash) {
4134:                         x += addHtmlValue('MPS URL', '<input style=width:230px readonly value="' + message.mpsUrl + '" />');
4135:                         x += addHtmlValue('MPS Cert Hash', '<input style=width:230px readonly value="' + message.mpsCertHash + '" />');
4136:                     }
4137:                     */
4138:                     setDialogMode(2, "MQTT Credentials", 1, null, x);
4139:                     break;
4140:                 }
4141:                 case 'stopped': { // Server is stopping.
4142:                     // Disconnect
4143:                     autoReconnect = false;
4144:                     QH('p0span', message.msg);
4145:                     break;
4146:                 }
4147:                 case 'updatePluginList': {
4148:                     installedPluginList = message.list;
4149:                     updatePluginList();
4150:                     break;
4151:                 }
4152:                 case 'pluginVersionsAvailable': {
4153:                     if (pluginHandler == null) break;
4154:                     updatePluginList(message.list);
4155:                     break;
4156:                 }
4157:                 case 'downgradePluginVersions': {
4158:                     var vSelect = '<select id="lastPluginVersion">';
4159:                     message.info.versionList.forEach(function(v) { vSelect += '<option value="' + EscapeHtml(v.zipball_url) + '">' + EscapeHtml(v.name) + '</option>'; });
4160:                     vSelect += '</select>';
4161:                     setDialogMode(2, "Plugin Action", 3, pluginActionEx, format("Select the version to downgrade the plugin: {0}", EscapeHtml(message.info.name)) + '<hr />' + vSelect + '<hr />' + "Please be aware that downgrading is not recommended. Please only do so in the event that a recent upgrade has broken something." + '<input id="lastPluginAct" type="hidden" value="downgrade" /><input id="lastPluginId" type="hidden" value="' + EscapeHtml(message.info.id) + '" />');
4162:                     break;
4163:                 }
4164:                 case 'serverBackup': {
4165:                     if (message.service == 'googleDrive') {
4166:                         QV('p2ServerActionsGoogleBackup', message.state > 0);
4167:                         QV('p2ServerActionsGoogleBackupCheck', message.state > 2);
4168:                         miscState['googleDrive'] = { state: message.state }
4169:                         if (message.state == 2) {
4170:                             var x = '<img style=float:right src="images/googledrive-48.png" /><div>' + "Nagivate to the URL below, grant access and copy the token code back." + '</div><br />';
4171:                             x += addHtmlValue("Activation", '<a href=\"' + message.url + '\" rel="noreferrer noopener" target="_blank">Browse to this URL</a>');
4172:                             x += addHtmlValue("Code", '<input id=gdcode onkeyup=server_setupGoogleDriveBackupCheck3() style=width:240px></input>');
4173:                             setDialogMode(2, "Google Drive Backup", 3, server_setupGoogleDriveBackupEx, x, 'gd2');
4174:                             Q('gdcode').focus();
4175:                             QE('idx_dlgOkButton', false);
4176:                         }
4177:                     }
4178:                     break;
4179:                 }
4180:                 case 'pluginError': {
4181:                     setDialogMode(2, "Plugin Error", 1, null, message.msg);
4182:                     break;
4183:                 }
4184:                 case 'plugin': {
4185:                     if ((pluginHandler == null) || (typeof message.plugin != 'string')) break;
4186:                     try { pluginHandler[message.plugin][message.method](server, message); } catch (e) { console.log('Error loading plugin handler ('+ e + ')'); }
4187:                     break;
4188:                 }
4189:                 case 'amtsetupbin': {
4190:                     saveAs(new Blob([ Uint8Array.from(atob(message.file), function (c) { return c.charCodeAt(0) }) ], { type: 'application/octet-stream' }), 'setup.bin');
4191:                     break;
4192:                 }
4193:                 case 'previousLogins':{
4194:                     var tag = 'previousLogins';
4195:                     if (message.userid != null) { tag += ':' + message.userid; }
4196:                     if ((xxdialogMode == 2) && (xxdialogTag == tag))  {
4197:                         var x = '', c = 'BBB', xx = '';
4198:                         if (message.events.length == 0) {
4199:                             x += 'No previous login.';
4200:                         } else {
4201:                             x += '<div style=max-height:260px;overflow-y:scroll;overflow-x:hidden><table>';
4202:                             for (var i in message.events) {
4203:                                 var m = message.events[i].m;
4204:                                 if (m == 107) {  m = "Valid login"; c = 'BBD1BB'; xx = ''; if (message.events[i].tn != null) { m = format("Token: {0}", message.events[i].tn); c = '88D188' } }
4205:                                 else if (m == 108) { m = "Invalid 2FA"; c ='DD9DC3'; xx = 'x'; }
4206:                                 else if (m == 109) { m = "Locked account"; c ='E1BBBB'; xx = 'x'; }
4207:                                 else if (m == 110) { m = "Invalid password"; c = 'E1BBBB'; xx = 'x'; }
4208:                                 x += '<tr><td><img src=images/user-32' + xx + '.png height=32 width=32 style=float:left srcset="images/user-64' + xx + '.png 2x"><td><div style=width:300px;background-color:#' + c + ';border-radius:6px;margin-bottom:4px;padding:4px><div>' + printDateTime(new Date(message.events[i].t)) + ', <b>' + EscapeHtml(m) + '</b></div><div style=font-size:x-small>' + EscapeHtml(message.events[i].a.join(', ')) + '</div></div></tr>';
4209:                             }
4210:                             x += '</table></div>';
4211:                         }
4212:                         setDialogMode(2, "Previous Logins", 1, null, x);
4213:                     }
4214:                     break;
4215:                 }
4216:                 case 'getDeviceDetails': {
4217:                     saveAs(new Blob([message.data], { type: 'application/octet-stream' }), "devicelist" + '.' + message.type);
4218:                     break;
4219:                 }
```

## Lines 5573-5689
```text
5573:         }
5574: 
5575:         function getMeshActions(mesh, meshrights) {
5576:             if ((meshrights & 4) == 0) return '';
5577:             var r = '';
5578:             if (mesh.mtype == 1) {
5579:                 if ((features & 1) == 0) { // If not WAN-Only
5580:                     r += ' <a href=# style=cursor:pointer;font-size:small title="' + "Add a new Intel&reg; AMT computer that is located on the local network." + '" onclick=\'return addDeviceToMesh("' + mesh._id + '")\'>' + "Add Local" + '</a>';
5581:                     r += ' <a href=# style=cursor:pointer;font-size:small title="' + "Add a new Intel&reg; AMT computer by scanning the local network." + '" onclick=\'return addAmtScanToMesh("' + mesh._id + '")\'>' + "Scan Network" + '</a>';
5582:                 }
5583:                 if (mesh.amt && (mesh.amt.type > 0)) { // CCM Deactivate, CCM or ACM activation, Full Automatic
5584:                     r += ' <a href=# style=cursor:pointer;font-size:small title="' + "Perform Intel&reg; AMT activation and configuration." + '" onclick=\'return showAmtSetup("' + mesh._id + '")\'>' + "Setup" + '</a>';
5585:                 }
5586:             }
5587:             if ((mesh.mtype == 2) && ((userinfo.siteadmin == 0xFFFFFFFF) || ((userinfo.siteadmin & 4096) == 0))) { // Agent device group
5588:                 r += ' <a href=# style=cursor:pointer;font-size:small title="' + "Add a new computer to this device group by installing the mesh agent." + '" onclick=\'return addAgentToMesh("' + mesh._id + '")\'>' + "Add Agent" + '</a>';
5589:                 if ((features & 2) == 0) { r += ' <a href=# style=cursor:pointer;font-size:small title="' + "Invite someone to install the mesh agent on this device group." + '" onclick=\'return inviteAgentToMesh("' + mesh._id + '")\'>' + "Invite" + '</a>'; }
5590:             }
5591:             if (mesh.mtype == 3) { // Local device group
5592:                 r += ' <a href=# style=cursor:pointer;font-size:small title="' + "Add device located on the local network." + '" onclick=\'return addLocalDeviceToMesh("' + mesh._id + '")\'>' + "Add Device" + '</a>';
5593:             }
5594:             //if (mesh.amt && (mesh.amt.type > 2)) { // ACM activation or Full Automatic
5595:             //    r += ' <a href=# style=cursor:pointer;font-size:small title="' + "Switch Intel AMT to Admin Control Mode (ACM)." + '" onclick=\'return showAmtAcmSetup()\'>' + "ACM" + '</a>';
5596:             //}
5597:             return r;
5598:         }
5599: 
5600:         function addLocalDeviceToMesh(meshid) {
5601:             if (xxdialogMode) return false;
5602:             var mesh = meshes[meshid];
5603:             var x = format("Add a local device to device group \"{0}\".", EscapeHtml(mesh.name)) + '<br /><br />';
5604:             x += addHtmlValue("Device Name", '<input id=dp1devicename style=width:230px maxlength=32 autocomplete=off onchange=validateLocalDeviceToMesh() onkeyup=validateLocalDeviceToMesh() />');
5605:             x += addHtmlValue("Hostname", '<input id=dp1hostname style=width:230px maxlength=32 autocomplete=off placeholder="' + "Same as device name" + '" onchange=validateLocalDeviceToMesh() onkeyup=validateLocalDeviceToMesh() />');
5606:             x += addHtmlValue("Type", '<select id=dp1type style=width:236px><option value=4>' + "Windows (RDP)" + '</option><option value=6>' + "Linux (SSH/SCP/VNC)" + '</option><option value=29>' + "macOS (SSH/SCP/VNC)" + '</option></select>');
5607:             setDialogMode(2, "Add local device", 3, addLocalDeviceToMeshEx, x, meshid);
5608:             validateLocalDeviceToMesh();
5609:             Q('dp1devicename').focus();
5610:         }
5611: 
5612:         function validateLocalDeviceToMesh() {
5613:             QE('idx_dlgOkButton', Q('dp1devicename').value.length > 0);
5614:         }
5615: 
5616:         function addLocalDeviceToMeshEx(button, meshid) {
5617:             var host = Q('dp1hostname').value;
5618:             if (host == '') host = Q('dp1devicename').value;
5619:             meshserver.send({ action: 'addlocaldevice', meshid: meshid, devicename: Q('dp1devicename').value, hostname: host, type: parseInt(Q('dp1type').value) });
5620:         }
5621: 
5622:         function addDeviceToMesh(meshid) {
5623:             if (xxdialogMode) return false;
5624:             var mesh = meshes[meshid];
5625:             var x = format("Add a new Intel&reg; AMT device to device group \"{0}\".", EscapeHtml(mesh.name)) + '<br /><br />';
5626:             x += addHtmlValue("Device Name", '<input id=dp1devicename style=width:230px maxlength=32 autocomplete=off onchange=validateDeviceToMesh() onkeyup=validateDeviceToMesh() />');
5627:             x += addHtmlValue("Hostname", '<input id=dp1hostname style=width:230px maxlength=32 autocomplete=off placeholder="' + "Same as device name" + '" onchange=validateDeviceToMesh() onkeyup=validateDeviceToMesh() />');
5628:             x += addHtmlValue("Username", '<input id=dp1username style=width:230px maxlength=32 autocomplete=off placeholder="' + "admin" + '" onchange=validateDeviceToMesh() onkeyup=validateDeviceToMesh() />');
5629:             x += addHtmlValue("Password", '<input id=dp1password type=password style=width:230px autocomplete=off maxlength=32 onchange=validateDeviceToMesh() onkeyup=validateDeviceToMesh() />');
5630:             x += addHtmlValue("Security", '<select id=dp1tls style=width:236px><option value=0>' + "No TLS security" + '</option><option value=1>' + "TLS security required" + '</option></select>');
5631:             setDialogMode(2, "Add Intel&reg; AMT device", 3, addDeviceToMeshEx, x, meshid);
5632:             validateDeviceToMesh();
5633:             Q('dp1devicename').focus();
5634:             return false;
5635:         }
5636: 
5637:         // Intel AMT device import for a device group
5638:         // function showAmtImport(meshid) {
5639:         //     if (xxdialogMode) return false;
5640:         //     var x = '<form method=post enctype=multipart/form-data action=amtimport.ashx target=fileUploadFrame><input type=text name=link style=display:none id=amtImportDevGroup value="' + meshid + '" /><input type=file name=files id=amtImportInput style=width:100% onchange="p5updateUploadDialogOk(\'p5uploadinput\')" /><input type=hidden name=authCookie value=' + authCookie + ' /><input type=submit id=p5loginSubmit style=display:none /></form>';
5641:         //     setDialogMode(2, "Import Intel AMT devices", 3, p5uploadFileEx, x);
5642:         //     p5updateUploadDialogOk('amtImportInput');
5643:         // }
5644: 
5645:         function showAmtImport(meshid) {
5646:             if (xxdialogMode) return;
5647:             var x = "Create many Intel&reg; AMT device at once by importing a JSON file with the following format:" + '<br /><pre>[\r\n  {\r\n    "fqdn":"mycomputer.com",\r\n    "uuid":"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",\r\n    "version":"12.0.1",\r\n    "username":"admin",\r\n    "password":"password",\r\n    "tls":true\r\n  }\r\n]</pre><input style=width:370px type=file id=d4importFile accept=".json" onchange=showAmtImportValidate() />';
5648:             setDialogMode(2, "Intel&reg; AMT Device Import", 3, showAmtImportEx, x, meshid);
5649:             QE('idx_dlgOkButton', false);
5650:         }
5651: 
5652:         function showAmtImportValidate() {
5653:             QE('idx_dlgOkButton', Q('d4importFile').value != null);
5654:         }
5655: 
5656:         function showAmtImportEx(b, meshid) {
5657:             var fr = new FileReader();
5658:             fr.onload = function (r) {
5659:                 var j = null;
5660:                 try { j = JSON.parse(r.target.result); } catch (ex) { setDialogMode(2, "Intel&reg; AMT Device Import", 1, null, format("Invalid JSON file: {0}.", ex)); return; }
5661:                 if (j != null) { meshserver.send({ action: 'importamtdevices', meshid: meshid, amtdevices: j }); } else { setDialogMode(2, "Intel&reg; AMT Device Import", 1, null, "Invalid JSON file format."); }
5662:             };
5663:             fr.readAsText(Q('d4importFile').files[0]);
5664:         }
5665: 
5666:         // Intel AMT activation and configuration for agentless device groups
5667:         function showAmtSetup(meshid) {
5668:             if (xxdialogMode) return false;
5669:             var servername = serverinfo.name, mesh = meshes[meshid];
5670:             if ((servername.indexOf('.') == -1) || ((features & 2) != 0)) { servername = window.location.hostname; } // If the server name is not set or it's in LAN-only mode, use the URL hostname as server name.
5671:             var url, domainUrlNoSlash = domainUrl.substring(0, domainUrl.length - 1);
5672:             if (serverinfo.https == true) {
5673:                 var portStr = (serverinfo.port == 443) ? '' : (':' + serverinfo.port);
5674:                 url = 'wss://' + servername + portStr + domainUrl;
5675:             } else {
5676:                 var portStr = (serverinfo.port == 80) ? '' : (':' + serverinfo.port);
5677:                 url = 'ws://' + servername + portStr + domainUrl;
5678:             }
5679:             var x = format("Add, activate and configure Intel&reg; AMT to group \"{0}\" by periodically running MeshCmd as administrator on the remote device.", EscapeHtml(mesh.name)) + '<br /><br />';
5680:             x += '<textarea readonly=readonly style=width:100%;resize:none;height:100px;overflow:auto;font-size:12px readonly>meshcmd amtconfig --url ' + url + 'apf.ashx --id \'' + meshid.split('/')[2] + '\' --serverhttpshash ' + serverinfo.tlshash + '</textarea>';
5681:             if (serverinfo.amtAcmFqdn != null) {
5682:                 x += ('<div style=margin-top:8px>' + "For ACM activation, Intel&reg; AMT will need to be set to the following trusted FQDN:" + ' <b>' + serverinfo.amtAcmFqdn.join(', ') + '</b></div>');
5683:             }
5684:             setDialogMode(2, "Intel&reg; AMT setup", 9, null, x);
5685:             Q('idx_dlgOkButton').focus();
5686:             return false;
5687:         }
5688: 
5689:         // Intel AMT ACM activation using setup.bin
```

## Lines 5778-6121
```text
5778: 
5779:         // Called when a scanned computer is checked or unchecked.
5780:         function addAmtScanToMeshCheckbox() {
5781:             var elements = document.getElementsByClassName('DevScanCheckbox'), checkcount = 0;
5782:             for (var i=0;i<elements.length;i++) { if (elements[i].checked) checkcount++; }
5783:             QE('idx_dlgOkButton2', checkcount > 0);
5784:         }
5785: 
5786:         // Return true is the input string looks like an email address
5787:         function checkEmail(str) {
5788:             var x = str.split('@');
5789:             var ok = ((x.length == 2) && (x[0].length > 0) && (x[1].split('.').length > 1) && (x[1].length > 2));
5790:             if (ok == true) { var y = x[1].split('.'); for (var i in y) { if (y[i].length == 0) { ok = false; } } }
5791:             return ok;
5792:         }
5793: 
5794:         function inviteAgentToMesh(meshid) {
5795:             if (xxdialogMode) return false;
5796:             var x = '', mesh = meshes[meshid];
5797:             if (features & 64) {
5798:                 x += addHtmlValue("Invitation Type", '<select id=d2InviteType onchange=d2ChangedInviteType() style=width:236px><option value=0>' + "Link invitation" + '</option><option value=1>' + "Email invitation" + '</option></select>') + '<hr />';
5799:                 x += '<div id=emailInviteDiv style=display:none>' + format("Invite someone to install the mesh agent. An email with be sent with the link to the mesh agent installation for the \"{0}\" device group.", EscapeHtml(mesh.name)) + '<br /><br />';
5800:                 x += addHtmlValue("Name (optional)", '<input id=agentInviteName value="" style=width:230px maxlength=64 />');
5801:                 x += addHtmlValue("Email", '<input id=agentInviteEmail style=width:230px placeholder="' + "example@email.com" + '" onkeyup=validateAgentInvite()></input>');
5802:                 x += addHtmlValue("Operating System", '<select id=agentInviteNameOs onchange=d2ChangedInviteType() style=width:236px><option value=4>' + "Send installation link" + '</option><option value=0 selected>' + "Any supported" + '</option><option value=1>' + "Windows only" + '</option><option value=3>' + "Apple macOS only" + '</option><option value=2>' + "Linux only" + '</option><option value=5>' + "MeshCentral Assistant" + '</option></select>');
5803:                 x += '<div id=d2agentexpirediv>';
5804:                 x += addHtmlValue("Link Expiration", '<select id=agentInviteExpire style=width:236px><option value=1>' + "1 hour" + '</option><option value=8>' + "8 hours" + '</option><option value=24>' + "1 day" + '</option><option value=168>' + "1 week" + '</option><option value=5040>' + "1 month" + '</option><option value=0>' + "Unlimited" + '</option></select>');
5805:                 x += '</div>';
5806:                 x += '<div id=d2agentInstallTypeDiv2>';
5807:                 x += addHtmlValue("Installation Type", '<select id=agentInviteType style=width:236px><option value=0>' + "Background and interactive" + '</option><option value=2>' + "Background only" + '</option><option value=1>' + "Interactive only" + '</option></select>');
5808:                 x += '</div>';
5809:                 x += addHtmlValue("Message" + '<br />' + "(optional)", '<textarea id=agentInviteMessage value="" style=width:230px;height:100px;resize:none maxlength=1024 /></textarea>');
5810:                 x += '</div>';
5811:             }
5812:             x += '<div id=urlInviteDiv>' + format("Invite someone to install the mesh agent by sharing an invitation link. This link points the user to installation instructions for the \"{0}\" device group. The link is public and no account for this server is needed.", EscapeHtml(mesh.name)) + '<br /><br />';
5813:             x += addHtmlValue("Link Expiration", '<select id=d2inviteExpire style=width:236px onchange=d2RequestInvitationLink()><option value=1>' + "1 hour" + '</option><option value=8>' + "8 hours" + '</option><option value=24>' + "1 day" + '</option><option value=168>' + "1 week" + '</option><option value=5040>' + "1 month" + '</option><option value=0>' + "Unlimited" + '</option></select>');
5814:             x += addHtmlValue("Agents", '<select id=d2agentType style=width:236px onchange=d2ChangedInviteType()><option value=0>' + "All Available Agents" + '</option><option value=1>' + "Windows MeshAgent" + '</option><option value=2>' + "Linux MeshAgent" + '</option><option value=4>' + "MacOS MeshAgent" + '</option><option value=8>' + "MeshCentral Assistant" + '</option><option value=16>' + "Android MeshAgent" + '</option></select>');
5815:             x += '<div id=d2agentInstallTypeDiv>';
5816:             x += addHtmlValue("Installation Type", '<select id=d2agentInviteType style=width:236px onchange=d2RequestInvitationLink()><option value=0>' + "Background and interactive" + '</option><option value=2>' + "Background only" + '</option><option value=1>' + "Interactive only" + '</option></select>');
5817:             x += '</div>';
5818:             x += '<div id=agentInvitationLinkDiv style="text-align:center;font-size:large;margin:16px;display:none"><a href=# id=agentInvitationLink rel="noreferrer noopener" target="_blank" style=cursor:pointer></a> <img src=images/link4.png height=10 width=10 title="' + "Copy link to clipboard" + '" style=cursor:pointer onclick=d2CopyInviteToClip()></div></div>';
5819:             setDialogMode(2, "Invite", 3, performAgentInvite, x, meshid);
5820:             if (features & 64) { Q('d2InviteType').focus(); d2ChangedInviteType(); } else { Q('d2inviteExpire').focus(); validateAgentInvite(); }
5821:             d2RequestInvitationLink();
5822:             return false;
5823:         }
5824: 
5825:         function d2RequestInvitationLink() {
5826:             meshserver.send({ action: 'createInviteLink', meshid: xxdialogTag, expire: parseInt(Q('d2inviteExpire').value), flags: parseInt(Q('d2agentInviteType').value), agents: parseInt(Q('d2agentType').value) });
5827:         }
5828: 
5829:         function d2ChangedInviteType() {
5830:             if (features & 64) {
5831:                 QV('urlInviteDiv', Q('d2InviteType').value == 0);
5832:                 QV('d2agentexpirediv', Q('agentInviteNameOs').value == 4);
5833:                 QV('d2agentInstallTypeDiv2', [0,1,2,3].indexOf(parseInt(Q('agentInviteNameOs').value)) > -1);
5834:                 QV('emailInviteDiv', Q('d2InviteType').value == 1);
5835:             }
5836:             QV('d2agentInstallTypeDiv', [0,1,2,4].indexOf(parseInt(Q('d2agentType').value)) > -1);
5837:             validateAgentInvite();
5838:             d2RequestInvitationLink();
5839:         }
5840: 
5841:         function d2CopyInviteToClip() { copyTextToClip(Q('agentInvitationLink').href); }
5842: 
5843:         function d2CopySecretToClip() { copyTextToClip(Q('d2optsecret').getAttribute('secret')); }
5844: 
5845:         function validateAgentInvite() {
5846:             if ((features & 64) && (Q('d2InviteType').value == 1)) {
5847:                 QE('idx_dlgOkButton', checkEmail(Q('agentInviteEmail').value));
5848:                 QV('idx_dlgCancelButton', true);
5849:             } else {
5850:                 QE('idx_dlgOkButton', true);
5851:                 QV('idx_dlgCancelButton', false);
5852:             }
5853:         }
5854: 
5855:         function performAgentInvite(button, meshid) {
5856:             if ((features & 64) && (Q('d2InviteType').value == 1)) {
5857:                 meshserver.send({ action: 'inviteAgent', meshid: meshid, email: Q('agentInviteEmail').value, name: Q('agentInviteName').value, os: Q('agentInviteNameOs').value, flags: Q('agentInviteType').value, msg: Q('agentInviteMessage').value, expire: parseInt(Q('agentInviteExpire').value) });
5858:             }
5859:         }
5860: 
5861:         function addAgentToMesh(meshid) {
5862:             if (xxdialogMode) return false;
5863:             var mesh = meshes[meshid], x = '', installType = 0, moreoptions = '';
5864: 
5865:             var opts = '<select id=aginsSelect onchange=addAgentToMeshClick() style=width:236px><option value=0>' + "Windows" + '</option><option value=1>' + "Linux / BSD" + '</option><option value=5>' + "Linux / BSD / macOS Binary Installer" + '</option><option value=2>' + "Apple macOS" + '</option>';
5866:             if ((features & 2) == 0) { opts += '<option value=6>' + "Mobile device" + '</option>'; } // Don't display mobile setup in LAN mode.
5867:             opts += '<option value=7>' + "MeshCentral Assistant" + '</option>';
5868:             opts += '<option value=3>' + "Windows (Uninstall)" + '</option><option value=4>' + "Linux / BSD (Uninstall)" + '</option><option value=8>' + "Apple macOS (Uninstall)" + '</option></select>';
5869:             x += addHtmlValue("Operating System", opts);
5870: 
5871:             var servername = serverinfo.name;
5872:             if ((servername.indexOf('.') == -1) || ((features & 2) != 0)) { servername = window.location.hostname; } // If the server name is not set or it's in LAN-only mode, use the URL hostname as server name.
5873:             var domainUrlNoSlash = domainUrl.substring(0, domainUrl.length - 1), portStr = '';
5874:             if (serverinfo.https == true) { portStr = (serverinfo.port == 443)?'':(':' + serverinfo.port); } else { portStr = (serverinfo.port == 80) ? '' : (':' + serverinfo.port); }
5875: 
5876:             // Add Linux/macOS binary installer option
5877:             var binaryInstallAgentsOrder = [ 6, 5, 10005, 25, 26, 28, 30, 32, 36, 37, 40, 41, 45, 16, 29 ];
5878:             var binaryInstallAgents = { 6 : 'Linux x86-64', 5 : 'Linux x86-32', 10005 : 'Apple OSX Universal', 25 : 'Linux ARM-HF, Rasberry Pi', 26 : 'Linux ARM64-HF', 28: 'Linux MIPS24KC (OpenWRT)', 30 : 'FreeBSD x86-64', 32: 'Linux ARM 64 bit', 36: 'OpenWRT x86-64', 37: 'OpenBSD x86-64', 40: 'Linux MIPSEL24KC (OpenWRT)', 41: 'ARMADA/CORTEX-A53/MUSL (OpenWRT)', 45: 'RISC-V x86-64', 16: 'Apple macOS x86-64', 29: 'Apple macOS ARM-64' };
5879:             for (var i in binaryInstallAgentsOrder) { moreoptions += '<option value=' + binaryInstallAgentsOrder[i] + '>' + binaryInstallAgents[binaryInstallAgentsOrder[i]] + '</option>' }
5880:             x += '<div id=aginsSysTypeDiv>';
5881:             x += addHtmlValue("System Type", '<select id=aginsSysType onchange=addAgentToMeshClick() style=width:236px>' + moreoptions + '</select>');
5882:             x += '</div>';
5883: 
5884:             // Add agent and assistant installation type option
5885:             x += '<div id=aginsTypeDiv>';
5886:             x += addHtmlValue("Installation Type", '<select id=aginsType onchange=addAgentToMeshClick() style=width:236px><option value=0>' + "Background & interactive" + '</option><option value=2>' + "Background only" + '</option><option value=1>' + "Interactive only" + '</option></select>');
5887:             x += '</div><div id=asinsTypeDiv>';
5888:             x += addHtmlValue("Installation Type", '<select id=asinsType onchange=addAgentToMeshClick() style=width:236px><option value=2>' + "Application, Connect on user request" + '</option><option value=3>' + "Application, Always connected" + '</option><option value=0>' + "System Tray, Connect on user request" + '</option><option value=1>' + "System Tray, Always connected" + '</option><option value=4>' + "System Tray, Monitor only" + '</option></select>');
5889:             x += '</div><hr>';
5890: 
5891:             // \/:*?"<>|
5892:             var meshfilename = mesh.name
5893:             meshfilename = meshfilename.split('\\').join('').split('/').join('').split(':').join('').split('*').join('').split('?').join('').split('"').join('').split('<').join('').split('>').join('').split('|').join('').split(' ').join('').split('\'').join('');
5894: 
5895:             // Windows agent install
5896:             x += '<div id=agins_windows>' + format("To add a new computer to the device group of \"{0}\", download the mesh agent and install it onto the designated computer to manage. This agent has server and device group information embedded within it.", EscapeHtml(mesh.name)) + '<br /><br />';
5897:             x += addHtmlValue("Mesh Agent", '<a id=aginsw32lnk name="meshagents?id=3&meshid=' + meshid.split('/')[2] + '&installflags=0' + (urlargs.key?('&key=' + urlargs.key):'') + '" title="' + "x86 32bit version of the MeshAgent" + '">' + "Windows x86-32 (.exe)" + '</a> <img src=images/link4.png height=10 width=10 title="' + "Copy Windows x86 32bit agent URL to clipboard" + '" style=cursor:pointer onclick=copyAgentUrl("meshagents?id=3&meshid=' + meshid.split('/')[2] + '&installflags=",1)>');
5898:             x += addHtmlValue("Mesh Agent", '<a id=aginsw64lnk name="meshagents?id=4&meshid=' + meshid.split('/')[2] + '&installflags=0' + (urlargs.key?('&key=' + urlargs.key):'') + '" title="' + "x86 64bit version of the MeshAgent" + '">' + "Windows x86-64 (.exe)" + '</a> <img src=images/link4.png height=10 width=10 title="' + "Copy Windows x86 64bit agent URL to clipboard" + '" style=cursor:pointer onclick=copyAgentUrl("meshagents?id=4&meshid=' + meshid.split('/')[2] + '&installflags=",1)>');
5899:             x += addHtmlValue("Mesh Agent", '<a id=aginswa64lnk name="meshagents?id=43&meshid=' + meshid.split('/')[2] + '&installflags=0' + (urlargs.key?('&key=' + urlargs.key):'') + '" title="' + "ARM 64bit version of the MeshAgent" + '">' + "Windows ARM-64 (.exe)" + '</a> <img src=images/link4.png height=10 width=10 title="' + "Copy Windows ARM 64bit agent URL to clipboard" + '" style=cursor:pointer onclick=copyAgentUrl("meshagents?id=43&meshid=' + meshid.split('/')[2] + '&installflags=",1)>');
5900:             if (debugmode > 0) { x += addHtmlValue("Settings File", '<a id=aginswmshlnk name="meshsettings?id=' + meshid.split('/')[2] + '&installflags=0' + (urlargs.key?('&key=' + urlargs.key):'') + '">' + format("{0} settings (.msh)", EscapeHtml(mesh.name)) + '</a>'); }
5901:             x += '</div>';
5902: 
5903:             // Linux agent install
5904:             x += '<div id=agins_linux style=display:none>' + format("To add a new computer to the device group of \"{0}\", run the following command. Root credentials will be needed.", EscapeHtml(mesh.name)) + '<br />';
5905:             x += '<textarea id=agins_linux_area rows=2 cols=20 readonly=readonly style=border-radius:4px;padding:6px;margin-top:4px;margin-bottom:4px;background-color:#FFF9D3;border:0;width:100%;resize:none;height:160px;overflow:auto;font-size:12px></textarea>';
5906:             x += '<div style=font-size:x-small;float:right>' + "* For BSD, run \"pkg install wget sudo bash\" beforehand." + '</div><a style=text-decoration:none title="' + "Copy to clipboard" + '" onclick=copyAgentIdValue("agins_linux_area")><img src=images/link4.png height=10 width=10 style=cursor:pointer> ' + "Copy" + '</a></div>';
5907: 
5908:             // macOS agent install
5909:             x += '<div id=agins_osx style=display:none>' + format("To add a new computer to the device group of \"{0}\", download the mesh agent and install it onto the designated computer to manage. This agent installer has server and device group information embedded within it.", EscapeHtml(mesh.name)) + '<br /><br />';
5910:             x += addHtmlValue("Mesh Agent", '<a onclick=downloadFile("meshosxagent?id=16&meshid=' + meshid.split('/')[2] + (urlargs.key?('&key=' + urlargs.key):'') + '") title="' + "x86-64bit version of macOS Mesh Agent" + '">' + "macOS x86-64bit" + '</a> <img src=images/link4.png height=10 width=10 title="' + "Copy macOS agent URL to clipboard" + '" style=cursor:pointer onclick=copyAgentUrl("meshosxagent?id=16&meshid=' + meshid.split('/')[2] + '",0)>');
5911:             x += addHtmlValue("Mesh Agent", '<a onclick=downloadFile("meshosxagent?id=29&meshid=' + meshid.split('/')[2] + (urlargs.key?('&key=' + urlargs.key):'') + '") title="' + "ARM 64bit version of macOS Mesh Agent" + '">' + "macOS ARM (64bit)" + '</a> <img src=images/link4.png height=10 width=10 title="' + "Copy macOS agent URL to clipboard" + '" style=cursor:pointer onclick=copyAgentUrl("meshosxagent?id=29&meshid=' + meshid.split('/')[2] + '",0)>');
5912:             x += addHtmlValue("Mesh Agent", '<a onclick=downloadFile("meshosxagent?id=10005&meshid=' + meshid.split('/')[2] + (urlargs.key?('&key=' + urlargs.key):'') + '") title="' + "Universal version of macOS Mesh Agent" + '">' + "macOS (Universal)" + '</a> <img src=images/link4.png height=10 width=10 title="' + "Copy macOS agent URL to clipboard" + '" style=cursor:pointer onclick=copyAgentUrl("meshosxagent?id=10005&meshid=' + meshid.split('/')[2] + '",0)>');
5913:             x += '</div>';
5914: 
5915:             // QR code agent install
5916:             x += '<div id=agins_qrcode style=display:none;min-height:180px><a id=agins_qrimage_a rel=\"noreferrer noopener\" target=_blank><div id=agins_qrimage style=float:right;margin-left:10px;width:180px;height:180px;cursor:pointer></div></a><div>' + format("To add a mobile device to group \"{0}\", download the MeshAgent application and scan this QR code.", EscapeHtml(mesh.name)) + '</div>';
5917:             x += '<table style=width:180px>';
5918:             x += '<tr><td style=text-align:center><a title="' + "Google Play Store" + '"rel="noreferrer noopener" target=_blank href="https://play.google.com/store/apps/details?id=com.meshcentral.agent2"><img style=cursor:pointer src="images/google-play-140.png" width=140 srcset="images/google-play-280.png 2x" /></a></td></tr>';
5919:             x += '<tr><td style=text-align:center><a title="' + "Amazon App Store" + '" rel="noreferrer noopener" target=_blank href="https://www.amazon.co.uk/gp/product/B097Z4Q7SK/"><img style=cursor:pointer src="images/amazon-appstore-140.png"  width=140 srcset="images/amazon-appstore-280.png 2x" /></a></td></tr>';
5920:             x += '</table>';
5921:             x += addHtmlValue("Android APK", '<a onclick=downloadFile("meshagents?id=14' + (urlargs.key?('&key=' + urlargs.key):'') + '",null,true) title="' + "APK version of the MeshAgent" + '">' + "APK" + '</a> <img src=images/link4.png height=10 width=10 title="' + "Copy URL to clipboard" + '" style=cursor:pointer onclick=copyAgentUrl("meshagents?id=14&meshid=' + meshid.split('/')[2] + (urlargs.key?('&key=' + urlargs.key):'') + '")>');
5922:             x += '</div>'
5923: 
5924:             // MeshCentral Assistant
5925:             x += '<div id=agins_assistant style=display:none><table><tr><td style=vertical-align:top><div>' + format("MeshCentral Assistant is a Windows tool that users can use to ask for help. Use the link below to download a version that will connect to device group \"{0}\".", EscapeHtml(mesh.name)) + '</div><div></div><br />';
5926:             x += '<a id=asinslnk name="meshagents?id=10006&meshid=' + meshid.split('/')[2] + (urlargs.key?('&key=' + urlargs.key):'') + '" title="' + "MeshCentral Assistant for Windows" + '">' + "Assistant for Windows (.exe)" + '</a> <img src=images/link4.png height=10 width=10 title="' + "Copy URL to clipboard" + '" style=cursor:pointer onclick=copyAgentUrl("meshagents?id=10006&meshid=' + meshid.split('/')[2] + (urlargs.key?('&key=' + urlargs.key):'') + '")>';
5927:             x += '</td><td><img id=agass_img1 src=images/assistant-100.png width=74 height=100 srcset="images/assistant-200.png 2x"><img id=agass_img2 src=images/assistant2-100.png width=74 height=100 srcset="images/assistant2-200.png 2x"></td></tr></table></div>';
5928: 
5929:             // MeshCentral Assistant2
5930:             x += '<div id=agins_assistant2 style=display:none><table><tr><td style=vertical-align:top><div>' + "MeshCentral Assistant is a Windows tool that users can use to ask for help. Use the link below to download a version that will monitor the background agent." + '</div><div></div><br />';
5931:             x += '<a id=asinslnk2 name="meshagents?id=10006&meshid=' + meshid.split('/')[2] + (urlargs.key?('&key=' + urlargs.key):'') + '" title="' + "MeshCentral Assistant for Windows" + '">' + "Assistant for Windows (.exe)" + '</a> <img src=images/link4.png height=10 width=10 title="' + "Copy URL to clipboard" + '" style=cursor:pointer onclick=copyAgentUrl("meshagents?id=10006&meshid=' + meshid.split('/')[2] + (urlargs.key?('&key=' + urlargs.key):'') + '")>';
5932:             x += '</td><td><img src=images/assistant-100.png width=74 height=100 srcset="images/assistant-200.png 2x"></td></tr></table></div>';
5933: 
5934:             // Windows agent uninstall
5935:             x += '<div id=agins_windows_un style=display:none>' + "To remove a mesh agent, download the file below, run it and click \"Uninstall\"." + '<br /><br />';
5936:             x += addHtmlValue("Mesh Agent", '<a id=aginsw32unlnk name="meshagents?id=3&meshid=' + meshid.split('/')[2] + '&installflags=0' + (urlargs.key?('&key=' + urlargs.key):'') + '" title="' + "x86 32bit version of the MeshAgent" + '">' + "Windows x86-32 (.exe)" + '</a> <img src=images/link4.png height=10 width=10 title="' + "Copy Windows x86 32bit agent URL to clipboard" + '" style=cursor:pointer onclick=copyAgentUrl("meshagents?id=3&meshid=' + meshid.split('/')[2] + '&installflags=",1)>');
5937:             x += addHtmlValue("Mesh Agent", '<a id=aginsw64unlnk name="meshagents?id=4&meshid=' + meshid.split('/')[2] + '&installflags=0' + (urlargs.key?('&key=' + urlargs.key):'') + '" title="' + "x86 64bit version of the MeshAgent" + '">' + "Windows x86-64 (.exe)" + '</a> <img src=images/link4.png height=10 width=10 title="' + "Copy Windows x86 64bit agent URL to clipboard" + '" style=cursor:pointer onclick=copyAgentUrl("meshagents?id=4&meshid=' + meshid.split('/')[2] + '&installflags=",1)>');
5938:             x += addHtmlValue("Mesh Agent", '<a id=aginswa64unlnk name="meshagents?id=43&meshid=' + meshid.split('/')[2] + '&installflags=0' + (urlargs.key?('&key=' + urlargs.key):'') + '" title="' + "ARM 64bit version of the MeshAgent" + '">' + "Windows ARM-64 (.exe)" + '</a> <img src=images/link4.png height=10 width=10 title="' + "Copy Windows ARM 64bit agent URL to clipboard" + '" style=cursor:pointer onclick=copyAgentUrl("meshagents?id=43&meshid=' + meshid.split('/')[2] + '&installflags=",1)>');
5939:             x += '</div>';
5940: 
5941:             // Linux agent uninstall
5942:             x += '<div id=agins_linux_un style=display:none>' + "To remove a mesh agent, run the following command. Root credentials will be needed." + '<br />';
5943:             x += '<textarea id=agins_linux_area_un rows=2 cols=20 readonly=readonly style=border-radius:4px;padding:6px;margin-top:4px;margin-bottom:4px;background-color:#FFF9D3;border:0;width:100%;resize:none;height:160px;overflow:auto;font-size:12px></textarea>';
5944:             x += '<a style=text-decoration:none title="' + "Copy to clipboard" + '" onclick=copyAgentIdValue("agins_linux_area_un")><img src=images/link4.png height=10 width=10 style=cursor:pointer> Copy</a></div>';
5945: 
5946:             // macOS agent uninstall
5947:             x += '<div id=agins_osx_un style=display:none>' + "To remove a mesh agent, download the file below, extract the ZIP archive, then right-click \"Uninstall.command\" and select \"Open\"." + '<br /><br />';
5948:             x += addHtmlValue("Mesh Agent", '<a onclick=downloadFile("meshosxagent?id=10005&meshid=' + meshid.split('/')[2] + (urlargs.key?('&key=' + urlargs.key):'') + '") title="' + "Universal version of macOS Mesh Agent" + '">macOS Agent (Universal)</a> <img src=images/link4.png height=10 width=10 title="' + "Copy macOS agent URL to clipboard" + '" style=cursor:pointer onclick=copyAgentUrl("meshosxagent?id=10005&meshid=' + meshid.split('/')[2] + '",0)>');
5949:             x += '</div>';
5950: 
5951:             // Linux binary installer
5952:             x += '<div id=agins_linux_inst style=display:none>' + "This is an executable on OSs with graphical user interfaces." + '<br /><br />' + "Apple macOS executables will need to be removed from quarantine by running 'xattr -r -d com.apple.quarantine meshagent'." + '<br /><br />' + "You need to run 'chmod +x meshagent', and then run this file." + '<br /><br />';
5953:             x += addHtmlValue("Mesh Agent", '<a id=aginsbinlnk name="meshagents?id=' + meshid.split('/')[2] + '&installflags=0' + (urlargs.key?('&key=' + urlargs.key):'') + '">' + "meshagent" + '</a> <img src=images/link4.png height=10 width=10 title="' + "Copy agent URL to clipboard" + '" style=cursor:pointer onclick=copyAgentUrl("meshagents?id=' + meshid.split('/')[2] + '&installflags=",1)>');
5954:             x += addHtmlValue("Command", '<input id=aginsbincmd type=text style="width:216px" readonly value=\'wget -O meshagent' + (((features & 0x80000000) != 0)?' --no-check-certificate':'') + ' \"https://' + servername + portStr + domainUrl + 'meshagents?id=' + meshid.split('/')[2].split('$').join('%24').split('@').join('%40') + '\' /> <img src=images/link4.png height=10 width=10 title="' + "Copy agent URL to clipboard" + '" style=cursor:pointer onclick=copyAgentIdValue("aginsbincmd")>');
5955:             x += '</div>';
5956: 
5957:             setDialogMode(2, "Add Mesh Agent", 2, null, x, 'fileDownload');
5958: 
5959:             // Create the QR code
5960:             new QRCode(Q('agins_qrimage'), { text: serverinfo.magenturl + ',' + serverinfo.agentCertHash + ',' + meshid.split('/')[2], width: 180, height: 180, colorDark: '#000000', colorLight: '#EEE', correctLevel: QRCode.CorrectLevel.M });
5961:             Q('agins_qrimage_a').setAttribute('href', serverinfo.magenturl + ',' + serverinfo.agentCertHash + ',' + meshid.split('/')[2]);
5962: 
5963:             if ((features & 0x2000) == 0)
5964:             {
5965:                 Q('agins_linux_area').value = '(wget "https://' + servername + portStr + domainUrl + 'meshagents?script=1' + (urlargs.key?('&key=' + urlargs.key):'') + '"' + (((features & 0x80000000) != 0)?' --no-check-certificate':'') + ' -O ./meshinstall.sh || wget "https://' + servername + portStr + domainUrl + 'meshagents?script=1" --no-proxy' + (((features & 0x80000000) != 0)?' --no-check-certificate':'') + ' -O ./meshinstall.sh) && chmod 755 ./meshinstall.sh && sudo -E ./meshinstall.sh https://' + servername + portStr + domainUrlNoSlash + ' \'' + meshid.split('/')[2] + '\' || ./meshinstall.sh https://' + servername + portStr + domainUrlNoSlash + ' \'' + meshid.split('/')[2] + '\'\r\n';
5966:                 Q('agins_linux_area_un').value = '(wget "https://' + servername + portStr + domainUrl + 'meshagents?script=1' + (urlargs.key?('&key=' + urlargs.key):'') + '"' + (((features & 0x80000000) != 0)?' --no-check-certificate':'') + ' -O ./meshinstall.sh || wget "https://' + servername + portStr + domainUrl + 'meshagents?script=1" --no-proxy' + (((features & 0x80000000) != 0)?' --no-check-certificate':'') + ' -O ./meshinstall.sh) && chmod 755 ./meshinstall.sh && sudo -E ./meshinstall.sh uninstall https://' + servername + portStr + domainUrlNoSlash + ' \'' + meshid.split('/')[2] + '\' || ./meshinstall.sh uninstall https://' + servername + portStr + domainUrlNoSlash + ' \'' + meshid.split('/')[2] + '\'\r\n';
5967:                 //Q('agins_linux_area_un').value = '(wget "https://' + servername + portStr + domainUrl + 'meshagents?script=1' + (urlargs.key?('&key=' + urlargs.key):'') + '"' + (((features & 0x80000000) != 0)?' --no-check-certificate':'') + ' -O ./meshinstall.sh || wget "https://' + servername + portStr + domainUrl + 'meshagents?script=1" --no-proxy' + (((features & 0x80000000) != 0)?' --no-check-certificate':'') + ' -O ./meshinstall.sh) && chmod 755 ./meshinstall.sh && sudo ./meshinstall.sh uninstall\r\n';
5968:             }
5969:             else
5970:             {
5971:                 // Server asked that agent be installed to preferably not use a HTTP proxy.
5972:                 Q('agins_linux_area').value = 'wget "https://' + servername + portStr + domainUrl + 'meshagents?script=1' + (urlargs.key?('&key=' + urlargs.key):'') + '" --no-proxy' + (((features & 0x80000000) != 0)?' --no-check-certificate':'') + ' -O ./meshinstall.sh && chmod 755 ./meshinstall.sh && sudo -E ./meshinstall.sh https://' + servername + portStr + domainUrlNoSlash + ' \'' + meshid.split('/')[2] + '\' || ./meshinstall.sh https://' + servername + portStr + domainUrlNoSlash + ' \'' + meshid.split('/')[2] + '\'\r\n';
5973:                 Q('agins_linux_area_un').value = 'wget "https://' + servername + portStr + domainUrl + 'meshagents?script=1' + (urlargs.key?('&key=' + urlargs.key):'') + '" --no-proxy' + (((features & 0x80000000) != 0)?' --no-check-certificate':'') + ' -O ./meshinstall.sh && chmod 755 ./meshinstall.sh && sudo -E ./meshinstall.sh uninstall https://' + servername + portStr + domainUrlNoSlash + ' \'' + meshid.split('/')[2] + '\' || ./meshinstall.sh uninstall https://' + servername + portStr + domainUrlNoSlash + ' \'' + meshid.split('/')[2] + '\'\r\n';
5974:                 //Q('agins_linux_area_un').value = 'wget "https://' + servername + portStr + domainUrl + 'meshagents?script=1' + (urlargs.key?('&key=' + urlargs.key):'') + '" --no-proxy' + (((features & 0x80000000) != 0)?' --no-check-certificate':'') + ' -O ./meshinstall.sh && chmod 755 ./meshinstall.sh && sudo ./meshinstall.sh uninstall\r\n';
5975:             }
5976:             Q('aginsSelect').focus();
5977:             addAgentToMeshClick();
5978:             return false;
5979:         }
5980: 
5981:         function copyAgentIdValue(id) { copyTextToClip(Q(id).value); }
5982: 
5983:         function copyAgentUrl(url,addflag) {
5984:             var servername = serverinfo.name;
5985:             if ((servername.indexOf('.') == -1) || ((features & 2) != 0)) { servername = window.location.hostname; } // If the server name is not set or it's in LAN-only mode, use the URL hostname as server name.
5986:             var domainUrlNoSlash = domainUrl.substring(0, domainUrl.length - 1);
5987:             var portStr = (serverinfo.port == 443) ? '' : (':' + serverinfo.port);
5988:             var c = 'https://' + servername + portStr + domainUrl + url;
5989:             if (addflag == 1) c += Q('aginsType').value;
5990:             c += (urlargs.key?('&key=' + urlargs.key):'');
5991:             if (Q('aginsSelect').value == 5) { c += '&meshinstall=' + Q('aginsSysType').value; }
5992:             if (Q('aginsSelect').value == 7) { c += '&ac=' + Q('asinsType').value; }
5993:             copyTextToClip(c);
5994:         }
5995: 
5996:         function addAgentToMeshClick() {
5997:             var v = Q('aginsSelect').value;
5998:             QV('agins_windows', v == 0);
5999:             QV('agins_linux', v == 1);
6000:             QV('agins_osx', v == 2);
6001:             QV('agins_windows_un', v == 3);
6002:             QV('agins_linux_un', v == 4);
6003:             QV('agins_linux_inst', v == 5);
6004:             QV('aginsSysTypeDiv', v == 5);
6005:             QV('agins_qrcode', v == 6);
6006:             QV('agins_assistant', (v == 7) && (Q('asinsType').value != 4));
6007:             QV('agins_assistant2', (v == 7) && (Q('asinsType').value == 4));
6008:             QV('agins_osx_un', v == 8);
6009:             Q('aginsbinlnk').onclick = function() { downloadFile((Q('aginsbinlnk').name.split('installflags=')[0]) + 'installflags=' + Q('aginsType').value + (urlargs.key?('&key=' + urlargs.key):'') + '&meshinstall=' + Q('aginsSysType').value); };
6010:             Q('aginsbincmd').value = (Q('aginsbincmd').value.split('&installflags=')[0]) + '&installflags=' + Q('aginsType').value + (urlargs.key?('&key=' + urlargs.key):'') + '&meshinstall=' + Q('aginsSysType').value + '\"';
6011:             QV('aginsTypeDiv', (v == 0) || (v == 5));
6012:             QV('asinsTypeDiv', (v == 7));
6013: 
6014:             // Fix the links if needed
6015:             Q('aginsw32lnk').onclick = function() { downloadFile((Q('aginsw32lnk').name.split('installflags=')[0]) + 'installflags=' + Q('aginsType').value + (urlargs.key?('&key=' + urlargs.key):''), null, true); }
6016:             Q('aginsw64lnk').onclick = function() { downloadFile((Q('aginsw64lnk').name.split('installflags=')[0]) + 'installflags=' + Q('aginsType').value + (urlargs.key?('&key=' + urlargs.key):''), null, true); }
6017:             Q('aginswa64lnk').onclick = function() { downloadFile((Q('aginswa64lnk').name.split('installflags=')[0]) + 'installflags=' + Q('aginsType').value + (urlargs.key?('&key=' + urlargs.key):''), null, true); }
6018:             Q('aginsw32unlnk').onclick = function() { downloadFile((Q('aginsw32lnk').name.split('installflags=')[0]) + 'installflags=' + Q('aginsType').value + (urlargs.key?('&key=' + urlargs.key):''), null, true); }
6019:             Q('aginsw64unlnk').onclick = function() { downloadFile((Q('aginsw64lnk').name.split('installflags=')[0]) + 'installflags=' + Q('aginsType').value + (urlargs.key?('&key=' + urlargs.key):''), null, true); }
6020:             Q('aginswa64unlnk').onclick = function() { downloadFile((Q('aginswa64lnk').name.split('installflags=')[0]) + 'installflags=' + Q('aginsType').value + (urlargs.key?('&key=' + urlargs.key):''), null, true); }
6021:             if (debugmode > 0) { Q('aginswmshlnk').onclick = function() { downloadFile((Q('aginswmshlnk').name.split('installflags=')[0]) + 'installflags=' + Q('aginsType').value + (urlargs.key?('&key=' + urlargs.key):''), null, true); } }
6022:             Q('asinslnk').onclick = function() { downloadFile(Q('asinslnk').name + '&ac=' + Q('asinsType').value, null, true); }
6023:             Q('asinslnk2').onclick = function() { downloadFile(Q('asinslnk').name + '&ac=' + Q('asinsType').value, null, true); }
6024:             if (v == 7) {
6025:                 QV('agass_img1', Q('asinsType').value < 2);
6026:                 QV('agass_img2', Q('asinsType').value >= 2);
6027:             }
6028:         }
6029: 
6030:         function validateDeviceToMesh() {
6031:             QE('idx_dlgOkButton', (Q('dp1devicename').value.length > 0) && (passwordcheck(Q('dp1password').value)));
6032:         }
6033: 
6034:         function addDeviceToMeshEx(button, meshid) {
6035:             var amtuser = Q('dp1username').value;
6036:             if (amtuser == '') amtuser = 'admin';
6037:             var host = Q('dp1hostname').value;
6038:             if (host == '') host = Q('dp1devicename').value;
6039:             meshserver.send({ action: 'addamtdevice', meshid: meshid, devicename: Q('dp1devicename').value, hostname: host, amtusername: amtuser, amtpassword: Q('dp1password').value, amttls: Q('dp1tls').value });
6040:         }
6041: 
6042:         function deviceHeaderSet() {
6043:             if (deviceHeaderId == 0) { deviceHeaderId = 1; return; }
6044:             deviceHeaders['DevxHeader' + deviceHeaderId] = ((deviceHeaderTotal == 1) ? "1 node" : format("{0} nodes", deviceHeaderTotal));
6045:             //var title = '';
6046:             //for (x in deviceHeaderCount) { if (title.length > 0) title += ', '; title += deviceHeaderCount[x] + ' ' + PowerStateStr2(x); }
6047:             //deviceHeadersTitles["DevxHeader" + deviceHeaderId] = title;
6048:             deviceHeaderId++;
6049:             deviceHeaderCount = {};
6050:             deviceHeaderTotal = 0;
6051:         }
6052: 
6053:         var powerStateStrings = ['', '<span title="' + "Device is powered on." + '">' + "Powered" + '</span>', '<span title="' + "Device is in sleep state (S1)." + '">' + "Sleeping" + '</span>', '<span title="' + "Device is in sleep state (S2)." + '">' + "Sleeping" + '</span>', '<span title="' + "Device is in deep sleep state (S3)." + '">' + "Deep Sleep" + '</span>', '<span title="' + "Device is in hibernating state (S4)." + '">' + "Hibernating" + '</span>', '<span title="' + "Device is in powered off state (S5)." + '">' + "Soft-Off" + '</span>', '<span title="' + "Device is detected but power state could not be obtained." + '">' + "Present" + '</span>', '<span title="' + "Device is powered off." + '">' + "Off" + '</span>'];
6054:         var powerStateStrings2 = ['', "Device is powered", "Device is in sleep state (S1)", "Device is in sleep state (S2)", "Device is in deep sleep state (S3)", "Device is hibernating (S4)", "Device is in soft-off state (S5)", "Device is present, but power state cannot be determined", "The device is powered off"];
6055:         var powerColorTable = ['pwsTransparent', 'pwsBlack', 'pwsBlue', 'pwsBlue2', 'pwsLightblue', 'pwsBlueviolet', 'pwsDarkgreen', 'pwsLightseagreen', 'pwsLightseagreen2'];
6056:         function NodeStateStr(node) {
6057:             var states = [];
6058:             if (node.state > 0 && node.state < powerStatetable.length) state.push(powerStatetable[node.state]);
6059:             if (node.conn) {
6060:                 if ((node.conn & 1) != 0) { 
6061:                     if (node.mtype == 4) {
6062:                         if (node.porttype == 'PDU') {
6063:                             states.push('<span title="' + "Power switch is ready for use." + '">' + "Switch" + '</span>');
6064:                         } else {
6065:                             states.push('<span title="' + "IP-KVM port is up and ready for use." + '">' + "IP-KVM" + '</span>');
6066:                         }
6067:                     } else {
6068:                         states.push('<span title="' + "Mesh agent is connected and ready for use." + '">' + "Agent" + '</span>');
6069:                     }
6070:                 }
6071:                 if ((node.conn & 2) != 0) { states.push('<span title="' + "Intel&reg; AMT CIRA is connected and ready for use." + '">' + "CIRA" + '</span>'); }
6072:                 else if ((node.conn & 4) != 0) { states.push('<span title="' + "Intel&reg; AMT is routable." + '">' + "AMT" + '</span>'); }
6073:                 if ((node.conn & 8) != 0) { states.push('<span title="' + "Mesh agent is reachable using another agent as relay." + '">' + "Relay" + '</span>'); }
6074:                 if ((node.conn & 16) != 0) { states.push('<span title="' + "MQTT connection to the device is active." + '">' + "MQTT" + '</span>'); }
6075:             }
6076:             if ((node.pwr != null) && (node.pwr != 0)) { states.push(powerStateStrings[node.pwr]); }
6077:             if (states.length == 0) return '&nbsp;';
6078:             return states.join(', ');
6079:         }
6080: 
6081:         function PowerStateStr(x) {
6082:             if (x < powerStatetable.length) return powerStatetable[x];
6083:             return '';
6084:         }
6085: 
6086:         function PowerStateStr2(x) {
6087:             if ((x != 0) && (x < powerStatetable.length)) return powerStatetable[x];
6088:             return "Unknown";
6089:         }
6090: 
6091:         function updateCollapseAllButton() {
6092:             var x = (Object.keys(CollapsedGroups).length > 0)
6093:             QV('CollapseAllButton', !x);
6094:             QV('ExpandAllButton', x);
6095:             QV('CollapseAllButton2', !x);
6096:             QV('ExpandAllButton2', x);
6097:         }
6098: 
6099:         function selectallButtonFunction() {
6100:             var elements = document.getElementsByClassName('DeviceCheckbox'), checkcount = Object.keys(checkedNodeids).length;
6101:             for (var i = 0; i < elements.length; i++) { elements[i].checked = (checkcount == 0); }
6102:             checkedNodeids = {};
6103:             if (checkcount == 0) {
6104:                 checkedNodeids = {};
6105:                 var devdivs = document.getElementsByName('xxdevice' + Q('viewselect').value);
6106:                 // Checking that the parent style is null will insure that "select all" does not select any devices in collapsed groups
6107:                 for (var i = 0; i < devdivs.length; i++) { if ((devdivs[i].parentElement.attributes.style == null) || (devdivs[i].parentElement.attributes.style.value.indexOf('display') < 0)) { checkedNodeids[devdivs[i].id.substring(3)] = 1; } }
6108:             }
6109:             p1updateInfo();
6110:         }
6111: 
6112:         function p1devcheck(e) {
6113:             if (e.srcElement.checked) { checkedNodeids[e.srcElement.value.substring(6)] = 1; } else { delete checkedNodeids[e.srcElement.value.substring(6)]; }
6114:             p1updateInfo();
6115:         }
6116: 
6117:         function p1updateInfo() {
6118:             var checkcount = Object.keys(checkedNodeids).length;
6119:             if (checkcount > 0) {
6120:                 QE('GroupActionButton', true);
6121:                 Q('SelectAllButton').value = "Select None";
```

## Lines 7740-7858
```text
7740:         }
7741: 
7742:         //
7743:         // MY DEVICE
7744:         //
7745:         function refreshDevice(nodeid) {
7746:             if (!currentNode || currentNode._id != nodeid) return;
7747:             gotoDevice(nodeid, xxcurrentView, true);
7748:         }
7749: 
7750:         var currentNode;
7751:         var powerTimelineNode = null;
7752:         var powerTimelineReq = null;
7753:         var powerTimelineUpdate = null;
7754:         var powerTimeline = null;
7755:         
7756:         var deviceSharesNode = null;
7757:         var deviceSharesReq = null;
7758:         var deviceShares = null;
7759:         
7760:         function getCurrentNode() { return currentNode; };
7761:         function gotoDevice(nodeid, panel, refresh, event) {
7762:             if(event && ((event.which == 3) || (event.button == 2))) return; // ignore right click events here as handled elsewhere
7763:             // Remind the user to verify the email address
7764:             if ((userinfo.emailVerified !== true) && (serverinfo.emailcheck == true) && (userinfo.siteadmin != 0xFFFFFFFF)) { setDialogMode(2, "Account Security", 1, null, "Unable to access this feature until a email address is verified. This is required for password recovery. Go to the \"My Account\" tab to change and verify an email address."); return; }
7765: 
7766:             // Remind the user to add two factor authentication
7767:             if ((features & 0x00040000) && (count2factoraAuths() == 0)) { setDialogMode(2, "Account Security", 1, null, "Unable to access this feature until two-factor authentication is enabled. This is required for extra security. Go to the \"My Account\" tab and look at the \"Account Security\" section."); return; }
7768: 
7769:             if (event && ((event.shiftKey == true) || (event.which == 2) || (event.button == 1))) {
7770:                 // Open the device in a different tab
7771:                 safeNewWindow(window.location.origin + '?node=' + nodeid.split('/')[2] + '&viewmode=10&hide=16' + ((urlargs.key)?('&key=' + urlargs.key):''), 'meshcentral:' + nodeid);
7772:                 return;
7773:             }
7774: 
7775:             //disconnectAllKvmFunction();
7776:             var node = getNodeFromId(nodeid);
7777:             if (node == null) return;
7778:             if ((currentNode == null) || (currentNode._id != node._id) || ((node.conn & 1) == 0)) { deviceDetailsStatsClear(); } // Hide the list cpu/memory graph
7779:             var mesh = meshes[node.meshid];
7780:             var meshrights = GetNodeRights(node);
7781:             var deviceSwitch = ((currentNode == null) || (currentNode._id != nodeid));
7782:             if (!currentNode || currentNode._id != node._id || refresh == true) {
7783:                 currentNode = node;
7784: 
7785:                 // Pre defined scripts
7786:                 QV('deskPreConfigScriptContextMenu1', (currentNode.mtype == 2) && isWindowsNode(currentNode)); // Windows devices
7787:                 QV('deskPreConfigScriptContextMenu2', (currentNode.mtype == 2) && !isWindowsNode(currentNode)); // Other devices
7788: 
7789:                 // Device Notification
7790:                 QV('p10deviceNotify', (currentNode.sessions != null) && ((currentNode.sessions.kvm != null) || (currentNode.sessions.terminal != null) || (currentNode.sessions.files != null) || (currentNode.sessions.registry != null) || (currentNode.sessions.tcp != null) || (currentNode.sessions.udp != null)));
7791:                 QV('p10deviceStar', stars[currentNode._id] == 1);
7792:                 QV('p10deviceHelp', (currentNode.sessions != null) && (currentNode.sessions.help != null))
7793:                 if ((currentNode.sessions != null) && (currentNode.sessions.msg != null)) { QV('p10deviceMsg', true); QH('p10deviceMsg', Object.keys(currentNode.sessions.msg).length); } else { QV('p10deviceMsg', false); }
7794: 
7795:                 // Device Battery
7796:                 QV('p10deviceBattery', false);
7797:                 if ((currentNode.sessions != null) && (currentNode.sessions.battery != null)) {
7798:                     var bat = currentNode.sessions.battery;
7799: 
7800:                     var statestr = '';
7801:                     if (bat.state == 'ac') { statestr = "Device is plugged-in"; }
7802:                     if (bat.state == 'dc') { statestr = "Device is battery powered"; }
7803: 
7804:                     var levelstr = '', levelnum = -1;
7805:                     if ((typeof bat.level == 'number') && (bat.level >= 0) && (bat.level <= 100)) {
7806:                         levelstr = bat.level + '%';
7807:                         levelnum = (Math.floor((bat.level + 10) / 25) + 1);
7808:                         if (levelnum > 5) { lvl = 5; }
7809:                         if (bat.state == 'ac') { if (bat.level == 100) { levelnum = 11; } else { levelnum += 5; } }
7810:                     }
7811: 
7812:                     if (levelnum > 0) {
7813:                         Q('p10deviceBattery').title = (statestr != null)?(statestr + ', ' + levelstr):levelstr;
7814:                         QV('p10deviceBattery', true);
7815:                         Q('p10deviceBattery').className = 'deviceBatteryLarge deviceBatteryLarge' + levelnum;
7816:                     }
7817:                 } else {
7818:                     QV('p10deviceBattery', false);
7819:                 }
7820: 
7821:                 // Clear installedAppsData for Software tab
7822:                 installedAppsData = { desktop: [], store: [] };
7823:                 installedAppsLoading = false;
7824:                 installedAppsLoadingType = 0;
7825:                 showStoreApps = false;
7826: 
7827:                 // Add node name
7828:                 var nname = '&nbsp;-&nbsp;' + EscapeHtml(node.name), nnameEx;
7829:                 if (nname.length == 0) { nname = ' - <i>' + "None" + '</i>'; }
7830:                 if (((meshrights & 4) != 0) && ((!mesh.flags) || ((mesh.flags & 2) == 0 || (mesh.flags & 16)))) { nname = '<span tabindex=0 title="' + "Click here to edit the server-side device name" + '" onclick=showEditNodeValueDialog(0) onkeyup="if (event.key == \'Enter\') showEditNodeValueDialog(0)" style=cursor:pointer>' + nname + ' <img class=hoverButton src="images/link5.png" /></span>'; }
7831:                 nnameEx = nname;
7832:                 if (mesh) { nname += '<span style=color:#AAA;font-size:small> - ' + EscapeHtml(mesh.name) + '</span>'; }
7833:                 QH('p9deviceName', nname);
7834:                 QH('p10deviceName', nname);
7835:                 QH('p11deviceName', nname);
7836:                 QH('p12deviceName', nname);
7837:                 QH('p13deviceName', nname);
7838:                 QH('p14deviceName', nname);
7839:                 QH('p15deviceName', nname);
7840:                 QH('p16deviceName', nname);
7841:                 QH('p17deviceName', nname);
7842:                 QH('p18deviceName', nname);
7843:                 QH('p19deviceName', nname);
7844: 
7845:                 // Node attributes
7846:                 var x = '<table style=width:100%>';
7847: 
7848:                 // If title bar is hidden, display the device name here
7849:                 if ((args.hide & 8) != 0) { x += '<br />' + addDeviceAttribute("Name", nnameEx); }
7850: 
7851:                 // Attribute: Mesh
7852:                 if (mesh) { x += addDeviceAttribute('<span title="' + "The name of the device group this computer belong to." + '">' + "Group" + '</span>', '<a href=# title="' + "The name of the device group this computer belong to" + '" onclick=gotoMesh("' + node.meshid + '") style=cursor:pointer>' + EscapeHtml(meshes[node.meshid].name) + '</a>'); }
7853: 
7854:                 // Attribute: Name
7855:                 if ((node.rname != null) && (node.name != node.rname)) { x += addDeviceAttribute('<span title="' + "The name of this computer as set in the operating system" + '">' + "OS Name" + '</span>', '<span title="' + "The name of this computer as set in the operating system" + '">' + EscapeHtml(node.rname) + '</span>'); }
7856: 
7857:                 // Attribute: Host
7858:                 if ((((features & 1) == 0) && (node.mtype != 4)) || (node.mtype == 3)) { // If not WAN-only, local hostname is in use
```

## Lines 8327-8532
```text
8327:                 Q('p14iframe').contentWindow.setFrameHeight('650px');
8328:                 Q('p14iframe').contentWindow.setAuthCallback(updateAmtCredentials);
8329: 
8330:                 // Request the power timeline
8331:                 if ((powerTimelineNode != currentNode._id) && (powerTimelineReq != currentNode._id)) {
8332:                     QH('p10html2', '');
8333:                     powerTimelineReq = currentNode._id;
8334:                     meshserver.send({ action: 'powertimeline', nodeid: currentNode._id });
8335:                     meshserver.send({ action: 'lastconnect', nodeid: currentNode._id });
8336:                     meshserver.send({ action: 'getsysinfo', nodeid: currentNode._id });
8337:                     meshserver.send({ action: 'getnetworkinfo', nodeid: currentNode._id });
8338:                     meshserver.send({ action: 'getNotes', id: currentNode._id });
8339:                     QH('p17info2', '');
8340:                 }
8341: 
8342:                 // Request device sharing
8343:                 if ((deviceSharesNode != currentNode._id) && (deviceSharesReq != currentNode._id)) {
8344:                     deviceSharesReq = currentNode._id;
8345:                     meshserver.send({ action: 'deviceShares', nodeid: currentNode._id });
8346:                 }
8347: 
8348:                 // Reset the desktop tools
8349:                 QV('DeskTools', false);
8350:                 showDeskToolsProcesses();
8351: 
8352:                 // Ask for device events
8353:                 refreshDeviceEvents();
8354: 
8355:                 // Update the web page title
8356:                 if ((currentNode) && (xxcurrentView >= 9) && (xxcurrentView < 20)) {
8357:                     document.title = currentNode.name + (mesh?(' - ' + mesh.name):'') + ' - ' + decodeURIComponent('{{{extitle}}}');
8358:                 } else {
8359:                     document.title = decodeURIComponent('{{{extitle}}}');
8360:                 }
8361: 
8362:                 // Clear user consent status if present
8363:                 if (deviceSwitch) {
8364:                     p11clearConsoleMsg();
8365:                     p12clearConsoleMsg();
8366:                     p13clearConsoleMsg();
8367:                 }
8368: 
8369:                 // Device refresh plugin handler
8370:                 if (pluginHandler != null) {
8371:                     QH('p19headers', ''); QH('p19pages', '');
8372:                     pluginHandler.callHook('onDeviceRefreshEnd', nodeid, panel, refresh, event);
8373:                     var lastTab = getstore('_curPluginPage', null);
8374:                     if (lastTab != null && Q('p19ph-' + lastTab) != null) pluginHandler.callPluginPage(lastTab, Q('p19ph-' + lastTab));
8375:                 }
8376: 
8377:                 // Show user device permissions
8378:                 x = '';
8379:                 if (meshrights & 7) {
8380:                     x += '<a href=# onclick="return p20showAddMeshUserDialog(5)" style=cursor:pointer;margin-right:10px><img src=images/icon-addnew.png border=0 height=12 width=12> ' + "Add User" + '</a>';
8381:                     if (usergroups != null) {
8382:                         var userGroupCount = 0, newUserGroup = false;
8383:                         for (var i in usergroups) {
8384:                             if ((usergroups[i].membershipType != null) || (usergroups[i]._id.split('/')[1] != nodeid.split('/')[1])) continue;
8385:                             userGroupCount++; if ((currentNode.links == null) || (currentNode.links[i] == null)) { newUserGroup = true; }
8386:                         }
8387:                         if ((userGroupCount > 0) && (newUserGroup)) {  x += '<a href=# onclick="return p20showAddMeshUserDialog(6)" style=cursor:pointer;margin-right:10px><img src=images/icon-addnew.png border=0 height=12 width=12> ' + "Add User Group" + '</a>'; }
8388:                     }
8389:                 }
8390: 
8391:                 x += '<table style="color:black;background-color:#EEE;border-color:#AAA;border-width:1px;border-style:solid;border-collapse:collapse" border=0 cellpadding=2 cellspacing=0 width=100%><tbody><tr style=background-color:#AAAAAA;font-weight:bold><th scope=col style=text-align:left;width:430px>' + "User Authorizations" + '</th><th scope=col style=text-align:left></th></tr>';
8392:                 var count = 1;
8393:                 if (currentNode.links != null) {
8394:                     // Sort the list of users to display
8395:                     var useridlist = [];
8396:                     for (var i in currentNode.links) { if (i.startsWith('user/') || i.startsWith('ugrp/')) { useridlist.push(i); } }
8397:                     useridlist.sort();
8398:                     for (var i in useridlist) {
8399:                         var trash = '', rights = '', userid = useridlist[i], srights = currentNode.links[userid].rights, username = EscapeHtml(userid.split('/')[2]), rights = makeUserDeviceRightsString(srights), ugroup = false;
8400:                         if (currentNode.links[userid].name != null) { username = EscapeHtml(currentNode.links[userid].name); }
8401:                         if (userid == userinfo._id) { username = EscapeHtml(userinfo.name); }
8402:                         if ((users != null) && (users[userid] != null)) { username = EscapeHtml(users[userid].name); }
8403:                         if ((usergroups != null) && (usergroups[userid] != null)) { username = EscapeHtml(usergroups[userid].name); ugroup = true; }
8404:                         if ((meshrights & 2) != 0) {
8405:                             if (ugroup) {
8406:                                 trash = '<a href=# onclick=\'return p30removeUserFromNode(event,"' + encodeURIComponentEx(userid) + '")\' title="' + "Remove user group rights to this device group" + '" style=cursor:pointer><img src=images/trash.png border=0 height=10 width=10></a>';
8407:                                 rights = '<span style=cursor:pointer onclick=p20showAddMeshUserDialog(6,"' + encodeURIComponentEx(userid) + '")>' + rights + ' <img class=hoverButton style=cursor:pointer src=images/link5.png></span>';
8408:                             } else {
8409:                                 trash = '<a href=# onclick=\'return p30removeUserFromNode(event,"' + encodeURIComponentEx(userid) + '")\' title="' + "Remove user rights to this device group" + '" style=cursor:pointer><img src=images/trash.png border=0 height=10 width=10></a>';
8410:                                 rights = '<span style=cursor:pointer onclick=p20showAddMeshUserDialog(5,"' + encodeURIComponentEx(userid) + '")>' + rights + ' <img class=hoverButton style=cursor:pointer src=images/link5.png></span>';
8411:                             }
8412:                         }
8413:                         if (users != null) {
8414:                             if (ugroup) {
8415:                                 username = '<a href=# onclick=\'gotoUserGroup("' + encodeURIComponentEx(userid) + '");haltEvent(event);\'>' + username + '</a>';
8416:                             } else {
8417:                                 username = '<a href=# onclick=\'gotoUser("' + encodeURIComponentEx(userid) + '");haltEvent(event);\'>' + username + '</a>';
8418:                             }
8419:                         }
8420: 
8421:                         x += '<tr ' + (((++count % 2) == 0) ? 'style=background-color:#DDD' : '') + '><td style=width:30%><div title="' + (ugroup?"User Group":"User") + '" class=m' + (ugroup?4:2) + '></div><div>&nbsp;' + username + '<div></div></div></td><td style=width:70%><div style=float:right>' + trash + '</div><div>' + rights + '</div></td></tr>';
8422:                     }
8423:                 }
8424:                 if (count == 1) { x += '<tr><td><div style=padding:6px>&nbsp;<i>' + "No users with special device permissions" + '</i><div></div></div></td><td></td></tr>'; }
8425:                 x += '</tbody></table>';
8426: 
8427:                 // Show device shares
8428:                 if ((deviceShares != null) && (deviceSharesNode == currentNode._id) && (deviceShares.length > 0)) {
8429:                     x += '<br /><table style="color:black;background-color:#EEE;border-color:#AAA;border-width:1px;border-style:solid;border-collapse:collapse" border=0 cellpadding=2 cellspacing=0 width=100%><tbody><tr style=background-color:#AAAAAA;font-weight:bold><th scope=col style=text-align:left;width:430px>' + "Active Device Sharing" + '</th><th scope=col style=text-align:left></th></tr>';
8430:                     count = 1;
8431:                     for (var i = 0; i < deviceShares.length; i++) {
8432:                         var dshare = deviceShares[i], trash = '';
8433:                         if (dshare.url != null) { trash += '<a href="' + dshare.url + '" rel="noreferrer noopener" target=_blank title="' + "Device Sharing Link" + '" style=cursor:pointer><img src=images/link2.png border=0 height=10 width=10></a> '; }
8434:                         trash += '<a href=# onclick=\'return p30removeDeviceSharing(event,"' + encodeURIComponentEx(currentNode._id) + '","' + encodeURIComponentEx(dshare.publicid) + '","' + encodeURIComponentEx(dshare.guestName) + '")\' title="' + "Remove device sharing" + '" style=cursor:pointer><img src=images/trash.png border=0 height=10 width=10></a>';
8435:                         var type = ''; if (dshare.p <= 7) { type = ['', "Terminal", "Desktop", "Desktop + Terminal", "Files", "Terminal + Files", "Desktop + Files", "Desktop + Terminal + Files"][dshare.p]; } else if (dshare.p == 8) { type = "HTTP/" + dshare.port; } else if (dshare.p == 16) { type = "HTTPS/" + dshare.port; }
8436:                         var details = type;
8437:                         if ((dshare.startTime != null) && (dshare.expireTime != null)) { details = format("{0}, {1} to {2}", type, printFlexDateTime(new Date(dshare.startTime)), printFlexDateTime(new Date(dshare.expireTime))); }
8438:                         if ((dshare.startTime != null) && (dshare.duration != null)) {
8439:                             if (dshare.duration < 2) {
8440:                                 details = format("{0}, {1} for {2} minute", type, printFlexDateTime(new Date(dshare.startTime)), dshare.duration);
8441:                             } else {
8442:                                 details = format("{0}, {1} for {2} minutes", type, printFlexDateTime(new Date(dshare.startTime)), dshare.duration);
8443:                             }
8444:                         }
8445:                         if (dshare.recurring == 1) { details += ", Reccuring daily"; }
8446:                         if (dshare.recurring == 2) { details += ", Reccuring weekly"; }
8447:                         if (((dshare.p & 2) != 0) && (dshare.viewOnly === true)) { details += ", View only desktop"; }
8448:                         if (dshare.consent != null) {
8449:                             if (dshare.consent == 0) { details += ", No Consent"; } else {
8450:                                 if ((dshare.consent & 0x0038) != 0) { details += ", Prompt for consent"; }
8451:                                 if ((dshare.consent & 0x0040) != 0) { details += ", Toolbar"; }
8452:                             }
8453:                         }
8454:                         var guestName = EscapeHtml(dshare.guestName);
8455:                         if (dshare.publicid.startsWith('AS:node/')) { guestName = '<i>' + "Agent Self-Share" + '</i>'; }
8456:                         x += '<tr ' + (((++count % 2) == 0) ? 'style=background-color:#DDD' : '') + '><td style=width:30%><div class=m' + 2 + '></div><div>&nbsp;' + guestName + '<div></div></div></td><td style=width:70%><div style=float:right>' + trash + '</div><div>' + details + '</div></td></tr>';
8457:                     }
8458:                     x += '</tbody></table>';
8459:                 }
8460:                 QH('p10html4', x);
8461: 
8462:                 // Change the URL
8463:                 var urlviewmode = '';
8464:                 if (((features & 0x10000000) == 0) && (xxcurrentView >= 9) && (xxcurrentView <= 19) && (currentNode != null)) {
8465:                     urlviewmode = '?viewmode=' + xxcurrentView + '&gotonode=' + currentNode._id.split('/')[2];
8466:                     for (var i in urlargs) { urlviewmode += ('&' + i + '=' + urlargs[i]); }
8467:                     try { window.history.replaceState({}, document.title, window.location.pathname + urlviewmode); } catch (ex) { }
8468:                 }
8469: 
8470:                 // Clear the desktop session selector
8471:                 QV('p11DeskSessionSelector', false);
8472:                 QH('p11DeskSessionSelector', '');
8473:             }
8474:             setupDesktop(); // Always refresh the desktop, even if we are on the same device, we need to do some canvas switching.
8475:             if (!panel) panel = 10;
8476:             go(panel);
8477:         }
8478: 
8479:         function setIpPduState(op) {
8480:             if (op == 0) {
8481:                 setDialogMode(2, "Power Operation", 3, function() { meshserver.send({ action: 'poweraction', nodeids: [ currentNode._id ], actiontype: 2 }); }, "Perform power off?"); // Turn off
8482:             } else {
8483:                 setDialogMode(2, "Power Operation", 3, function() { meshserver.send({ action: 'wakedevices', nodeids: [ currentNode._id ] }); }, "Perform power on?"); // Turn on
8484:             }
8485:         }
8486: 
8487:         function p20editDeviceNotify() {
8488:             if (xxdialogMode) return false;
8489:             var devNotify = 0, fx = ((features2 & 0x00004000) && (userinfo.emailVerified))?1:0;
8490:             if (userinfo.notify && userinfo.notify[currentNode._id]) { devNotify = userinfo.notify[currentNode._id]; }
8491:             var x = '<div style="border-bottom: 1px solid #888;margin-bottom:3px">' + "Web Page Notifications" + '</div>';
8492:             x += '<div><label><input id=p20notifyIntelDeviceConnect type=checkbox />' + "Device connections" + '</label></div>';
8493:             x += '<div><label><input id=p20notifyIntelDeviceDisconnect type=checkbox />' + "Device disconnections" + '</label></div>';
8494:             if (currentNode.intelamt != null) { fx += 2; x += '<div><label><input id=p20notifyIntelAmtKvmActions type=checkbox />' + "Intel&reg; AMT desktop and serial events" + '</label></div>'; }
8495:             if (fx & 1) {
8496:                 x += '<br /><div style="border-bottom: 1px solid #888;margin-bottom:3px">' + "Email Notifications" + '</div>';
8497:                 x += '<div><label><input id=p20enotifyIntelDeviceConnect type=checkbox />' + "Device connections" + '</label></div>';
8498:                 x += '<div><label><input id=p20enotifyIntelDeviceDisconnect type=checkbox />' + "Device disconnections" + '</label></div>';
8499:                 x += '<div><label><input id=p20enotifyIntelDeviceHelp type=checkbox />' + "Help requests" + '</label></div>';
8500:             }
8501:             if ((userinfo.msghandle != null) && ((features2 & 0x02000000) != 0)) {
8502:                 x += '<br /><div style="border-bottom: 1px solid #888;margin-bottom:3px">' + "Messaging Notifications" + '</div>';
8503:                 x += '<div><label><input id=p20emsgDeviceConnect type=checkbox />' + "Device connections" + '</label></div>';
8504:                 x += '<div><label><input id=p20emsgDeviceDisconnect type=checkbox />' + "Device disconnections" + '</label></div>';
8505:                 x += '<div><label><input id=p20emsgDeviceHelp type=checkbox />' + "Help requests" + '</label></div>';
8506:             }
8507:             setDialogMode(2, "Notification Settings", 3, p20editDeviceNotifyEx, x, fx);
8508:             Q('p20notifyIntelDeviceConnect').checked = (devNotify & 2);
8509:             Q('p20notifyIntelDeviceDisconnect').checked = (devNotify & 4);
8510:             if (fx & 2) { Q('p20notifyIntelAmtKvmActions').checked = (devNotify & 8); }
8511:             if (fx & 1) {
8512:                 Q('p20enotifyIntelDeviceConnect').checked = (devNotify & 16);
8513:                 Q('p20enotifyIntelDeviceDisconnect').checked = (devNotify & 32);
8514:                 Q('p20enotifyIntelDeviceHelp').checked = (devNotify & 64);
8515:             }
8516:             if ((userinfo.msghandle != null) && ((features2 & 0x02000000) != 0)) {
8517:                 Q('p20emsgDeviceConnect').checked = (devNotify & 128);
8518:                 Q('p20emsgDeviceDisconnect').checked = (devNotify & 256);
8519:                 Q('p20emsgDeviceHelp').checked = (devNotify & 512);
8520:             }
8521:             return false;
8522:         }
8523: 
8524:         function p20editDeviceNotifyEx(b, fx) {
8525:             var devNotify = 0;
8526:             devNotify += Q('p20notifyIntelDeviceConnect').checked ? 2 : 0;
8527:             devNotify += Q('p20notifyIntelDeviceDisconnect').checked ? 4 : 0;
8528:             if (fx & 2) { devNotify += Q('p20notifyIntelAmtKvmActions').checked ? 8 : 0; }
8529:             if (fx & 1) {
8530:                 devNotify += Q('p20enotifyIntelDeviceConnect').checked ? 16 : 0;
8531:                 devNotify += Q('p20enotifyIntelDeviceDisconnect').checked ? 32 : 0;
8532:                 devNotify += Q('p20enotifyIntelDeviceHelp').checked ? 64 : 0;
```

## Lines 8780-9008
```text
8780:             } else if (op == 2) { // Toast
8781:                 meshserver.send({ action: 'toast', nodeids: [ currentNode._id ], title: title, msg: msg });
8782:             } else if (op == 3) { // Old Style MessageBox
8783:                 meshserver.send({ action: 'msg', type: 'alertbox', nodeid: currentNode._id, title: title, msg: msg });
8784:             }
8785:         }
8786: 
8787:         // Lock desktop
8788:         function deviceLockFunction() {
8789:             if ((xxdialogMode == null) && (desktop != null) && (desktop.contype == 1)) { setDialogMode(2, "Lock Desktop", 3, function() { if ((desktop != null) && (desktop.contype == 1)) { desktop.sendCtrlMsg('{"ctrlChannel":"102938","type":"lock"}'); } }, "Lock user desktop?"); }
8790:         }
8791: 
8792:         function showShareDevice() {
8793:             if (xxdialogMode) return;
8794:             var rights = GetNodeRights(currentNode);
8795:             var y = '', x = "Creates a link that allows a guest without an account to remote control this device for a limited time." + '<br /><br />';
8796:             x += addHtmlValue("Guest Name", '<input id=d2inviteName style=width:250px maxlength=128 type=text onkeyup=showShareDeviceValidate() />');
8797:             var deskFull = '<option value=2>' + "Desktop" + '</option>';
8798:             if ((rights != 0xFFFFFFFF) && ((rights & 0x100) != 0)) { deskFull = ''; }
8799:             var fullTerm = '<option value=1>' + "Terminal" + '</option>';
8800:             if ((rights != 0xFFFFFFFF) && ((rights & 0x200) != 0)) { fullTerm = ''; }
8801:             var fullFiles = '<option value=4>' + "Files" + '</option>';
8802:             if ((rights != 0xFFFFFFFF) && ((rights & 0x400) != 0)) { fullFiles = ''; }
8803:             var deskFiles = '<option value=5>' + "Desktop + Files" + '</option>';
8804:             if ((rights != 0xFFFFFFFF) && ((rights & 0x500) != 0)) { deskFiles = ''; }
8805:             var termFiles = '<option value=6>' + "Terminal + Files" + '</option>';
8806:             if ((rights != 0xFFFFFFFF) && ((rights & 0x600) != 0)) { termFiles = ''; }
8807:             var allFeatures = '<option value=7>' + "Desktop + Terminal + Files" + '</option>';
8808:             if ((rights != 0xFFFFFFFF) && ((rights & 0x700) != 0)) { allFeatures = ''; }
8809:             var httpFeature = '';
8810:             if (webRelayPort != 0) {
8811:                 httpFeature = '<option value=8>' + "HTTP" + '</option><option value=9>' + "HTTPS" + '</option>';
8812:                 if ((rights != 0xFFFFFFFF) && ((rights & 8) != 0)) { httpFeature = ''; }
8813:             }
8814: 
8815:             var y = '', z = '';
8816:             if ((currentNode.agent.caps & 1) == 1) { y += (deskFull + '<option value=3>' + "Desktop, View only" + '</option>'); } // Agent is desktop capable
8817:             if ((currentNode.agent.caps & 2) == 2) { y += fullTerm; } // Agent is terminal capable
8818:             if ((currentNode.agent.caps & 4) == 4) { y += fullFiles; } // Agent is files capable
8819:             if ((currentNode.agent.caps & 5) == 5) { y += deskFiles; } // Agent is desktop + files capable
8820:             if ((currentNode.agent.caps & 6) == 6) { y += termFiles; } // Agent is terminal + files capable
8821:             if ((currentNode.agent.caps & 7) == 7) { y += allFeatures; } // Agent is desktop + terminal + files capable
8822:             y += httpFeature;
8823:             
8824:             x += addHtmlValue("Type", '<select id=d2shareType style=float:right;width:250px onchange=showShareDeviceValidate()>' + y + '</select>');
8825:             var options = { 1 : "1 minute", 5 : "5 minutes", 10 : "10 minutes", 15 : "15 minutes", 30 : "30 minutes", 45 : "45 minutes", 60 : "60 minutes", 120 : "2 hours", 240 : "4 hours", 480 : "8 hours", 720 : "12 hours", 960 : "16 hours", 1440 : "24 hours", 2880 : "2 days", 5760 : "4 days", 0 : "Unlimited" }
8826:             y = '';
8827:             for (var i in options) {
8828:                 if ((serverinfo.guestdevicesharingmaxtime == null) || ((i > 0) && (i <= serverinfo.guestdevicesharingmaxtime))) {
8829:                     y += '<option value=' + i + '>' + options[i] + '</option>';
8830:                 }
8831:                 if ((i > 0) && (i <= 720) && ((serverinfo.guestdevicesharingmaxtime == null) || ((i > 0) && (i <= serverinfo.guestdevicesharingmaxtime)))) {
8832:                     z += '<option value=' + i + '>' + options[i] + '</option>';
8833:                 }
8834:             }
8835:             x += addHtmlValue("Validity", '<select id=d2timeRange style=float:right;width:250px onchange=showShareDeviceValidate()><option value=0>' + "Starting now" + '</option><option value=1>' + "Time range" + '</option><option value=2>' + "Recurring daily" + '</option><option value=3>' + "Recurring weekly" + '</option></select>');
8836:             x += '<div id=d2modenow>';
8837:             x += addHtmlValue("Expire Time", '<select id=d2inviteExpire style=float:right;width:250px>' + y + '</select>');
8838:             x += '</div><div id=d2moderange style=display:none>';
8839:             x += addHtmlValue("Time Range Start", '<input id=d2timeRangeStartSelector style=float:right;width:250px class=flatpickr type="text" placeholder="' + "Select Date & Time..." + '" data-id="altinput">');
8840:             x += addHtmlValue("Time Range End", '<input id=d2timeRangeEndSelector style=float:right;width:250px class=flatpickr type="text" placeholder="' + "Select Date & Time..." + '" data-id="altinput">');
8841:             x += '</div><div id=d2moderecurring style=display:none>';
8842:             x += addHtmlValue("Start Time", '<input id=d2timeStartSelector style=float:right;width:250px class=flatpickr type="text" placeholder="' + "Select Date & Time..." + '" data-id="altinput">');
8843:             x += addHtmlValue("Duration", '<select id=d2inviteDuration style=float:right;width:250px>' + z + '</select>');
8844:             x += '</div>';
8845:             if (currentNode.agent.caps & 1) { x += '<div id=d2userConsentSelector>' + addHtmlValue("User Consent", '<select id=d2userConsent style=float:right;width:250px><option value=0>' + "Notify Only" + '<option value=1>' + "Prompt for consent" + '</option><option value=2>' + "No Consent" + '</option></select>') + '</div>'; }
8846:             x += '<div id=d2httpPortSelector>' + addHtmlValue("Port", '<input id=d2httpPort style=float:right;width:250px value=80 onkeyup=showShareDeviceValidate()></input>') + '</div>';
8847:             x += '<div id=d2httpsPortSelector>' + addHtmlValue("Port", '<input id=d2httpsPort style=float:right;width:250px value=443 onkeyup=showShareDeviceValidate()></input>') + '</div>';
8848:             setDialogMode(2, "Share Device", 3, showShareDeviceEx, x);
8849:             showShareDeviceValidate();
8850:             var tomorrow = new Date();
8851:             tomorrow.setDate(tomorrow.getDate() + 1);
8852:             var rangeStartTime = flatpickr('#d2timeRangeStartSelector', { 
8853:                 enableTime: true, minDate: new Date(), defaultDate: new Date(), minuteIncrement: 1, plugins: [new confirmDatePlugin({})],
8854:                 onChange: function(selectedDates, dateStr, instance) {
8855:                     rangeEndTime.set('minDate', dateStr);
8856:                     if (rangeEndTime.selectedDates[0] && rangeEndTime.selectedDates[0] < selectedDates[0]) {
8857:                         rangeEndTime.clear();
8858:                     }
8859:                 }
8860:             });
8861:             var rangeEndTime = flatpickr('#d2timeRangeEndSelector', { enableTime: true, minDate: new Date(), defaultDate: tomorrow, minuteIncrement: 1, plugins: [new confirmDatePlugin({})] });
8862:             var startTime = flatpickr('#d2timeStartSelector', { enableTime: true, minDate: new Date(), defaultDate: new Date(), minuteIncrement: 1, plugins: [new confirmDatePlugin({})] });
8863:             xxdialogTag = { rangeStart: rangeStartTime, rangeEnd: rangeEndTime, start: startTime };
8864:         }
8865: 
8866:         function showShareDeviceValidate() {
8867:             if (currentNode.agent.caps & 1) { QV('d2userConsentSelector', Q('d2shareType').value < 8); }
8868:             QV('d2httpPortSelector', Q('d2shareType').value == 8);
8869:             QV('d2httpsPortSelector', Q('d2shareType').value == 9);
8870:             QV('d2modenow', Q('d2timeRange').value == 0);
8871:             QV('d2moderange', Q('d2timeRange').value == 1);
8872:             QV('d2moderecurring', Q('d2timeRange').value >= 2);
8873:             var ok = true;
8874:             if (Q('d2shareType').value == 8) { var port = parseInt(Q('d2httpPort').value); if ((Q('d2httpPort').value != port) || (port < 1) || (port > 65535)) { ok = false; } }
8875:             if (Q('d2shareType').value == 9) { var port = parseInt(Q('d2httpsPort').value); if ((Q('d2httpsPort').value != port) || (port < 1) || (port > 65535)) { ok = false; } }
8876:             if (Q('d2inviteName').value.trim().length == 0) { ok = false; }
8877:             QE('idx_dlgOkButton', ok);
8878:         }
8879: 
8880:         function showShareDeviceEx(b, tag) {
8881:             var consent = 0, p = parseInt(Q('d2shareType').value), viewOnly = false, q = 0;
8882:             if (p == 8) { meshserver.send({ action: 'createDeviceShareLink', nodeid: currentNode._id, guestname: Q('d2inviteName').value.trim(), p: 8, expire: parseInt(Q('d2inviteExpire').value), port: parseInt(Q('d2httpPort').value), consent: 0 }); return; }
8883:             if (p == 9) { meshserver.send({ action: 'createDeviceShareLink', nodeid: currentNode._id, guestname: Q('d2inviteName').value.trim(), p: 16, expire: parseInt(Q('d2inviteExpire').value), port: parseInt(Q('d2httpsPort').value), consent: 0 }); return; }
8884:             if (p == 3) { viewOnly = true; }
8885:             var q = [0, 1, 2, 2, 4, 6, 5, 7][p]; // Protocol flags: 1 = Terminal, 2 = Desktop, 4 = Files, 8 = HTTP, 16 = HTTPS.
8886: 
8887:             if (q & 1) {
8888:                 consent |= 0x0002; // Terminal notify
8889:                 if ((currentNode.agent.caps & 1) && (Q('d2userConsent').value == 1)) { consent |= 0x0010; } // Terminal prompt for user consent
8890:                 if ((currentNode.agent.caps & 1) && (Q('d2userConsent').value == 2)) { consent = 0; } // Terminal with no user consent
8891:             }
8892:             if (q & 2) {
8893:                 consent |= 0x0041; // Desktop connection toolbar + Desktop notify
8894:                 if ((currentNode.agent.caps & 1) && (Q('d2userConsent').value == 1)) { consent |= 0x0008; } // Desktop prompt for user consent
8895:                 if ((currentNode.agent.caps & 1) && (Q('d2userConsent').value == 2)) { consent = 0; } // Desktop with no user consent
8896:             }
8897:             if (q & 4) {
8898:                 consent |= 0x0004; // Files notify
8899:                 if ((currentNode.agent.caps & 1) && (Q('d2userConsent').value == 1)) { consent |= 0x0020; } // Files prompt for user consent
8900:                 if ((currentNode.agent.caps & 1) && (Q('d2userConsent').value == 2)) { consent = 0; } // Files prompt for user consent
8901:             }
8902: 
8903:             if (Q('d2timeRange').value == 0) {
8904:                 meshserver.send({ action: 'createDeviceShareLink', nodeid: currentNode._id, guestname: Q('d2inviteName').value.trim(), p: q, expire: parseInt(Q('d2inviteExpire').value), consent: consent, viewOnly: viewOnly });
8905:             } else if (Q('d2timeRange').value == 1) {
8906:                 meshserver.send({ action: 'createDeviceShareLink', nodeid: currentNode._id, guestname: Q('d2inviteName').value.trim(), p: q, start: Math.floor(tag.rangeStart.selectedDates[0].getTime() / 1000), end: Math.floor(tag.rangeEnd.selectedDates[0].getTime() / 1000), consent: consent, viewOnly: viewOnly });
8907:             } else {
8908:                 meshserver.send({ action: 'createDeviceShareLink', nodeid: currentNode._id, guestname: Q('d2inviteName').value.trim(), p: q, start: Math.floor(tag.start.selectedDates[0].getTime() / 1000), expire: parseInt(Q('d2inviteDuration').value), recurring: Q('d2timeRange').value - 1, consent: consent, viewOnly: viewOnly });
8909:             }
8910:         }
8911: 
8912:         function deviceActionFunction() {
8913:             if (xxdialogMode) return;
8914:             var rights = GetNodeRights(currentNode), count = 0;
8915:             var x = "Select an operation to perform on this device." + '<br /><br />';
8916:             var y = '<select id=d2deviceop style=float:right;width:250px onchange=deviceActionFunctionValidate()>';
8917:             var z = '';
8918:             if ((currentNode.agent != null) && (currentNode.agent.id == 14)) {
8919:                 if (((currentNode.conn & 1) != 0) && ((rights & 8) != 0)) {
8920:                     count++;
8921:                     y += '<option value=400>' + "Flash" + '</option>';
8922:                     y += '<option value=401>' + "Vibrate" + '</option>';
8923:                     z += '<div id=d2devicetimediv>' + addHtmlValue("Time", '<select id=d2devicetime style=float:right;width:250px><option value=1000>' + "1 second" + '</option><option value=5000>' + "5 seconds" + '</option><option value=10000>' + "10 seconds" + '</option></select>') + '</div>';
8924:                 }
8925:             } else {
8926:                 if ((rights & 64) != 0) { count++; y += '<option value=100>' + "Wake-up" + '</option>'; } // Wake-up permission
8927:                 if (((currentNode.conn & 1) != 0) && ((rights & 131072) != 0)) { count++; y += '<option value=106>' + "Run Commands" + '</option>'; } // Remote command permission
8928:                 if ((currentNode.conn != 0) && ((rights & 262144) != 0)) { count++; y += '<option value=4>' + "Sleep" + '</option><option value=3>' + "Reset" + '</option><option value=2>' + "Power off" + '</option>'; }
8929:                 if ((currentNode.conn & 16) != 0) { count++; y += '<option value=103>' + "Send MQTT Message" + '</option>'; }
8930:                 if ((currentNode.intelamt != null) && (currentNode.intelamt.state == 2) && ((currentNode.conn & 6) != 0) && ((rights & 262144) != 0)) {
8931:                     count++;
8932:                     y += '<option value=310>' + "Intel&reg; AMT Reset" + '</option>';
8933:                     y += '<option value=308>' + "Intel&reg; AMT Power off" + '</option>';
8934:                     if ((xxcurrentView == 11) || (xxcurrentView == 12)) { // Only show these options on terminal or desktop tabs
8935:                         y += '<option value=312>' + "Intel&reg; AMT Reset to BIOS" + '</option>';
8936:                         y += '<option value=311>' + "Intel&reg; AMT Power on to BIOS" + '</option>';
8937:                         y += '<option value=315>' + "Intel&reg; AMT Power on to PXE" + '</option>';
8938:                         y += '<option value=316>' + "Intel&reg; AMT Reset to PXE" + '</option>';
8939:                     }
8940:                 }
8941:                 if ((currentNode.intelamt != null) && (currentNode.intelamt.state == 2) && ((currentNode.conn & 6) != 0) && ((rights & 64) != 0)) {
8942:                     count++;
8943:                     y += '<option value=302>' + "Intel&reg; AMT Power on" + '</option>';
8944:                 }
8945:                 if (((xxcurrentView == 11) || (xxcurrentView == 12)) && (getNodeAmtVersion(currentNode) >= 15) && (currentNode.intelamt.state == 2) && ((currentNode.conn & 6) != 0) && (rights == 0xFFFFFFFF) && ((features & 0x00000400) == 0)) { count++; y += '<option value=107>' + "Intel&reg; AMT One Click Recovery" + '</option>'; } // CIRA (2) or AMT (4) connected
8946:                 if (((currentNode.conn & 1) != 0) && ((rights & 32768) != 0)) { count++; y += '<option value=104>' + "Uninstall Agent" + '</option>'; }
8947:             }
8948:             y += '</select>';
8949:             x += addHtmlValue("Operation", y);
8950:             if (count == 0) { x = "No actions currently available for this device."; }
8951:             setDialogMode(2, "Device Action", (count == 0)?2:3, deviceActionFunctionEx, x + z);
8952:             if (count > 0) { deviceActionFunctionValidate(); }
8953:         }
8954: 
8955:         function deviceActionFunctionValidate() {
8956:             var op = Q('d2deviceop').value;
8957:             try { QV('d2devicetimediv', (op == 400) || (op == 401)); } catch (ex) {}
8958:         }
8959: 
8960:         function deviceActionFunctionEx() {
8961:             var op = Q('d2deviceop').value;
8962:             if (op == 100) {
8963:                 // Device wake
8964:                 meshserver.send({ action: 'wakedevices', nodeids: [currentNode._id] });
8965:             } else if (op == 103) {
8966:                 // Send MQTT Message
8967:                 p10showSendMqttMsgDialog([currentNode._id]);
8968:             } else if (op == 104) {
8969:                 // Uninstall agent
8970:                 p10showSendUninstallAgentDialog([currentNode._id]);
8971:             } else if (op == 106) {
8972:                 // Run commands
8973:                 var wintype = false, linuxtype = false;
8974:                 if (currentNode.agent) { if (isWindowsNode(currentNode)) { wintype = true; } else { linuxtype = true; } }
8975:                 if ((wintype == true) || (linuxtype == true)) {
8976:                     var x = "Run commands on selected devices." + '<br />';
8977:                     if (wintype == true) {
8978:                         x += '<select id=d2cmdtype style=width:100%;margin-bottom:4px;margin-top:4px>';
8979:                         x += '<option value=1>' + "Windows Command Prompt" + '</option><option value=2>' + "Windows PowerShell" + '</option>';
8980:                         if (linuxtype == true) { x += '<option value=3>' + "Linux/BSD/macOS Command Shell" + '</option>'; }
8981:                         x += '</select>';
8982:                     }
8983:                     x += '<select id=d2cmduser style=width:100%;margin-bottom:4px><option value=0>' + "Run as agent" + '</option><option value=1>' + "Run as user, agent if no user" + '</option><option value=2>' + "Must run as user" + '</option></select>';
8984:                     x += '<textarea id=d2runcmd style=background-color:#fcf3cf;width:100%;height:200px;resize:none;overflow-y:scroll></textarea>';
8985:                     setDialogMode(2, "Run Commands", 3, deviceRunCmdsFunctionEx, x);
8986:                     Q('d2runcmd').focus();
8987:                     //QE('idx_dlgOkButton', true);
8988:                 }
8989:             } else if (op == 107) {
8990:                 // Intel AMT One Click Recovery (OCR)
8991:                 Q('d3localmodeform').action = 'oneclickrecovery.ashx';
8992:                 Q('d3auth').value = authCookie;
8993:                 Q('d3filter').value = '.efi';
8994:                 Q('d3attrib').value = currentNode._id;
8995:                 setDialogMode(3, "Intel&reg; AMT One Click Recovery", 3, deviceActionOneClickRecovery);
8996:                 d3init();
8997:             } else if (op == 302) { // Intel AMT power on
8998:                 setDialogMode(2, "Intel&reg; AMT Power Operation", 3, function() { meshserver.send({ action: 'poweraction', nodeids: [ currentNode._id ], actiontype: parseInt(op) }); }, "Perform Intel&reg; AMT power on?");
8999:             } else if (op == 308) { // Intel AMT power off
9000:                 setDialogMode(2, "Intel&reg; AMT Power Operation", 3, function() { meshserver.send({ action: 'poweraction', nodeids: [ currentNode._id ], actiontype: parseInt(op) }); }, "Perform Intel&reg; AMT power off?<br><br><b>NOTE: If there is an active AMT session, then power off command will be rejected, so you must disconnect from the AMT session first!</b>");
9001:             } else if (op == 310) { // Intel AMT reset
9002:                 setDialogMode(2, "Intel&reg; AMT Power Operation", 3, function() { meshserver.send({ action: 'poweraction', nodeids: [ currentNode._id ], actiontype: parseInt(op) }); }, "Perform Intel&reg; AMT reset?");
9003:             } else if (op == 311) { // Intel AMT power on to BIOS
9004:                 setDialogMode(2, "Intel&reg; AMT Power Operation", 3, function() { meshserver.send({ action: 'poweraction', nodeids: [ currentNode._id ], actiontype: parseInt(op) + ((xxcurrentView == 12) ? 2 : 0) }); }, "Perform Intel&reg; AMT power on to BIOS?");
9005:             } else if (op == 312) { // Intel AMT reset to BIOS
9006:                 setDialogMode(2, "Intel&reg; AMT Power Operation", 3, function() { meshserver.send({ action: 'poweraction', nodeids: [ currentNode._id ], actiontype: parseInt(op) + ((xxcurrentView == 12) ? 2 : 0) }); }, "Perform Intel&reg; AMT reset to BIOS?");
9007:             } else if (op == 315) { // Intel AMT power to PXE
9008:                 setDialogMode(2, "Intel&reg; AMT Power Operation", 3, function() { meshserver.send({ action: 'poweraction', nodeids: [ currentNode._id ], actiontype: parseInt(op) + ((xxcurrentView == 12) ? 2 : 0) }); }, "Perform Intel&reg; AMT power on to PXE?");
```

## Lines 9341-9527
```text
9341:             });
9342:             return false;
9343:         }
9344: 
9345:         // Show network interfaces
9346:         function p10showNodeNetInfoDialog() {
9347:             if (xxdialogMode) return false;
9348:             setDialogMode(2, "Network Interfaces", 1, null, '<div id=d2netinfo>' + "Loading..." + '</div>', 'if' + currentNode._id );
9349:             meshserver.send({ action: 'getnetworkinfo', nodeid: currentNode._id });
9350:             return false;
9351:         }
9352: 
9353:         // Show MeshCentral Router dialog
9354:         function p10showMeshRouterDialog() {
9355:             if (xxdialogMode) return;
9356:             var x = '<div>' + "MeshCentral Router is a Windows tool for TCP port mapping. You can, for example, RDP into a remote device thru this server." + '</div><br />';
9357:             x += addHtmlValue("Win32 Executable", '<a style=cursor:pointer onclick=downloadFile("meshagents?meshaction=winrouter' + (urlargs.key?('&key=' + urlargs.key):'') + '",null,true)>MeshCentralRouter.exe</a>');
9358:             x += addHtmlValue("MacOS Installer", '<a style=cursor:pointer onclick=downloadFile("meshagents?meshaction=macrouter' + (urlargs.key?('&key=' + urlargs.key):'') + '",null,true)>MeshCentralRouter.dmg</a>');
9359:             var servername = serverinfo.name;
9360:             if ((servername.indexOf('.') == -1) || ((features & 2) != 0)) { servername = window.location.hostname; } // If the server name is not set or it's in LAN-only mode, use the URL hostname as server name.
9361:             var domainUrlNoSlash = domainUrl.substring(0, domainUrl.length - 1);
9362:             var portStr = (serverinfo.port == 443) ? '' : (':' + serverinfo.port);
9363:             var url = 'mcrouter://' + servername + portStr + domainUrl + 'control.ashx?c=' + authCookie + '&t=' + serverinfo.tlshash + '&l={{{lang}}}' + (urlargs.key?('&key=' + urlargs.key):'');
9364:             //x += addHtmlValue("Launch", '<a style=cursor:pointer target="mcrouterframe" rel="noreferrer noopener" download href="' + url + '">Start MeshCentral Router</a>');
9365:             //x += '<br /><div style=width:100%><a style=cursor:pointer target="mcrouterframe" rel="noreferrer noopener" download href="' + url + '">' + "Start MeshCentral Router" + '</a>' + ", for this link to work you must download MeshCentral Router run it and click the install button." + '</div>';
9366:             x += '<br /><div>' + "Run MeshCentral Router and click \"install\" to make it launchable from the browser." + '</div>';
9367:             x += '<br /><a style=cursor:pointer target="mcrouterframe" rel="noreferrer noopener" href="' + url + '"><input type=button style=width:100%;cursor:pointer value="' + "Launch MeshCentral Router" + '" /></a>';
9368:             x += '<iframe style=display:none name="mcrouterframe"></iframe>';
9369:             setDialogMode(2, "MeshCentral Router", 1, null, x, 'fileDownload');
9370:         }
9371: 
9372:         // Request MQTT login credentials
9373:         function p10showMqttLoginDialog(nodeid) { meshserver.send({ action: 'getmqttlogin', nodeid: nodeid }); }
9374: 
9375:         // Open XTerm
9376:         function p10openxterm(e, nodeid) {
9377:             haltEvent(e);
9378:             var url = '/xterm?nodeid=' + encodeURIComponentEx(nodeid) + '&auto=1';
9379:             var node = getNodeFromId(nodeid);
9380:             if (node == null) return;
9381:             if ([1, 2, 3, 4, 21, 22].indexOf(node.agent.id) >= 0) { url += '&os=win'; } else { url += '&os=linux'; }
9382:             if (urlargs.key) { url += '&key=' + urlargs.key; }
9383:             safeNewWindow(url, 'xterm:' + nodeid);
9384:             return false;
9385:         }
9386: 
9387:         // Show MeshCmd dialog
9388:         function p10showMeshCmdDialog(mode, nodeid) {
9389:             if (xxdialogMode) return;
9390:             var y = '<select id=aginsSelect onchange=meshCmdOsClick() style=width:236px>';
9391:             y += '<option value=4>' + "Windows x86 (64bit)" + '</option>';
9392:             y += '<option value=3>' + "Windows x86 (32bit)" + '</option>';
9393:             y += '<option value=43>' + "Windows ARM (64bit)" + '</option>';
9394:             y += '<option value=6>' + "Linux x86 (64bit)" + '</option>';
9395:             y += '<option value=5>' + "Linux x86 (32bit)" + '</option>';
9396:             y += '<option value=16>' + "macOS x86 (64bit)" + '</option>';
9397:             y += '<option value=29>' + "macOS ARM (64bit)" + '</option>';
9398:             y += '<option value=25>' + "Linux ARM, Raspberry Pi (32bit)" + '</option>';
9399:             y += '<option value=26>' + "Linux ARM, Raspberry Pi (64bit)" + '</option>';
9400:             y += '</select>';
9401: 
9402:             var x = '';
9403:             if (mode == 0) { x += '<div>' + "MeshCmd is a command line tool that performs lots of different operations. The action file can optionally be downloaded and edited to provide server information and credentials." + '<br /><br />'; }
9404:             if (mode == 1) { x += '<div>' + "Download \"meshcmd\" with an action file to route traffic thru this server to this device. Make sure to edit meshaction.txt and add your account password or make any changes needed." + '<br /><br />'; }
9405:             x += addHtmlValue("Operating System", y);
9406:             x += addHtmlValue("MeshCmd", '<a id=meshcmddownloadid onclick=downloadFile("meshagents?meshcmd=3' + (urlargs.key?('&key=' + urlargs.key):'') + '")></a>');
9407:             if (mode == 0) { x += addHtmlValue("Action File", '<a onclick=downloadFile("meshagents?meshaction=generic' + (urlargs.key?('&key=' + urlargs.key):'') + '")>' + "MeshAction (.txt)" + '</a>'); }
9408:             if (mode == 1) { x += addHtmlValue("Action File", '<a onclick=downloadFile("meshagents?meshaction=route&nodeid=' + nodeid + (urlargs.key?('&key=' + urlargs.key):'') + '")>' + "MeshAction (.txt)" + '</a>'); }
9409:             x += '</div>';
9410:             setDialogMode(2, "Download MeshCmd", 9, null, x, 'fileDownload');
9411:             meshCmdOsClick();
9412:         }
9413: 
9414:         function meshCmdOsClick() {
9415:             var os = Q('aginsSelect').value, osn = '', osurl = '';
9416:             //Q('meshcmddownloadid').href = 'meshagents?meshcmd=' + os + (urlargs.key?('&key=' + urlargs.key):'');
9417:             if (os == 3) { osn = "MeshCmd (Win x86-32 executable)"; }
9418:             if (os == 4) { osn = "MeshCmd (Win x86-64 executable)"; }
9419:             if (os == 43) { osn = "MeshCmd (Win ARM-64 executable)"; }
9420:             if (os == 5) { osn = "MeshCmd (Linux x86, 32bit)"; }
9421:             if (os == 6) { osn = "MeshCmd (Linux x86, 64bit)"; }
9422:             if (os == 16) { osn = "MeshCmd (macOS, x86-64bit)"; }
9423:             if (os == 29) { osn = "MeshCmd (macOS, ARM-64bit)"; }
9424:             if (os == 25) { osn = "MeshCmd (Linux ARM, 32bit)"; }
9425:             if (os == 26) { osn = "MeshCmd (Linux ARM, 64bit)"; }
9426:             QH('meshcmddownloadid', osn);
9427:             Q('meshcmddownloadid').onclick = function() { downloadFile('meshagents?meshcmd=' + os + (urlargs.key?('&key=' + urlargs.key):'')); }
9428:         }
9429: 
9430:         function p10showiconselector() {
9431:             if (xxdialogMode) return;
9432:             if ((GetNodeRights(currentNode) & 4) == 0) return;
9433: 
9434:             var x = '<div style=text-align:center><br />';
9435:             x += '<div tabindex=0 style="display:inline-block;margin:0 10px 0 10px" class=i1 onclick=p10setIcon(1) onkeypress="if (event.key==\'Enter\') p10setIcon(1)"></div>';
9436:             x += '<div tabindex=0 style="display:inline-block;margin:0 10px 0 10px" class=i2 onclick=p10setIcon(2) onkeypress="if (event.key==\'Enter\') p10setIcon(2)"></div>';
9437:             x += '<div tabindex=0 style="display:inline-block;margin:0 10px 0 10px" class=i3 onclick=p10setIcon(3) onkeypress="if (event.key==\'Enter\') p10setIcon(3)"></div>';
9438:             x += '<div tabindex=0 style="display:inline-block;margin:0 10px 0 10px" class=i4 onclick=p10setIcon(4) onkeypress="if (event.key==\'Enter\') p10setIcon(4)"></div>';
9439:             x += '<br />';
9440:             x += '<div tabindex=0 style="display:inline-block;margin:0 10px 0 10px" class=i5 onclick=p10setIcon(5) onkeypress="if (event.key==\'Enter\') p10setIcon(5)"></div>';
9441:             x += '<div tabindex=0 style="display:inline-block;margin:0 10px 0 10px" class=i6 onclick=p10setIcon(6) onkeypress="if (event.key==\'Enter\') p10setIcon(6)"></div>';
9442:             x += '<div tabindex=0 style="display:inline-block;margin:0 10px 0 10px" class=i7 onclick=p10setIcon(7) onkeypress="if (event.key==\'Enter\') p10setIcon(7)"></div>';
9443:             x += '<div tabindex=0 style="display:inline-block;margin:0 10px 0 10px" class=i8 onclick=p10setIcon(8) onkeypress="if (event.key==\'Enter\') p10setIcon(8)"></div>';
9444:             x += '<br /><br /></div>';
9445:             setDialogMode(2, "Icon Selection", 0, null, x);
9446:             QV('id_dialogclose', true);
9447:         }
9448: 
9449:         function p10setIcon(icon) {
9450:             setDialogMode(0);
9451:             meshserver.send({ action: 'changedevice', nodeid: currentNode._id, icon: icon });
9452:         }
9453: 
9454:         function showClearSshDialog() { setDialogMode(2, "Edit Device", 3, showClearSshDialogEx, "Clear SSH credentials?"); }
9455:         function showClearSshDialogEx(button, mode) { meshserver.send({ action: 'changedevice', nodeid: currentNode._id, ssh: 0 }); }
9456:         function showClearRdpDialog() { setDialogMode(2, "Edit Device", 3, showClearRdpDialogEx, "Clear RDP credentials?"); }
9457:         function showClearRdpDialogEx(button, mode) { meshserver.send({ action: 'changedevice', nodeid: currentNode._id, rdp: 0 }); }
9458: 
9459:         var showEditNodeValueDialog_modes = ["Device Name", "Hostname", "Description", "Tags"];
9460:         var showEditNodeValueDialog_modes2 = ['name', 'host', 'desc', 'tags'];
9461:         var showEditNodeValueDialog_modes3 = ['', '', '', "Tag1, Tag2, Tag3"];
9462:         var showEditNodeValueDialog_modes4 = [64, 64, 64, 4096];
9463:         function showEditNodeValueDialog(mode) {
9464:             if (xxdialogMode) return;
9465:             var x = addHtmlValue(showEditNodeValueDialog_modes[mode], '<input id=dp10devicevalue maxlength=' + showEditNodeValueDialog_modes4[mode] + ' placeholder="' + showEditNodeValueDialog_modes3[mode] + '" onchange=p10editdevicevalueValidate(' + mode + ',event) onkeyup=p10editdevicevalueValidate(' + mode + ',event) />');
9466:             if (mode == 3) {
9467:                 // Get a list of all possible device tags
9468:                 var allTags = [], y = '';
9469:                 for (var i in nodes) { if (nodes[i].tags) { for (var j in nodes[i].tags) { if (allTags.indexOf(nodes[i].tags[j]) == -1) { allTags.push(nodes[i].tags[j]); } } } }
9470:                 if (allTags.length > 0) {
9471:                     allTags.sort();
9472:                     for (var i in allTags) { y += '<span style=padding:4px;background-color:#BBB;border-radius:3px;cursor:pointer onclick=showEditNodeValueDialogAddTag("' + encodeURIComponentEx(allTags[i]) + '")>' + EscapeHtml(allTags[i]) + '</span> '; }
9473:                     x += '<div style=margin-top:8px;width:370px;line-height:26px;max-height:160px;overflow-y:auto>' + y + '</div>';
9474:                 }
9475:             }
9476:             setDialogMode(2, "Edit Device", 3, showEditNodeValueDialogEx, x, mode);
9477:             var v = currentNode[showEditNodeValueDialog_modes2[mode]];
9478:             if (v == null) v = '';
9479:             if (Array.isArray(v)) { v = v.join(', '); }
9480:             Q('dp10devicevalue').value = v;
9481:             p10editdevicevalueValidate();
9482:             Q('dp10devicevalue').focus();
9483:         }
9484: 
9485:         function showEditNodeValueDialogAddTag(t) {
9486:             var tt = Q('dp10devicevalue').value.split(','), t2 = [];
9487:             for (var i in tt) { t2.push(tt[i].trim()); }
9488:             if (t2.indexOf(t) >= 0) return;
9489:             Q('dp10devicevalue').value += ((Q('dp10devicevalue').value.length == 0)?'':', ') + decodeURIComponent(t);
9490:             setTimeout(function(){ Q('dp10devicevalue').selectionStart = Q('dp10devicevalue').selectionEnd = 90000; }, 0);
9491:             p10editdevicevalueValidate();
9492:         }
9493: 
9494:         function showEditNodeValueDialogEx(button, mode) {
9495:             var x = { action: 'changedevice', nodeid: currentNode._id };
9496:             x[showEditNodeValueDialog_modes2[mode]] = Q('dp10devicevalue').value;
9497:             meshserver.send(x);
9498:         }
9499: 
9500:         function p10editdevicevalueValidate(mode, e) {
9501:             var x = ((mode > 1) || (Q('dp10devicevalue').value.length > 0));
9502:             QE('idx_dlgOkButton', x);
9503:             if ((e != null) && (x == true) && (e.keyCode == 13)) { dialogclose(1); }
9504:         }
9505: 
9506:         //
9507:         // DESKTOP
9508:         //
9509: 
9510:         var desktopNode;
9511:         function setupDesktop() {
9512:             // Setup the remote desktop
9513:             if ((desktopNode != currentNode) && (desktop != null)) {
9514:                 if (desktopNode._id != currentNode._id) {
9515:                     desktop.Stop(); desktopNode = null; desktop = null;
9516:                 }
9517:             }
9518: 
9519:             // If the device desktop is already connected in multi-desktop, use that.
9520:             if ((desktopNode != currentNode) || (desktop == null)) {
9521:                 var xdesk = multiDesktop[currentNode._id];
9522:                 if (xdesk != null) {
9523:                     // This device already has a canvas, use it.
9524:                     QH('DeskParent', '');
9525:                     var c = xdesk.m.CanvasId;
9526:                     c.setAttribute('id', 'Desk');
9527:                     c.setAttribute('onmousedown', 'dmousedown(event)');
```

## Lines 15226-15478
```text
15226:                         var xx = 0;
15227:                         if (meshNotify & 16) { xx++; } if (meshNotify & 32) { xx++; } if (meshNotify & 64) { xx++; }
15228:                         if (xx > 0) { meshNotifyStr.push(format("Email ({0})", xx)); }
15229:                     }
15230:                     if ((userinfo.msghandle != null) && ((features2 & 0x02000000) != 0)) {
15231:                         var xx = 0;
15232:                         if (meshNotify & 128) { xx++; } if (meshNotify & 256) { xx++; } if (meshNotify & 512) { xx++; }
15233:                         if (xx > 0) { meshNotifyStr.push(format("Messaging ({0})", xx)); }
15234:                     }
15235:                     if (meshNotifyStr.length == 0) { meshNotifyStr.push('<i>' + "None" + '</i>'); }
15236:                     x += addHtmlValue("Notifications", addLink(meshNotifyStr.join(', '), 'p20editMeshNotify()'));
15237:                 }
15238:             }
15239: 
15240:             // Display invitation codes
15241:             if ((features & 0x01000000) && (currentMesh.mtype == 2)) {
15242:                 var inviteCodeStr = '<i>' + "None" + '</i>', icodes = false;
15243:                 if (currentMesh.invite != null) { icodes = true; inviteCodeStr = EscapeHtml(currentMesh.invite.codes.join(', ')); /* + ', ' + currentMesh.invite.flags;*/ }
15244:                 x += addHtmlValue("Invite Codes", addLinkConditional(inviteCodeStr, 'p20editmeshInviteCode()', (meshrights & 1) || (icodes)));
15245:             }
15246: 
15247:             // If the Intel AMT manager is active on the server, show the Intel AMT policy edit box.
15248:             if ((currentMesh.mtype < 3) && ((features2 & 1) != 0)) {
15249:                 // Intel AMT setup
15250:                 var intelAmtPolicy = "No Policy";
15251:                 if (currentMesh.amt) {
15252:                     if (currentMesh.amt.type == 1) { intelAmtPolicy = "Deactivate"; }
15253:                     else if (currentMesh.amt.type == 2) {
15254:                         intelAmtPolicy = "Simple Client Control Mode (CCM)";
15255:                         if (currentMesh.amt.cirasetup == 2) { intelAmtPolicy += " + CIRA"; }
15256:                     } else if (currentMesh.amt.type == 3) {
15257:                         intelAmtPolicy = "Simple Admin Control Mode (ACM)";
15258:                         if (currentMesh.amt.cirasetup == 2) { intelAmtPolicy += " + CIRA"; }
15259:                     } else if (currentMesh.amt.type == 4) {
15260:                         intelAmtPolicy = "Fully Automatic";
15261:                     }
15262:                 }
15263:                 x += addHtmlValue("Intel&reg; AMT", addLinkConditional(intelAmtPolicy, 'p20editMeshAmt()', meshrights & 1));
15264:             }
15265: 
15266:             // Display device group note support
15267:             if (meshrights & 1) { x += '<br><input type=button value=' + "Notes" + ' title="' + "View notes about this device group" + '" onclick=showNotes(false,"' + encodeURIComponentEx(currentMesh._id) + '") />'; }
15268: 
15269:             x += '<br style=clear:both><br>';
15270:             if (meshrights & 2) {
15271:                 x += '<a href=# onclick="return p20showAddMeshUserDialog()" style=cursor:pointer;margin-right:10px><img src=images/icon-addnew.png border=0 height=12 width=12> ' + "Add Users" + '</a>';
15272:                 if (usergroups != null) {
15273:                     var userGroupCount = 0, newUserGroup = false;
15274:                     for (var i in usergroups) {
15275:                         if ((usergroups[i].membershipType != null) || (usergroups[i]._id.split('/')[1] != currentMesh._id.split('/')[1])) continue;
15276:                         userGroupCount++;
15277:                         if ((currentMesh.links == null) || (currentMesh.links[i] == null)) { newUserGroup = true; }
15278:                     }
15279:                     if ((userGroupCount > 0) && (newUserGroup)) {  x += '<a href=# onclick="return p20showAddMeshUserDialog(2)" style=cursor:pointer;margin-right:10px><img src=images/icon-addnew.png border=0 height=12 width=12> ' + "Add User Group" + '</a>'; }
15280:                 }
15281:             }
15282: 
15283:             //if (meshrights & 4) { }
15284:             if (currentMesh.mtype == 1) {
15285:                 if (meshrights & 1) { x += '<a href=# style=cursor:pointer;margin-right:10px title="' + "Import Intel&reg; AMT devices." + '" onclick=\'return showAmtImport("' + currentMesh._id + '")\'><img src=images/icon-installmesh.png border=0 height=12 width=12> ' + "Import" + '</a>'; }
15286:                 /*
15287:                 if ((features & 1) == 0) { // If not WAN-Only
15288:                     x += '<a href=# onclick=\'return addDeviceToMesh("' + currentMesh._id + '")\' style=cursor:pointer;margin-right:10px title="' + "Add a new Intel&reg; AMT computer that is located on the local network." + '"><img src=images/icon-installmesh.png border=0 height=12 width=12> ' + "Install local" + '</a>';
15289:                 }
15290:                 */
15291:                 if ((currentMesh.amt != null) && (currentMesh.amt.type > 0)) { // CCM Deactivate, CCM or ACM activation
15292:                     x += '<a href=# style=cursor:pointer;margin-right:10px title="' + "Perform Intel&reg; AMT activation and configuration." + '" onclick=\'return showAmtSetup("' + currentMesh._id + '")\'><img src=images/icon-installmesh.png border=0 height=12 width=12> ' + "Setup" + '</a>';
15293:                 }
15294:             }
15295:             if ((currentMesh.mtype == 2) && ((userinfo.siteadmin == 0xFFFFFFFF) || ((userinfo.siteadmin & 4096) == 0))) {
15296:                 x += '<a href=# onclick=\'return addAgentToMesh("' + currentMesh._id + '")\' style=cursor:pointer;margin-right:10px title="' + "Add a new computer to this device group by installing the mesh agent." + '"><img src=images/icon-addnew.png border=0 height=12 width=12> ' + "Add Agent" + '</a>';
15297:                 x += '<a href=# onclick=\'return inviteAgentToMesh("' + currentMesh._id + '")\' style=cursor:pointer;margin-right:10px title="' + "Invite someone to install the mesh agent on this device group." + '"><img src=images/icon-addnew.png border=0 height=12 width=12> ' + "Invite" + '</a>';
15298:             }
15299:             if ((currentMesh.mtype == 3) && ((userinfo.siteadmin == 0xFFFFFFFF) || ((userinfo.siteadmin & 4096) == 0))) {
15300:                 x += '<a href=# onclick=\'return addLocalDeviceToMesh("' + currentMesh._id + '")\' style=cursor:pointer;margin-right:10px title="' + "Add device located on the local network." + '"><img src=images/icon-addnew.png border=0 height=12 width=12> ' + "Add Device" + '</a>';
15301:             }
15302:             if (currentMesh.amt && (currentMesh.amt.type > 2) && ((userinfo.siteadmin == 0xFFFFFFFF) || ((userinfo.siteadmin & 4096) == 0))) { // ACM activation or Full Automatic
15303:                 x += '<a href=# style=cursor:pointer;font-size:small title="' + "Switch Intel AMT to Admin Control Mode (ACM)." + '" onclick=\'return showAmtAcmSetup()\'><img src=images/icon-installmesh.png border=0 height=12 width=12> ' + "ACM" + '</a>';
15304:             }
15305: 
15306:             x += '<table style="color:black;background-color:#EEE;border-color:#AAA;border-width:1px;border-style:solid;border-collapse:collapse" border=0 cellpadding=2 cellspacing=0 width=100%><tbody><tr style=background-color:#AAAAAA;font-weight:bold><th scope=col style=text-align:left;width:430px>' + "User Authorizations" + '</th><th scope=col style=text-align:left></th></tr>';
15307: 
15308:             // Sort the users for this mesh
15309:             var count = 1, sortedusers = [];
15310:             for (var i in currentMesh.links) {
15311:                 var uname = i.split('/')[2];
15312:                 if (currentMesh.links[i].name) { uname = currentMesh.links[i].name; }
15313:                 if (i == userinfo._id) { uname = userinfo.name; }
15314:                 if ((usergroups != null) && (usergroups[i] != null)) { uname = usergroups[i].name; }
15315:                 sortedusers.push({ id: i, name: uname, rights: currentMesh.links[i].rights });
15316:             }
15317:             sortedusers.sort(function(a, b) { if (a.name > b.name) return 1; if (a.name < b.name) return -1; return 0; });
15318: 
15319:             // Display all users for this device group
15320:             for (var i in sortedusers) {
15321:                 var trash = '', r = sortedusers[i].rights, rights = makeDeviceGroupRightsString(r), icon = 2;
15322:                 if ((sortedusers[i].id != userinfo._id) && (meshrights == 0xFFFFFFFF || (((meshrights & 2) != 0)))) {
15323:                     if ((meshrights == 0xFFFFFFFF) || (currentMesh.links[sortedusers[i].id].rights != 0xFFFFFFFF)) {
15324:                         trash = '<a href=# onclick=\'return p20deleteUser(event,"' + encodeURIComponentEx(sortedusers[i].id) + '")\' title="' + "Remove user rights to this device group" + '" style=cursor:pointer><img src=images/trash.png border=0 height=10 width=10></a>';
15325:                     }
15326:                     rights = '<span tabindex=0 style=cursor:pointer onclick=p20viewuser("' + encodeURIComponentEx(sortedusers[i].id) + '") onkeypress="if (event.key==\'Enter\') p20viewuser(\'' + encodeURIComponentEx(sortedusers[i].id) + '\')">' + rights + ' <img class=hoverButton style=cursor:pointer src=images/link5.png></span>';
15327:                 }
15328:                 if (sortedusers[i].id.startsWith('ugrp/')) { icon = 4; }
15329:                 var username = EscapeHtml(decodeURIComponent(sortedusers[i].name));
15330:                 if ((usergroups != null) && sortedusers[i].id.startsWith('ugrp/')) { username = '<a tabindex=0 href=# onclick=\'gotoUserGroup("' + encodeURIComponentEx(sortedusers[i].id) + '");haltEvent(event);\'>' + username + '</a>'; }
15331:                 if ((users != null) && sortedusers[i].id.startsWith('user/')) { username = '<a tabindex=0 href=# onclick=\'gotoUser("' + encodeURIComponentEx(sortedusers[i].id) + '");haltEvent(event);\'>' + username + '</a>'; }
15332:                 x += '<tr style=' + (((count % 2) == 0) ? ';background-color:#DDD' : '') + '><td style=width:30%><div title="' + "User" + '" class=m' + icon + '></div><div>&nbsp;' + username + '<div></div></div></td><td style=width:70%><div style=float:right>' + trash + '</div><div>' + rights + '</div></td></tr>';
15333:                 ++count;
15334:             }
15335: 
15336:             x += '</tbody></table>';
15337:             
15338:             // Show device shares
15339:             if ((deviceShares != null) && (deviceSharesNode == currentMesh._id) && (deviceShares.length > 0)) {
15340:                 x += '<p></p><table style="color:black;background-color:#EEE;border-color:#AAA;border-width:1px;border-style:solid;border-collapse:collapse" border=0 cellpadding=2 cellspacing=0 width=100%><tbody><tr style=background-color:#AAAAAA;font-weight:bold><th scope=col style=text-align:left;width:430px>' + "Active Device Sharing" + '</th><th scope=col style=text-align:left><th scope=col style=text-align:left></th></tr>';
15341:                 count = 1;
15342:                 for (var i = 0; i < deviceShares.length; i++) {
15343:                     var dshare = deviceShares[i], trash = '';
15344:                     if (dshare.url != null) { trash += '<a href="' + dshare.url + '" rel="noreferrer noopener" target=_blank title="' + "Device Sharing Link" + '" style=cursor:pointer><img src=images/link2.png border=0 height=10 width=10></a> '; }
15345:                     trash += '<a href=# onclick=\'return p30removeDeviceSharing(event,"' + encodeURIComponentEx(dshare.nodeid) + '","' + encodeURIComponentEx(dshare.publicid) + '","' + encodeURIComponentEx(dshare.guestName) + '")\' title="' + "Remove device sharing" + '" style=cursor:pointer><img src=images/trash.png border=0 height=10 width=10></a>';
15346:                     var type = ''; if (dshare.p <= 7) { type = ['', "Terminal", "Desktop", "Desktop + Terminal", "Files", "Terminal + Files", "Desktop + Files", "Desktop + Terminal + Files"][dshare.p]; } else if (dshare.p == 8) { type = "HTTP/" + dshare.port; } else if (dshare.p == 16) { type = "HTTPS/" + dshare.port; }
15347:                     var details = type;
15348:                     if ((dshare.startTime != null) && (dshare.expireTime != null)) { details = format("{0}, {1} to {2}", type, printFlexDateTime(new Date(dshare.startTime)), printFlexDateTime(new Date(dshare.expireTime))); }
15349:                     if ((dshare.startTime != null) && (dshare.duration != null)) {
15350:                         if (dshare.duration < 2) {
15351:                             details = format("{0}, {1} for {2} minute", type, printFlexDateTime(new Date(dshare.startTime)), dshare.duration);
15352:                         } else {
15353:                             details = format("{0}, {1} for {2} minutes", type, printFlexDateTime(new Date(dshare.startTime)), dshare.duration);
15354:                         }
15355:                     }
15356:                     if (dshare.recurring == 1) { details += ", Reccuring daily"; }
15357:                     if (dshare.recurring == 2) { details += ", Reccuring weekly"; }
15358:                     if (((dshare.p & 2) != 0) && (dshare.viewOnly === true)) { details += ", View only desktop"; }
15359:                     if (dshare.consent != null) {
15360:                         if (dshare.consent == 0) { details += ", No Consent"; } else {
15361:                             if ((dshare.consent & 0x0038) != 0) { details += ", Prompt for consent"; }
15362:                             if ((dshare.consent & 0x0040) != 0) { details += ", Toolbar"; }
15363:                         }
15364:                     }
15365:                     var guestName = EscapeHtml(dshare.guestName);
15366:                     if (dshare.publicid.startsWith('AS:node/')) { guestName = '<i>' + "Agent Self-Share" + '</i>'; }
15367:                     var node = getNodeFromId(dshare.nodeid);
15368:                     if (node != null) {
15369:                         var gray = ((node.conn > 0)?'':' gray');
15370:                         var computer = '<div onclick=\'gotoDevice("' + node._id + '",10);haltEvent(event);\' style=float:left class="j' + node.icon + gray + '"></div>&nbsp;<a onclick=\'gotoDevice("' + node._id + '",10);haltEvent(event);\'>' + EscapeHtml(node.name) + '</a>';
15371:                         x += '<tr ' + (((++count % 2) == 0) ? 'style=background-color:#DDD' : '') + '><td style=width:30%>' + computer + '<td style=width:30%><div class=m' + 2 + '></div><div>&nbsp;' + guestName + '<div></div></div></td><td style=width:70%><div style=float:right>' + trash + '</div><div>' + details + '</div></td></tr>';
15372:                     }
15373:                 }
15374:                 x += '</tbody></table>';
15375:             } else {
15376:                 // Request device sharing
15377:                 if ((deviceSharesNode != currentMesh._id) && (deviceSharesReq != currentMesh._id)) {
15378:                     deviceSharesReq = currentMesh._id;
15379:                     meshserver.send({ action: 'deviceMeshShares', meshid: currentMesh._id });
15380:                 }
15381:             }
15382: 
15383:             // Display list of devices in this device group
15384:             count = 0;
15385:             nodes.sort(deviceSort);
15386:             var dllist = '';
15387:             if (currentMesh.mtype == 1) {
15388:                 dllist += '<a onclick=meshDownloadDeviceList()><img title="' + "Download device list" + '" src="images/link4.png" /></a>';
15389:                 if ((features & 1) == 0) { dllist += ' <a onclick=meshImportDeviceList()><img title="' + "Import device list" + '" src="images/link6.png" /></a>'; } // Show import only in LAN or Hybrid mode
15390:             }
15391:             
15392:             var y = '<table style="color:black;background-color:#EEE;border-color:#AAA;border-width:1px;border-style:solid;border-collapse:collapse" border=0 cellpadding=2 cellspacing=0 width=100%><tbody><tr style=background-color:#AAAAAA;font-weight:bold><th scope=col style=text-align:left;width:430px>' + "Devices" + '</th><th scope=col style=text-align:right>' + dllist + '</th></tr>';
15393:             for (var i in nodes) {
15394:                 var node = nodes[i], gray = ((node.conn > 0)?'':' gray');
15395:                 if (currentMesh._id != node.meshid) continue;
15396:                 y += '<tr style=' + (((count % 2) == 0) ? ';background-color:#DDD' : '') + '><td style=width:30%><div onclick=\'gotoDevice("' + node._id + '",10);haltEvent(event);\' style=float:left class="j' + node.icon + gray + '"></div>&nbsp;<a onclick=\'gotoDevice("' + node._id + '",10);haltEvent(event);\'>' + EscapeHtml(node.name) + '</a></div></div></td><td style=width:70%><div style=float:right>' + PowerStateStr(node.pwr) + '&nbsp;</div><div>' + (node.osdesc ? EscapeHtml(node.osdesc) : '') + '</div></td></tr>';
15397:                 ++count;
15398:             }
15399:             if (count == 0) { y += '<tr><td><i>' + "None" + '</i></td></tr>'; }
15400:             y += '</tbody></table>';
15401: 
15402:             // If we are full administrator on this mesh, allow deletion of the mesh
15403:             if (meshrights == 0xFFFFFFFF) {
15404:                 y += '<div style=font-size:small;text-align:right><span><a href=# onclick=p20showDeleteMeshDialog() style=cursor:pointer>' + "Delete Group" + '</a></span></div>';
15405:             }
15406: 
15407:             QH('p20info', x);
15408:             QH('p20info2', y);
15409: 
15410:             // Change the URL
15411:             var urlviewmode = '';
15412:             if (((features & 0x10000000) == 0) && (xxcurrentView >= 20) && (xxcurrentView <= 29) && (currentMesh != null)) {
15413:                 urlviewmode = '?viewmode=' + xxcurrentView + '&gotomesh=' + currentMesh._id.split('/')[2];
15414:                 for (var i in urlargs) { urlviewmode += ('&' + i + '=' + urlargs[i]); }
15415:                 try { window.history.replaceState({}, document.title, window.location.pathname + urlviewmode); } catch (ex) { }
15416:             }
15417:         }
15418:         
15419:         // Save the list of devices in a device group in the MeshCommander format
15420:         function meshDownloadDeviceList() {
15421:             if (xxdialogMode) return;
15422:             var r = { webappversion: '0.9.0', computers: [ ] };
15423:             for (var i in nodes) {
15424:                 var node = nodes[i];
15425:                 if ((currentMesh._id != node.meshid) || (node.intelamt == null)) continue;
15426:                 var c = { name: node.name, host: node.host, user: node.intelamt.user, pass: '', tls: (node.intelamt.tls == 1)?1:0, ver: node.intelamt.ver, pstate: node.intelamt.state }
15427:                 if (node.intelamt.state == 2) { c.pmode = 1; } // TODO
15428:                 if (node.intelamt.realm) { c.digestrealm = node.intelamt.realm; }
15429:                 if (node.intelamt.hash) { c.tlscerthash = node.intelamt.hash; }
15430:                 r.computers.push(c);
15431:             }
15432:             saveAs(stringToUtf8BlobNoHeader(JSON.stringify(r, null, 2)), currentMesh.name + '.json');
15433:         }
15434: 
15435:         // Import a list of devices into the Intel AMT only device group
15436:         function meshImportDeviceList() {
15437:             if (xxdialogMode) return;
15438:             var x = "Import a list of local Intel&reg; AMT devices in MeshCommander JSON format." + '<br /><br /><input style=width:370px type=file id=d4importFile accept=".json" onchange=meshImportDeviceListValidate() />';
15439:             setDialogMode(2, "Import Intel&reg; AMT Devices", 3, meshImportDeviceListEx, x);
15440:             QE('idx_dlgOkButton', false);
15441:         }
15442: 
15443:         function meshImportDeviceListValidate() {
15444:             QE('idx_dlgOkButton', Q('d4importFile').value != null);
15445:         }
15446: 
15447:         function meshImportDeviceListEx() {
15448:             var fr = new FileReader();
15449:             fr.onload = function (r) {
15450:                 var j = null;
15451:                 try { j = JSON.parse(r.target.result); } catch (ex) { setDialogMode(2, "Import Intel&reg; AMT Devices", 1, null, format("Invalid JSON file: {0}.", ex)); return; }
15452:                 if ((typeof j == 'object') && (typeof j.webappversion == 'string') && (typeof j.computers == 'object') && (Array.isArray(j.computers))) {
15453:                     var ok = 0;
15454:                     for (var k in j.computers) {
15455:                         var computer = j.computers[k];
15456:                         if ((typeof computer.name == 'string') && (typeof computer.host == 'string') && (typeof computer.user == 'string') && (typeof computer.pass == 'string')) {
15457:                             var cmd = { action: 'addamtdevice', meshid: currentMesh._id, devicename: computer.name, hostname: computer.host, amtusername: computer.user, amtpassword: computer.pass, amttls: (computer.tls == 1)?1:0 };
15458:                             if (typeof computer.ver == 'string') { cmd.ver = computer.ver; }
15459:                             if (typeof computer.pstate == 'number') { cmd.state = computer.pstate; }
15460:                             if (typeof computer.digestrealm == 'string') { cmd.realm = computer.digestrealm; }
15461:                             if (typeof computer.tlscerthash == 'string') { cmd.hash = computer.tlscerthash; }
15462:                             meshserver.send(cmd);
15463:                             ok++;
15464:                         }
15465:                     }
15466:                     if (ok == 0) { setDialogMode(2, "User Account Import", 1, null, "Unable to import any device."); }
15467:                 } else { setDialogMode(2, "Import Intel&reg; AMT Devices", 1, null, "Invalid JSON file format."); }
15468:             };
15469:             fr.readAsText(Q('d4importFile').files[0]);
15470:         }
15471: 
15472:         function p20editMeshAmt() {
15473:             if (xxdialogMode) return;
15474:             var x = '', acmoption = '';
15475:             if ((features & 0x100000) != 0) { acmoption = '<option value=3>' + "Simple Admin Control Mode (ACM)" + '</option>'; }
15476:             x += addHtmlValue("Type", '<select id=dp20amtpolicy style=width:230px onchange=p20editMeshAmtChange()><option value=0>' + "No Policy" + '</option><option value=1>' + "Deactivate" + '</option><option value=2>' + "Simple Client Control Mode (CCM)" + '</option>' + acmoption + '<option value=4>' + "Fully Automatic" + '</option></select>');
15477:             x += '<div id=dp20amtpolicydiv></div>';
15478:             setDialogMode(2, "Intel&reg; AMT Policy", 3, p20editMeshAmtEx, x);
```

## Lines 16190-16365
```text
16190:                 setDialogMode(2, "Device Group User", buttons, p20viewuserEx, x, xuserid);
16191:             }
16192:         }
16193: 
16194:         function p20viewuserEx(button, userid) {
16195:             if (button != 2) return;
16196:             var uname = userid.split('/')[2];
16197:             if (users && users[userid]) { uname = users[userid].name; }
16198:             if (usergroups && usergroups[userid]) { uname = usergroups[userid].name; }
16199:             if (userinfo._id == userid) { uname = userinfo.name; }
16200:             if (userid.startsWith('user/')) { setDialogMode(2, "Remove User Permissions", 3, p20viewuserEx2, format("Confirm removal of rights for user \"{0}\"?", EscapeHtml(decodeURIComponent(uname))), userid); }
16201:             if (userid.startsWith('ugrp/')) { setDialogMode(2, "Remove User Group Permissions", 3, p20viewuserEx2, format("Confirm removal of rights for user group \"{0}\"?", EscapeHtml(decodeURIComponent(uname))), userid); }
16202:         }
16203:         function p20deleteUser(e, userid) { haltEvent(e); p20viewuserEx(2, decodeURIComponent(userid)); return false; }
16204:         function p20viewuserEx2(button, userid) { meshserver.send({ action: 'removemeshuser', meshid: currentMesh._id, meshname: currentMesh.name, userid: userid }); }
16205: 
16206:         function p20editmeshInviteCode() {
16207:             if (xxdialogMode) return false;
16208:             var meshrights = GetMeshRights(currentMesh);
16209: 
16210:             var servername = serverinfo.name;
16211:             if ((servername.indexOf('.') == -1) || ((features & 2) != 0)) { servername = window.location.hostname; } // If the server name is not set or it's in LAN-only mode, use the URL hostname as server name.
16212:             var url;
16213:             if (serverinfo.https == true) {
16214:                 var portStr = (serverinfo.port == 443) ? '' : (':' + serverinfo.port);
16215:                 url = 'https://' + servername + portStr + domainUrl + 'invite' + ((urlargs.key)?('?key=' + urlargs.key):'');
16216:             } else {
16217:                 var portStr = (serverinfo.port == 80) ? '' : (':' + serverinfo.port);
16218:                 url = 'http://' + servername + portStr + domainUrl + 'invite' + ((urlargs.key)?('?key=' + urlargs.key):'');
16219:             }
16220: 
16221:             if (meshrights & 1) {
16222:                 // We can edit the mesh invite codes
16223:                 var x = "When enabled, invitation codes can be used by anyone to join devices to this device group using the following public link:" + '<br /><br />';
16224:                 x += '<div style=width:100%;text-align:center><a rel="noreferrer noopener" target=_blank href="' + url + '">' + url + '</a></div><br />';
16225:                 x += '<div style=margin-bottom:5px><label><input id=agentJoinCheck type=checkbox onclick=p20editmeshInviteCodeValidate() />' + "Enable Invite Codes" + '</label></div>';
16226:                 x += addHtmlValue("Invite Codes", '<input id=agentInviteCode style=width:236px onkeyup=p20editmeshInviteCodeValidate() placeholder="code1, code2, code3" />');
16227:                 x += addHtmlValue("Agents", '<select id=agentType style=width:236px onchange=p20editmeshInviteCodeValidate()><option value=0>' + "All Available Agents" + '</option><option value=1>' + "Windows MeshAgent" + '</option><option value=2>' + "Linux MeshAgent" + '</option><option value=4>' + "MacOS MeshAgent" + '</option><option value=8>' + "MeshCentral Assistant" + '</option></select>');
16228:                 x += '<div id=d2agentInstallTypeDiv>';
16229:                 x += addHtmlValue("Installation Type", '<select id=agentInviteType style=width:236px><option value=0>' + "Background and interactive" + '</option><option value=2>' + "Background only" + '</option><option value=1>' + "Interactive only" + '</option></select>');
16230:                 x += '</div>';
16231:                 setDialogMode(2, "Invite Codes", 3, p20editmeshInviteCodeEx, x);
16232:                 if (currentMesh.invite != null) {
16233:                     Q('agentJoinCheck').checked = true;
16234:                     Q('agentInviteCode').value = currentMesh.invite.codes.join(', ');
16235:                     if (currentMesh.invite.ag) { Q('agentType').value = currentMesh.invite.ag; }
16236:                     Q('agentInviteType').value = (currentMesh.invite.flags & 3);
16237:                 }
16238:                 p20editmeshInviteCodeValidate();
16239:             } else {
16240:                 // View codes only
16241:                 var x = "Invitation codes can be used by anyone to join devices to this device group using the following public link:" + '<br /><br />';
16242:                 x += '<div style=width:100%;text-align:center><a rel="noreferrer noopener" target=_blank href="' + url + '">' + url + '</a></div><br />';
16243:                 x += addHtmlValue("Invite Codes", EscapeHtml(currentMesh.invite.codes.join(', ')));
16244:                 x += addHtmlValue("Installation Type", ["Background and interactive", "Interactive only", "Background only"][currentMesh.invite.flags & 3]);
16245:                 setDialogMode(2, "Invite Codes", 1, null, x);
16246:             }
16247:         }
16248: 
16249:         function p20editmeshInviteCodeValidate() {
16250:             var ok = true, codes = Q('agentInviteCode').value.split(',');
16251:             for (var i in codes) { codes[i] = codes[i].trim(); if (codes[i] == '') { ok = false; } }
16252:             QE('agentInviteCode', Q('agentJoinCheck').checked);
16253:             QE('agentType', Q('agentJoinCheck').checked);
16254:             QE('agentInviteType', Q('agentJoinCheck').checked);
16255:             QE('idx_dlgOkButton', (Q('agentJoinCheck').checked == false) || (ok));
16256:             QV('d2agentInstallTypeDiv', [0,1,2,4].indexOf(parseInt(Q('agentType').value)) > -1);
16257:         }
16258: 
16259:         function p20editmeshInviteCodeEx() {
16260:             if (Q('agentJoinCheck').checked == true) {
16261:                 var codes = Q('agentInviteCode').value.split(',');
16262:                 for (var i in codes) { codes[i] = codes[i].trim(); }
16263:                 meshserver.send({ action: 'editmesh', meshid: currentMesh._id, invite: { codes: codes, flags: parseInt(Q('agentInviteType').value), ag: parseInt(Q('agentType').value) } });
16264:             } else {
16265:                 meshserver.send({ action: 'editmesh', meshid: currentMesh._id, invite: '*' });
16266:             }
16267:         }
16268: 
16269:         function p20editMeshNotify() {
16270:             if (xxdialogMode) return false;
16271:             var meshNotify = 0;
16272:             var emailNotify = ((features2 & 0x00004000) && (userinfo.emailVerified));
16273:             if (userinfo.links && userinfo.links[currentMesh._id] && userinfo.links[currentMesh._id].notify) { meshNotify |= userinfo.links[currentMesh._id].notify; }
16274:             if (userinfo.notify && userinfo.notify[currentMesh._id]) { meshNotify |= userinfo.notify[currentMesh._id]; }
16275:             //var x = "Notification settings must also be turned on in account settings." + '<br /><br />';
16276:             var x = '<div style="border-bottom: 1px solid #888;margin-bottom:3px">' + "Web Page Notifications" + '</div>';
16277:             x += '<div><label><input id=p20notifyIntelDeviceConnect type=checkbox />' + "Device connections" + '</label></div>';
16278:             x += '<div><label><input id=p20notifyIntelDeviceDisconnect type=checkbox />' + "Device disconnections" + '</label></div>';
16279:             if (currentMesh.mtype < 3) {
16280:                 x += '<div><label><input id=p20notifyIntelAmtKvmActions type=checkbox />' + "Intel&reg; AMT desktop and serial events" + '</label></div>';
16281:             }
16282:             if (emailNotify) {
16283:                 x += '<br /><div style="border-bottom: 1px solid #888;margin-bottom:3px">' + "Email Notifications" + '</div>';
16284:                 x += '<div><label><input id=p20enotifyIntelDeviceConnect type=checkbox />' + "Device connections" + '</label></div>';
16285:                 x += '<div><label><input id=p20enotifyIntelDeviceDisconnect type=checkbox />' + "Device disconnections" + '</label></div>';
16286:                 x += '<div><label><input id=p20enotifyIntelDeviceHelp type=checkbox />' + "Help requests" + '</label></div>';
16287:             }
16288:             if ((userinfo.msghandle != null) && ((features2 & 0x02000000) != 0)) {
16289:                 x += '<br /><div style="border-bottom: 1px solid #888;margin-bottom:3px">' + "Messaging Notifications" + '</div>';
16290:                 x += '<div><label><input id=p20emsgDeviceConnect type=checkbox />' + "Device connections" + '</label></div>';
16291:                 x += '<div><label><input id=p20emsgDeviceDisconnect type=checkbox />' + "Device disconnections" + '</label></div>';
16292:                 x += '<div><label><input id=p20emsgDeviceHelp type=checkbox />' + "Help requests" + '</label></div>';
16293:             }
16294:             setDialogMode(2, "Notification Settings", 3, p20editMeshNotifyEx, x, emailNotify);
16295:             Q('p20notifyIntelDeviceConnect').checked = (meshNotify & 2);
16296:             Q('p20notifyIntelDeviceDisconnect').checked = (meshNotify & 4);
16297:             if (currentMesh.mtype < 3) { Q('p20notifyIntelAmtKvmActions').checked = (meshNotify & 8); }
16298:             if (emailNotify) {
16299:                 Q('p20enotifyIntelDeviceConnect').checked = (meshNotify & 16);
16300:                 Q('p20enotifyIntelDeviceDisconnect').checked = (meshNotify & 32);
16301:                 Q('p20enotifyIntelDeviceHelp').checked = (meshNotify & 64);
16302:             }
16303:             if ((userinfo.msghandle != null) && ((features2 & 0x02000000) != 0)) {
16304:                 Q('p20emsgDeviceConnect').checked = (meshNotify & 128);
16305:                 Q('p20emsgDeviceDisconnect').checked = (meshNotify & 256);
16306:                 Q('p20emsgDeviceHelp').checked = (meshNotify & 512);
16307:             }
16308:             return false;
16309:         }
16310: 
16311:         function p20editMeshNotifyEx(b, emailNotify) {
16312:             var meshNotify = 0;
16313:             meshNotify += Q('p20notifyIntelDeviceConnect').checked ? 2 : 0;
16314:             meshNotify += Q('p20notifyIntelDeviceDisconnect').checked ? 4 : 0;
16315:             if (currentMesh.mtype < 3) { meshNotify += Q('p20notifyIntelAmtKvmActions').checked ? 8 : 0; }
16316:             if (emailNotify) {
16317:                 meshNotify += Q('p20enotifyIntelDeviceConnect').checked ? 16 : 0;
16318:                 meshNotify += Q('p20enotifyIntelDeviceDisconnect').checked ? 32 : 0;
16319:                 meshNotify += Q('p20enotifyIntelDeviceHelp').checked ? 64 : 0;
16320:             }
16321:             if ((userinfo.msghandle != null) && ((features2 & 0x02000000) != 0)) {
16322:                 meshNotify += Q('p20emsgDeviceConnect').checked ? 128 : 0;
16323:                 meshNotify += Q('p20emsgDeviceDisconnect').checked ? 256 : 0;
16324:                 meshNotify += Q('p20emsgDeviceHelp').checked ? 512 : 0;
16325:             }
16326:             meshserver.send({ action: 'changemeshnotify', meshid: currentMesh._id, notify: meshNotify });
16327:         }
16328: 
16329:         //
16330:         // Mesh Summary
16331:         //
16332: 
16333:         function setupMeshSummaryStats() {
16334:             window.meshPowerChart = new Chart(document.getElementById('meshPowerChart').getContext('2d'), {
16335:                 type: 'doughnut',
16336:                 data: { datasets: [{ data: [0, 0], backgroundColor: ['#20F', '#40D', '#60B', '#809', '#A07', '#C05'] }] },
16337:                 options: { shadowColor: 'blue', responsive: true, plugins: { legend: { display: false } }, animation: { animateScale: true, animateRotate: true }, layout: { padding: { left: 10, right: 10, top: 10, bottom: 10 } } }
16338:             });
16339:             window.meshOsChart = new Chart(document.getElementById('meshOsChart').getContext('2d'), {
16340:                 type: 'doughnut',
16341:                 data: { datasets: [{ data: [0, 0], backgroundColor: ['#20F', '#30E', '#40D', '#50C', '#60B', '#70A', '#809', '#908', '#A07', '#B06', '#C05'] }] },
16342:                 options: { responsive: true, plugins: { legend: { display: false } }, animation: { animateScale: true, animateRotate: true }, layout: { padding: { left: 10, right: 10, top: 10, bottom: 10 } } }
16343:             });
16344:             window.meshConnChart = new Chart(document.getElementById('meshConnChart').getContext('2d'), {
16345:                 type: 'doughnut',
16346:                 data: { datasets: [{ data: [0, 0], backgroundColor: ['#20F', '#40D', '#60B', '#809', '#A07', '#C05'] }], labels: ["Not Connected", "Agent", "Intel&reg; AMT", "Agent + Intel&reg; AMT"] },
16347:                 options: { responsive: true, plugins: { legend: { display: false } }, animation: { animateScale: true, animateRotate: true }, layout: { padding: { left: 10, right: 10, top: 10, bottom: 10 } } }
16348:             });
16349:             window.meshSecurityChart = new Chart(document.getElementById('meshSecurityChart').getContext('2d'), {
16350:                 type: 'doughnut',
16351:                 data: { datasets: [{ data: [0, 0], backgroundColor: ['#20F', '#40D', '#60B', '#809', '#A07', '#C05'] }], labels: ["OK", "Anti-virus not active", "No automatic update", "Firewall not active", "Multiple Issues"] },
16352:                 options: { responsive: true, plugins: { legend: { display: false } }, animation: { animateScale: true, animateRotate: true }, layout: { padding: { left: 10, right: 10, top: 10, bottom: 10 } } }
16353:             });
16354:         }
16355: 
16356:         function p21updateMesh() {
16357:             if (currentMesh == null) return;
16358: 
16359:             // Add device group name
16360:             var meshrights = GetMeshRights(currentMesh), mname = EscapeHtml(currentMesh.name);
16361:             if (mname.length == 0) { mname = '<i>' + "None" + '</i>'; }
16362:             if ((meshrights & 1) != 0) { mname = '<span tabindex=0 title="' + "Click here to edit the device group name" + '" onclick=p20editmesh(1) onkeyup="if (event.key == \'Enter\') p20editmesh(1)" style=cursor:pointer>' + mname + ' <img class=hoverButton src="images/link5.png" /></span>'; }
16363:             QH('p21meshName', mname);
16364: 
16365:             // Update charts
```

## Lines 16992-17108
```text
16992:             157: "Removed messaging account of user {0}",
16993:             158: "Displaying alert box, title=\"{0}\", message=\"{1}\"",
16994:             159: "Device Powered On",
16995:             160: "Enabled Duo two-factor authentication",
16996:             161: "Disabled Duo two-factor authentication",
16997:             162: "Started messenger session \"{0}\" from {1} to {2}",
16998:             163: "Removed device {0} from user group {1}",
16999:             164: "Create file: \"{0}\""
17000:         };
17001: 
17002:         var eventsShortMessageId = {
17003:             1: "Group name",
17004:             2: "Description",
17005:             3: "Flags",
17006:             4: "Consent",
17007:             5: "Auto-Remove",
17008:             6: "Invite code",
17009:             7: "Relay device"
17010:         }
17011: 
17012:         // Highlights the device being hovered
17013:         function eventMouseHover(e, over) {
17014:             e.children[1].classList.remove('g1s');
17015:             e.children[2].classList.remove('style10s');
17016:             //e.children[2].style['background-color'] = ((over == 0) ? '#c9c9c9' : '#b9b9b9');
17017:             e.children[3].classList.remove('g2s');
17018:             if (over == 1) { e.children[1].classList.add('g1s'); e.children[2].classList.add('style10s'); e.children[3].classList.add('g2s'); }
17019:         }
17020: 
17021:         function eventsUpdate() {
17022:             var x = '', dateHeader = null;
17023:             for (var i in events) {
17024:                 var event = events[i], time = new Date(event.time);
17025:                 if (event.msg) {
17026:                     if (event.h == null) { event.h = Math.random(); }
17027:                     if (printDate(time) != dateHeader) {
17028:                         if (dateHeader != null) x += '</table>';
17029:                         dateHeader = printDate(time);
17030:                         x += '<table class=p3eventsTable cellpadding=0 cellspacing=0><tr><td colspan=4 class=DevSt>' + dateHeader + '</td></tr>';
17031:                     }
17032:                     var icon = 'si3';
17033:                     if (event.etype == 'ugrp') icon = 'm4';
17034:                     if (event.etype == 'user') icon = 'm2';
17035:                     if (event.etype == 'server') icon = 'si3';
17036: 
17037:                     var msg;
17038:                     if ((event.msgid == null) || (eventsMessageId[event.msgid] == null)) {
17039:                         if (typeof event.msg == 'string') { msg = EscapeHtml(event.msg).split('(R)').join('&reg;'); } else { msg = ''; }
17040:                     } else {
17041:                         msg = eventsMessageId[event.msgid];
17042:                         if (event.msgArgs != null) {
17043:                             for (var i in event.msgArgs) {
17044:                                 var xx = event.msgArgs[i];
17045:                                 if (Array.isArray(xx)) {
17046:                                     var x2 = [];
17047:                                     for (var j in xx) { if ((typeof xx[j] == 'number') && (eventsShortMessageId[xx[j]])) { x2.push(eventsShortMessageId[xx[j]]); } else { x2.push(xx[j]); } }
17048:                                     xx = x2.join(", ");
17049:                                 } else {
17050:                                     if ((typeof xx == 'string') && (xx.indexOf('DATETIME:') == 0)) { xx = printFlexDateTime(new Date(parseInt(xx.substring(9)))); }
17051:                                     
17052:                                 }
17053:                                 msg = msg.split('{' + i + '}').join(xx);
17054:                             }
17055:                         }
17056:                         msg = EscapeHtml(msg).split('(R)').join('&reg;');
17057:                     }
17058:                     if (event.nodeid) {
17059:                         var node = getNodeFromId(event.nodeid);
17060:                         if (node != null) {
17061:                             icon = 'si' + node.icon;
17062:                             msg = '<a href=# onclick=\'gotoDevice("' + event.nodeid + '",10);haltEvent(event);\'>' + EscapeHtml(node.name) + '</a> &rarr; ' + msg;
17063:                         }
17064:                     }
17065:                     if (event.username) {
17066:                         var guestname = '';
17067:                         if (event.guestname) { guestname = ' / <span title="' + "This is a guest sharing session" + '">' + EscapeHtml(event.guestname) + '</span>'; }
17068:                         if ((userinfo.siteadmin & 2) && (event.userid)) {
17069:                             msg = '<a href=# onclick=\'gotoUser("' + encodeURIComponentEx(event.userid) + '");haltEvent(event);\'>' + EscapeHtml(event.username) + '</a>' + guestname + ' &rarr; ' + msg;
17070:                         } else {
17071:                             msg = EscapeHtml(event.username) + guestname + ' &rarr; ' + msg;
17072:                         }
17073:                     }
17074:                     if (event.remoteaddr) { msg += ' (' + EscapeHtml(event.remoteaddr) + ')'; }
17075:                     if (event.etype == 'relay' || event.action == 'relaylog') icon = 'relayIcon16';
17076:                     x += '<tr onclick=showEventDetails(' + event.h + ',2)  onmouseover=eventMouseHover(this,1) onmouseout=eventMouseHover(this,0) style=cursor:pointer><td style=width:18px><div class=' + icon + '></div></td><td class=g1>&nbsp;</td><td class=style10>' + printTime(time) + ' - ' + msg + '</td><td class=g2>&nbsp;</td></tr><tr style=height:2px></tr>';
17077:                 }
17078:             }
17079:             if (dateHeader != null) x += '</table>';
17080:             if (x == '') x = '<br><i>' + "No Events Found" + '</i><br><br>';
17081:             QH('p3events', x);
17082:         }
17083: 
17084:         function refreshEvents() {
17085:             if (p3filterevents.value != "") {
17086:                 meshserver.send({ action: 'events', limit: parseInt(p3limitdropdown.value), filter: p3filterevents.value });
17087:             } else {
17088:                 meshserver.send({ action: 'events', limit: parseInt(p3limitdropdown.value) });
17089:             }
17090:         }
17091: 
17092:         function p3showReportDialog() {
17093:             if (xxdialogMode) return;
17094:             var x = '<div style=float:right><img src=\'images/report60.png\' height=74 width=60 style=padding-top:2px /></div><div>';
17095:             x += addHtmlValue("Report Type", '<select id=d2reportType style=width:160px onchange=p3showReportValidation()><option value=1>' + "All Events" + '</option>');
17096:             x += addHtmlValue("Time Span", '<select id=d2reportSpan style=width:160px onchange=p3showReportValidation()><option value=1>' + "All Available" + '</option><option value=2>' + "One Day" + '</option></select>');
17097:             x += '<div id=d2daySelector>' + addHtmlValue("Report Day", '<input type=date id=d2reportDay style=width:160px onchange=p3showReportValidation()></input>') + '</div>';
17098:             x += addHtmlValue("Group By", '<select id=d2reportGroupBy style=width:160px onchange=p3showReportValidation()><option value=1>' + "None" + '</option><option value=2>' + "Device Group" + '</option><option value=3>' + "User" + '</option>');
17099:             x += addHtmlValue("Format", '<select id=d2reportFormat style=width:160px onchange=p3showReportValidation()><option value=1>' + "CSV" + '</option><option value=2>' + "JSON" + '</option>');
17100:             x += '</div>';
17101:             setDialogMode(2, "Download Report", 3, null, x);
17102:             p3showReportValidation();
17103:             Q('d2reportType').focus();
17104:         }
17105: 
17106:         function p3showReportValidation() {
17107:             var reportType = Q('d2reportType').value;
17108:             var reportSpan = Q('d2reportSpan').value;
```

## Lines 18898-19014
```text
18898:                     if ((cr & 2) != 0) {
18899:                         trash = '<a href=# onclick=\'return p30removeNodeFromUser(event,"' + encodeURIComponentEx(node._id) + '")\' title="' + "Remove user rights to this device group" + '" style=cursor:pointer><img src=images/trash.png border=0 height=10 width=10></a>';
18900:                         rights = '<span style=cursor:pointer onclick=p20showAddMeshUserDialog(4,"' + encodeURIComponentEx(node._id) + '")>' + makeUserDeviceRightsString(r) + ' <img class=hoverButton style=cursor:pointer src=images/link5.png></span>';
18901:                     }
18902:                     nodename = '<a href=# onclick=\'gotoDevice("' + node._id + '",10);haltEvent(event);\'>' + nodename + '</a>';
18903:                     x += '<tr ' + (((++count % 2) == 0) ? 'style=background-color:#DDD' : '') + '><td style=width:30%><div title="' + "Device" + '" class=si' + node.icon + '></div><div>&nbsp;' + nodename + '<div></div></div></td><td style=width:70%><div style=float:right>' + trash + '</div><div>' + rights + '</div></td></tr>';
18904:                 }
18905:             }
18906:             if (count == 1) { x += '<tr><td><div style=padding:6px>&nbsp;<i>' + "No devices in common" + '</i><div></div></div></td><td></td></tr>'; }
18907:             x += '</tbody></table>';
18908: 
18909:             QH('p30html2', x);
18910:         }
18911: 
18912:         function p30removeDeviceSharing(event, nodeid, publicid, guestname) {
18913:             if (xxdialogMode) return;
18914:             setDialogMode(2, "Remove Device Share", 3, function(b, tag) { meshserver.send({ action: 'removeDeviceShare', nodeid: tag[0], publicid: tag[1] }); }, format("Confirm removal of device share \"{0}\"?", decodeURIComponent(guestname)), [ decodeURIComponent(nodeid), decodeURIComponent(publicid) ]);
18915:         }
18916: 
18917:         function p30removeNodeFromUser(event, nodeid) {
18918:             if (xxdialogMode) return;
18919:             var node = getNodeFromId(decodeURIComponent(nodeid));
18920:             setDialogMode(2, "Remove Device Permissions", 3, function(b, node) { meshserver.send({ action: 'adddeviceuser', nodeid: node._id, nodename: node.name, userids: [ currentUser._id ], rights: 0, remove: true }); }, format("Confirm removal of access rights for device \"{0}\"?", EscapeHtml(node.name)), node);
18921:         }
18922: 
18923:         function p30removeUserFromNode(event, userid) {
18924:             if (xxdialogMode) return;
18925:             var user = null, name = '';
18926:             userid = decodeURIComponent(userid);
18927:             if (userid.startsWith('user/')) {
18928:                 if (users) { user = users[userid]; if (user != null) { name = user.name; } }
18929:                 setDialogMode(2, "Remove User Permissions", 3, function(b, user) { meshserver.send({ action: 'adddeviceuser', nodeid: currentNode._id, nodename: currentNode.name, userids: [ userid ], rights: 0, remove: true }); }, name?format("Confirm removal of access rights for user \"{0}\"?", name):"Confirm removal of access rights?", user);
18930:             } else if (userid.startsWith('ugrp/')) {
18931:                 if (usergroups) { user = usergroups[userid]; if (user != null) { name = user.name; } }
18932:                 setDialogMode(2, "Remove User Group Permissions", 3, function(b, user) { meshserver.send({ action: 'adddeviceuser', nodeid: currentNode._id, nodename: currentNode.name, userids: [ userid ], rights: 0, remove: true }); }, name?format("Confirm removal of access rights for user group \"{0}\"?", name):"Confirm removal of access rights?", user);
18933:             }
18934:         }
18935: 
18936:         function p30RemoveUserGroup(button, ugrpid) {
18937:             if (xxdialogMode || (usergroups == null)) return;
18938:             var groupid = decodeURIComponent(ugrpid), group = usergroups[groupid];
18939:             var name = (group != null)?EscapeHtml(group.name):('<i>' + "Unknown" + '</i>');
18940:             setDialogMode(2, "Remove User Group Membership", 3, p30RemoveUserGroupEx, format("Confirm membership removal of user group \"{0}\"?", name), groupid);
18941:         }
18942: 
18943:         function p30RemoveUserGroupEx(b, groupid) {
18944:             meshserver.send({ action: 'removeuserfromusergroup', ugrpid: groupid, userid: currentUser._id });
18945:         }
18946: 
18947:         function p30showAddUserGroupDialog() {
18948:             if (xxdialogMode || (usergroups == null)) return;
18949:             var y = '';
18950:             for (var i in usergroups) {
18951:                 if ((usergroups[i].membershipType != null) || (usergroups[i]._id.split('/')[1] != currentUser._id.split('/')[1])) continue;
18952:                 if ((currentUser.links == null) || (currentUser.links[i] == null)) { y += '<option value=' + encodeURIComponentEx(i) + '>' + EscapeHtml(usergroups[i].name) + '</option>'; }
18953:             }
18954:             var x = addHtmlValue("User Group", '<div style=width:230px;margin:0;padding:0><select id=dp2groupid style=width:100%>' + y + '</select></div>');
18955:             setDialogMode(2, "Add Membership", 3, p30showAddUserGroupDialogEx, x);
18956:             Q('dp2groupid').focus();
18957:             return false;
18958:         }
18959: 
18960:         function p30showAddUserGroupDialogEx() {
18961:             meshserver.send({ action: 'addusertousergroup', ugrpid: decodeURIComponent(Q('dp2groupid').value), usernames: [ currentUser._id.split('/')[2] ] });
18962:         }
18963: 
18964:         function p30removeMeshFromUser(e, meshid) {
18965:             if (xxdialogMode) return;
18966:             var mesh = meshes[decodeURIComponent(meshid)];
18967:             if (mesh == null) return;
18968:             setDialogMode(2, "Remove Device Group Permissions", 3, p30removeMeshFromUserEx, format("Confirm removal of access rights for device group \"{0}\"?", EscapeHtml(mesh.name)), mesh._id);
18969:         }
18970: 
18971:         function p30removeMeshFromUserEx(b, meshid) {
18972:             meshserver.send({ action: 'removemeshuser', meshid: meshid, userid: currentUser._id });
18973:         }
18974: 
18975:         //
18976:         // MY USER EVENTS
18977:         //
18978: 
18979:         var currentUserEvents = null;
18980:         function userEventsUpdate() {
18981:             if (currentUser == null) return;
18982:             var x = '', dateHeader = null;
18983:             for (var i in currentUserEvents) {
18984:                 var event = currentUserEvents[i], time = new Date(event.time);
18985:                 if (event.msg) {
18986:                     if (event.h == null) { event.h = Math.random(); }
18987:                     if (printDate(time) != dateHeader) {
18988:                         if (dateHeader != null) x += '</table>';
18989:                         dateHeader = printDate(time);
18990:                         x += '<table class=p3eventsTable cellpadding=0 cellspacing=0><tr><td colspan=4 class=DevSt>' + dateHeader + '</td></tr>';
18991:                     }
18992:                     var icon = 'si3';
18993:                     if (event.etype == 'user') icon = 'm2';
18994:                     if (event.etype == 'server') icon = 'si3';
18995: 
18996:                     var msg;
18997:                     if ((event.msgid == null) || (eventsMessageId[event.msgid] == null)) {
18998:                         if (typeof event.msg == 'string') { msg = EscapeHtml(event.msg).split('(R)').join('&reg;'); } else { msg = ''; }
18999:                     } else {
19000:                         msg = eventsMessageId[event.msgid];
19001:                         for (var i in event.msgArgs) {
19002:                             var xx = event.msgArgs[i];
19003:                             if ((typeof xx == 'string') && (xx.indexOf('DATETIME:') == 0)) { xx = printFlexDateTime(new Date(parseInt(xx.substring(9)))); }
19004:                             msg = msg.split('{' + i + '}').join(xx);
19005:                         }
19006:                         //if (event.msgArgs != null) { for (var i in event.msgArgs) { msg = msg.split('{' + i + '}').join(event.msgArgs[i]); } }
19007:                         msg = EscapeHtml(msg).split('(R)').join('&reg;');
19008:                     }
19009:                     if (event.nodeid) {
19010:                         var node = getNodeFromId(event.nodeid);
19011:                         if (node != null) {
19012:                             icon = 'si' + node.icon;
19013:                             msg = '<a href=# onclick=\'gotoDevice("' + event.nodeid + '",10);haltEvent(event);\'>' + EscapeHtml(node.name) + '</a> &rarr; ' + msg;
19014:                         }
```

## Lines 19568-19684
```text
19568:             }
19569:             if (f == 'records') {
19570:                 var c = {
19571:                     'node': "Device record",
19572:                     'mesh': "Device group record",
19573:                     'user': "User records",
19574:                     'sysinfo': "Device information records",
19575:                     'iploc': "IP location information records",
19576:                     'note': "Text notes records",
19577:                     'ifinfo': "Network interface information records",
19578:                     'cfile': "Configuration file records",
19579:                     'lastconnect': "Last connection time records",
19580:                     'power': "Device power transition records",
19581:                     'events': "Event records",
19582:                     'serverstats': "Server statistics records",
19583:                     'ugrp': "User group records",
19584:                     'deviceshare': "Device sharing records",
19585:                     'logintoken': "Account login token records",
19586:                     'smbios': "Device SMBIOS record",
19587:                     'pmt': "Device Push Notification Record",
19588:                     'meshcentral': "Device, users, groups and other records"
19589:                 }
19590:                 return (c[v] != null)? c[v] : v;
19591:             }
19592:             return EscapeHtml(v);
19593:         }
19594: 
19595:         function renderReportFormatCSV(v, f) {
19596:             if (f == 'datetime') { return printDateTime(new Date(v)); }
19597:             if (f == 'time') { return printTime(new Date(v)); }
19598:             if (f == 'protocol') {
19599:                 if (v == 1) return "Terminal";
19600:                 if (v == 2) return "Desktop";
19601:                 if (v == 5) return "Files";
19602:                 if (v == 6) return "Admin PowerShell";
19603:                 if (v == 8) return "User Shell";
19604:                 if (v == 9) return "User PowerShell";
19605:                 if (v == 100) return "AMT-WSMAN";
19606:                 if (v == 101) return "AMT-Redir";
19607:                 if (v == 200) return "Messenger";
19608:                 if (v == 201) return "Web-RDP";
19609:                 if (v == 202) return "Web-SSH";
19610:                 if (v == 203) return "Web-SFTP";
19611:                 if (v == 204) return "Web-VNC";
19612:                 return "Unknown" + ' (' + v + ')';
19613:             }
19614:             if (f == 'node') {
19615:                 var node = getNodeFromId(v);
19616:                 if (node != null) { return node.name; }
19617:             }
19618:             if (f == 'mesh') {
19619:                 var mesh = meshes[v];
19620:                 if (mesh != null) { return mesh.name }
19621:             }
19622:             if (f == 'user') {
19623:                 var user = null;
19624:                 if (v == userinfo._id) { user = userinfo; } else { if (users != null) { user = users[v]; } }
19625:                 if (user != null) {
19626:                     var name = user.name;
19627:                     if (user.realname != null) { name += ' - ' + user.realname; }
19628:                     return name;
19629:                 } else {
19630:                     return "Unknown User";
19631:                 }
19632:             }
19633:             if (f == 'msg') {
19634:                 if (v == 107) return "Succesful login";
19635:                 if (v == 108) return "Incorrect 2nd factor";
19636:                 if (v == 109) return "Locked account";
19637:                 if (v == 110) return "Invalid login attempt";
19638:             }
19639:             if (f == 'records') { return renderReportFormat(v, f); }
19640:             return '' + v;
19641:         }
19642: 
19643:         function p60downloadReport() {
19644:             if (xxdialogMode || (reportCSV == null)) return;
19645:             saveAs(stringToUtf8Blob(reportCSV), "Report.csv");
19646:         }
19647: 
19648:         //
19649:         // NOTIFICATIONS
19650:         //
19651: 
19652:         var notifications = [];
19653: 
19654:         // Toggle showing notifications
19655:         function clickNotificationIcon(show) {
19656:             //addNotification({ icon:0, text:'test' });
19657:             if (show == true) { QV('notifiyBox', true); } else if (show == false) { QV('notifiyBox', false); } else { QV('notifiyBox', QS('notifiyBox')['display'] == 'none'); }
19658:             drawNotifications();
19659:         }
19660: 
19661:         // Set the notification count on the upper right of the screen
19662:         function setNotificationCount(c) {
19663:             if (parseInt(Q('notificationCount').innerHTML) == c) return; // If the count did not change, exit now.
19664:             QH('notificationCount', c);
19665:             QS('notificationCount')['background-color'] = (c == 0)?'lightblue':'orange';
19666:             QV('notificationCount', c > 0);
19667:         }
19668: 
19669:         // Refresh the notification box
19670:         function drawNotifications() {
19671:             var notifySettings = getstore('notifications', 0);
19672:             var r = '';
19673:             if (notifications.length == 0) {
19674:                 r = '<div style=margin:5px>' + "There are currently no notifications" + '</div>';
19675:             } else {
19676:                 for (var i in notifications) {
19677:                     var n = notifications[i], t = '', d = new Date(n.time), icon = 0;
19678:                     if (n.title != null) { t = '<b>' + EscapeHtml(n.title) + '</b>: ' }
19679:                     if (n.nodeid != null) {
19680:                         var node = getNodeFromId(n.nodeid);
19681:                         if (node != null) {
19682:                             icon = node.icon;
19683:                             if (notifySettings & 16) { t = '<b>' + EscapeHtml(meshes[node.meshid].name) + ' / ' + EscapeHtml(node.name) + '</b>: '; } else { t = '<b>' + EscapeHtml(node.name) + '</b>: '; } // Display with or without group name
19684:                         }
```

## Lines 19745-19886
```text
19745:                         QS('notifyx' + n.id)['border-top'] = '1px solid transparent';
19746:                     }
19747:                 }
19748:             }
19749:         }
19750: 
19751:         // Add a new notification and play the notification sound
19752:         function addNotification(n) {
19753:             // Perform message translation
19754:             var translatedTitles = [
19755:                 null,
19756:                 "New Account", // 1
19757:                 "Server Limit",
19758:                 "Security Warning",
19759:                 "Account Settings",
19760:                 "Device Group",
19761:                 "Invite Codes"
19762:             ];
19763:             var translatedMessages = [
19764:                 null,
19765:                 "Permission denied", // 1
19766:                 "Invalid username",
19767:                 "Invalid password",
19768:                 "Invalid email",
19769:                 "Invalid domain",
19770:                 "Invalid site permissions",
19771:                 "User already exists",
19772:                 "Unable to add user in this mode",
19773:                 "Validation exception",
19774:                 "Account limit reached.", // 10
19775:                 "Chat Request, Click here to accept.",
19776:                 "There has been {0} failed login attempts on this account since the last login.",
19777:                 "Failed to change email address, another account already using: {0}.",
19778:                 "Email sent.",
19779:                 "User {0} not found.",
19780:                 "Users {0} not found.",
19781:                 "Error, unable to change to previously used password.",
19782:                 "Error, unable to change to commonly used password.",
19783:                 "Error, password not changed.",
19784:                 "Password changed.", // 20
19785:                 "Current password not correct.",
19786:                 "Error, invite code \"{0}\" already in use.",
19787:                 "SMS gateway not enabled",
19788:                 "No user management rights",
19789:                 "Invalid SMS message",
19790:                 "No phone number for this user",
19791:                 "SMS succesfully sent.",
19792:                 "SMS error",
19793:                 "SMS error: {0}",
19794:                 "Email domain \"{0}\" is not allowed. Only ({1}) are allowed", // 30,
19795:                 "Invalid message",
19796:                 "Message succesfully sent.",
19797:                 "Message error",
19798:                 "Message error: {0}"
19799:             ];
19800:             if (typeof n.titleid == 'number') { try { n.title = translatedTitles[n.titleid]; } catch (ex) {} }
19801:             if (typeof n.msgid == 'number') { try { n.text = translatedMessages[n.msgid]; if (Array.isArray(n.args)) { n.text = format(n.text, n.args[0], n.args[1], n.args[2], n.args[3], n.args[4], n.args[5]); } } catch (ex) { } }
19802: 
19803:             // Show notification within the web page.
19804:             if (n.time == null) { n.time = Date.now(); }
19805:             if ((typeof n.id != 'number') || (isFinite(n.id) == false)) { n.id = Math.random(); }
19806:             notifications.unshift(n);
19807:             setNotificationCount(notifications.length);
19808:             clickNotificationIcon(true);
19809:             var notifySettings = getstore('notifications', 0);
19810:             if (notifySettings & 1) { Q('chimes').play(); }
19811: 
19812:             // If web notifications are granted, use it.
19813:             var notification = null;
19814:             if (Notification && (Notification.permission == 'granted')) {
19815:                 var text = n.text.split('&reg;').join('').split('<b>').join('').split('</b>').join('').split('<br />').join('\r\n'); // Clean up any HTML codes
19816:                 if (n.nodeid) {
19817:                     var node = getNodeFromId(n.nodeid);
19818:                     if (node) {
19819:                         if (notifySettings & 16) { // Notify with group name
19820:                             notification = new Notification(decodeURIComponent('{{{extitle}}}') + ' - ' + meshes[node.meshid].name + ' - ' + node.name, { tag: n.tag, body: text, icon: '/images/notify/icons128-' + node.icon + '.png' });
19821:                         } else {
19822:                             notification = new Notification(decodeURIComponent('{{{extitle}}}') + ' - ' + node.name, { tag: n.tag, body: text, icon: '/images/notify/icons128-' + node.icon + '.png' });
19823:                         }
19824:                     }
19825:                 } else {
19826:                     if (n.icon == null) { n.icon = 0; }
19827:                     var title = n.title;
19828:                     if (title == null) { title = ''; } else { title = ' - ' + n.title; }
19829:                     notification = new Notification(decodeURIComponent('{{{extitle}}}') + title, { tag: n.tag, body: text, icon: '/images/notify/icons128-' + n.icon + '.png' });
19830:                 }
19831:                 notification.id = n.id;
19832:                 notification.xtag = n.tag;
19833:                 notification.url = n.url;
19834:                 notification.nodeid = n.nodeid;
19835:                 notification.username = n.username;
19836:                 notification.onclick = function (e) { notificationSelected(e.target.id, true); }
19837:                 n.notification = notification;
19838:             }
19839: 
19840:             // If the notification has a max time, setup the timer here.
19841:             if ((typeof n.maxtime == 'number') && (n.maxtime > 0)) { var trigger = function notifyRemoveTrigger() { notificationDelete(notifyRemoveTrigger.xid); }; trigger.xid = n.id; setTimeout(trigger, n.maxtime * 1000); }
19842:         }
19843: 
19844:         // Remove all notifications
19845:         function deleteAllNotifications() {
19846:             notifications = [];
19847:             setNotificationCount(0);
19848:             drawNotifications();
19849:             QV('notifiyBox', false);
19850:         }
19851: 
19852:         //
19853:         // MyServer General
19854:         //
19855: 
19856:         function setupGeneralServerStats() {
19857:             window.serverStatCpu = new Chart(document.getElementById('serverCpuChart').getContext('2d'), {
19858:                 type: 'doughnut',
19859:                 data: { datasets: [{ data: [0, 0], backgroundColor: ['#AAAAAA', '#00AA00'] }], labels: ["Used", "Free"] },
19860:                 options: { events: [], responsive: true, plugins: { legend: { display: false, } }, animation: { animateScale: true, animateRotate: true }, width: '40px' }
19861:             });
19862:             window.serverStatMemory = new Chart(document.getElementById('serverMemoryChart').getContext('2d'), {
19863:                 type: 'doughnut',
19864:                 data: { datasets: [{ data: [0, 0], backgroundColor: ['#AAAAAA', '#00AA00'] }], labels: ["Used", "Free"] },
19865:                 options: { events: [], responsive: true, plugins: { legend: { display: false, } }, animation: { animateScale: true, animateRotate: true }, width: '40px' }
19866:             });
19867:         }
19868: 
19869:         var lastServerStats = null;
19870:         function updateGeneralServerStats(message) {
19871:             if (message != null) { lastServerStats = message; } else { message = lastServerStats; }
19872:             if (message == null) return;
19873: 
19874:             var serverStatsStrings = {
19875:                 ServerState: "Server State",
19876:                 AgentErrorCounters: "Agent Error Counters",
19877:                 UnknownGroup: "Unknown Group",
19878:                 InvalidPKCSsignature: "Invalid PKCS signature",
19879:                 InvalidRSAsignature: "Invalid RSA signature",
19880:                 InvalidJSON: "Invalid JSON",
19881:                 UnknownAction: "Unknown Action",
19882:                 BadWebCertificate: "Bad Web Certificate",
19883:                 BadSignature: "Bad Signature",
19884:                 MaxSessionsReached: "Max Sessions Reached",
19885:                 UnknownDeviceGroup: "Unknown Device Group",
19886:                 InvalidDeviceGroupType: "Invalid Device Group Type",
```

